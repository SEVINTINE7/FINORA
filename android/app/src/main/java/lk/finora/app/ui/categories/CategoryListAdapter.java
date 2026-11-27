package lk.finora.app.ui.categories;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import lk.finora.app.R;
import lk.finora.app.ui.FinancialCard;
import lk.finora.app.data.api.CategoryDto;

final class CategoryListAdapter {
    private CategoryListAdapter() {}

    static String displayName(CategoryDto item, String language) {
        return "si".equals(language) && item.nameSi() != null && !item.nameSi().trim().isEmpty()
                ? item.nameSi() : item.name();
    }

    static View row(Context context, ViewGroup parent, CategoryDto item, Runnable edit, Runnable remove) {
        View row = LayoutInflater.from(context).inflate(R.layout.item_category, parent, false);
        String language = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        String name = displayName(item, language);
        ((TextView) row.findViewById(R.id.record_name)).setText(name);
        TextView detail = row.findViewById(R.id.record_details);
        if (item.isDefault()) detail.setText(R.string.category_default);
        else if ("NEED".equals(item.needOrWant())) detail.setText(R.string.category_need);
        else if ("WANT".equals(item.needOrWant())) detail.setText(R.string.category_want);
        else detail.setVisibility(View.GONE);
        View editButton = row.findViewById(R.id.record_edit);
        View removeButton = row.findViewById(R.id.record_remove);
        if (item.isDefault()) {
            editButton.setVisibility(View.GONE);
            removeButton.setVisibility(View.GONE);
        } else {
            editButton.setContentDescription(context.getString(R.string.records_edit_named, name));
            removeButton.setContentDescription(context.getString(R.string.records_remove_named, name));
            editButton.setOnClickListener(view -> edit.run());
            removeButton.setOnClickListener(view -> remove.run());
        }
        var body = (android.widget.LinearLayout) row;
        int icon = "INCOME".equals(item.kind()) ? R.drawable.finora_nav_insights : R.drawable.finora_nav_records;
        return item.isDefault() ? FinancialCard.wrap(body, name, icon)
                : FinancialCard.wrap(body, name, icon, editButton, removeButton);
    }
}
