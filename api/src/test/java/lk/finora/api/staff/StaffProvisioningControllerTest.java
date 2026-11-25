package lk.finora.api.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffProvisioningControllerTest extends IntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.persistence.EntityManager entities;
    private static final String STAFF = "/api/v1/admin/staff";
    private static final String ACTIVATION = "/api/v1/auth/staff/activate";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String EMAIL = "activation-fixture@example.test";

    @Test void activationIsSingleUseAndDisableThenEnableDoesNotResurrectSessions() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var request = Map.of("email", EMAIL, "fullName", "Synthetic staff");
        send(STAFF, request, tokenFor(createUser(Role.STAFF))).andExpect(status().isForbidden());
        var result = send(STAFF, request, admin).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        var created = jsonMapper.readTree(result);
        long id = created.get("id").asLong();
        String temporary = created.get("temporaryPassword").asString();
        send(LOGIN, Map.of("email", EMAIL, "password", temporary), null).andExpect(status().isUnauthorized());
        String password = "Synthetic9replacement";
        var activation = Map.of("email", EMAIL, "temporaryPassword", temporary, "password", password);
        send(ACTIVATION, activation, null).andExpect(status().isNoContent());
        send(ACTIVATION, activation, null).andExpect(status().isUnauthorized());
        // MockMvc shares this test's transaction and first-level cache across requests.
        entities.clear();
        var login = send(LOGIN, Map.of("email", EMAIL, "password", password), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF")).andReturn().getResponse().getContentAsString();
        var tokens = jsonMapper.readTree(login);
        var access = tokens.get("accessToken").asString();
        var refresh = tokens.get("refreshToken").asString();
        mockMvc.perform(get("/api/v1/staff/stats").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        send(STAFF, Map.of("email", "escalation-fixture@example.test", "fullName", "Synthetic staff",
                "role", "ADMIN"), access).andExpect(status().isForbidden());
        mockMvc.perform(patch(STAFF + "/" + id).header("Authorization", "Bearer " + access)
                .contentType("application/json").content(json(Map.of("enabled", true, "role", "ADMIN"))))
                .andExpect(status().isForbidden());
        setEnabled(admin, id, false);
        mockMvc.perform(get("/api/v1/staff/stats").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        setEnabled(admin, id, true);
        mockMvc.perform(get("/api/v1/staff/stats").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        send("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null).andExpect(status().isUnauthorized());
    }

    @Test
    void adminReissuesOnlyPendingStaffActivationAndOldCredentialCannotActivate() throws Exception {
        var admin = tokenFor(createUser(Role.ADMIN));
        var ordinaryUser = createUser(Role.USER);
        var activeStaff = createUser(Role.STAFF);
        var provisioned = jsonMapper.readTree(send(STAFF, Map.of("email", EMAIL, "fullName", "Synthetic staff"), admin)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(provisioned.propertyNames()).containsExactlyInAnyOrder("id", "temporaryPassword");
        long id = provisioned.get("id").asLong();
        for (long notPending : new long[]{ordinaryUser.getId(), activeStaff.getId()}) {
            send(STAFF + "/" + notPending + "/activation", Map.of(), admin)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        var replacement = jsonMapper.readTree(send(STAFF + "/" + id + "/activation", Map.of(), admin)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
        assertThat(replacement.propertyNames()).containsExactlyInAnyOrder("id", "temporaryPassword");
        assertThat(replacement.get("id").asLong()).isEqualTo(id);
        String password = "Synthetic9replacement";
        send(ACTIVATION, Map.of("email", EMAIL,
                "temporaryPassword", provisioned.get("temporaryPassword").asString(), "password", password),
                null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        send(ACTIVATION, Map.of("email", EMAIL,
                "temporaryPassword", replacement.get("temporaryPassword").asString(), "password", password),
                null).andExpect(status().isNoContent());
    }

    private void setEnabled(String admin, long id, boolean enabled) throws Exception {
        mockMvc.perform(patch(STAFF + "/" + id).header("Authorization", "Bearer " + admin)
                .contentType("application/json").content(json(Map.of("enabled", enabled))))
                .andExpect(status().isNoContent());
        entities.flush();
        entities.clear();
    }

    private org.springframework.test.web.servlet.ResultActions send(String path, Object value, String token)
            throws Exception {
        var call = post(path).contentType("application/json").content(json(value));
        if (token != null) call.header("Authorization", "Bearer " + token);
        return mockMvc.perform(call);
    }
}
