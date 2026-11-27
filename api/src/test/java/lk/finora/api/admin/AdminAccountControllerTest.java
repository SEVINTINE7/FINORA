package lk.finora.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import lk.finora.api.common.error.ApiException;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.persistence.EntityManager;

/** T59 (D62): ADMIN-only creation, listing and enable/disable of other admins. */
@ExtendWith(OutputCaptureExtension.class)
class AdminAccountControllerTest extends IntegrationTest {
    private static final String PATH = "/api/v1/admin/admins";
    private static final String PASSWORD = "Synthetic9-admin-pass";

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired AdminAccountService service;

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        var request = post(PATH).contentType(MediaType.APPLICATION_JSON).content(json(body));
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions setEnabled(String token, long id, Object body) throws Exception {
        return mockMvc.perform(patch(PATH + "/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private static Map<String, Object> newAdmin(String email, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("fullName", "Synthetic Admin");
        body.put("password", password);
        return body;
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("accessToken").asString();
    }

    @Test
    void adminCreatesAnAdminWhoCanSignInAndUseAdminRoutes(CapturedOutput output) throws Exception {
        var creator = createUser(Role.ADMIN);
        String body = create(tokenFor(creator), newAdmin("New.Admin@Example.test", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.email").value("new.admin@example.test"))
                .andExpect(jsonPath("$.fullName").value("Synthetic Admin"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD).doesNotContainIgnoringCase("password");
        long id = jsonMapper.readTree(body).get("id").asLong();

        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id)).isEqualTo("ADMIN");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id))
                .startsWith("$2").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForMap("""
                SELECT actor_id, target_type, reason FROM audit_events
                WHERE action = 'ADMIN_CREATED' AND target_id = ?
                """, id)).containsEntry("actor_id", creator.getId()).containsEntry("target_type", "ADMIN")
                .containsEntry("reason", null);

        String access = login("new.admin@example.test", PASSWORD);
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + access)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].email").value("new.admin@example.test"));
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @Test
    void onlyAdminsReachTheRoutes() throws Exception {
        var target = createUser(Role.ADMIN);
        create(null, newAdmin("x1@example.test", PASSWORD)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        for (Role role : new Role[]{Role.USER, Role.STAFF}) {
            String token = tokenFor(createUser(role));
            create(token, newAdmin("x2@example.test", PASSWORD)).andExpect(status().isForbidden());
            mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            setEnabled(token, target.getId(), Map.of("enabled", false)).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE 'x_@example.test'",
                Integer.class)).isZero();
    }

    @Test
    void rejectsWeakPasswordsBadInputAndDuplicateEmails() throws Exception {
        String token = tokenFor(createUser(Role.ADMIN));
        for (String weak : new String[]{"short1", "lettersonly", "12345678"}) {
            create(token, newAdmin("weak@example.test", weak)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        create(token, newAdmin("not-an-email", PASSWORD)).andExpect(status().isBadRequest());
        var user = createUser(Role.USER);
        create(token, newAdmin(user.getEmail().toUpperCase(), PASSWORD)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, user.getId()))
                .isEqualTo("USER");
    }

    @Test
    void cannotDisableYourselfAndBodyMustOnlyHoldEnabled() throws Exception {
        var admin = createUser(Role.ADMIN);
        var other = createUser(Role.ADMIN);
        String token = tokenFor(admin);
        setEnabled(token, admin.getId(), Map.of("enabled", false)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        setEnabled(token, other.getId(), Map.of("enabled", false, "role", "USER")).andExpect(status().isBadRequest());
        setEnabled(token, other.getId(), Map.of("enabled", "no")).andExpect(status().isBadRequest());
        setEnabled(token, createUser(Role.STAFF).getId(), Map.of("enabled", false)).andExpect(status().isNotFound());
        setEnabled(token, 999_999_999L, Map.of("enabled", false)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, admin.getId()))
                .isEqualTo("ACTIVE");
    }

    @Test
    void lastActiveAdminCannotBeDisabledEvenByARacingAdmin() {
        var first = createUser(Role.ADMIN);
        var second = createUser(Role.ADMIN);
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE role = 'ADMIN' AND id NOT IN (?, ?)",
                first.getId(), second.getId());
        service.setEnabled(first.getId(), second.getId(), false);
        // second's request arrives after it was disabled: it must not take out the last active admin
        assertThatThrownBy(() -> service.setEnabled(second.getId(), first.getId(), false))
                .isInstanceOf(ApiException.class).hasMessageContaining("no longer active");
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, first.getId()))
                .isEqualTo("ACTIVE");
    }

    @Test
    void disablingEndsSessionsAndReEnablingNeedsAFreshSignIn() throws Exception {
        String actor = tokenFor(createUser(Role.ADMIN));
        long id = jsonMapper.readTree(create(actor, newAdmin("rotating.admin@example.test", PASSWORD))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "rotating.admin@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = jsonMapper.readTree(login).get("accessToken").asString();
        String refresh = jsonMapper.readTree(login).get("refreshToken").asString();

        setEnabled(actor, id, Map.of("enabled", false)).andExpect(status().isNoContent());
        entities.clear();
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refresh)))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + actor))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].enabled").value(false));

        setEnabled(actor, id, Map.of("enabled", true)).andExpect(status().isNoContent());
        entities.clear();
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        login("rotating.admin@example.test", PASSWORD);
        assertThat(jdbc.queryForList("""
                SELECT action FROM audit_events WHERE target_type = 'ADMIN' AND target_id = ? ORDER BY id
                """, String.class, id)).containsExactly("ADMIN_CREATED", "ADMIN_DISABLED", "ADMIN_ENABLED");
    }
}
