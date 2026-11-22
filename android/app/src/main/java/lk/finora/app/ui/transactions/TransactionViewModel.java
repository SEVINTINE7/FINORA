package lk.finora.app.ui.transactions;

import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;

import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.data.repo.TransactionRepository;
import lk.finora.app.data.repo.TransactionRepository.Edit;
import lk.finora.app.data.repo.TransactionRepository.Filters;
import lk.finora.app.data.repo.TransactionRepository.Page;
import lk.finora.app.data.repo.TransactionRepository.Row;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class TransactionViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    List<AccountDto> accounts = Collections.emptyList();
    List<CategoryDto> categories = Collections.emptyList();
    Filters filters = new Filters(null, null, null, null, null);
    Page page = new Page(Collections.emptyList(), 0, 0);
    Row editing;
    boolean ready;
    private final TransactionRepository repository = work.requests == null ? null
            : new TransactionRepository(work.requests);

    void start() {
        if (ready || !work.isCurrent()) return;
        AccountRepository accountRepository = new AccountRepository(work.requests);
        CategoryRepository categoryRepository = new CategoryRepository(work.requests);
        work.run(accountRepository::list, AccountRepository::rows, loadedAccounts ->
                work.run(categoryRepository::list, CategoryRepository::rows, loadedCategories -> {
                    accounts = loadedAccounts;
                    categories = loadedCategories;
                    ready = true;
                    load(0);
                }, false), false);
    }

    void filter(Filters selection) {
        filters = selection;
        page = new Page(Collections.emptyList(), 0, 0);
        load(0);
    }

    void load(int index) {
        work.run(() -> repository.list(filters, index), TransactionRepository::readPage, result -> {
            if (result.items().isEmpty() && index > 0) {
                load(Math.min(index - 1, Math.max(0, result.totalPages() - 1)));
            } else {
                page = result;
            }
        }, false);
    }

    void open(long id) {
        work.run(() -> repository.get(id), TransactionRepository::readRow, row -> editing = row, false);
    }

    void update(Edit edit) {
        if (editing == null) return;
        long id = editing.id();
        work.run(() -> repository.update(id, edit), TransactionRepository::readRow, result -> {
            editing = null;
            load(page.page());
        }, false);
    }

    void delete(long id) {
        work.run(() -> repository.delete(id), Work::empty, ignored -> load(page.page()), false);
    }

    private void clear() {
        accounts = Collections.emptyList();
        categories = Collections.emptyList();
        page = new Page(Collections.emptyList(), 0, 0);
        editing = null;
    }

    @Override protected void onCleared() { work.close(); }
}
