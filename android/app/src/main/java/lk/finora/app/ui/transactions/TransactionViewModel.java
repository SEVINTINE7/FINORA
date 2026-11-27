package lk.finora.app.ui.transactions;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.data.repo.TransactionRepository;
import lk.finora.app.data.repo.TransactionRepository.Filters;
import lk.finora.app.data.repo.TransactionRepository.Page;
import lk.finora.app.data.repo.TransactionRepository.Row;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class TransactionViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    List<AccountDto> accounts = Collections.emptyList();
    List<CategoryDto> categories = Collections.emptyList();
    Filters filters = new Filters(null, null, null, null, null);
    final Pagination pagination = new Pagination();
    boolean ready;
    private final TransactionRepository repository = work.requests == null ? null
            : new TransactionRepository(work.requests);

    void start() {
        if (ready || !work.isCurrent() || work.busy()) return;
        AccountRepository accountRepository = new AccountRepository(work.requests);
        CategoryRepository categoryRepository = new CategoryRepository(work.requests);
        work.run(accountRepository::list, AccountRepository::rows, loadedAccounts ->
                work.run(categoryRepository::list, CategoryRepository::rows, loadedCategories -> {
                    accounts = loadedAccounts;
                    categories = loadedCategories;
                    ready = true;
                    load(0, 1);
                }, false), false);
    }

    void filter(Filters selection) {
        filters = selection;
        pagination.reset();
        load(0, 1);
    }

    void retry() {
        if (!ready) {
            start();
            return;
        }
        int index = pagination.retryIndex();
        load(index, index + 1);
    }

    /** T62/D65 "Load more": append the next page to the rows already shown. */
    void loadMore() {
        if (!work.busy() && pagination.hasMore()) {
            int next = pagination.loadedPages();
            load(next, next + 1);
        }
    }

    /** After an edit, delete or new record, reload every page already shown so rows stay in order. */
    void reload() {
        if (!ready) {
            start();
            return;
        }
        if (work.busy()) return;
        load(0, Math.max(1, pagination.loadedPages()));
    }

    private void load(int index, int until) {
        work.run(() -> {
            var call = repository.list(filters, index);
            pagination.requested(index);
            return call;
        }, TransactionRepository::readPage, result -> {
            pagination.loaded(result);
            if (index + 1 < until && index + 1 < result.totalPages()) load(index + 1, until);
        }, false);
    }

    boolean canFinishReview() {
        return !work.busy() && work.error == 0 && pagination.hasCurrentResult();
    }

    void delete(long id) {
        work.run(() -> repository.delete(id), Work::empty, ignored -> {
            int pages = Math.max(1, pagination.loadedPages());
            pagination.reset();
            load(0, pages);
        }, false);
    }

    private void clear() {
        accounts = Collections.emptyList();
        categories = Collections.emptyList();
        pagination.reset();
    }

    @Override protected void onCleared() { work.close(); }

    static final class Pagination {
        private Page page = new Page(Collections.emptyList(), 0, 0);
        private final List<Row> items = new ArrayList<>();
        private final Set<Long> ids = new HashSet<>();
        private int loadedPages;
        private Integer requestedIndex;
        private boolean loaded;

        Page page() { return page; }

        /** Every row loaded so far, in server order, without duplicates across pages. */
        List<Row> items() { return Collections.unmodifiableList(items); }

        int loadedPages() { return loadedPages; }

        boolean hasMore() { return loaded && loadedPages < page.totalPages(); }

        void requested(int index) { requestedIndex = index; }

        boolean hasCurrentResult() { return loaded && requestedIndex == null; }

        int retryIndex() { return requestedIndex == null ? page.page() : requestedIndex; }

        void loaded(Page result) {
            if (result.page() == 0) {
                items.clear();
                ids.clear();
            }
            for (Row row : result.items()) {
                // A record created elsewhere can shift page boundaries; never show a row twice.
                if (ids.add(row.id())) items.add(row);
            }
            page = result;
            loadedPages = result.page() + 1;
            requestedIndex = null;
            loaded = true;
        }

        void reset() {
            page = new Page(Collections.emptyList(), 0, 0);
            items.clear();
            ids.clear();
            loadedPages = 0;
            requestedIndex = null;
            loaded = false;
        }
    }
}
