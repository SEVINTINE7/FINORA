package lk.finora.api.advice.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T25 catalogue actions must match the eligible rule text exactly. D58 coaching sections may be worded freely, but
 * every number in their text must, in order, equal a listed fact that the server calculated (same key, unit and
 * period), all eligible sections must be present, and unsafe phrasing is rejected. Semantic accuracy of free wording
 * is not claimed beyond these checks.
 */
public final class AdviceVerifier {
    public enum Reason { ACCEPTED, MALFORMED_JSON, INVALID_SHAPE, UNKNOWN_ACTION, DUPLICATE_ACTION,
        UNSUPPORTED_CLAIM, FACT_MISMATCH, WRONG_UNIT_OR_PERIOD, TEXT_NUMBER_MISMATCH, PROVIDER_FAILURE,
        MISSING_SECTION, UNSAFE_TEXT }
    public record Verdict(boolean verified, Reason reason, List<AdviceItem> advice) {
        static Verdict reject(Reason reason) { return new Verdict(false, reason, List.of()); }
    }
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    /** Free-text numbers may use thousands separators: 9,625 or 54,900.00. */
    private static final Pattern FREE_NUMBER = Pattern.compile("[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?");
    private static final int MAX_SECTION_TEXT = 1200;
    /** Products, borrowing, guarantees, links and cuts to essential (need) categories are never allowed. */
    private static final Pattern UNSAFE = Pattern.compile("(?i)guarantee|risk[- ]free|crypto|stock|shares|invest|"
            + "borrow|take (?:out )?a loan|new loan|credit card|lottery|gambl|https?:|www\\.|@|"
            + "\\b(?:cut|reduce|lower|stop|skip|drop)\\b[^.,;]{0,30}\\b(?:food(?! delivery)|groceries|transport|rent|"
            + "utilities|mobile/internet|health|medicine|education|loan payment)\\b");

