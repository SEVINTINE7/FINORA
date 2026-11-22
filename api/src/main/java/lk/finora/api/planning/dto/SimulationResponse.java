package lk.finora.api.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SimulationResponse(
        Long goalId,
        BigDecimal targetAmount,
        BigDecimal saved,
        BigDecimal remaining,
        BigDecimal baselineMonthlySavings,
        BigDecimal monthlyChange,
        BigDecimal categoryReductions,
        BigDecimal hypotheticalMonthlySavings,
        Integer monthsRequired,
        LocalDate hypotheticalFinishDate,
        String status,
        String spendingReferenceMonth,
        List<String> assumptions) {
}