package lk.finora.api.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class AdminStatsService {
    private final JdbcTemplate jdbc;

    AdminStatsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Counts read() {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*) FROM users WHERE role = 'USER' AND status = 'ACTIVE') AS active_users,
                  (SELECT COUNT(*) FROM users WHERE role = 'USER' AND status = 'DISABLED') AS disabled_users,
                  (SELECT COUNT(*) FROM users WHERE role = 'STAFF') AS staff,
                  (SELECT COUNT(*) FROM support_tickets WHERE status IN ('OPEN', 'IN_PROGRESS')) AS tickets,
                  (SELECT COUNT(*) FROM sms_formats WHERE active = TRUE) AS formats
                """, (row, n) -> new Counts(row.getLong("active_users"), row.getLong("disabled_users"),
                        row.getLong("staff"), row.getLong("tickets"), row.getLong("formats")));
    }

    record Counts(long activeUsers, long disabledUsers, long staff, long openTickets, long activeFormats) {}
}
