package lk.finora.api.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyGuideResponse(
        LocalDate asOf, LocalDate nextIncomeDate, String incomeDateSource,
        LocalDate lastRecordedIncomeDate, BigDecimal availableFunds,
        BigDecimal trackedUnpaidBills, Integer trackedUnpaidBillCount,
        BigDecimal additionalUnpaidBills, BigDecimal plannedSavings,
        String status, Long spendingDays, BigDecimal spendableFunds,
        BigDecimal dailyAllowance, BigDecimal shortfall, boolean warning,
        List<String> assumptions) {}
