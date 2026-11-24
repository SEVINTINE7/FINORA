package lk.finora.api.history;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class HistoryRecovery {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final int BATCH_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final HistoryService history;
    private final YearMonth configuredCutoff;
    private YearMonth reportedWindowStart;

    HistoryRecovery(JdbcTemplate jdbc, HistoryService history,
                    @Value("${finora.history.activation-instant:}") String activation) {
        this.jdbc = jdbc;
        this.history = history;
        configuredCutoff = activation.isBlank() ? null : firstFullMonth(Instant.parse(activation));
    }

    static YearMonth firstFullMonth(Instant activation) {
        YearMonth month = YearMonth.from(activation.atZone(COLOMBO));
        return activation.equals(start(month).toInstant(ZoneOffset.UTC)) ? month : month.plusMonths(1);
    }

    private static LocalDateTime start(YearMonth month) {
        return LocalDateTime.ofInstant(month.atDay(1).atStartOfDay(COLOMBO).toInstant(), ZoneOffset.UTC);
    }

    void run(Instant now) {
        if (configuredCutoff != null) {
            jdbc.update("""
                    INSERT INTO history_recovery_policy (id, first_month) VALUES (1, ?)
                    ON DUPLICATE KEY UPDATE id = 1
                    """, configuredCutoff.toString());
        }
        var cutoffs = jdbc.queryForList("SELECT first_month FROM history_recovery_policy WHERE id = 1", String.class);
        if (cutoffs.isEmpty()) return;
        YearMonth cutoff = HistoryService.parse(cutoffs.get(0));
        if (configuredCutoff != null && !configuredCutoff.equals(cutoff)) {
            throw new IllegalStateException("Configured history recovery cutoff differs from the stored cutoff");
        }
        YearMonth previous = HistoryScheduler.previousMonth(now);
        YearMonth windowStart = previous.minusMonths(5);
        if (windowStart.isAfter(cutoff) && !windowStart.equals(reportedWindowStart)) {
            LoggerFactory.getLogger(HistoryRecovery.class).info(
                    "Any history gaps from {} through {} are outside automatic recovery and need owner-directed review",
                    cutoff, windowStart.minusMonths(1));
            reportedWindowStart = windowStart;
        }
        int remaining = BATCH_SIZE;
        for (YearMonth month = cutoff.isAfter(windowStart) ? cutoff : windowStart;
             month.isBefore(previous) && remaining > 0; month = month.plusMonths(1)) {
            remaining -= recoverMonth(month, remaining);
        }
    }

    private int recoverMonth(YearMonth month, int limit) {
        var end = start(month.plusMonths(1));
        String target = month.toString();
        var candidates = jdbc.queryForList("""
                SELECT u.id FROM users u
                LEFT JOIN monthly_history h ON h.user_id = u.id AND h.month = ?
                LEFT JOIN history_recovery_retry r ON r.user_id = u.id AND r.month = ?
                WHERE u.role = 'USER' AND u.created_at < ? AND h.user_id IS NULL
                  AND (r.retry_at IS NULL OR r.retry_at <= UTC_TIMESTAMP(6))
                ORDER BY r.retry_at, u.id LIMIT ?
                """, Long.class, target, target, end, limit);
        for (long id : candidates) {
            try {
                history.recover(new HistoryService.Key(id, target), end);
            } catch (RuntimeException failure) {
                LoggerFactory.getLogger(HistoryRecovery.class)
                        .warn("History recovery for {} remains pending: {}", target, failure.getClass().getSimpleName());
                jdbc.update("""
                        INSERT INTO history_recovery_retry (user_id, month, retry_at)
                        SELECT id, ?, UTC_TIMESTAMP(6) + INTERVAL 30 MINUTE FROM users
                        WHERE id = ? AND NOT EXISTS (
                            SELECT 1 FROM monthly_history WHERE user_id = users.id AND month = ?
                        )
                        ON DUPLICATE KEY UPDATE retry_at = UTC_TIMESTAMP(6) + INTERVAL 30 MINUTE
                        """, target, id, target);
            }
        }
        return candidates.size();
    }
}
