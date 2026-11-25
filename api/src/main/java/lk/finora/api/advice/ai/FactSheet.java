package lk.finora.api.advice.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Only aggregate amounts and fixed category labels may cross the LLM boundary. Missing facts are omitted.
 * {@code slotLabels} names the TOP_WANT_n / TOP_NEED_n slots with built-in category names only (D58):
 * custom category names are grouped as "Other want" / "Other need" before they reach this record.
 */
public record FactSheet(String month, Map<FactKey, BigDecimal> facts, List<String> categoryLabels,
                        Map<String, String> slotLabels) {
    private static final Set<String> LABELS = Set.of("NEED", "WANT", "SAVINGS");
    public static final Set<String> SLOTS = Set.of("TOP_WANT_1", "TOP_WANT_2", "TOP_WANT_3", "TOP_NEED_1", "TOP_NEED_2");
    /** English names of the built-in expense categories (V2/V21 seed), plus the two groups for custom categories. */
    public static final Set<String> SLOT_NAMES = Set.of("Food", "Food delivery", "Transport", "Rent", "Utilities",
            "Mobile/Internet", "Shopping", "Entertainment", "Health", "Education", "Loan payment", "Other",
            "Other want", "Other need");

    public FactSheet(String month, Map<FactKey, BigDecimal> facts, List<String> categoryLabels) {
        this(month, facts, categoryLabels, Map.of());
    }

    public FactSheet {
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException("Invalid fact period");
        facts = Map.copyOf(facts);
        categoryLabels = List.copyOf(categoryLabels);
        slotLabels = Map.copyOf(slotLabels);
        if (!LABELS.containsAll(categoryLabels)) throw new IllegalArgumentException("Only canonical category labels are allowed");
        if (!SLOTS.containsAll(slotLabels.keySet()) || !SLOT_NAMES.containsAll(slotLabels.values()))
            throw new IllegalArgumentException("Only built-in category names are allowed");
        for (BigDecimal value : facts.values()) {
            if (value.signum() < 0) throw new IllegalArgumentException("Fact amounts must be nonnegative");
            value.setScale(2, java.math.RoundingMode.UNNECESSARY);
        }
    }
}
