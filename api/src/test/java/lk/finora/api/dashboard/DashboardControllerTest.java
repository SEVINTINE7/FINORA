package lk.finora.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
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
import tools.jackson.databind.JsonNode;

/** T20.1: dashboard totals, expenses per category, 6-month trend, default month, ownership. */
class DashboardControllerTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private User userA;
    private User userB;
    private Long accountA;
    private Long accountB;
    private Long food;      // default EXPENSE
    private Long transport; // default EXPENSE
    private Long salary;    // default INCOME

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
        accountA = createAccount(userA, "Cash A");
        accountB = createAccount(userB, "Cash B");
        food = categoryId("Food", "EXPENSE");
        transport = categoryId("Transport", "EXPENSE");
        salary = categoryId("Salary", "INCOME");
    }

    private Long categoryId(String name, String kind) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = ? AND kind = ?",
                Long.class, name, kind);
    }

    private Long createAccount(User user, String name) {
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, ?, 'CASH')", user.getId(), name);
        return jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ? AND name = ?", Long.class,
                user.getId(), name);
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    // Records are added through the real API, like the app does
    private void record(User user, Long accountId, Long categoryId, String type, String amount, String date)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("accountId", accountId);
        body.put("categoryId", categoryId);
        body.put("type", type);
        body.put("amount", amount);
        body.put("txnDate", date);
        send(user, post("/api/v1/transactions"), body).andExpect(status().isCreated());
    }

    private JsonNode dashboard(User user, String month) throws Exception {
        String response = send(user, get("/api/v1/dashboard?month=" + month), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response);
    }

    // Reads a money field as BigDecimal; compare by value because "0.10" may come back as "0.1"
    private BigDecimal money(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asString());
    }

    @Test
    void totalsEqualSumOfRawTransactions() throws Exception {
        // Fixture: November 2026, incl. the first and last day of the month
        record(userA, accountA, salary, "INCOME", "85000.00", "2026-11-01");
        record(userA, accountA, null, "INCOME", "1500.50", "2026-11-30");
        record(userA, accountA, food, "EXPENSE", "1250.75", "2026-11-01");
        record(userA, accountA, food, "EXPENSE", "0.10", "2026-11-15");
        record(userA, accountA, transport, "EXPENSE", "3400.00", "2026-11-30");
        record(userA, accountA, null, "EXPENSE", "99.99", "2026-11-20");
        // Just outside the month: must not count
        record(userA, accountA, food, "EXPENSE", "5000.00", "2026-10-31");
        record(userA, accountA, food, "EXPENSE", "5000.00", "2026-12-01");
        // Another user's data: must not count
        record(userB, accountB, food, "EXPENSE", "777.00", "2026-11-10");

        JsonNode json = dashboard(userA, "2026-11");

        // Raw sums, done by hand in the test with BigDecimal
        BigDecimal income = new BigDecimal("85000.00").add(new BigDecimal("1500.50"));
        BigDecimal expense = new BigDecimal("1250.75").add(new BigDecimal("0.10")).add(new BigDecimal("3400.00"))
                .add(new BigDecimal("99.99"));
        assertThat(json.get("month").asString()).isEqualTo("2026-11");
        assertThat(money(json, "income")).isEqualByComparingTo(income);   // 86500.50
        assertThat(money(json, "expense")).isEqualByComparingTo(expense); // 4750.84
        assertThat(money(json, "net")).isEqualByComparingTo(income.subtract(expense));

        // The SQL sum over the same rows gives the same expense
        BigDecimal sqlExpense = jdbc.queryForObject("SELECT SUM(amount) FROM transactions WHERE user_id = ?"
                + " AND type = 'EXPENSE' AND txn_date BETWEEN '2026-11-01' AND '2026-11-30'",
                BigDecimal.class, userA.getId());
        assertThat(money(json, "expense")).isEqualByComparingTo(sqlExpense);

        // Pie: expenses only, biggest first; uncategorised has categoryId null
        JsonNode pie = json.get("byCategory");
        assertThat(pie.size()).isEqualTo(3);
        assertThat(pie.get(0).get("name").asString()).isEqualTo("Transport");
        assertThat(money(pie.get(0), "total")).isEqualByComparingTo(new BigDecimal("3400.00"));
        assertThat(pie.get(1).get("categoryId").asLong()).isEqualTo(food);
        assertThat(pie.get(1).get("nameSi").asString()).isEqualTo("ආහාර");
        assertThat(money(pie.get(1), "total")).isEqualByComparingTo(new BigDecimal("1250.85"));
        assertThat(pie.get(2).get("categoryId").isNull()).isTrue();
        assertThat(money(pie.get(2), "total")).isEqualByComparingTo(new BigDecimal("99.99"));

        // Pie slices add up to the expense total
        BigDecimal pieSum = BigDecimal.ZERO;
        for (JsonNode slice : pie) {
            pieSum = pieSum.add(money(slice, "total"));
        }
        assertThat(pieSum).isEqualByComparingTo(expense);
    }

    @Test
    void trendHasSixMonthsOldestFirstAcrossYearEnd() throws Exception {
        record(userA, accountA, salary, "INCOME", "50000.00", "2025-11-30"); // before the window
        record(userA, accountA, salary, "INCOME", "60000.00", "2025-12-31");
        record(userA, accountA, food, "EXPENSE", "2500.25", "2026-02-28");
        record(userA, accountA, food, "EXPENSE", "100.00", "2026-05-01");

        JsonNode trend = dashboard(userA, "2026-05").get("trend");

        assertThat(trend.size()).isEqualTo(6);
        assertThat(trend.get(0).get("month").asString()).isEqualTo("2025-12");
        assertThat(money(trend.get(0), "income")).isEqualByComparingTo(new BigDecimal("60000.00"));
        assertThat(money(trend.get(0), "net")).isEqualByComparingTo(new BigDecimal("60000.00"));
        // Empty month shows 0.00
        assertThat(trend.get(1).get("month").asString()).isEqualTo("2026-01");
        assertThat(money(trend.get(1), "expense")).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(trend.get(3).get("month").asString()).isEqualTo("2026-03");
        assertThat(money(trend.get(2), "expense")).isEqualByComparingTo(new BigDecimal("2500.25"));
        assertThat(money(trend.get(2), "net")).isEqualByComparingTo(new BigDecimal("-2500.25"));
        assertThat(trend.get(5).get("month").asString()).isEqualTo("2026-05");
        assertThat(money(trend.get(5), "expense")).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void newUserGetsZerosAndEmptyPie() throws Exception {
        // Raw text, so we see the money is sent as 0.00 (2 decimals) and not 0
        String raw = send(userA, get("/api/v1/dashboard?month=2026-11"), null)
                .andReturn().getResponse().getContentAsString();
        assertThat(raw).contains("\"income\":0.00").contains("\"expense\":0.00").contains("\"net\":0.00")
                .contains("\"byCategory\":[]");

        JsonNode json = dashboard(userA, "2026-11");
        assertThat(json.get("trend").size()).isEqualTo(6);
    }

    @Test
    void noMonthMeansCurrentMonthInColombo() throws Exception {
        String thisMonth = YearMonth.now(ZoneId.of("Asia/Colombo")).toString();

        send(userA, get("/api/v1/dashboard"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(thisMonth))
                .andExpect(jsonPath("$.trend[5].month").value(thisMonth));
    }

    @Test
    void badMonthGives400() throws Exception {
        send(userA, get("/api/v1/dashboard?month=2026-13"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(userA, get("/api/v1/dashboard?month=2026-1"), null).andExpect(status().isBadRequest());
        send(userA, get("/api/v1/dashboard?month=Nov"), null).andExpect(status().isBadRequest());
    }

    @Test
    void otherUsersDataIsNeverCounted() throws Exception {
        record(userB, accountB, salary, "INCOME", "99999.00", "2026-11-05");
        record(userB, accountB, food, "EXPENSE", "555.00", "2026-11-05");

        JsonNode json = dashboard(userA, "2026-11");
        assertThat(money(json, "income")).isEqualByComparingTo("0");
        assertThat(json.get("byCategory").size()).isEqualTo(0);

        assertThat(money(dashboard(userB, "2026-11"), "expense")).isEqualByComparingTo("555.00");
    }
}
