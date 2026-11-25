package lk.finora.api.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.GeminiClient;
import lk.finora.api.advice.ai.GroqClient;
import lk.finora.api.advice.ai.LlmClient;
import lk.finora.api.advice.rules.RuleAdviceService;
import lk.finora.api.advice.verify.VerificationSink;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Explicit offline wiring. No production profile or provider secret can activate this configuration. */
@Import(StructuredFocusIntegrationTest.OfflineConfiguration.class)
class StructuredFocusIntegrationTest extends IntegrationTest {
    private static final String MONTH = "2026-10";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WireFixture wire;

    @Test
    void bothRealRequestBuildersDeliverTheExactOwnerScopedCanonicalAmounts() throws Exception {
        for (String provider : List.of("GROQ", "GEMINI")) {
            var seed = seed();
            wire.reset(provider, 200, "{\"focus\":\"SPENDING_PATTERN\"}");
            JsonNode response = delivery(seed.token(), seed.goal());
            assertThat(response.path("source").asString()).isEqualTo("AI");
            assertThat(response.path("verified").asBoolean()).isTrue();
            assertThat(response.path("items").get(0).path("actionId").asString()).isEqualTo("SPENDING_PATTERN");
            String body = response.path("items").toString();
            assertThat(body).contains("FOCUS_GOAL_REMAINING", "150000.00", "50000.00")
                    .doesNotContain("Private target marker", "Private wallet marker");
            assertThat(wire.sends.get()).isEqualTo(1);
            var envelope = jsonMapper.readTree(wire.request.get());
            String user = provider.equals("GROQ")
                    ? envelope.path("messages").get(1).path("content").asString()
                    : envelope.path("contents").get(0).path("parts").get(0).path("text").asString();
            var choices = jsonMapper.readTree(user);
            assertThat(choices.propertyNames()).containsExactly("eligibleFocus");
            assertThat(choices.path("eligibleFocus").toString()).contains("GOAL_PLAN", "SALARY_PLAN");
            assertThat(wire.request.get()).doesNotContain("Private target marker", "Private wallet marker",
                    "private-merchant-marker", "150000", "50000");

            String foreign = tokenFor(createUser(Role.USER));
            mockMvc.perform(get("/api/v1/advice").header("Authorization", "Bearer " + foreign)
                    .param("month", MONTH).param("mode", "V1").param("goalId", Long.toString(seed.goal())))
                    .andExpect(status().isNotFound());
            assertThat(wire.sends.get()).isEqualTo(1);
        }
    }

    @Test
    void malformedChoiceAndTerminalFailureBothFallBackWithoutASecondSend() throws Exception {
        for (int outcome : List.of(200, 503)) {
            var seed = seed();
            wire.reset("GEMINI", outcome, "{\"focus\":\"GOAL_PLAN\",\"text\":\"unsupported claim\"}");
            var response = delivery(seed.token(), seed.goal());
            assertThat(response.path("source").asString()).isEqualTo("RULE");
            assertThat(response.path("verified").asBoolean()).isFalse();
            assertThat(response.path("items").get(0).path("actionId").asString()).isEqualTo("GOAL_PLAN");
            assertThat(response.path("items").toString()).contains("COACHING_WARNINGS", "150000.00")
                    .doesNotContain("unsupported claim");
            assertThat(wire.sends.get()).isEqualTo(1);
        }
    }

    @Test
    void noRecordsProducesNoSend() throws Exception {
        wire.reset("GROQ", 200, "{\"focus\":\"GOAL_PLAN\"}");
        var response = delivery(tokenFor(createUser(Role.USER)), null);
        assertThat(response.path("items").size()).isZero();
        assertThat(wire.sends.get()).isZero();
    }

