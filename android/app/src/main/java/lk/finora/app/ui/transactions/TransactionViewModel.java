package lk.finora.app.ui.transactions;

import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;

import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.data.repo.TransactionRepository;
import lk.finora.app.data.repo.TransactionRepository.Filters;
import lk.finora.app.data.repo.TransactionRepository.Page;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class TransactionViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    List<AccountDto> accounts = Collections.emptyList();
    List<CategoryDto> categories = Collections.emptyList();
    Filters filters = new Filters(null, null, null, null, null);
    final Pagination pagination = new Pagination();
    boolean hasLoadedPage;
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
        pagination.reset();
        load(0);
    }

    void retry() {
        if (ready) load(pagination.retryIndex());
        else start();
    }

    void load(int index) {
        work.run(() -> {
            var call = repository.list(filters, index);
            pagination.requested(index);
            return call;
        }, TransactionRepository::readPage, result -> {
            if (result.items().isEmpty() && index > 0) {
                load(Math.min(index - 1, Math.max(0, result.totalPages() - 1)));
            } else {
                pagination.loaded(result);
                hasLoadedPage = true;
            }
        }, false);
    }

    void delete(long id) {
        work.run(() -> repository.delete(id), Work::empty,
                ignored -> load(pagination.page().page()), false);
    }

    private void clear() {
        accounts = Collections.emptyList();
        categories = Collections.emptyList();
        pagination.reset();
        hasLoadedPage = false;
    }

    @Override protected void onCleared() { work.close(); }

    static final class Pagination {
        private Page page = new Page(Collections.emptyList(), 0, 0);
        private Integer requestedIndex;

        Page page() { return page; }

        void requested(int index) { requestedIndex = index; }

        int retryIndex() { return requestedIndex == null ? page.page() : requestedIndex; }

        void loaded(Page result) {
            page = result;
            requestedIndex = null;
        }

        void reset() { loaded(new Page(Collections.emptyList(), 0, 0)); }
    }
}
