package lk.finora.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Consent;
import lk.finora.api.user.ConsentRepository;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;

/** T06: URL role rules, ownership (404) and disabled accounts. */
class RoleAccessTest extends IntegrationTest {

    // Test-only USER endpoint, see support/TestOwnershipController
    private static final String USER_URL = "/api/v1/test/consents/";

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private ResultActions getAs(User user, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + tokenFor(user)));
    }

    private Consent createConsent(User owner) {
        Consent consent = new Consent();
        consent.setUserId(owner.getId());
        consent.setVersion("1.0");
        consent.setOver18(true);
        consent.setAcceptedAt(Instant.now());
        return consentRepository.save(consent);
    }

    // ---------- URL rules ----------

    @Test
    void noTokenGives401() throws Exception {
        mockMvc.perform(get("/api/v1/test/consents/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotOpenAdminPaths() throws Exception {
        getAs(createUser(Role.USER), "/api/v1/admin/users")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void staffCannotOpenAdminPaths() throws Exception {
        getAs(createUser(Role.STAFF), "/api/v1/admin/users")
                .andExpect(status().isForbidden());
    }

    @Test
    void userAndAdminCannotOpenStaffPaths() throws Exception {
        getAs(createUser(Role.USER), "/api/v1/staff/requests").andExpect(status().isForbidden());
        getAs(createUser(Role.ADMIN), "/api/v1/staff/requests").andExpect(status().isForbidden());
    }

    @Test
    void adminAndStaffCannotOpenUserEndpoints() throws Exception {
        User user = createUser(Role.USER);
        Consent consent = createConsent(user);

        getAs(createUser(Role.ADMIN), USER_URL + consent.getId())
                .andExpect(status().isForbidden());
        getAs(createUser(Role.STAFF), USER_URL + consent.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void pathsOutsideApiAreDenied() throws Exception {
        getAs(createUser(Role.ADMIN), "/something-else")
                .andExpect(status().isForbidden());
    }

    // ---------- ownership ----------

    @Test
    void userCanReadOwnResource() throws Exception {
        User user = createUser(Role.USER);
        Consent consent = createConsent(user);

        getAs(user, USER_URL + consent.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0"));
    }

    @Test
    void otherUsersResourceGives404() throws Exception {
        User userA = createUser(Role.USER);
        User userB = createUser(Role.USER);
        Consent consentOfB = createConsent(userB);

        // Same answer as for an id that does not exist at all
        getAs(userA, USER_URL + consentOfB.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        getAs(userA, USER_URL + 999999999)
                .andExpect(status().isNotFound());
    }

    // ---------- disabled accounts ----------

    @Test
    void disabledUsersValidTokenGives403() throws Exception {
        User user = createUser(Role.USER);
        Consent consent = createConsent(user);
        String token = tokenFor(user); // issued while still ACTIVE

        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", user.getId());
        entityManager.clear(); // tests share one transaction; drop the cached ACTIVE user

        mockMvc.perform(get(USER_URL + consent.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void tokenOfDeletedUserGives401() throws Exception {
        User user = createUser(Role.USER);
        String token = tokenFor(user);

        jdbc.update("DELETE FROM users WHERE id = ?", user.getId());
        entityManager.clear();

        mockMvc.perform(get(USER_URL + 1).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ---------- CurrentUser ----------

    @Test
    void currentUserReadsIdAndRole() {
        User admin = createUser(Role.ADMIN);
        // Same login object the filter creates from a token
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            assertThat(CurrentUser.id()).isEqualTo(admin.getId());
            assertThat(CurrentUser.role()).isEqualTo(Role.ADMIN);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
