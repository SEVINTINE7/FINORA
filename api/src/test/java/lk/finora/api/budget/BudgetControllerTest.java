package lk.finora.api.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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

/** T17: budgets CRUD, used/remaining/state, month edges, ownership. */
class BudgetControllerTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private User userA;
    private User userB;
    private Long accountA;
    private Long food;      // default EXPENSE
    private Long transport; // default EXPENSE
    private Long salary;    // default INCOME

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Cash', 'CASH')", userA.getId());
        accountA = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ?", Long.class, userA.getId());
        food = defaultCategory("Food");
        transport = defaultCategory("Transport");
        salary = defaultCategory("Salary");
    }

    private Long defaultCategory(String name) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = ?", Long.class, name);
    }

    private void addTransaction(Long categoryId, String type, String amount, String date) {
        jdbc.update("INSERT INTO transactions (user_id, account_id, category_id, type, amount, txn_date) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userA.getId(), accountA, categoryId, type, amount, date);
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    private Long createBudget(User user, Long categoryId, String month, String limit) throws Exception {
        String response = send(user, post("/api/v1/budgets"),
                Map.of("categoryId", categoryId, "month", month, "limitAmount", limit))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    private String budgetJson(Long id) throws Exception {
        return send(userA, get("/api/v1/budgets/" + id), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ---------- CRUD ----------

    @Test
    void createListUpdateDelete() throws Exception {
        send(userA, post("/api/v1/budgets"), Map.of("categoryId", food, "month", "2026-11", "limitAmount", "20000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.month").value("2026-11"))
                .andExpect(jsonPath("$.limitAmount").value(20000.00))
                .andExpect(jsonPath("$.used").value(0.00))
                .andExpect(jsonPath("$.state").value("OK"));
        Long id = createBudget(userA, transport, "2026-11", "5000.00");
        createBudget(userA, food, "2026-12", "20000.00");

        // Only November's budgets
        send(userA, get("/api/v1/budgets?month=2026-11"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        send(userA, put("/api/v1/budgets/" + id),
                Map.of("categoryId", transport, "month", "2026-11", "limitAmount", "6500.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitAmount").value(6500.50));

        send(userA, delete("/api/v1/budgets/" + id), null).andExpect(status().isNoContent());
        send(userA, get("/api/v1/budgets/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void secondBudgetForSameCategoryAndMonthGives409() throws Exception {
        createBudget(userA, food, "2026-11", "20000.00");
        Long transportBudget = createBudget(userA, transport, "2026-11", "5000.00");

        send(userA, post("/api/v1/budgets"), Map.of("categoryId", food, "month", "2026-11", "limitAmount", "1.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        send(userA, put("/api/v1/budgets/" + transportBudget),
                Map.of("categoryId", food, "month", "2026-11", "limitAmount", "1.00"))
                .andExpect(status().isConflict());
    }

    @Test
    void badValuesGive400() throws Exception {
        send(userA, post("/api/v1/budgets"), Map.of("categoryId", food, "month", "2026-13", "limitAmount", "100.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(userA, post("/api/v1/budgets"), Map.of("categoryId", food, "month", "2026-11", "limitAmount", "0"))
                .andExpect(status().isBadRequest());
        // No budgets on income categories
        send(userA, post("/api/v1/budgets"), Map.of("categoryId", salary, "month", "2026-11", "limitAmount", "100.00"))
                .andExpect(status().isBadRequest());
        send(userA, get("/api/v1/budgets?month=11-2026"), null).andExpect(status().isBadRequest());
        send(userA, get("/api/v1/budgets"), null).andExpect(status().isBadRequest());
    }

    // ---------- used / remaining / state ----------

    @Test
    void usedCountsOnlyThatMonthsExpensesInThatCategory() throws Exception {
        Long id = createBudget(userA, food, "2026-11", "10000.00");

        addTransaction(food, "EXPENSE", "1000.25", "2026-11-01");      // first day: counts
        addTransaction(food, "EXPENSE", "500.50", "2026-11-30");       // last day: counts
        addTransaction(food, "EXPENSE", "9999.00", "2026-10-31");      // day before: no
        addTransaction(food, "EXPENSE", "9999.00", "2026-12-01");      // day after: no
        addTransaction(transport, "EXPENSE", "9999.00", "2026-11-15"); // other category: no
        addTransaction(null, "EXPENSE", "9999.00", "2026-11-15");      // no category: no

        String json = budgetJson(id);
        assertThat(json).contains("\"used\":1500.75");
        assertThat(json).contains("\"remaining\":8499.25");
        assertThat(json).contains("\"state\":\"OK\"");
    }

    @Test
    void anotherUsersSpendingDoesNotCount() throws Exception {
        Long id = createBudget(userA, food, "2026-11", "1000.00");
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Cash', 'CASH')", userB.getId());
        Long accountB = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ?", Long.class, userB.getId());
        jdbc.update("INSERT INTO transactions (user_id, account_id, category_id, type, amount, txn_date) "
                + "VALUES (?, ?, ?, 'EXPENSE', 5000.00, '2026-11-10')", userB.getId(), accountB, food);

        assertThat(budgetJson(id)).contains("\"used\":0.00").contains("\"state\":\"OK\"");
    }

    @Test
    void leapYearFebruaryEndsOn29th() throws Exception {
        Long id = createBudget(userA, food, "2028-02", "1000.00");
        addTransaction(food, "EXPENSE", "100.00", "2028-02-29"); // counts
        addTransaction(food, "EXPENSE", "100.00", "2028-03-01"); // does not

        assertThat(budgetJson(id)).contains("\"used\":100.00");
    }

    @Test
    void stateIsNearFrom80PercentAndOverAboveTheLimit() throws Exception {
        Long id = createBudget(userA, food, "2026-11", "1000.00");

        addTransaction(food, "EXPENSE", "799.99", "2026-11-05");
        assertThat(budgetJson(id)).contains("\"state\":\"OK\"");

        addTransaction(food, "EXPENSE", "0.01", "2026-11-06");   // exactly 800.00 = 80%
        assertThat(budgetJson(id)).contains("\"state\":\"NEAR\"");

        addTransaction(food, "EXPENSE", "200.00", "2026-11-07"); // exactly 1000.00 = the limit
        assertThat(budgetJson(id)).contains("\"state\":\"NEAR\"").contains("\"remaining\":0.00");

        addTransaction(food, "EXPENSE", "250.50", "2026-11-08"); // 1250.50 = over-spent
        assertThat(budgetJson(id)).contains("\"state\":\"OVER\"").contains("\"remaining\":-250.50");
    }

    // ---------- ownership ----------

    @Test
    void otherUsersBudgetGives404() throws Exception {
        Long idOfA = createBudget(userA, food, "2026-11", "1000.00");

        send(userB, get("/api/v1/budgets/" + idOfA), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        send(userB, put("/api/v1/budgets/" + idOfA),
                Map.of("categoryId", food, "month", "2026-11", "limitAmount", "1.00"))
                .andExpect(status().isNotFound());
        send(userB, delete("/api/v1/budgets/" + idOfA), null).andExpect(status().isNotFound());
        send(userB, get("/api/v1/budgets?month=2026-11"), null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void otherUsersCategoryIsRejected() throws Exception {
        jdbc.update("INSERT INTO categories (user_id, name, kind) VALUES (?, 'B only', 'EXPENSE')", userB.getId());
        Long categoryOfB = jdbc.queryForObject("SELECT id FROM categories WHERE user_id = ?", Long.class,
                userB.getId());

        send(userA, post("/api/v1/budgets"),
                Map.of("categoryId", categoryOfB, "month", "2026-11", "limitAmount", "100.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }
}
