package lk.finora.app.ui.budgets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.api.CategoryDto;

public class BudgetFormViewModelTest {
    private static final List<CategoryDto> ALL = List.of(
            new CategoryDto(1, "Salary", null, "INCOME", null, true),
            new CategoryDto(2, "Food", null, "EXPENSE", "NEED", true),
            new CategoryDto(3, "Transport", null, "EXPENSE", "NEED", true),
            new CategoryDto(4, "Fun", null, "EXPENSE", "WANT", false));

    @Test public void newBudgetOffersOnlyExpenseCategoriesWithoutABudget() {
        List<CategoryDto> options = BudgetFormViewModel.choices(ALL, List.of(3L), 0);
        assertEquals(List.of(2L, 4L), options.stream().map(CategoryDto::id).toList());
    }

    @Test public void editKeepsItsOwnCategory() {
        List<CategoryDto> options = BudgetFormViewModel.choices(ALL, List.of(2L, 3L), 3);
        assertEquals(List.of(3L, 4L), options.stream().map(CategoryDto::id).toList());
        assertEquals(0, BudgetFormViewModel.indexOf(options, 3));
        assertEquals(-1, BudgetFormViewModel.indexOf(options, 2));
    }

    @Test public void limitMustBePositiveWithTwoDecimalsAtMost() {
        assertEquals(new BigDecimal("20000.00"), BudgetFormViewModel.parseLimit(" 20000 "));
        assertEquals(new BigDecimal("0.01"), BudgetFormViewModel.parseLimit("0.01"));
        assertEquals(new BigDecimal("1250.50"), BudgetFormViewModel.parseLimit("1250.5"));
        for (String bad : new String[]{"", "0", "0.00", "-5", "10.555", "1,000", "abc", "12345678901234"}) {
            assertThrows(bad, IllegalArgumentException.class, () -> BudgetFormViewModel.parseLimit(bad));
        }
    }
}
