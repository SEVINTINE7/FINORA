package lk.finora.api.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.security.JwtService;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;
import lk.finora.api.user.UserRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for API integration tests: full Spring context, the D11 MySQL schema {@code finora_test}
 * (test profile) and {@link MockMvc}. Each test runs in a transaction that is rolled back.
 *
 * <pre>
 * class CategoryControllerTest extends IntegrationTest {
 *     &#64;Test void list() throws Exception {
 *         mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
 *     }
 * }
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtService jwtService;

    private static int userCounter = 0;

    /** Serialises a request DTO for {@code .content(...)}. */
    protected String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }

    /** Saves an ACTIVE user with the given role and a unique email (T06.4). */
    protected User createUser(Role role) {
        userCounter++;
        User user = new User();
        user.setEmail(role.name().toLowerCase() + userCounter + "-" + System.nanoTime() + "@test.lk");
        // Not a real BCrypt hash: users made here log in with tokenFor(...), never with a password
        user.setPasswordHash("test-only-not-a-hash");
        user.setFullName("Test " + role.name());
        user.setRole(role);
        return userRepository.save(user);
    }

    /** A valid access token for the user, for {@code .header("Authorization", "Bearer " + token)}. */
    protected String tokenFor(User user) {
        return jwtService.createAccessToken(user.getId(), user.getRole().name());
    }
}
