package lk.finora.app.ui.budgets;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;

public class BudgetListViewModelTest {
    @Test public void percentRoundsDownAndStaysWithinTheBar() {
        assertEquals(0, BudgetListViewModel.percent(new BigDecimal("0.00"), new BigDecimal("20000.00")));
        assertEquals(79, BudgetListViewModel.percent(new BigDecimal("15999.99"), new BigDecimal("20000.00")));
        assertEquals(80, BudgetListViewModel.percent(new BigDecimal("16000.00"), new BigDecimal("20000.00")));
        assertEquals(100, BudgetListViewModel.percent(new BigDecimal("20000.00"), new BigDecimal("20000.00")));
        assertEquals(100, BudgetListViewModel.percent(new BigDecimal("25000.00"), new BigDecimal("20000.00")));
        assertEquals(100, BudgetListViewModel.percent(new BigDecimal("1.00"), new BigDecimal("0.00")));
    }

    @Test public void eachStateHasItsOwnLabel() {
        assertEquals(R.string.budget_state_ok, BudgetListViewModel.stateLabel("OK"));
        assertEquals(R.string.budget_state_near, BudgetListViewModel.stateLabel("NEAR"));
        assertEquals(R.string.budget_state_over, BudgetListViewModel.stateLabel("OVER"));
        assertEquals(R.color.finora_error, BudgetListViewModel.stateColor("OVER"));
        assertEquals(R.string.budget_chip_near, BudgetListViewModel.stateChip("NEAR"));
        assertEquals(R.string.budget_chip_over, BudgetListViewModel.stateChip("OVER"));
        assertEquals(R.string.budget_state_ok, BudgetListViewModel.stateChip("OK"));
        // Colour is never the only signal: urgent states share the alert icon, on track has a tick.
        assertEquals(R.drawable.finora_ic_alert, BudgetListViewModel.stateIcon("NEAR"));
        assertEquals(R.drawable.finora_ic_check, BudgetListViewModel.stateIcon("OK"));
    }

    @Test public void overLimitFirstThenNearThenOnTrackKeepingApiOrder() {
        var food = budget(1, "OK", "100.00", "10.00");
        var fun = budget(2, "OVER", "50.00", "60.00");
        var bus = budget(3, "NEAR", "100.00", "85.00");
        var rent = budget(4, "OK", "200.00", "20.00");
        var sorted = BudgetListViewModel.ordered(List.of(food, fun, bus, rent));
        assertEquals(List.of(fun, bus, food, rent), sorted);
    }

    @Test public void totalsAddLimitsAndSpendingExactly() {
        var totals = BudgetListViewModel.totals(List.of(budget(1, "OK", "25000.00", "18450.00"),
                budget(2, "OVER", "5000.00", "6200.00"), budget(3, "NEAR", "8000.00", "6900.00")));
        assertEquals(new BigDecimal("38000.00"), totals.limit());
        assertEquals(new BigDecimal("31550.00"), totals.used());
        assertEquals(new BigDecimal("6450.00"), totals.left());
        var over = BudgetListViewModel.totals(List.of(budget(1, "OVER", "100.00", "100.01")));
        assertEquals(new BigDecimal("-0.01"), over.left());
        assertEquals(new BigDecimal("0.00"), BudgetListViewModel.totals(List.of()).left());
    }

    private static lk.finora.app.data.api.BudgetDto budget(long id, String state, String limit, String used) {
        var limitAmount = new BigDecimal(limit);
        var usedAmount = new BigDecimal(used);
        return new lk.finora.app.data.api.BudgetDto(id, id, "2026-11", limitAmount, usedAmount,
                limitAmount.subtract(usedAmount), state);
    }

    @Test public void indexesCategoriesById() {
        Map<Long, CategoryDto> map = BudgetListViewModel.index(List.of(
                new CategoryDto(3, "Food", null, "EXPENSE", "NEED", true),
                new CategoryDto(9, "Fun", null, "EXPENSE", "WANT", false)));
        assertEquals("Fun", map.get(9L).name());
        assertEquals(2, map.size());
    }
}
