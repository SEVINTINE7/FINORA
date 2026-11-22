package lk.finora.api.advice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import tools.jackson.databind.json.JsonMapper;

final class AdviceTestFixtures {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static FactSheet facts() {
        return new FactSheet("2026-11", Map.of(
                FactKey.INCOME, new BigDecimal("40000.00"),
                FactKey.WANTS, new BigDecimal("20000.00"),
                FactKey.WANTS_REFERENCE, new BigDecimal("16000.00"),
                FactKey.SPENDING_DEFICIT, new BigDecimal("5000.00"),
                FactKey.UNMARKED_LOAN_BILLS, new BigDecimal("5000.00")), List.of("NEED", "WANT"));
    }
    static List<AdviceItem> advice() { return RuleAdviceEngine.generate(facts()); }
    static String reply() { return JSON.writeValueAsString(Map.of("advice", advice())); }
    static String groqEnvelope(String reply) {
        return JSON.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                "message", Map.of("content", reply))), "usage", Map.of("prompt_tokens", 10, "completion_tokens", 20)));
    }
    static String geminiEnvelope(String reply) {
        return JSON.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content",
                Map.of("parts", List.of(Map.of("text", reply))))), "usageMetadata", Map.of("promptTokenCount", 10, "candidatesTokenCount", 20)));
    }
    private AdviceTestFixtures() {}
}
