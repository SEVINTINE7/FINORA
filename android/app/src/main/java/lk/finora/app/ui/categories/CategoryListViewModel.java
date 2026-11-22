package lk.finora.app.ui.categories;

import androidx.lifecycle.ViewModel;

import java.util.List;

import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class CategoryListViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final CategoryRepository repository = work.requests == null ? null : new CategoryRepository(work.requests);
    List<CategoryDto> rows = java.util.Collections.emptyList();

    void load() {
        if (repository != null) work.run(repository::list, CategoryRepository::rows, value -> rows = value, false);
    }

    void delete(CategoryDto item) {
        if (!item.isDefault()) work.run(() -> repository.delete(item.id()), Work::empty, ignored -> load(), false);
    }

    static List<CategoryDto> group(List<CategoryDto> items, String kind) {
        java.util.ArrayList<CategoryDto> group = new java.util.ArrayList<>();
        for (CategoryDto item : items) {
            if (kind.equals(item.kind())) group.add(item);
        }
        return group;
    }

    private void clear() { rows = java.util.Collections.emptyList(); }

    @Override protected void onCleared() { work.close(); }
}
