package lk.finora.api.advice.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Server-side aggregates for D58 goal coaching. Labels are built-in category names or "Other want"/"Other need";
 * the goal carries amounts and a date only, never its name.
 */
public record CoachingInputs(List<Bucket> wants, List<Bucket> needs, BigDecimal early, BigDecimal middle,
                             BigDecimal late, Goal goal) {
    public CoachingInputs {
        wants = List.copyOf(wants);
        needs = List.copyOf(needs);
    }

    /** Spending in one category group this month and the average of the three months before it. */
    public record Bucket(String label, BigDecimal spend, BigDecimal average) {}

    /** saved = contributions up to the month end; recent = contributions in the 3 months ending with the month. */
    public record Goal(BigDecimal target, BigDecimal saved, BigDecimal recent, LocalDate targetDate) {}

    public static CoachingInputs none() {
        BigDecimal zero = new BigDecimal("0.00");
        return new CoachingInputs(List.of(), List.of(), zero, zero, zero, null);
    }
}
