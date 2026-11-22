package lk.finora.app.ui.categories;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CategoryFormViewModelTest {
    @Test
    public void optionalNamePreservesUneditedTextAndAllowsExplicitClear() {
        assertEquals(" Original ", CategoryFormViewModel.optionalName(" Original ", " Original "));
        assertEquals("New", CategoryFormViewModel.optionalName(" New ", "Original"));
        assertNull(CategoryFormViewModel.optionalName("", "Original"));
    }
}
