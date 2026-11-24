package lk.finora.api.importer.dto;

import java.util.List;

/** How many rows were saved, and which file lines were skipped because they were already imported. */
public record CsvConfirmResponse(
        int saved,
        List<Integer> skippedDuplicateLines) {
}
