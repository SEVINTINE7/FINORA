package lk.finora.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

class SimulationControllerTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private User outsider;

    @BeforeEach
    void setUp() {
        owner = createUser(Role.USER);
        outsider = createUser(Role.USER);
    }

    private ResultActions send(
            User user,
            MockHttpServletRequestBuilder request,
            Object body) throws Exception {

        if (user != null) {
            request.header("Authorization", "Bearer " + tokenFor(user));
        }

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(json(body));
        }

        return mockMvc.perform(request);
    }

    private Long createGoal(User user) throws Exception {
        String response = send(
                user,
                post("/api/v1/goals"),
                Map.of(
                        "name", "Laptop savings",
                        "targetAmount", "10000.00"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return jsonMapper.readTree(response).get("id").asLong();
    }

    private Map<String, Object> scenario(Long goalId) {
        return Map.of(
                "goalId", goalId,
                "baselineMonthlySavings", "1500.00",
                "monthlyChange", "500.00",
                "categoryCuts", List.of());
    }

    @Test
    void ownedGoalProducesCorrectHypotheticalResultWithoutWrites()
            throws Exception {

        Long id = createGoal(owner);

        String before = send(
                owner, get("/api/v1/goals/" + id), null)
                .andReturn().getResponse().getContentAsString();

        Integer transactionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                Integer.class, owner.getId());

        send(owner, post("/api/v1/simulate"), scenario(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(id))
                .andExpect(jsonPath("$.remaining").value(10000.00))
                .andExpect(jsonPath("$.hypotheticalMonthlySavings").value(2000.00))
                .andExpect(jsonPath("$.monthsRequired").value(5))
                .andExpect(jsonPath("$.status").value("POSSIBLE"))
                .andExpect(jsonPath("$.assumptions").isArray());

        String after = send(
                owner, get("/api/v1/goals/" + id), null)
                .andReturn().getResponse().getContentAsString();

        Integer transactionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                Integer.class, owner.getId());

        assertThat(jsonMapper.readTree(after))
                .isEqualTo(jsonMapper.readTree(before));

        assertThat(transactionsAfter).isEqualTo(transactionsBefore);
    }

    @Test
    void anotherUsersGoalReturns404() throws Exception {
        Long id = createGoal(owner);

        send(outsider, post("/api/v1/simulate"), scenario(id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        Long id = createGoal(owner);

        send(null, post("/api/v1/simulate"), scenario(id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotAccessPersonalSimulation() throws Exception {
        Long id = createGoal(owner);
        User staff = createUser(Role.STAFF);

        send(staff, post("/api/v1/simulate"), scenario(id))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingBaselineIsRejected() throws Exception {
        Long id = createGoal(owner);

        Map<String, Object> body = new HashMap<>(scenario(id));
        body.remove("baselineMonthlySavings");

        send(owner, post("/api/v1/simulate"), body)
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelledGoalCannotBeSimulated() throws Exception {
        Long id = createGoal(owner);

        send(owner, put("/api/v1/goals/" + id), Map.of(
                "name", "Laptop savings",
                "targetAmount", "10000.00",
                "cancelled", true))
                .andExpect(status().isOk());

        send(owner, post("/api/v1/simulate"), scenario(id))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroMonthlySavingsDoesNotInventFinishDate() throws Exception {
        Long id = createGoal(owner);

        send(owner, post("/api/v1/simulate"), Map.of(
                "goalId", id,
                "baselineMonthlySavings", "0.00",
                "monthlyChange", "0.00",
                "categoryCuts", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_MONTHLY_CAPACITY"))
                .andExpect(jsonPath("$.monthsRequired").doesNotExist())
                .andExpect(jsonPath("$.hypotheticalFinishDate").doesNotExist());
    }

    @Test
    void unknownCategoryCannotBeUsedForSavingsReduction()
            throws Exception {
        Long id = createGoal(owner);

        send(owner, post("/api/v1/simulate"), Map.of(
                "goalId", id,
                "baselineMonthlySavings", "1500.00",
                "monthlyChange", "0.00",
                "categoryCuts", List.of(Map.of(
                        "categoryId", Long.MAX_VALUE,
                        "monthlyReduction", "100.00"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingCutsAreRejected() throws Exception {
        Long id = createGoal(owner);
        Map<String, Object> body = new HashMap<>(scenario(id));
        body.remove("categoryCuts");

        send(owner, post("/api/v1/simulate"), body)
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativeBaselineIsRejected() throws Exception {
        Long id = createGoal(owner);
        Map<String, Object> body = new HashMap<>(scenario(id));
        body.put("baselineMonthlySavings", "-1.00");

        send(owner, post("/api/v1/simulate"), body)
                .andExpect(status().isBadRequest());
    }

    @Test
    void nullCategoryCutIsRejected() throws Exception {
        Long id = createGoal(owner);
        Map<String, Object> body = new HashMap<>(scenario(id));
        body.put("categoryCuts",
                java.util.Collections.singletonList(null));

        send(owner, post("/api/v1/simulate"), body)
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryReductionCannotExceedRecordedSpending()
            throws Exception {
        Long id = createGoal(owner);

        // Active seeded default expense category, visible to this user.
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM categories "
                + "WHERE user_id IS NULL AND active = 1 "
                + "AND kind = 'EXPENSE' "
                + "ORDER BY id LIMIT 1",
                Long.class);

        // Newly created owner has no transactions in the previous month.
        send(owner, post("/api/v1/simulate"), Map.of(
                "goalId", id,
                "baselineMonthlySavings", "1000.00",
                "monthlyChange", "0.00",
                "categoryCuts", List.of(Map.of(
                        "categoryId", categoryId,
                        "monthlyReduction", "100.00"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryCutsRejectForeignAndInactiveReferencesWithoutWrites() throws Exception {
        long goal = createGoal(owner);
        long otherGoal = createGoal(outsider);
        var previousMonth = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Colombo")).minusMonths(1);
        for (User user : List.of(owner, outsider)) {
            jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?, 'Synthetic wallet', 'CASH')",
                    user.getId());
            jdbc.update("INSERT INTO categories(user_id,name,kind) VALUES (?, 'Synthetic cut', 'EXPENSE')",
                    user.getId());
            jdbc.update("""
                    INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date)
                    SELECT a.user_id,a.id,c.id,'EXPENSE',200,? FROM accounts a
                    JOIN categories c ON c.user_id=a.user_id
                    WHERE a.user_id=? AND a.name='Synthetic wallet' AND c.name='Synthetic cut'
                    """, previousMonth.atDay(1), user.getId());
        }
        Long own = jdbc.queryForObject("SELECT id FROM categories WHERE user_id=? AND name='Synthetic cut'",
                Long.class, owner.getId());
        Long foreign = jdbc.queryForObject("SELECT id FROM categories WHERE user_id=? AND name='Synthetic cut'",
                Long.class, outsider.getId());
        jdbc.update("INSERT INTO categories(user_id,name,kind,active) VALUES (?, 'Synthetic inactive cut', 'EXPENSE', FALSE)",
                owner.getId());
        Long inactive = jdbc.queryForObject("SELECT id FROM categories WHERE user_id=? AND name='Synthetic inactive cut'",
                Long.class, owner.getId());
        String txScope = "SELECT * FROM transactions WHERE user_id IN (?,?) ORDER BY id";
        String goalScope = "SELECT * FROM goals WHERE user_id IN (?,?) ORDER BY id";
        var transactions = jdbc.queryForList(txScope, owner.getId(), outsider.getId());
        var goals = jdbc.queryForList(goalScope, owner.getId(), outsider.getId());

        send(owner, post("/api/v1/simulate"), scenarioWithCut(goal, own))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hypotheticalMonthlySavings").value(2100.00));
        for (long categoryId : new long[]{foreign, inactive, Long.MAX_VALUE}) {
            send(owner, post("/api/v1/simulate"), scenarioWithCut(goal, categoryId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Category not found"));
        }
        send(outsider, post("/api/v1/simulate"), scenarioWithCut(otherGoal, foreign))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hypotheticalMonthlySavings").value(2100.00));
        assertThat(jdbc.queryForList(txScope, owner.getId(), outsider.getId())).isEqualTo(transactions);
        assertThat(jdbc.queryForList(goalScope, owner.getId(), outsider.getId())).isEqualTo(goals);
    }

    private Map<String, Object> scenarioWithCut(long goal, long category) {
        var request = new HashMap<>(scenario(goal));
        request.put("categoryCuts", List.of(Map.of("categoryId", category, "monthlyReduction", "100.00")));
        return request;
    }

}