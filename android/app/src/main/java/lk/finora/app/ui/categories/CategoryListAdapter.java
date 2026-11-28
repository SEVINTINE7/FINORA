package lk.finora.app.ui.categories;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.databinding.ItemCategoryBinding;
import lk.finora.app.ui.CategoryIcons;

/** T65.3/D69 category row inside a grouped card. */
final class CategoryListAdapter {
    private CategoryListAdapter() {}

    static String displayName(CategoryDto item, String language) {
        return "si".equals(language) && item.nameSi() != null && !item.nameSi().trim().isEmpty()
                ? item.nameSi() : item.name();
    }

    /** Need/Want chip: tinted fill and a word (shared with the form preview). */
    static void needChip(Context context, TextView chip, String needOrWant) {
        int label = CategoryRules.needLabel(needOrWant);
        chip.setVisibility(label == 0 ? View.GONE : View.VISIBLE);
        if (label == 0) return;
        chip.setText(label);
        chip.setTextColor(context.getColor(CategoryRules.needInk(needOrWant)));
        chip.setBackgroundTintList(ColorStateList.valueOf(context.getColor(CategoryRules.needFill(needOrWant))));
    }

    /** Type icon in a tinted circle: green for expense, blue for income. */
    static void icon(Context context, android.widget.ImageView view, String name, boolean income) {
        view.setImageResource(CategoryIcons.forCategory(name, income));
        view.setBackgroundTintList(ColorStateList.valueOf(context.getColor(income
                ? R.color.finora_neutral_surface : R.color.finora_success_surface)));
        view.setImageTintList(ColorStateList.valueOf(context.getColor(income ? R.color.finora_chart_2 : R.color.finora_green)));
    }

    static View row(Context context, ViewGroup parent, CategoryDto item, boolean first, Runnable edit, Runnable remove) {
        var row = ItemCategoryBinding.inflate(LayoutInflater.from(context), parent, false);
        String language = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        String name = displayName(item, language);
        String other = CategoryRules.secondaryName(item, language);
        row.categoryDivider.setVisibility(first ? View.GONE : View.VISIBLE);
        row.recordName.setText(name);
        if (other != null) {
            row.recordDetails.setText(other);
            row.recordDetails.setTypeface(null, Typeface.NORMAL);
            row.recordDetails.setVisibility(View.VISIBLE);
        } else if (!"si".equals(language)) {
            row.recordDetails.setText(R.string.category_no_si);
            row.recordDetails.setTypeface(null, Typeface.ITALIC);
        } else {
            row.recordDetails.setVisibility(View.GONE);
        }
        icon(context, row.categoryIcon, item.name(), "INCOME".equals(item.kind()));
        needChip(context, row.categoryNeed, item.needOrWant());

        // TalkBack: both names, need/want, and either "Default category · read-only" or "Edit <name>".
        StringBuilder spoken = new StringBuilder(name);
        if (other != null) spoken.append(". ").append(other);
        int need = CategoryRules.needLabel(item.needOrWant());
        if (need != 0) spoken.append(". ").append(context.getString(need));
        row.categoryOpen.setContentDescription(spoken);
        if (item.isDefault()) {
            row.recordRemove.setVisibility(View.GONE);
            row.categoryLock.setVisibility(View.VISIBLE);
            row.categoryOpen.setFocusable(true);
            ViewCompat.setStateDescription(row.categoryOpen, context.getString(R.string.category_default));
        } else {
            row.categoryOpen.setBackgroundResource(resolve(context, android.R.attr.selectableItemBackground));
            row.categoryOpen.setOnClickListener(view -> edit.run());
            ViewCompat.replaceAccessibilityAction(row.categoryOpen, AccessibilityActionCompat.ACTION_CLICK,
                    context.getString(R.string.records_edit_named, name), null);
            row.recordRemove.setContentDescription(context.getString(R.string.records_remove_named, name));
            row.recordRemove.setOnClickListener(view -> remove.run());
        }
        return row.getRoot();
    }

    private static int resolve(Context context, int attribute) {
        var value = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attribute, value, true);
        return value.resourceId;
    }
}
