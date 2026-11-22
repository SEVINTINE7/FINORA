package lk.finora.api.advice.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;

import lk.finora.api.analytics.MonthMetrics;
import lk.finora.api.score.dto.ScoreResponse;
import lk.finora.api.score.dto.ScoreResponse.Part;

/** Constructs facts from calculated values only, never entities, labels, notes or merchants. */
public final class FactSheetFactory {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private FactSheetFactory() {}

    public static FactSheet from(MonthMetrics m, ScoreResponse score) {
        if (!m.month().equals(score.month())) throw new IllegalArgumentException("Fact periods must match");
        EnumMap<FactKey, BigDecimal> f = new EnumMap<>(FactKey.class);
        BigDecimal income = m.income().amount();
        BigDecimal savings = m.savedToCategory().amount();
        BigDecimal consumption = m.expense().amount().subtract(savings);
        BigDecimal supported = savings.min(income.subtract(consumption).max(ZERO));
        BigDecimal target = income.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal headroom = m.leftOver().amount().max(ZERO);
        f.put(FactKey.INCOME, income); f.put(FactKey.CONSUMPTION, consumption);
        f.put(FactKey.NEEDS, m.needs().amount()); f.put(FactKey.WANTS, m.wants().amount());
        f.put(FactKey.WANTS_REFERENCE, income.multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP));
        f.put(FactKey.SAVINGS_ALLOCATED, savings); f.put(FactKey.SUPPORTED_SAVINGS, supported);
        f.put(FactKey.SAVINGS_TARGET, target); f.put(FactKey.SAVINGS_SHORTFALL, target.subtract(supported).max(ZERO));
        f.put(FactKey.AVAILABLE_HEADROOM, headroom);
        f.put(FactKey.SPENDING_DEFICIT, consumption.subtract(income).max(ZERO));
        Part debt = part(score, "DEBT_PAYMENTS");
        if (debt.status().equals("NOT_APPLICABLE")) f.put(FactKey.UNMARKED_LOAN_BILLS, ZERO);
        if (debt.status().equals("SCORED")) f.put(FactKey.UNMARKED_LOAN_BILLS,
                debt.facts().get("target").subtract(debt.facts().get("credited")).max(ZERO));
        // Do not suggest extra allocations when tracked debt evidence is missing or a bill is unmarked.
        BigDecimal unmarked = f.get(FactKey.UNMARKED_LOAN_BILLS);
        if (income.signum() > 0 && unmarked != null && unmarked.signum() == 0) {
            f.put(FactKey.AFFORDABLE_SAVINGS, headroom.min(f.get(FactKey.SAVINGS_SHORTFALL)));
        }
        Part goals = part(score, "GOAL_PROGRESS");
        if (goals.status().equals("SCORED")) {
            f.put(FactKey.GOAL_TARGET, goals.facts().get("target"));
            f.put(FactKey.GOAL_CREDITED, goals.facts().get("credited"));
            f.put(FactKey.GOAL_REMAINING, goals.facts().get("target").subtract(goals.facts().get("credited")).max(ZERO));
        }
        Part emergency = part(score, "EMERGENCY_SAVINGS");
        if (emergency.status().equals("SCORED")) {
            BigDecimal reserve = emergency.facts().get("reserve");
            f.put(FactKey.EMERGENCY_RESERVE, reserve);
            for (String ratio : new String[] {"0.25", "0.5", "1", "3"}) {
                BigDecimal next = m.needs().amount().multiply(new BigDecimal(ratio)).setScale(2, RoundingMode.HALF_UP);
                if (next.compareTo(reserve) > 0) {
                    f.put(FactKey.EMERGENCY_NEXT_TARGET, next);
                    f.put(FactKey.EMERGENCY_GAP, next.subtract(reserve));
                    break;
                }
            }
        }
        ArrayList<String> labels = new ArrayList<>();
        if (m.needs().amount().signum() > 0) labels.add("NEED");
        if (m.wants().amount().signum() > 0) labels.add("WANT");
        if (savings.signum() > 0) labels.add("SAVINGS");
        return new FactSheet(m.month(), f, labels);
    }

    private static Part part(ScoreResponse score, String key) {
        return score.parts().stream().filter(p -> p.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing D20 component"));
    }
}
