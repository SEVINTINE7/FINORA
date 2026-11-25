package lk.finora.api.user;

import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.finora.api.export.ExportService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/consent")
final class ConsentRecoveryController {
    private final ConsentRecoveryService recovery;

    ConsentRecoveryController(ConsentRecoveryService recovery) { this.recovery = recovery; }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@RequestHeader(value = "Authorization", required = false) String grant,
            @Valid @RequestBody Acceptance request) {
        recovery.accept(grant, request.consentVersion(), request.accepted());
    }

    @PostMapping("/export")
    ResponseEntity<ExportService.Snapshot> export(
            @RequestHeader(value = "Authorization", required = false) String grant) {
        var filename = "finora-export-" + LocalDate.now(ZoneId.of("Asia/Colombo")) + ".json";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(recovery.export(grant));
    }

    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestHeader(value = "Authorization", required = false) String grant,
            @Valid @RequestBody AccountDeletionController.Confirmation confirmation) {
        recovery.delete(grant, confirmation.password());
    }

    record Acceptance(@NotBlank @Size(max = 10) String consentVersion, boolean accepted) {}
}
