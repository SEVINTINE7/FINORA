package lk.finora.app.data.repo;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class CsvImportRepository {
    public record Row(int line, String txnDate, String type, BigDecimal amount, Long accountId,
                      String accountName, Long categoryId, String categoryName, String merchant,
                      String note, Integer occurrence, boolean duplicate, List<String> errors) {
        public boolean selectable() {
            return !duplicate && errors != null && errors.isEmpty() && line > 0 && occurrence != null
                    && occurrence > 0 && accountId != null && accountId > 0 && amount != null
                    && amount.signum() > 0 && txnDate != null && ("INCOME".equals(type) || "EXPENSE".equals(type));
        }
    }

    public record Preview(int totalRows, int validRows, int errorRows, int duplicateRows, List<Row> rows) {}
    public record Result(Integer saved, List<Integer> skippedDuplicateLines) {}
    private record ConfirmRow(int line, String txnDate, String type, BigDecimal amount,
                              Long accountId, Long categoryId, String merchant, String note, Integer occurrence) {}
    private record Confirmation(List<ConfirmRow> rows) {}

    private final ProtectedRequests requests;
    private final HttpUrl endpoint;

    public CsvImportRepository(ProtectedRequests requests) {
        this.requests = requests;
        endpoint = requests.origin().newBuilder().addPathSegments("api/v1/imports/csv").build();
    }

    public Call preview(byte[] bytes) {
        return requests.call(previewRequest(endpoint, bytes));
    }

    static Request previewRequest(HttpUrl endpoint, byte[] bytes) {
        RequestBody file = RequestBody.create(bytes, MediaType.get("text/csv"));
        return new Request.Builder().url(endpoint.newBuilder().addPathSegment("preview").build())
                .post(new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("file", "transactions.csv", file).build()).build();
    }

    public Call confirm(List<Row> rows) {
        return requests.call(confirmRequest(endpoint, rows));
    }

    static Request confirmRequest(HttpUrl endpoint, List<Row> rows) {
        if (rows.isEmpty() || rows.size() > 2000 || rows.stream().anyMatch(row -> !row.selectable())) {
            throw new IllegalArgumentException("Only eligible preview rows can be confirmed");
        }
        var payload = new Confirmation(rows.stream().map(row -> new ConfirmRow(row.line(), row.txnDate(),
                row.type(), row.amount(), row.accountId(), row.categoryId(), row.merchant(), row.note(),
                row.occurrence())).collect(java.util.stream.Collectors.toList()));
        return new Request.Builder().url(endpoint.newBuilder().addPathSegment("confirm").build())
                .post(AccountRepository.json(payload)).build();
    }

    public static Preview readPreview(Response response) throws IOException {
        Preview preview = AccountRepository.read(response, Preview.class);
        if (preview.rows() == null || preview.rows().isEmpty() || preview.rows().size() > 2000
                || preview.totalRows() != preview.rows().size()) throw new IOException("Invalid preview");
        var lines = new java.util.HashSet<Integer>();
        for (Row row : preview.rows()) {
            if (row == null || row.line() < 1 || row.errors() == null || !lines.add(row.line())
                    || (!row.duplicate() && row.errors().isEmpty() && !row.selectable())) {
                throw new IOException("Invalid preview row");
            }
        }
        return preview;
    }

    public static Result readResult(Response response, int submitted) throws IOException {
        Result result = AccountRepository.read(response, Result.class);
        if (result.saved() == null || result.saved() < 0 || result.skippedDuplicateLines() == null
                || result.saved() + result.skippedDuplicateLines().size() != submitted) {
            throw new IOException("Invalid confirmation result");
        }
        return result;
    }
}
