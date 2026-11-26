package lk.finora.api.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/** Null means not yet declared; zero is an explicit declaration. No owner ID is accepted. */
public record DailyGuideRequest(
        @Digits(integer = 13, fraction = 2) BigDecimal availableFunds,
        @Digits(integer = 13, fraction = 2) @DecimalMin("0.00") BigDecimal plannedSavings,
        LocalDate nextIncomeDate,
        @Digits(integer = TRACKED_BILL_INTEGER_DIGITS, fraction = 2) @DecimalMin("0.00") BigDecimal confirmedTrackedUnpaidBills,
        @Digits(integer = 13, fraction = 2) @DecimalMin("0.00") BigDecimal additionalUnpaidBills) {
    // The quote totals up to Integer.MAX_VALUE individual 13-integer-digit occurrences.
    public static final int TRACKED_BILL_INTEGER_DIGITS = 23;
}
