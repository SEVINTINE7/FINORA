package lk.finora.api.staff;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lk.finora.api.common.error.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StaffProvisioningService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    StaffProvisioningService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Transactional
    public Provisioned provision(String email, String name) {
        var key = new GeneratedKeyHolder();
        var opaquePassword = encoder.encode(secret());
        try {
            jdbc.update(connection -> {
                var row = connection.prepareStatement("""
                        INSERT INTO users (email, full_name, password_hash, role, status)
                        VALUES (?, ?, ?, 'STAFF', 'DISABLED')
                        """, java.sql.Statement.RETURN_GENERATED_KEYS);
                row.setString(1, normalize(email));
                row.setString(2, name.strip());
                row.setString(3, opaquePassword);
                return row;
            }, key);
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException("CONFLICT", HttpStatus.CONFLICT, "Staff email is already registered");
        }
        long id = java.util.Objects.requireNonNull(key.getKey()).longValue();
        String temporary = secret();
        jdbc.update("""
                INSERT INTO staff_activations (staff_id, password_hash, expires_at)
                VALUES (?, ?, UTC_TIMESTAMP(6) + INTERVAL 1 DAY)
                """, id, encoder.encode(temporary));
        return new Provisioned(id, temporary);
    }

    @Transactional
    public Provisioned reissue(long id) {
        var staff = jdbc.queryForList("""
                SELECT id FROM users WHERE id = ? AND role = 'STAFF' AND status = 'DISABLED' FOR UPDATE
                """, Long.class, id);
        if (staff.isEmpty()) throw ApiException.notFound("Pending staff account unavailable");
        String temporary = secret();
        if (jdbc.update("""
                UPDATE staff_activations SET password_hash = ?, expires_at = UTC_TIMESTAMP(6) + INTERVAL 1 DAY
                WHERE staff_id = ?
                """, encoder.encode(temporary), id) != 1) {
            throw ApiException.notFound("Pending staff account unavailable");
        }
        return new Provisioned(id, temporary);
    }

    @Transactional
    public void activate(String email, String temporary, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72 || password.equals(temporary)) {
            throw ApiException.badRequest("Choose a different supported password");
        }
        var staff = jdbc.queryForList("""
                SELECT id FROM users WHERE email = ? AND role = 'STAFF' AND status = 'DISABLED' FOR UPDATE
                """, Long.class, normalize(email));
        if (staff.isEmpty()) throw unauthenticated();
        long id = staff.get(0);
        var hashes = jdbc.queryForList("""
                SELECT password_hash FROM staff_activations
                WHERE staff_id = ? AND expires_at > UTC_TIMESTAMP(6) FOR UPDATE
                """, String.class, id);
        if (hashes.isEmpty() || !encoder.matches(temporary, hashes.get(0))) throw unauthenticated();
        String replacement = encoder.encode(password);
        if (jdbc.update("DELETE FROM staff_activations WHERE staff_id = ? AND expires_at > UTC_TIMESTAMP(6)", id)
                != 1) throw unauthenticated();
        jdbc.update("""
                UPDATE users SET password_hash = ?, status = 'ACTIVE',
                    staff_session_generation = staff_session_generation + 1 WHERE id = ?
                """, replacement, id);
    }

    void setEnabled(long id, boolean enabled) {
        int changed = jdbc.update("""
                UPDATE users SET status = ?, staff_session_generation = staff_session_generation + 1
                WHERE id = ? AND role = 'STAFF' AND status <> ?
                  AND NOT EXISTS (SELECT 1 FROM staff_activations WHERE staff_id = ?)
                """, enabled ? "ACTIVE" : "DISABLED", id, enabled ? "ACTIVE" : "DISABLED", id);
        if (changed != 1) throw ApiException.notFound("Staff account unavailable for this change");
    }

    List<Staff> list() {
        return jdbc.query("""
                SELECT u.id, u.email, u.full_name, u.status, a.staff_id IS NOT NULL AS pending
                FROM users u LEFT JOIN staff_activations a ON a.staff_id = u.id
                WHERE u.role = 'STAFF' ORDER BY u.id DESC LIMIT 100
                """, (row, n) -> new Staff(row.getLong("id"), row.getString("email"),
                        row.getString("full_name"), row.getString("status"), row.getBoolean("pending")));
    }

    private String secret() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private static ApiException unauthenticated() {
        return new ApiException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Activation details are invalid or expired");
    }

    record Provisioned(long id, String temporaryPassword) {
        @Override public String toString() { return "Provisioned staff " + id; }
    }
    record Staff(long id, String email, String fullName, String status, boolean pending) {}
}
