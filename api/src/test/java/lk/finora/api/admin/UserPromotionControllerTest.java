package lk.finora.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import lk.finora.api.support.EvaluationDatasetSeeder;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserPromotionControllerTest extends IntegrationTest {
    private static final Map<String,String> CONFIRM = Map.of("confirm", "DELETE_FINANCIAL_DATA");
    private static final String PASSWORD = "Synthetic2026";
    private static final java.util.List<String> FINANCIAL_TABLES = java.util.List.of(
            "accounts", "transactions", "budgets", "bills", "goals", "score_inputs", "categories",
            "health_scores", "insights", "advice", "advice_ratings", "advice_generation_leases",
            "advice_verification_events", "monthly_history", "history_recovery_retry", "alert_events", "device_tokens");
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired jakarta.persistence.EntityManager entities;

    @Test void erasesTargetOnlyAndAllowsNewStaffLogin() throws Exception {
        var seed = new EvaluationDatasetSeeder(jdbc).seed();
        long target = seed.users().get("worker"), other = seed.users().get("irregular");
        for (long id : new long[]{target, other}) seedDerivedRows(id);
        var otherSnapshot = financialCounts(other);
        assertThat(financialCounts(target).values()).allMatch(count -> count > 0);
        assertThat(otherSnapshot.values()).allMatch(count -> count > 0);
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, target);
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?", passwords.encode(PASSWORD), target);
        jdbc.update("INSERT INTO consents(user_id,version,over18,accepted_at) VALUES (?,'1.0',TRUE,UTC_TIMESTAMP(6))", target);
        var identity = jdbc.queryForMap("SELECT email,full_name,password_hash FROM users WHERE id=?", target);
        var old = login(email);
        long admin = createUser(Role.ADMIN).getId();
        var res = promote(jwtService.createAccessToken(admin, "ADMIN"), target, CONFIRM)
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        assertThat(jsonMapper.readTree(res.getResponse().getContentAsString()).propertyNames())
                .containsExactlyInAnyOrder("id","email","fullName","status","pending");
        assertThat(financialCounts(target).values()).allMatch(count -> count == 0);
        assertThat(financialCounts(other)).isEqualTo(otherSnapshot);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_contributions WHERE goal_id=?", Integer.class,
                seed.goals().get("worker"))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_contributions WHERE goal_id=?", Integer.class,
                seed.goals().get("irregular"))).isPositive();
        assertThat(count("transactions",other)).isEqualTo(402);
        assertThat(jdbc.queryForMap("SELECT email,full_name,password_hash FROM users WHERE id=?",target)).isEqualTo(identity);
        assertThat(jdbc.queryForMap("SELECT actor_id,target_id,reason FROM audit_events WHERE action='USER_PROMOTED_TO_STAFF' AND target_id=?",target))
                .containsEntry("actor_id",admin).containsEntry("target_id",target).containsEntry("reason",null);
        entities.clear();
        finance(old.get("accessToken").asString()).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json(Map.of("refreshToken",old.get("refreshToken").asString()))))
                .andExpect(status().isUnauthorized());
        var staff = login(email);
        assertThat(staff.get("role").asString()).isEqualTo("STAFF");
        finance(staff.get("accessToken").asString()).andExpect(status().isForbidden());
    }

    @Test void rejectsNonAdminsInvalidConfirmationAndInvalidTargets() throws Exception {
        var user = createUser(Role.USER);
        var admin = createUser(Role.ADMIN);
        var staff = createUser(Role.STAFF);
        var disabled = createUser(Role.USER);
        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=?",disabled.getId());
        entities.clear();
        long id = user.getId();
        promote(null,id,CONFIRM).andExpect(status().isUnauthorized());
        promote(tokenFor(user),id,CONFIRM).andExpect(status().isForbidden());
        promote(tokenFor(staff),id,CONFIRM).andExpect(status().isForbidden());
        promote(tokenFor(disabled),id,CONFIRM).andExpect(status().isForbidden());
        String actor = tokenFor(admin);
        promote(actor,id,Map.of()).andExpect(status().isBadRequest());
        promote(actor,id,Map.of("confirm","no")).andExpect(status().isBadRequest());
        promote(actor,Long.MAX_VALUE,CONFIRM).andExpect(status().isNotFound());
        promote(actor,admin.getId(),CONFIRM).andExpect(status().isConflict());
        promote(actor,staff.getId(),CONFIRM).andExpect(status().isConflict());
        promote(actor,disabled.getId(),CONFIRM).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id=?",String.class,id)).isEqualTo("USER");
    }

    private void seedDerivedRows(long id) {
        jdbc.update("INSERT INTO categories(user_id,kind,name,need_or_want) VALUES (?,'EXPENSE','Synthetic food','NEED')", id);
        jdbc.update("INSERT INTO insights(user_id,month,type,facts) VALUES (?,'2026-11','HIGH_WANTS','{}')", id);
        jdbc.update("INSERT INTO health_scores(user_id,month,total,parts) VALUES (?,'2026-11',70,'{}')", id);
        jdbc.update("INSERT INTO advice(user_id,month,source,mode,body) VALUES (?,'2026-11','RULE','V1','{}')", id);
        jdbc.update("INSERT INTO advice_ratings(advice_id,user_id,rating) SELECT id,user_id,4 FROM advice WHERE user_id=?", id);
        jdbc.update("INSERT INTO monthly_history(user_id,month,income,expense,insights) VALUES (?,'2026-11',60000,40000,'[]')", id);
        jdbc.update("INSERT INTO history_recovery_retry(user_id,month,retry_at) VALUES (?,'2026-11',UTC_TIMESTAMP(6))", id);
        jdbc.update("INSERT INTO advice_generation_leases(user_id,month,lease_token,expires_at) VALUES (?,'2026-11','synthetic',UTC_TIMESTAMP(6))", id);
        jdbc.update("INSERT INTO advice_verification_events(user_id,month,provider,verified,reason) VALUES (?,'2026-11','GROQ',FALSE,'TIMEOUT')", id);
        jdbc.update("INSERT INTO alert_events(user_id,type,ref_id,period) VALUES (?,'BILL_DUE',1,'2026-11')", id);
        jdbc.update("INSERT INTO device_tokens(user_id,token) VALUES (?,?)", id, "synthetic-" + id);
    }

    private Map<String, Integer> financialCounts(long owner) {
        var counts = new java.util.LinkedHashMap<String, Integer>();
        for (String table : FINANCIAL_TABLES) counts.put(table, count(table, owner));
        counts.put("bill_payments", jdbc.queryForObject("""
                SELECT COUNT(*) FROM bill_payments p JOIN bills b ON b.id=p.bill_id WHERE b.user_id=?
                """, Integer.class, owner));
        counts.put("goal_contributions", jdbc.queryForObject("""
                SELECT COUNT(*) FROM goal_contributions c JOIN goals g ON g.id=c.goal_id WHERE g.user_id=?
                """, Integer.class, owner));
        return counts;
    }

    private int count(String table,long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id=?",Integer.class,id);
    }

    private org.springframework.test.web.servlet.ResultActions promote(String token,long id,Map<String,String> body) throws Exception {
        var req = post("/api/v1/admin/users/"+id+"/promote-to-staff").contentType("application/json").content(json(body));
        if (token != null) req.header("Authorization","Bearer "+token);
        return mockMvc.perform(req);
    }

    private tools.jackson.databind.JsonNode login(String email) throws Exception {
        return jsonMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions finance(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts").header("Authorization","Bearer "+token));
    }
}
