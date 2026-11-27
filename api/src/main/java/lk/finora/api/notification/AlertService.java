package lk.finora.api.notification;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lk.finora.api.bill.BillService;
import lk.finora.api.bill.dto.BillMonthResponse;
import lk.finora.api.budget.BudgetService;
import lk.finora.api.budget.dto.BudgetResponse;
import lk.finora.api.goal.GoalService;
import lk.finora.api.goal.GoalStatus;
import lk.finora.api.goal.dto.GoalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * T34.2–T34.3 budget, bill and goal alerts.
 * <ul>
 *   <li>Budget NEAR / OVER for the current month (same rule as T17.2).</li>
 *   <li>Unpaid bill due in {@value #BILL_DAYS_AHEAD} days (T18 due-date rule, so day 31 = month end).</li>
 *   <li>Active goal whose target date is {@value #GOAL_DAYS_AHEAD} days away or less (D57).</li>
 * </ul>
 * Each event is stored once (unique user/type/ref/period), so running the job again never sends it again.
 * Only ACTIVE USER accounts with consent, at least one phone and that alert type turned on are checked.
 */
@Service
public class AlertService {

    static final int BILL_DAYS_AHEAD = 2;
    static final int GOAL_DAYS_AHEAD = 7;

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    // Shared by both passes: who may receive alerts at all
    private static final String ELIGIBLE = """
            u.role = 'USER' AND u.status = 'ACTIVE'
            AND EXISTS (SELECT 1 FROM consents c WHERE c.user_id = u.id AND c.withdrawn_at IS NULL)
            AND EXISTS (SELECT 1 FROM device_tokens d WHERE d.user_id = u.id)
            """;

    private final JdbcTemplate jdbc;
    private final BudgetService budgets;
    private final BillService bills;
    private final GoalService goals;
    private final DeviceService devices;
    private final PushSender push;

    public AlertService(JdbcTemplate jdbc, BudgetService budgets, BillService bills, GoalService goals,
            DeviceService devices, PushSender push) {
        this.jdbc = jdbc;
        this.budgets = budgets;
        this.bills = bills;
        this.goals = goals;
        this.devices = devices;
        this.push = push;
    }

    /** Daily pass: records new events for every eligible user, then sends what is unsent. Returns new events. */
    public int runDaily(LocalDate today) {
        int created = 0;
        for (Recipient recipient : recipients()) {
            try {
                created += record(recipient, today);
            } catch (RuntimeException failure) {
                // One user's bad data must not stop everyone else's alerts; no user details in the log
                log.warn("Alert check failed for one user: {}", failure.getClass().getSimpleName());
            }
        }
        sendPending();
        return created;
    }

    /**
     * Sends events from the last 24 hours that no phone has accepted yet. A type the user has since turned
     * off is skipped. Returns how many events were delivered.
     */
    public int sendPending() {
        List<Pending> pending = jdbc.query("""
                SELECT e.id, e.user_id, e.type FROM alert_events e JOIN users u ON u.id = e.user_id
                WHERE e.sent_at IS NULL AND e.created_at >= NOW(6) - INTERVAL 1 DAY AND
                """ + ELIGIBLE + """
                AND ((e.type IN ('BUDGET_NEAR', 'BUDGET_OVER') AND u.alert_budget)
                     OR (e.type = 'BILL_DUE' AND u.alert_bill)
                     OR (e.type = 'GOAL_DATE_NEAR' AND u.alert_goal))
                ORDER BY e.id LIMIT 500
                """, (rs, n) -> new Pending(rs.getLong(1), rs.getLong(2), AlertType.valueOf(rs.getString(3))));
        int delivered = 0;
        for (Pending event : pending) {
            List<String> tokens = devices.tokens(event.userId());
            PushSender.Result result = push.send(tokens, event.type());
            if (!result.invalidTokens().isEmpty()) {
                devices.forget(result.invalidTokens());
            }
            if (result.delivered()) {
                delivered += jdbc.update("UPDATE alert_events SET sent_at = NOW(6) WHERE id = ? AND sent_at IS NULL",
                        event.id());
            }
        }
        return delivered;
    }

    List<Recipient> recipients() {
        return jdbc.query("""
                SELECT u.id, u.alert_budget, u.alert_bill, u.alert_goal FROM users u
                WHERE (u.alert_budget OR u.alert_bill OR u.alert_goal) AND
                """ + ELIGIBLE + " ORDER BY u.id",
                (rs, n) -> new Recipient(rs.getLong(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4)));
    }

    /** Stores today's events for one user; returns how many were new. */
    int record(Recipient user, LocalDate today) {
        List<Event> events = new ArrayList<>();
        if (user.budget()) {
            for (BudgetResponse budget : budgets.list(user.id(), YearMonth.from(today).toString())) {
                if ("OVER".equals(budget.state())) {
                    events.add(new Event(AlertType.BUDGET_OVER, budget.id(), budget.month()));
                } else if ("NEAR".equals(budget.state())) {
                    events.add(new Event(AlertType.BUDGET_NEAR, budget.id(), budget.month()));
                }
            }
        }
        if (user.bill()) {
            LocalDate due = today.plusDays(BILL_DAYS_AHEAD);
            for (BillMonthResponse bill : bills.listForMonth(user.id(), YearMonth.from(due).toString())) {
                if (!bill.paid() && due.equals(bill.dueDate())) {
                    events.add(new Event(AlertType.BILL_DUE, bill.id(), bill.month()));
                }
            }
        }
        if (user.goal()) {
            LocalDate last = today.plusDays(GOAL_DAYS_AHEAD);
            for (GoalResponse goal : goals.list(user.id())) {
                LocalDate target = goal.targetDate();
                if (goal.status() == GoalStatus.ACTIVE && target != null
                        && !target.isBefore(today) && !target.isAfter(last)) {
                    events.add(new Event(AlertType.GOAL_DATE_NEAR, goal.id(), target.toString()));
                }
            }
        }
        int created = 0;
        for (Event event : events) {
            try {
                created += jdbc.update("""
                        INSERT INTO alert_events (user_id, type, ref_id, period)
                        SELECT id,?,?,? FROM users WHERE id=? AND role='USER' FOR UPDATE
                        """, event.type().name(), event.refId(), event.period(), user.id());
            } catch (DuplicateKeyException alreadyRecorded) {
                // The unique key holds this event already, so a repeated run adds (and sends) nothing
            }
        }
        return created;
    }

    record Recipient(long id, boolean budget, boolean bill, boolean goal) {}

    private record Event(AlertType type, long refId, String period) {}

    private record Pending(long id, long userId, AlertType type) {}
}
