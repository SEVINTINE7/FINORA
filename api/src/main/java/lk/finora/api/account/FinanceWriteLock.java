package lk.finora.api.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.common.error.ApiException;

/** One database lock per owner, acquired before reading account state or transaction hashes. */
@Component
public class FinanceWriteLock {
    private final JdbcTemplate jdbc;

    public FinanceWriteLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireIdentity(Long userId) {
        if (jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId).isEmpty()) {
            throw ApiException.notFound("User not found");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Long userId) {
        if (jdbc.queryForList("SELECT id FROM users WHERE id = ? AND role = 'USER' FOR UPDATE",
                Long.class, userId).isEmpty()) {
            throw ApiException.notFound("User not found");
        }
    }
}
