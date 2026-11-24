package lk.finora.api.smsformat;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminSmsFormatControllerTest extends IntegrationTest {
    private static final String ADMIN_FORMAT = "/api/v1/admin/sms-formats/NSB/1";

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

    private org.springframework.test.web.servlet.ResultActions activate(String token, boolean active) throws Exception {
        return mockMvc.perform(patch(ADMIN_FORMAT).header("Authorization", "Bearer " + token)
                .contentType("application/json").content(json(java.util.Map.of("active", active))));
    }
}
