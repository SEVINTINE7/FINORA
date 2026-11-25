package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import lk.finora.api.advice.ai.FactKey;
import lk.finora.api.advice.ai.LlmClient;
import lk.finora.api.advice.rules.RuleAdviceService;
import lk.finora.api.advice.verify.AdviceVerifier;
import lk.finora.api.advice.verify.VerificationSink;
import lk.finora.api.analytics.MetricsService;
import lk.finora.api.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/** T54: hand-checked financial expectations across real HTTP records and mocked advice services. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class FinancialJourneyTest extends IntegrationTest {
    private static final String MONTH = "2026-11";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MetricsService metrics;
    @Autowired private RuleAdviceService rules;
    @Autowired private VerificationSink sink;
    private record Session(long id, String token) {}
    private record Ledger(Session user, long account, long needs, long wants) {}
    @AfterEach void clearPrincipal() { SecurityContextHolder.clearContext(); }

    private JsonNode send(Session user, String method, String path, Object body, int expected) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON);
        if (user != null) builder.header("Authorization", "Bearer " + user.token());
        if (body != null) builder.content(json(body));
        var response = mockMvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as("%s %s", method, path).isEqualTo(expected);
        return response.getContentAsString().isEmpty() ? null : jsonMapper.readTree(response.getContentAsString());
    }

    private Session register() throws Exception {
        String email = "synthetic-" + System.nanoTime() + "@example.com";
        var registered = send(null, "POST", "/api/v1/auth/register", Map.of("email", email,
                "password", "Synthetic123", "fullName", "PRIVATE_JOURNEY_PERSON", "language", "EN",
                "over18", true, "consentVersion", "1.0"), 201);
        var login = send(null, "POST", "/api/v1/auth/login", Map.of("email", email, "password", "Synthetic123"), 200);
        return new Session(registered.get("userId").asLong(), login.get("accessToken").asString());
    }

    private Ledger ledger() throws Exception {
        Session user = register();
        long account = send(user, "POST", "/api/v1/accounts", Map.of("name", "PRIVATE_JOURNEY_ACCOUNT",
                "type", "CASH", "openingBalance", "999999.99"), 201).get("id").asLong();
        long needs = category(user, "NEED");
        return new Ledger(user, account, needs, category(user, "WANT"));
    }

    private long category(Session user, String classification) throws Exception {
        return send(user, "POST", "/api/v1/categories", Map.of("name", "PRIVATE_JOURNEY_" + classification,
                "kind", "EXPENSE", "needOrWant", classification), 201).get("id").asLong();
    }

    private long transaction(Ledger l, String type, String amount, Long category) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("accountId", l.account(), "type", type,
                "amount", amount, "txnDate", "2026-11-01", "merchant", "PRIVATE_JOURNEY_MERCHANT"));
        if (category != null) body.put("categoryId", category);
        return send(l.user(), "POST", "/api/v1/transactions", body, 201).get("id").asLong();
    }

    private void populate(Ledger l, String income, String needs, String wants, String savings) throws Exception {
        if (d(income).signum() > 0) transaction(l, "INCOME", income, null);
        if (d(needs).signum() > 0) transaction(l, "EXPENSE", needs, l.needs());
        if (d(wants).signum() > 0) transaction(l, "EXPENSE", wants, l.wants());
        if (d(savings).signum() > 0) transaction(l, "EXPENSE", savings,
                jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND kind='EXPENSE' AND name='Savings'", Long.class));
    }

    private Map<String, Object> declarations(String reserve, boolean noDebt, boolean noGoals) {
        Map<String, Object> body = new HashMap<>(Map.of("noApplicableDebt", noDebt, "noApplicableGoals", noGoals));
        if (reserve != null) { body.put("emergencyReserve", reserve); body.put("reserveAsOf", "2026-11-01"); }
        return body;
    }

    private void principal(Session user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.id(), null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }
    private AdvicePipelineService pipeline(LlmClient provider) {
        return new AdvicePipelineService(rules, List.of(provider), new AdviceVerifier(), sink);
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private void money(JsonNode value, String expected) {
        if (expected == null) assertThat(value.isNull()).isTrue();
        else assertThat(value.decimalValue()).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "NULL", value = {
        // name,I,N,W,S,B,total,spending,savings,emergency,affordable,actions
        "worker,50000,30000,15000,1000,7500,79,45,4,2.50,1500,BUILD_EMERGENCY_BUFFER|SAVE_WITHIN_AVAILABLE_HEADROOM",
        "small-saver,40000,38000,1600,400,0,50,30.60,2,0,0,BUILD_EMERGENCY_BUFFER",
        "unemployed,0,10000,0,0,0,NULL,NULL,NULL,0,NULL,REVIEW_SPENDING_DEFICIT|BUILD_EMERGENCY_BUFFER",
        "break-even,100000,60000,40000,0,30000,49,27,0,5,0,BUILD_EMERGENCY_BUFFER",
        "cent-rounding,10000.01,5000.01,4000,499.99,1250,88,45,10,2.50,0.01,BUILD_EMERGENCY_BUFFER|SAVE_WITHIN_AVAILABLE_HEADROOM",
        "no-records,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL"
    })
    void handCheckedUserMonthFlowsThroughRecordsScoreFactsAndFallback(String name, String income, String needs,
            String wants, String savings, String reserve, Integer total, String spendingPoints, String savingsPoints,
            String emergencyPoints, String affordable, String actions) throws Exception {
        Ledger l = ledger(); populate(l, income, needs, wants, savings);
        send(l.user(), "PUT", "/api/v1/score-inputs/" + MONTH, declarations(reserve, true, true), 200);
        var dashboard = send(l.user(), "GET", "/api/v1/dashboard?month=" + MONTH, null, 200);
        BigDecimal expense = d(needs).add(d(wants)).add(d(savings));
        money(dashboard.get("income"), income); money(dashboard.get("expense"), expense.toPlainString());
        money(dashboard.get("net"), d(income).subtract(expense).toPlainString());
        BigDecimal categorySum = BigDecimal.ZERO;
        for (var category : dashboard.get("byCategory")) categorySum = categorySum.add(category.get("total").decimalValue());
        assertThat(categorySum).isEqualByComparingTo(expense);
        var m = metrics.forMonth(l.user().id(), YearMonth.parse(MONTH));
        assertThat(m.needs().amount()).isEqualByComparingTo(needs);
        assertThat(m.wants().amount()).isEqualByComparingTo(wants);
        assertThat(m.unclassified().amount()).isEqualByComparingTo(savings);
        var score = send(l.user(), "GET", "/api/v1/score?month=" + MONTH, null, 200);
        if (total == null) assertThat(score.get("total").isNull()).isTrue();
        else assertThat(score.get("total").asInt()).isEqualTo(total);
        money(score.get("parts").get(0).get("points"), spendingPoints);
        money(score.get("parts").get(3).get("points"), savingsPoints);
        money(score.get("parts").get(4).get("points"), emergencyPoints);
        var facts = rules.facts(l.user().id(), MONTH);
        if (affordable == null) assertThat(facts.facts()).doesNotContainKey(FactKey.AFFORDABLE_SAVINGS);
        else assertThat(facts.facts().get(FactKey.AFFORDABLE_SAVINGS)).isEqualByComparingTo(affordable);
        var eligible = rules.forMonth(l.user().id(), MONTH);
        assertThat(eligible.stream().map(a -> a.actionId()).toList())
                .containsExactlyElementsOf(actions == null ? List.of() : List.of(actions.split("\\|")));
        for (var item : eligible) for (var number : item.numbers()) {
            assertThat(number.value()).isEqualByComparingTo(facts.facts().get(number.factKey()));
            assertThat(number.unit()).isEqualTo("LKR"); assertThat(number.period()).isEqualTo(MONTH);
        }
        LlmClient provider = mock(LlmClient.class); when(provider.provider()).thenReturn("GROQ");
        when(provider.generate(any(), any())).thenReturn(LlmClient.Result.failed(LlmClient.Failure.TIMEOUT));
        principal(l.user());
        var delivery = pipeline(provider).forCurrentUser(MONTH);
        assertThat(delivery.advice()).isEqualTo(eligible);
        assertThat(delivery.reason()).isEqualTo(eligible.isEmpty() ? "NO_ELIGIBLE_ACTIONS" : "PROVIDERS_FAILED");
        if (eligible.isEmpty()) verify(provider, never()).generate(any(), any());
        assertThat(json(delivery)).doesNotContain("PRIVATE_JOURNEY", "999999.99", l.user().token());
    }

    @Test
    void budgetCentBoundaryUpdatesAnalysisAndRejectsUngroundedAdvice() throws Exception {
        Ledger l = ledger(); populate(l, "100000", "60000", "40000", "0");
        long budget = send(l.user(), "POST", "/api/v1/budgets", Map.of("categoryId", l.wants(), "month", MONTH,
                "limitAmount", "40000"), 201).get("id").asLong();
        assertThat(send(l.user(), "GET", "/api/v1/budgets/" + budget, null, 200).get("state").asString()).isEqualTo("NEAR");
        var before = rules.forMonth(l.user().id(), MONTH);
        assertThat(before).isEmpty(); // Debt/reserve unknown: no allocation advice; wants exactly 40%.
        for (var insight : send(l.user(), "GET", "/api/v1/insights?month=" + MONTH, null, 200))
            assertThat(insight.get("type").asString()).isNotEqualTo("HIGH_WANTS");
        long extra = transaction(l, "EXPENSE", "0.01", l.wants());
        var over = send(l.user(), "GET", "/api/v1/budgets/" + budget, null, 200);
        money(over.get("used"), "40000.01"); money(over.get("remaining"), "-0.01");
        assertThat(over.get("state").asString()).isEqualTo("OVER");
        boolean highWants = false;
        for (var insight : send(l.user(), "GET", "/api/v1/insights?month=" + MONTH, null, 200))
            if (insight.get("type").asString().equals("HIGH_WANTS")) highWants = true;
        assertThat(highWants).isTrue();
        var eligible = rules.forMonth(l.user().id(), MONTH);
        assertThat(eligible).extracting(a -> a.actionId()).containsExactly("REVIEW_SPENDING_DEFICIT", "REVIEW_HIGH_WANTS");
        LlmClient provider = mock(LlmClient.class); when(provider.provider()).thenReturn("GROQ");
        when(provider.generate(any(), any())).thenReturn(LlmClient.Result.received(json(eligible)
                .replace("Review optional spending.", "Guaranteed wealth from cutting essential food.")));
        principal(l.user());
        var rejected = pipeline(provider).forCurrentUser(MONTH);
        assertThat(rejected.verified()).isFalse(); assertThat(rejected.reason()).isEqualTo("UNSUPPORTED_CLAIM");
        assertThat(rejected.advice()).isEqualTo(eligible);
        send(l.user(), "DELETE", "/api/v1/transactions/" + extra, null, 204);
        assertThat(rules.forMonth(l.user().id(), MONTH)).isEmpty();
        for (var insight : send(l.user(), "GET", "/api/v1/insights?month=" + MONTH, null, 200))
            assertThat(insight.get("type").asString()).isNotEqualTo("HIGH_WANTS");
        send(l.user(), "POST", "/api/v1/transactions", Map.of("accountId", l.account(), "type", "EXPENSE",
                "amount", "-1", "txnDate", "2026-11-01"), 400);
        money(send(l.user(), "GET", "/api/v1/dashboard?month=" + MONTH, null, 200).get("expense"), "100000");
    }

    @Test
    void billAndGoalUpdatesChangeScoreWithoutManufacturingExpenseOrLeakingToOtherUser() throws Exception {
        Ledger l = ledger(); populate(l, "100000", "60000", "30000", "5000");
        long loanCategory = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name='Loan payment'", Long.class);
        long bill = send(l.user(), "POST", "/api/v1/bills", Map.of("name", "PRIVATE_JOURNEY_LOAN", "amount", "15000",
                "dueDay", 15, "categoryId", loanCategory), 201).get("id").asLong();
        long goal = send(l.user(), "POST", "/api/v1/goals", Map.of("name", "PRIVATE_JOURNEY_GOAL", "targetAmount", "100000"), 201).get("id").asLong();
        // Pin fixture metadata so the historical month stays reproducible after November 2026.
        // These are only this test user's synthetic plans; financial values still come from HTTP.
        assertThat(jdbc.update("UPDATE bills SET created_at='2026-11-01 00:00:00' WHERE id=? AND user_id=?",
                bill, l.user().id())).isEqualTo(1);
        assertThat(jdbc.update("UPDATE goals SET created_at='2026-11-01 00:00:00' WHERE id=? AND user_id=?",
                goal, l.user().id())).isEqualTo(1);
        send(l.user(), "POST", "/api/v1/goals/" + goal + "/contributions", Map.of("amount", "25000", "date", "2026-11-01"), 201);
        send(l.user(), "PUT", "/api/v1/score-inputs/" + MONTH, declarations("30000", false, false), 200);
        var score = send(l.user(), "GET", "/api/v1/score?month=" + MONTH, null, 200);
        assertThat(score.get("total").asInt()).isEqualTo(64);
        assertThat(score.get("parts").get(1).get("reasonKey").asString()).isEqualTo("DEBT_PAID_MARKER_PROXY");
        send(l.user(), "POST", "/api/v1/bills/" + bill + "/paid?month=" + MONTH, null, 204);
        send(l.user(), "POST", "/api/v1/goals/" + goal + "/contributions", Map.of("amount", "25000", "date", "2026-11-01"), 201);
        assertThat(send(l.user(), "GET", "/api/v1/score?month=" + MONTH, null, 200).get("total").asInt()).isEqualTo(88);
        money(send(l.user(), "GET", "/api/v1/dashboard?month=" + MONTH, null, 200).get("expense"), "95000");
        send(l.user(), "PUT", "/api/v1/score-inputs/" + MONTH, declarations("30000", true, true), 400);
        assertThat(send(l.user(), "GET", "/api/v1/score?month=" + MONTH, null, 200).get("total").asInt()).isEqualTo(88);
        LlmClient provider = mock(LlmClient.class); when(provider.provider()).thenReturn("GROQ");
        when(provider.generate(any(), any())).thenAnswer(call -> LlmClient.Result.received(json(call.getArgument(1))));
        principal(l.user()); var verified = pipeline(provider).forCurrentUser(MONTH);
        assertThat(verified.verified()).isTrue(); assertThat(verified.advice()).isEqualTo(rules.forMonth(l.user().id(), MONTH));
        Session other = register();
        money(send(other, "GET", "/api/v1/dashboard?month=" + MONTH, null, 200).get("expense"), "0");
        assertThat(send(other, "GET", "/api/v1/insights?month=" + MONTH, null, 200).isEmpty()).isTrue();
        org.mockito.Mockito.clearInvocations(provider); principal(other);
        var empty = pipeline(provider).forCurrentUser(MONTH);
        assertThat(empty.reason()).isEqualTo("NO_ELIGIBLE_ACTIONS"); assertThat(empty.advice()).isEmpty();
        verify(provider, never()).generate(any(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM advice_verification_events WHERE user_id=?", Integer.class, other.id())).isZero();
        assertThat(json(empty)).doesNotContain("PRIVATE_JOURNEY", "15000", "50000");
    }
}
