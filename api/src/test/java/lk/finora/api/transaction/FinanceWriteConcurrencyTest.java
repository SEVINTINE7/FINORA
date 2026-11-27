package lk.finora.api.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

/** Requests commit on different MySQL connections. The loser must reach the real row lock before release. */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FinanceWriteConcurrencyTest extends IntegrationTest {
    private enum Write { MANUAL, MOVE, CSV, SMS, PASTE }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private User owner;
    private long source;
    private long target;
    private long existing;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser(Role.USER);
        source = account(owner, "Source");
        target = account(owner, "Target");
        existing = jsonMapper.readTree(request(post("/api/v1/transactions"), owner, row(source, "MANUAL"))
                .getResponse().getContentAsString()).get("id").asLong();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
    }

    @ParameterizedTest
    @EnumSource(Write.class)
    void writeWinsAndRemovalArchivesInsteadOfCascading(Write write) throws Exception {
        race(() -> submit(write, owner, target), this::removeTarget,
                result -> assertAccepted(write, result), result -> assertStatus(204, result));
        assertEquals(1, count(target));
        assertTrue(jdbc.queryForObject("SELECT archived FROM accounts WHERE id = ?", Boolean.class, target));
    }

    @ParameterizedTest
    @EnumSource(Write.class)
    void deletionWinsAndWriteRejectsMissingAccount(Write write) throws Exception {
        race(this::removeTarget, () -> submit(write, owner, target),
                result -> assertStatus(204, result), result -> assertRejection(result, "Account not found"));
        assertEquals(0, count(target));
        assertEquals(1, count(source));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE id = ?", Integer.class, target));
    }

    @ParameterizedTest
    @EnumSource(Write.class)
    void archivalWinsAndWriteRejectsInactiveAccount(Write write) throws Exception {
        assertStatus(201, submit(Write.MANUAL, owner, target));
        race(this::removeTarget, () -> submit(write, owner, target),
                result -> assertStatus(204, result), result -> assertRejection(result, "This account is archived"));
        assertEquals(1, count(target));
        assertStatus(200, request(put("/api/v1/transactions/" + existing), owner, row(source, "MANUAL")));
    }

    @ParameterizedTest
    @EnumSource(value = Write.class, names = {"CSV", "SMS", "PASTE"})
    void concurrentMultiAccountImportsDeduplicateEvenWithOppositeAccountOrder(Write write) throws Exception {
        race(() -> bulk(write, owner, source, target), () -> bulk(write, owner, target, source),
                result -> assertBulkResult(write, result, false), result -> assertBulkResult(write, result, true));
        assertEquals(2, count(source));
        assertEquals(1, count(target));
    }

    @ParameterizedTest
    @EnumSource(value = Write.class, names = {"CSV", "SMS", "PASTE"})
    void losingSecondAccountRejectsWholeBatch(Write write) throws Exception {
        race(this::removeTarget, () -> bulk(write, owner, source, target),
                result -> assertStatus(204, result), result -> assertRejection(result,
                        (write == Write.CSV ? "Line 2" : "Item 2") + ": Account not found"));
        assertEquals(1, count(source));
        assertEquals(0, count(target));
    }

    @ParameterizedTest
    @EnumSource(Write.class)
    void foreignAccountRemainsInvisible(Write write) throws Exception {
        User other = createUser(Role.USER);
        try {
            long foreign = account(other, "Foreign");
            assertRejection(submit(write, owner, foreign), "Account not found");
            assertStatus(404, request(delete("/api/v1/accounts/" + foreign), owner, null));
            assertEquals(0, count(foreign));
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", other.getId());
        }
    }

    @Test
    void sameAccountEditStillWorksAfterArchivalAndOwnerDeletionCascades() throws Exception {
        assertStatus(204, request(delete("/api/v1/accounts/" + source), owner, null));
        assertStatus(200, request(put("/api/v1/transactions/" + existing), owner, row(source, "MANUAL")));
        assertEquals(1, count(source));
        jdbc.update("DELETE FROM users WHERE id = ?", owner.getId());
        assertEquals(0, count(source));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE user_id = ?",
                Integer.class, owner.getId()));
    }

    private void race(Callable<MvcResult> winner, Callable<MvcResult> loser,
                      Consumer<MvcResult> checkWinner, Consumer<MvcResult> checkLoser) throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        var outer = new TransactionTemplate(transactionManager);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            var pending = outer.execute(tx -> {
                MvcResult result;
                try {
                    result = winner.call();
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                checkWinner.accept(result);
                var next = worker.submit(loser);
                // A separate connection sees the loser's SELECT blocked by the uncommitted request.
                for (int attempt = 0; attempt < 100; attempt++) {
                    if (next.isDone()) fail("The second request completed before the first committed");
                    int pendingLocks = jdbc.queryForObject("""
                            SELECT COUNT(*) FROM information_schema.PROCESSLIST
                            WHERE ID <> CONNECTION_ID() AND INFO = ?
                            """, Integer.class, "SELECT id FROM users WHERE id = " + owner.getId()
                                    + " AND role = 'USER' FOR UPDATE");
                    if (pendingLocks == 1) return next;
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
                return fail("The second request did not reach the MySQL write lock");
            });
            checkLoser.accept(pending.get(10, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
        }
    }

    private long account(User user, String name) throws Exception {
        var result = request(post("/api/v1/accounts"), user,
                Map.of("name", name, "type", "WALLET", "openingBalance", "0.00"));
        assertStatus(201, result);
        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private MvcResult removeTarget() throws Exception {
        return request(delete("/api/v1/accounts/" + target), owner, null);
    }

    private MvcResult submit(Write write, User user, long id) throws Exception {
        return switch (write) {
            case MANUAL -> request(post("/api/v1/transactions"), user, row(id, "MANUAL"));
            case MOVE -> request(put("/api/v1/transactions/" + existing), user, row(id, "MANUAL"));
            default -> bulk(write, user, id);
        };
    }

    private MvcResult bulk(Write write, User user, long... ids) throws Exception {
        if (write == Write.CSV) {
            var rows = new java.util.ArrayList<Map<String, Object>>();
            for (long id : ids) {
                rows.add(Map.of("line", rows.size() + 1, "occurrence", 1, "accountId", id, "type", "EXPENSE",
                        "amount", "42.10", "txnDate", "2026-11-21", "merchant", "Synthetic import"));
            }
            return request(post("/api/v1/imports/csv/confirm"), user, Map.of("rows", rows));
        }
        var items = new java.util.ArrayList<Map<String, Object>>();
        for (long id : ids) {
            items.add(Map.of("dedupHash", "%064x".formatted(id), "transaction", row(id, write.name())));
        }
        return request(post("/api/v1/transactions/batch"), user, Map.of("items", items));
    }

    private Map<String, Object> row(long account, String sourceType) {
        return Map.of("accountId", account, "type", "EXPENSE", "amount", "42.10",
                "txnDate", "2026-11-21", "source", sourceType);
    }

    private MvcResult request(MockHttpServletRequestBuilder builder, User user, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + tokenFor(user));
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
        return mockMvc.perform(builder).andReturn();
    }

    private int count(long account) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE account_id = ?", Integer.class, account);
    }

    private void assertAccepted(Write write, MvcResult result) {
        assertStatus(write == Write.MANUAL ? 201 : 200, result);
        if (write == Write.CSV) assertTrue(body(result).contains("\"saved\":1"), body(result));
        if (write == Write.SMS || write == Write.PASTE) assertTrue(body(result).contains("\"status\":\"SAVED\""), body(result));
    }

    private void assertBulkResult(Write write, MvcResult result, boolean duplicate) {
        assertStatus(200, result);
        if (write == Write.CSV) {
            assertTrue(body(result).contains(duplicate ? "\"saved\":0" : "\"saved\":2"), body(result));
        } else {
            String expected = duplicate ? "\"status\":\"DUPLICATE\"" : "\"status\":\"SAVED\"";
            assertEquals(2, body(result).split(expected, -1).length - 1, body(result));
        }
    }

    private void assertRejection(MvcResult result, String reason) {
        assertStatus(400, result);
        assertTrue(body(result).contains(reason), body(result));
    }

    private void assertStatus(int expected, MvcResult result) {
        assertEquals(expected, result.getResponse().getStatus(), () -> body(result));
    }

    private String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
