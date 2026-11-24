package lk.finora.app.ui.transactions;

import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.HttpFailure;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.data.repo.TransactionRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.transactions.TransactionFormValidator.Draft;
import lk.finora.app.ui.transactions.TransactionFormValidator.Field;

public final class TransactionFormViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final TransactionRepository transactions = work.requests == null ? null
            : new TransactionRepository(work.requests);
    List<AccountDto> accounts = Collections.emptyList();
    List<CategoryDto> categories = Collections.emptyList();
    final Map<Field, Integer> errors = new EnumMap<>(Field.class);
    final CreateRetry retry = new CreateRetry();
    Draft draft;
    Long originalAccount;
    boolean saved;
    private long id;

    void load(long id) {
        if (!work.isCurrent() || work.busy() || draft != null) return;
        this.id = id;
        var accountRepository = new AccountRepository(work.requests);
        var categoryRepository = new CategoryRepository(work.requests);
        work.run(accountRepository::list, AccountRepository::rows, loadedAccounts ->
                work.run(categoryRepository::list, CategoryRepository::rows, loadedCategories -> {
                    accounts = loadedAccounts;
                    categories = loadedCategories;
                    if (id == 0) {
                        draft = new Draft(null, null, null, "", "", "", "");
                    } else {
                        work.run(() -> transactions.get(id), TransactionRepository::readRow, row -> {
                            originalAccount = row.accountId();
                            draft = new Draft(row.accountId(), row.categoryId(), row.type(),
                                    row.amount().toPlainString(), row.txnDate(),
                                    row.merchant() == null ? "" : row.merchant(),
                                    row.note() == null ? "" : row.note());
                        }, false);
                    }
                }, false), false);
    }

    void submit(char separator, boolean confirmed) {
        if (!work.isCurrent() || draft == null || saved || work.busy() || !retry.permits(confirmed)) return;
        var validation = TransactionFormValidator.validate(draft, separator, accounts, categories, originalAccount);
        errors.clear();
        errors.putAll(validation.errors());
        if (validation.request() == null) {
            work.error = 0;
            work.changed();
            return;
        }
        boolean create = id == 0;
        work.run(() -> create ? transactions.create(validation.request()) : transactions.update(id, validation.request()),
                TransactionRepository::readRow, ignored -> saved = true, create, failure -> {
                    if (create && isUncertainCreate(failure)) {
                        retry.markUncertain();
                    } else if (failure instanceof HttpFailure http && http.status == 400) {
                        for (var error : http.fields) {
                            Field field = error == null ? null : TransactionFormValidator.serverField(error.field);
                            if (field != null) errors.put(field, R.string.records_invalid);
                        }
                    }
                });
    }

    static boolean isUncertainCreate(Exception failure) {
        return !(failure instanceof HttpFailure http)
                || (http.status != 400 && http.status != 401 && http.status != 403);
    }

    private void clear() {
        draft = null;
        originalAccount = null;
        accounts = Collections.emptyList();
        categories = Collections.emptyList();
        errors.clear();
        saved = false;
        retry.reset();
    }

    @Override protected void onCleared() { work.close(); }

    static final class CreateRetry {
        boolean uncertain;
        boolean reviewed;

        void markUncertain() {
            uncertain = true;
            reviewed = false;
        }

        boolean permits(boolean confirmed) {
            return !uncertain || (reviewed && confirmed);
        }

        void reset() {
            uncertain = false;
            reviewed = false;
        }
    }
}
