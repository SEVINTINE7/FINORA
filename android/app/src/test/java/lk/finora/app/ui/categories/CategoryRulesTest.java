package lk.finora.app.ui.categories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;

public final class CategoryRulesTest {
    private static CategoryDto category(long id, String name, String si, String kind, String need, boolean isDefault) {
        return new CategoryDto(id, name, si, kind, need, isDefault);
    }

    @Test public void ownCategoriesComeFirstWithinEachType() {
        var food = category(1, "Food", "ආහාර", "EXPENSE", "NEED", true);
        var gym = category(2, "Gym", null, "EXPENSE", "WANT", false);
        var salary = category(3, "Salary", "වැටුප", "INCOME", null, true);
        var rent = category(4, "Rent", "නිවාස කුලිය", "EXPENSE", "NEED", true);
        var tutoring = category(5, "Tutoring", null, "INCOME", null, false);
        var rows = List.of(food, gym, salary, rent, tutoring);
        assertEquals(List.of(gym, food, rent), CategoryRules.ordered(rows, "EXPENSE"));
        assertEquals(List.of(tutoring, salary), CategoryRules.ordered(rows, "INCOME"));
    }

    @Test public void theOtherLanguageNameGoesUnderTheMainOne() {
        var food = category(1, "Food", "ආහාර", "EXPENSE", "NEED", true);
        var gym = category(2, "Gym", "  ", "EXPENSE", "WANT", false);
        assertEquals("ආහාර", CategoryRules.secondaryName(food, "en"));
        assertEquals("Food", CategoryRules.secondaryName(food, "si"));
        assertNull(CategoryRules.secondaryName(gym, "en"));
        assertNull(CategoryRules.secondaryName(gym, "si"));
    }

    @Test public void needAndWantChipsHaveAWordAndTheirOwnColours() {
        assertEquals(R.string.category_need, CategoryRules.needLabel("NEED"));
        assertEquals(R.string.category_want, CategoryRules.needLabel("WANT"));
        assertEquals(0, CategoryRules.needLabel(null));
        assertEquals(R.color.finora_warn_fill, CategoryRules.needFill("WANT"));
        assertEquals(R.color.finora_success_surface, CategoryRules.needFill("NEED"));
    }

    @Test public void formChoicesFollowTheApprovedOrder() {
        assertEquals(List.of("EXPENSE", "INCOME"), List.of(CategoryFormViewModel.KINDS));
        assertEquals("NEED", CategoryFormViewModel.NEEDS[0]);
        assertEquals("WANT", CategoryFormViewModel.NEEDS[1]);
        assertNull(CategoryFormViewModel.NEEDS[2]);
    }
}
