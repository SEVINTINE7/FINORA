package lk.finora.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SmsDiagnosticControllerTest extends IntegrationTest {
    private static final String SUBMIT = "/api/v1/support/sms-diagnostics";
    private static final String GROUPS = "/api/v1/staff/sms-diagnostics";
    private static final String INBOX = "/api/v1/admin/sms-diagnostics/escalations";
    private static final String ACCEPTED = """
            {"bankCode":null,"formatVersion":null,"errorCode":"FORMAT_AMBIGUOUS"}
            """;

    @Test void aggregatesOnlyMetadataAndDeduplicatesWithinOneUserDay() throws Exception {
        String user = tokenFor(createUser(Role.USER));
        send(user, ACCEPTED).andExpect(status().isNoContent());
        send(user, ACCEPTED).andExpect(status().isNoContent());
        send(tokenFor(createUser(Role.USER)), ACCEPTED).andExpect(status().isNoContent());
        mockMvc.perform(get(GROUPS).header("Authorization", "Bearer " + tokenFor(createUser(Role.STAFF))))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"bankCode":null,"formatVersion":null,"errorCode":"FORMAT_AMBIGUOUS","reports":2}]
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void rejectsUnapprovedMetadataPrivateFieldsAndCrossRoleAccess() throws Exception {
        String user = tokenFor(createUser(Role.USER));
        send(user, ACCEPTED.replace("\"bankCode\":null", "\"bankCode\":\"OTHER\""))
                .andExpect(status().isBadRequest());
        send(user, ACCEPTED.replace("\"formatVersion\":null", "\"formatVersion\":1"))
                .andExpect(status().isBadRequest());
        send(user, ACCEPTED.replace("\"bankCode\":null", "\"bankCode\":null,\"sms\":\"synthetic\""))
                .andExpect(status().isBadRequest());
        send(tokenFor(createUser(Role.STAFF)), ACCEPTED).andExpect(status().isForbidden());
        mockMvc.perform(get(GROUPS).header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions send(String token, String body) throws Exception {
        return mockMvc.perform(post(SUBMIT).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body));
    }

    @Test void staffFlagsGroupOnceAndOnlyAdminCanAdvanceIt() throws Exception {
        var staff = tokenFor(createUser(Role.STAFF));
        var admin = tokenFor(createUser(Role.ADMIN));
        send(tokenFor(createUser(Role.USER)), ACCEPTED).andExpect(status().isNoContent());
        var flag = post(GROUPS + "/escalations").header("Authorization", "Bearer " + staff)
                .contentType("application/json").content("""
                        {"bankCode":null,"errorCode":"FORMAT_AMBIGUOUS"}
                        """);
        mockMvc.perform(flag).andExpect(status().isNoContent());
        mockMvc.perform(flag).andExpect(status().isNoContent());
        String inbox = mockMvc.perform(get(INBOX).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        var escalation = jsonMapper.readTree(inbox).get(0);
        assertThat(escalation.propertyNames()).containsExactlyInAnyOrder(
                "id", "bankCode", "errorCode", "status", "createdAt");
        long id = escalation.get("id").asLong();
        mockMvc.perform(get(INBOX).header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
        for (String state : new String[]{"ACKNOWLEDGED", "CLOSED"}) {
            mockMvc.perform(patch(INBOX + "/" + id).header("Authorization", "Bearer " + admin)
                    .contentType("application/json").content(json(java.util.Map.of("status", state))))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get(INBOX).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
