package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.rules.RuleAdviceEngine;

class StructuredCoachingPrototypeTest {
    private static final FactSheet GOAL = new FactSheet("2026-11", Map.of(
            FactKey.FOCUS_GOAL_TARGET, money(1200), FactKey.FOCUS_GOAL_SAVED, money(300),
            FactKey.FOCUS_GOAL_REMAINING, money(900), FactKey.FOCUS_GOAL_MONTHS_LEFT, money(3),
            FactKey.FOCUS_GOAL_MONTHLY_NEEDED, money(300), FactKey.PLAN_GOAL_TRANSFER, money(100),
            FactKey.FOCUS_GOAL_MONTHS_WITH_PLAN, money(9), FactKey.PLAN_NEEDS, money(400),
            FactKey.PLAN_WANTS_CAP, money(100), FactKey.PLAN_BUFFER, money(0)), List.of());
    private final StructuredCoachingPrototype prototype = new StructuredCoachingPrototype();

    @Test
    void historicalElevenContradictionsCannotEnterTheStructuredDelivery() {
        var claims = List.of(
                "LKR 900 is fully affordable now.",
                "Only LKR 100 is required each month.",
                "You already saved LKR 900.",
                "LKR 300 is the total remaining balance.",
                "There is no saving capacity despite a LKR 100 transfer.",
                "The plan will finish in 3 months.",
                "Your spending history is complete and LKR 900 remains.",
                "Your payday is known. Move LKR 100 then.",
                "Move LKR 900 immediately.",
                "The required amount is below LKR 100.",
                "You do not need the remaining LKR 900.");
        for (String claim : claims) {
            String reply = AdviceTestFixtures.JSON.writeValueAsString(Map.of("focus", "SALARY_PLAN", "text", claim));
            assertFallback(reply);
        }
    }

    @Test
    void aSupportedProviderChoiceCanPrioritizeButNeverChangeServerContent() {
        var canonical = RuleAdviceEngine.generate(GOAL);
        assertThat(prototype.eligibleFocus(GOAL)).containsExactly(
                StructuredCoachingPrototype.Focus.GOAL_PLAN, StructuredCoachingPrototype.Focus.SALARY_PLAN);
        var goal = prototype.deliver(GOAL, "{\"focus\":\"GOAL_PLAN\"}");
        var salary = prototype.deliver(GOAL, "{\"focus\":\"SALARY_PLAN\"}");
        assertThat(goal.personalized()).isTrue();
        assertThat(salary.personalized()).isTrue();
        assertThat(goal.sections()).containsExactlyElementsOf(canonical);
        assertThat(salary.sections()).containsExactly(canonical.get(1), canonical.get(0));
        assertThat(salary.warnings()).containsExactly(StructuredCoachingPrototype.Warning.INSUFFICIENT_FOR_TARGET_DATE);
        assertThat(goal.warnings()).isEqualTo(salary.warnings());
        assertThat(prototype.deliver(GOAL, null).warnings()).isEqualTo(salary.warnings());
    }

    @Test
    void malformedUnknownAndIneligibleChoicesFallBackInFull() {
        for (String reply : List.of("", "{}", "[]", "{", "{\"focus\":null}", "{\"focus\":1}",
                "{\"focus\":\"UNKNOWN\"}", "{\"focus\":\"CUT_WANTS\"}",
                "{\"focus\":\"GOAL_PLAN\",\"warnings\":[]}",
                "{\"focus\":\"GOAL_PLAN\",\"focus\":\"SALARY_PLAN\"}",
                "{\"focus\":\"GOAL_PLAN\"} {}")) {
            assertFallback(reply);
        }
        assertFallback(null);
    }

    private void assertFallback(String reply) {
        var result = prototype.deliver(GOAL, reply);
        assertThat(result.personalized()).isFalse();
        assertThat(result.sections()).containsExactlyElementsOf(RuleAdviceEngine.generate(GOAL));
        assertThat(result.warnings()).containsExactly(StructuredCoachingPrototype.Warning.INSUFFICIENT_FOR_TARGET_DATE);
    }

    private static BigDecimal money(int value) { return BigDecimal.valueOf(value).setScale(2); }
}
