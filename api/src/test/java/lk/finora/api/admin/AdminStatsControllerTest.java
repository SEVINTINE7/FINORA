package lk.finora.api.admin;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminStatsControllerTest extends IntegrationTest {
    private static final String PATH = "/api/v1/admin/stats";

    @Value("${finora.jwt.secret}")
    private String testSecret;

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

    @Test void rejectsExpiredAdminTokenEvenWithValidSignature() throws Exception {
        var admin = createUser(Role.ADMIN);
        var key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));
        var expired = Jwts.builder()
                .subject(admin.getId().toString())
                .claim("role", Role.ADMIN.name())
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key)
                .compact();
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
