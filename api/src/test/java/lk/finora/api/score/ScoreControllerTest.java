package lk.finora.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

class ScoreControllerTest extends IntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    private User user;
    private Long account;

    @BeforeEach
    void setUp() {
        user = createUser(Role.USER);
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?,'Test','CASH')", user.getId());
        account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, user.getId());
    }

    private Long category(String name) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name=?", Long.class, name);
    }
    private void txn(String type, String amount, String category) {
        jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date) VALUES (?,?,?,?,?,'2026-11-10')",
                user.getId(), account, category(category), type, amount);
    }
    private void month() {
        txn("INCOME", "100000.00", "Salary");
        txn("EXPENSE", "60000.00", "Food");
        txn("EXPENSE", "30000.00", "Shopping");
        txn("EXPENSE", "5000.00", "Savings");
    }
    private Map<String, Object> declarations() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("emergencyReserve", "30000.00"); r.put("reserveAsOf", "2026-11-20");
        r.put("noApplicableDebt", true); r.put("noApplicableGoals", true);
        return r;
    }
    private ResultActions request(User caller, MockHttpServletRequestBuilder r, Object body) throws Exception {
        r.header("Authorization", "Bearer " + tokenFor(caller));
        if (body != null) r.contentType(MediaType.APPLICATION_JSON).content(json(body));
        return mockMvc.perform(r);
    }
    private void declare(Map<String, Object> body) throws Exception {
        request(user, put("/api/v1/score-inputs/2026-11"), body).andExpect(status().isOk());
    }

    @Test
    void missingRecordsReturnUnavailableAndNeverInventApplicability() throws Exception {
        request(user, get("/api/v1/score?month=2026-11"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").isEmpty())
                .andExpect(jsonPath("$.missingWeight").value(100));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM health_scores WHERE user_id=?", Integer.class, user.getId())).isZero();
    }

    @Test
    void explicitDeclarationsGiveNormalizedScoreAndOnlyOneInputRow() throws Exception {
        month();
        declare(declarations());
        declare(declarations());
        request(user, get("/api/v1/score?month=2026-11"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(92))
                .andExpect(jsonPath("$.scoredWeight").value(65))
                .andExpect(jsonPath("$.notApplicableWeight").value(35))
                .andExpect(jsonPath("$.parts[4].reasonKey").value("EMERGENCY_USER_DECLARED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_inputs WHERE user_id=?", Integer.class, user.getId())).isEqualTo(1);
        String jsonBody = request(user, get("/api/v1/score-inputs/2026-11"), null)
                .andExpect(jsonPath("$.confirmedAt").isString()).andReturn().getResponse().getContentAsString();
        assertThat(jsonBody).containsPattern("\"confirmedAt\":\"[^\"]+Z\"");
    }

    @Test
    void scoreInputsAndComputedFactsAreOwnerIsolated() throws Exception {
        month(); declare(declarations());
        User other = createUser(Role.USER);
        request(other, get("/api/v1/score-inputs/2026-11"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.emergencyReserve").isEmpty());
        request(other, get("/api/v1/score?month=2026-11"), null)
                .andExpect(jsonPath("$.total").isEmpty()).andExpect(jsonPath("$.missingWeight").value(100));
        request(other, put("/api/v1/score-inputs/2026-11"), Map.of("noApplicableDebt", true))
                .andExpect(status().isOk());
        request(user, get("/api/v1/score-inputs/2026-11"), null)
                .andExpect(jsonPath("$.emergencyReserve").value(30000.00));
    }

    @Test
    void debtIsMarkedStatusNotDoubleCountedExpenseAndGoalsRespectDateCutoff() throws Exception {
        month();
        jdbc.update("INSERT INTO bills(user_id,name,amount,due_day,category_id) VALUES (?,'Loan',15000,15,?)", user.getId(), category("Loan payment"));
        Long bill = jdbc.queryForObject("SELECT id FROM bills WHERE user_id=?", Long.class, user.getId());
        jdbc.update("INSERT INTO bill_payments(bill_id,month) VALUES (?,'2026-11')", bill);
        jdbc.update("INSERT INTO goals(user_id,name,target_amount,target_date) VALUES (?,'Goal',100000,'2026-11-01')", user.getId());
        Long goal = jdbc.queryForObject("SELECT id FROM goals WHERE user_id=?", Long.class, user.getId());
        jdbc.update("INSERT INTO goal_contributions(goal_id,amount,contrib_date) VALUES (?,50000,'2026-11-10'),(?,50000,'2026-12-01')", goal, goal);
        Map<String, Object> input = declarations(); input.put("noApplicableDebt", false); input.put("noApplicableGoals", false);
        declare(input);
        request(user, get("/api/v1/score?month=2026-11"), null)
                .andExpect(jsonPath("$.total").value(88))
                .andExpect(jsonPath("$.parts[0].facts.consumption").value(90000.00))
                .andExpect(jsonPath("$.parts[1].points").value(20.00))
                .andExpect(jsonPath("$.parts[1].reasonKey").value("DEBT_PAID_MARKER_PROXY"))
                .andExpect(jsonPath("$.parts[2].points").value(7.50));
        jdbc.update("DELETE FROM bill_payments WHERE bill_id=?", bill);
        request(user, get("/api/v1/score?month=2026-11"), null).andExpect(jsonPath("$.total").value(68));
    }

    @Test
    void missingReserveKeepsTotalNullEvenWithExplicitNoDebtAndGoals() throws Exception {
        month();
        declare(Map.of("noApplicableDebt", true, "noApplicableGoals", true));
        request(user, get("/api/v1/score?month=2026-11"), null)
                .andExpect(jsonPath("$.total").isEmpty()).andExpect(jsonPath("$.missingWeight").value(10));
    }

    @Test
    void noDebtDeclarationCannotHideLoanTransactions() throws Exception {
        month(); txn("EXPENSE", "1000.00", "Loan payment");
        request(user, put("/api/v1/score-inputs/2026-11"), declarations()).andExpect(status().isBadRequest());
        Map<String, Object> input = declarations(); input.put("noApplicableDebt", false);
        declare(input);
        request(user, get("/api/v1/score?month=2026-11"), null)
                .andExpect(jsonPath("$.total").isEmpty()).andExpect(jsonPath("$.parts[1].status").value("UNAVAILABLE"));
    }

    @Test
    void laterRecordsInvalidateContradictoryNoGoalsDeclaration() throws Exception {
        month(); declare(declarations());
        jdbc.update("INSERT INTO goals(user_id,name,target_amount) VALUES (?,'New',1000)", user.getId());
        request(user, get("/api/v1/score?month=2026-11"), null)
                .andExpect(jsonPath("$.total").isEmpty()).andExpect(jsonPath("$.parts[2].status").value("UNAVAILABLE"));
        request(user, put("/api/v1/score-inputs/2026-11"), declarations()).andExpect(status().isBadRequest());
    }

    @Test
    void reserveMustBeNonnegativeExactMoneyAndDatedWithinMonth() throws Exception {
        for (Map<String, Object> bad : java.util.List.<Map<String, Object>>of(
                Map.of("emergencyReserve", "-1.00", "reserveAsOf", "2026-11-20"),
                Map.of("emergencyReserve", "1.001", "reserveAsOf", "2026-11-20"),
                Map.of("emergencyReserve", "1.00"), Map.of("reserveAsOf", "2026-11-20"),
                Map.of("emergencyReserve", "1.00", "reserveAsOf", "2026-10-20"),
                Map.of("emergencyReserve", "1.00", "reserveAsOf", "2099-11-20"))) {
            request(user, put("/api/v1/score-inputs/2026-11"), bad).andExpect(status().isBadRequest());
        }
    }

    @Test
    void futureCreatedPlansAreNotEligibleForEarlierMonth() throws Exception {
        month();
        jdbc.update("INSERT INTO goals(user_id,name,target_amount,created_at) VALUES (?,'Future',1000,'2026-12-01')", user.getId());
        jdbc.update("INSERT INTO bills(user_id,name,amount,due_day,category_id,created_at) VALUES (?,'Future loan',1000,15,?,'2026-12-01')",
                user.getId(), category("Loan payment"));
        declare(declarations());
        request(user, get("/api/v1/score?month=2026-11"), null)
                .andExpect(jsonPath("$.notApplicableWeight").value(35)).andExpect(jsonPath("$.total").value(92));
    }

    @Test
    void eachGoalIsCappedBeforeAggregationAndCancellationIsExcluded() throws Exception {
        month();
        jdbc.update("INSERT INTO goals(user_id,name,target_amount) VALUES (?,'Small',100),(?,'Large',100000)", user.getId(), user.getId());
        Long small = jdbc.queryForObject("SELECT id FROM goals WHERE user_id=? AND name='Small'", Long.class, user.getId());
        jdbc.update("INSERT INTO goal_contributions(goal_id,amount,contrib_date) VALUES (?,1000,'2026-11-10')", small);
        Map<String, Object> input = declarations(); input.put("noApplicableGoals", false); declare(input);
        request(user, get("/api/v1/score?month=2026-11"), null).andExpect(jsonPath("$.parts[2].points").value(0.01));
        jdbc.update("UPDATE goals SET status='CANCELLED' WHERE user_id=? AND name='Large'", user.getId());
        request(user, get("/api/v1/score?month=2026-11"), null).andExpect(jsonPath("$.parts[2].points").value(15.00));
    }

    @Test
    void invalidMonthAndMissingMonthAreRejected() throws Exception {
        for (String value : new String[] {"2026-13", "abc", "0000-01"}) {
            request(user, get("/api/v1/score").param("month", value), null).andExpect(status().isBadRequest());
            request(user, get("/api/v1/score-inputs/" + value), null).andExpect(status().isBadRequest());
        }
        request(user, get("/api/v1/score"), null).andExpect(status().isBadRequest());
    }

    @Test
    void anonymousAdminAndStaffCannotReadOrWriteFinancialScores() throws Exception {
        mockMvc.perform(get("/api/v1/score?month=2026-11")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/score-inputs/2026-11").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        for (Role role : new Role[] {Role.ADMIN, Role.STAFF}) {
            User caller = createUser(role);
            request(caller, get("/api/v1/score?month=2026-11"), null).andExpect(status().isForbidden());
            request(caller, get("/api/v1/score-inputs/2026-11"), null).andExpect(status().isForbidden());
            request(caller, put("/api/v1/score-inputs/2026-11"), declarations()).andExpect(status().isForbidden());
        }
    }
}
