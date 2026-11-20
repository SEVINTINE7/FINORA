package lk.finora.api.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

/** Money put toward a goal. The date cannot be in the future (it has already been saved). */
public record ContributionRequest(

        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal amount,

        @NotNull @PastOrPresent
        LocalDate date) {
}
