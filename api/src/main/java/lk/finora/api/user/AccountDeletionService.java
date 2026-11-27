package lk.finora.api.user;

import lk.finora.api.account.FinanceWriteLock;
import lk.finora.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountDeletionService {
    private final JdbcTemplate jdbc;
    private final FinanceWriteLock writeLock;
    private final PasswordEncoder passwords;

    private final FinancialDataDeletion financialData;

    AccountDeletionService(JdbcTemplate jdbc, FinanceWriteLock writeLock, PasswordEncoder passwords,
            FinancialDataDeletion financialData) {
        this.financialData = financialData;
        this.jdbc = jdbc;
        this.writeLock = writeLock;
        this.passwords = passwords;
    }

    @Transactional
    public void delete(long id, String password) {
        writeLock.acquire(id);
        var hashes = jdbc.queryForList("SELECT password_hash FROM users WHERE id = ? AND role = 'USER'",
                String.class, id);
        if (hashes.isEmpty()) throw ApiException.notFound("User not found");
        if (!passwords.matches(password, hashes.get(0))) {
            throw new ApiException("INVALID_CREDENTIALS", HttpStatus.BAD_REQUEST, "Password is incorrect");
        }
        jdbc.update("UPDATE audit_events SET target_id = NULL WHERE target_type = 'USER' AND target_id = ?", id);
        financialData.erase(id);
        jdbc.update("DELETE FROM users WHERE id = ? AND role = 'USER'", id);
        jdbc.update("INSERT INTO audit_events (action, target_type) VALUES ('USER_DELETED', 'USER')");
    }
}
