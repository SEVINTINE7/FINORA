package lk.finora.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** T34.1: POST / DELETE /api/v1/devices — ownership, idempotency, validation, roles. */
class DeviceControllerTest extends IntegrationTest {

    private static final String TOKEN = "synthetic-fcm:token_A-1";

    @Autowired
    private JdbcTemplate jdbc;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = createUser(Role.USER);
        userB = createUser(Role.USER);
    }

    private ResultActions send(User user, MockHttpServletRequestBuilder request, String token) throws Exception {
        if (user != null) {
            request.header("Authorization", "Bearer " + tokenFor(user));
        }
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", token))));
    }

    private Long ownerOf(String token) {
        return jdbc.query("SELECT user_id FROM device_tokens WHERE token = ?",
                rs -> rs.next() ? rs.getLong(1) : null, token);
    }

    private int count(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM device_tokens WHERE user_id = ?", Integer.class, user.getId());
    }

    @Test
    void registerTwiceKeepsOneRow() throws Exception {
        send(userA, post("/api/v1/devices"), TOKEN).andExpect(status().isNoContent());
        send(userA, post("/api/v1/devices"), TOKEN).andExpect(status().isNoContent());
        assertThat(count(userA)).isEqualTo(1);
        assertThat(ownerOf(TOKEN)).isEqualTo(userA.getId());
    }

    @Test
    void tokenMovesWhenAnotherAccountSignsInOnThePhone() throws Exception {
        send(userA, post("/api/v1/devices"), TOKEN).andExpect(status().isNoContent());
        send(userB, post("/api/v1/devices"), TOKEN).andExpect(status().isNoContent());
        assertThat(ownerOf(TOKEN)).isEqualTo(userB.getId());
        assertThat(count(userA)).isZero();
    }

    @Test
    void deleteOwnTokenButAnotherUsersTokenIs404() throws Exception {
        send(userA, post("/api/v1/devices"), TOKEN).andExpect(status().isNoContent());
        send(userB, delete("/api/v1/devices"), TOKEN)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(ownerOf(TOKEN)).isEqualTo(userA.getId());

        send(userA, delete("/api/v1/devices"), TOKEN).andExpect(status().isNoContent());
        assertThat(ownerOf(TOKEN)).isNull();
        send(userA, delete("/api/v1/devices"), TOKEN).andExpect(status().isNotFound());
    }

    @Test
    void keepsOnlyTheNewestTenTokens() throws Exception {
        for (int i = 1; i <= DeviceService.MAX_DEVICES + 2; i++) {
            send(userA, post("/api/v1/devices"), "synthetic-token-" + i).andExpect(status().isNoContent());
        }
        assertThat(count(userA)).isEqualTo(DeviceService.MAX_DEVICES);
        assertThat(ownerOf("synthetic-token-1")).isNull();
        assertThat(ownerOf("synthetic-token-2")).isNull();
        assertThat(ownerOf("synthetic-token-12")).isEqualTo(userA.getId());
    }

    @Test
    void invalidTokensGive400() throws Exception {
        send(userA, post("/api/v1/devices"), "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(userA, post("/api/v1/devices"), "has space").andExpect(status().isBadRequest());
        send(userA, post("/api/v1/devices"), "x".repeat(256)).andExpect(status().isBadRequest());
        assertThat(count(userA)).isZero();
    }

    @Test
    void onlyUsersMayRegister() throws Exception {
        send(null, post("/api/v1/devices"), TOKEN).andExpect(status().isUnauthorized());
        send(createUser(Role.STAFF), post("/api/v1/devices"), TOKEN).andExpect(status().isForbidden());
        send(createUser(Role.ADMIN), post("/api/v1/devices"), TOKEN).andExpect(status().isForbidden());
        assertThat(ownerOf(TOKEN)).isNull();
    }

    @Test
    void privilegedRequestsCannotMoveOrRemoveAnAlreadyRegisteredToken() throws Exception {
        var created = send(userA, post("/api/v1/devices"), TOKEN).andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(created.getContentAsByteArray()).isEmpty();
        for (Role role : java.util.List.of(Role.STAFF, Role.ADMIN)) {
            var operator = createUser(role);
            for (var request : java.util.List.of(post("/api/v1/devices"), delete("/api/v1/devices"))) {
                var denied = send(operator, request, TOKEN).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN")).andReturn().getResponse();
                assertThat(jsonMapper.readTree(denied.getContentAsString()).propertyNames())
                        .containsExactlyInAnyOrder("timestamp", "status", "code", "message", "path");
                assertThat(denied.getContentAsString()).doesNotContain(TOKEN, userA.getEmail());
                assertThat(ownerOf(TOKEN)).isEqualTo(userA.getId());
                assertThat(count(operator)).isZero();
            }
        }
        var removed = send(userA, delete("/api/v1/devices"), TOKEN).andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(removed.getContentAsByteArray()).isEmpty();
    }
}
