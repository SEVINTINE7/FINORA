package lk.finora.api.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class ErrorLoggingPrivacyTest {
    @Test
    void unexpectedErrorsDoNotLogExceptionDetailsOrRawUrls(CapturedOutput output) {
        String marker = "synthetic-sensitive-marker";
        var request = new MockHttpServletRequest("GET", "/api/v1/" + marker);
        request.setQueryString("token=" + marker);
        var response = new GlobalExceptionHandler().handleOther(new IllegalStateException(marker), request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Unexpected server error");
        assertThat(output.getAll()).contains("type=IllegalStateException").doesNotContain(marker);
    }
}
