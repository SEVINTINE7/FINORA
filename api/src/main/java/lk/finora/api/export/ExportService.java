package lk.finora.api.export;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.finora.api.common.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {
    // Each query identifies its owner, including child rows without a direct user_id.
    private static final Map<String, String> DATA = Map.ofEntries(
            Map.entry("consents", "SELECT version, over18, accepted_at, withdrawn_at FROM consents WHERE user_id = ? ORDER BY id"),
            Map.entry("accounts", "SELECT id, name, type, opening_balance, archived, created_at FROM accounts WHERE user_id = ? ORDER BY id"),
            Map.entry("categories", "SELECT id, name, name_si, kind, need_or_want, active, created_at FROM categories WHERE user_id = ? ORDER BY id"),
            Map.entry("transactions", """
                    SELECT id, account_id, category_id, type, amount, CAST(txn_date AS CHAR) AS txn_date, merchant, note, source, created_at, updated_at
                    FROM transactions WHERE user_id = ? ORDER BY id
                    """),
            Map.entry("budgets", "SELECT id, category_id, month, limit_amount, created_at FROM budgets WHERE user_id = ? ORDER BY id"),
            Map.entry("bills", "SELECT id, name, amount, due_day, category_id, active, created_at FROM bills WHERE user_id = ? ORDER BY id"),
            Map.entry("billPayments", """
                    SELECT p.id, p.bill_id, p.month, p.paid_at FROM bill_payments p JOIN bills b ON b.id=p.bill_id
                    WHERE b.user_id = ? ORDER BY p.id
                    """),
            Map.entry("goals", "SELECT id, name, target_amount, CAST(target_date AS CHAR) AS target_date, status, created_at FROM goals WHERE user_id = ? ORDER BY id"),
            Map.entry("goalContributions", """
                    SELECT c.id, c.goal_id, c.amount, CAST(c.contrib_date AS CHAR) AS contrib_date, c.created_at FROM goal_contributions c
                    JOIN goals g ON g.id=c.goal_id WHERE g.user_id = ? ORDER BY c.id
                    """),
            Map.entry("insights", "SELECT month, type, facts, created_at FROM insights WHERE user_id = ? ORDER BY id"),
            Map.entry("healthScores", "SELECT month, total, parts, created_at FROM health_scores WHERE user_id = ? ORDER BY id"),
            Map.entry("scoreInputs", """
                    SELECT month, emergency_reserve, CAST(reserve_as_of AS CHAR) AS reserve_as_of, no_applicable_debt, no_applicable_goals, confirmed_at
                    FROM score_inputs WHERE user_id = ? ORDER BY id
                    """),
            Map.entry("history", "SELECT month, income, expense, score, insights FROM monthly_history WHERE user_id = ? ORDER BY month"),
            Map.entry("advice", "SELECT id, month, source, mode, body, verified, created_at FROM advice WHERE user_id = ? ORDER BY id"),
            Map.entry("ratings", "SELECT advice_id, rating, created_at FROM advice_ratings WHERE user_id = ? ORDER BY id"),
            Map.entry("tickets", "SELECT id, subject, body, status, error_code, created_at FROM support_tickets WHERE user_id = ? ORDER BY id"),
            Map.entry("ticketReplies", """
                    SELECT r.ticket_id, r.body, r.created_at FROM ticket_replies r JOIN support_tickets t ON t.id=r.ticket_id
                    WHERE t.user_id = ? ORDER BY r.id
                    """),
            Map.entry("smsDiagnostics", """
                    SELECT bank_code, format_version, error_code, CAST(report_day AS CHAR) AS report_day FROM sms_diagnostic_reports
                    WHERE user_id = ? ORDER BY report_day, bank_code, format_version, error_code
                    """));
    private final JdbcTemplate jdbc;

    ExportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot read(long id) {
        var profiles = jdbc.queryForList("""
                SELECT id, email, full_name, language, created_at, updated_at FROM users WHERE id = ? AND role = 'USER'
                """, id);
        if (profiles.isEmpty()) throw ApiException.notFound("User not found");
        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        DATA.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> data.put(entry.getKey(), jdbc.queryForList(entry.getValue(), id)));
        return new Snapshot(1, profiles.get(0), data);
    }

    public record Snapshot(int schemaVersion, Map<String, Object> profile, Map<String, List<Map<String, Object>>> data) {}
}
