package lk.finora.api.export;

import java.time.LocalDate;
import java.time.ZoneId;
import lk.finora.api.security.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class ExportController {
    private final ExportService exports;

    ExportController(ExportService exports) { this.exports = exports; }

    @GetMapping("/api/v1/me/export")
    ResponseEntity<ExportService.Snapshot> export() {
        var filename = "finora-export-" + LocalDate.now(ZoneId.of("Asia/Colombo")) + ".json";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(exports.read(CurrentUser.id()));
    }
}
