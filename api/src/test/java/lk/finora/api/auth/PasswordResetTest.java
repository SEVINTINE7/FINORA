package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import lk.finora.api.support.IntegrationTest;

@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PasswordResetTest extends IntegrationTest {
    private static final String OWNER = "reset-owner@example.test";
    private static final String OLD = "BeforeReset2026";
    private static final String NEW = "AfterReset2026";
    private static final String FOR_OWNER = " WHERE user_id = (SELECT id FROM users WHERE email = ?)";
    @MockitoBean PasswordResetMail mail;
    @MockitoBean PasswordResetRequests requests;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;

    @BeforeEach void register() throws Exception {
        when(mail.configured()).thenReturn(true);
        doAnswer(call -> {
            call.getArgument(0, Runnable.class).run();
            return null;
        }).when(requests).submit(any(Runnable.class));
        send("register", Map.of("email", OWNER, "password", OLD, "fullName", "Synthetic Owner",
                "language", "SI", "over18", true, "consentVersion", "1.0")).andExpect(status().isCreated());
    }

    private ResultActions send(String route, Map<String, ?> fields) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/" + route).contentType("application/json").content(json(fields)));
    }
    private String issue(String email) throws Exception {
        return send("password/forgot", Map.of("email", email)).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }
    private String latest(int count) {
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail, times(count)).afterCommit(eq(OWNER), eq("SI"), code.capture());
        return code.getValue();
    }
    private ResultActions resetCode(String code, String password) throws Exception {
        return send("password/reset", Map.of("email", OWNER, "code", code, "newPassword", password));
    }
    private void invalid(String code) throws Exception {
        resetCode(code, NEW).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_CODE"));
    }

    @Test void replacesCodeAndEnforcesRollingLimitAcrossSuccessfulReset() throws Exception {
        assertThat(issue("nobody@example.test")).isEqualTo(issue(OWNER));
        verify(mail, never()).afterCommit(eq("nobody@example.test"), anyString(), anyString());
        String first = latest(1);
        String hash = jdbc.queryForObject("SELECT code_hash FROM password_reset_codes" + FOR_OWNER, String.class, OWNER);
        issue(OWNER);
        String second = latest(2);
        assertThat(jdbc.queryForObject("SELECT code_hash FROM password_reset_codes" + FOR_OWNER, String.class, OWNER))
                .isNotEqualTo(hash);
        assertThat(first.equals(second)).isFalse();
        invalid(first);
        resetCode(second, NEW).andExpect(status().isNoContent());
        issue(OWNER);
        issue(OWNER);
        latest(3);
        jdbc.update("UPDATE password_reset_issues SET created_at = UTC_TIMESTAMP(6) - INTERVAL 1 HOUR" +
                FOR_OWNER + " ORDER BY id LIMIT 1", OWNER);
        issue(OWNER);
        latest(4);
    }

    @Test void enforcesFifteenMinutesFiveGuessesAndAccountState() throws Exception {
        issue(OWNER);
        String code = latest(1);
        String wrong = code.equals("000000") ? "000001" : "000000";
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,created_at,expires_at) FROM password_reset_codes"
                + FOR_OWNER, Integer.class, OWNER)).isEqualTo(900);
        for (int attempt = 1; attempt <= 4; attempt++) {
            invalid(wrong);
            assertThat(jdbc.queryForObject("SELECT attempts FROM password_reset_codes" + FOR_OWNER,
                    Integer.class, OWNER)).isEqualTo(attempt);
        }
        invalid(wrong);
        invalid(code);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_codes" + FOR_OWNER, Integer.class, OWNER)).isZero();
        issue(OWNER);
        jdbc.update("UPDATE password_reset_codes SET expires_at = UTC_TIMESTAMP(6)" + FOR_OWNER, OWNER);
        invalid(latest(2));
        issue(OWNER);
        jdbc.update("UPDATE users SET status='DISABLED' WHERE email=?", OWNER);
        invalid(latest(3));
        issue(OWNER);
        latest(3);
    }

    @Test void resetsOnceRevokesBothSessionsAndRedactsSecrets(CapturedOutput logs) throws Exception {
        var credentials = Map.of("email", OWNER, "password", OLD);
        String[] refresh = new String[2];
        for (int i = 0; i < refresh.length; i++) {
            refresh[i] = jsonMapper.readTree(send("login", credentials).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).get("refreshToken").asString();
        }
        issue(OWNER);
        String code = latest(1);
        assertThat(code).matches("[0-9]{6}");
        assertThat(jdbc.queryForObject("SELECT code_hash FROM password_reset_codes" + FOR_OWNER,
                String.class, OWNER)).startsWith("$2");
        resetCode(code, "weak").andExpect(status().isBadRequest());
        resetCode(code, "A1" + "ක".repeat(24)).andExpect(status().isBadRequest());
        resetCode(code, NEW).andExpect(status().isNoContent()).andExpect(content().string(""));
        entities.clear();
        invalid(code);
        for (String token : refresh) {
            send("refresh", Map.of("refreshToken", token)).andExpect(status().isUnauthorized());
        }
        send("login", credentials).andExpect(status().isUnauthorized());
        send("login", Map.of("email", OWNER, "password", NEW)).andExpect(status().isOk());
        var audit = jdbc.queryForMap("SELECT actor_id,target_id,reason FROM audit_events WHERE action='PASSWORD_RESET' AND actor_id=(SELECT id FROM users WHERE email=?)", OWNER);
        assertThat(audit.get("actor_id")).isEqualTo(audit.get("target_id"));
        assertThat(audit.get("reason")).isNull();
        assertThat(logs.getAll().contains(OLD) || logs.getAll().contains(NEW) || logs.getAll().contains(code)).isFalse();
    }

    @Test void localizedMailContainsOnlyCodeAndExpiry() {
        String code = "093572";
        var en = PasswordResetMail.message("from@example.test", OWNER, "EN", code);
        var si = PasswordResetMail.message("from@example.test", OWNER, "SI", code);
        assertThat(en.getText()).isEqualTo("Code: 093572\nExpires in 15 minutes.");
        assertThat(si.getText()).isEqualTo("කේතය: 093572\nවිනාඩි 15 කින් කල් ඉකුත් වේ.");
        assertThat(en.getSubject()).doesNotContain(code);
        assertThat(si.getSubject()).doesNotContain(code);
    }
}
