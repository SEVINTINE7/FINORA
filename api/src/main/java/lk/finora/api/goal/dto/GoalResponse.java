package lk.finora.api.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lk.finora.api.goal.GoalStatus;

/**
 * A goal with its progress. saved is the real total (can be more than the target);
 * remaining never goes below 0.00 and progressPercent stops at 100.
 */
public record GoalResponse(Long id, String name, BigDecimal targetAmount, LocalDate targetDate, GoalStatus status,
        BigDecimal saved, BigDecimal remaining, int progressPercent) {
}
