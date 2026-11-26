package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.CoachingInputs;
import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.ai.FactSheetFactory;
import lk.finora.api.advice.ai.LlmClient;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import lk.finora.api.advice.rules.RuleAdviceService;
import lk.finora.api.advice.verify.AdviceVerifier;
import lk.finora.api.advice.verify.AdviceVerifier.Reason;
import lk.finora.api.advice.verify.VerificationSink;
import lk.finora.api.analytics.Metric;
import lk.finora.api.analytics.MonthMetrics;
import lk.finora.api.score.ScoreCalculator;

class D58ReviewTest {
    private static final String MONTH = "2026-12";
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void roundsGoalNeedUpAcrossYearAndReportsInfeasiblePaceHonestly() {
        var goal = new CoachingInputs.Goal(d("101.00"), d("0.99"), d("0.99"), LocalDate.of(2027, 3, 31));
        var facts = facts("80.00", "64.00", "64.00", "0", List.of(), goal);
        value(facts, FactKey.FOCUS_GOAL_REMAINING, "100.01");
        value(facts, FactKey.FOCUS_GOAL_MONTHS_LEFT, "3");
        value(facts, FactKey.FOCUS_GOAL_MONTHLY_NEEDED, "33.34");
        value(facts, FactKey.FOCUS_GOAL_MONTHLY_PACE, "0.33");
        value(facts, FactKey.FOCUS_GOAL_MONTHS_AT_PACE, "304");
        value(facts, FactKey.PLAN_GOAL_TRANSFER, "16");
        value(facts, FactKey.PLAN_BUFFER, "0");
        value(facts, FactKey.FOCUS_GOAL_MONTHS_WITH_PLAN, "7");
    }

    @Test
    void wholeRupeeCutsAndCentWantsCapDoNotExceedD58Ceilings() {
        var bucket = new CoachingInputs.Bucket("Shopping", d("3.00"), d("1.00"));
        var facts = facts("2.02", "3.00", "0", "3.00", List.of(bucket), null);
        value(facts, FactKey.TOP_WANT_1_CUT, "1.00");
        value(facts, FactKey.PLAN_WANTS_CAP, "0.80");
        value(facts, FactKey.PLAN_WEEKLY_WANTS, "0.00");
    }

    @Test
    void shortfallAndAbsentIncomeDoNotProduceAnAffordableCompletion() {
        var goal = new CoachingInputs.Goal(d("100"), d("0"), d("0"), LocalDate.of(2027, 1, 31));
        var shortfall = facts("20", "25", "25", "0", List.of(), goal);
        value(shortfall, FactKey.PLAN_GOAL_TRANSFER, "0");
        assertThat(shortfall.facts()).doesNotContainKeys(FactKey.FOCUS_GOAL_MONTHS_AT_PACE,
                FactKey.FOCUS_GOAL_MONTHS_WITH_PLAN);
        assertThat(facts("0", "0", "0", "0", List.of(), goal).facts())
                .doesNotContainKeys(FactKey.PLAN_NEEDS, FactKey.WANTS_SHARE, FactKey.NEEDS_SHARE);
    }

    @Test
    void completedAndOverdueGoalsDoNotGetNewDeadlineClaims() {
        var completed = new CoachingInputs.Goal(d("100"), d("100"), d("25"), END);
        assertThat(facts("0", "0", "0", "0", List.of(), completed).facts())
                .doesNotContainKey(FactKey.FOCUS_GOAL_REMAINING);
        var overdue = new CoachingInputs.Goal(d("100"), d("25"), d("15"), END.minusMonths(1));
        var result = facts("0", "0", "0", "0", List.of(), overdue);
        value(result, FactKey.FOCUS_GOAL_REMAINING, "75");
        value(result, FactKey.FOCUS_GOAL_MONTHS_AT_PACE, "15");
        assertThat(result.facts()).doesNotContainKeys(FactKey.FOCUS_GOAL_MONTHS_LEFT,
                FactKey.FOCUS_GOAL_MONTHLY_NEEDED);
        assertThat(RuleAdviceEngine.coach(result).get(0).text()).doesNotContain("by the target date");
    }

