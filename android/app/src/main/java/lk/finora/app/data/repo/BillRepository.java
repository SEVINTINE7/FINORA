package lk.finora.app.data.repo;

import java.io.IOException;
import java.util.List;

import lk.finora.app.data.api.BillDto;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class BillRepository {
    private final ProtectedRequests requests;
    private final HttpUrl endpoint;

    public BillRepository(ProtectedRequests requests) {
        this.requests = requests;
        endpoint = requests.origin().newBuilder().addPathSegments("api/v1/bills").build();
    }

    public Call list(String month) {
        return requests.call(new Request.Builder().url(monthUrl(endpoint, month)).build());
    }

    public Call summary(String month) {
        return requests.call(new Request.Builder()
                .url(monthUrl(endpoint.newBuilder().addPathSegment("summary").build(), month)).build());
    }

    public Call get(long id) { return requests.call(item(id).get().build()); }
    public Call create(BillDto.Write body) {
        return requests.call(new Request.Builder().url(endpoint).post(AccountRepository.json(body)).build());
    }
    public Call update(long id, BillDto.Write body) {
        return requests.call(item(id).put(AccountRepository.json(body)).build());
    }
    public Call delete(long id) { return requests.call(item(id).delete().build()); }

    /** POST marks the bill paid for that month (409 if it already is); DELETE marks it unpaid. */
    public Call setPaid(long id, String month, boolean paid) {
        Request.Builder request = new Request.Builder().url(paidUrl(endpoint, id, month));
        return requests.call((paid ? request.post(RequestBody.create(new byte[0])) : request.delete()).build());
    }

    static HttpUrl monthUrl(HttpUrl url, String month) {
        return url.newBuilder().addQueryParameter("month", month).build();
    }

    static HttpUrl paidUrl(HttpUrl endpoint, long id, String month) {
        return monthUrl(endpoint.newBuilder().addPathSegment(Long.toString(id)).addPathSegment("paid").build(),
                month);
    }

    private Request.Builder item(long id) {
        return new Request.Builder().url(endpoint.newBuilder().addPathSegment(Long.toString(id)).build());
    }

    public static List<BillDto> rows(Response response) throws IOException {
        BillDto[] rows = AccountRepository.read(response, BillDto[].class);
        for (BillDto row : rows) validate(row);
        return java.util.Arrays.asList(rows);
    }

    public static BillDto.Saved readSaved(Response response) throws IOException {
        BillDto.Saved saved = AccountRepository.read(response, BillDto.Saved.class);
        if (saved.id() <= 0 || saved.name() == null || saved.amount() == null
                || saved.dueDay() < 1 || saved.dueDay() > 31) {
            throw new IOException("Invalid bill");
        }
        return saved;
    }

    public static BillDto.Summary readSummary(Response response) throws IOException {
        BillDto.Summary summary = AccountRepository.read(response, BillDto.Summary.class);
        if (summary.total() == null || summary.paid() == null || summary.unpaid() == null) {
            throw new IOException("Invalid bill summary");
        }
        return summary;
    }

    static BillDto validate(BillDto row) throws IOException {
        if (row == null || row.id() <= 0 || row.name() == null || row.amount() == null
                || row.dueDate() == null || row.dueDay() < 1 || row.dueDay() > 31) {
            throw new IOException("Invalid bill");
        }
        return row;
    }
}
