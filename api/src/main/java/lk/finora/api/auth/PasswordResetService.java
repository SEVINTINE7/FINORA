package lk.finora.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

import lk.finora.api.common.error.ApiException;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** T61/D64. All code issuance, attempts and credential changes serialize on the users row. */
@Service
class PasswordResetService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PasswordResetMailer mailer;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    PasswordResetService(JdbcTemplate jdbc, PasswordEncoder encoder, PasswordResetMailer mailer) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mailer = mailer;
        this.dummyHash = encoder.encode("000000");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void forgot(String email) {
        Account account = lockActiveAccount(email);
        ResetCode previous = account == null ? null : codeFor(account.id());
        String code = newCode();
        // Do BCrypt work for unknown accounts too. Never reissue the current numeric code.
        while (encoder.matches(code, previous == null ? dummyHash : previous.hash())) {
            code = newCode();
        }
        String hash = encoder.encode(code);
        if (account == null) return;

        jdbc.update("DELETE FROM password_reset_issuances WHERE user_id = ? "
                + "AND issued_at <= UTC_TIMESTAMP(6) - INTERVAL 1 HOUR", account.id());
        int issued = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_issuances WHERE user_id = ?",
                Integer.class, account.id());
        if (issued >= 3) return;

        jdbc.update("DELETE FROM password_reset_codes WHERE user_id = ?", account.id());
        jdbc.update("""
                INSERT INTO password_reset_codes (user_id, code_hash, expires_at, attempts, created_at, session_generation)
                VALUES (?, ?, UTC_TIMESTAMP(6) + INTERVAL 15 MINUTE, 0, UTC_TIMESTAMP(6), ?)
                """, account.id(), hash, account.generation());
        jdbc.update("INSERT INTO password_reset_issuances (user_id, issued_at) VALUES (?, UTC_TIMESTAMP(6))", account.id());

        // Only dispatch a committed code. SMTP runs on a bounded background executor, not the request thread.
        final String issuedCode = code;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                mailer.send(account.email(), account.language(), issuedCode);
            }
        });
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
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Choose a shorter password");
        }
        Account account = lockActiveAccount(email);
        ResetCode current = account == null ? null : codeFor(account.id());
        boolean syntax = code != null && code.length() == 6 && code.matches("[0-9]{6}");
        boolean matches = encoder.matches(syntax ? code : "000000", current == null ? dummyHash : current.hash());
        if (current == null) return false;
        if (!current.unexpired() || current.generation() != account.generation()) {
            deleteCode(account.id());
            return false;
        }
        if (!syntax || !matches) {
            if (current.attempts() >= 4) {
                deleteCode(account.id());
            } else {
                jdbc.update("UPDATE password_reset_codes SET attempts = attempts + 1 WHERE user_id = ?", account.id());
            }
            return false;
        }

        String replacement = encoder.encode(password);
        // Recheck expiry after hashing; only one concurrent request can consume this row.
        if (jdbc.update("DELETE FROM password_reset_codes WHERE user_id = ? AND expires_at > UTC_TIMESTAMP(6)",
                account.id()) != 1) {
            deleteCode(account.id());
            return false;
        }
        jdbc.update("""
                UPDATE users SET password_hash = ?, staff_session_generation = staff_session_generation + 1
                WHERE id = ?
                """, replacement, account.id());
        jdbc.update("UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP(6) WHERE user_id = ? AND revoked_at IS NULL",
                account.id());
        // A password reset also ends old normal access tokens and outstanding consent-recovery grants.
        jdbc.update("DELETE FROM consent_recovery_grants WHERE user_id = ?", account.id());
        jdbc.update("INSERT INTO audit_events (actor_id, action, target_type, target_id) VALUES (?, 'PASSWORD_RESET', 'USER', ?)",
                account.id(), account.id());
        return true;
    }

    private Account lockActiveAccount(String email) {
        List<Account> rows = jdbc.query("""
                SELECT id, email, language, staff_session_generation FROM users
                WHERE email = ? AND status = 'ACTIVE' FOR UPDATE
                """, (rs, n) -> new Account(rs.getLong("id"), rs.getString("email"), rs.getString("language"),
                rs.getLong("staff_session_generation")), email.strip().toLowerCase(Locale.ROOT));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ResetCode codeFor(long id) {
        var rows = jdbc.query("""
                SELECT code_hash, attempts, session_generation, expires_at > UTC_TIMESTAMP(6) AS unexpired
                FROM password_reset_codes WHERE user_id = ?
                """, (rs, n) -> new ResetCode(rs.getString("code_hash"), rs.getInt("attempts"),
                rs.getLong("session_generation"), rs.getBoolean("unexpired")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void deleteCode(long id) {
        jdbc.update("DELETE FROM password_reset_codes WHERE user_id = ?", id);
    }

    private String newCode() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }

    private record Account(long id, String email, String language, long generation) {
        @Override public String toString() { return "Reset account"; }
    }
    private record ResetCode(String hash, int attempts, long generation, boolean unexpired) {
        @Override public String toString() { return "Reset code metadata"; }
    }
}
