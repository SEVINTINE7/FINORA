package lk.finora.api.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for API integration tests: full Spring context, the D11 MySQL schema {@code finora_test}
 * (test profile) and {@link MockMvc}. Each test runs in a transaction that is rolled back.
 *
 * <pre>
 * class CategoryControllerTest extends IntegrationTest {
 *     &#64;Test void list() throws Exception {
 *         mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
 *     }
 * }
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    /** Serialises a request DTO for {@code .content(...)}. */
    protected String json(Object body) {
        return jsonMapper.writeValueAsString(body);
    }
}
