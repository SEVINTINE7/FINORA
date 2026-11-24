package lk.finora.api.smsformat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;

class SmsFormatControllerTest extends IntegrationTest {
    private static final String PATH = "/api/v1/sms-formats";

    @Autowired private JdbcTemplate jdbc;

    private ResultActions fetch(Role role) throws Exception {
        return mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenFor(createUser(role))));
    }

    @Test void userGetsOnlyApprovedActiveSnapshot() throws Exception {
        jdbc.update("INSERT INTO sms_formats(bank_code, version, pattern, active, published) VALUES (?, ?, ?, TRUE, TRUE)",
                "UNAPPROVED", 1, "{}");
        fetch(Role.USER).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.templates.length()").value(3))
                .andExpect(jsonPath("$.templates[0].id").value("nsb-pos-debit"))
                .andExpect(jsonPath("$.templates[1].id").value("nsb-outward-ceft-debit"))
                .andExpect(jsonPath("$.templates[2].id").value("nsb-inward-ceft-credit"));
    }

    @Test void requiresUserAuthenticationAndRole() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        fetch(Role.ADMIN).andExpect(status().isForbidden());
        fetch(Role.STAFF).andExpect(status().isForbidden());
    }

    @Test void disabledBundleReturnsAuthoritativeEmptySnapshot() throws Exception {
        jdbc.update("UPDATE sms_formats SET active = FALSE WHERE bank_code = ?", "NSB");
        fetch(Role.USER).andExpect(status().isOk()).andExpect(jsonPath("$.templates").isEmpty());
    }

    @Test void rejectsTamperedActiveDefinitionWithoutExposingIt() throws Exception {
        jdbc.update("UPDATE sms_formats SET pattern = ? WHERE bank_code = ?", "invalid fixture", "NSB");
        fetch(Role.USER).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SMS_FORMATS_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("SMS formats are unavailable"))
                .andExpect(jsonPath("$.templates").doesNotExist());
    }
}
