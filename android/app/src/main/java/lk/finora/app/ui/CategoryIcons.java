package lk.finora.app.ui;

import java.util.Locale;
import java.util.Map;

import lk.finora.app.R;

/** T62: a client-side icon for each default category name (V6 seed). User categories get a neutral tag. */
public final class CategoryIcons {
    private static final Map<String, Integer> DEFAULTS = Map.ofEntries(
            Map.entry("salary", R.drawable.finora_ic_cat_salary),
            Map.entry("allowance", R.drawable.finora_ic_cat_salary),
            Map.entry("part-time", R.drawable.finora_ic_cat_salary),
            Map.entry("food", R.drawable.finora_ic_cat_cart),
            Map.entry("food delivery", R.drawable.finora_ic_cat_food),
            Map.entry("transport", R.drawable.finora_ic_cat_car),
            Map.entry("rent", R.drawable.finora_ic_cat_home),
            Map.entry("utilities", R.drawable.finora_ic_cat_bolt),
            Map.entry("mobile/internet", R.drawable.finora_ic_cat_phone),
            Map.entry("shopping", R.drawable.finora_ic_cat_bag),
            Map.entry("entertainment", R.drawable.finora_ic_cat_film),
            Map.entry("health", R.drawable.finora_ic_cat_heart),
            Map.entry("education", R.drawable.finora_ic_cat_book),
            Map.entry("loan payment", R.drawable.finora_ic_cat_card),
            Map.entry("savings", R.drawable.finora_ic_cat_savings));

    private CategoryIcons() {}

    /** @param name the category's English name, or null when uncategorised */
    public static int forCategory(String name, boolean income) {
        if (name == null) return income ? R.drawable.finora_ic_arrow_up : R.drawable.finora_ic_arrow_down;
        Integer icon = DEFAULTS.get(name.trim().toLowerCase(Locale.ROOT));
        return icon == null ? R.drawable.finora_ic_cat_other : icon;
    }
}