    @Test
    void checksNumericSignAndExistingProhibitionsAcrossTypography() {
        var f = new FactSheet(MONTH, Map.of(FactKey.FOCUS_GOAL_REMAINING, d("1200"),
                FactKey.FOCUS_GOAL_SAVED, d("0"), FactKey.FOCUS_GOAL_TARGET, d("1200")), List.of());
        var item = new AdviceItem.NumberFact(FactKey.FOCUS_GOAL_REMAINING, d("1200"), "LKR", MONTH);
        assertThat(verify(f, "LKR 1,200.00 remains.", item).verified()).isTrue();
        assertThat(verify(f, "LKR -1,200.00 remains.", item).reason()).isEqualTo(Reason.TEXT_NUMBER_MISMATCH);
        assertThat(verify(f, "LKR \u22121,200.00 remains.", item).reason()).isEqualTo(Reason.TEXT_NUMBER_MISMATCH);
        for (String text : List.of("A GUARANTEE. ", "A risk\u2011free offer. ", "Use a credit\u00a0 card. ",
                "Reduce your rent. ", "Visit HTTPS://example.invalid. ")) {
            var verdict = verify(f, text + "LKR 1,200.00 remains.", item);
            assertThat(verdict.reason()).isEqualTo(Reason.UNSAFE_TEXT);
            assertThat(verdict.advice()).isEmpty();
        }
    }

    @Test
    void unsafeCoachingDiscardsTheWholeProviderReplyAndDoesNotCallBackup() {
        var goal = new CoachingInputs.Goal(d("100"), d("20"), d("15"), END.plusMonths(2));
        var facts = facts("100", "40", "40", "0", List.of(), goal);
        var canonical = RuleAdviceEngine.generate(facts);
        var proposed = new ArrayList<>(canonical);
        var salary = proposed.get(1);
        assertThat(salary.actionId()).isEqualTo("SALARY_PLAN");
        proposed.set(1, new AdviceItem(salary.actionId(), "Guaranteed returns. " + salary.text(), salary.numbers()));
        var rules = mock(RuleAdviceService.class);
        var primary = mock(LlmClient.class);
        var backup = mock(LlmClient.class);
        var sink = mock(VerificationSink.class);
        when(rules.facts(42L, MONTH, 7L)).thenReturn(facts);
        when(primary.provider()).thenReturn("GROQ");
        when(backup.provider()).thenReturn("GEMINI");
        String reply = AdviceTestFixtures.JSON.writeValueAsString(Map.of("advice", proposed));
        when(primary.generate(facts, canonical))
                .thenReturn(LlmClient.Result.received(reply));
        var pipeline = new AdvicePipelineService(rules, List.of(primary, backup), new AdviceVerifier(), sink);
        var prior = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(42L, null,
                AuthorityUtils.createAuthorityList("ROLE_USER")));
        SecurityContextHolder.setContext(context);
        try {
            assertThat(pipeline.forCurrentUser(MONTH, 7L))
                    .isEqualTo(new AdvicePipelineService.Delivery("RULE", false, "UNSAFE_TEXT", canonical));
            org.mockito.Mockito.verify(sink).record(42L, MONTH, "GROQ", false, Reason.UNSAFE_TEXT);
            org.mockito.Mockito.verify(backup, never())
                    .generate(any(), any());
        } finally {
            SecurityContextHolder.setContext(prior);
        }
    }

    private static AdviceVerifier.Verdict verify(FactSheet facts, String text, AdviceItem.NumberFact fact) {
        var items = new ArrayList<>(RuleAdviceEngine.generate(facts));
        items.set(0, new AdviceItem("GOAL_PLAN", text, List.of(fact)));
        return new AdviceVerifier().verify(AdviceTestFixtures.JSON.writeValueAsString(Map.of("advice", items)), facts);
    }

    private static FactSheet facts(String income, String spending, String needs, String wants,
            List<CoachingInputs.Bucket> buckets, CoachingInputs.Goal goal) {
        var i = d(income);
        var e = d(spending);
        var n = d(needs);
        var zero = metric(BigDecimal.ZERO);
        var metrics = new MonthMetrics(MONTH, metric(i), metric(e), metric(n), metric(d(wants)),
                zero, zero, metric(i.subtract(e)), zero, zero, zero, zero, null, null, null);
        var score = ScoreCalculator.calculate(MONTH, new ScoreCalculator.Inputs(i, e, d("0"), n,
                null, null, null, null, null, null));
        return FactSheetFactory.from(metrics, score,
                new CoachingInputs(buckets, List.of(), d("0"), d("0"), d("0"), goal));
    }

    private static Metric metric(BigDecimal amount) {
        return new Metric(amount, YearMonth.parse(MONTH).atDay(1), END, 1);
    }

    private static BigDecimal d(String amount) { return new BigDecimal(amount).setScale(2); }

    private static void value(FactSheet facts, FactKey key, String expected) {
        assertThat(facts.facts().get(key)).as(key.name()).isEqualByComparingTo(expected);
    }
}
