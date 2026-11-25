package lk.finora.api.admin;

import java.util.List;
import java.util.Set;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.user.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminUserService {
    private static final Set<String> REASONS = Set.of("SECURITY", "POLICY", "SUPPORT", "RESTORED");
    private final JdbcTemplate jdbc;

    AdminUserService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<AdminUserController.UserSummary> search(String query) {
        String text = query.strip();
        if (text.length() > 120) throw ApiException.badRequest("Search text is too long");
        return jdbc.query("""
                SELECT id, email, full_name, status, created_at FROM users
                WHERE role = 'USER' AND (LOCATE(?, email) > 0 OR LOCATE(?, full_name) > 0)
                ORDER BY id DESC LIMIT 100
                """, (row, index) -> new AdminUserController.UserSummary(row.getLong("id"),
                row.getString("email"), row.getString("full_name"), UserStatus.valueOf(row.getString("status")),
                row.getObject("created_at", java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC)),
                text, text);
    }

    @Transactional
    public void changeStatus(long admin, long id, UserStatus status, String reason) {
        if (!REASONS.contains(reason)) throw ApiException.badRequest("Choose a supported status reason");
        var rows = jdbc.queryForList("""
                SELECT status FROM users WHERE id = ? AND role = 'USER' FOR UPDATE
                """, String.class, id);
        if (rows.isEmpty()) throw ApiException.notFound("User not found");
        if (status.name().equals(rows.get(0))) return;
        if (status == UserStatus.DISABLED) {
            jdbc.update("""
                    UPDATE users SET status = 'DISABLED', staff_session_generation = staff_session_generation + 1
                    WHERE id = ?
                    """, id);
            jdbc.update("""
                    UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP(6)
                    WHERE user_id = ? AND revoked_at IS NULL
                    """, id);
        } else {
            jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", id);
        }
        jdbc.update("""
                INSERT INTO audit_events (actor_id, action, target_type, target_id, reason)
                VALUES (?, ?, 'USER', ?, ?)
                """, admin, status == UserStatus.DISABLED ? "USER_DISABLED" : "USER_ENABLED", id, reason);
    }
}
