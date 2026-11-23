package lk.finora.app.data.repo;

import java.io.IOException;
import java.util.List;

import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public final class GoalRepository {
    private final ProtectedRequests requests;
    private final HttpUrl endpoint;

    public GoalRepository(ProtectedRequests requests) {
        this.requests = requests;
        endpoint = requests.origin().newBuilder().addPathSegments("api/v1/goals").build();
    }

    public Call list() { return requests.call(new Request.Builder().url(endpoint).build()); }
    public Call get(long id) { return requests.call(new Request.Builder().url(goalUrl(endpoint, id)).build()); }
    public Call create(GoalDto.Write body) {
        return requests.call(new Request.Builder().url(endpoint).post(AccountRepository.json(body)).build());
    }
    public Call update(long id, GoalDto.Write body) {
        return requests.call(new Request.Builder().url(goalUrl(endpoint, id)).put(AccountRepository.json(body)).build());
    }
    public Call delete(long id) {
        return requests.call(new Request.Builder().url(goalUrl(endpoint, id)).delete().build());
    }

    public Call contributions(long id) {
        return requests.call(new Request.Builder().url(contributionsUrl(endpoint, id)).build());
    }
    /** Returns the goal with its new progress. */
    public Call addContribution(long id, GoalDto.NewContribution body) {
        return requests.call(new Request.Builder().url(contributionsUrl(endpoint, id))
                .post(AccountRepository.json(body)).build());
    }
    /** Returns the goal with its new progress. */
    public Call deleteContribution(long id, long contributionId) {
        return requests.call(new Request.Builder().url(contributionsUrl(endpoint, id).newBuilder()
                .addPathSegment(Long.toString(contributionId)).build()).delete().build());
    }

    static HttpUrl goalUrl(HttpUrl endpoint, long id) {
        return endpoint.newBuilder().addPathSegment(Long.toString(id)).build();
    }

    static HttpUrl contributionsUrl(HttpUrl endpoint, long id) {
        return goalUrl(endpoint, id).newBuilder().addPathSegment("contributions").build();
    }

    public static List<GoalDto> rows(Response response) throws IOException {
        GoalDto[] rows = AccountRepository.read(response, GoalDto[].class);
        for (GoalDto row : rows) validate(row);
        return java.util.Arrays.asList(rows);
    }

    public static GoalDto readGoal(Response response) throws IOException {
        return validate(AccountRepository.read(response, GoalDto.class));
    }

    public static List<GoalDto.Contribution> readContributions(Response response) throws IOException {
        GoalDto.Contribution[] rows = AccountRepository.read(response, GoalDto.Contribution[].class);
        for (GoalDto.Contribution row : rows) {
            if (row == null || row.id() <= 0 || row.amount() == null || row.date() == null) {
                throw new IOException("Invalid contribution");
            }
        }
        return java.util.Arrays.asList(rows);
    }

    static GoalDto validate(GoalDto row) throws IOException {
        if (row == null || row.id() <= 0 || row.name() == null || row.targetAmount() == null
                || row.saved() == null || row.remaining() == null
                || row.progressPercent() < 0 || row.progressPercent() > 100
                || !("ACTIVE".equals(row.status()) || "DONE".equals(row.status())
                        || "CANCELLED".equals(row.status()))) {
            throw new IOException("Invalid goal");
        }
        return row;
    }
}
