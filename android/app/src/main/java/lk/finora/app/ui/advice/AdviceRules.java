package lk.finora.app.ui.advice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.repo.MonthlyRepository.Fact;

/** T66.2/D70: pure Advice display rules, kept out of the activity for unit tests. */
public final class AdviceRules {
    private AdviceRules() {}

    /** Heading for a D58 coaching section, or 0 for a T25 catalogue item ("Next step"). */
    public static int section(String actionId) {
        Integer section = AdviceActivity.SECTIONS.get(actionId);
        return section == null ? 0 : section;
    }

    public static int icon(String actionId) {
        return switch (actionId) {
            case "GOAL_PLAN" -> R.drawable.finora_ic_target;
            case "CUT_WANTS", "REVIEW_HIGH_WANTS" -> R.drawable.finora_ic_cat_bag;
            case "NEEDS_BEHAVIOUR" -> R.drawable.finora_ic_cat_cart;
            case "SPENDING_PATTERN" -> R.drawable.finora_ic_sun;
            case "SALARY_PLAN" -> R.drawable.finora_ic_cat_salary;
            case "BUILD_EMERGENCY_BUFFER", "SAVE_WITHIN_AVAILABLE_HEADROOM" -> R.drawable.finora_ic_cat_savings;
            default -> R.drawable.finora_ic_info;
        };
    }

    /** 0 = green (plans), 1 = amber (wants and timing), 2 = blue (salary), 3 = neutral (other steps). */
    public static int tone(String actionId) {
        return switch (actionId) {
            case "GOAL_PLAN", "NEEDS_BEHAVIOUR", "BUILD_EMERGENCY_BUFFER", "SAVE_WITHIN_AVAILABLE_HEADROOM" -> 0;
            case "CUT_WANTS", "SPENDING_PATTERN", "REVIEW_HIGH_WANTS" -> 1;
            case "SALARY_PLAN" -> 2;
            default -> 3;
        };
    }

    /** The item's checked figures in order, each shown once (the same value and unit may repeat in the text). */
    public static List<Fact> distinct(List<Fact> numbers) {
        List<Fact> out = new ArrayList<>();
        for (Fact fact : numbers) {
            boolean seen = false;
            for (Fact kept : out) {
                if (kept.unit().equals(fact.unit()) && kept.value().compareTo(fact.value()) == 0) { seen = true; break; }
            }
            if (!seen) out.add(fact);
        }
        return out;
    }

    /** {@code 8.00} → {@code 8}; percent and month figures drop trailing zeros. */
    public static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
