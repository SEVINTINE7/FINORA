package lk.finora.api.auth;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordResetService {
    private record Account(long id, String email, String language) {}
    private record Challenge(String hash, int attempts) {}
    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PasswordResetMail mail;

    PasswordResetService(JdbcTemplate jdbc, PasswordEncoder encoder, PasswordResetMail mail) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mail = mail;
    }

    private List<Account> lockActiveAccount(String email) {
        return jdbc.query("SELECT id, email, language FROM users WHERE email = ? AND status = 'ACTIVE' FOR UPDATE",
                (rs, n) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3)),
                email.trim().toLowerCase(Locale.ROOT));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void issue(String email) {
        if (!mail.configured()) return;
        var accounts = lockActiveAccount(email);
        if (accounts.isEmpty()) return;
        var account = accounts.get(0);
        long id = account.id();
        jdbc.update("DELETE FROM password_reset_issues WHERE user_id = ? AND created_at <= UTC_TIMESTAMP(6) - INTERVAL 1 HOUR", id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_issues WHERE user_id = ?", Integer.class, id) >= 3) return;

        var previous = jdbc.queryForList("SELECT code_hash FROM password_reset_codes WHERE user_id = ?", String.class, id);
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (!previous.isEmpty() && encoder.matches(code, previous.get(0)));
        jdbc.update("DELETE FROM password_reset_codes WHERE user_id = ?", id);
        jdbc.update("""
                INSERT INTO password_reset_codes (user_id, code_hash, expires_at, created_at)
                VALUES (?, ?, UTC_TIMESTAMP(6) + INTERVAL 15 MINUTE, UTC_TIMESTAMP(6))
                """, id, encoder.encode(code));
        jdbc.update("INSERT INTO password_reset_issues (user_id, created_at) VALUES (?, UTC_TIMESTAMP(6))", id);
        mail.afterCommit(account.email(), account.language(), code);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean reset(String email, String code, String password) {
        var accounts = lockActiveAccount(email);
        if (accounts.isEmpty()) return false;
        long id = accounts.get(0).id();
        var codes = jdbc.query("""
                SELECT code_hash, attempts FROM password_reset_codes
                WHERE user_id = ? AND expires_at > UTC_TIMESTAMP(6)
                """, (rs, n) -> new Challenge(rs.getString(1), rs.getInt(2)), id);
        if (codes.isEmpty()) return false;
        var challenge = codes.get(0);
        if (code == null || !code.matches("[0-9]{6}") || !encoder.matches(code, challenge.hash())) {
            if (challenge.attempts() >= 4) {
                jdbc.update("DELETE FROM password_reset_codes WHERE user_id = ?", id);
            } else {
                jdbc.update("UPDATE password_reset_codes SET attempts = attempts + 1 WHERE user_id = ?", id);
            }
            return false;
        }
        if (jdbc.update("DELETE FROM password_reset_codes WHERE user_id = ? AND expires_at > UTC_TIMESTAMP(6)", id) != 1) {
            return false;
        }
        jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", encoder.encode(password), id);
        jdbc.update("UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP(6) WHERE user_id = ? AND revoked_at IS NULL", id);
        jdbc.update("INSERT INTO audit_events (actor_id, action, target_type, target_id) VALUES (?, 'PASSWORD_RESET', 'USER', ?)", id, id);
        return true;
    }
}
