package lk.finora.api.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** T31: exact, non-predictive arithmetic; a plan never mutates financial records. */
final class DailyGuideMath {
    static final BigDecimal ZERO = new BigDecimal("0.00");
    enum Status { READY, NO_SPENDABLE_FUNDS, SHORTFALL, MISSING_BALANCE,
        MISSING_PLANNED_SAVINGS, MISSING_NEXT_INCOME_DATE, INCOME_TODAY,
        INCOME_DATE_PASSED, BILL_CONFIRMATION_REQUIRED, BILLS_CHANGED,
        MISSING_ADDITIONAL_BILLS }
    record Result(Status status, Long spendingDays, BigDecimal spendableFunds,
                  BigDecimal dailyAllowance, BigDecimal shortfall, boolean warning) {}

    private DailyGuideMath() {}

    static Result unavailable(Status status) {
        return new Result(status, null, null, null, null, true);
    }

    static Result calculate(LocalDate today, LocalDate nextIncome, BigDecimal available,
                            BigDecimal bills, BigDecimal savings) {
        if (available == null) return unavailable(Status.MISSING_BALANCE);
        if (savings == null) return unavailable(Status.MISSING_PLANNED_SAVINGS);
        if (nextIncome == null) return unavailable(Status.MISSING_NEXT_INCOME_DATE);
        if (bills == null) return unavailable(Status.BILL_CONFIRMATION_REQUIRED);
        if (bills.signum() < 0 || savings.signum() < 0) throw new IllegalArgumentException("Negative commitment");
        available = available.setScale(2, RoundingMode.UNNECESSARY);
        bills = bills.setScale(2, RoundingMode.UNNECESSARY);
        savings = savings.setScale(2, RoundingMode.UNNECESSARY);
        long days = ChronoUnit.DAYS.between(today, nextIncome);
        if (days == 0) return unavailable(Status.INCOME_TODAY);
        if (days < 0) return unavailable(Status.INCOME_DATE_PASSED);
        BigDecimal net = available.subtract(bills).subtract(savings);
        if (net.signum() < 0) return new Result(Status.SHORTFALL, days, ZERO, ZERO, net.negate(), true);
        if (net.signum() == 0) return new Result(Status.NO_SPENDABLE_FUNDS, days, ZERO, ZERO, ZERO, true);
        BigDecimal daily = net.divide(BigDecimal.valueOf(days), 2, RoundingMode.DOWN);
        return new Result(Status.READY, days, net, daily, ZERO, daily.signum() == 0);
    }
}
