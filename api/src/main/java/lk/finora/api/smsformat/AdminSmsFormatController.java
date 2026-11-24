package lk.finora.api.smsformat;

import lk.finora.api.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @org.springframework.web.bind.annotation.GetMapping("/api/v1/admin/sms-formats/NSB/revisions")
    java.util.List<SmsFormatService.Revision> revisions() { return formats.revisions(); }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/admin/sms-formats/NSB/revisions/{revision}")
    @ResponseStatus(HttpStatus.CREATED)
    void draft(@org.springframework.web.bind.annotation.PathVariable int revision) { formats.draft(revision); }

    @DeleteMapping("/api/v1/admin/sms-formats/NSB/revisions/{revision}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discardDraft(@PathVariable int revision) { formats.discardDraft(revision); }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/admin/sms-formats/NSB/revisions/{revision}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void publish(@org.springframework.web.bind.annotation.PathVariable int revision) { formats.publish(revision); }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/admin/sms-formats/NSB/revisions/{revision}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activate(@org.springframework.web.bind.annotation.PathVariable int revision) { formats.activate(revision); }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/admin/sms-formats/NSB/revisions/{revision}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@org.springframework.web.bind.annotation.PathVariable int revision) { formats.deactivate(revision); }

    @PatchMapping("/api/v1/admin/sms-formats/NSB/1")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setActive(@RequestBody JsonNode payload) {
        if (!payload.isObject() || payload.size() != 1 || !payload.path("active").isBoolean()) {
            throw ApiException.badRequest("Expected an active boolean");
        }
        formats.setApprovedActive(payload.path("active").asBoolean());
    }
}
