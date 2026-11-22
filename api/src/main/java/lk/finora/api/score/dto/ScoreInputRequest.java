package lk.finora.api.score.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PastOrPresent;

/** Null is unknown, not a zero balance or a declaration of no debt/goals. */
public record ScoreInputRequest(
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal emergencyReserve,
        @PastOrPresent LocalDate reserveAsOf,
        Boolean noApplicableDebt, Boolean noApplicableGoals) {}
