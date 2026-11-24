package lk.finora.api.smsformat;

import lk.finora.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
final class AdminSmsFormatController {
    private final SmsFormatService formats;

    AdminSmsFormatController(SmsFormatService formats) {
        this.formats = formats;
    }

    @PatchMapping("/api/v1/admin/sms-formats/NSB/1")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setActive(@RequestBody JsonNode payload) {
        if (!payload.isObject() || payload.size() != 1 || !payload.path("active").isBoolean()) {
            throw ApiException.badRequest("Expected an active boolean");
        }
        formats.setApprovedActive(payload.path("active").asBoolean());
    }
}
