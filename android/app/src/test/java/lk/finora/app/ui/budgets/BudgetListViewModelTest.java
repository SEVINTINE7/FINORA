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
    }

    @Test public void indexesCategoriesById() {
        Map<Long, CategoryDto> map = BudgetListViewModel.index(List.of(
                new CategoryDto(3, "Food", null, "EXPENSE", "NEED", true),
                new CategoryDto(9, "Fun", null, "EXPENSE", "WANT", false)));
        assertEquals("Fun", map.get(9L).name());
        assertEquals(2, map.size());
    }
}
