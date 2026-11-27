package lk.finora.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class SupportTicketControllerTest extends IntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private SupportTicketService ticketService;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final String TICKETS = "/api/v1/support/tickets";
    private static final String STAFF = "/api/v1/staff/tickets";
    private static final String CATALOG = "/api/v1/staff/reply-catalog";

    @Test void ownerAndAssigneeAreIsolatedAndApprovedReplySurvivesRetirement() throws Exception {
        var owner = tokenFor(createUser(Role.USER));
        var otherUser = tokenFor(createUser(Role.USER));
        var staff = tokenFor(createUser(Role.STAFF));
        var otherStaff = tokenFor(createUser(Role.STAFF));
        var created = send(TICKETS, owner, """
                {"subject":"Sign-in","body":"I need help signing in."}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = jsonMapper.readTree(created).get("id").asLong();
        read(TICKETS, otherUser).andExpect(jsonPath("$.length()").value(0));
        read(TICKETS + "/" + id + "/replies", otherUser).andExpect(status().isNotFound());
        var pendingQueue = read(STAFF, staff).andExpect(status().isOk()).andReturn().getResponse();
        var pending = jsonMapper.readTree(pendingQueue.getContentAsString()).get(0);
        assertThat(pending.propertyNames()).containsExactlyInAnyOrder("id", "subject", "body", "status", "error_code");
        assertThat(pending.get("id").asLong()).isEqualTo(id);
        assertThat(pending.get("status").asString()).isEqualTo("OPEN");
        for (String field : java.util.List.of("subject", "body", "error_code")) {
            assertThat(pending.get(field).isNull()).as(field).isTrue();
        }
        send(STAFF + "/" + id + "/claim", staff, "").andExpect(status().isNoContent());
        send(STAFF + "/" + id + "/claim", otherStaff, "").andExpect(status().isNotFound());
        read(STAFF, otherStaff).andExpect(jsonPath("$.length()").value(0));
        send(CATALOG, staff, "{\"topic\":\"SIGN_IN\"}").andExpect(status().isNoContent());
        var drafts = read(CATALOG, staff).andReturn().getResponse().getContentAsString();
        var draft = jsonMapper.readTree(drafts).get(0);
        assertThat(draft.propertyNames()).containsExactlyInAnyOrder("id", "topic", "state", "english", "sinhala");
        long revision = draft.get("id").asLong();
        send(CATALOG + "/" + revision + "/approve", staff, "").andExpect(status().isNotFound());
        send(CATALOG + "/" + revision + "/approve", otherStaff, "").andExpect(status().isNoContent());
        var reply = json(java.util.Map.of("revision", revision, "language", "EN"));
        send(STAFF + "/" + id + "/replies", otherStaff, reply).andExpect(status().isNotFound());
        send(STAFF + "/" + id + "/replies", staff, reply).andExpect(status().isNoContent());
        send(CATALOG + "/" + revision + "/retire", staff, "").andExpect(status().isNoContent());
        for (long unavailable : new long[]{revision, Long.MAX_VALUE}) {
            send(STAFF + "/" + id + "/replies", staff, json(java.util.Map.of("revision", unavailable, "language", "EN")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Support item unavailable"));
        }
        read(TICKETS + "/" + id + "/replies", owner).andExpect(status().isOk())
                .andExpect(content().json(json(java.util.List.of(java.util.Map.of("body", SupportCopy.SIGN_IN.replyEn))),
                        org.springframework.test.json.JsonCompareMode.STRICT));
        send(STAFF + "/" + id + "/close", staff, "").andExpect(status().isNoContent());
        read(TICKETS + "/" + id + "/replies", owner)
                .andExpect(jsonPath("$[0].body").value(SupportCopy.SIGN_IN.replyEn));
    }

    @Test void rejectsUnstructuredContentAndNonStaffCatalogAccess() throws Exception {
        var user = tokenFor(createUser(Role.USER));
        var admin = tokenFor(createUser(Role.ADMIN));
        var staff = tokenFor(createUser(Role.STAFF));
        send(TICKETS, user, "{\"subject\":\"Sign-in\",\"body\":\"Synthetic account 123456789\"}")
                .andExpect(status().isBadRequest());
        read(TICKETS, user).andExpect(jsonPath("$.length()").value(0));
        read(CATALOG, user).andExpect(status().isForbidden());
        read(CATALOG, admin).andExpect(status().isForbidden());
        for (String token : new String[]{user, admin}) {
            send(CATALOG, token, "{\"topic\":\"SIGN_IN\"}").andExpect(status().isForbidden());
        }
        send(CATALOG, staff, "{\"topic\":\"SIGN_IN\",\"text\":\"Unapproved synthetic reply\"}")
                .andExpect(status().isBadRequest());
        read(CATALOG, staff).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void optionalErrorCodeIsAllowlistedAndVisibleOnlyAfterClaim() throws Exception {
        var user = tokenFor(createUser(Role.USER));
        var staff = tokenFor(createUser(Role.STAFF));
        String payload = """
                {"subject":"Sign-in","body":"I need help signing in.","error_code":"SIGN_IN_FAILED"}
                """;
        send(TICKETS, user, payload.replace("SIGN_IN_FAILED", "UNKNOWN")).andExpect(status().isBadRequest());
        var created = send(TICKETS, user, payload).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = jsonMapper.readTree(created).get("id").asLong();
        read(TICKETS, user).andExpect(jsonPath("$[0].error_code").value("SIGN_IN_FAILED"));
        read(STAFF, staff).andExpect(jsonPath("$[0].error_code").isEmpty());
        send(STAFF + "/" + id + "/claim", staff, "").andExpect(status().isNoContent());
        read(STAFF, staff).andExpect(jsonPath("$[0].error_code").value("SIGN_IN_FAILED"));
    }

    @Test void stripsBoundedIdentifierLinesBeforeStorageAndDisclosure(CapturedOutput output) throws Exception {
        var owner = tokenFor(createUser(Role.USER));
        var assignee = tokenFor(createUser(Role.STAFF));
        var otherUser = tokenFor(createUser(Role.USER));
        var otherStaff = tokenFor(createUser(Role.STAFF));
        String canonical = "I need help signing in.";
        for (String line : java.util.List.of("Account number: 0000-0000-0000-0000-987",
                "Card number: 0000 0000 0000 0789")) {
            String result = send(TICKETS, owner, json(java.util.Map.of("subject", "Sign-in",
                    "body", canonical + "\n" + line, "error_code", "SIGN_IN_FAILED")))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long id = jsonMapper.readTree(result).get("id").asLong();
            org.junit.jupiter.api.Assertions.assertEquals(canonical, jdbc.queryForObject(
                    "SELECT body FROM support_tickets WHERE id = ?", String.class, id));
            read(TICKETS, owner).andExpect(jsonPath("$[0].body").value(canonical))
                    .andExpect(jsonPath("$[0].error_code").value("SIGN_IN_FAILED"));
            read(TICKETS, otherUser).andExpect(jsonPath("$.length()").value(0));
            read(STAFF, assignee).andExpect(jsonPath("$[0].body").isEmpty());
            send(STAFF + "/" + id + "/claim", assignee, "").andExpect(status().isNoContent());
            read(STAFF, assignee).andExpect(jsonPath("$[0].body").value(canonical));
            read(STAFF, otherStaff).andExpect(jsonPath("$.length()").value(0));
            String identifier = line.substring(line.indexOf(':') + 2);
            org.junit.jupiter.api.Assertions.assertFalse(output.getAll().contains(identifier));
            org.junit.jupiter.api.Assertions.assertEquals(0L, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM audit_events WHERE reason LIKE ?
                    """, Long.class, "%" + identifier + "%"));
        }
    }

    @Test void rejectsUnsafeTextRatherThanTryingToRedactIt() throws Exception {
        var owner = tokenFor(createUser(Role.USER));
        String canonical = "I need help signing in.";
        for (String suffix : java.util.List.of(" 00000000", "\nPassword: 00000000", "\nOTP: 00000000",
                "\nSMS: 00000000", "\nCard number: 00000000", "\nAccount number: 00000000 and more",
                "\nAccount number: 00000000\nCard number: 0000000000000",
                "\nAccount number: ００００００００", "\nAccount number: " + "0".repeat(100))) {
            send(TICKETS, owner, json(java.util.Map.of("subject", "Sign-in", "body", canonical + suffix)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Choose a supported help topic"));
        }
        read(TICKETS, owner).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void competingStaffCannotBothClaimTheSameTicket() throws Exception {
        long owner = createUser(Role.USER).getId();
        long first = createUser(Role.STAFF).getId();
        long second = createUser(Role.STAFF).getId();
        long ticket = ticketService.create(owner, "Sign-in", "I need help signing in.");
        var gate = new java.util.concurrent.CyclicBarrier(2);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var firstClaim = workers.submit(() -> claimAfter(gate, first, ticket));
            var secondClaim = workers.submit(() -> claimAfter(gate, second, ticket));
            boolean firstWon = firstClaim.get(10, java.util.concurrent.TimeUnit.SECONDS);
            boolean secondWon = secondClaim.get(10, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotEquals(firstWon, secondWon);
            org.junit.jupiter.api.Assertions.assertEquals(firstWon ? first : second,
                    jdbc.queryForObject("SELECT staff_id FROM support_tickets WHERE id = ?", Long.class, ticket));
        } finally {
            workers.shutdownNow();
            jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", owner, first, second);
        }
    }

    private boolean claimAfter(java.util.concurrent.CyclicBarrier gate, long staff, long ticket) throws Exception {
        gate.await(5, java.util.concurrent.TimeUnit.SECONDS);
        try {
            ticketService.claim(staff, ticket);
            return true;
        } catch (lk.finora.api.common.error.ApiException error) {
            org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, error.getStatus());
            return false;
        }
    }

    private org.springframework.test.web.servlet.ResultActions read(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions send(String path, String token, String body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body));
    }

    @Test
    void characterizesRetirementByProposingAndUnrelatedStaffBeforeAndAfterApproval() throws Exception {
        String proposer = tokenFor(createUser(Role.STAFF));
        String reviewer = tokenFor(createUser(Role.STAFF));
        String unrelated = tokenFor(createUser(Role.STAFF));
        for (String initialState : java.util.List.of("DRAFT", "ACTIVE")) {
            for (String actor : java.util.List.of(proposer, unrelated)) {
                send(CATALOG, proposer, "{\"topic\":\"SIGN_IN\"}").andExpect(status().isNoContent());
                var revision = jsonMapper.readTree(read(CATALOG, proposer).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
                String path = CATALOG + "/" + revision;
                if (initialState.equals("ACTIVE")) {
                    send(path + "/approve", reviewer, "").andExpect(status().isNoContent());
                }
                String row = "SELECT * FROM support_reply_revisions WHERE id = ?";
                var expected = new java.util.HashMap<>(jdbc.queryForMap(row, revision));
                assertThat(expected.put("state", "RETIRED")).isEqualTo(initialState);
                assertThat(expected.put("active_topic", null)).isEqualTo(initialState.equals("ACTIVE") ? "SIGN_IN" : null);
                send(path + "/retire", actor, "").andExpect(status().isNoContent());
                assertThat(jdbc.queryForMap(row, revision)).isEqualTo(expected);
                send(path + "/retire", actor, "").andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
                assertThat(jdbc.queryForMap(row, revision)).isEqualTo(expected);
            }
        }
    }

    @Test
    void missingSharedRevisionCannotChangeTheReplyCatalog() throws Exception {
        String proposer = tokenFor(createUser(Role.STAFF));
        String reviewer = tokenFor(createUser(Role.STAFF));
        send(CATALOG, proposer, "{\"topic\":\"SIGN_IN\"}").andExpect(status().isNoContent());
        var before = jdbc.queryForList("SELECT * FROM support_reply_revisions ORDER BY id");
        for (String action : java.util.List.of("approve", "retire")) {
            send(CATALOG + "/" + Long.MAX_VALUE + "/" + action, reviewer, "")
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Support item unavailable"));
            assertThat(jdbc.queryForList("SELECT * FROM support_reply_revisions ORDER BY id")).isEqualTo(before);
        }
    }
}
