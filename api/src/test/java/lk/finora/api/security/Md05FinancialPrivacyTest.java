package lk.finora.api.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class Md05FinancialPrivacyTest extends IntegrationTest {

    private static final String MONTH = "2026-11";
    private static final String API = "/api/v1";

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;

    private Fixture owner;
    private Fixture other;

    private record Fixture(
            long userId, String token, long account, long category,
            long transaction, long budget, long bill, long payment,
            long goal, long contribution, int reserve) {}

    @BeforeEach
    void prepare() {
        owner = seed("Owner", 111);
        other = seed("Other", 222);
    }

    private Fixture seed(String label, int reserve) {
        var user = createUser(Role.USER);
        entities.flush();
        long id = user.getId();

        jdbc.update("""
                INSERT INTO accounts(user_id,name,type,opening_balance)
                VALUES (?,?,'CASH',5000.00)
                """, id, label + " synthetic wallet");

        jdbc.update("""
                INSERT INTO categories(user_id,name,kind)
                VALUES (?,?,'EXPENSE')
                """, id, label + " synthetic category");

        long account = ownId("accounts", id);
        long category = ownId("categories", id);

        jdbc.update("""
                INSERT INTO transactions
                (user_id,account_id,category_id,type,amount,txn_date,merchant)
                VALUES (?,?,?,'EXPENSE',125.50,'2026-11-01',?)
                """, id, account, category, label + " synthetic merchant");

        jdbc.update("""
                INSERT INTO budgets(user_id,category_id,month,limit_amount)
                VALUES (?,?,?,1000.00)
                """, id, category, MONTH);

        jdbc.update("""
                INSERT INTO bills(user_id,name,amount,due_day)
                VALUES (?,?,250.00,15)
                """, id, label + " synthetic bill");

        jdbc.update("""
                INSERT INTO goals(user_id,name,target_amount)
                VALUES (?,?,10000.00)
                """, id, label + " synthetic goal");

        long bill = ownId("bills", id);
        long goal = ownId("goals", id);

        jdbc.update("""
                INSERT INTO bill_payments(bill_id,month)
                VALUES (?,?)
                """, bill, MONTH);

        jdbc.update("""
                INSERT INTO goal_contributions(goal_id,amount,contrib_date)
                VALUES (?,100.00,'2026-11-01')
                """, goal);

        jdbc.update("""
                INSERT INTO score_inputs
                (user_id,month,emergency_reserve,reserve_as_of,confirmed_at)
                VALUES (?,?,?,'2026-11-01',UTC_TIMESTAMP(6))
                """, id, MONTH, reserve);

        long payment = jdbc.queryForObject(
                "SELECT id FROM bill_payments WHERE bill_id=?",
                Long.class, bill);

        long contribution = jdbc.queryForObject(
                "SELECT id FROM goal_contributions WHERE goal_id=?",
                Long.class, goal);

        return new Fixture(id, tokenFor(user), account, category,
                ownId("transactions", id), ownId("budgets", id),
                bill, payment, goal, contribution, reserve);
    }

    private long ownId(String table, long userId) {
        return jdbc.queryForObject(
                "SELECT id FROM " + table + " WHERE user_id=?",
                Long.class, userId);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode export(Fixture fixture) throws Exception {
        var response = mockMvc.perform(get(API + "/me/export")
                .header("Authorization", bearer(fixture.token())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();

        return jsonMapper.readTree(response.getContentAsString());
    }

    private void assertOwnedRow(JsonNode data, String family, long id) {
        JsonNode rows = data.get(family);
        assertThat(rows.isArray()).isTrue();
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).get("id").asLong()).isEqualTo(id);
    }

    private void assertExport(Fixture fixture) throws Exception {
        JsonNode result = export(fixture);

        assertThat(result.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(result.get("profile").get("id").asLong())
                .isEqualTo(fixture.userId());

        JsonNode data = result.get("data");

        assertThat(data.propertyNames()).containsExactlyInAnyOrder(
                "consents", "accounts", "categories", "transactions",
                "budgets", "bills", "billPayments", "goals",
                "goalContributions", "insights", "healthScores",
                "scoreInputs", "history", "advice", "ratings",
                "tickets", "ticketReplies", "smsDiagnostics");

        assertOwnedRow(data, "accounts", fixture.account());
        assertOwnedRow(data, "categories", fixture.category());
        assertOwnedRow(data, "transactions", fixture.transaction());
        assertOwnedRow(data, "budgets", fixture.budget());
        assertOwnedRow(data, "bills", fixture.bill());
        assertOwnedRow(data, "billPayments", fixture.payment());
        assertOwnedRow(data, "goals", fixture.goal());
        assertOwnedRow(data, "goalContributions", fixture.contribution());

        assertThat(data.get("billPayments").get(0)
                .get("bill_id").asLong()).isEqualTo(fixture.bill());

        assertThat(data.get("goalContributions").get(0)
                .get("goal_id").asLong()).isEqualTo(fixture.goal());

        assertThat(data.get("scoreInputs").size()).isEqualTo(1);
        assertThat(data.get("scoreInputs").get(0)
                .get("emergency_reserve").asInt())
                .isEqualTo(fixture.reserve());

        assertThat(result.get("profile").propertyNames())
                .containsExactlyInAnyOrder(
                        "id", "email", "full_name", "language",
                        "created_at", "updated_at");
    }

    @Test
    void bothUsersExportOnlyTheirOwnPopulatedFinancialFamilies()
            throws Exception {
        assertExport(owner);
        assertExport(other);

        System.out.println(
                "T52_EXT export: 2 users, 9 populated families each");
    }

    private List<Long> counts() {
        return List.of("users", "accounts", "categories", "transactions",
                        "budgets", "bills", "bill_payments", "goals",
                        "goal_contributions", "score_inputs", "consents",
                        "monthly_history", "advice", "advice_ratings",
                        "refresh_tokens", "device_tokens")
                .stream()
                .map(table -> jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table, Long.class))
                .toList();
    }

    private void assertPrivateDenial(RequestBuilder request)
            throws Exception {
        var result = mockMvc.perform(request).andReturn();
        var response = result.getResponse();

        assertThat(response.getStatus()).isEqualTo(403);

        JsonNode error = jsonMapper.readTree(
                response.getContentAsString());

        assertThat(error.propertyNames()).containsExactlyInAnyOrder(
                "timestamp", "status", "code", "message", "path");

        assertThat(error.get("status").asInt()).isEqualTo(403);
        assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(error.get("message").asString())
                .isEqualTo("Access denied");

        assertThat(error.get("path").asString())
                .isEqualTo(result.getRequest().getRequestURI());

        java.time.Instant.parse(error.get("timestamp").asString());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "STAFF"})
    void privilegedRolesCannotAccessAdditionalFinancialOperations(Role role)
            throws Exception {

        String auth = bearer(tokenFor(createUser(role)));
        entities.flush();

        JsonNode beforeOwner = export(owner);
        JsonNode beforeOther = export(other);
        List<Long> beforeCounts = counts();

        String csv = """
                date,type,amount,category,account,merchant,note
                2026-11-01,EXPENSE,1.00,,,Synthetic,
                """;

        var simulation = Map.of(
                "goalId", owner.goal(),
                "baselineMonthlySavings", "1000.00",
                "monthlyChange", "0.00",
                "categoryCuts", List.of());

        var csvRow = Map.of(
                "line", 2,
                "txnDate", "2026-11-01",
                "type", "EXPENSE",
                "amount", "1.00",
                "accountId", owner.account(),
                "categoryId", owner.category(),
                "occurrence", 1);

        List<RequestBuilder> requests = List.of(
                get(API + "/history")
                        .param("from", MONTH).param("to", MONTH)
                        .header("Authorization", auth),

                get(API + "/advice")
                        .param("month", MONTH).param("mode", "V1")
                        .header("Authorization", auth),

                post(API + "/advice/9223372036854775807/rating")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"),

                post(API + "/daily-guide")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),

                post(API + "/simulate")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(simulation)),

                multipart(API + "/imports/csv/preview")
                        .file("file", csv.getBytes(StandardCharsets.UTF_8))
                        .header("Authorization", auth),

                post(API + "/imports/csv/confirm")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("rows", List.of(csvRow)))),

                get(API + "/me/export")
                        .header("Authorization", auth),

                delete(API + "/me")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Synthetic9-password\"}"),

                get(API + "/me")
                        .header("Authorization", auth),

                put(API + "/me")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Denied\",\"language\":\"EN\"}"),

                post(API + "/me/consent/withdraw")
                        .header("Authorization", auth)
        );

        for (RequestBuilder request : requests) {
            assertPrivateDenial(request);
            entities.flush();

            assertThat(counts()).isEqualTo(beforeCounts);
            assertThat(export(owner)).isEqualTo(beforeOwner);
            assertThat(export(other)).isEqualTo(beforeOther);
        }

        System.out.printf(
                "T52_EXT %s: %d private denials, snapshots unchanged%n",
                role, requests.size());
    }


    @Test
    void foreignFinancialIdsAreRejectedWithoutChangingEitherUser()
            throws Exception {

        String auth = bearer(other.token());
        String ownerAuth = bearer(owner.token());

        record Resource(String root, long id, Object update) {}

        List<Resource> resources = List.of(
                new Resource("accounts", owner.account(),
                        Map.of("name", "Denied update", "type", "CASH",
                                "openingBalance", "1.00")),

                new Resource("categories", owner.category(),
                        Map.of("name", "Denied category")),

                new Resource("transactions", owner.transaction(),
                        Map.of("accountId", other.account(),
                                "categoryId", other.category(),
                                "type", "EXPENSE", "amount", "1.00",
                                "txnDate", "2026-11-01")),

                new Resource("budgets", owner.budget(),
                        Map.of("categoryId", other.category(),
                                "month", MONTH, "limitAmount", "1.00")),

                new Resource("bills", owner.bill(),
                        Map.of("name", "Denied bill", "amount", "1.00",
                                "dueDay", 15)),

                new Resource("goals", owner.goal(),
                        Map.of("name", "Denied goal",
                                "targetAmount", "1.00"))
        );

        List<RequestBuilder> attacks = new java.util.ArrayList<>();

        for (Resource resource : resources) {
            String route = API + "/" + resource.root()
                    + "/" + resource.id();

            // Positive control: this resource really exists.
            mockMvc.perform(get(route)
                    .header("Authorization", ownerAuth))
                    .andExpect(status().isOk());

            attacks.add(get(route)
                    .header("Authorization", auth));

            attacks.add(put(route)
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(resource.update())));

            attacks.add(delete(route)
                    .header("Authorization", auth));
        }

        String foreignGoal = API + "/goals/" + owner.goal()
                + "/contributions";

        String ownGoal = API + "/goals/" + other.goal()
                + "/contributions";

        mockMvc.perform(get(foreignGoal)
                .header("Authorization", ownerAuth))
                .andExpect(status().isOk());

        attacks.add(get(foreignGoal)
                .header("Authorization", auth));

        attacks.add(post(foreignGoal)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("amount", "10.00",
                        "date", "2026-11-01"))));

        attacks.add(delete(foreignGoal + "/" + owner.contribution())
                .header("Authorization", auth));

        attacks.add(delete(ownGoal + "/" + owner.contribution())
                .header("Authorization", auth));

        attacks.add(delete(foreignGoal + "/" + other.contribution())
                .header("Authorization", auth));

        attacks.add(post(API + "/simulate")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "goalId", owner.goal(),
                        "baselineMonthlySavings", "1000.00",
                        "monthlyChange", "0.00",
                        "categoryCuts", List.of()))));

        entities.flush();

        JsonNode beforeOwner = export(owner);
        JsonNode beforeOther = export(other);
        List<Long> beforeCounts = counts();

        for (RequestBuilder attack : attacks) {

            var result = mockMvc.perform(attack).andReturn();
            var response = result.getResponse();

            assertThat(response.getStatus())
                    .as("%s %s",
                            result.getRequest().getMethod(),
                            result.getRequest().getRequestURI())
                    .isEqualTo(404);

            JsonNode error = jsonMapper.readTree(
                    response.getContentAsString());

            assertThat(error.get("code").asString())
                    .isEqualTo("NOT_FOUND");

            entities.flush();

            assertThat(counts()).isEqualTo(beforeCounts);
            assertThat(export(owner)).isEqualTo(beforeOwner);
            assertThat(export(other)).isEqualTo(beforeOther);
        }

        System.out.printf(
                "T52_IDOR: %d foreign-ID operations denied, both snapshots unchanged%n",
                attacks.size());
    }


    private void seedRemaining(Fixture f, String label, int score, long staffId) {
        long id = f.userId();
        jdbc.update("INSERT INTO consents(user_id,version,over18) VALUES (?,?,TRUE)", id, label);
        jdbc.update("INSERT INTO insights(user_id,month,type,facts) VALUES (?,?,?,JSON_OBJECT('fixture',?))", id, MONTH, "HIGH_WANTS", label);
        jdbc.update("INSERT INTO health_scores(user_id,month,total,parts) VALUES (?,?,?,'[]')", id, MONTH, score);
        jdbc.update("INSERT INTO monthly_history(user_id,month,income,expense,score,insights) VALUES (?,?,?,0,?,'[]')", id, MONTH, score * 100, score);
        jdbc.update("INSERT INTO advice(user_id,month,source,mode,body,verified) VALUES (?,?,'RULE','V1','[]',FALSE)", id, MONTH);
        long advice = ownId("advice", id);
        jdbc.update("INSERT INTO advice_ratings(advice_id,user_id,rating) VALUES (?,?,?)", advice, id, score == 71 ? 4 : 2);
        String subject = score == 71 ? "Sign-in" : "Language";
        String body = score == 71 ? "I need help signing in." : "I need help changing the app language.";
        jdbc.update("INSERT INTO support_tickets(user_id,staff_id,subject,body,status) VALUES (?,?,?,?,'IN_PROGRESS')", id, staffId, subject, body);
        long ticket = ownId("support_tickets", id);
        jdbc.update("INSERT INTO ticket_replies(ticket_id,author_id,body) VALUES (?,?,?)", ticket, staffId, score == 71 ? "Check your connection and try signing in again." : "Open Language and choose English or Sinhala. Connect to the internet to save the choice to your account.");
        jdbc.update("INSERT INTO sms_diagnostic_reports(user_id,bank_code,format_version,error_code,report_day) VALUES (?,'NSB',1,?,'2026-11-01')", id, score == 71 ? "FORMAT_AMBIGUOUS" : "FORMAT_UNRECOGNIZED");
    }

    private JsonNode onlyRow(JsonNode data, String family, String... fields) {
        JsonNode rows = data.get(family);
        assertThat(rows).as(family).isNotNull();
        assertThat(rows.isArray()).as(family).isTrue();
        assertThat(rows.size()).as(family).isEqualTo(1);
        JsonNode row = rows.get(0);
        assertThat(row.propertyNames()).as(family).containsExactlyInAnyOrder(fields);
        return row;
    }

    private void verifyRemaining(Fixture f, String label, int score) throws Exception {
        assertExport(f);
        JsonNode data = export(f).get("data");
        long id = f.userId();
        long advice = ownId("advice", id);
        long ticket = ownId("support_tickets", id);
        assertThat(onlyRow(data,"consents","version","over18","accepted_at","withdrawn_at").get("version").asString()).isEqualTo(label);
        assertThat(onlyRow(data,"insights","month","type","facts","created_at").get("facts").toString()).contains(label);
        assertThat(onlyRow(data,"healthScores","month","total","parts","created_at").get("total").asInt()).isEqualTo(score);
        assertThat(onlyRow(data,"history","month","income","expense","score","insights").get("score").asInt()).isEqualTo(score);
        assertThat(onlyRow(data,"advice","id","month","source","mode","body","verified","created_at").get("id").asLong()).isEqualTo(advice);
        JsonNode rating = onlyRow(data,"ratings","advice_id","rating","created_at");
        assertThat(rating.get("advice_id").asLong()).isEqualTo(advice);
        assertThat(rating.get("rating").asInt()).isEqualTo(score == 71 ? 4 : 2);
        assertThat(onlyRow(data,"tickets","id","subject","body","status","error_code","created_at").get("id").asLong()).isEqualTo(ticket);
        JsonNode reply = onlyRow(data,"ticketReplies","ticket_id","body","created_at");
        assertThat(reply.get("ticket_id").asLong()).isEqualTo(ticket);
        assertThat(reply.get("body").asString()).isEqualTo(score == 71 ? "Check your connection and try signing in again." : "Open Language and choose English or Sinhala. Connect to the internet to save the choice to your account.");
        assertThat(onlyRow(data,"smsDiagnostics","bank_code","format_version","error_code","report_day").get("error_code").asString()).isEqualTo(score == 71 ? "FORMAT_AMBIGUOUS" : "FORMAT_UNRECOGNIZED");
    }

    @Test
    void allExportFamiliesArePopulatedAndOwnerIsolated() throws Exception {
        var staff = createUser(Role.STAFF);
        entities.flush();
        seedRemaining(owner, "1.0", 71, staff.getId());
        seedRemaining(other, "2.0", 82, staff.getId());
        verifyRemaining(owner,"1.0",71);
        verifyRemaining(other,"2.0",82);
        System.out.println("T52_T35_EXPORT: 18 populated families per user, 2 users verified");
    }

    private JsonNode readAuthorized(String route, String auth) throws Exception {
        return jsonMapper.readTree(mockMvc.perform(get(API + route).header("Authorization",auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void populatedFinanceNeverLeaksThroughAuthorizedOperationsResponses() throws Exception {
        var admin = createUser(Role.ADMIN);
        var staff = createUser(Role.STAFF);
        var second = createUser(Role.STAFF);
        entities.flush();
        seedRemaining(owner,"1.0",71,staff.getId());
        seedRemaining(other,"2.0",82,second.getId());
        long ownedTicket = ownId("support_tickets",owner.userId());
        long otherTicket = ownId("support_tickets",other.userId());
        String aa = bearer(tokenFor(admin)), sa = bearer(tokenFor(staff));
        JsonNode beforeOwner = export(owner), beforeOther = export(other);
        List<Long> beforeCounts = counts();
        String email = beforeOwner.get("profile").get("email").asString();
        JsonNode users = jsonMapper.readTree(mockMvc.perform(get(API + "/admin/users").param("q",email)
                .header("Authorization",aa)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(users.size()).isEqualTo(1);
        assertThat(users.get(0).propertyNames()).containsExactlyInAnyOrder("id","email","name","status","created_at");
        assertThat(users.get(0).get("id").asLong()).isEqualTo(owner.userId());
        assertThat(readAuthorized("/admin/stats",aa).propertyNames()).containsExactlyInAnyOrder("activeUsers","disabledUsers","staff","openTickets","activeFormats");
        assertThat(readAuthorized("/staff/stats",sa).propertyNames()).containsExactlyInAnyOrder("myOpenTickets","unassignedTickets");
        JsonNode queue = readAuthorized("/staff/tickets",sa);
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.get(0).propertyNames()).containsExactlyInAnyOrder("id","subject","body","status","error_code");
        assertThat(queue.get(0).get("id").asLong()).isEqualTo(ownedTicket);
        assertThat(queue.get(0).get("body").asString()).isEqualTo("I need help signing in.");
        JsonNode groups = readAuthorized("/staff/sms-diagnostics",sa);
        assertThat(groups.size()).isEqualTo(2);
        for (JsonNode group : groups) assertThat(group.propertyNames()).containsExactlyInAnyOrder("bankCode","formatVersion","errorCode","reports");
        JsonNode staffList = readAuthorized("/admin/staff",aa);
        assertThat(staffList.size()).isGreaterThanOrEqualTo(2);
        for (JsonNode row : staffList) assertThat(row.propertyNames()).containsExactlyInAnyOrder("id","email","fullName","status","pending");
        JsonNode categories = readAuthorized("/admin/categories",aa);
        assertThat(categories.size()).isPositive();
        for (JsonNode row : categories) assertThat(row.propertyNames()).containsExactlyInAnyOrder("id","name","nameSi","kind","active","semanticCode");
        JsonNode revisions = readAuthorized("/admin/sms-formats/NSB/revisions",aa);
        assertThat(revisions.size()).isPositive();
        for (JsonNode row : revisions) assertThat(row.propertyNames()).containsExactlyInAnyOrder("version","published","active");
        mockMvc.perform(post(API + "/staff/tickets/" + otherTicket + "/close").header("Authorization",sa))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT staff_id FROM support_tickets WHERE id=?",Long.class,otherTicket)).isEqualTo(second.getId());
        assertThat(counts()).isEqualTo(beforeCounts);
        assertThat(export(owner)).isEqualTo(beforeOwner);
        assertThat(export(other)).isEqualTo(beforeOther);
        System.out.println("T52_AUTHORIZED: 8 response families, foreign staff close denied, both snapshots unchanged");
    }
}
