package lk.finora.api.history;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.account.FinanceWriteLock;
import lk.finora.api.analytics.MetricsService;
import lk.finora.api.analytics.PatternRules;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.score.ScoreService;
import tools.jackson.databind.json.JsonMapper;

@Service
public class HistoryService {
    private final JdbcTemplate jdbc;
    private final FinanceWriteLock lock;
    private final MetricsService metrics;
    private final ScoreService scores;
    private final JsonMapper json;

    public record Month(String month, BigDecimal income, BigDecimal expense, BigDecimal net,
                        Integer score, List<Pattern> insights, boolean refreshPending, Instant reconstructedAt) {}
    public record Pattern(String type, Map<String, BigDecimal> facts) {}
    private record StoredPattern(String type, Map<String, String> facts) {}
    record Key(long userId, String month) {}

    public HistoryService(JdbcTemplate jdbc, FinanceWriteLock lock, MetricsService metrics,
                          ScoreService scores, JsonMapper json) {
        this.jdbc = jdbc;
        this.lock = lock;
        this.metrics = metrics;
        this.scores = scores;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<Month> read(long userId, String from, String to) {
        long span = ChronoUnit.MONTHS.between(parse(from), parse(to));
        if (span < 0 || span >= 24) throw ApiException.badRequest("Select an ordered range of up to 24 months");
        return jdbc.query("""
                SELECT month, income, expense, score, insights, dirty, reconstructed_at
                FROM monthly_history WHERE user_id = ? AND month BETWEEN ? AND ? ORDER BY month
                """, (rs, n) -> {
            var timestamp = rs.getObject("reconstructed_at", LocalDateTime.class);
            Instant reconstructedAt = timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
            if (rs.getBoolean("dirty")) {
                return new Month(rs.getString("month"), null, null, null, null, List.of(), true, reconstructedAt);
            }
            BigDecimal income = rs.getBigDecimal("income");
            BigDecimal expense = rs.getBigDecimal("expense");
            return new Month(rs.getString("month"), income, expense, income.subtract(expense),
                    rs.getObject("score", Integer.class),
                    List.of(json.readValue(rs.getString("insights"), Pattern[].class)), false, reconstructedAt);
        }, userId, from, to);
    }

    List<Key> missing(String month) {
        return jdbc.query("""
                SELECT u.id FROM users u WHERE u.role = 'USER'
                    AND NOT EXISTS (SELECT 1 FROM monthly_history h WHERE h.user_id = u.id AND h.month = ?)
                ORDER BY u.id LIMIT 100
                """, (rs, n) -> new Key(rs.getLong(1), month), month);
    }

    List<Key> dirty() {
        return jdbc.query("""
                SELECT user_id, month FROM monthly_history WHERE dirty = TRUE
                ORDER BY user_id, month LIMIT 100
                """, (rs, n) -> new Key(rs.getLong(1), rs.getString(2)));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void materialize(Key key, boolean existingOnly) {
        lock.acquire(key.userId());
        List<Boolean> dirty = jdbc.queryForList(
                "SELECT dirty FROM monthly_history WHERE user_id = ? AND month = ?",
                Boolean.class, key.userId(), key.month());
        if ((dirty.isEmpty() && existingOnly) || (!dirty.isEmpty() && !dirty.get(0))) return;
        writeSnapshot(key, false);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void recover(Key key, LocalDateTime endExclusiveUtc) {
        lock.acquire(key.userId());
        boolean eligible = !jdbc.queryForList("""
                SELECT id FROM users WHERE id = ? AND role = 'USER' AND created_at < ?
                """, Long.class, key.userId(), endExclusiveUtc).isEmpty();
        if (eligible && jdbc.queryForList("""
                SELECT user_id FROM monthly_history WHERE user_id = ? AND month = ?
                """, Long.class, key.userId(), key.month()).isEmpty()) {
            writeSnapshot(key, true);
        }
        jdbc.update("DELETE FROM history_recovery_retry WHERE user_id = ? AND month = ?", key.userId(), key.month());
    }

    private void writeSnapshot(Key key, boolean reconstructed) {
        YearMonth month = parse(key.month());
        var data = metrics.forMonth(key.userId(), month);
        var score = scores.forMonth(key.userId(), key.month());
        var patterns = PatternRules.detect(data).stream().map(pattern -> new StoredPattern(pattern.type(),
                pattern.facts().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().toString())))).toList();
        jdbc.update("""
                INSERT INTO monthly_history (user_id, month, income, expense, score, insights, dirty, reconstructed_at)
                VALUES (?, ?, ?, ?, ?, ?, FALSE, IF(?, UTC_TIMESTAMP(6), NULL)) AS incoming
                ON DUPLICATE KEY UPDATE income = incoming.income, expense = incoming.expense,
                    score = incoming.score, insights = incoming.insights, dirty = FALSE
                """, key.userId(), key.month(), data.income().amount(), data.expense().amount(),
                score.total(), json.writeValueAsString(patterns), reconstructed);
    }

    static YearMonth parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])"))
            throw ApiException.badRequest("Months must be YYYY-MM with a year from 1000 to 9999");
        return YearMonth.parse(value);
    }
}
