package lk.finora.app.ui.categories;

import java.util.List;

import org.junit.Test;

import lk.finora.app.data.api.CategoryDto;

import static org.junit.Assert.assertEquals;

public class CategoryListViewModelTest {
    @Test
    public void groupsKindsAndChoosesSinhalaNameWhenAvailable() {
        var income = new CategoryDto(1, "Salary", "Local name", "INCOME", null, true);
        var expense = new CategoryDto(2, "Food", " ", "EXPENSE", "NEED", false);
        assertEquals(List.of(income), CategoryListViewModel.group(List.of(expense, income), "INCOME"));
        assertEquals(List.of(expense), CategoryListViewModel.group(List.of(expense, income), "EXPENSE"));
        assertEquals("Local name", CategoryListAdapter.displayName(income, "si"));
        assertEquals("Food", CategoryListAdapter.displayName(expense, "si"));
        assertEquals("Salary", CategoryListAdapter.displayName(income, "en"));
    }
}
