package lk.finora.api.advice.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lk.finora.api.advice.ai.CoachingInputs;
import lk.finora.api.advice.ai.CoachingInputs.Bucket;

/** Aggregate-only reads for D58 coaching. Every query is scoped to the caller's user id. */
@Repository
public class CoachingQueries {
    private static final BigDecimal THREE = new BigDecimal("3");
    private final JdbcTemplate jdbc;

    public CoachingQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public CoachingInputs load(long userId, YearMonth month, Long goalId) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        LocalDate priorFrom = month.minusMonths(3).atDay(1);
        LocalDate priorTo = from.minusDays(1);
        // Custom category names can hold personal text, so they are grouped as "Other want" / "Other need".
        List<Row> rows = jdbc.query("""
                SELECT x.label, x.kind,
                       SUM(CASE WHEN x.txn_date BETWEEN ? AND ? THEN x.amount ELSE 0 END) AS spend,
                       SUM(CASE WHEN x.txn_date BETWEEN ? AND ? THEN x.amount ELSE 0 END) AS prior
                FROM (SELECT CASE WHEN c.user_id IS NULL THEN c.name
                                  WHEN c.need_or_want = 'WANT' THEN 'Other want' ELSE 'Other need' END AS label,
                             c.need_or_want AS kind, t.txn_date, t.amount
                      FROM transactions t JOIN categories c ON c.id = t.category_id
                      WHERE t.user_id = ? AND t.type = 'EXPENSE' AND c.need_or_want IS NOT NULL
                        AND t.txn_date BETWEEN ? AND ?) x
                GROUP BY x.label, x.kind
                """, (rs, n) -> new Row(rs.getString("label"), rs.getString("kind"),
                rs.getBigDecimal("spend").setScale(2, RoundingMode.UNNECESSARY),
                rs.getBigDecimal("prior").setScale(2, RoundingMode.UNNECESSARY)),
                Date.valueOf(from), Date.valueOf(to), Date.valueOf(priorFrom), Date.valueOf(priorTo),
                userId, Date.valueOf(priorFrom), Date.valueOf(to));
        List<Bucket> wants = top(rows, "WANT", 3);
        List<Bucket> needs = top(rows, "NEED", 2);

        BigDecimal[] timing = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN DAY(t.txn_date) <= 10 THEN t.amount END), 0) AS early,
                       COALESCE(SUM(CASE WHEN DAY(t.txn_date) BETWEEN 11 AND 20 THEN t.amount END), 0) AS middle,
                       COALESCE(SUM(CASE WHEN DAY(t.txn_date) >= 21 THEN t.amount END), 0) AS late
                FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ? AND t.type = 'EXPENSE' AND t.txn_date BETWEEN ? AND ?
                  AND NOT (c.id IS NOT NULL AND c.user_id IS NULL AND c.semantic_code <=> 'SAVINGS')
                """, (rs, n) -> new BigDecimal[] {money(rs.getBigDecimal("early")), money(rs.getBigDecimal("middle")),
                money(rs.getBigDecimal("late"))}, userId, Date.valueOf(from), Date.valueOf(to));

        CoachingInputs.Goal goal = goalId == null ? null : jdbc.query("""
                SELECT g.target_amount, g.target_date,
                       COALESCE((SELECT SUM(amount) FROM goal_contributions WHERE goal_id = g.id AND contrib_date <= ?), 0) AS saved,
                       COALESCE((SELECT SUM(amount) FROM goal_contributions WHERE goal_id = g.id
                                 AND contrib_date BETWEEN ? AND ?), 0) AS recent
                FROM goals g WHERE g.id = ? AND g.user_id = ?
                """, (rs, n) -> new CoachingInputs.Goal(money(rs.getBigDecimal("target_amount")),
                money(rs.getBigDecimal("saved")), money(rs.getBigDecimal("recent")),
                rs.getDate("target_date") == null ? null : rs.getDate("target_date").toLocalDate()),
                Date.valueOf(to), Date.valueOf(month.minusMonths(2).atDay(1)), Date.valueOf(to), goalId, userId)
                .stream().findFirst().orElse(null);
        return new CoachingInputs(wants, needs, timing[0], timing[1], timing[2], goal);
    }

    private static List<Bucket> top(List<Row> rows, String kind, int limit) {
        return rows.stream().filter(r -> kind.equals(r.kind) && r.spend.signum() > 0)
                .sorted(Comparator.comparing((Row r) -> r.spend).reversed().thenComparing(r -> r.label))
                .limit(limit)
                .map(r -> new Bucket(r.label, r.spend, r.prior.divide(THREE, 2, RoundingMode.HALF_UP)))
                .toList();
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private record Row(String label, String kind, BigDecimal spend, BigDecimal prior) {}
}
