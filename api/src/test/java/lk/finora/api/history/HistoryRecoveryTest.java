package lk.finora.api.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class HistoryRecoveryTest extends IntegrationTest {
    private static final Instant RUN = Instant.parse("2021-02-01T00:00:00Z");
    @Autowired private HistoryService history;
    @Autowired private JdbcTemplate jdbc;

    @Test void cutoffUsesTheFirstFullColomboMonthAcrossNewYear() {
        assertEquals(YearMonth.of(2021, 1),
                HistoryRecovery.firstFullMonth(Instant.parse("2020-12-31T18:30:00Z")));
        assertEquals(YearMonth.of(2021, 2),
                HistoryRecovery.firstFullMonth(Instant.parse("2020-12-31T18:30:00.001Z")));
    }

    @Test void downtimeRecoveryKeepsTheCutoffEligibilityExactValuesAndExistingSnapshots() throws Exception {
        var owner = createUser(Role.USER);
        long id = owner.getId();
        createdAt(id, "2020-09-30T18:29:59");
        long late = createUser(Role.USER).getId();
        createdAt(late, "2020-10-31T18:30:00");
        long admin = createUser(Role.ADMIN).getId();
        createdAt(admin, "2020-08-01T00:00:00");
        history.materialize(new HistoryService.Key(id, "2020-11"), false);
        jdbc.update("INSERT INTO accounts (user_id,name,type) VALUES (?, 'Recovery wallet','CASH')", id);
        jdbc.update("""
                INSERT INTO transactions (user_id,account_id,type,amount,txn_date)
                SELECT user_id,id,'EXPENSE',9007199254740.21,'2020-12-12' FROM accounts WHERE user_id=?
                """, id);
        jdbc.update("""
                INSERT INTO transactions (user_id,account_id,type,amount,txn_date)
                SELECT user_id,id,'EXPENSE',9007199254740.22,'2020-12-12' FROM accounts WHERE user_id=?
                """, id);
        var worker = new HistoryRecovery(jdbc, history, "2020-09-15T00:00:00Z");
        worker.run(RUN);
        var rows = history.read(id, "2020-08", "2021-02");
        assertEquals(java.util.List.of("2020-10", "2020-11", "2020-12"),
                rows.stream().map(HistoryService.Month::month).toList());
        assertNull(rows.get(1).reconstructedAt());
        var december = rows.get(2);
        assertNotNull(december.reconstructedAt());
        assertEquals(new BigDecimal("-18014398509480.43"), december.net());
        assertNull(december.score());
        assertTrue(history.read(late, "2020-10", "2020-10").isEmpty());
        assertEquals(2, history.read(late, "2020-11", "2020-12").size());
        assertTrue(history.read(admin, "2020-10", "2020-12").isEmpty());
        new HistoryRecovery(jdbc, history, "").run(RUN);
        assertEquals(rows, history.read(id, "2020-08", "2021-02"));
        assertThrows(IllegalStateException.class,
                () -> new HistoryRecovery(jdbc, history, "2020-10-15T00:00:00Z").run(RUN));
        mockMvc.perform(get("/api/v1/history?from=2020-12&to=2020-12")
                .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reconstructedAt").isString());
        jdbc.update("UPDATE monthly_history SET dirty=TRUE WHERE user_id=? AND month='2020-12'", id);
        history.materialize(new HistoryService.Key(id, "2020-12"), true);
        assertEquals(december.reconstructedAt(), history.read(id, "2020-12", "2020-12").get(0).reconstructedAt());
        jdbc.update("DELETE FROM users WHERE id=?", id);
        assertTrue(history.read(id, "2020-10", "2020-12").isEmpty());
    }

    @Test void rollingWindowBatchesOldestFirstAndRetriesFailedOwnersIndependently() {
        for (int i = 0; i < 101; i++) {
            createdAt(createUser(Role.USER).getId(), "2020-07-01T00:00:00");
        }
        long deferred = createUser(Role.USER).getId();
        createdAt(deferred, "2020-07-01T00:00:00");
        jdbc.update("""
                INSERT INTO history_recovery_retry VALUES (?, '2020-08', UTC_TIMESTAMP(6) + INTERVAL 1 HOUR)
                """, deferred);
        var worker = new HistoryRecovery(jdbc, history, "2020-06-15T00:00:00Z");
        worker.run(RUN);
        assertEquals(100, count("2020-08"));
        assertEquals(0, count("2020-07"));
        assertEquals(0, count("2020-09"));
        worker.run(RUN);
        assertEquals(101, count("2020-08"));
        assertEquals(99, count("2020-09"));
        jdbc.update("UPDATE history_recovery_retry SET retry_at=UTC_TIMESTAMP(6) WHERE user_id=?", deferred);
        worker.run(RUN);
        assertEquals(102, count("2020-08"));
        jdbc.update("INSERT INTO history_recovery_retry VALUES (?, '2020-10', UTC_TIMESTAMP(6))", deferred);
        jdbc.update("DELETE FROM users WHERE id=?", deferred);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM history_recovery_retry WHERE user_id=?",
                Integer.class, deferred));
    }

    @Test void failedRecoveryIsDeferredWithoutBlockingOtherOwnersOrLosingWorkOnRestart() {
        long failed = createUser(Role.USER).getId();
        createdAt(failed, "2020-12-01T00:00:00");
        long other = createUser(Role.USER).getId();
        createdAt(other, "2020-12-01T00:00:00");
        new HistoryRecovery(jdbc, history, "").run(RUN);
        assertEquals(0, count("2020-12"));

        var failingWorker = mock(HistoryService.class);
        doAnswer(call -> {
            HistoryService.Key key = call.getArgument(0);
            if (key.userId() == failed) throw new IllegalStateException("Synthetic worker failure");
            history.recover(key, call.getArgument(1));
            return null;
        }).when(failingWorker).recover(any(), any());
        new HistoryRecovery(jdbc, failingWorker, "2020-11-15T00:00:00Z").run(RUN);
        assertTrue(history.read(failed, "2020-12", "2020-12").isEmpty());
        assertEquals(1, history.read(other, "2020-12", "2020-12").size());
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM history_recovery_retry WHERE user_id=? AND retry_at > UTC_TIMESTAMP(6)
                """, Integer.class, failed));
        new HistoryRecovery(jdbc, history, "").run(RUN);
        assertTrue(history.read(failed, "2020-12", "2020-12").isEmpty());
        jdbc.update("UPDATE history_recovery_retry SET retry_at=UTC_TIMESTAMP(6) WHERE user_id=?", failed);
        new HistoryRecovery(jdbc, history, "").run(RUN);
        assertEquals(1, history.read(failed, "2020-12", "2020-12").size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM history_recovery_retry WHERE user_id=?",
                Integer.class, failed));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void competingRecoveryWorkersLeaveOneSnapshot() throws Exception {
        long owner = createUser(Role.USER).getId();
        createdAt(owner, "2020-12-01T00:00:00");
        var threads = Executors.newFixedThreadPool(2);
        try {
            var gate = new CyclicBarrier(2);
            java.util.concurrent.Callable<Void> attempt = () -> {
                gate.await(5, TimeUnit.SECONDS);
                history.recover(new HistoryService.Key(owner, "2020-12"),
                        LocalDateTime.parse("2020-12-31T18:30:00"));
                return null;
            };
            var one = threads.submit(attempt);
            var two = threads.submit(attempt);
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
            var rows = history.read(owner, "2020-12", "2020-12");
            assertEquals(1, rows.size());
            assertNotNull(rows.get(0).reconstructedAt());
        } finally {
            threads.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id=?", owner);
        }
    }

    private void createdAt(long user, String instant) {
        jdbc.update("UPDATE users SET created_at=? WHERE id=?", LocalDateTime.parse(instant), user);
    }

    private int count(String month) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM monthly_history WHERE month=?", Integer.class, month);
    }
}
