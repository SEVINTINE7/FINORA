package lk.finora.api.support;

import java.util.List;
import lk.finora.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
final class SmsDiagnosticController {
    private final SmsDiagnosticService diagnostics;

    SmsDiagnosticController(SmsDiagnosticService diagnostics) {
        this.diagnostics = diagnostics;
    }

    @PostMapping("/api/v1/support/sms-diagnostics")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void submit(@RequestBody JsonNode payload) {
        diagnostics.submit(CurrentUser.id(), payload);
    }

    @GetMapping("/api/v1/staff/sms-diagnostics")
    List<SmsDiagnosticService.Group> groups() {
        return diagnostics.groups();
    }

    @PostMapping("/api/v1/staff/sms-diagnostics/escalations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void flag(@RequestBody JsonNode payload) {
        diagnostics.flag(payload);
    }

    @GetMapping("/api/v1/admin/sms-diagnostics/escalations")
    List<SmsDiagnosticService.Escalation> escalations() {
        return diagnostics.escalations();
    }

    @PatchMapping("/api/v1/admin/sms-diagnostics/escalations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void advance(@PathVariable long id, @RequestBody JsonNode payload) {
        diagnostics.advance(id, payload);
    }
}
