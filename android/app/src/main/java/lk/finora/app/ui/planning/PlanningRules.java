package lk.finora.app.ui.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lk.finora.app.R;

/** T64.3: pure planning screen rules, kept out of the activity so they can be unit tested. */
final class PlanningRules {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private PlanningRules() {}

    static LocalDate today() { return LocalDate.now(COLOMBO); }

    /** D68: the change is typed as a positive amount; Less makes it negative. Empty means no change. */
    static BigDecimal monthlyChange(BigDecimal amount, boolean less) {
        if (amount == null) return BigDecimal.ZERO.setScale(2);
        return less ? amount.negate() : amount;
    }

    /** The calendar opens at the stored date, or tomorrow (the earliest date that gives a daily allowance). */
    static LocalDate pickerStart(String incomeDate, LocalDate today) {
        if (incomeDate != null) {
            try { return LocalDate.parse(incomeDate); } catch (java.time.DateTimeException ignored) { /* below */ }
        }
        return today.plusDays(1);
    }

    /** Full month and year ("February 2027"); the full name avoids ambiguous short Sinhala months. */
    static String monthYear(String isoDateOrMonth, Locale locale) {
        if (isoDateOrMonth == null) return "";
        try {
            YearMonth month = isoDateOrMonth.length() > 7 ? YearMonth.from(LocalDate.parse(isoDateOrMonth))
                    : YearMonth.parse(isoDateOrMonth);
            return month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale));
        } catch (java.time.DateTimeException invalid) {
            return isoDateOrMonth;
        }
    }

    /** The message shown for a guide status that is not a ready allowance (0 = none). */
    static int guideMessage(String status, boolean warning) {
        if (status == null) return R.string.records_failed;
        return switch (status) {
            case "READY" -> warning ? R.string.planning_zero : 0;
            case "NO_SPENDABLE_FUNDS" -> R.string.planning_zero;
            case "SHORTFALL" -> R.string.planning_overcommitted;
            case "MISSING_BALANCE" -> R.string.planning_missing_funds;
            case "MISSING_PLANNED_SAVINGS" -> R.string.planning_missing_savings;
            case "MISSING_NEXT_INCOME_DATE" -> R.string.planning_date_missing;
            case "INCOME_TODAY" -> R.string.planning_income_today;
            case "INCOME_DATE_PASSED" -> R.string.planning_income_passed;
            case "BILL_CONFIRMATION_REQUIRED" -> R.string.planning_confirm_needed;
            case "BILLS_CHANGED" -> R.string.planning_bills_changed;
            case "MISSING_ADDITIONAL_BILLS" -> R.string.planning_missing_arrears;
            default -> R.string.records_failed;
        };
    }

    /** The message shown for a simulation status (0 = a normal estimate, shown in the card). */
    static int simulationMessage(String status) {
        if (status == null) return R.string.records_failed;
        return switch (status) {
            case "POSSIBLE" -> 0;
            case "ALREADY_COMPLETE" -> R.string.planning_completed;
            case "NO_MONTHLY_CAPACITY" -> R.string.planning_no_capacity;
            case "BEYOND_HORIZON" -> R.string.planning_horizon;
            default -> R.string.records_failed;
        };
    }

    static boolean needsConfirmation(String status) {
        return "BILL_CONFIRMATION_REQUIRED".equals(status) || "BILLS_CHANGED".equals(status);
    }
}
