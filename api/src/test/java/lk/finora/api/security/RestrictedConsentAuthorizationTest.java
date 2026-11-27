package lk.finora.api.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** T52: restricted grants are separate from normal role credentials. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RestrictedConsentAuthorizationTest extends IntegrationTest {
    private static final String AUTH = "/api/v1/auth/";
    private static final String RECOVERY = AUTH + "consent/";
    private static final String PASSWORD = "Fixture-only-9word";
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private record Owner(long id, String email, String marker, String access, String grant) {}
    private record Route(HttpMethod method, String path) {}

    private Owner withdrawnOwner() throws Exception {
        String marker = "ConsentFixture-" + UUID.randomUUID();
        String email = marker.toLowerCase(java.util.Locale.ROOT) + "@test.lk";
        mockMvc.perform(post(AUTH + "register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", PASSWORD, "fullName", "Synthetic consent owner",
                        "language", "EN", "over18", true, "consentVersion", "1.0"))))
                .andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        String access = login(email).get("accessToken").asString();
        jdbc.update("INSERT INTO accounts (user_id,name,type) VALUES (?,?,'CASH')", id, marker);
        mockMvc.perform(post("/api/v1/me/consent/withdraw").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        var restricted = login(email);
        assertFalse(restricted.hasNonNull("accessToken"));
        assertFalse(restricted.hasNonNull("refreshToken"));
        String grant = restricted.get("restrictedToken").asString();
        assertTrue(grant.matches("[A-Za-z0-9_-]{43}"));
        return new Owner(id,email,marker,access,grant);
    }

    private tools.jackson.databind.JsonNode login(String email) throws Exception {
        var result = mockMvc.perform(post(AUTH + "login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email",email,"password",PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return jsonMapper.readTree(result.getContentAsString());
    }

    private List<Route> protectedRoutes() {
        var result = new ArrayList<Route>();
        var authRoutes = new TreeSet<String>();
        for (var mapping : mappings.getHandlerMethods().keySet()) {
            for (String p : mapping.getPatternValues()) {
                if (!p.startsWith("/api/v1/") || p.startsWith("/api/v1/test/") || p.startsWith("/api/v1/_probe")) continue;
                assertFalse(mapping.getMethodsCondition().getMethods().isEmpty(), p);
                for (var m : mapping.getMethodsCondition().getMethods()) {
                    if (p.startsWith(AUTH)) authRoutes.add(m.name() + " " + p);
                    else result.add(new Route(m.asHttpMethod(),p.replace("{month}","2026-11").replaceAll("\\{[^}]+}","1")));
                }
            }
        }
        assertEquals(Set.of("POST " + AUTH + "register", "POST " + AUTH + "login", "POST " + AUTH + "refresh",
                "POST " + AUTH + "password/forgot", "POST " + AUTH + "password/reset",
                "POST " + AUTH + "logout", "POST " + AUTH + "staff/activate", "POST " + RECOVERY + "accept",
                "POST " + RECOVERY + "export", "POST " + RECOVERY + "delete", "POST " + AUTH + "password/forgot", "POST " + AUTH + "password/reset"),authRoutes);
        assertTrue(result.size() >= 40);
        return result;
    }

    private Object state(long id) {
        return List.of(jdbc.queryForList("SELECT * FROM consents WHERE user_id=? ORDER BY id",id),
                jdbc.queryForList("SELECT * FROM accounts WHERE user_id=? ORDER BY id",id),
                jdbc.queryForList("SELECT user_id,token_hash,expires_at FROM consent_recovery_grants WHERE user_id=?",id),
                jdbc.queryForList("SELECT status,staff_session_generation FROM users WHERE id=?",id));
    }

    @Test void restrictedGrantAndWithdrawnAccessCannotReachOrdinaryProtectedRoutes() throws Exception {
        Owner owner = withdrawnOwner();
        Object before = state(owner.id());
        List<Route> routes = protectedRoutes();
        for (Route r : routes) {
            for (String credential : List.of("Consent " + owner.grant(), "Bearer " + owner.grant(), "Bearer " + owner.access())) {
                var response = mockMvc.perform(request(r.method(),r.path()).header("Authorization",credential)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();
                assertEquals(401,response.getStatus(),r.method() + " " + r.path());
                assertEquals("UNAUTHORIZED",jsonMapper.readTree(response.getContentAsString()).get("code").asString());
            }
        }
        assertTrue(before.equals(state(owner.id())),"Denied routes must leave owner state unchanged");
        export(owner,owner.id());
        System.out.printf("T52_CONSENT_BOUNDARY: %d protected mappings, %d denied probes, 10 auth mappings inventoried%n",routes.size(),routes.size()*3);
    }

    @Test void normalRolesAndInvalidCredentialsCannotUseRecoveryEndpoints() throws Exception {
        Owner owner = withdrawnOwner();
        var disabled = createUser(Role.USER);
        String disabledToken = tokenFor(disabled);
        disabled.setStatus(UserStatus.DISABLED);
        userRepository.saveAndFlush(disabled);
        var headers = List.of("", "Consent malformed", "Consent " + "A".repeat(43), "Bearer " + owner.grant(),
                "Bearer " + owner.access(), "Bearer " + tokenFor(createUser(Role.USER)),
                "Bearer " + tokenFor(createUser(Role.STAFF)), "Bearer " + tokenFor(createUser(Role.ADMIN)));
        Object before = state(owner.id());
        for (String action : List.of("accept","export","delete")) {
            Object body = switch(action) {
                case "accept" -> Map.of("accepted",true,"consentVersion","1.0");
                case "delete" -> Map.of("password",PASSWORD);
                default -> Map.of();
            };
            for (String credential : headers) {
                var req = post(RECOVERY + action).contentType(MediaType.APPLICATION_JSON).content(json(body));
                if (!credential.isEmpty()) req.header("Authorization",credential);
                mockMvc.perform(req).andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("INVALID_CONSENT_GRANT"));
            }
            mockMvc.perform(post(RECOVERY + action).contentType(MediaType.APPLICATION_JSON).content(json(body))
                    .header("Authorization","Bearer " + disabledToken)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        }
        assertTrue(before.equals(state(owner.id())),"Invalid credentials must not change owner state");
        export(owner,owner.id());
        System.out.println("T52_CONSENT_RECOVERY: 27 invalid/disabled credential probes denied");
    }

    private String export(Owner owner,long requestedId) throws Exception {
        return mockMvc.perform(post(RECOVERY + "export").header("Authorization","Consent " + owner.grant())
                .param("userId",Long.toString(requestedId)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.profile.id").value(owner.id()))
                .andExpect(jsonPath("$.data.accounts.length()").value(1))
                .andExpect(jsonPath("$.data.accounts[0].name").value(owner.marker()))
                .andReturn().getResponse().getContentAsString();
    }

    @Test void twoRestrictedExportsAreBoundToGrantOwnersNotRequestedIds() throws Exception {
        Owner a = withdrawnOwner();
        Owner b = withdrawnOwner();
        Object beforeB = state(b.id());
        String first = export(a,b.id());
        assertFalse(first.contains(b.marker()) || first.contains(b.email()) || first.contains(a.grant()) || first.contains(b.grant()));
        assertTrue(beforeB.equals(state(b.id())),"Other user's state must remain unchanged");
        String second = export(b,a.id());
        assertFalse(second.contains(a.marker()) || second.contains(a.email()) || second.contains(a.grant()) || second.contains(b.grant()));
        for (Owner owner : List.of(a,b)) {
            mockMvc.perform(post(RECOVERY + "export").header("Authorization","Consent " + owner.grant()))
                    .andExpect(status().isUnauthorized());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM consent_recovery_grants WHERE user_id=?",Integer.class,owner.id()));
        }
        System.out.println("T52_CONSENT_EXPORT: two owners isolated, foreign userId ignored, both grants consumed");
    }
}
