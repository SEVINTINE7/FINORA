package lk.finora.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import lk.finora.api.ApiApplication;
import lk.finora.api.auth.AuthService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PersistentEvaluationDatasetTest {
    private static final JsonMapper JSON = new JsonMapper();
    private static final String WORKER = "finora-t55-perf-worker@example.invalid";
    private static final String IRREGULAR = "finora-t55-perf-irregular@example.invalid";

    @Test
    void refusesWrongEndpointCatalogAndAutoCommit() throws Exception {
        var target = new PersistentEvaluationDatasetLoader.Target(3317, UUID.randomUUID(), "/unused",
                "finora_t55_loader@127.0.0.1");
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn("finora_t55_perf");
        for (String badUrl : new String[] {"jdbc:mysql://127.0.0.1:3307/finora_test",
                "jdbc:mysql://192.0.2.1:3317/finora_t55_perf", "jdbc:mysql://127.0.0.1:3318/finora_t55_perf",
                target.url() + "?allowMultiQueries=true"}) {
            when(metadata.getURL()).thenReturn(badUrl);
            assertThatThrownBy(() -> PersistentEvaluationDatasetLoader.verifyConnection(connection, target))
                    .isInstanceOf(IllegalStateException.class);
        }
        when(metadata.getURL()).thenReturn(target.url());
        when(connection.getCatalog()).thenReturn("finora_test");
        assertThatThrownBy(() -> PersistentEvaluationDatasetLoader.verifyConnection(connection, target))
                .isInstanceOf(IllegalStateException.class);
        when(connection.getCatalog()).thenReturn("finora_t55_perf");
        when(connection.getAutoCommit()).thenReturn(true);
        assertThatThrownBy(() -> PersistentEvaluationDatasetLoader.verifyConnection(connection, target))
                .isInstanceOf(IllegalStateException.class);
        when(connection.getAutoCommit()).thenReturn(false);
        PersistentEvaluationDatasetLoader.verifyConnection(connection, target);
    }

    @Test
    void refusesMissingSharedReference() {
        var jdbc = mock(JdbcTemplate.class);
        String category = "SELECT id FROM categories WHERE user_id IS NULL AND active=TRUE AND kind=? AND name=?";
        when(jdbc.queryForObject(category, Long.class, "INCOME", "Salary"))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        assertThatThrownBy(() -> new EvaluationDatasetSeeder(jdbc).resolveCategories())
                .isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
        org.mockito.Mockito.verify(jdbc).queryForObject(category, Long.class, "INCOME", "Salary");
        org.mockito.Mockito.verifyNoMoreInteractions(jdbc);
    }

    @Test
    @EnabledIfSystemProperty(named = "finora.t55.persist", matches = "true")
    void registerLoadAndVerify() throws Exception {
        var target = new PersistentEvaluationDatasetLoader.Target(
                Integer.parseInt(required("FINORA_T55_DB_PORT")),
                UUID.fromString(required("FINORA_T55_DB_UUID")),
                required("FINORA_T55_DB_DATADIR"), "finora_t55_loader@127.0.0.1");
        String appPassword = required("FINORA_T55_API_DB_PASSWORD");
        String jwt = required("FINORA_T55_JWT_SECRET");
        String workerPassword = required("FINORA_T55_WORKER_PASSWORD");
        String irregularPassword = required("FINORA_T55_IRREGULAR_PASSWORD");
        var source = new DriverManagerDataSource(target.url(), "finora_t55_loader",
                required("FINORA_T55_LOADER_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        var loader = new PersistentEvaluationDatasetLoader(source);
        var unregistered = new PersistentEvaluationDatasetLoader.Owner(1, WORKER);
        var another = new PersistentEvaluationDatasetLoader.Owner(2, IRREGULAR);
        var wrongServer = new PersistentEvaluationDatasetLoader.Target(target.port(), UUID.randomUUID(),
                target.dataDirectory(), target.account());
        assertThatThrownBy(() -> loader.preflight(wrongServer))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("MySQL instance");
        loader.preflight(target);
        ProxySelector previousProxy = ProxySelector.getDefault();
        ProxySelector.setDefault(new LoopbackOnlyProxySelector());
        var properties = new MapPropertySource("t55-evaluation", Map.of(
                "server.address", "127.0.0.1", "server.port", "0",
                "spring.datasource.url", target.url(), "spring.datasource.username", "finora_t55_api",
                "spring.datasource.password", appPassword, "finora.jwt.secret", jwt,
                "finora.llm.enabled", "false", "finora.llm.groq-api-key", "", "finora.llm.gemini-api-key", "",
                "finora.firebase.credentials", ""));
        try (var context = new SpringApplicationBuilder(ApiApplication.class)
                .initializers(app -> app.getEnvironment().getPropertySources().addFirst(properties)).run()) {
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            var client = HttpClient.newHttpClient();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class) == 0) {
                assertThatThrownBy(() -> loader.load(target, unregistered, another))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unexpected identities");
            }
            var worker = registerOrVerify(client, port, jdbc, WORKER, workerPassword);
            var irregular = registerOrVerify(client, port, jdbc, IRREGULAR, irregularPassword);
            var mismatched = new PersistentEvaluationDatasetLoader.Owner(worker.id(), "finora-t55-other@example.invalid");
            assertThatThrownBy(() -> loader.load(target, mismatched, irregular))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("unconsented evaluation owner");
            assertThatThrownBy(() -> loader.load(target, worker, irregular, () -> jdbc.update("""
                    INSERT INTO transactions(user_id,account_id,category_id,type,amount,txn_date,source)
                    SELECT user_id,account_id,category_id,type,amount,txn_date,source FROM transactions
                    ORDER BY id LIMIT 1
                    """))).isInstanceOf(IllegalStateException.class).hasMessage("Unexpected evaluation totals");
            for (String table : new String[]{"accounts", "transactions", "budgets", "bills", "bill_payments",
                    "goals", "goal_contributions", "score_inputs"}) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
            }
            var result = loader.load(target, worker, irregular);
            assertThat(result.digest()).isEqualTo(PersistentEvaluationDatasetLoader.DIGEST);
            var a = result.totals().get("worker");
            var b = result.totals().get("irregular");
            assertThat(a.net()).isEqualByComparingTo("213900.01");
            assertThat(b.net()).isEqualByComparingTo("118150.01");
            assertThat(a.transactions() + b.transactions()).isEqualTo(826);
            assertThat(a.income().add(b.income())).isEqualByComparingTo("955250.00");
            assertThat(a.expense().add(b.expense())).isEqualByComparingTo("623199.98");
            assertThat(a.net().add(b.net())).isEqualByComparingTo("332050.02");
            assertThatThrownBy(() -> loader.load(target, worker, irregular))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("not empty");
            var workerToken = login(client, port, WORKER, workerPassword);
            var irregularToken = login(client, port, IRREGULAR, irregularPassword);
            String workerGoal = "/api/v1/goals/" + result.goals().get("worker");
            String irregularGoal = "/api/v1/goals/" + result.goals().get("irregular");
            request(client, port, workerGoal, null, workerToken, 200);
            request(client, port, irregularGoal, null, irregularToken, 200);
            request(client, port, workerGoal, null, irregularToken, 404);
            request(client, port, irregularGoal, null, workerToken, 404);
            var dashboard = "/api/v1/dashboard?month=2026-11";
            assertThat(request(client, port, dashboard, null, workerToken, 200).get("income").decimalValue())
                    .isEqualByComparingTo("60000");
            assertThat(request(client, port, dashboard, null, irregularToken, 200).get("income").decimalValue())
                    .isEqualByComparingTo("25750");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isEqualTo(826);
        } finally {
            ProxySelector.setDefault(previousProxy);
        }
    }

    private static final class LoopbackOnlyProxySelector extends ProxySelector {
        @Override
        public List<Proxy> select(URI uri) {
            if (!"127.0.0.1".equals(uri.getHost())) {
                throw new SecurityException("External HTTP is disabled in T55");
            }
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            // Loopback connection failures are handled by the caller.
        }
    }

    private static PersistentEvaluationDatasetLoader.Owner registerOrVerify(HttpClient client, int port,
            JdbcTemplate jdbc, String email, String password) throws Exception {
        var ids = jdbc.queryForList("SELECT id FROM users WHERE email=?", Long.class, email);
        if (!ids.isEmpty()) {
            login(client, port, email, password);
            return new PersistentEvaluationDatasetLoader.Owner(ids.get(0), email);
        }
        var result = request(client, port, "/api/v1/auth/register", Map.of(
                "email", email, "password", password, "fullName", "Synthetic T55", "language", "EN",
                "over18", true, "consentVersion", AuthService.CONSENT_VERSION), null, 201);
        return new PersistentEvaluationDatasetLoader.Owner(result.get("userId").asLong(), email);
    }

    private static String login(HttpClient client, int port, String email, String password) throws Exception {
        var result = request(client, port, "/api/v1/auth/login", Map.of("email", email, "password", password), null, 200);
        var token = result.path("accessToken").asText();
        if (token.isBlank()) {
            throw new IllegalStateException("Expected a normal login session");
        }
        return token;
    }

    private static JsonNode request(HttpClient client, int port, String path, Map<String, ?> body,
            String token, int expectedStatus) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("HTTP status for %s", path).isEqualTo(expectedStatus);
        return JSON.readTree(response.body());
    }

    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + name);
        }
        return value;
    }
}
