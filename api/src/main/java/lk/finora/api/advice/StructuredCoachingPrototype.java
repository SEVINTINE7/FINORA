package lk.finora.api.advice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Option C review prototype. No controller, provider registration or production caller uses it. */
final class StructuredCoachingPrototype {
    enum Focus {
        GOAL_PLAN, CUT_WANTS, NEEDS_BEHAVIOUR, SPENDING_PATTERN, SALARY_PLAN
    }

    // Locale-neutral identifiers for reviewed English and Sinhala UI templates.
    enum Warning {
        NO_AFFORDABLE_TRANSFER, INSUFFICIENT_FOR_TARGET_DATE, ESTIMATE_UNAVAILABLE
    }

    record Delivery(boolean personalized, List<AdviceItem> sections, List<Warning> warnings) {
        Delivery {
            sections = List.copyOf(sections);
            warnings = List.copyOf(warnings);
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /** This is the complete allowed provider choice set for the already owner-scoped fact sheet. */
    List<Focus> eligibleFocus(FactSheet facts) {
        return RuleAdviceEngine.coach(facts).stream().map(a -> Focus.valueOf(a.actionId())).toList();
    }

    Delivery deliver(FactSheet facts, String providerReply) {
        var canonical = RuleAdviceEngine.generate(facts);
        var warnings = warnings(facts);
        var focus = parse(providerReply);
        if (focus == null) return new Delivery(false, canonical, warnings);

        var ordered = new ArrayList<>(canonical);
        for (int i = 0; i < ordered.size(); i++) {
            if (focus.name().equals(ordered.get(i).actionId())) {
                ordered.add(0, ordered.remove(i));
                return new Delivery(true, ordered, warnings);
            }
        }
        return new Delivery(false, canonical, warnings);
    }

    private static Focus parse(String reply) {
        if (reply == null || reply.length() > 256) return null;
        try {
            var node = JSON.readTree(reply);
            if (node == null || !node.isObject() || !node.propertyNames().equals(Set.of("focus"))
                    || !node.get("focus").isString()) return null;
            return Focus.valueOf(node.get("focus").asString());
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static List<Warning> warnings(FactSheet facts) {
        var amounts = facts.facts();
        if (!amounts.containsKey(FactKey.FOCUS_GOAL_REMAINING)) return List.of();
        BigDecimal required = amounts.get(FactKey.FOCUS_GOAL_MONTHLY_NEEDED);
        BigDecimal transfer = amounts.get(FactKey.PLAN_GOAL_TRANSFER);
        var result = new ArrayList<Warning>();
        if (required == null || transfer == null || !amounts.containsKey(FactKey.FOCUS_GOAL_MONTHS_WITH_PLAN)) {
            result.add(Warning.ESTIMATE_UNAVAILABLE);
        }
        if (transfer != null && transfer.signum() == 0) result.add(Warning.NO_AFFORDABLE_TRANSFER);
        if (required != null && transfer != null && transfer.compareTo(required) < 0) {
            result.add(Warning.INSUFFICIENT_FOR_TARGET_DATE);
        }
        return result;
    }
}
