package lk.finora.api.common.error;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lk.finora.api.support.IntegrationTest;

/** T04: error JSON, public health endpoint, security defaults, CORS off, OpenAPI off outside dev. */
@Import(ApiFoundationTest.ProbeController.class)
class ApiFoundationTest extends IntegrationTest {

    /** Test-only endpoints (nested in a test class, so never component-scanned into the app). */
    @RestController
    static class ProbeController {

        record ProbeRequest(@NotBlank String name, @NotNull @DecimalMin("0.01") BigDecimal amount) {
        }

        @PostMapping("/api/v1/_probe")
        Map<String, String> create(@Valid @RequestBody ProbeRequest body) {
            return Map.of("name", body.name());
        }

        @GetMapping("/api/v1/_probe/{id}")
        Map<String, String> find(@PathVariable long id) {
            throw ApiException.notFound("Probe not found");
        }

        @GetMapping("/api/v1/_probe/boom")
        Map<String, String> boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    @Test
    @WithMockUser
    void invalidDtoReturns400ValidationErrorWithFieldList() throws Exception {
        mockMvc.perform(post("/api/v1/_probe").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "", "amount", "0"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", matchesPattern("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/v1/_probe"))
                .andExpect(jsonPath("$.fields", hasSize(2)))
                .andExpect(jsonPath("$.fields[*].field", containsInAnyOrder("name", "amount")));
    }

    @Test
    @WithMockUser
    void validDtoPasses() throws Exception {
        mockMvc.perform(post("/api/v1/_probe").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Lunch", "amount", "450.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lunch"));
    }

    @Test
    @WithMockUser
    void malformedJsonReturns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/_probe").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @WithMockUser
    void unknownPathReturns404ErrorJson() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"))
                .andExpect(jsonPath("$.fields").doesNotExist());
    }

    @Test
    @WithMockUser
    void apiExceptionReturnsItsCodeAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/_probe/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Probe not found"));
    }

    @Test
    @WithMockUser
    void wrongPathVariableTypeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/_probe/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields[0].field").value("id"));
    }

    @Test
    @WithMockUser
    void unexpectedErrorReturns500WithoutDetails() throws Exception {
        mockMvc.perform(get("/api/v1/_probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedPathWithoutLoginReturns401ErrorJson() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"));
    }

    @Test
    void corsPreflightIsNotHonoured() throws Exception {
        mockMvc.perform(options("/actuator/health")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void openApiDocsDisabledOutsideDev() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
