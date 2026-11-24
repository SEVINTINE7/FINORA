package lk.finora.api.admin;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminStatsControllerTest extends IntegrationTest {
    private static final String PATH = "/api/v1/admin/stats";

    @Test void onlyAdminGetsCountsWithNoFinancialFields() throws Exception {
        createUser(Role.USER);
        var staff = createUser(Role.STAFF);
        var admin = createUser(Role.ADMIN);
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        for (var role : new Role[]{Role.USER, Role.STAFF}) {
            mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenFor(createUser(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"activeUsers":2,"disabledUsers":0,"staff":2,"openTickets":0,"activeFormats":1}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }
}
