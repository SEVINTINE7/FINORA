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

    @Test void exportIncludesEveryOwnedDataFamilyAndExcludesForeignRows(CapturedOutput output) throws Exception {
        var owner = createUser(Role.USER);
        var other = createUser(Role.USER);
        seedExportRows(owner.getId(), "ExportOwnerMarker", "2026-01");
        seedExportRows(other.getId(), "ExportForeignMarker", "2026-02");

        mockMvc.perform(get("/api/v1/me/export")).andExpect(status().isUnauthorized());
        String access = "Bearer " + tokenFor(owner);
        var response = mockMvc.perform(get("/api/v1/me/export").header("Authorization", access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value(owner.getId().intValue()))
                .andExpect(content().string(not(containsString("ExportForeignMarker"))))
                .andExpect(content().string(not(containsString(other.getEmail()))))
                .andReturn().getResponse();
        var data = jsonMapper.readTree(response.getContentAsString()).get("data");
        var fields = Map.ofEntries(
                Map.entry("consents", "version"), Map.entry("accounts", "name"),
                Map.entry("categories", "name"), Map.entry("transactions", "merchant"),
                Map.entry("budgets", "month"), Map.entry("bills", "name"),
                Map.entry("billPayments", "month"), Map.entry("goals", "name"),
                Map.entry("goalContributions", "contrib_date"), Map.entry("insights", "type"),
                Map.entry("healthScores", "month"), Map.entry("scoreInputs", "month"),
                Map.entry("history", "month"), Map.entry("advice", "month"),
                Map.entry("ratings", "rating"), Map.entry("tickets", "subject"),
                Map.entry("ticketReplies", "body"), Map.entry("smsDiagnostics", "report_day"));
        var values = Map.ofEntries(
                Map.entry("consents", "2026-01"), Map.entry("budgets", "2026-01"),
                Map.entry("billPayments", "2026-01"), Map.entry("goalContributions", "2026-01-15"),
                Map.entry("healthScores", "2026-01"), Map.entry("scoreInputs", "2026-01"),
                Map.entry("history", "2026-01"), Map.entry("advice", "2026-01"),
                Map.entry("ratings", "3"), Map.entry("smsDiagnostics", "2026-01-15"));
        assertEquals(fields.size(), data.size());
        fields.forEach((family, field) -> {
            var rows = data.path(family);
            assertEquals(1, rows.size(), family);
            assertEquals(values.getOrDefault(family, "ExportOwnerMarker"), rows.path(0).path(field).asText(), family);
        });
        assertEquals("2026-01-15", data.path("transactions").path(0).path("txn_date").asText());
        assertEquals("2026-01-15", data.path("goals").path(0).path("target_date").asText());
        assertEquals("2026-01-15", data.path("scoreInputs").path(0).path("reserve_as_of").asText());
        org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains("ExportOwnerMarker"));
        org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains("ExportForeignMarker"));
    }

    private void seedExportRows(long user, String marker, String month) {
        String day = month + "-15";
        jdbc.update("INSERT INTO consents (user_id, version, over18) VALUES (?, ?, TRUE)", user, month);
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, ?, 'CASH')", user, marker);
        jdbc.update("INSERT INTO categories (user_id, name, kind) VALUES (?, ?, 'EXPENSE')", user, marker);
        jdbc.update("""
                INSERT INTO transactions (user_id, account_id, type, amount, txn_date, merchant)
                SELECT ?, id, 'EXPENSE', 12.34, ?, ? FROM accounts WHERE user_id = ?
                """, user, day, marker, user);
        jdbc.update("""
                INSERT INTO budgets (user_id, category_id, month, limit_amount)
                SELECT ?, id, ?, 50.25 FROM categories WHERE user_id = ?
                """, user, month, user);
        jdbc.update("INSERT INTO bills (user_id, name, amount, due_day) VALUES (?, ?, 10.12, 15)", user, marker);
        jdbc.update("""
                INSERT INTO bill_payments (bill_id, month) SELECT id, ? FROM bills WHERE user_id = ?
                """, month, user);
        jdbc.update("INSERT INTO goals (user_id, name, target_amount, target_date) VALUES (?, ?, 100.01, ?)", user, marker, day);
        jdbc.update("""
                INSERT INTO goal_contributions (goal_id, amount, contrib_date)
                SELECT id, 11.22, ? FROM goals WHERE user_id = ?
                """, day, user);
        jdbc.update("INSERT INTO insights (user_id, month, type, facts) VALUES (?, ?, ?, '{}')", user, month, marker);
        jdbc.update("INSERT INTO health_scores (user_id, month, total, parts) VALUES (?, ?, 77, '{}')", user, month);
        jdbc.update("""
                INSERT INTO score_inputs (user_id, month, emergency_reserve, reserve_as_of, confirmed_at)
                VALUES (?, ?, 42.50, ?, CURRENT_TIMESTAMP)
                """, user, month, day);
        jdbc.update("""
                INSERT INTO monthly_history (user_id, month, income, expense, insights) VALUES (?, ?, 20.00, 12.34, '[]')
                """, user, month);
        jdbc.update("INSERT INTO advice (user_id, month, source, mode, body) VALUES (?, ?, 'RULE', 'V1', '{}')", user, month);
        jdbc.update("""
                INSERT INTO advice_ratings (advice_id, user_id, rating)
                SELECT id, ?, ? FROM advice WHERE user_id = ?
                """, user, month.endsWith("01") ? 3 : 4, user);
        jdbc.update("INSERT INTO support_tickets (user_id, subject, body) VALUES (?, ?, ?)", user, marker, marker);
        jdbc.update("""
                INSERT INTO ticket_replies (ticket_id, body) SELECT id, ? FROM support_tickets WHERE user_id = ?
                """, marker, user);
        jdbc.update("""
                INSERT INTO sms_diagnostic_reports (user_id, bank_code, format_version, error_code, report_day)
                VALUES (?, 'NSB', 1, 'UNKNOWN_FORMAT', ?)
                """, user, day);
    }

    private org.springframework.test.web.servlet.ResultActions deleteAs(String access, String password) throws Exception {
        return mockMvc.perform(delete("/api/v1/me").header("Authorization", access)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", password))));
    }
}
