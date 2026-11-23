package lk.finora.app.ui.bills;

import androidx.lifecycle.ViewModel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lk.finora.app.data.api.BillDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.BillRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** Add or edit a monthly bill (T18.4). Category is optional; the first spinner entry means "none". */
public final class BillFormViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final BillRepository bills = work.requests == null ? null : new BillRepository(work.requests);
    private final CategoryRepository categories =
            work.requests == null ? null : new CategoryRepository(work.requests);
    long id;
    boolean ready;
    boolean saved;
    String name = "";
    String amount = "";
    String dueDay = "";
    /** 0 = no category, otherwise options[category - 1]. */
    int category;
    List<CategoryDto> options = Collections.emptyList();

    void load(long recordId) {
        id = recordId;
        if (bills == null) return;
        work.run(categories::list, CategoryRepository::rows, all -> {
            options = expenseOnly(all);
            if (id == 0) {
                ready = true;
            } else {
                work.run(() -> bills.get(id), BillRepository::readSaved, item -> {
                    name = item.name();
                    amount = item.amount().setScale(2).toPlainString();
                    dueDay = Integer.toString(item.dueDay());
                    category = item.categoryId() == null ? 0 : position(options, item.categoryId());
                    ready = true;
                }, false);
            }
        }, false);
    }

    void save(String billName, BigDecimal billAmount, int day) {
        if (!ready || work.ambiguousCreate || !work.isCurrent() || work.busy()) return;
        Long categoryId = category == 0 ? null : options.get(category - 1).id();
        BillDto.Write body = new BillDto.Write(billName, billAmount, day, categoryId);
        work.run(() -> id == 0 ? bills.create(body) : bills.update(id, body), BillRepository::readSaved,
                value -> saved = true, id == 0);
    }

    static List<CategoryDto> expenseOnly(List<CategoryDto> all) {
        List<CategoryDto> result = new ArrayList<>();
        for (CategoryDto item : all) {
            if ("EXPENSE".equals(item.kind())) result.add(item);
        }
        return result;
    }

    /** Spinner position for a category id; 0 ("none") if it is no longer visible. */
    static int position(List<CategoryDto> options, long categoryId) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id() == categoryId) return i + 1;
        }
        return 0;
    }

    /** LKR, more than zero, at most 2 decimals and 13 whole digits (same rule as the API). */
    static BigDecimal parseAmount(String value) {
        String text = value.trim();
        if (!text.matches("[0-9]{1,13}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Invalid amount");
        BigDecimal result = new BigDecimal(text).setScale(2);
        if (result.signum() <= 0) throw new IllegalArgumentException("Amount must be more than zero");
        return result;
    }

    /** 1–31; 31 means the last day in shorter months (the API handles that). */
    static int parseDueDay(String value) {
        String text = value.trim();
        if (!text.matches("[0-9]{1,2}")) throw new IllegalArgumentException("Invalid day");
        int day = Integer.parseInt(text);
        if (day < 1 || day > 31) throw new IllegalArgumentException("Invalid day");
        return day;
    }

    static boolean validName(String value) {
        String text = value.trim();
        return !text.isEmpty() && text.length() <= 60;
    }

    private void clear() {
        options = Collections.emptyList();
        name = "";
        amount = "";
        dueDay = "";
        ready = false;
    }

    @Override protected void onCleared() { work.close(); }
}
