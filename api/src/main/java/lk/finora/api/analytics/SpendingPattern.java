package lk.finora.api.analytics;

import java.util.Map;

/**
 * A pattern found in one month (T22). facts holds only numbers (amounts, ratios, thresholds, counts)
 * so advice text can be built from them later and checked against them (rule 2 and 3: no names, no merchants).
 */
public record SpendingPattern(String type, Map<String, Object> facts) {
}
