package lk.finora.api.advice.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.AdviceItem.NumberFact;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;

/** Approved T25 catalogue. Review actions never assert repayment verification or prescribe essential cuts. */
public final class RuleAdviceEngine {
    public static final Set<String> ACTIONS = Set.of("REVIEW_SPENDING_DEFICIT", "REVIEW_HIGH_WANTS",
            "REVIEW_UNMARKED_LOAN_BILLS", "BUILD_EMERGENCY_BUFFER", "REVIEW_GOAL_PROGRESS", "SAVE_WITHIN_AVAILABLE_HEADROOM");
    private RuleAdviceEngine() {}

    public static List<AdviceItem> generate(FactSheet f) {
        List<AdviceItem> result = new ArrayList<>();
        if (positive(f, FactKey.SPENDING_DEFICIT)) add(result, f, "REVIEW_SPENDING_DEFICIT",
                "Recorded spending exceeds recorded income by LKR %s. Review the shortfall while protecting essential costs.", FactKey.SPENDING_DEFICIT);
        if (positive(f, FactKey.INCOME) && value(f, FactKey.WANTS).compareTo(value(f, FactKey.INCOME).multiply(new BigDecimal("0.40"))) > 0) {
            add(result, f, "REVIEW_HIGH_WANTS", "Recorded wants are LKR %s; the rounded policy reference is LKR %s. Review optional spending.",
                    FactKey.WANTS, FactKey.WANTS_REFERENCE);
        }
        if (positive(f, FactKey.UNMARKED_LOAN_BILLS)) add(result, f, "REVIEW_UNMARKED_LOAN_BILLS",
                "Loan bills totaling LKR %s are not marked paid. Review their recorded status; markers do not verify payment.", FactKey.UNMARKED_LOAN_BILLS);
        if (positive(f, FactKey.EMERGENCY_GAP)) add(result, f, "BUILD_EMERGENCY_BUFFER",
                "Declared emergency reserves are LKR %s; the next reserve milestone is LKR %s. Build gradually after essentials and due payments.",
                FactKey.EMERGENCY_RESERVE, FactKey.EMERGENCY_NEXT_TARGET);
        if (positive(f, FactKey.GOAL_REMAINING)) add(result, f, "REVIEW_GOAL_PROGRESS",
                "Recorded goal allocations remain LKR %s below current targets. Review affordability before allocating more.", FactKey.GOAL_REMAINING);
        if (positive(f, FactKey.AFFORDABLE_SAVINGS)) add(result, f, "SAVE_WITHIN_AVAILABLE_HEADROOM",
                "Recorded headroom supports up to LKR %s toward the initial savings target. Confirm all essential costs and obligations first.", FactKey.AFFORDABLE_SAVINGS);
        return List.copyOf(result);
    }

    private static boolean positive(FactSheet f, FactKey k) { return value(f, k).signum() > 0; }
    private static BigDecimal value(FactSheet f, FactKey k) { return f.facts().getOrDefault(k, BigDecimal.ZERO); }
    private static void add(List<AdviceItem> out, FactSheet f, String id, String template, FactKey... keys) {
        List<NumberFact> numbers = new ArrayList<>();
        List<String> formatted = new ArrayList<>();
        for (FactKey key : keys) {
            if (!f.facts().containsKey(key)) return;
            BigDecimal n = f.facts().get(key).setScale(2, java.math.RoundingMode.UNNECESSARY);
            formatted.add(n.toPlainString());
            numbers.add(new NumberFact(key, n, "LKR", f.month()));
        }
        out.add(new AdviceItem(id, String.format(java.util.Locale.ROOT, template, formatted.toArray()), numbers));
    }
}
