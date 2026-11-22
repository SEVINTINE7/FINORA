package lk.finora.api.advice.ai;

import java.math.BigDecimal;
import java.util.List;

/** Shared rule/AI response shape. Every number carries its fact, unit and period. */
public record AdviceItem(String actionId, String text, List<NumberFact> numbers) {
    public AdviceItem { numbers = List.copyOf(numbers); }
    public record NumberFact(FactKey factKey, BigDecimal value, String unit, String period) {}
}
