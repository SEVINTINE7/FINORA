package lk.finora.app.ui.budgets;

import androidx.lifecycle.ViewModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lk.finora.app.data.api.BudgetDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.BudgetRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** Add or edit one month's budget for an expense category (T17.3). */
public final class BudgetFormViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final BudgetRepository budgets = work.requests == null ? null : new BudgetRepository(work.requests);
    private final CategoryRepository categories =
            work.requests == null ? null : new CategoryRepository(work.requests);
    long id;
    String month;
    boolean ready;
    boolean saved;
    String limit = "";
    int category;
    List<CategoryDto> options = Collections.emptyList();
    private List<Long> taken = Collections.emptyList();
    private long categoryId;

    void load(long recordId, String recordMonth, List<Long> takenIds) {
        id = recordId;
        month = recordMonth;
        taken = takenIds;
        if (budgets == null) return;
        work.run(categories::list, CategoryRepository::rows, all -> {
            if (id == 0) {
                options = choices(all, taken, 0);
                ready = true;
            } else {
                work.run(() -> budgets.get(id), BudgetRepository::readRow, item -> {
                    categoryId = item.categoryId();
                    month = item.month();
                    limit = item.limitAmount().setScale(2).toPlainString();
                    options = choices(all, taken, categoryId);
                    category = Math.max(0, indexOf(options, categoryId));
                    ready = true;
                }, false);
            }
        }, false);
    }

    void save(BigDecimal amount) {
        if (!ready || options.isEmpty() || work.ambiguousCreate || !work.isCurrent() || work.busy()) return;
        BudgetDto.Write body = new BudgetDto.Write(options.get(category).id(), month, amount);
        work.run(() -> id == 0 ? budgets.create(body) : budgets.update(id, body), BudgetRepository::readRow,
                value -> saved = true, id == 0);
    }

    /** EXPENSE categories that have no budget this month yet, plus the one being edited. */
    static List<CategoryDto> choices(List<CategoryDto> all, List<Long> taken, long keep) {
        List<CategoryDto> result = new ArrayList<>();
        for (CategoryDto item : all) {
            if ("EXPENSE".equals(item.kind()) && (item.id() == keep || !taken.contains(item.id()))) {
                result.add(item);
            }
        }
        return result;
    }

    static int indexOf(List<CategoryDto> items, long categoryId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id() == categoryId) return i;
        }
        return -1;
    }

    /** LKR, more than zero, at most 2 decimals and 13 whole digits (same rule as the API). */
    static BigDecimal parseLimit(String value) {
        String text = value.trim();
        if (!text.matches("[0-9]{1,13}(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Invalid amount");
        }
        BigDecimal amount = new BigDecimal(text).setScale(2);
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be more than zero");
        return amount;
    }

    private void clear() {
        options = Collections.emptyList();
        limit = "";
        ready = false;
    }

    @Override protected void onCleared() { work.close(); }
}
