package lk.finora.api.user;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Owner data erased by T36 and D63, with dependent rows removed by existing foreign keys. */
@Component
public class FinancialDataDeletion {
    private static final List<String> ROOTS = List.of(
            "transactions", "budgets", "bills", "goals", "advice_ratings", "advice", "insights",
            "health_scores", "score_inputs", "monthly_history", "history_recovery_retry",
            "advice_generation_leases", "advice_verification_events", "alert_events",
            "device_tokens", "categories", "accounts");

    private final JdbcTemplate jdbc;

    public FinancialDataDeletion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Caller must hold the user's row lock for the surrounding transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void erase(long userId) {
        for (String table : ROOTS) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
        }
    }
}
