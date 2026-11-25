package lk.finora.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lk.finora.api.bill.BillService;
import lk.finora.api.budget.BudgetService;
import lk.finora.api.goal.GoalService;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;
import lk.finora.api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T34.2–T34.4: which events the daily job creates, that a second run sends nothing new,
 * opt-outs, consent/status/device gating, invalid-token clean-up.
 * "Today" is 2026-02-26, so a bill with due day 31 falls on 2026-02-28 = today + 2.
 */
class AlertServiceTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 2, 26);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private BudgetService budgets;
    @Autowired private BillService bills;
    @Autowired private GoalService goals;
    @Autowired private DeviceService devices;

    private final RecordingSender sender = new RecordingSender();
    private AlertService alerts;
    private User user;
    private String token;

    /** Pretends to be FCM: records what would be pushed. */
    static final class RecordingSender implements PushSender {
        final List<String> sent = new ArrayList<>();
        boolean deliver = true;
        List<String> invalid = List.of();

        @Override
        public Result send(List<String> tokens, AlertType type) {
            tokens.forEach(t -> sent.add(t + "|" + type));
            return new Result(deliver && !tokens.isEmpty(), invalid);
        }

        List<String> forToken(String token) {
            return sent.stream().filter(s -> s.startsWith(token + "|")).map(s -> s.substring(token.length() + 1)).toList();
        }
    }

    @BeforeEach
    void setUp() {
        alerts = new AlertService(jdbc, budgets, bills, goals, devices, sender);
        user = alertUser();
        token = "synthetic-device-" + user.getId();
        jdbc.update("INSERT INTO device_tokens (user_id, token) VALUES (?, ?)", user.getId(), token);
    }

    private User alertUser() {
        User created = createUser(Role.USER);
        jdbc.update("INSERT INTO consents (user_id, version, over18) VALUES (?, '1.0', TRUE)", created.getId());
        return created;
    }

    private Long category(String name) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name = ?", Long.class, name);
    }

    /** Budget for Feb 2026 with the given limit and spending. */
    private Long budget(User owner, String categoryName, String limit, String spent) {
        Long categoryId = category(categoryName);
        jdbc.update("INSERT INTO budgets (user_id, category_id, month, limit_amount) VALUES (?, ?, '2026-02', ?)",
                owner.getId(), categoryId, limit);
        jdbc.update("INSERT INTO accounts (user_id, name, type) VALUES (?, ?, 'CASH')", owner.getId(), "Cash " + categoryName);
        Long account = jdbc.queryForObject("SELECT MAX(id) FROM accounts WHERE user_id = ?", Long.class, owner.getId());
        jdbc.update("INSERT INTO transactions (user_id, account_id, category_id, type, amount, txn_date) "
                + "VALUES (?, ?, ?, 'EXPENSE', ?, '2026-02-10')", owner.getId(), account, categoryId, spent);
        return jdbc.queryForObject("SELECT MAX(id) FROM budgets WHERE user_id = ?", Long.class, owner.getId());
    }

    private Long bill(User owner, String name, int dueDay) {
        jdbc.update("INSERT INTO bills (user_id, name, amount, due_day) VALUES (?, ?, 1500.00, ?)", owner.getId(), name, dueDay);
        return jdbc.queryForObject("SELECT MAX(id) FROM bills WHERE user_id = ?", Long.class, owner.getId());
    }

    private Long goal(User owner, String targetDate, String status) {
        jdbc.update("INSERT INTO goals (user_id, name, target_amount, target_date, status) VALUES (?, 'Trip', 50000.00, ?, ?)",
                owner.getId(), targetDate, status);
        return jdbc.queryForObject("SELECT MAX(id) FROM goals WHERE user_id = ?", Long.class, owner.getId());
    }

    private List<String> events(User owner) {
        return jdbc.queryForList("SELECT CONCAT(type, ':', ref_id, ':', period) FROM alert_events WHERE user_id = ? ORDER BY id",
                String.class, owner.getId());
    }

    @Test
    void createsEachKindOfEventOnTheRightDay() {
        Long near = budget(user, "Food", "100.00", "85.00");       // 85% → NEAR
        Long over = budget(user, "Transport", "50.00", "60.00");   // over the limit → OVER
        budget(user, "Shopping", "1000.00", "10.00");               // OK → nothing
        Long rent = bill(user, "Rent", 31);                         // Feb has 28 days → due 2026-02-28
        Long paid = bill(user, "Water", 28);
        jdbc.update("INSERT INTO bill_payments (bill_id, month) VALUES (?, '2026-02')", paid);
        bill(user, "Phone", 1);                                     // 2026-03-01 is 3 days away → nothing
        Long soon = goal(user, "2026-03-05", "ACTIVE");             // exactly 7 days away
        goal(user, "2026-03-06", "ACTIVE");                         // 8 days → nothing
        goal(user, "2026-03-01", "CANCELLED");                      // cancelled → nothing
        goal(user, "2026-02-20", "ACTIVE");                         // already passed → nothing

        alerts.runDaily(TODAY);

        assertThat(events(user)).containsExactlyInAnyOrder(
                "BUDGET_NEAR:" + near + ":2026-02",
                "BUDGET_OVER:" + over + ":2026-02",
                "BILL_DUE:" + rent + ":2026-02",
                "GOAL_DATE_NEAR:" + soon + ":2026-03-05");
        assertThat(sender.forToken(token)).containsExactlyInAnyOrder(
                "BUDGET_NEAR", "BUDGET_OVER", "BILL_DUE", "GOAL_DATE_NEAR");
    }

    @Test
    void jobRunTwiceSendsOnce() {
        budget(user, "Food", "100.00", "90.00");
        bill(user, "Rent", 28);

        int first = alerts.runDaily(TODAY);
        int second = alerts.runDaily(TODAY);

        assertThat(first).isEqualTo(2);
        assertThat(second).isZero();
        assertThat(events(user)).hasSize(2);
        assertThat(sender.forToken(token)).containsExactlyInAnyOrder("BUDGET_NEAR", "BILL_DUE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alert_events WHERE user_id = ? AND sent_at IS NOT NULL",
                Integer.class, user.getId())).isEqualTo(2);
    }

    @Test
    void optedOutUserGetsNothing() {
        jdbc.update("UPDATE users SET alert_budget = FALSE, alert_bill = FALSE, alert_goal = FALSE WHERE id = ?", user.getId());
        budget(user, "Food", "100.00", "150.00");
        bill(user, "Rent", 28);
        goal(user, "2026-03-01", "ACTIVE");

        alerts.runDaily(TODAY);

        assertThat(events(user)).isEmpty();
        assertThat(sender.forToken(token)).isEmpty();
    }

    @Test
    void optOutIsPerType() {
        jdbc.update("UPDATE users SET alert_budget = FALSE WHERE id = ?", user.getId());
        budget(user, "Food", "100.00", "150.00");
        bill(user, "Rent", 28);

        alerts.runDaily(TODAY);

        assertThat(sender.forToken(token)).containsExactly("BILL_DUE");
    }

    @Test
    void turningATypeOffBeforeARetryStopsTheUnsentEvent() {
        sender.deliver = false;
        bill(user, "Rent", 28);
        alerts.runDaily(TODAY);
        assertThat(sender.forToken(token)).containsExactly("BILL_DUE");

        jdbc.update("UPDATE users SET alert_bill = FALSE WHERE id = ?", user.getId());
        sender.deliver = true;
        assertThat(alerts.sendPending()).isZero();
        assertThat(sender.forToken(token)).containsExactly("BILL_DUE");
    }

    @Test
    void undeliveredEventIsRetriedOnceThenNotAgain() {
        sender.deliver = false;
        bill(user, "Rent", 28);
        alerts.runDaily(TODAY);

        sender.deliver = true;
        alerts.sendPending();
        alerts.sendPending();
        alerts.runDaily(TODAY);

        assertThat(sender.forToken(token)).containsExactly("BILL_DUE", "BILL_DUE");
        assertThat(events(user)).hasSize(1);
    }

    @Test
    void noConsentDisabledOrNoPhoneMeansNoAlerts() {
        User withdrawn = alertUser();
        jdbc.update("UPDATE consents SET withdrawn_at = NOW(6) WHERE user_id = ?", withdrawn.getId());
        User disabled = alertUser();
        disabled.setStatus(UserStatus.DISABLED);
        userRepository.saveAndFlush(disabled);
        User noPhone = alertUser();
        for (User other : List.of(withdrawn, disabled)) {
            jdbc.update("INSERT INTO device_tokens (user_id, token) VALUES (?, ?)", other.getId(), "synthetic-device-" + other.getId());
        }
        for (User other : List.of(withdrawn, disabled, noPhone)) {
            bill(other, "Rent", 28);
        }

        alerts.runDaily(TODAY);

        for (User other : List.of(withdrawn, disabled, noPhone)) {
            assertThat(events(other)).isEmpty();
            assertThat(sender.forToken("synthetic-device-" + other.getId())).isEmpty();
        }
    }

    @Test
    void staffAndAdminNeverGetAlerts() {
        List<Long> privileged = new ArrayList<>();
        for (Role role : List.of(Role.STAFF, Role.ADMIN)) {
            User other = createUser(role);
            jdbc.update("INSERT INTO consents (user_id, version, over18) VALUES (?, '1.0', TRUE)", other.getId());
            jdbc.update("INSERT INTO device_tokens (user_id, token) VALUES (?, ?)", other.getId(), "synthetic-device-" + other.getId());
            privileged.add(other.getId());
        }
        List<Long> ids = alerts.recipients().stream().map(AlertService.Recipient::id).toList();
        assertThat(ids).contains(user.getId()).doesNotContainAnyElementsOf(privileged);
    }

    @Test
    void tokensFcmRejectsAreRemoved() {
        sender.invalid = List.of(token);
        bill(user, "Rent", 28);

        alerts.runDaily(TODAY);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM device_tokens WHERE token = ?", Integer.class, token)).isZero();
    }

    @Test
    void oneUsersFailureDoesNotStopOthers() {
        User other = alertUser();
        String otherToken = "synthetic-device-" + other.getId();
        jdbc.update("INSERT INTO device_tokens (user_id, token) VALUES (?, ?)", other.getId(), otherToken);
        bill(other, "Rent", 28);
        // A failing user: an illegal month string can't happen via the API, so break the service instead
        AlertService failing = new AlertService(jdbc, budgets, bills, goals, devices, sender) {
            @Override
            int record(Recipient recipient, LocalDate today) {
                if (recipient.id() == user.getId()) {
                    throw new IllegalStateException("synthetic failure");
                }
                return super.record(recipient, today);
            }
        };

        failing.runDaily(TODAY);

        assertThat(sender.forToken(otherToken)).containsExactly("BILL_DUE");
    }
}
