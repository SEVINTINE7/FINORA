package lk.finora.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** T10: transactions CRUD, ownership checks, list filters and paging, exact totals. */
class TransactionControllerTest extends IntegrationTest {

    private static final String SUMMARY_NOV = "/api/v1/transactions/summary?from=2026-11-01&to=2026-11-30";

    @Autowired
    private JdbcTemplate jdbc;

    private User userA;
    private User userB;
    private Long accountA;
    private Long accountB;
    private Long food;   // default EXPENSE category
    private Long salary; // default INCOME category

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
        accountA = createAccount(userA, "Cash A");
        accountB = createAccount(userB, "Cash B");
        food = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = 'Food'", Long.class);
        salary = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = 'Salary'", Long.class);
    }

    private Long createAccount(User user, String name) {
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, ?, 'CASH')", user.getId(), name);
        return jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ? AND name = ?", Long.class,
                user.getId(), name);
    }

    private Map<String, Object> body(Long accountId, Long categoryId, String type, String amount, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("accountId", accountId);
        body.put("categoryId", categoryId);
        body.put("type", type);
        body.put("amount", amount);
        body.put("txnDate", date);
        body.put("merchant", "Keells");
        return body;
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    private Long create(User user, Map<String, Object> body) throws Exception {
        String response = send(user, post("/api/v1/transactions"), body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    // ---------- CRUD ----------

    @Test
    void createGetUpdateDelete() throws Exception {
        String response = send(userA, post("/api/v1/transactions"),
                body(accountA, food, "EXPENSE", "1250.75", "2026-11-05"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1250.75))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.txnDate").value("2026-11-05"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andReturn().getResponse().getContentAsString();
        Long id = jsonMapper.readTree(response).get("id").asLong();

        send(userA, put("/api/v1/transactions/" + id), body(accountA, food, "EXPENSE", "999.99", "2026-11-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(999.99))
                .andExpect(jsonPath("$.txnDate").value("2026-11-06"));

        send(userA, get("/api/v1/transactions/" + id), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Keells"));

        send(userA, delete("/api/v1/transactions/" + id), null).andExpect(status().isNoContent());
        send(userA, get("/api/v1/transactions/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void badValuesGive400() throws Exception {
        // Zero amount, 3 decimals, missing date
        send(userA, post("/api/v1/transactions"), body(accountA, food, "EXPENSE", "0.00", "2026-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(userA, post("/api/v1/transactions"), body(accountA, food, "EXPENSE", "10.555", "2026-11-05"))
                .andExpect(status().isBadRequest());
        send(userA, post("/api/v1/transactions"), body(accountA, food, "EXPENSE", "10.00", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryKindMustMatchType() throws Exception {
        send(userA, post("/api/v1/transactions"), body(accountA, salary, "EXPENSE", "100.00", "2026-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void categoryIsOptional() throws Exception {
        send(userA, post("/api/v1/transactions"), body(accountA, null, "EXPENSE", "100.00", "2026-11-05"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    // ---------- ownership ----------

    @Test
    void otherUsersAccountOrCategoryIsRejected() throws Exception {
        send(userA, post("/api/v1/transactions"), body(accountB, food, "EXPENSE", "100.00", "2026-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account not found"));

        jdbc.update("INSERT INTO categories (user_id, name, kind) VALUES (?, 'B only', 'EXPENSE')", userB.getId());
        Long categoryOfB = jdbc.queryForObject("SELECT id FROM categories WHERE user_id = ?", Long.class,
                userB.getId());
        send(userA, post("/api/v1/transactions"), body(accountA, categoryOfB, "EXPENSE", "100.00", "2026-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void archivedAccountCannotGetNewRecords() throws Exception {
        jdbc.update("UPDATE accounts SET archived = TRUE WHERE id = ?", accountA);
        send(userA, post("/api/v1/transactions"), body(accountA, food, "EXPENSE", "100.00", "2026-11-05"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This account is archived"));
    }

    @Test
    void otherUsersTransactionGives404() throws Exception {
        Long idOfA = create(userA, body(accountA, food, "EXPENSE", "100.00", "2026-11-05"));

        send(userB, get("/api/v1/transactions/" + idOfA), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        send(userB, put("/api/v1/transactions/" + idOfA), body(accountB, food, "EXPENSE", "1.00", "2026-11-05"))
                .andExpect(status().isNotFound());
        send(userB, delete("/api/v1/transactions/" + idOfA), null).andExpect(status().isNotFound());
        send(userB, get("/api/v1/transactions"), null).andExpect(jsonPath("$.totalItems").value(0));
        send(userB, get(SUMMARY_NOV), null).andExpect(jsonPath("$.expense").value(0.00));
    }

    @Test
    void foreignAccountAndCategoryQueryFiltersDoNotReturnForeignRows() throws Exception {
        String path = "/api/v1/transactions";
        var category = send(userB, post("/api/v1/categories"),
                Map.of("name", "Synthetic owner category", "kind", "EXPENSE"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long categoryB = jsonMapper.readTree(category).get("id").asLong();
        long transactionB = create(userB, body(accountB, categoryB, "EXPENSE", "101.00", "2026-11-05"));
        create(userA, body(accountA, food, "EXPENSE", "102.00", "2026-11-05"));

        for (var filter : Map.of("accountId", accountB, "categoryId", categoryB).entrySet()) {
            send(userB, get(path).param(filter.getKey(), filter.getValue().toString()), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(transactionB))
                    .andExpect(jsonPath("$.totalItems").value(1));
            send(userA, get(path).param(filter.getKey(), filter.getValue().toString()), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalItems").value(0));
        }
    }

    // ---------- list filters and paging ----------

    @Test
    void listFiltersSortsAndPages() throws Exception {
        Long accountA2 = createAccount(userA, "Bank A");
        create(userA, body(accountA, food, "EXPENSE", "100.00", "2026-10-31"));
        create(userA, body(accountA, food, "EXPENSE", "200.00", "2026-11-03"));
        create(userA, body(accountA2, salary, "INCOME", "50000.00", "2026-11-01"));
        create(userA, body(accountA, null, "EXPENSE", "300.00", "2026-11-20"));

        // No filters: newest first
        send(userA, get("/api/v1/transactions"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].txnDate").value("2026-11-20"))
                .andExpect(jsonPath("$.items[3].txnDate").value("2026-10-31"));

        send(userA, get("/api/v1/transactions?from=2026-11-01&to=2026-11-30"), null)
                .andExpect(jsonPath("$.totalItems").value(3));
        send(userA, get("/api/v1/transactions?type=INCOME"), null)
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(50000.00));
        send(userA, get("/api/v1/transactions?categoryId=" + food), null)
                .andExpect(jsonPath("$.totalItems").value(2));
        send(userA, get("/api/v1/transactions?accountId=" + accountA2), null)
                .andExpect(jsonPath("$.totalItems").value(1));
        send(userA, get("/api/v1/transactions?type=EXPENSE&from=2026-11-01&accountId=" + accountA), null)
                .andExpect(jsonPath("$.totalItems").value(2));

        // Paging: 4 items, 3 per page -> page 1 has the oldest one
        send(userA, get("/api/v1/transactions?page=1&size=3"), null)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].txnDate").value("2026-10-31"));
    }

    @Test
    void badFilterGives400() throws Exception {
        send(userA, get("/api/v1/transactions?type=SAVING"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(userA, get("/api/v1/transactions?from=05-11-2026"), null)
                .andExpect(status().isBadRequest());
    }

    // ---------- summary ----------

    @Test
    void summaryIsExactToTheCentAfterCreateEditDelete() throws Exception {
        send(userA, get(SUMMARY_NOV), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(0.00))
                .andExpect(jsonPath("$.net").value(0.00));

        create(userA, body(accountA, salary, "INCOME", "45000.10", "2026-11-01"));
        Long coffee = create(userA, body(accountA, food, "EXPENSE", "0.10", "2026-11-02"));
        create(userA, body(accountA, food, "EXPENSE", "0.20", "2026-11-02"));
        Long rent = create(userA, body(accountA, null, "EXPENSE", "15000.33", "2026-11-03"));
        create(userA, body(accountA, food, "EXPENSE", "999.00", "2026-12-01")); // outside the range

        // 0.10 + 0.20 must be exactly 0.30 (a double would give 0.30000000000000004)
        String body = send(userA, get(SUMMARY_NOV), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertMoney(body, "income", "45000.10");
        assertMoney(body, "expense", "15000.63");
        assertMoney(body, "net", "29999.47");

        // Edit 15000.33 -> 14000.00, delete the 0.10
        send(userA, put("/api/v1/transactions/" + rent), body(accountA, null, "EXPENSE", "14000.00", "2026-11-03"))
                .andExpect(status().isOk());
        send(userA, delete("/api/v1/transactions/" + coffee), null).andExpect(status().isNoContent());

        body = send(userA, get(SUMMARY_NOV), null).andReturn().getResponse().getContentAsString();
        assertMoney(body, "expense", "14000.20");
        assertMoney(body, "net", "30999.90");
    }

    @Test
    void summaryNeedsValidRange() throws Exception {
        send(userA, get("/api/v1/transactions/summary?from=2026-11-30&to=2026-11-01"), null)
                .andExpect(status().isBadRequest());
        send(userA, get("/api/v1/transactions/summary?from=2026-11-01"), null)
                .andExpect(status().isBadRequest());
    }

    /** Checks the raw JSON text, e.g. "expense":15000.63, so no double rounding can hide an error. */
    private void assertMoney(String json, String field, String expected) {
        assertThat(json).contains("\"" + field + "\":" + expected);
    }
}
