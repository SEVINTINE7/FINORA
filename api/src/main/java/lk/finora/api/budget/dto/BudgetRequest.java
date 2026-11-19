package lk.finora.api.budget.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Body for creating or editing a budget. */
public record BudgetRequest(

        @NotNull
        Long categoryId,

        @NotNull @Pattern(regexp = BudgetRequest.MONTH_PATTERN, message = "must be YYYY-MM")
        String month,

        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal limitAmount) {

    /** Year and month 01-12, e.g. 2026-11. Same rule as the database check. */
    public static final String MONTH_PATTERN = "^[0-9]{4}-(0[1-9]|1[0-2])$";
}
