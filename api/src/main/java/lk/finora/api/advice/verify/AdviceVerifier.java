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
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** PROVISIONAL: only exact eligible rule claims can pass. No paraphrase/semantic validation is claimed. */
public final class AdviceVerifier {
    public enum Reason { ACCEPTED, MALFORMED_JSON, INVALID_SHAPE, UNKNOWN_ACTION, DUPLICATE_ACTION,
        UNSUPPORTED_CLAIM, FACT_MISMATCH, WRONG_UNIT_OR_PERIOD, TEXT_NUMBER_MISMATCH, PROVIDER_FAILURE }
    public record Verdict(boolean verified, Reason reason, List<AdviceItem> advice) {
        static Verdict reject(Reason reason) { return new Verdict(false, reason, List.of()); }
    }
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    public Verdict verify(String reply, FactSheet facts) {
        if (reply == null || reply.length() > 16000) return Verdict.reject(Reason.INVALID_SHAPE);
        try {
            JsonNode root = mapper.readTree(reply);
            if (root != null && root.isObject()) {
                if (!root.propertyNames().equals(Set.of("advice"))) return Verdict.reject(Reason.INVALID_SHAPE);
                root = root.get("advice");
            }
            if (root == null || !root.isArray() || root.isEmpty() || root.size() > 6) return Verdict.reject(Reason.INVALID_SHAPE);
            Map<String, AdviceItem> eligible = RuleAdviceEngine.generate(facts).stream()
                    .collect(Collectors.toMap(AdviceItem::actionId, a -> a));
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
            return new Verdict(true, Reason.ACCEPTED, List.copyOf(accepted));
        } catch (RuntimeException invalid) {
            return Verdict.reject(Reason.MALFORMED_JSON);
        }
    }
}
