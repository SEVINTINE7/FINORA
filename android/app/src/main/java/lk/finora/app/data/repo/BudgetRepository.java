package lk.finora.app.data.repo;

import java.io.IOException;
import java.util.List;

import lk.finora.app.data.api.BudgetDto;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public final class BudgetRepository {
    private final ProtectedRequests requests;
    private final HttpUrl endpoint;

    public BudgetRepository(ProtectedRequests requests) {
        this.requests = requests;
        endpoint = requests.origin().newBuilder().addPathSegments("api/v1/budgets").build();
    }

    public Call list(String month) {
        return requests.call(new Request.Builder().url(monthUrl(endpoint, month)).build());
    }

    static HttpUrl monthUrl(HttpUrl endpoint, String month) {
        return endpoint.newBuilder().addQueryParameter("month", month).build();
    }

    public Call get(long id) { return requests.call(item(id).get().build()); }
    public Call create(BudgetDto.Write body) {
        return requests.call(new Request.Builder().url(endpoint).post(AccountRepository.json(body)).build());
    }
    public Call update(long id, BudgetDto.Write body) {
        return requests.call(item(id).put(AccountRepository.json(body)).build());
    }
    public Call delete(long id) { return requests.call(item(id).delete().build()); }

    private Request.Builder item(long id) {
        return new Request.Builder().url(endpoint.newBuilder().addPathSegment(Long.toString(id)).build());
    }

    public static List<BudgetDto> rows(Response response) throws IOException {
        BudgetDto[] rows = AccountRepository.read(response, BudgetDto[].class);
        for (BudgetDto row : rows) validate(row);
        return java.util.Arrays.asList(rows);
    }

    public static BudgetDto readRow(Response response) throws IOException {
        return validate(AccountRepository.read(response, BudgetDto.class));
    }

    static BudgetDto validate(BudgetDto row) throws IOException {
        if (row == null || row.id() <= 0 || row.categoryId() <= 0 || row.month() == null
                || row.limitAmount() == null || row.used() == null || row.remaining() == null
                || !("OK".equals(row.state()) || "NEAR".equals(row.state()) || "OVER".equals(row.state()))) {
            throw new IOException("Invalid budget");
        }
        return row;
    }
}
