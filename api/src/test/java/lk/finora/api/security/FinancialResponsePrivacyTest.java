package lk.finora.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;

/** T52: privileged-role denial must be private and side-effect free, not just return 403. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class FinancialResponsePrivacyTest extends IntegrationTest {
    private static final String MONTH = "2026-11";
    private static final Set<String> ROOTS = Set.of("accounts", "categories", "transactions", "budgets",
            "bills", "goals", "dashboard", "insights", "score", "score-inputs");
    private static final List<String> TABLES = List.of("accounts", "categories", "transactions", "budgets",
            "bills", "bill_payments", "goals", "goal_contributions", "score_inputs", "insights");
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;
    private final Map<String, Long> ids = new LinkedHashMap<>();
    private long contributionId;

    @BeforeEach
    void seedSyntheticFinance() throws Exception {
        var owner = createUser(Role.USER);
        entities.flush();
        long userId = owner.getId();
        jdbc.update("INSERT INTO accounts (user_id,name,type,opening_balance) VALUES (?,?,'BANK',87654.32)",
                userId, "PRIVATE_SYNTHETIC_ACCOUNT");
        jdbc.update("INSERT INTO categories (user_id,name,kind) VALUES (?,?,'EXPENSE')",
                userId, "PRIVATE_SYNTHETIC_CATEGORY");
        ids.put("accounts", id("accounts", userId));
        ids.put("categories", id("categories", userId));
        jdbc.update("INSERT INTO transactions (user_id,account_id,category_id,type,amount,txn_date,merchant,note) "
                + "VALUES (?,?,?,'EXPENSE',4321.09,'2026-11-01',?,?)", userId, ids.get("accounts"),
                ids.get("categories"), "PRIVATE_SYNTHETIC_MERCHANT", "PRIVATE_SYNTHETIC_NOTE");
        jdbc.update("INSERT INTO budgets (user_id,category_id,month,limit_amount) VALUES (?,?,?,9876.54)",
                userId, ids.get("categories"), MONTH);
        jdbc.update("INSERT INTO bills (user_id,name,amount,due_day) VALUES (?, ?,2345.67,15)",
                userId, "PRIVATE_SYNTHETIC_BILL");
        jdbc.update("INSERT INTO goals (user_id,name,target_amount) VALUES (?, ?,76543.21)",
                userId, "PRIVATE_SYNTHETIC_GOAL");
        for (String table : List.of("transactions", "budgets", "bills", "goals")) ids.put(table, id(table, userId));
        jdbc.update("INSERT INTO bill_payments (bill_id,month) VALUES (?,?)", ids.get("bills"), MONTH);
        jdbc.update("INSERT INTO goal_contributions (goal_id,amount,contrib_date) VALUES (?,123.45,'2026-11-01')",
                ids.get("goals"));
        contributionId = jdbc.queryForObject("SELECT id FROM goal_contributions WHERE goal_id=?", Long.class, ids.get("goals"));
        jdbc.update("INSERT INTO score_inputs (user_id,month,emergency_reserve,reserve_as_of,confirmed_at) "
                + "VALUES (?,?,3456.78,'2026-11-01',UTC_TIMESTAMP(6))", userId, MONTH);
        // Positive controls: the records exist and are readable by their owner.
        for (String root : List.of("accounts", "transactions")) {
            mockMvc.perform(get("/api/v1/" + root + "/" + ids.get(root))
                    .header("Authorization", "Bearer " + tokenFor(owner))).andExpect(status().isOk());
        }
    }

    private long id(String table, long userId) {
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE user_id=?", Long.class, userId);
    }

    private record Endpoint(HttpMethod method, String pattern, String root) {}

    private List<Endpoint> endpoints(boolean reads) {
        List<Endpoint> result = new ArrayList<>();
        for (var mapping : mappings.getHandlerMethods().keySet()) {
            for (String pattern : mapping.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/")) continue;
                String root = pattern.substring("/api/v1/".length()).split("/")[0];
                if (!ROOTS.contains(root)) continue;
                for (var method : mapping.getMethodsCondition().getMethods()) {
                    if ((method.asHttpMethod() == HttpMethod.GET) == reads)
                        result.add(new Endpoint(method.asHttpMethod(), pattern, root));
                }
            }
        }
        assertThat(result.size()).as("Nonempty real endpoint discovery").isGreaterThanOrEqualTo(reads ? 15 : 20);
        return result;
    }

    private String path(Endpoint e) {
        return e.pattern().replace("{month}", MONTH).replace("{contributionId}", String.valueOf(contributionId))
                .replace("{id}", String.valueOf(ids.getOrDefault(e.root(), 1L)));
    }

    private Object body(Endpoint e) {
        if (e.pattern().endsWith("/contributions")) return Map.of("amount", "1.00", "date", "2026-11-01");
        return switch (e.root()) {
            case "accounts" -> Map.of("name", "Denied account", "type", "BANK", "openingBalance", "1.00");
            case "categories" -> e.method() == HttpMethod.POST
                    ? Map.of("name", "Denied category", "kind", "EXPENSE") : Map.of("name", "Denied category");
            case "transactions" -> Map.of("accountId", ids.get("accounts"), "categoryId", ids.get("categories"),
                    "type", "EXPENSE", "amount", "1.00", "txnDate", "2026-11-01");
            case "budgets" -> Map.of("categoryId", ids.get("categories"), "month", MONTH, "limitAmount", "1.00");
            case "bills" -> Map.of("name", "Denied bill", "amount", "1.00", "dueDay", 15);
            case "goals" -> Map.of("name", "Denied goal", "targetAmount", "1.00");
            case "score-inputs" -> Map.of("emergencyReserve", "1.00", "reserveAsOf", "2026-11-01");
            default -> Map.of();
        };
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        entities.flush(); // Include any accidental managed-entity mutation, not just JDBC changes.
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        for (String table : TABLES) rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        return rows;
    }

    private void assertPrivateDenial(Endpoint e, String token) throws Exception {
        String path = path(e);
        var response = mockMvc.perform(request(e.method(), path).param("month", MONTH)
                .param("from", "2026-11-01").param("to", "2026-11-30")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json(body(e)))).andReturn().getResponse();
        assertThat(response.getStatus()).as("%s %s", e.method(), path).isEqualTo(403);
        var error = jsonMapper.readTree(response.getContentAsString());
        assertThat(error.propertyNames()).containsExactlyInAnyOrder("timestamp", "status", "code", "message", "path");
        assertThat(error.get("status").asInt()).isEqualTo(403);
        assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(error.get("message").asString()).isEqualTo("Access denied");
        assertThat(error.get("path").asString()).isEqualTo(path);
        java.time.Instant.parse(error.get("timestamp").asString());
        assertThat(response.getContentAsString()).doesNotContain("PRIVATE_SYNTHETIC", "87654.32", "4321.09", token);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"STAFF", "ADMIN"})
    void rejectedFinancialReadsExposeOnlyStandardErrorFields(Role role) throws Exception {
        String token = tokenFor(createUser(role));
        var before = snapshot();
        var endpoints = endpoints(true);
        for (var e : endpoints) {
            assertPrivateDenial(e, token);
            assertThat(snapshot()).as("Denied read must not mutate %s", e).isEqualTo(before);
        }
        System.out.printf("T52 %s: %d financial reads private and unchanged%n", role, endpoints.size());
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"STAFF", "ADMIN"})
    void rejectedFinancialWritesCannotMutateAnyFinancialRows(Role role) throws Exception {
        String token = tokenFor(createUser(role));
        var before = snapshot();
        var endpoints = endpoints(false);
        for (var e : endpoints) {
            assertPrivateDenial(e, token);
            assertThat(snapshot()).as("Denied write must not mutate %s", e).isEqualTo(before);
        }
        System.out.printf("T52 %s: %d financial writes private and unchanged%n", role, endpoints.size());
    }
}
