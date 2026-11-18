package lk.finora.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** T04.4: with the dev profile, OpenAPI JSON is served at /v3/api-docs. "test" is last so it keeps the test DB. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "dev", "test" })
class OpenApiDevProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsServedInDev() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FINORA API"));
    }
}
