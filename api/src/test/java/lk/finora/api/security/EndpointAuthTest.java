package lk.finora.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;
import lk.finora.api.user.UserStatus;

/**
 * T50.1: every real {@code /api/v1} endpoint rejects a missing token (401 UNAUTHORIZED) and the wrong
 * role (403 FORBIDDEN). Endpoints are read from Spring's mapping registry, so new controllers are
 * checked automatically. Rule 4: Admin and Staff never reach user data endpoints.
 */
class EndpointAuthTest extends IntegrationTest {

    private static final String API = "/api/v1/";

    /** Test-only controllers (support/TestOwnershipController, ApiFoundationTest probes). */
    private static final List<String> TEST_ONLY_PREFIXES = List.of(API + "test/", API + "_probe");

    /** Lower bound so a broken discovery cannot make this test pass by checking nothing. */
    private static final int MIN_PROTECTED_ENDPOINTS = 40;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private User user;
    private User staff;
    private User admin;

    record Endpoint(HttpMethod method, String pattern) {

        /** Path with every {variable} replaced by 1. */
        String url() {
            return pattern.replaceAll("\\{[^}]+}", "1");
        }

        Role requiredRole() {
            if (pattern.startsWith(API + "admin/")) {
                return Role.ADMIN;
            }
            if (pattern.startsWith(API + "staff/")) {
                return Role.STAFF;
            }
            return Role.USER;
        }

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }

    @BeforeEach
    void setUp() {
        user = createUser(Role.USER);
        staff = createUser(Role.STAFF);
        admin = createUser(Role.ADMIN);
    }

    /** Every non-public /api/v1 endpoint, one entry per HTTP method. */
    private List<Endpoint> protectedEndpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPatternValues();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (!pattern.startsWith(API) || pattern.startsWith(API + "auth/")
                        || TEST_ONLY_PREFIXES.stream().anyMatch(pattern::startsWith)) {
                    continue;
                }
                assertThat(methods).as("%s must declare its HTTP method", pattern).isNotEmpty();
                for (RequestMethod method : methods) {
                    endpoints.add(new Endpoint(method.asHttpMethod(), pattern));
                }
            }
        }
        return endpoints;
    }

    private MockHttpServletResponse call(Endpoint endpoint, User caller) throws Exception {
        var builder = request(endpoint.method(), endpoint.url())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (caller != null) {
            builder.header("Authorization", "Bearer " + tokenFor(caller));
        }
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    private static void expectError(SoftAssertions soft, Endpoint endpoint, String who,
            MockHttpServletResponse response, int status, String code) throws Exception {
        soft.assertThat(response.getStatus()).as("%s as %s: status", endpoint, who).isEqualTo(status);
        soft.assertThat(response.getContentAsString()).as("%s as %s: body", endpoint, who)
                .contains("\"code\":\"" + code + "\"");
    }

    @Test
    void discoveryFindsAllControllers() {
        List<Endpoint> endpoints = protectedEndpoints();
        assertThat(endpoints).hasSizeGreaterThanOrEqualTo(MIN_PROTECTED_ENDPOINTS);
        assertThat(endpoints).map(Endpoint::pattern).contains(
                "/api/v1/accounts", "/api/v1/categories", "/api/v1/transactions", "/api/v1/budgets",
                "/api/v1/bills", "/api/v1/goals", "/api/v1/dashboard", "/api/v1/insights", "/api/v1/me");
    }

    @Test
    void noTokenGives401Everywhere() throws Exception {
        SoftAssertions soft = new SoftAssertions();
        for (Endpoint endpoint : protectedEndpoints()) {
            expectError(soft, endpoint, "anonymous", call(endpoint, null), 401, "UNAUTHORIZED");
        }
        soft.assertAll();
    }

    @Test
    void badTokenGives401Everywhere() throws Exception {
        SoftAssertions soft = new SoftAssertions();
        for (Endpoint endpoint : protectedEndpoints()) {
            var response = mockMvc.perform(request(endpoint.method(), endpoint.url())
                    .header("Authorization", "Bearer not.a.jwt")).andReturn().getResponse();
            expectError(soft, endpoint, "bad token", response, 401, "UNAUTHORIZED");
        }
        soft.assertAll();
    }

    @Test
    void wrongRoleGives403Everywhere() throws Exception {
        SoftAssertions soft = new SoftAssertions();
        for (Endpoint endpoint : protectedEndpoints()) {
            for (User caller : List.of(user, staff, admin)) {
                if (caller.getRole() == endpoint.requiredRole()) {
                    continue;
                }
                expectError(soft, endpoint, caller.getRole().name(), call(endpoint, caller), 403, "FORBIDDEN");
            }
        }
        soft.assertAll();
    }

    @Test
    void authEndpointsArePublic() throws Exception {
        // No token: validation runs (400), security does not block (not 401/403)
        for (String path : List.of("register", "login", "refresh", "logout")) {
            mockMvc.perform(post(API + "auth/" + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        // Logout with an unknown token is still 204 (T05)
        mockMvc.perform(post(API + "auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"unknown\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void disabledAccountsReturn403Everywhere() throws Exception {
        List<User> accounts = List.of(
                createUser(Role.USER),
                createUser(Role.STAFF),
                createUser(Role.ADMIN));

        // Issue JWTs while accounts are still active.
        List<String> tokens = accounts.stream()
                .map(this::tokenFor).toList();

        for (User account : accounts) {
            account.setStatus(UserStatus.DISABLED);
            userRepository.save(account);
        }

        SoftAssertions soft = new SoftAssertions();
        for (Endpoint endpoint : protectedEndpoints()) {
            for (String token : tokens) {
                var response = mockMvc.perform(
                        request(endpoint.method(), endpoint.url())
                                .header("Authorization", "Bearer " + token))
                        .andReturn().getResponse();

                expectError(soft, endpoint, "disabled account",
                        response, 403, "ACCOUNT_DISABLED");
            }
        }
        soft.assertAll();
    }

}
