package lk.finora.api.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lk.finora.api.score.dto.ScoreResponse;
import lk.finora.api.score.dto.ScoreResponse.Part;

/** Pure implementation of approved D20; no clock, database, names, or AI. */
public final class ScoreCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal TARGET = new BigDecimal("0.05");
    private ScoreCalculator() {}

    /** Null debt/goal denominators mean unknown; explicit zero means not applicable. */
    public record Inputs(BigDecimal income, BigDecimal expense, BigDecimal savings, BigDecimal needs,
            BigDecimal debtDue, BigDecimal debtMarkedPaid, BigDecimal goalTarget, BigDecimal goalCredit,
            BigDecimal reserve, LocalDate reserveAsOf) {}

    public static ScoreResponse calculate(String month, Inputs i) {
        BigDecimal consumption = i.expense().subtract(i.savings());
        Part spending;
        Part savings;
        if (i.income().signum() <= 0) {
            spending = missing("SPENDING_CONTROL", 45, "ZERO_INCOME");
            savings = missing("SAVINGS", 10, "ZERO_INCOME");
        } else {
            BigDecimal spendingPoints = consumption.signum() == 0 ? new BigDecimal("45.00")
                    : points("27", i.income(), consumption).add(points("18",
                            i.income().subtract(consumption), TARGET.multiply(i.income())));
            spending = scored("SPENDING_CONTROL", 45, spendingPoints,
                    consumption.compareTo(i.income()) > 0 ? "SPENDING_EXCEEDS_INCOME"
                            : consumption.compareTo(i.income()) == 0 ? "SPENDING_BREAK_EVEN" : "SPENDING_WITHIN_INCOME",
                    Map.of("income", i.income(), "consumption", consumption));
            BigDecimal supported = i.savings().min(i.income().subtract(consumption).max(ZERO));
            savings = scored("SAVINGS", 10, points("10", supported, TARGET.multiply(i.income())),
                    supported.compareTo(TARGET.multiply(i.income())) >= 0 ? "SAVINGS_TARGET_MET" : "SAVINGS_BELOW_TARGET",
                    Map.of("income", i.income(), "allocated", i.savings(), "supported", supported, "targetRate", TARGET));
        }
        Part debt = proportional("DEBT_PAYMENTS", 20, i.debtDue(), i.debtMarkedPaid(),
                "DEBT_INPUT_MISSING", "NO_APPLICABLE_TRACKED_DEBT", "DEBT_PAID_MARKER_PROXY");
        Part goals = proportional("GOAL_PROGRESS", 15, i.goalTarget(), i.goalCredit(),
                "GOAL_INPUT_MISSING", "NO_APPLICABLE_GOALS", "GOAL_RECORDED_PROGRESS");
        Part emergency;
        if (i.reserve() == null || i.reserveAsOf() == null) {
            emergency = missing("EMERGENCY_SAVINGS", 10, "EMERGENCY_DECLARATION_MISSING");
        } else if (i.needs().signum() <= 0) {
            emergency = missing("EMERGENCY_SAVINGS", 10, "ESSENTIAL_BASELINE_UNAVAILABLE");
        } else {
            emergency = scored("EMERGENCY_SAVINGS", 10, emergencyPoints(i.reserve(), i.needs()),
                    "EMERGENCY_USER_DECLARED", Map.of("reserve", i.reserve(), "monthlyNeeds", i.needs()));
        }
        List<Part> parts = List.of(spending, debt, goals, savings, emergency);
        int scored = parts.stream().filter(p -> p.status().equals("SCORED")).mapToInt(Part::weight).sum();
        int missing = parts.stream().filter(p -> p.status().equals("UNAVAILABLE")).mapToInt(Part::weight).sum();
        int na = 100 - scored - missing;
        BigDecimal earned = parts.stream().filter(p -> p.points() != null).map(Part::points)
                .reduce(ZERO, BigDecimal::add);
        Integer total = missing == 0 ? earned.multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(scored), 0, RoundingMode.HALF_UP).intValueExact() : null;
        return new ScoreResponse(month, "D20-v1", total == null ? "UNAVAILABLE" : "COMPLETE", total,
                scored, missing, na, i.reserveAsOf(), parts);
    }

    private static Part proportional(String key, int weight, BigDecimal denominator, BigDecimal numerator,
            String missingReason, String naReason, String reason) {
        if (denominator == null || numerator == null) return missing(key, weight, missingReason);
        if (denominator.signum() == 0) return new Part(key, weight, "NOT_APPLICABLE", null, naReason, Map.of());
        return scored(key, weight, points(Integer.toString(weight), numerator, denominator), reason,
                Map.of("credited", numerator, "target", denominator));
    }

    private static BigDecimal points(String weight, BigDecimal numerator, BigDecimal denominator) {
        return new BigDecimal(weight).multiply(numerator.max(ZERO).min(denominator))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal emergencyPoints(BigDecimal reserve, BigDecimal needs) {
        String[] ratios = {"0", "0.25", "0.5", "1", "3"};
        String[] values = {"0", "2.5", "5", "7.5", "10"};
        for (int j = 1; j < ratios.length; j++) {
            BigDecimal low = needs.multiply(new BigDecimal(ratios[j - 1]));
            BigDecimal high = needs.multiply(new BigDecimal(ratios[j]));
            if (reserve.compareTo(high) <= 0) {
                BigDecimal base = new BigDecimal(values[j - 1]);
                return base.add(new BigDecimal(values[j]).subtract(base).multiply(reserve.subtract(low).max(ZERO))
                        .divide(high.subtract(low), 2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.UNNECESSARY);
            }
        }
        return new BigDecimal("10.00");
    }

    private static Part scored(String key, int weight, BigDecimal points, String reason, Map<String, BigDecimal> facts) {
        return new Part(key, weight, "SCORED", points, reason, facts);
    }

    private static Part missing(String key, int weight, String reason) {
        return new Part(key, weight, "UNAVAILABLE", null, reason, Map.of());
    }
}
