package lk.finora.api.admin;

import java.math.BigDecimal;
import java.util.Map;
import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminDependencyControllerTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired PasswordEncoder passwords;

    @Test void userManagementRevokesCredentialsAndAuditsWithoutExposingFinance() throws Exception {
        var user = createUser(Role.USER);
        user.setPasswordHash(passwords.encode("Synthetic9-password"));
        userRepository.saveAndFlush(user);
        var staff = createUser(Role.STAFF);
        String admin = tokenFor(createUser(Role.ADMIN));
        String credentials = json(Map.of("email", user.getEmail(), "password", "Synthetic9-password"));
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials)).andExpect(status().isOk()).andReturn();
        var tokens = jsonMapper.readTree(result.getResponse().getContentAsString());
        String old = tokens.get("accessToken").asString();
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + old))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").param("q", user.getEmail())
                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].length()").value(5))
                .andExpect(jsonPath("$[0].email").value(user.getEmail()));
        update(admin, staff.getId(), "DISABLED").andExpect(status().isNotFound());
        update(admin, user.getId(), "DISABLED").andExpect(status().isNoContent());
        entities.clear();
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isForbidden());
        update(admin, user.getId(), "ACTIVE").andExpect(status().isNoContent());
        entities.clear();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + old))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", tokens.get("refreshToken").asString()))))
                .andExpect(status().isUnauthorized());
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_events WHERE target_type='USER' AND target_id=? AND reason='POLICY'
                """, Integer.class, user.getId()));
        for (String resource : java.util.List.of("transactions", "budgets", "goals")) {
            mockMvc.perform(get("/api/v1/" + resource).header("Authorization", "Bearer " + admin))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void defaultNamesAreUniqueAndDeletingInUseCategoryOnlyDeactivatesIt() throws Exception {
        var user = createUser(Role.USER);
        String admin = tokenFor(createUser(Role.ADMIN));
        String bearer = "Bearer " + admin;
        String data = json(Map.of("kind", "EXPENSE", "name", "Synthetic defaults", "nameSi", "පරීක්ෂණ"));
        var path = "/api/v1/admin/categories";
        var result = mockMvc.perform(post(path).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(data)).andExpect(status().isCreated()).andReturn();
        long id = jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(data)).andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/categories/" + id).header("Authorization", "Bearer " + tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Blocked"))))
                .andExpect(status().isForbidden());
        jdbc.update("INSERT INTO budgets (user_id, category_id, month, limit_amount) VALUES (?, ?, '2026-11', ?)",
                user.getId(), id, new BigDecimal("12.34"));
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Synthetic cash', 'CASH')", user.getId());
        jdbc.update("""
                INSERT INTO transactions (user_id, account_id, category_id, type, amount, txn_date)
                SELECT user_id, id, ?, 'EXPENSE', 2.34, '2026-11-24' FROM accounts WHERE user_id = ?
                """, id, user.getId());
        mockMvc.perform(delete(path + "/" + id).header("Authorization", bearer)).andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM budgets WHERE category_id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE category_id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT active FROM categories WHERE id = ?", Integer.class, id));
    }

    private org.springframework.test.web.servlet.ResultActions update(String token, long id, String state)
            throws Exception {
        return mockMvc.perform(put("/api/v1/admin/users/" + id + "/status").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", state, "reason", "POLICY"))));
    }
}
