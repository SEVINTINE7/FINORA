package lk.finora.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;

/** T07: consent row on register, GET/PUT /me and consent withdrawal. */
class MeControllerTest extends IntegrationTest {

    private static final String EMAIL = "kamala@example.com";
    private static final String PASSWORD = "Secret123";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private String accessToken;
    private String refreshToken;

    /** Registers and logs in through the real endpoints, so we get a real refresh token. */
    @BeforeEach
    void registerAndLogin() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("email", EMAIL);
        body.put("password", PASSWORD);
        body.put("fullName", "Kamala Silva");
        body.put("language", "EN");
        body.put("over18", true);
        body.put("consentVersion", "1.0");
        postJson("/api/v1/auth/register", body).andExpect(status().isCreated());

        String login = postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accessToken = jsonMapper.readTree(login).get("accessToken").asString();
        refreshToken = jsonMapper.readTree(login).get("refreshToken").asString();
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }

    // ---------- T07.1 consent row ----------

    @Test
    void registerSavesConsentRow() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT c.version, c.over18, c.accepted_at, c.withdrawn_at FROM consents c "
                        + "JOIN users u ON u.id = c.user_id WHERE u.email = ?", EMAIL);
        assertThat(row.get("version")).isEqualTo("1.0"); // D12
        assertThat(row.get("over18")).isEqualTo(true);
        assertThat(row.get("accepted_at")).isNotNull();
        assertThat(row.get("withdrawn_at")).isNull();
    }

    // ---------- T07.2 GET / PUT /me ----------

    @Test
    void getMeReturnsOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Kamala Silva"))
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.consentVersion").value("1.0"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getMeWithoutTokenGives401() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void putMeChangesNameAndLanguage() throws Exception {
        mockMvc.perform(put("/api/v1/me").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "  Kamala Perera ", "language", "SI"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Kamala Perera"))
                .andExpect(jsonPath("$.language").value("SI"));

        // Saved in the database, not just echoed back
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer()))
                .andExpect(jsonPath("$.fullName").value("Kamala Perera"))
                .andExpect(jsonPath("$.language").value("SI"));
    }

    @Test
    void putMeRejectsBadValues() throws Exception {
        mockMvc.perform(put("/api/v1/me").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "", "language", "TA"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- T07.3 withdraw consent ----------

    @Test
    void withdrawConsentRevokesRefreshTokens() throws Exception {
        mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        entityManager.flush(); // tests share one transaction; write JPA changes before the SQL check

        Integer withdrawn = jdbc.queryForObject(
                "SELECT COUNT(*) FROM consents c JOIN users u ON u.id = c.user_id "
                        + "WHERE u.email = ? AND c.withdrawn_at IS NOT NULL", Integer.class, EMAIL);
        assertThat(withdrawn).isEqualTo(1);

        // The refresh token from login no longer works
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // The app sees consentVersion = null and asks to re-consent or delete data (D15)
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentVersion").doesNotExist());
    }

    @Test
    void withdrawTwiceIsStillOk() throws Exception {
        mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
    }
}
