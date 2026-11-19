package lk.finora.api.category;

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

/** T09.2 / T09.3: defaults + own categories, read-only defaults, duplicates, in-use delete, ownership. */
class CategoryControllerTest extends IntegrationTest {

    // V6 seeds 17 default categories
    private static final int DEFAULTS = 17;

    @Autowired
    private JdbcTemplate jdbc;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
    }

    private Map<String, Object> body(String name, String kind) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("nameSi", "පොත්");
        body.put("kind", kind);
        body.put("needOrWant", "WANT");
        return body;
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    private Long createCategory(User user, String name) throws Exception {
        String response = send(user, post("/api/v1/categories"), body(name, "EXPENSE"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    private Long defaultCategoryId(String name, String kind) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = ? AND kind = ?",
                Long.class, name, kind);
    }

    @Test
    void listShowsDefaultsPlusOwnOnly() throws Exception {
        createCategory(userA, "Books");
        createCategory(userB, "Gaming");

        send(userA, get("/api/v1/categories"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(DEFAULTS + 1))
                .andExpect(jsonPath("$[?(@.name == 'Books')].isDefault").value(false))
                .andExpect(jsonPath("$[?(@.name == 'Food')].isDefault").value(true))
                .andExpect(jsonPath("$[?(@.name == 'Gaming')]").isEmpty());
    }

    @Test
    void createGetUpdateDeleteOwn() throws Exception {
        Long id = createCategory(userA, "Books");

        send(userA, get("/api/v1/categories/" + id), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Books"))
                .andExpect(jsonPath("$.kind").value("EXPENSE"))
                .andExpect(jsonPath("$.needOrWant").value("WANT"));

        send(userA, put("/api/v1/categories/" + id), Map.of("name", "Books and stationery", "needOrWant", "NEED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Books and stationery"))
                .andExpect(jsonPath("$.needOrWant").value("NEED"));

        send(userA, delete("/api/v1/categories/" + id), null).andExpect(status().isNoContent());
        send(userA, get("/api/v1/categories/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void defaultCategoryCanBeReadButNotChanged() throws Exception {
        Long food = defaultCategoryId("Food", "EXPENSE");

        send(userA, get("/api/v1/categories/" + food), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
        send(userA, put("/api/v1/categories/" + food), Map.of("name", "My food"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        send(userA, delete("/api/v1/categories/" + food), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateNameGives409() throws Exception {
        createCategory(userA, "Books");
        Long gym = createCategory(userA, "Gym");

        send(userA, post("/api/v1/categories"), body("Books", "EXPENSE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        // Same name as a default of the same kind
        send(userA, post("/api/v1/categories"), body("Food", "EXPENSE")).andExpect(status().isConflict());
        // Renaming onto an existing name
        send(userA, put("/api/v1/categories/" + gym), Map.of("name", "Books")).andExpect(status().isConflict());

        // Same name is fine for the other kind, or for another user
        send(userA, post("/api/v1/categories"), body("Books", "INCOME")).andExpect(status().isCreated());
        send(userB, post("/api/v1/categories"), body("Books", "EXPENSE")).andExpect(status().isCreated());
    }

    @Test
    void deleteCategoryInUseGives409() throws Exception {
        Long usedByTransaction = createCategory(userA, "Books");
        Long usedByBudget = createCategory(userA, "Gym");

        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, 'Cash', 'CASH')", userA.getId());
        Long accountId = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ?", Long.class, userA.getId());
        jdbc.update("INSERT INTO transactions (user_id, account_id, category_id, type, amount, txn_date) "
                + "VALUES (?, ?, ?, 'EXPENSE', 1200.00, '2026-11-01')", userA.getId(), accountId, usedByTransaction);
        jdbc.update("INSERT INTO budgets (user_id, category_id, month, limit_amount) VALUES (?, ?, '2026-11', 5000.00)",
                userA.getId(), usedByBudget);

        send(userA, delete("/api/v1/categories/" + usedByTransaction), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"));
        send(userA, delete("/api/v1/categories/" + usedByBudget), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"));
    }

    @Test
    void otherUsersCategoryGives404() throws Exception {
        Long idOfA = createCategory(userA, "Books");

        send(userB, get("/api/v1/categories/" + idOfA), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        send(userB, put("/api/v1/categories/" + idOfA), Map.of("name", "Stolen")).andExpect(status().isNotFound());
        send(userB, delete("/api/v1/categories/" + idOfA), null).andExpect(status().isNotFound());
    }

    @Test
    void badValuesGive400() throws Exception {
        send(userA, post("/api/v1/categories"), Map.of("name", "X", "kind", "SAVING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
