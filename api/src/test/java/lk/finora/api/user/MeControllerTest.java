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

        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawnSessionCannotRepeatAuthenticatedWithdrawal() throws Exception {
        mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoveryIsRestrictedSingleUseAndRequiresFreshLogin() throws Exception {
        mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        String first = recoveryToken();
        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized());
        recover("export", first, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(EMAIL));
        recover("export", first, Map.of()).andExpect(status().isUnauthorized());
        String replaced = recoveryToken();
        String current = recoveryToken();
        recover("accept", replaced, Map.of("accepted", true, "consentVersion", "1.0"))
                .andExpect(status().isUnauthorized());
        recover("accept", current, Map.of("accepted", false, "consentVersion", "1.0"))
                .andExpect(status().isBadRequest());
        recover("accept", current, Map.of("accepted", true, "consentVersion", "0.1"))
                .andExpect(status().isBadRequest());
        recover("accept", current, Map.of("accepted", true, "consentVersion", "1.0"))
                .andExpect(status().isNoContent());
        recover("accept", current, Map.of("accepted", true, "consentVersion", "1.0"))
                .andExpect(status().isUnauthorized());
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized());
        var login = postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.restrictedToken").doesNotExist()).andReturn();
        String access = jsonMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentVersion").value("1.0"));
    }

    @Test
    void expiredGrantIsRejectedAndRestrictedDeletionRetainsPasswordCheck() throws Exception {
        mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        String expired = recoveryToken();
        jdbc.update("UPDATE consent_recovery_grants SET expires_at = TIMESTAMP '2000-01-01 00:00:00'");
        recover("accept", expired, Map.of("accepted", true, "consentVersion", "1.0"))
                .andExpect(status().isUnauthorized());
        String current = recoveryToken();
        recover("delete", current, Map.of("password", "wrong-password")).andExpect(status().isBadRequest());
        String replacement = recoveryToken();
        recover("delete", replacement, Map.of("password", PASSWORD)).andExpect(status().isNoContent());
        recover("export", replacement, Map.of()).andExpect(status().isUnauthorized());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void competingAcceptanceConsumesOneGrantAndRecordsOneConsent() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", bearer()))
                    .andExpect(status().isNoContent());
            String grant = recoveryToken();
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> accept = () -> {
                ready.countDown();
                assertThat(start.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return recover("accept", grant, Map.of("accepted", true, "consentVersion", "1.0"))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(accept);
            var second = pool.submit(accept);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(204, 401);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM consents c JOIN users u ON u.id = c.user_id
                    WHERE u.email = ? AND c.withdrawn_at IS NULL
                    """, Integer.class, EMAIL)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM users WHERE email = ?", EMAIL);
        }
    }

    private String recoveryToken() throws Exception {
        var result = postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.consentVersion").value("1.0")).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString()).get("restrictedToken").asString();
    }

    private ResultActions recover(String action, String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/consent/" + action).header("Authorization", "Consent " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    // ---------- T34.4 alert opt-outs ----------

    @Test
    void alertsStartOnAndCanBeTurnedOffPerType() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts.budget").value(true))
                .andExpect(jsonPath("$.alerts.bill").value(true))
                .andExpect(jsonPath("$.alerts.goal").value(true));

        mockMvc.perform(put("/api/v1/me/alerts").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("budget", false, "bill", true, "goal", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts.budget").value(false))
                .andExpect(jsonPath("$.alerts.bill").value(true))
                .andExpect(jsonPath("$.alerts.goal").value(false))
                .andExpect(jsonPath("$.language").value("EN"));

        entityManager.flush();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT alert_budget, alert_bill, alert_goal FROM users WHERE email = ?", EMAIL);
        assertThat(row).containsEntry("alert_budget", false).containsEntry("alert_bill", true)
                .containsEntry("alert_goal", false);
    }

    @Test
    void alertSettingsNeedAllThreeFlags() throws Exception {
        mockMvc.perform(put("/api/v1/me/alerts").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("budget", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/v1/me/alerts").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("budget", false, "bill", false, "goal", false))))
                .andExpect(status().isUnauthorized());
    }
}
