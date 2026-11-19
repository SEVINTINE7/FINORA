package lk.finora.api.budget.dto;

import java.math.BigDecimal;

/**
 * A budget with how much was spent. remaining = limit - used, so it is negative when over budget.
 * state is OK, NEAR (used is 80% of the limit or more) or OVER (used is more than the limit).
 */
public record BudgetResponse(Long id, Long categoryId, String month, BigDecimal limitAmount, BigDecimal used,
        BigDecimal remaining, String state) {
}
