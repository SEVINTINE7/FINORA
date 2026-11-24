package lk.finora.app.data.api;

import java.math.BigDecimal;

/**
 * A savings goal with progress (T19). {@code saved} can be more than the target; {@code remaining} never goes
 * below 0 and {@code progressPercent} stops at 100. {@code targetDate} is optional (YYYY-MM-DD).
 */
public record GoalDto(long id, String name, BigDecimal targetAmount, String targetDate, String status,
                      BigDecimal saved, BigDecimal remaining, int progressPercent) {

    /** POST/PUT body. On PUT, cancelled=true keeps or makes the goal CANCELLED; anything else resumes it (D16). */
    public record Write(String name, BigDecimal targetAmount, String targetDate, Boolean cancelled) {}

    public record Contribution(long id, long goalId, BigDecimal amount, String date) {}

    /** Date must be today or earlier. */
    public record NewContribution(BigDecimal amount, String date) {}
}
