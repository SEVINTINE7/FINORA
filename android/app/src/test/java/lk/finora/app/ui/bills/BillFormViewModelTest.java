package lk.finora.app.ui.bills;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.api.CategoryDto;

public class BillFormViewModelTest {
    private static final List<CategoryDto> ALL = List.of(
            new CategoryDto(1, "Salary", null, "INCOME", null, true),
            new CategoryDto(2, "Utilities", null, "EXPENSE", "NEED", true),
            new CategoryDto(3, "Rent", null, "EXPENSE", "NEED", false));

    @Test public void onlyExpenseCategoriesAfterTheNoneEntry() {
        List<CategoryDto> options = BillFormViewModel.expenseOnly(ALL);
        assertEquals(List.of(2L, 3L), options.stream().map(CategoryDto::id).toList());
        assertEquals(2, BillFormViewModel.position(options, 3));   // 0 is "No category"
        assertEquals(0, BillFormViewModel.position(options, 99));  // no longer visible
    }

    @Test public void dueDayIsOneToThirtyOne() {
        assertEquals(1, BillFormViewModel.parseDueDay(" 1 "));
        assertEquals(31, BillFormViewModel.parseDueDay("31"));
        for (String bad : new String[]{"", "0", "32", "-1", "1.5", "abc"}) {
            assertThrows(bad, IllegalArgumentException.class, () -> BillFormViewModel.parseDueDay(bad));
        }
    }

    @Test public void amountAndNameRules() {
        assertEquals(new BigDecimal("4500.00"), BillFormViewModel.parseAmount("4500"));
        assertThrows(IllegalArgumentException.class, () -> BillFormViewModel.parseAmount("0"));
        assertThrows(IllegalArgumentException.class, () -> BillFormViewModel.parseAmount("10.555"));
        assertTrue(BillFormViewModel.validName(" Rent "));
        assertFalse(BillFormViewModel.validName("  "));
        assertFalse(BillFormViewModel.validName("x".repeat(61)));
    }
}
