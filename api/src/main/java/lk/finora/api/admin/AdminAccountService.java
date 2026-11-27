package lk.finora.api.admin;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import lk.finora.api.admin.AdminAccountController.AdminSummary;
import lk.finora.api.common.error.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T59 (D62). The creating admin sets the password; it is only stored as a BCrypt hash and never
 * returned or logged. Every enable/disable locks all ADMIN rows first, so two admins disabling
 * each other at the same time can never leave the system without an active admin.
 */
@Service
class AdminAccountService {
    private static final RowMapper<AdminSummary> SUMMARY = (row, n) -> new AdminSummary(row.getLong("id"),
            row.getString("email"), row.getString("full_name"), "ACTIVE".equals(row.getString("status")),
            row.getObject("created_at", java.time.LocalDateTime.class).toInstant(ZoneOffset.UTC));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    AdminAccountService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Transactional
    public AdminSummary create(long actor, String email, String fullName, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Choose a shorter password");
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        try {
            jdbc.update("""
                    INSERT INTO users (email, full_name, password_hash, role, status)
                    VALUES (?, ?, ?, 'ADMIN', 'ACTIVE')
                    """, normalized, fullName.strip(), encoder.encode(password));
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException("CONFLICT", HttpStatus.CONFLICT, "Email is already registered");
        }
        AdminSummary created = jdbc.queryForObject("""
                SELECT id, email, full_name, status, created_at FROM users WHERE email = ? AND role = 'ADMIN'
                """, SUMMARY, normalized);
        audit(actor, "ADMIN_CREATED", created.id());
        return created;
    }

    List<AdminSummary> list() {
        return jdbc.query("""
                SELECT id, email, full_name, status, created_at FROM users
                WHERE role = 'ADMIN' ORDER BY id LIMIT 100
                """, SUMMARY);
    }

    @Transactional
    public void setEnabled(long actor, long id, boolean enabled) {
        var rows = jdbc.queryForList("SELECT id, status FROM users WHERE role = 'ADMIN' ORDER BY id FOR UPDATE");
        var target = rows.stream().filter(row -> ((Number) row.get("id")).longValue() == id).findFirst()
                .orElseThrow(() -> ApiException.notFound("Admin account not found"));
        boolean actorActive = rows.stream().anyMatch(row -> ((Number) row.get("id")).longValue() == actor
                && "ACTIVE".equals(row.get("status")));
        if (!actorActive) throw conflict("Your admin account is no longer active");
        String wanted = enabled ? "ACTIVE" : "DISABLED";
        if (wanted.equals(target.get("status"))) return;
        if (!enabled) {
            if (id == actor) throw conflict("You cannot disable your own admin account");
            long othersActive = rows.stream().filter(row -> ((Number) row.get("id")).longValue() != id
                    && "ACTIVE".equals(row.get("status"))).count();
            if (othersActive == 0) throw conflict("The last active admin cannot be disabled");
        }
        // A new generation ends every existing session, also after a later re-enable (as for STAFF).
        jdbc.update("""
                UPDATE users SET status = ?, staff_session_generation = staff_session_generation + 1 WHERE id = ?
                """, wanted, id);
        if (!enabled) {
            jdbc.update("UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP(6) WHERE user_id = ? AND revoked_at IS NULL",
                    id);
        }
        audit(actor, enabled ? "ADMIN_ENABLED" : "ADMIN_DISABLED", id);
    }

    private void audit(long actor, String action, long target) {
        jdbc.update("INSERT INTO audit_events (actor_id, action, target_type, target_id) VALUES (?, ?, 'ADMIN', ?)",
                actor, action, target);
    }

    private static ApiException conflict(String message) {
        return new ApiException("CONFLICT", HttpStatus.CONFLICT, message);
    }
}
