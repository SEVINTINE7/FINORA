package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.ai.GroqClient;
import lk.finora.api.advice.ai.GeminiClient;
import lk.finora.api.advice.ai.AiPrompt;
import static org.junit.jupiter.api.Assertions.*;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

/** Explicit T54 experiment. IT suffix excludes ordinary Surefire discovery. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class T54ProviderEvaluationIT extends IntegrationTest {
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
                jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND kind='EXPENSE' AND semantic_code='SAVINGS'", Long.class));
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

    private record Fixture(String name, Session user, FactSheet facts, List<AdviceItem> eligible) {}
    private static final List<String> ROWS = List.of(
        "worker,50000,30000,15000,1000,7500,79,45,4,2.50,1500,BUILD_EMERGENCY_BUFFER|SAVE_WITHIN_AVAILABLE_HEADROOM",
        "small-saver,40000,38000,1600,400,0,50,30.60,2,0,0,BUILD_EMERGENCY_BUFFER",
        "unemployed,0,10000,0,0,0,NULL,NULL,NULL,0,NULL,REVIEW_SPENDING_DEFICIT|BUILD_EMERGENCY_BUFFER",
        "break-even,100000,60000,40000,0,30000,49,27,0,5,0,BUILD_EMERGENCY_BUFFER",
        "cent-rounding,10000.01,5000.01,4000,499.99,1250,88,45,10,2.50,0.01,BUILD_EMERGENCY_BUFFER|SAVE_WITHIN_AVAILABLE_HEADROOM",
        "no-records,0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL");

    private List<Fixture> fixtures() throws Exception {
        var result = new ArrayList<Fixture>();
        for (String row : ROWS) {
            String[] a = row.split(",", -1);
            for (int j=0;j<a.length;j++) if ("NULL".equals(a[j])) a[j]=null;
            result.add(prepare(a[0],a[1],a[2],a[3],a[4],a[5],a[6]==null?null:Integer.valueOf(a[6]),a[7],a[8],a[9],a[10],a[11]));
        }
        assertEquals(6,result.size());
        assertEquals(5,result.stream().filter(f -> !f.eligible().isEmpty()).count());
        return List.copyOf(result);
    }
    private Fixture prepare(String name, String income, String needs,
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
        String payload = AiPrompt.user(facts, eligible, jsonMapper);
        assertThat(payload).doesNotContain("PRIVATE_JOURNEY", "999999.99", l.user().token(), "accountId", "userId", "merchant", "email");
        System.out.printf("T54_ORACLE fixture=%s status=PASS actions=%d%n", name,eligible.size());
        return new Fixture(name,l.user(),facts,eligible);
    }

    private static final class Probe implements LlmClient {
        final LlmClient delegate;
        final Fixture fixture;
        int calls;
        Result response;
        Probe(LlmClient delegate, Fixture fixture) { this.delegate=delegate; this.fixture=fixture; }
        public String provider() { return delegate.provider(); }
        public Result generate(FactSheet facts, List<AdviceItem> eligible) {
            assertEquals(0,calls,"Only one candidate per fixture/provider");
            calls++;
            assertEquals(fixture.facts(),facts);
            assertEquals(fixture.eligible(),eligible);
            response=delegate.generate(facts,eligible);
            return response;
        }
    }
    private record Outcome(boolean accepted, boolean transportFailed, int attempts) {}
    private Outcome evaluate(Fixture f, LlmClient client, String scenario, boolean live) {
        var probe = new Probe(client,f);
        var before = eventCount(f);
        principal(f.user());
        long start=System.nanoTime();
        AdvicePipelineService.Delivery delivery;
        try { delivery=pipeline(probe).forCurrentUser(MONTH); }
        finally { SecurityContextHolder.clearContext(); }
        long ms=Duration.ofNanos(System.nanoTime()-start).toMillis();
        if (f.eligible().isEmpty()) {
            assertEquals(0,probe.calls);
            assertEquals(before,eventCount(f));
            assertEquals("NO_ELIGIBLE_ACTIONS",delivery.reason());
            assertEquals("RULE",delivery.source());
            assertFalse(delivery.verified());
            assertTrue(delivery.advice().isEmpty());
            System.out.printf("T54_CONTROL provider=%s fixture=%s calls=0 status=PASS%n",client.provider(),f.name());
            return new Outcome(false,false,0);
        }
        assertEquals(1,probe.calls);
        assertNotNull(probe.response,"Provider did not return bounded metadata; stop");
        int attempts=probe.response.httpAttempts();
        if (live) assertTrue(attempts>=1 && attempts<=2,"Unexpected application HTTP attempt count");
        else assertEquals(0,attempts);
        var verdict=probe.response.success()?new AdviceVerifier().verify(probe.response.reply(),f.facts()):null;
        boolean accepted=verdict!=null && verdict.verified();
        var reason=verdict==null?AdviceVerifier.Reason.PROVIDER_FAILURE:verdict.reason();
        assertEquals(before+1,eventCount(f));
        var event=jdbc.queryForMap("SELECT provider,verified,reason FROM advice_verification_events WHERE user_id=? ORDER BY id DESC LIMIT 1",f.user().id());
        assertEquals(client.provider(),event.get("provider"));
        assertEquals(reason.name(),event.get("reason"));
        assertEquals(accepted, jdbc.queryForObject("SELECT verified FROM advice_verification_events WHERE user_id=? ORDER BY id DESC LIMIT 1",Boolean.class,f.user().id()));
        if (accepted) {
            assertEquals("AI",delivery.source()); assertTrue(delivery.verified());
            assertEquals(verdict.advice(),delivery.advice());
        } else {
            assertEquals("RULE",delivery.source()); assertFalse(delivery.verified());
            assertEquals(f.eligible(),delivery.advice());
        }
        assertEquals(accepted?"ACCEPTED":verdict==null?"PROVIDERS_FAILED":reason.name(),delivery.reason());
        System.out.printf("T54_%s provider=%s fixture=%s scenario=%s httpSendAttempts=%d adapter=%s verifier=%s %s delivered=%s fallback=%s latencyMs=%d%n",
            live?"LIVE":"DRY",client.provider(),f.name(),scenario,attempts,
            probe.response.success()?"REPLY":probe.response.failure().name(),reason.name(),
            LiveProviderAcceptanceIT.verificationEvidence(verdict),delivery.source(),accepted?"NOT_REQUIRED":"PASS",ms);
        return new Outcome(accepted,!probe.response.success(),attempts);
    }
    private int eventCount(Fixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM advice_verification_events WHERE user_id=?",Integer.class,f.user().id());
    }
    private LlmClient simulated(String provider, String scenario) {
        return new LlmClient() {
            public String provider() { return provider; }
            public Result generate(FactSheet facts,List<AdviceItem> eligible) {
                if (scenario.equals("TRANSPORT_FAILURE")) return Result.failed(Failure.TIMEOUT);
                if (scenario.equals("EXACT")) return Result.received(json(Map.of("advice",eligible)));
                var item=eligible.get(0); var n=item.numbers().get(0);
                var altered=new AdviceItem(item.actionId(),item.text(),List.of(new AdviceItem.NumberFact(n.factKey(),n.value().add(new BigDecimal("0.01")),n.unit(),n.period())));
                // Retain number cardinality so the rejection is a true value mismatch.
                var nums=new ArrayList<>(item.numbers()); nums.set(0,altered.numbers().get(0));
                return Result.received(json(Map.of("advice",List.of(new AdviceItem(item.actionId(),item.text(),nums)))));
            }
        };
    }
    @Test void dryEvaluation() throws Exception {
        assertTrue(System.getenv("GROQ_API_KEY")==null && System.getenv("GEMINI_API_KEY")==null,"Dry run requires absent provider keys");
        assertFalse("true".equals(System.getenv("FINORA_T54_APPROVED")));
        var all=fixtures(); int cases=0,controls=0;
        for (String provider:List.of("GEMINI","GROQ")) for (Fixture f:all) {
            if (f.eligible().isEmpty()) { evaluate(f,simulated(provider,"EXACT"),"CONTROL",false); controls++; continue; }
            for (String scenario:List.of("EXACT","NUMBER_MISMATCH","TRANSPORT_FAILURE")) {
                var outcome=evaluate(f,simulated(provider,scenario),scenario,false);
                assertEquals(scenario.equals("EXACT"),outcome.accepted());
                if (scenario.equals("NUMBER_MISMATCH")) assertEquals("FACT_MISMATCH",jdbc.queryForObject("SELECT reason FROM advice_verification_events WHERE user_id=? ORDER BY id DESC LIMIT 1",String.class,f.user().id()));
                cases++;
            }
        }
        assertEquals(30,cases); assertEquals(2,controls);
        System.out.println("T54_DRY_SUMMARY oracles=6 cases=30 accepted=10 fallback=20 zeroCallControls=2 liveHttpAttempts=0");
    }
    private static String required(String name) {
        String value=System.getenv(name);
        if (value==null || value.isBlank()) throw new IllegalStateException("Missing "+name);
        return value;
    }
    @Test void liveEvaluation() throws Exception {
        assertEquals("true",System.getenv("FINORA_T54_APPROVED"),"Separate recorded T54 approval required");
        assertEquals("20",System.getenv("FINORA_T54_HTTP_LIMIT"));
        assertTrue(Boolean.getBoolean("jdk.httpclient.disableRetryConnect"));
        assertFalse(Boolean.getBoolean("jdk.httpclient.enableAllMethodRetry"));
        assertEquals("openai/gpt-oss-120b",required("FINORA_LLM_GROQ_MODEL"));
        assertEquals("gemini-3.8-flash",required("FINORA_LLM_GEMINI_MODEL"));
        var all=fixtures(); // All six hand oracles must pass before any live call.
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        var clients=List.<LlmClient>of(new GeminiClient(http,jsonMapper,required("GEMINI_API_KEY"),required("FINORA_LLM_GEMINI_MODEL")),
                new GroqClient(http,jsonMapper,required("GROQ_API_KEY"),required("FINORA_LLM_GROQ_MODEL")));
        int attempted=0,accepted=0,attempts=0,skipped=0,controls=0;
        for (var client:clients) {
            boolean stopped=false; int providerCases=0;
            for (var f:all) {
                if (f.eligible().isEmpty()) { evaluate(f,client,"CONTROL",true); controls++; continue; }
                if (stopped) { skipped++; System.out.printf("T54_SKIPPED provider=%s fixture=%s reason=PROVIDER_STOP%n",client.provider(),f.name()); continue; }
                assertTrue(attempts+2<=20,"Insufficient remaining HTTP allowance");
                var outcome=evaluate(f,client,"REAL",true);
                attempted++; providerCases++; attempts+=outcome.attempts();
                if (outcome.accepted()) accepted++;
                if (outcome.transportFailed() || (client.provider().equals("GEMINI") && providerCases==1 && !outcome.accepted())) stopped=true;
            }
        }
        assertEquals(10,attempted+skipped); assertEquals(2,controls); assertTrue(attempts<=20);
        System.out.printf("T54_LIVE_SUMMARY eligible=10 attempted=%d accepted=%d fallback=%d skipped=%d httpSendAttempts=%d zeroCallControls=%d%n",attempted,accepted,attempted-accepted,skipped,attempts,controls);
        // A partial run or failed candidate remains a valid measurement, not full acceptance.
        assertEquals(10,accepted,"Full live candidate acceptance not established");
    }
}
