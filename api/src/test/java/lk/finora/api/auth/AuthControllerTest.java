package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;

/** T05: register, login, JWT filter, refresh rotation and logout. */
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest extends IntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Value("${finora.jwt.secret}")
    private String jwtSecret;

    // ---------- helpers ----------

    private Map<String, Object> registerBody(String email, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("fullName", "Nimal Perera");
        body.put("language", "SI");
        body.put("over18", true);
        body.put("consentVersion", "1.0");
        return body;
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    private String readField(ResultActions result, String field) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get(field).asString();
    }

    // ---------- register ----------

    @Test
    void registerOk() throws Exception {
        postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber());

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'nimal@example.com'", String.class);
        assertThat(hash).startsWith("$2a$12$"); // BCrypt cost 12 (D09)

        Integer consents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consents c JOIN users u ON u.id = c.user_id "
                        + "WHERE u.email = 'nimal@example.com' AND c.version = '1.0' AND c.over18 = TRUE",
                Integer.class);
        assertThat(consents).isEqualTo(1);
    }

    @Test
    void registerDuplicateEmailGives409() throws Exception {
        postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD)).andExpect(status().isCreated());

        // Same email in different case is still a duplicate
        postJson("/api/v1/auth/register", registerBody("Nimal@Example.com", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registerUnder18Gives400() throws Exception {
        Map<String, Object> body = registerBody("young@example.com", PASSWORD);
        body.put("over18", false);

        postJson("/api/v1/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNDER_18"));
    }

    @Test
    void registerWeakPasswordGives400() throws Exception {
        // no digit
        postJson("/api/v1/auth/register", registerBody("a@example.com", "onlyletters"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[*].field", hasItem("password")));

        // too short
        postJson("/api/v1/auth/register", registerBody("b@example.com", "abc12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[*].field", hasItem("password")));
    }

    // ---------- login ----------

    @Test
    void loginOk() throws Exception {
        postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD));

        login("nimal@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.language").value("SI"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSameBody() throws Exception {
        postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD));

        String wrongPassword = login("nimal@example.com", "Wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", "Wrong1234")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Only the timestamp may differ
        String timestamp = "\"timestamp\":\"[^\"]*\"";
        assertThat(wrongPassword.replaceAll(timestamp, "")).isEqualTo(unknownEmail.replaceAll(timestamp, ""));
    }

    @Test
    void disabledAccountGives403() throws Exception {
        postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD));
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE email = 'nimal@example.com'");
        entityManager.clear(); // the test runs in one transaction, so forget the cached ACTIVE user

        login("nimal@example.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    // ---------- access token (JwtAuthFilter) ----------

    @Test
    void validAccessTokenIsAccepted() throws Exception {
        postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD));
        String accessToken = readField(login("nimal@example.com", PASSWORD), "accessToken");

        // No real protected endpoint exists yet: a logged-in user gets 404 instead of 401
        mockMvc.perform(get("/api/v1/nothing-here").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void expiredAccessTokenGives401() throws Exception {
        String expired = Jwts.builder()
                .subject("1")
                .claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis() - 3_600_000))
                .expiration(new Date(System.currentTimeMillis() - 1_800_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/nothing-here").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void garbageAccessTokenGives401() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- refresh and logout ----------

    @Test
    void refreshGivesNewPairAndOldTokenCannotBeReused() throws Exception {
        postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD));
        String oldRefresh = readField(login("nimal@example.com", PASSWORD), "refreshToken");

        String newRefresh = readField(postJson("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString()), "refreshToken");
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // Old token was rotated: using it again fails
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // The database stores only the SHA-256 hash, never the token
        Integer plain = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash IN (?, ?)",
                Integer.class, oldRefresh, newRefresh);
        assertThat(plain).isZero();
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD));
        String refresh = readField(login("nimal@example.com", PASSWORD), "refreshToken");

        postJson("/api/v1/auth/logout", Map.of("refreshToken", refresh)).andExpect(status().isNoContent());

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    // ---------- secrets never leak ----------

    @Test
    void passwordAndHashNeverInResponsesOrLogs(CapturedOutput output) throws Exception {
        String registerResponse = postJson("/api/v1/auth/register", registerBody("nimal@example.com", PASSWORD))
                .andReturn().getResponse().getContentAsString();
        String loginResponse = login("nimal@example.com", PASSWORD).andReturn().getResponse().getContentAsString();
        String wrongResponse = login("nimal@example.com", "Wrong1234").andReturn().getResponse().getContentAsString();
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'nimal@example.com'", String.class);

        for (String text : new String[] { registerResponse, loginResponse, wrongResponse, output.getAll() }) {
            assertThat(text).doesNotContain(PASSWORD).doesNotContain("Wrong1234").doesNotContain(hash);
        }
    }
}
