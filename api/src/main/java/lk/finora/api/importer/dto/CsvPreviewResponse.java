package lk.finora.api.importer.dto;

import java.util.List;

/** Result of a CSV preview. Nothing has been saved. */
public record CsvPreviewResponse(
        int totalRows,
        int validRows,
        int errorRows,
        int duplicateRows,
        List<CsvPreviewRow> rows) {
}
