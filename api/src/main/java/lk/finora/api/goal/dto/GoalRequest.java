package lk.finora.api.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or editing a goal. targetDate is optional but, if given, must be after today
 * (also on edit). cancelled is only read on edit; leave it out or false to keep the goal running.
 */
public record GoalRequest(

        @NotBlank @Size(max = 60)
        String name,

        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
        BigDecimal targetAmount,

        @Future
        LocalDate targetDate,

        Boolean cancelled) {
}
