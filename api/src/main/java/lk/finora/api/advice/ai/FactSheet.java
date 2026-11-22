package lk.finora.api.advice.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only aggregate amounts and fixed category labels may cross the LLM boundary. Missing facts are omitted. */
public record FactSheet(String month, Map<FactKey, BigDecimal> facts, List<String> categoryLabels) {
    private static final Set<String> LABELS = Set.of("NEED", "WANT", "SAVINGS");
    public FactSheet {
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException("Invalid fact period");
        facts = Map.copyOf(facts);
        categoryLabels = List.copyOf(categoryLabels);
        if (!LABELS.containsAll(categoryLabels)) throw new IllegalArgumentException("Only canonical category labels are allowed");
        for (BigDecimal value : facts.values()) {
            if (value.signum() < 0) throw new IllegalArgumentException("Fact amounts must be nonnegative");
            value.setScale(2, java.math.RoundingMode.UNNECESSARY);
        }
    }
}
