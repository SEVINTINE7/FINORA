package lk.finora.api.smsformat;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminSmsFormatControllerTest extends IntegrationTest {
    private static final String ADMIN_FORMAT = "/api/v1/admin/sms-formats/NSB/1";
    private static final String REVISIONS = "/api/v1/admin/sms-formats/NSB/revisions";

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void onlyAdminCanToggleApprovedFormatWithoutChangingUserContract() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var user = tokenFor(createUser(Role.USER));
        activate(tokenFor(createUser(Role.STAFF)), false).andExpect(status().isForbidden());
        activate(user, false).andExpect(status().isForbidden());
        activate(admin, false).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.templates.length()").value(0));
        activate(admin, true).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.templates.length()").value(3));
    }

    @Test void publishesAndRollsBackImmutableDeploymentRevisions() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var staff = tokenFor(createUser(Role.STAFF));
        var user = tokenFor(createUser(Role.USER));
        String initial = mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andReturn().getResponse().getContentAsString();
        command(staff, "/2").andExpect(status().isForbidden());
        command(user, "/2/publish").andExpect(status().isForbidden());
        command(admin, "/2").andExpect(status().isCreated());
        command(admin, "/2/activate").andExpect(status().isNotFound());
        command(admin, "/2/publish").andExpect(status().isNoContent());
        command(admin, "/2/publish").andExpect(status().isNotFound());
        command(admin, "/2/activate").andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(2, activeRevision());
        command(admin, "/1/activate").andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(1, activeRevision());
        mockMvc.perform(get("/api/v1/sms-formats").header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andExpect(content().string(initial));
        command(admin, "/3").andExpect(status().isCreated());
        jdbc.update("UPDATE sms_formats SET pattern = ? WHERE bank_code = 'NSB' AND version = 3", "invalid fixture");
        command(admin, "/3/publish").andExpect(status().isConflict());
    }

    private Integer activeRevision() {
        return jdbc.queryForObject("SELECT version FROM sms_formats WHERE bank_code = 'NSB' AND active", Integer.class);
    }

    private org.springframework.test.web.servlet.ResultActions command(String token, String path) throws Exception {
        return mockMvc.perform(post(REVISIONS + path).header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions activate(String token, boolean active) throws Exception {
        return mockMvc.perform(patch(ADMIN_FORMAT).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json(java.util.Map.of("active", active))));
    }
}
