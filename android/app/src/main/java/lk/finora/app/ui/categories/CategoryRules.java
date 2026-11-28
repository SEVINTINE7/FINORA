package lk.finora.app.ui.categories;

import java.util.ArrayList;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;

/** T65.3/D69: pure category list rules, kept out of the activity for unit tests. */
final class CategoryRules {
    private CategoryRules() {}

    /** One type's categories: your own first, then the defaults, each in API order. */
    static List<CategoryDto> ordered(List<CategoryDto> rows, String kind) {
        var group = CategoryListViewModel.group(rows, kind);
        List<CategoryDto> ordered = new ArrayList<>();
        for (var item : group) if (!item.isDefault()) ordered.add(item);
        for (var item : group) if (item.isDefault()) ordered.add(item);
        return ordered;
    }

    private static boolean hasSinhala(CategoryDto item) {
        return item.nameSi() != null && !item.nameSi().trim().isEmpty();
    }

    /**
     * The name under the main one: the other language's name, or null when there is no Sinhala name
     * (the row then says so in the English UI).
     */
    static String secondaryName(CategoryDto item, String language) {
        if (!hasSinhala(item)) return null;
        return "si".equals(language) ? item.name() : item.nameSi();
    }

    /** Need/Want chip label, or 0 for none. */
    static int needLabel(String needOrWant) {
        if ("NEED".equals(needOrWant)) return R.string.category_need;
        if ("WANT".equals(needOrWant)) return R.string.category_want;
        return 0;
    }

    static int needFill(String needOrWant) {
        return "WANT".equals(needOrWant) ? R.color.finora_warn_fill : R.color.finora_success_surface;
    }

    static int needInk(String needOrWant) {
        return "WANT".equals(needOrWant) ? R.color.finora_warn_ink : R.color.finora_green;
    }
}
