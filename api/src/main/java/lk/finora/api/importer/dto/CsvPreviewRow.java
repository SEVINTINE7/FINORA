package lk.finora.api.importer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One data row of the uploaded file. When {@code errors} is empty the parsed fields are filled and the row
 * can be sent back to confirm as-is; otherwise fields that could not be read are null.
 * {@code occurrence} = how many identical rows came before it in the file, plus one (D25).
 */
public record CsvPreviewRow(
        int line,
        LocalDate txnDate,
        String type,
        BigDecimal amount,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        String merchant,
        String note,
        Integer occurrence,
        boolean duplicate,
        List<String> errors) {
}
