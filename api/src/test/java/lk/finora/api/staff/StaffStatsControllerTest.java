package lk.finora.api.staff;

import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffStatsControllerTest extends IntegrationTest {
    private static final String ENDPOINT = "/api/v1/staff/stats";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;

    @Test void staffSeesOnlyOwnUnresolvedAndUnassignedQueueCounts() throws Exception {
        var staff = createUser(Role.STAFF);
        var other = createUser(Role.STAFF);
        long owner = createUser(Role.USER).getId();
        ticket(owner, staff.getId(), "OPEN");
        ticket(owner, staff.getId(), "IN_PROGRESS");
        ticket(owner, staff.getId(), "CLOSED");
        ticket(owner, other.getId(), "OPEN");
        ticket(owner, null, "OPEN");
        ticket(owner, null, "CLOSED");
        mockMvc.perform(get(ENDPOINT).header("Authorization", "Bearer " + tokenFor(staff)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"myOpenTickets\":2,\"unassignedTickets\":1}",
                        org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void routeRejectsAnonymousNonStaffAndDisabledStaff() throws Exception {
        mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
        for (Role role : new Role[]{Role.USER, Role.ADMIN}) {
            mockMvc.perform(get(ENDPOINT).header("Authorization", "Bearer " + tokenFor(createUser(role))))
                    .andExpect(status().isForbidden());
        }
        var staff = createUser(Role.STAFF);
        String token = tokenFor(staff);
        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=?", staff.getId());
        entities.clear();
        mockMvc.perform(get(ENDPOINT).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private void ticket(long owner, Long staff, String status) {
        jdbc.update("""
                INSERT INTO support_tickets (user_id, staff_id, subject, body, status)
                VALUES (?, ?, 'Synthetic support', 'Synthetic fixture', ?)
                """, owner, staff, status);
    }
}
