package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lk.finora.api.support.IntegrationTest;

/** Independent committed requests, isolated synthetic accounts, and a fake mail delivery sink. */
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetControllerTest extends IntegrationTest {
    private static final String AUTH = "/api/v1/auth/";
    private static final String OLD = "Original-Synthetic9";
    private static final String NEW = "Replacement-Synthetic8";
    private static final String OTHER = "Another-Synthetic7";
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean PasswordResetMailer mailer;
    private final List<Long> users = new ArrayList<>();
    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();

    @BeforeEach void captureMail() {
        doAnswer(call -> {
            deliveries.add(new Delivery(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            return null;
        }).when(mailer).send(anyString(), anyString(), anyString());
    }

    @AfterEach void cleanSyntheticAccounts() {
        for (long id : users) {
            jdbc.update("DELETE FROM audit_events WHERE action = 'PASSWORD_RESET' AND target_type = 'USER' AND target_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        }
    }

    private String account(String language) throws Exception {
        String email = "t61-" + UUID.randomUUID() + "@example.test";
        String body = postJson("register", Map.of("email", email, "password", OLD, "fullName", "Synthetic T61",
                "language", language, "over18", true, "consentVersion", "1.0"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        users.add(jsonMapper.readTree(body).get("userId").asLong());
        return email;
    }

    private long id(String email) { return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email); }
    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(AUTH + path).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }
    private ResultActions forgot(String email) throws Exception {
        return postJson("password/forgot", Map.of("email", email)).andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    private ResultActions reset(String email, String code, String password) throws Exception {
        return postJson("password/reset", Map.of("email", email, "code", code, "newPassword", password));
    }
    private ResultActions login(String email, String password) throws Exception {
        return postJson("login", Map.of("email", email, "password", password));
    }
    private String body(ResultActions result) throws Exception { return result.andReturn().getResponse().getContentAsString(); }
    private String field(String body, String name) { return jsonMapper.readTree(body).get(name).asString(); }
    private String latest(String email) {
        return deliveries.stream().filter(d -> d.email().equals(email)).reduce((a, b) -> b).orElseThrow().code();
    }
    private int codeCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_codes WHERE user_id = ?", Integer.class, id);
    }
    private String wrong(String code) { return "000000".equals(code) ? "000001" : "000000"; }
    private void invalid(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_CODE"))
                .andExpect(jsonPath("$.message").value("Reset code is invalid or expired"));
    }

    @Test void genericForgotResponseAndHashOnly(CapturedOutput output) throws Exception {
        String email = account("SI");
        String disabled = account("EN");
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", id(disabled));
        String accepted = body(forgot(email.toUpperCase(java.util.Locale.ROOT)));
        assertThat(body(forgot("unknown-" + UUID.randomUUID() + "@example.test"))).isEqualTo(accepted);
        assertThat(body(forgot(disabled))).isEqualTo(accepted);
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).language()).isEqualTo("SI");
        String code = latest(email);
        assertThat(code).matches("[0-9]{6}");
        String hash = jdbc.queryForObject("SELECT code_hash FROM password_reset_codes WHERE user_id = ?", String.class, id(email));
        assertThat(hash).startsWith("$2a$12$").isNotEqualTo(code);
        assertThat(encoder.matches(code, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND, created_at, expires_at) FROM password_reset_codes WHERE user_id = ?",
                Integer.class, id(email))).isEqualTo(900);
        assertThat(codeCount(id(disabled))).isZero();
        assertThat(accepted).doesNotContain(code, email, hash);
        assertThat(output.getAll()).doesNotContain(code, hash, OLD);
    }

    @Test void replacesOldCodeAndLimitsFiveAttemptsAcrossRequests() throws Exception {
        String email = account("EN");
        forgot(email);
        String old = latest(email);
        forgot(email);
        String active = latest(email);
        assertThat(active).isNotEqualTo(old);
        invalid(reset(email, old, NEW));
        for (int i = 2; i <= 5; i++) {
            invalid(reset(email, wrong(active), NEW));
            if (i < 5) assertThat(jdbc.queryForObject("SELECT attempts FROM password_reset_codes WHERE user_id = ?",
                    Integer.class, id(email))).isEqualTo(i);
        }
        assertThat(codeCount(id(email))).isZero();
        invalid(reset(email, active, NEW));
        login(email, OLD).andExpect(status().isOk());
    }

    @Test void expiryUnknownDisabledAndGenerationMismatchAreRejected() throws Exception {
        String email = account("EN");
        forgot(email);
        String code = latest(email);
        jdbc.update("UPDATE password_reset_codes SET expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE user_id = ?", id(email));
        invalid(reset(email, code, NEW));
        assertThat(codeCount(id(email))).isZero();
        invalid(reset("unknown@example.test", code, NEW));
        forgot(email);
        code = latest(email);
        jdbc.update("UPDATE users SET status = 'DISABLED', staff_session_generation = staff_session_generation + 1 WHERE id = ?", id(email));
        invalid(reset(email, code, NEW));
        jdbc.update("UPDATE users SET status = 'ACTIVE', staff_session_generation = staff_session_generation + 1 WHERE id = ?", id(email));
        invalid(reset(email, code, NEW));
        assertThat(codeCount(id(email))).isZero();
    }

    @Test void rollingLimitSurvivesSuccessfulReset() throws Exception {
        String email = account("EN");
        String accepted = body(forgot(email));
        forgot(email);
        forgot(email);
        assertThat(deliveries).hasSize(3);
        String code = latest(email);
        reset(email, code, NEW).andExpect(status().isNoContent());
        invalid(reset(email, code, OTHER));
        assertThat(body(forgot(email))).isEqualTo(accepted);
        assertThat(deliveries).hasSize(3);
        jdbc.update("UPDATE password_reset_issuances SET issued_at = UTC_TIMESTAMP(6) - INTERVAL 61 MINUTE WHERE user_id = ? ORDER BY id LIMIT 1", id(email));
        forgot(email);
        assertThat(deliveries).hasSize(4);
        assertThat(codeCount(id(email))).isEqualTo(1);
    }

    @Test void weakAndOverlongUtf8PasswordDoNotConsumeCode() throws Exception {
        String email = account("EN");
        forgot(email);
        String code = latest(email);
        reset(email, code, "letters-only").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        reset(email, code, "A1" + "\u0dc3".repeat(30)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT attempts FROM password_reset_codes WHERE user_id = ?", Integer.class, id(email))).isZero();
        reset(email, code, NEW).andExpect(status().isNoContent());
    }

    @Test void resetRevokesAllRolesSessionsAndDoesNotAffectOtherAccounts(CapturedOutput output) throws Exception {
        String other = account("EN");
        String unaffected = body(login(other, OLD).andExpect(status().isOk()));
        for (String role : List.of("USER", "STAFF", "ADMIN")) {
            String email = account("EN");
            long owner = id(email);
            jdbc.update("UPDATE users SET role = ? WHERE id = ?", role, owner);
            String session1 = body(login(email, OLD).andExpect(status().isOk()));
            String session2 = body(login(email, OLD).andExpect(status().isOk()));
            forgot(email);
            String code = latest(email);
            String response = body(reset(email, code, NEW).andExpect(status().isNoContent()));
            assertThat(response).isEmpty();
            assertThat(codeCount(owner)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                    Integer.class, owner)).isZero();
            for (String session : List.of(session1, session2)) {
                postJson("refresh", Map.of("refreshToken", field(session, "refreshToken")))
                        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
                String path = switch (role) {
                    case "ADMIN" -> "/api/v1/admin/admins";
                    case "STAFF" -> "/api/v1/staff/stats";
                    default -> "/api/v1/me";
                };
                mockMvc.perform(get(path).header("Authorization", "Bearer " + field(session, "accessToken")))
                        .andExpect(status().isUnauthorized());
            }
            login(email, OLD).andExpect(status().isUnauthorized());
            login(email, NEW).andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role));
            assertThat(jdbc.queryForMap("SELECT actor_id, target_id, reason FROM audit_events WHERE action = 'PASSWORD_RESET' AND target_type = 'USER' AND target_id = ?", owner))
                    .containsEntry("actor_id", owner).containsEntry("target_id", owner).containsEntry("reason", null);
            String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, owner);
            assertThat(encoder.matches(NEW, hash)).isTrue();
            assertThat(output.getAll()).doesNotContain(OLD, NEW, code, hash);
        }
        postJson("refresh", Map.of("refreshToken", field(unaffected, "refreshToken"))).andExpect(status().isOk());
    }

    @Test void concurrentIssueAndConsumeAreSerialized() throws Exception {
        String email = account("EN");
        var pool = Executors.newFixedThreadPool(4);
        try {
            var a = pool.submit(() -> body(forgot(email)));
            var b = pool.submit(() -> body(forgot(email)));
            var c = pool.submit(() -> body(forgot(email)));
            var d = pool.submit(() -> body(forgot(email)));
            String accepted = a.get(45, TimeUnit.SECONDS);
            for (var request : List.of(b, c, d)) assertThat(request.get(45, TimeUnit.SECONDS)).isEqualTo(accepted);
            assertThat(deliveries).hasSize(3);
            String hash = jdbc.queryForObject("SELECT code_hash FROM password_reset_codes WHERE user_id = ?", String.class, id(email));
            String active = deliveries.stream().map(Delivery::code).filter(code -> encoder.matches(code, hash)).findFirst().orElseThrow();
            var first = pool.submit(() -> reset(email, active, NEW).andReturn().getResponse().getStatus());
            var second = pool.submit(() -> reset(email, active, OTHER).andReturn().getResponse().getStatus());
            assertThat(List.of(first.get(45, TimeUnit.SECONDS), second.get(45, TimeUnit.SECONDS))).containsExactlyInAnyOrder(204, 400);
            assertThat(codeCount(id(email))).isZero();
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void resetMetadataCascadesWithUserDeletion() throws Exception {
        String email = account("EN");
        long owner = id(email);
        forgot(email);
        jdbc.update("DELETE FROM users WHERE id = ?", owner);
        assertThat(codeCount(owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_issuances WHERE user_id = ?", Integer.class, owner)).isZero();
    }

    private record Delivery(String email, String language, String code) {
        @Override public String toString() { return "Synthetic reset mail"; }
    }
}
