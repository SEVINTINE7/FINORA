package lk.finora.api.importer;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lk.finora.api.importer.dto.CsvConfirmRequest;
import lk.finora.api.importer.dto.CsvConfirmResponse;
import lk.finora.api.importer.dto.CsvPreviewResponse;
import lk.finora.api.security.CurrentUser;

/** CSV import of income/expense records (T12). USER role only. Template: docs/csv-template.md. */
@RestController
@RequestMapping("/api/v1/imports/csv")
public class CsvImportController {

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    /** multipart/form-data with one part named {@code file}. Nothing is saved. */
    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvPreviewResponse preview(@RequestParam("file") MultipartFile file) {
        return csvImportService.preview(CurrentUser.id(), file);
    }

    /** The preview rows the user selected, sent back as JSON. */
    @PostMapping("/confirm")
    public CsvConfirmResponse confirm(@Valid @RequestBody CsvConfirmRequest request) {
        return csvImportService.confirm(CurrentUser.id(), request);
    }
}
