package lk.finora.api.account;

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

import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

/** T09.1 / T09.3: accounts CRUD, duplicate names, archive instead of delete, ownership. */
class AccountControllerTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
    }

    private Map<String, Object> body(String name) {
        return Map.of("name", name, "type", "BANK", "openingBalance", "15000.50");
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    private Long createAccount(User user, String name) throws Exception {
        String response = send(user, post("/api/v1/accounts"), body(name))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asLong();
    }

    @Test
    void createListGetUpdateDelete() throws Exception {
        send(userA, post("/api/v1/accounts"), body("  BOC Savings "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("BOC Savings"))
                .andExpect(jsonPath("$.type").value("BANK"))
                .andExpect(jsonPath("$.openingBalance").value(15000.50))
                .andExpect(jsonPath("$.archived").value(false));
        Long id = createAccount(userA, "Cash");

        send(userA, get("/api/v1/accounts"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        send(userA, put("/api/v1/accounts/" + id),
                Map.of("name", "Wallet cash", "type", "CASH", "openingBalance", "2500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wallet cash"))
                .andExpect(jsonPath("$.type").value("CASH"));

        send(userA, get("/api/v1/accounts/" + id), null)
                .andExpect(jsonPath("$.name").value("Wallet cash"));

        send(userA, delete("/api/v1/accounts/" + id), null).andExpect(status().isNoContent());
        send(userA, get("/api/v1/accounts/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void badValuesGive400() throws Exception {
        send(userA, post("/api/v1/accounts"), Map.of("name", "", "type", "GOLD", "openingBalance", "1.234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void duplicateNameGives409() throws Exception {
        Long first = createAccount(userA, "Cash");
        Long second = createAccount(userA, "Card");

        send(userA, post("/api/v1/accounts"), body("Cash"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        // Renaming to a name already used also fails; keeping its own name is fine
        send(userA, put("/api/v1/accounts/" + second), body("Cash")).andExpect(status().isConflict());
        send(userA, put("/api/v1/accounts/" + first), body("Cash")).andExpect(status().isOk());

        // Another user may use the same name
        send(userB, post("/api/v1/accounts"), body("Cash")).andExpect(status().isCreated());
    }

    @Test
    void accountWithTransactionsIsArchivedNotDeleted() throws Exception {
        Long id = createAccount(userA, "Cash");
        jdbc.update("INSERT INTO transactions (user_id, account_id, type, amount, txn_date) "
                + "VALUES (?, ?, 'EXPENSE', 500.00, '2026-11-01')", userA.getId(), id);

        send(userA, delete("/api/v1/accounts/" + id), null).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        send(userA, get("/api/v1/accounts/" + id), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        Integer transactions = jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE account_id = ?",
                Integer.class, id);
        assertThat(transactions).isEqualTo(1);
    }

    @Test
    void otherUsersAccountGives404() throws Exception {
        Long idOfA = createAccount(userA, "Cash");

        send(userB, get("/api/v1/accounts/" + idOfA), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        send(userB, put("/api/v1/accounts/" + idOfA), body("Mine now")).andExpect(status().isNotFound());
        send(userB, delete("/api/v1/accounts/" + idOfA), null).andExpect(status().isNotFound());
        send(userB, get("/api/v1/accounts"), null).andExpect(jsonPath("$.length()").value(0));
    }
}
