package lk.finora.api.history;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HistoryControllerTest extends IntegrationTest {
    @Autowired private HistoryService history;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired private lk.finora.api.score.ScoreService scores;
    @Autowired private lk.finora.api.account.FinanceWriteLock ownerLock;

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void scheduledSnapshotWaitsForFinanceCommit() throws Exception {
        var owner = createUser(Role.USER);
        long userId = owner.getId();
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?, 'Worker wallet','CASH')", userId);
            var key = new HistoryService.Key(userId, "2026-10");
            var parent = new org.springframework.transaction.support.TransactionTemplate(transactions);
            var pending = parent.execute(tx -> {
                ownerLock.acquire(userId);
                var next = worker.submit(() -> history.materialize(key, false));
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(6);
                while (System.nanoTime() < deadline) {
                    int waiting = jdbc.queryForObject("""
                            SELECT COUNT(*) FROM information_schema.PROCESSLIST
                            WHERE ID <> CONNECTION_ID() AND INFO = ?
                            """, Integer.class, "SELECT id FROM users WHERE id = " + userId + " FOR UPDATE");
                    if (waiting == 1) {
                        jdbc.update("""
                                INSERT INTO transactions (user_id,account_id,type,amount,txn_date)
                                SELECT user_id,id,'INCOME',15.23,'2026-10-10'
                                FROM accounts WHERE user_id=?
                                """, userId);
                        return next;
                    }
                    assertFalse(next.isDone(), "Worker completed before the finance commit");
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                }
                return fail("Snapshot worker did not reach the owner lock");
            });
            pending.get(10, java.util.concurrent.TimeUnit.SECONDS);
            history.materialize(key, false);
            var rows = history.read(userId, "2026-10", "2026-10");
            assertEquals(1, rows.size());
            assertEquals(new java.math.BigDecimal("15.23"), rows.get(0).net());
        } finally {
            worker.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test void editOfScoredPastMonthUpdatesTheStoredScore() throws Exception {
        var owner = createUser(Role.USER);
        String auth = "Bearer " + tokenFor(owner);
        long userId = owner.getId();
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?, 'Scored wallet','CASH')", userId);
        long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ?", Long.class, userId);
        for (String[] input : new String[][]{
                {"INCOME", "100000.00", "Salary"}, {"EXPENSE", "60000.00", "Food"},
                {"EXPENSE", "30000.00", "Shopping"}, {"EXPENSE", "5000.00", "Savings"}}) {
            jdbc.update("""
                    INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date)
                    SELECT ?,?,id,?,?,'2026-10-10' FROM categories WHERE user_id IS NULL AND name=?
                    """, userId, account, input[0], input[1], input[2]);
        }
        mockMvc.perform(put("/api/v1/score-inputs/2026-10").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("emergencyReserve", "30000.00", "reserveAsOf", "2026-10-20",
                        "noApplicableDebt", true, "noApplicableGoals", true)))).andExpect(status().isOk());
        var key = new HistoryService.Key(userId, "2026-10");
        history.materialize(key, false);
        assertEquals(92, history.read(userId, "2026-10", "2026-10").get(0).score());
        long food = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name='Food'", Long.class);
        long expense = jdbc.queryForObject("SELECT id FROM transactions WHERE user_id=? AND category_id=?",
                Long.class, userId, food);
        mockMvc.perform(put("/api/v1/transactions/" + expense).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("accountId", account, "categoryId", food, "type", "EXPENSE",
                        "amount", "70000.00", "txnDate", "2026-10-10")))).andExpect(status().isOk());
        assertTrue(history.read(userId, "2026-10", "2026-10").get(0).refreshPending());
        history.materialize(key, true);
        var rescored = history.read(userId, "2026-10", "2026-10").get(0);
        assertEquals(scores.forMonth(userId, "2026-10").total(), rescored.score());
        assertNotEquals(92, rescored.score());
    }

    @Test void usesColomboMonthBoundaryAndReadsExactSnapshotFacts() {
        assertEquals(java.time.YearMonth.of(2025, 11), HistoryScheduler.previousMonth(
                java.time.Instant.parse("2025-12-31T18:29:59Z")));
        assertEquals(java.time.YearMonth.of(2025, 12), HistoryScheduler.previousMonth(
                java.time.Instant.parse("2025-12-31T18:30:00Z")));
        var owner = createUser(Role.USER);
        jdbc.update("""
                INSERT INTO monthly_history(user_id, month, income, expense, insights)
                VALUES (?, '2025-12', 0, 0, '[{"type":"HIGH_WANTS","facts":{"wants":"9007199254740993.21"}}]')
                """, owner.getId());
        var row = history.read(owner.getId(), "2025-12", "2025-12").get(0);
        assertEquals(new java.math.BigDecimal("9007199254740993.21"), row.insights().get(0).facts().get("wants"));
    }

    @Test void deletingOwnerRemovesStoredHistoryAndPendingRefresh() {
        long owner = createUser(Role.USER).getId();
        long other = createUser(Role.USER).getId();
        history.materialize(new HistoryService.Key(owner, "2026-10"), false);
        history.materialize(new HistoryService.Key(other, "2026-10"), false);
        jdbc.update("UPDATE monthly_history SET dirty = TRUE WHERE user_id = ?", owner);

        jdbc.update("DELETE FROM users WHERE id = ?", owner);

        assertTrue(history.read(owner, "2026-10", "2026-10").isEmpty());
        assertFalse(history.dirty().contains(new HistoryService.Key(owner, "2026-10")));
        assertEquals(1, history.read(other, "2026-10", "2026-10").size());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void rolledBackTransactionDoesNotQueueOrAlterSnapshot() throws Exception {
        var user = createUser(Role.USER);
        try {
            var month = new HistoryService.Key(user.getId(), "2026-10");
            history.materialize(month, false);
            jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?, 'Rollback wallet','CASH')", user.getId());
            long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, user.getId());
            String auth = "Bearer " + tokenFor(user);
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                try {
                    mockMvc.perform(post("/api/v1/transactions").header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("accountId", account,
                                    "type", "EXPENSE", "amount", "19.73", "txnDate", "2026-10-18"))))
                            .andExpect(status().isCreated());
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                assertTrue(history.read(user.getId(), "2026-10", "2026-10").get(0).refreshPending());
                tx.setRollbackOnly();
            });
            assertFalse(history.read(user.getId(), "2026-10", "2026-10").get(0).refreshPending());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id=?",
                    Integer.class, user.getId()));
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", user.getId());
        }
    }

    @Test void repeatsSnapshotAndMovesTransactionBetweenExistingMonths() throws Exception {
        var user = createUser(Role.USER);
        String auth = "Bearer " + tokenFor(user);
        var december = new HistoryService.Key(user.getId(), "2025-12");
        var january = new HistoryService.Key(user.getId(), "2026-01");
        history.materialize(december, false);
        history.materialize(january, false);
        history.materialize(january, false);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM monthly_history WHERE user_id=?", Integer.class,
                user.getId()));
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?, 'History wallet','CASH')", user.getId());
        long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, user.getId());
        var created = mockMvc.perform(post("/api/v1/transactions").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("accountId", account,
                        "type", "INCOME", "amount", "17.21", "txnDate", "2025-12-31"))))
                .andExpect(status().isCreated()).andReturn();
        long id = jsonMapper.readTree(created.getResponse().getContentAsString()).path("id").longValue();
        var pending = history.read(user.getId(), "2025-12", "2025-12").get(0);
        assertTrue(pending.refreshPending());
        assertNull(pending.income());
        history.materialize(december, true);
        mockMvc.perform(put("/api/v1/transactions/" + id).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("accountId", account,
                        "type", "INCOME", "amount", "17.21", "txnDate", "2026-01-01"))))
                .andExpect(status().isOk());
        history.materialize(december, true);
        history.materialize(january, true);
        mockMvc.perform(get("/api/v1/history").param("from", "2025-12").param("to", "2026-01")
                .header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].income").value(0.0))
                .andExpect(jsonPath("$[1].income").value(17.21))
                .andExpect(jsonPath("$[1].score").isEmpty());
        assertTrue(history.read(createUser(Role.USER).getId(), "2025-12", "2026-01").isEmpty());
    }

    @Test void boundsAndRolesAreEnforced() throws Exception {
        var call = "/api/v1/history?from=2024-01&to=2026-01";
        mockMvc.perform(get(call)).andExpect(status().isUnauthorized());
        for (Role role : new Role[]{Role.ADMIN, Role.STAFF}) {
            mockMvc.perform(get(call).header("Authorization", "Bearer " + tokenFor(createUser(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(call).header("Authorization", "Bearer " + tokenFor(createUser(Role.USER))))
                .andExpect(status().isBadRequest());
    }
}
