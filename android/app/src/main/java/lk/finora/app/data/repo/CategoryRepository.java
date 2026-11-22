package lk.finora.app.data.repo;

import java.io.IOException;
import java.util.List;

import lk.finora.app.data.api.CategoryApi;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.Response;

public final class CategoryRepository {
    private final CategoryApi api;
    private final ProtectedRequests requests;

    public CategoryRepository(ProtectedRequests requests) {
        this.requests = requests;
        api = new CategoryApi(requests.origin());
    }

    public Call list() { return requests.call(api.list()); }
    public Call get(long id) { return requests.call(api.get(id)); }
    public Call create(CategoryDto.Create body) { return requests.call(api.create(AccountRepository.json(body))); }
    public Call update(long id, CategoryDto.Update body) { return requests.call(api.update(id, AccountRepository.json(body))); }
    public Call delete(long id) { return requests.call(api.delete(id)); }

    public static List<CategoryDto> rows(Response response) throws IOException {
        return java.util.Arrays.asList(AccountRepository.read(response, CategoryDto[].class));
    }
}
