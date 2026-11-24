package lk.finora.api.support;

import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        read(STAFF, staff).andExpect(jsonPath("$[0].body").isEmpty());
        send(STAFF + "/" + id + "/claim", staff, "").andExpect(status().isNoContent());
        send(STAFF + "/" + id + "/claim", otherStaff, "").andExpect(status().isNotFound());
        read(STAFF, otherStaff).andExpect(jsonPath("$.length()").value(0));
        send(CATALOG, staff, "{\"topic\":\"SIGN_IN\"}").andExpect(status().isNoContent());
        var drafts = read(CATALOG, staff).andReturn().getResponse().getContentAsString();
        long revision = jsonMapper.readTree(drafts).get(0).get("id").asLong();
        send(CATALOG + "/" + revision + "/approve", staff, "").andExpect(status().isNotFound());
        send(CATALOG + "/" + revision + "/approve", otherStaff, "").andExpect(status().isNoContent());
        var reply = json(java.util.Map.of("revision", revision, "language", "EN"));
        send(STAFF + "/" + id + "/replies", otherStaff, reply).andExpect(status().isNotFound());
        send(STAFF + "/" + id + "/replies", staff, reply).andExpect(status().isNoContent());
        send(CATALOG + "/" + revision + "/retire", staff, "").andExpect(status().isNoContent());
        send(STAFF + "/" + id + "/close", staff, "").andExpect(status().isNoContent());
        read(TICKETS + "/" + id + "/replies", owner)
                .andExpect(jsonPath("$[0].body").value(SupportCopy.SIGN_IN.replyEn));
    }

    @Test void rejectsUnstructuredContentAndNonStaffCatalogAccess() throws Exception {
        var user = tokenFor(createUser(Role.USER));
        send(TICKETS, user, "{\"subject\":\"Sign-in\",\"body\":\"Synthetic account 123456789\"}")
                .andExpect(status().isBadRequest());
        read(TICKETS, user).andExpect(jsonPath("$.length()").value(0));
        read(CATALOG, user).andExpect(status().isForbidden());
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
}
