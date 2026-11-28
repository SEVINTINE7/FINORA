package lk.finora.app.ui.insights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

import lk.finora.app.R;

/** T66.2/D70: pure display rules for Insights, kept out of the activity for unit tests. */
public final class InsightRules {
    private InsightRules() {}

    /** Pattern title, or 0 for a type this app does not know (the card is skipped). */
    public static int title(String type) {
        if (type == null) return 0;
        return switch (type) {
            case "HIGH_WANTS" -> R.string.insight_wants;
            case "HIGH_FIXED_BILLS" -> R.string.insight_bills;
            case "PAYDAY_SPENDING" -> R.string.insight_payday;
            case "WEEKEND_SPENDING" -> R.string.insight_weekend;
            default -> 0;
        };
    }

    static int icon(String type) {
        return switch (type) {
            case "HIGH_WANTS" -> R.drawable.finora_ic_cat_bag;
            case "HIGH_FIXED_BILLS" -> R.drawable.finora_ic_bill;
            case "PAYDAY_SPENDING" -> R.drawable.finora_ic_cat_salary;
            default -> R.drawable.finora_ic_sun;
        };
    }

    /** The amount and ratio fact keys for share patterns, or null for the weekend comparison. */
    static String[] shareKeys(String type) {
        return switch (type) {
            case "HIGH_WANTS" -> new String[]{"wants", "wantsRatio"};
            case "HIGH_FIXED_BILLS" -> new String[]{"fixedBills", "fixedBillsRatio"};
            case "PAYDAY_SPENDING" -> new String[]{"paydaySpend", "paydayShare"};
            default -> null;
        };
    }

    /** Share patterns compare with income except income-day spending, which compares with all spending. */
    static int shareText(String type) {
        return "PAYDAY_SPENDING".equals(type) ? R.string.insight_expense_share : R.string.insight_income_share;
    }

    /** {@code 0.3391} → {@code 33.91%}; {@code 0.4} → {@code 40%}. */
    public static String percent(BigDecimal ratio, Locale locale) {
        var format = NumberFormat.getPercentInstance(locale);
        format.setMaximumFractionDigits(2);
        return format.format(ratio);
    }

    /** Weekend daily average ÷ weekday daily average to one decimal place, e.g. {@code 2.7}; null if not known. */
    public static String times(Map<String, BigDecimal> facts) {
        BigDecimal weekend = facts.get("weekendDailyAverage");
        BigDecimal weekday = facts.get("weekdayDailyAverage");
        if (weekend == null || weekday == null || weekday.signum() <= 0) return null;
        return weekend.divide(weekday, 1, RoundingMode.HALF_UP).toPlainString();
    }

    /** Bar fill for the score part: points ÷ weight, 0 when not scored. */
    public static int partProgress(BigDecimal points, int weight) {
        if (points == null || weight <= 0) return 0;
        return points.multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(weight), 0, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(BigDecimal.valueOf(1000)).intValue();
    }

    /** {@code 45.00} → {@code 45.00}; points always show two decimals, as the API returns them. */
    public static String points(BigDecimal points) {
        return points.setScale(Math.max(2, points.scale()), RoundingMode.UNNECESSARY).toPlainString();
    }

    public static int partName(String key) {
        return switch (key) {
            case "SPENDING_CONTROL" -> R.string.score_spending;
            case "DEBT_PAYMENTS" -> R.string.score_debt;
            case "GOAL_PROGRESS" -> R.string.score_goals;
            case "SAVINGS" -> R.string.score_savings;
            default -> R.string.score_emergency;
        };
    }

    public static int reason(String key) {
        return switch (key) {
            case "ZERO_INCOME" -> R.string.score_zero_income;
            case "SPENDING_EXCEEDS_INCOME" -> R.string.score_spending_exceeds;
            case "SPENDING_BREAK_EVEN" -> R.string.score_spending_equal;
            case "SPENDING_WITHIN_INCOME" -> R.string.score_spending_within;
            case "SAVINGS_TARGET_MET" -> R.string.score_savings_met;
            case "SAVINGS_BELOW_TARGET" -> R.string.score_savings_below;
            case "DEBT_INPUT_MISSING" -> R.string.score_debt_missing;
            case "NO_APPLICABLE_TRACKED_DEBT" -> R.string.score_debt_na;
            case "DEBT_PAID_MARKER_PROXY" -> R.string.score_debt_basis;
            case "GOAL_INPUT_MISSING" -> R.string.score_goal_missing;
            case "NO_APPLICABLE_GOALS" -> R.string.score_goal_na;
            case "GOAL_RECORDED_PROGRESS" -> R.string.score_goal_basis;
            case "EMERGENCY_DECLARATION_MISSING" -> R.string.score_reserve_missing;
            case "ESSENTIAL_BASELINE_UNAVAILABLE" -> R.string.score_needs_missing;
            case "EMERGENCY_USER_DECLARED" -> R.string.score_reserve_basis;
            default -> R.string.records_failed;
        };
    }
}
