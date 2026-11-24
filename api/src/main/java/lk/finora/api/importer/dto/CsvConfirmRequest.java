package lk.finora.api.importer.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/imports/csv/confirm: the selected preview rows (at most 2000). */
public record CsvConfirmRequest(
        @NotEmpty @Size(max = 2000) @Valid
        List<CsvConfirmRow> rows) {
}