    private JsonNode delivery(String token, Long goal) throws Exception {
        var request = get("/api/v1/advice").header("Authorization", "Bearer " + token)
                .param("month", MONTH).param("mode", "V1");
        if (goal != null) request.param("goalId", goal.toString());
        return jsonMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private record Seed(String token, long goal) {}

    private Seed seed() {
        var owner = createUser(Role.USER);
        long user = owner.getId();
        jdbc.update("INSERT INTO accounts(user_id,name,type) VALUES (?,'Private wallet marker','CASH')", user);
        long account = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=?", Long.class, user);
        long income = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name='Salary'", Long.class);
        long expense = jdbc.queryForObject("SELECT id FROM categories WHERE user_id IS NULL AND name='Rent'", Long.class);
        jdbc.update("INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date,merchant) "
                + "VALUES (?,?,?,'INCOME',100000,'2026-10-01',NULL),(?,?,?,'EXPENSE',80000,'2026-10-02','private-merchant-marker')",
                user, account, income, user, account, expense);
        jdbc.update("INSERT INTO goals(user_id,name,target_amount,target_date,status) "
                + "VALUES (?,'Private target marker',180000,'2027-01-31','ACTIVE')", user);
        long goal = jdbc.queryForObject("SELECT id FROM goals WHERE user_id=?", Long.class, user);
        jdbc.update("INSERT INTO goal_contributions(goal_id,amount,contrib_date) VALUES (?,30000,'2026-10-03')", goal);
        return new Seed(tokenFor(owner), goal);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OfflineConfiguration {
        @Bean(destroyMethod = "close") WireFixture wireFixture(JsonMapper mapper) throws IOException, InterruptedException {
            return new WireFixture(mapper);
        }

        @Bean @Primary AdvicePipelineService offlinePipeline(RuleAdviceService rules, WireFixture wire,
                VerificationSink outcomes) {
            return AdvicePipelineService.forStructuredEvaluation(rules, wire, outcomes);
        }
    }

    static final class WireFixture implements LlmClient, AutoCloseable {
        private final HttpServer server;
        private final GroqClient groq;
        private final GeminiClient gemini;
        private final AtomicInteger sends = new AtomicInteger();
        private final AtomicReference<String> request = new AtomicReference<>();
        private volatile String provider = "GROQ";
        private volatile int status;
        private volatile String reply;

        WireFixture(JsonMapper mapper) throws IOException, InterruptedException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    sends.incrementAndGet();
                    request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    byte[] response = mapper.writeValueAsString("GROQ".equals(provider)
                            ? Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", reply))))
                            : Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content",
                                    Map.of("parts", List.of(Map.of("text", reply))))))).getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, response.length);
                    exchange.getResponseBody().write(response);
                }
            });
            server.start();
            HttpClient loopback = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            HttpClient isolated = mock(HttpClient.class);
            when(isolated.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
            when(isolated.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenAnswer(call -> {
                        HttpRequest original = call.getArgument(0);
                        assertThat(original.uri().getScheme()).isEqualTo("https");
                        assertThat(original.uri().getHost()).isIn("api.groq.com", "generativelanguage.googleapis.com");
                        var target = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
                        return loopback.send(HttpRequest.newBuilder(target).timeout(original.timeout().orElseThrow())
                                        .POST(original.bodyPublisher().orElseThrow()).build(),
                                call.getArgument(1));
                    });
            groq = new GroqClient(isolated, mapper, "synthetic-fixture-only", "fixture");
            gemini = new GeminiClient(isolated, mapper, "synthetic-fixture-only", "fixture");
        }

        void reset(String name, int outcome, String response) {
            provider = name;
            status = outcome;
            reply = response;
            sends.set(0);
            request.set(null);
        }

        @Override public String provider() { return provider; }
        @Override public Result generate(lk.finora.api.advice.ai.FactSheet facts, List<AdviceItem> advice) {
            throw new AssertionError("Structured qualification must never invoke the free-wording route");
        }

        @Override public Result chooseCoachingFocus(List<String> eligible) {
            LlmClient client = "GROQ".equals(provider) ? groq : gemini;
            return client.chooseCoachingFocus(eligible);
        }

        @Override public void close() { server.stop(0); }
    }
}