    public Verdict verify(String reply, FactSheet facts) {
        if (reply == null || reply.length() > 16000) return Verdict.reject(Reason.INVALID_SHAPE);
        try {
            JsonNode root = mapper.readTree(reply);
            if (root != null && root.isObject()) {
                if (!root.propertyNames().equals(Set.of("advice"))) return Verdict.reject(Reason.INVALID_SHAPE);
                root = root.get("advice");
            }
            if (root == null || !root.isArray() || root.isEmpty() || root.size() > 12) return Verdict.reject(Reason.INVALID_SHAPE);
            List<AdviceItem> ordered = RuleAdviceEngine.generate(facts);
            Map<String, AdviceItem> eligible = ordered.stream().collect(Collectors.toMap(AdviceItem::actionId, a -> a));
            Set<String> seen = new HashSet<>();
            List<AdviceItem> accepted = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isObject() || !item.propertyNames().equals(Set.of("actionId", "text", "numbers"))
                        || !item.path("actionId").isString() || !item.path("text").isString()
                        || !item.path("numbers").isArray()) return Verdict.reject(Reason.INVALID_SHAPE);
                String id = item.get("actionId").asString();
                AdviceItem expected = eligible.get(id);
                if (expected == null) return Verdict.reject(Reason.UNKNOWN_ACTION);
                if (!seen.add(id)) return Verdict.reject(Reason.DUPLICATE_ACTION);
                if (RuleAdviceEngine.SECTIONS.contains(id)) {
                    Object section = section(id, item.get("text").asString(), item.get("numbers"), facts);
                    if (section instanceof Reason failure) return Verdict.reject(failure);
                    accepted.add((AdviceItem) section);
                    continue;
                }
                // Provisional claim verification is deliberately exact; a new guarantee/forecast cannot slip through.
                if (!expected.text().equals(item.get("text").asString())) return Verdict.reject(Reason.UNSUPPORTED_CLAIM);
                JsonNode numbers = item.get("numbers");
                if (numbers.size() != expected.numbers().size()) return Verdict.reject(Reason.FACT_MISMATCH);
                List<BigDecimal> tokens = NUMBER.matcher(expected.text()).results().map(r -> new BigDecimal(r.group())).toList();
                if (tokens.size() != numbers.size()) return Verdict.reject(Reason.TEXT_NUMBER_MISMATCH);
                for (int j = 0; j < numbers.size(); j++) {
                    JsonNode n = numbers.get(j);
                    var canonical = expected.numbers().get(j);
                    if (!n.isObject() || !n.propertyNames().equals(Set.of("factKey", "value", "unit", "period"))
                            || !n.path("factKey").isString() || !n.path("value").isNumber()
                            || !n.path("unit").isString() || !n.path("period").isString()) return Verdict.reject(Reason.INVALID_SHAPE);
                    if (!"LKR".equals(n.get("unit").asString()) || !facts.month().equals(n.get("period").asString()))
                        return Verdict.reject(Reason.WRONG_UNIT_OR_PERIOD);
                    if (!canonical.factKey().name().equals(n.get("factKey").asString())) return Verdict.reject(Reason.FACT_MISMATCH);
                    BigDecimal amount = n.get("value").decimalValue();
                    try { amount.setScale(2, java.math.RoundingMode.UNNECESSARY); }
                    catch (ArithmeticException precision) { return Verdict.reject(Reason.FACT_MISMATCH); }
                    if (amount.compareTo(canonical.value()) != 0 || amount.compareTo(facts.facts().get(canonical.factKey())) != 0)
                        return Verdict.reject(Reason.FACT_MISMATCH);
                    if (amount.compareTo(tokens.get(j)) != 0) return Verdict.reject(Reason.TEXT_NUMBER_MISMATCH);
                }
                // Return trusted canonical objects, never provider-created text or number objects.
                accepted.add(expected);
            }
            for (AdviceItem expected : ordered) {
                if (RuleAdviceEngine.SECTIONS.contains(expected.actionId()) && !seen.contains(expected.actionId()))
                    return Verdict.reject(Reason.MISSING_SECTION);
            }
            return new Verdict(true, Reason.ACCEPTED, List.copyOf(accepted));
        } catch (RuntimeException invalid) {
            return Verdict.reject(Reason.MALFORMED_JSON);
        }
    }

    /** Returns the trusted section (provider wording, server-built number facts) or the rejection reason. */
    private static Object section(String id, String text, JsonNode numbers, FactSheet facts) {
        if (text.isBlank() || text.length() > MAX_SECTION_TEXT || text.chars().anyMatch(ch -> ch < 0x20))
            return Reason.INVALID_SHAPE;
        if (UNSAFE.matcher(text).find()) return Reason.UNSAFE_TEXT;
        List<BigDecimal> tokens = FREE_NUMBER.matcher(text).results()
                .map(r -> new BigDecimal(r.group().replace(",", ""))).toList();
        if (tokens.size() != numbers.size()) return Reason.TEXT_NUMBER_MISMATCH;
        List<AdviceItem.NumberFact> trusted = new ArrayList<>();
        for (int j = 0; j < numbers.size(); j++) {
            JsonNode n = numbers.get(j);
            if (!n.isObject() || !n.propertyNames().equals(Set.of("factKey", "value", "unit", "period"))
                    || !n.path("factKey").isString() || !n.path("value").isNumber()
                    || !n.path("unit").isString() || !n.path("period").isString()) return Reason.INVALID_SHAPE;
            FactKey key;
            try { key = FactKey.valueOf(n.get("factKey").asString()); }
            catch (IllegalArgumentException unknown) { return Reason.FACT_MISMATCH; }
            BigDecimal fact = facts.facts().get(key);
            if (fact == null) return Reason.FACT_MISMATCH;
            if (!key.unit().equals(n.get("unit").asString()) || !facts.month().equals(n.get("period").asString()))
                return Reason.WRONG_UNIT_OR_PERIOD;
            if (n.get("value").decimalValue().compareTo(fact) != 0) return Reason.FACT_MISMATCH;
            if (tokens.get(j).compareTo(fact) != 0) return Reason.TEXT_NUMBER_MISMATCH;
            trusted.add(new AdviceItem.NumberFact(key, fact.setScale(2), key.unit(), facts.month()));
        }
        return new AdviceItem(id, text.strip(), trusted);
    }
}
