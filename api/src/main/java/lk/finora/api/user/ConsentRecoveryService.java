package lk.finora.api.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import lk.finora.api.account.FinanceWriteLock;
import lk.finora.api.auth.AuthService;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.export.ExportService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentRecoveryService {
    public static final int LIFETIME_SECONDS = 300;
    private final JdbcTemplate jdbc;
    private final FinanceWriteLock owners;
    private final ExportService exports;
    private final AccountDeletionService deletion;
    private final ConsentRepository consents;
    private final SecureRandom random = new SecureRandom();

    ConsentRecoveryService(JdbcTemplate jdbc, FinanceWriteLock owners, ExportService exports,
            AccountDeletionService deletion, ConsentRepository consents) {
        this.jdbc = jdbc;
        this.owners = owners;
        this.exports = exports;
        this.deletion = deletion;
        this.consents = consents;
    }

    public boolean withdrawn(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM consents WHERE user_id = ? AND withdrawn_at IS NOT NULL)
                   AND NOT EXISTS (SELECT 1 FROM consents WHERE user_id = ? AND withdrawn_at IS NULL)
                """, Boolean.class, id, id));
    }

    public boolean validNormalSession(long id, long generation) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM users u WHERE u.id = ? AND u.role = 'USER' AND u.status = 'ACTIVE'
                    AND u.staff_session_generation = ?
                    AND (NOT EXISTS (SELECT 1 FROM consents c WHERE c.user_id = u.id AND c.withdrawn_at IS NOT NULL)
                         OR EXISTS (SELECT 1 FROM consents c WHERE c.user_id = u.id AND c.withdrawn_at IS NULL)))
                """, Boolean.class, id, generation));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String issue(long id) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        clear(id);
        jdbc.update("""
                INSERT INTO consent_recovery_grants (user_id, token_hash, expires_at)
                VALUES (?, ?, UTC_TIMESTAMP(6) + INTERVAL 300 SECOND)
                """, id, hash(token));
        return token;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void accept(String header, String version, boolean accepted) {
        if (!accepted || !AuthService.CONSENT_VERSION.equals(version)) {
            throw ApiException.badRequest("Explicit acceptance of the current consent version is required");
        }
        long id = consume(header);
        var consent = new Consent();
        consent.setUserId(id);
        consent.setOver18(true);
        consent.setVersion(version);
        consent.setAcceptedAt(Instant.now());
        consents.save(consent);
        invalidateNormalSessions(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ExportService.Snapshot export(String header) {
        return exports.read(consume(header));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(String header, String password) {
        deletion.delete(consume(header), password);
    }

    void invalidateNormalSessions(long id) {
        jdbc.update("UPDATE users SET staff_session_generation = staff_session_generation + 1 WHERE id = ?", id);
        jdbc.update("UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP(6) WHERE user_id = ? AND revoked_at IS NULL", id);
        clear(id);
    }

    private void clear(long id) {
        jdbc.update("DELETE FROM consent_recovery_grants WHERE user_id = ?", id);
    }

    private long consume(String header) {
        if (header == null || !header.matches("Consent [A-Za-z0-9_-]{43}")) throw invalid();
        String digest = hash(header.substring(8));
        var ids = jdbc.queryForList("SELECT user_id FROM consent_recovery_grants WHERE token_hash = ?", Long.class, digest);
        if (ids.isEmpty()) throw invalid();
        long id = ids.get(0);
        owners.acquireIdentity(id);
        var validAccounts = jdbc.queryForList("SELECT id FROM users WHERE id = ? AND role = 'USER' AND status = 'ACTIVE'",
                Long.class, id);
        if (validAccounts.isEmpty() || !withdrawn(id) || jdbc.update("""
                DELETE FROM consent_recovery_grants
                WHERE user_id = ? AND token_hash = ? AND expires_at > UTC_TIMESTAMP(6)
                """, id, digest) != 1) throw invalid();
        return id;
    }

    private static ApiException invalid() {
        return new ApiException("INVALID_CONSENT_GRANT", HttpStatus.UNAUTHORIZED, "Please sign in again to manage consent");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
