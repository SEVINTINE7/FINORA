package lk.finora.api.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Deterministic hypothetical savings calculation.
 * Not a forecast or a guarantee of future financial outcomes.
 */
final class SimulationMath {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal MAX_MONTHS = BigDecimal.valueOf(1200);

    private SimulationMath() {
    }

    record Result(
            BigDecimal monthlyAmount,
            Integer monthsRequired,
            LocalDate finishDate,
            String status) {
    }

    static Result calculate(
            BigDecimal remaining,
            BigDecimal baseline,
            BigDecimal monthlyChange,
            BigDecimal categoryReductions,
            LocalDate today) {

        Objects.requireNonNull(remaining);
        Objects.requireNonNull(baseline);
        Objects.requireNonNull(monthlyChange);
        Objects.requireNonNull(categoryReductions);
        Objects.requireNonNull(today);

        if (remaining.signum() < 0
                || baseline.signum() < 0
                || categoryReductions.signum() < 0) {
            throw new IllegalArgumentException(
                    "Remaining, baseline and reductions cannot be negative");
        }

        BigDecimal monthly = baseline
                .add(monthlyChange)
                .add(categoryReductions)
                .setScale(2, RoundingMode.UNNECESSARY);

        if (remaining.signum() == 0) {
            return new Result(
                    monthly.max(ZERO),
                    0,
                    today,
                    "ALREADY_COMPLETE");
        }

        if (monthly.signum() <= 0) {
            return new Result(
                    ZERO,
                    null,
                    null,
                    "NO_MONTHLY_CAPACITY");
        }

        BigDecimal months = remaining.divide(
                monthly, 0, RoundingMode.CEILING);

        // Avoid producing misleading dates centuries into the future.
        if (months.compareTo(MAX_MONTHS) > 0) {
            return new Result(
                    monthly,
                    null,
                    null,
                    "BEYOND_HORIZON");
        }

        int count = months.intValueExact();

        // First hypothetical contribution: end of next calendar month.
        LocalDate finish = YearMonth.from(today)
                .plusMonths(count)
                .atEndOfMonth();

        return new Result(monthly, count, finish, "POSSIBLE");
    }
}