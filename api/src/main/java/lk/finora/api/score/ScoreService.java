package lk.finora.api.score;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.analytics.MetricsService;
import lk.finora.api.account.FinanceWriteLock;
import lk.finora.api.history.HistoryInvalidation;
import lk.finora.api.analytics.MonthMetrics;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.score.dto.ScoreInputRequest;
import lk.finora.api.score.dto.ScoreInputResponse;
import lk.finora.api.score.dto.ScoreResponse;

/** D20 adapter: current recorded plans with dated contributions, not a reconstructed historical ledger. */
@Service
public class ScoreService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final MetricsService metrics;
    private final ScoreInputRepository inputs;
    private final JdbcTemplate jdbc;
    private final FinanceWriteLock writeLock;
    private final HistoryInvalidation history;

    public ScoreService(MetricsService metrics, ScoreInputRepository inputs, JdbcTemplate jdbc,
                        FinanceWriteLock writeLock, HistoryInvalidation history) {
        this.metrics = metrics; this.inputs = inputs; this.jdbc = jdbc;
        this.writeLock = writeLock;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public ScoreResponse forMonth(Long userId, String month) {
        YearMonth period = parseMonth(month);
        MonthMetrics m = metrics.forMonth(userId, period);
        ScoreInput input = inputs.findByUserIdAndMonth(userId, month).orElse(null);
        Sources s = sources(userId, period);
        boolean noDebt = input != null && Boolean.TRUE.equals(input.getNoApplicableDebt());
        boolean noGoals = input != null && Boolean.TRUE.equals(input.getNoApplicableGoals());
        BigDecimal due = s.debtDue();
        if ((due.signum() == 0 && (!noDebt || s.loanTransactions() > 0)) || (due.signum() > 0 && noDebt)) due = null;
        BigDecimal target = s.goalTarget();
        if ((target.signum() == 0 && !noGoals) || (target.signum() > 0 && noGoals)) target = null;
        return ScoreCalculator.calculate(month, new ScoreCalculator.Inputs(m.income().amount(), m.expense().amount(),
                m.savedToCategory().amount(), m.needs().amount(), due, s.debtPaid(), target, s.goalCredit(),
                input == null ? null : input.getEmergencyReserve(), input == null ? null : input.getReserveAsOf()));
    }

    @Transactional(readOnly = true)
    public ScoreInputResponse getInputs(Long userId, String month) {
        parseMonth(month);
        return response(month, inputs.findByUserIdAndMonth(userId, month).orElse(null));
    }

    @Transactional
    public ScoreInputResponse putInputs(Long userId, String month, ScoreInputRequest request) {
        writeLock.acquire(userId);
        YearMonth period = parseMonth(month);
        if ((request.emergencyReserve() == null) != (request.reserveAsOf() == null)) {
            throw ApiException.badRequest("Emergency reserve and date must be supplied together");
        }
        if (request.reserveAsOf() != null && !YearMonth.from(request.reserveAsOf()).equals(period)) {
            throw ApiException.badRequest("Emergency reserve date must belong to the requested month");
        }
        Sources s = sources(userId, period);
        if (Boolean.TRUE.equals(request.noApplicableDebt()) && (s.debtDue().signum() > 0 || s.loanTransactions() > 0)) {
            throw ApiException.badRequest("Recorded loan bills or transactions contradict no applicable debt");
        }
        if (Boolean.TRUE.equals(request.noApplicableGoals()) && s.goalTarget().signum() > 0) {
            throw ApiException.badRequest("Recorded goals contradict no applicable goals");
        }
        ScoreInput input = inputs.findByUserIdAndMonth(userId, month).orElseGet(() -> new ScoreInput(userId, month));
        input.confirm(request, LocalDateTime.now(ZoneOffset.UTC));
        inputs.save(input);
        history.monthChanged(userId, month);
        return response(month, input);
    }

    private ScoreInputResponse response(String month, ScoreInput input) {
        return input == null ? new ScoreInputResponse(month, null, null, null, null, null)
                : new ScoreInputResponse(month, input.getEmergencyReserve(), input.getReserveAsOf(),
                        input.getNoApplicableDebt(), input.getNoApplicableGoals(), input.getConfirmedAt().toInstant(ZoneOffset.UTC));
    }

    private record Sources(BigDecimal debtDue, BigDecimal debtPaid, int loanTransactions,
            BigDecimal goalTarget, BigDecimal goalCredit) {}

    private Sources sources(Long userId, YearMonth period) {
        // created_at is UTC; select plans that existed before the end of the selected Colombo month.
        LocalDateTime cutoff = LocalDateTime.ofInstant(period.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of("Asia/Colombo"))
                .toInstant(), ZoneOffset.UTC);
        BigDecimal[] debt = jdbc.queryForObject("""
                SELECT COALESCE(SUM(b.amount),0), COALESCE(SUM(CASE WHEN p.id IS NOT NULL THEN b.amount ELSE 0 END),0)
                FROM bills b JOIN categories c ON c.id=b.category_id
                LEFT JOIN bill_payments p ON p.bill_id=b.id AND p.month=?
                WHERE b.user_id=? AND b.active=TRUE AND b.created_at < ?
                  AND c.user_id IS NULL AND c.kind='EXPENSE' AND c.name='Loan payment'
                """, (rs, n) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2)},
                period.toString(), userId, cutoff);
        Integer loanTransactions = jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions t JOIN categories c ON c.id=t.category_id
                WHERE t.user_id=? AND t.type='EXPENSE' AND t.txn_date BETWEEN ? AND ?
                  AND c.user_id IS NULL AND c.kind='EXPENSE' AND c.name='Loan payment'
                """, Integer.class, userId, period.atDay(1), period.atEndOfMonth());
        List<BigDecimal[]> goals = jdbc.query("""
                SELECT g.target_amount, COALESCE(SUM(c.amount),0)
                FROM goals g LEFT JOIN goal_contributions c ON c.goal_id=g.id AND c.contrib_date<=?
                WHERE g.user_id=? AND g.status<>'CANCELLED' AND g.created_at < ?
                GROUP BY g.id, g.target_amount
                """, (rs, n) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2)},
                period.atEndOfMonth(), userId, cutoff);
        BigDecimal target = ZERO;
        BigDecimal credit = ZERO;
        for (BigDecimal[] goal : goals) {
            target = target.add(goal[0]);
            credit = credit.add(goal[1].min(goal[0]));
        }
        return new Sources(debt[0], debt[1], loanTransactions, target, credit);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])") || Integer.parseInt(month.substring(0, 4)) < 1000) {
            throw ApiException.badRequest("month must be YYYY-MM with year 1000-9999");
        }
        return YearMonth.parse(month);
    }
}
