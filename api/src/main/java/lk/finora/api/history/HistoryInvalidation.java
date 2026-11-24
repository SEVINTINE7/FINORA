package lk.finora.api.history;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoryInvalidation {
    private final JdbcTemplate jdbc;
    public HistoryInvalidation(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Durable work flag, committed atomically with the finance mutation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void transactionChanged(long userId, LocalDate date, boolean income) {
        String from = YearMonth.from(date).toString();
        YearMonth adjacent = income ? YearMonth.from(date.plusDays(2)) : YearMonth.from(date);
        String to = adjacent.getYear() > 9999 ? from : adjacent.toString();
        jdbc.update("UPDATE monthly_history SET dirty = TRUE WHERE user_id = ? AND month BETWEEN ? AND ?",
                userId, from, to);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void monthChanged(long userId, String month) {
        jdbc.update("UPDATE monthly_history SET dirty = TRUE WHERE user_id = ? AND month = ?", userId, month);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void monthsChangedFrom(long userId, String month) {
        jdbc.update("UPDATE monthly_history SET dirty = TRUE WHERE user_id = ? AND month >= ?", userId, month);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void allMonthsChanged(long userId) {
        jdbc.update("UPDATE monthly_history SET dirty = TRUE WHERE user_id = ?", userId);
    }
}
