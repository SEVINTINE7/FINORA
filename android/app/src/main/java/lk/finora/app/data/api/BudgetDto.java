package lk.finora.app.data.api;

import java.math.BigDecimal;

/** A monthly budget from /api/v1/budgets (T17). {@code remaining} is negative when over the limit. */
public record BudgetDto(long id, long categoryId, String month, BigDecimal limitAmount, BigDecimal used,
                        BigDecimal remaining, String state) {
    public record Write(long categoryId, String month, BigDecimal limitAmount) {}
}
