package lk.finora.api.user;

import java.util.Map;
import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class DataRightsControllerTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entities;

    @Test void exportAndDeletionStayWithinTheCallerAndLeaveNoCredentialMaterial(CapturedOutput output) throws Exception {
        var owner = createUser(Role.USER);
        var other = createUser(Role.USER);
        owner.setPasswordHash(passwords.encode("Synthetic9-password"));
        userRepository.saveAndFlush(owner);
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Owner wallet', 'CASH')", owner.getId());
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Other wallet', 'CASH')", other.getId());
        jdbc.update("""
                INSERT INTO goals (user_id,name,target_amount) VALUES (?, 'Owner goal', 100.00),
                (?, 'Other goal', 200.00)
                """, owner.getId(), other.getId());
        jdbc.update("""
                INSERT INTO goal_contributions (goal_id,amount,contrib_date) SELECT id, 4.56, '2026-11-24'
                FROM goals WHERE user_id IN (?,?)
                """, owner.getId(), other.getId());
        jdbc.update("INSERT INTO device_tokens (user_id, token) VALUES (?, 'Synthetic-device-secret-9')", owner.getId());
        var login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(
                json(Map.of("email", owner.getEmail(), "password", "Synthetic9-password"))))
                .andExpect(status().isOk()).andReturn();
        String refresh = jsonMapper.readTree(login.getResponse().getContentAsString()).get("refreshToken").asString();
        String access = "Bearer " + tokenFor(owner);
        mockMvc.perform(get("/api/v1/me/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/export").header("Authorization", access))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", containsString("finora-export-")))
                .andExpect(jsonPath("$.data.accounts.length()").value(1))
                .andExpect(jsonPath("$.data.accounts[0].name").value("Owner wallet"))
                .andExpect(jsonPath("$.data.goalContributions.length()").value(1))
                .andExpect(content().string(not(containsString("Other wallet"))))
                .andExpect(content().string(not(containsString("Other goal"))))
                .andExpect(content().string(not(containsString("Synthetic-device-secret-9"))))
                .andExpect(content().string(not(containsString("password_hash"))));
        deleteAs(access, "Synthetic9-wrong").andExpect(status().isBadRequest());
        deleteAs(access, "Synthetic9-password").andExpect(status().isNoContent());
        entities.clear();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE user_id=?", Integer.class, owner.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE user_id=?", Integer.class, other.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM device_tokens WHERE user_id=?", Integer.class, owner.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", Integer.class, owner.getId()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_events WHERE action='USER_DELETED'
                AND actor_id IS NULL AND target_id IS NULL AND reason IS NULL
                """, Integer.class));
        mockMvc.perform(get("/api/v1/me").header("Authorization", access)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refresh)))).andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains("Owner wallet"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                "email", owner.getEmail(), "password", "Synthetic9-password", "fullName", "Synthetic replacement",
                "language", "EN", "over18", true, "consentVersion", "1.0")))).andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions deleteAs(String access, String password) throws Exception {
        return mockMvc.perform(delete("/api/v1/me").header("Authorization", access)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", password))));
    }
}
