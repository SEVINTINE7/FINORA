package lk.finora.api.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.HashMap;
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

/** T22.2-T22.3: GET /insights recalculates, saves to the insights table, and only uses the caller's data. */
class InsightControllerTest extends IntegrationTest {

    private static final String NOV = "/api/v1/insights?month=2026-11";

    @Autowired
    private JdbcTemplate jdbc;

    private User userA;
    private User userB;
    private Long accountA;
    private Long accountB;
    private Long salary;   // INCOME
    private Long shopping; // EXPENSE, WANT
    private Long food;     // EXPENSE, NEED

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
        accountA = createAccount(userA);
        accountB = createAccount(userB);
        salary = categoryId("Salary", "INCOME");
        shopping = categoryId("Shopping", "EXPENSE");
        food = categoryId("Food", "EXPENSE");
    }

    private Long categoryId(String name, String kind) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = ? AND kind = ?",
                Long.class, name, kind);
    }

    private Long createAccount(User user) {
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Cash', 'CASH')", user.getId());
        return jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ?", Long.class, user.getId());
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    private void record(User user, Long accountId, Long categoryId, String type, String amount, String date)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("accountId", accountId);
        body.put("categoryId", categoryId);
        body.put("type", type);
        body.put("amount", amount);
        body.put("txnDate", date);
        body.put("merchant", "Some Shop");
        send(user, post("/api/v1/transactions"), body).andExpect(status().isCreated());
    }

    private int savedRows(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM insights WHERE user_id = ? AND month = '2026-11'",
                Integer.class, user.getId());
    }

    @Test
    void findsPatternsAndSavesThem() throws Exception {
        record(userA, accountA, salary, "INCOME", "10000.00", "2026-11-02");   // Monday, payday 2-4
        record(userA, accountA, shopping, "EXPENSE", "6000.00", "2026-11-03"); // want, on payday

        send(userA, get(NOV), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("HIGH_WANTS"))
                .andExpect(jsonPath("$[0].month").value("2026-11"))
                .andExpect(jsonPath("$[0].from").value("2026-11-01"))
                .andExpect(jsonPath("$[0].to").value("2026-11-30"))
                .andExpect(jsonPath("$[0].facts.wants").value(6000.00))
                .andExpect(jsonPath("$[0].facts.income").value(10000.00))
                .andExpect(jsonPath("$[0].facts.wantsRatio").value(0.6))
                .andExpect(jsonPath("$[1].type").value("PAYDAY_SPENDING"))
                .andExpect(jsonPath("$[1].facts.paydayShare").value(1.0));

        assertThat(savedRows(userA)).isEqualTo(2);
        // Facts are stored as JSON numbers only; the merchant name never goes in.
        // (MySQL JSON keeps 6000.00 as 6000.0, so compare the value, not the text.)
        String facts = jdbc.queryForObject("SELECT facts FROM insights WHERE user_id = ? AND type = 'HIGH_WANTS'",
                String.class, userA.getId());
        assertThat(facts).doesNotContain("Some Shop");
        BigDecimal wants = jdbc.queryForObject("SELECT CAST(JSON_EXTRACT(facts, '$.wants') AS DECIMAL(15,2))"
                + " FROM insights WHERE user_id = ? AND type = 'HIGH_WANTS'", BigDecimal.class, userA.getId());
        assertThat(wants).isEqualByComparingTo("6000.00");
    }

    @Test
    void everyGetRecalculatesAndReplaces() throws Exception {
        record(userA, accountA, salary, "INCOME", "10000.00", "2026-11-02");
        record(userA, accountA, shopping, "EXPENSE", "6000.00", "2026-11-03");

        send(userA, get(NOV), null).andExpect(jsonPath("$.length()").value(2));
        send(userA, get(NOV), null).andExpect(jsonPath("$.length()").value(2));
        assertThat(savedRows(userA)).isEqualTo(2); // not 4

        // A big food expense on a Friday: payday share drops to 6000 / 106000 = 5.66%
        record(userA, accountA, food, "EXPENSE", "100000.00", "2026-11-20");
        send(userA, get(NOV), null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("HIGH_WANTS"));
        assertThat(savedRows(userA)).isEqualTo(1);
    }

    @Test
    void emptyMonthGivesEmptyList() throws Exception {
        send(userA, get(NOV), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(savedRows(userA)).isEqualTo(0);
    }

    @Test
    void badOrMissingMonthGives400() throws Exception {
        send(userA, get("/api/v1/insights?month=2026-13"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(userA, get("/api/v1/insights"), null).andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejectedWithoutSavingInsights() throws Exception {
        mockMvc.perform(get(NOV)).andExpect(status().isUnauthorized());
        assertThat(savedRows(userA)).isEqualTo(0);
    }

    @Test
    void staffAndAdminCannotAccessUserInsights() throws Exception {
        for (Role role : new Role[] {Role.STAFF, Role.ADMIN}) {
            User privileged = createUser(role);
            send(privileged, get(NOV), null).andExpect(status().isForbidden());
            assertThat(savedRows(privileged)).isEqualTo(0);
        }
    }

    @Test
    void recalculationWithNoPatternsRemovesOnlyCallersMonth() throws Exception {
        record(userA, accountA, salary, "INCOME", "10000.00", "2026-11-02");
        record(userA, accountA, shopping, "EXPENSE", "6000.00", "2026-11-03");
        record(userB, accountB, salary, "INCOME", "10000.00", "2026-11-02");
        record(userB, accountB, shopping, "EXPENSE", "6000.00", "2026-11-03");
        send(userA, get(NOV), null).andExpect(jsonPath("$.length()").value(2));
        send(userB, get(NOV), null).andExpect(jsonPath("$.length()").value(2));
        jdbc.update("INSERT INTO insights (user_id, month, type, facts) VALUES (?, '2026-10', 'HIGH_WANTS', '{}')",
                userA.getId());

        // A second income on Nov 20 makes wants 6%; the Nov 3 spend is outside its window.
        record(userA, accountA, salary, "INCOME", "90000.00", "2026-11-20");
        // Add non-payday spending so the original purchase no longer triggers the payday rule.
        record(userA, accountA, food, "EXPENSE", "20000.00", "2026-11-06");
        // Payday share = 6000 / 26000 = 23.08%; no weekend expenses.
        send(userA, get(NOV), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(savedRows(userA)).isEqualTo(0);
        assertThat(savedRows(userB)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM insights WHERE user_id = ? AND month = '2026-10'",
                Integer.class, userA.getId())).isEqualTo(1);
    }

    @Test
    void onlyOwnDataIsUsed() throws Exception {
        record(userB, accountB, salary, "INCOME", "10000.00", "2026-11-02");
        record(userB, accountB, shopping, "EXPENSE", "9000.00", "2026-11-03");

        send(userA, get(NOV), null).andExpect(jsonPath("$.length()").value(0));
        send(userB, get(NOV), null).andExpect(jsonPath("$.length()").value(2));
        assertThat(savedRows(userA)).isEqualTo(0);
        assertThat(savedRows(userB)).isEqualTo(2);
    }
}
