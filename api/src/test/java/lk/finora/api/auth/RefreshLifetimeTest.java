package lk.finora.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import lk.finora.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** D07/T53: issuance duration and rotation/expiry on disposable, rolled-back accounts. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RefreshLifetimeTest extends IntegrationTest {
    private static final String AUTH = "/api/v1/auth/";
    private static final String PASSWORD = "Lifetime-Fixture9";
    private static final Duration TTL = Duration.ofDays(14);
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private String register() throws Exception {
        String email = "t53-lifetime-" + UUID.randomUUID() + "@example.test";
        postJson("register", Map.of("email", email, "password", PASSWORD,
                "fullName", "Synthetic lifetime fixture", "language", "EN", "over18", true,
                "consentVersion", "1.0")).andExpect(status().isCreated());
        return email;
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(AUTH + path).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private String refreshToken(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("refreshToken").asString();
    }

    private String login(String email) throws Exception {
        return refreshToken(postJson("login", Map.of("email", email, "password", PASSWORD)));
    }

    private ResultActions refresh(String token) throws Exception {
        return postJson("refresh", Map.of("refreshToken", token));
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private void assertLifetime(String token, Instant before, Instant after) throws Exception {
        Instant expiry = jdbc.queryForObject("SELECT expires_at FROM refresh_tokens WHERE token_hash = ?",
                (rs, row) -> rs.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC), hash(token));
        // DATETIME(6) has microsecond precision; bracket the real issuance clock.
        assertThat(expiry).isBetween(before.plus(TTL).minusMillis(1), after.plus(TTL).plusMillis(1));
    }

    @Test void loginAndRotationIssueFourteenDayRefreshTokens() throws Exception {
        String email = register();
        Instant before = Instant.now();
        String first = login(email);
        assertLifetime(first, before, Instant.now());
        before = Instant.now();
        String next = refreshToken(refresh(first));
        assertLifetime(next, before, Instant.now());
        assertThat(next.equals(first)).isFalse();
        refresh(first).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test void futureExpiryAllowsRotationButReachedExpiryDoesNotMintTokens() throws Exception {
        String email = register();
        String first = login(email);
        long owner = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(jdbc.update("UPDATE refresh_tokens SET expires_at = UTC_TIMESTAMP(6) + INTERVAL 60 SECOND "
                + "WHERE token_hash = ?", hash(first))).isEqualTo(1);
        entityManager.clear();
        String next = refreshToken(refresh(first));
        assertThat(jdbc.update("UPDATE refresh_tokens SET expires_at = UTC_TIMESTAMP(6) WHERE token_hash = ?",
                hash(next))).isEqualTo(1);
        entityManager.clear();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ? "
                + "AND expires_at <= UTC_TIMESTAMP(6) AND revoked_at IS NULL", Integer.class, hash(next))).isEqualTo(1);
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, owner);
        refresh(next).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, owner))
                .isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ? AND revoked_at IS NULL",
                Integer.class, hash(next))).isEqualTo(1);
    }
}
