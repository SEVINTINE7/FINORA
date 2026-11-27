package lk.finora.app.ui.budgets;

import androidx.lifecycle.ViewModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.finora.app.R;
import lk.finora.app.data.api.BudgetDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.repo.BudgetRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** Budgets of one month (T17.3). Categories are loaded first so each budget can show its name. */
public final class BudgetListViewModel extends ViewModel {
    final Work work = new Work(this::clear);
    private final BudgetRepository budgets = work.requests == null ? null : new BudgetRepository(work.requests);
    private final CategoryRepository categories =
            work.requests == null ? null : new CategoryRepository(work.requests);
    YearMonth month = YearMonth.now();
    List<BudgetDto> rows = Collections.emptyList();
    Map<Long, CategoryDto> categoryById = Collections.emptyMap();

    void load() {
        if (budgets == null) return;
        work.run(categories::list, CategoryRepository::rows, value -> {
            categoryById = index(value);
            work.run(() -> budgets.list(month.toString()), BudgetRepository::rows, list -> rows = list, false);
        }, false);
    }

    void shiftMonth(int months) {
        if (work.busy()) return;
        month = month.plusMonths(months);
        rows = Collections.emptyList();
        load();
    }

    void delete(long id) {
        work.run(() -> budgets.delete(id), Work::empty, ignored -> load(), false);
    }

    static Map<Long, CategoryDto> index(List<CategoryDto> items) {
        Map<Long, CategoryDto> map = new HashMap<>();
        for (CategoryDto item : items) map.put(item.id(), item);
        return map;
    }

    /** Whole percent used for the progress bar, 0–100. Over the limit shows a full bar. */
    static int percent(BigDecimal used, BigDecimal limit) {
        if (limit.signum() <= 0) return 100;
        BigDecimal value = used.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.DOWN);
        return Math.max(0, Math.min(100, value.intValue()));
    }

    static int stateLabel(String state) {
        switch (state) {
            case "OVER": return R.string.budget_state_over;
            case "NEAR": return R.string.budget_state_near;
            default: return R.string.budget_state_ok;
        }
    }

    /** Ink for the state chip text, icon and amount line; the chip also carries a word and an icon (T63.2). */
    static int stateColor(String state) {
        switch (state) {
            case "OVER": return R.color.finora_error;
            case "NEAR": return R.color.finora_warn_ink;
            default: return R.color.finora_green;
        }
    }

    static int stateFill(String state) {
        switch (state) {
            case "OVER": return R.color.finora_error_surface;
            case "NEAR": return R.color.finora_warn_fill;
            default: return R.color.finora_success_surface;
        }
    }

    static int stateBar(String state) {
        switch (state) {
            case "OVER": return R.color.finora_bar_over;
            case "NEAR": return R.color.finora_chart_3;
            default: return R.color.finora_green;
        }
    }

    static int stateChip(String state) {
        switch (state) {
            case "OVER": return R.string.budget_chip_over;
            case "NEAR": return R.string.budget_chip_near;
            default: return R.string.budget_state_ok;
        }
    }

    static int stateIcon(String state) {
        return "OVER".equals(state) || "NEAR".equals(state) ? R.drawable.finora_ic_alert : R.drawable.finora_ic_check;
    }

    /** Over-limit budgets first, then near the limit, then on track; the API order is kept inside each group. */
    static List<BudgetDto> ordered(List<BudgetDto> rows) {
        List<BudgetDto> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(item -> "OVER".equals(item.state()) ? 0 : "NEAR".equals(item.state()) ? 1 : 2));
        return sorted;
    }

    /** Sum of the month's limits and spending; {@code left} is negative when spending is over the total. */
    record Totals(BigDecimal limit, BigDecimal used) {
        BigDecimal left() { return limit.subtract(used); }
    }

    static Totals totals(List<BudgetDto> rows) {
        BigDecimal limit = BigDecimal.ZERO.setScale(2);
        BigDecimal used = BigDecimal.ZERO.setScale(2);
        for (BudgetDto item : rows) {
            limit = limit.add(item.limitAmount());
            used = used.add(item.used());
        }
        return new Totals(limit, used);
    }

    private void clear() {
        rows = Collections.emptyList();
        categoryById = Collections.emptyMap();
    }

    @Override protected void onCleared() { work.close(); }
}
