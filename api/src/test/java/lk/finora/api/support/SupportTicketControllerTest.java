package lk.finora.api.support;

import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportTicketControllerTest extends IntegrationTest {
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

    private org.springframework.test.web.servlet.ResultActions read(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions send(String path, String token, String body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body));
    }
}
