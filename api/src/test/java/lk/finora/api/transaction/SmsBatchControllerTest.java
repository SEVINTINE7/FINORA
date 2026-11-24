package lk.finora.api.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

class SmsBatchControllerTest extends IntegrationTest {
    private static final String PATH = "/api/v1/transactions/batch";
    private static final String HASH = "a".repeat(64);
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test void savesOnlyOnceAcrossInboxPasteAndSameBatchRepeats() throws Exception {
        User user = createUser(Role.USER);
        long account = account(user);
        send(user, List.of(item(account, "SMS", HASH), item(account, "PASTE", HASH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("SAVED"))
                .andExpect(jsonPath("$.items[1].status").value("DUPLICATE"));
        send(user, List.of(item(account, "PASTE", HASH))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("DUPLICATE"));
        assertEquals(1, count(user));
        assertEquals(new BigDecimal("1250.75"), jdbc.queryForObject(
                "SELECT amount FROM transactions WHERE user_id = ?", BigDecimal.class, user.getId()));
    }

    @Test void possibleManualMatchPreservesManualAndDistinctFingerprint() throws Exception {
        User user = createUser(Role.USER);
        long account = account(user);
        Object body = Map.of("accountId", account, "type", "EXPENSE", "txnDate", "2026-11-21", "amount", "1250.75");
        var manual = mockMvc.perform(post("/api/v1/transactions").header("Authorization", "Bearer " + tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andExpect(status().isCreated());
        long manualId = jsonMapper.readTree(manual.andReturn().getResponse().getContentAsString()).get("id").asLong();
        send(user, List.of(item(account, "SMS", HASH), item(account, "SMS", "b".repeat(64))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].possibleManualMatch[0]").value(manualId))
                .andExpect(jsonPath("$.items[1].status").value("SAVED"));
        assertEquals(3, count(user));
        assertEquals("MANUAL", jdbc.queryForObject("SELECT source FROM transactions WHERE id = ?",
                String.class, manualId));
    }

    @Test void validatesEveryRowBeforeWritesAndScopesHashesToUser() throws Exception {
        User user = createUser(Role.USER);
        User other = createUser(Role.USER);
        long account = account(user);
        long otherAccount = account(other);
        send(user, List.of(item(account, "SMS", HASH), item(otherAccount, "SMS", HASH)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Item 2: Account not found"));
        send(user, List.of(item(account, "SMS", HASH), item(account, "MANUAL", HASH)))
                .andExpect(status().isBadRequest());
        send(user, List.of(item(account, "SMS", HASH), item(account, "SMS", "malformed")))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE accounts SET archived = TRUE WHERE id = ?", account);
        entityManager.clear();
        send(user, List.of(item(account, "SMS", HASH))).andExpect(status().isBadRequest());
        assertEquals(0, count(user));
        send(other, List.of(item(otherAccount, "SMS", HASH))).andExpect(status().isOk());
        jdbc.update("UPDATE accounts SET archived = FALSE WHERE id = ?", account);
        entityManager.clear();
        send(user, List.of(item(account, "SMS", HASH))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("SAVED"));
        assertEquals(1, count(user));
        assertEquals(1, count(other));
    }

    @Test void requiresUserRole() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        for (Role role : List.of(Role.ADMIN, Role.STAFF)) {
            send(createUser(role), List.of(item(1, "SMS", HASH))).andExpect(status().isForbidden());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsSaveOneRecordAndReportOneDuplicate() throws Exception {
        User user = createUser(Role.USER);
        var pool = Executors.newFixedThreadPool(2);
        try {
            long account = account(user);
            var a = pool.submit(() -> firstStatus(send(user, List.of(item(account, "SMS", HASH)))));
            var b = pool.submit(() -> firstStatus(send(user, List.of(item(account, "PASTE", HASH)))));
            var statuses = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter("SAVED"::equals).count());
            assertEquals(1, statuses.stream().filter("DUPLICATE"::equals).count());
            assertEquals(1, count(user));
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id = ?", user.getId());
        }
    }

    private String firstStatus(ResultActions response) throws Exception {
        return jsonMapper.readTree(response.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("items").get(0).get("status").asText();
    }

    private int count(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Integer.class, user.getId());
    }

    private long account(User user) throws Exception {
        var account = mockMvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "BANK", "name", "Synthetic",
                        "openingBalance", "0.00")))).andExpect(status().isCreated());
        return jsonMapper.readTree(account.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions send(User user, List<Map<String, Object>> items) throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", "Bearer " + tokenFor(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("items", items))));
    }

    private Map<String, Object> item(long account, String source, String hash) {
        var item = new LinkedHashMap<String, Object>();
        item.put("dedupHash", hash);
        item.put("transaction", Map.of("accountId", account, "type", "EXPENSE", "amount", "1250.75",
                "txnDate", "2026-11-21", "merchant", "SYNTHETIC SHOP", "source", source));
        return item;
    }
}
