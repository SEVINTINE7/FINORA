package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.AdviceItem.NumberFact;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import lk.finora.api.advice.verify.AdviceVerifier;

class AdviceVerifierTest {
    private final AdviceVerifier verifier = new AdviceVerifier();
    private String json(Object value) { return AdviceTestFixtures.JSON.writeValueAsString(value); }
    private void rejected(Object value) { assertThat(verifier.verify(json(value), AdviceTestFixtures.facts()).verified()).isFalse(); }
    private AdviceItem canonical() { return AdviceTestFixtures.advice().get(0); }

    @Test
    void exactEligibleAdvicePassesWithProviderEnvelopeOrDirectArray() {
        assertThat(verifier.verify(AdviceTestFixtures.reply(), AdviceTestFixtures.facts()).verified()).isTrue();
        var verdict = verifier.verify(json(AdviceTestFixtures.advice()), AdviceTestFixtures.facts());
        assertThat(verdict.reason()).isEqualTo(AdviceVerifier.Reason.ACCEPTED);
        assertThat(verdict.advice()).isEqualTo(AdviceTestFixtures.advice());
    }

    @Test
    void eligibleActionsCanBeSelectedAndReordered() {
        var chosen = List.of(AdviceTestFixtures.advice().get(2), AdviceTestFixtures.advice().get(0));
        assertThat(verifier.verify(json(chosen), AdviceTestFixtures.facts()).advice()).isEqualTo(chosen);
    }

    @ParameterizedTest @ValueSource(strings = {"not json", "null", "[]", "{}", "{\"advice\":null}", "42"})
    void malformedOrEmptyShapesAreRejected(String reply) {
        assertThat(verifier.verify(reply, AdviceTestFixtures.facts()).verified()).isFalse();
    }

    @Test
    void duplicateJsonFieldsTrailingDocumentsAndExtraFieldsAreRejected() {
        String valid = json(List.of(canonical()));
        assertThat(verifier.verify(valid.replace("\"actionId\":", "\"actionId\":\"EVIL\",\"actionId\":"), AdviceTestFixtures.facts()).verified()).isFalse();
        assertThat(verifier.verify(valid + " {}", AdviceTestFixtures.facts()).verified()).isFalse();
        rejected(Map.of("advice", List.of(canonical()), "account", "PRIVATE ACCOUNT"));
        assertThat(verifier.verify(valid.replace("\"unit\":\"LKR\"", "\"unit\":\"LKR\",\"account\":\"PRIVATE\""), AdviceTestFixtures.facts()).verified()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"Guaranteed financial recovery.", "Pay every bill on time: verified.",
            "Cut essential food and medicine.", "Save ten thousand tomorrow.", "Spending will fall next month.",
            "Your spending deficit is LKR 5000.01."})
    void unsupportedClaimsParaphrasesAndChangedNumbersAreRejected(String text) {
        rejected(List.of(new AdviceItem(canonical().actionId(), text, canonical().numbers())));
    }

    @Test
    void unknownIneligibleAndDuplicateActionsAreRejected() {
        rejected(List.of(new AdviceItem("UNKNOWN", canonical().text(), canonical().numbers())));
        rejected(List.of(new AdviceItem("SAVE_WITHIN_AVAILABLE_HEADROOM", canonical().text(), canonical().numbers())));
        rejected(List.of(canonical(), canonical()));
    }

    @Test
    void numbersMustMatchExactFactAndPrecision() {
        for (String amount : List.of("5000.01", "4999.99", "5000.001", "5000.000000001")) {
            var n = canonical().numbers().get(0);
            rejected(List.of(new AdviceItem(canonical().actionId(), canonical().text(),
                    List.of(new NumberFact(n.factKey(), new BigDecimal(amount), n.unit(), n.period())))));
        }
        rejected(List.of(new AdviceItem(canonical().actionId(), canonical().text(), List.of())));
        var n = canonical().numbers().get(0);
        rejected(List.of(new AdviceItem(canonical().actionId(), canonical().text(), List.of(
                new NumberFact(FactKey.UNMARKED_LOAN_BILLS, n.value(), n.unit(), n.period())))));
    }

    @Test
    void wrongUnitsAndMonthsAreRejectedEvenWhenTheNumberMatches() {
        var n = canonical().numbers().get(0);
        for (NumberFact wrong : List.of(new NumberFact(n.factKey(), n.value(), "USD", n.period()),
                new NumberFact(n.factKey(), n.value(), "LKR", "2026-10"))) {
            var verdict = verifier.verify(json(List.of(new AdviceItem(canonical().actionId(), canonical().text(), List.of(wrong)))), AdviceTestFixtures.facts());
            assertThat(verdict.reason()).isEqualTo(AdviceVerifier.Reason.WRONG_UNIT_OR_PERIOD);
        }
    }

    @Test
    void largeExactAggregateNeverPassesThroughFloatingPointRounding() {
        FactSheet f = new FactSheet("2026-11", Map.of(FactKey.SPENDING_DEFICIT, new BigDecimal("999999999999999999.99")), List.of());
        var rules = RuleAdviceEngine.generate(f);
        assertThat(verifier.verify(json(rules), f).verified()).isTrue();
        String changed = json(rules).replace("999999999999999999.99", "999999999999999999.98");
        assertThat(verifier.verify(changed, f).verified()).isFalse();
    }
}
