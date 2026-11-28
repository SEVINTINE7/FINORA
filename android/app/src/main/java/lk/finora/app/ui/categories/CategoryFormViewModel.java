package lk.finora.app.ui.categories;

import androidx.lifecycle.ViewModel;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class CategoryFormViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final CategoryRepository repository = work.requests == null ? null : new CategoryRepository(work.requests);
    long id;
    boolean ready;
    boolean saved;
    String name = "";
    String nameSi = "";
    private String originalNameSi;
    int kind;
    int need;
    /** T65.3/D69: Expense first, as on the list. */
    static final String[] KINDS = {"EXPENSE", "INCOME"};
    /** T65.3/D69: Need, Want, Not set. */
    static final String[] NEEDS = {"NEED", "WANT", null};

    void load(long recordId) {
        id = recordId;
        if (id == 0) {
            need = 2;
            ready = true;
            work.changed();
        } else if (repository != null) {
            work.run(() -> repository.get(id), response -> AccountRepository.read(response, CategoryDto.class),
                    this::populate, false);
        }
    }

    private void populate(CategoryDto item) {
        if (item.isDefault()) {
            work.error = R.string.category_default;
            return;
        }
        name = item.name();
        originalNameSi = item.nameSi();
        nameSi = originalNameSi == null ? "" : originalNameSi;
        kind = java.util.Arrays.asList(KINDS).indexOf(item.kind());
        need = java.util.Arrays.asList(NEEDS).indexOf(item.needOrWant());
        ready = true;
    }

    void save() {
        if (!ready || work.ambiguousCreate || !work.isCurrent() || work.busy()) return;
        String si = optionalName(nameSi, originalNameSi);
        String needOrWant = NEEDS[need];
        work.run(() -> id == 0
                        ? repository.create(new CategoryDto.Create(name.trim(), si, KINDS[kind], needOrWant))
                        : repository.update(id, new CategoryDto.Update(name.trim(), si, needOrWant)),
                response -> AccountRepository.read(response, CategoryDto.class), value -> saved = true, id == 0);
    }

    static String optionalName(String draft, String original) {
        if (original != null && original.equals(draft)) return original;
        return draft.trim().isEmpty() ? null : draft.trim();
    }

    private void clear() {
        name = "";
        nameSi = "";
        originalNameSi = null;
        ready = false;
    }

    @Override protected void onCleared() { work.close(); }
}
