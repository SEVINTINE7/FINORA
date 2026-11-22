package lk.finora.api.planning.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SimulationRequest(
        @NotNull @Positive Long goalId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2)
        BigDecimal baselineMonthlySavings,
        @NotNull @Digits(integer = 13, fraction = 2)
        BigDecimal monthlyChange,
        @NotNull @Size(max = 20)
        List<@NotNull @Valid CategoryCut> categoryCuts) {

    public record CategoryCut(
            @NotNull @Positive Long categoryId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2)
            BigDecimal monthlyReduction) {
    }
}