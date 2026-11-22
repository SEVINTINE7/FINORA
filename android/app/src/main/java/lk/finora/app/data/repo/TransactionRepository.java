package lk.finora.app.data.repo;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public final class TransactionRepository {
    public record Row(long id, long accountId, Long categoryId, String type, BigDecimal amount,
                      String txnDate, String merchant, String note) {}

    public record Page(List<Row> items, int page, int totalPages) {}

    // Omitting source preserves the original ingestion method on PUT.
    public record Edit(long accountId, Long categoryId, String type, BigDecimal amount,
                       String txnDate, String merchant, String note) {}

    public record Filters(String from, String to, String type, Long categoryId, Long accountId) {}

    private final ProtectedRequests requests;
    private final HttpUrl endpoint;

    public TransactionRepository(ProtectedRequests requests) {
        this.requests = requests;
        endpoint = requests.origin().newBuilder().addPathSegments("api/v1/transactions").build();
    }

    public Call list(Filters filters, int page) {
        return requests.call(new Request.Builder().url(pageUrl(endpoint, filters, page)).build());
    }

    static HttpUrl pageUrl(HttpUrl endpoint, Filters filters, int page) {
        HttpUrl.Builder url = endpoint.newBuilder().addQueryParameter("page", Integer.toString(page));
        if (filters.from() != null) url.addQueryParameter("from", filters.from());
        if (filters.to() != null) url.addQueryParameter("to", filters.to());
        if (filters.type() != null) url.addQueryParameter("type", filters.type());
        if (filters.categoryId() != null) url.addQueryParameter("categoryId", filters.categoryId().toString());
        if (filters.accountId() != null) url.addQueryParameter("accountId", filters.accountId().toString());
        return url.build();
    }

    public Call get(long id) {
        return requests.call(item(id).get().build());
    }

    public Call update(long id, Edit edit) {
        return requests.call(item(id).put(AccountRepository.json(edit)).build());
    }

    public Call delete(long id) {
        return requests.call(item(id).delete().build());
    }

    private Request.Builder item(long id) {
        return new Request.Builder().url(endpoint.newBuilder().addPathSegment(Long.toString(id)).build());
    }

    public static Page readPage(Response response) throws IOException {
        Page page = AccountRepository.read(response, Page.class);
        if (page.items() == null) throw new IOException("Missing transaction list");
        for (Row row : page.items()) validate(row);
        return page;
    }

    public static Row readRow(Response response) throws IOException {
        return validate(AccountRepository.read(response, Row.class));
    }

    private static Row validate(Row row) throws IOException {
        if (row == null || row.id() <= 0 || row.accountId() <= 0 || row.amount() == null
                || row.txnDate() == null || !("INCOME".equals(row.type()) || "EXPENSE".equals(row.type()))) {
            throw new IOException("Invalid transaction");
        }
        return row;
    }
}
