package lk.finora.api.advice.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import lk.finora.api.analytics.MonthMetrics;
import lk.finora.api.score.dto.ScoreResponse;
import lk.finora.api.score.dto.ScoreResponse.Part;

/** Constructs facts from calculated values only, never entities, labels, notes or merchants. */
public final class FactSheetFactory {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private FactSheetFactory() {}

    /** The approved T25 facts only (no coaching facts). */
    public static FactSheet from(MonthMetrics m, ScoreResponse score) {
        return base(m, score);
    }

    /** D58: the T25 facts plus goal-coaching facts. Every coaching number is calculated here, never by the LLM. */
    public static FactSheet from(MonthMetrics m, ScoreResponse score, CoachingInputs coaching) {
        FactSheet base = base(m, score);
        EnumMap<FactKey, BigDecimal> f = new EnumMap<>(FactKey.class);
        f.putAll(base.facts());
        Map<String, String> slots = new HashMap<>();
        addCoaching(f, slots, m, coaching);
        return new FactSheet(base.month(), f, base.categoryLabels(), slots);
    }

    private static FactSheet base(MonthMetrics m, ScoreResponse score) {
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

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static void addCoaching(EnumMap<FactKey, BigDecimal> f, Map<String, String> slots, MonthMetrics m,
                                    CoachingInputs c) {
        BigDecimal income = m.income().amount();
        BigDecimal wants = m.wants().amount();
        BigDecimal consumption = f.get(FactKey.CONSUMPTION);
        if (income.signum() > 0) {
            f.put(FactKey.WANTS_SHARE, percent(wants, income));
            f.put(FactKey.NEEDS_SHARE, percent(m.needs().amount(), income));
        }
        BigDecimal cutTotal = ZERO;
        FactKey[][] wantKeys = {
                {FactKey.TOP_WANT_1_SPEND, FactKey.TOP_WANT_1_AVERAGE, FactKey.TOP_WANT_1_CUT},
                {FactKey.TOP_WANT_2_SPEND, FactKey.TOP_WANT_2_AVERAGE, FactKey.TOP_WANT_2_CUT},
                {FactKey.TOP_WANT_3_SPEND, FactKey.TOP_WANT_3_AVERAGE, FactKey.TOP_WANT_3_CUT}};
        for (int i = 0; i < Math.min(3, c.wants().size()); i++) {
            CoachingInputs.Bucket b = c.wants().get(i);
            BigDecimal cut = suggestedCut(b);
            slots.put("TOP_WANT_" + (i + 1), b.label());
            f.put(wantKeys[i][0], b.spend());
            if (b.average().signum() > 0) f.put(wantKeys[i][1], b.average());
            f.put(wantKeys[i][2], cut);
            cutTotal = cutTotal.add(cut);
        }
        if (!c.wants().isEmpty()) f.put(FactKey.WANTS_CUT_TOTAL, cutTotal);
        FactKey[][] needKeys = {{FactKey.TOP_NEED_1_SPEND, FactKey.TOP_NEED_1_AVERAGE},
                {FactKey.TOP_NEED_2_SPEND, FactKey.TOP_NEED_2_AVERAGE}};
        for (int i = 0; i < Math.min(2, c.needs().size()); i++) {
            CoachingInputs.Bucket b = c.needs().get(i);
            slots.put("TOP_NEED_" + (i + 1), b.label());
            f.put(needKeys[i][0], b.spend());
            if (b.average().signum() > 0) f.put(needKeys[i][1], b.average());
        }
        BigDecimal timed = c.early().add(c.middle()).add(c.late());
        if (timed.signum() > 0) {
            f.put(FactKey.SPEND_EARLY, c.early());
            f.put(FactKey.SPEND_MIDDLE, c.middle());
            f.put(FactKey.SPEND_LATE, c.late());
            f.put(FactKey.EARLY_SPEND_SHARE, percent(c.early(), timed));
        }

        BigDecimal remaining = null;
        BigDecimal needed = null;
        CoachingInputs.Goal g = c.goal();
        if (g != null && g.target().compareTo(g.saved()) > 0) {
            remaining = g.target().subtract(g.saved());
            f.put(FactKey.FOCUS_GOAL_TARGET, g.target());
            f.put(FactKey.FOCUS_GOAL_SAVED, g.saved());
            f.put(FactKey.FOCUS_GOAL_REMAINING, remaining);
            if (g.targetDate() != null) {
                long months = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.parse(m.month()), YearMonth.from(g.targetDate())));
                f.put(FactKey.FOCUS_GOAL_MONTHS_LEFT, whole(BigDecimal.valueOf(months)));
                needed = remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
                f.put(FactKey.FOCUS_GOAL_MONTHLY_NEEDED, needed);
            }
            BigDecimal pace = g.recent().divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            f.put(FactKey.FOCUS_GOAL_MONTHLY_PACE, pace);
            if (pace.signum() > 0) f.put(FactKey.FOCUS_GOAL_MONTHS_AT_PACE, monthsToFinish(remaining, pace));
        }

        if (income.signum() > 0) {
            BigDecimal planNeeds = consumption.subtract(wants).max(ZERO);
            BigDecimal cap = wants.subtract(cutTotal).max(ZERO).min(f.get(FactKey.WANTS_REFERENCE));
            BigDecimal free = income.subtract(planNeeds).subtract(cap).max(ZERO);
            BigDecimal transfer = needed == null ? free : needed.min(free);
            f.put(FactKey.PLAN_NEEDS, planNeeds);
            f.put(FactKey.PLAN_WANTS_CAP, cap);
            f.put(FactKey.PLAN_WEEKLY_WANTS, cap.divide(new BigDecimal("4"), 0, RoundingMode.DOWN).setScale(2));
            f.put(FactKey.PLAN_GOAL_TRANSFER, transfer);
            f.put(FactKey.PLAN_BUFFER, free.subtract(transfer));
            if (remaining != null && transfer.signum() > 0)
                f.put(FactKey.FOCUS_GOAL_MONTHS_WITH_PLAN, monthsToFinish(remaining, transfer));
        }
    }

    /** A quarter of the category, or the excess over the usual average if larger, capped at half; whole rupees. */
    static BigDecimal suggestedCut(CoachingInputs.Bucket b) {
        BigDecimal quarter = b.spend().multiply(new BigDecimal("0.25"));
        BigDecimal excess = b.average().signum() > 0 ? b.spend().subtract(b.average()).max(ZERO) : ZERO;
        BigDecimal half = b.spend().multiply(new BigDecimal("0.5"));
        return quarter.max(excess).min(half).setScale(0, RoundingMode.HALF_UP).setScale(2);
    }

    private static BigDecimal monthsToFinish(BigDecimal remaining, BigDecimal perMonth) {
        return whole(remaining.divide(perMonth, 0, RoundingMode.CEILING));
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole(part.multiply(HUNDRED).divide(whole, 0, RoundingMode.HALF_UP));
    }

    private static BigDecimal whole(BigDecimal value) { return value.setScale(2, RoundingMode.UNNECESSARY); }
}
