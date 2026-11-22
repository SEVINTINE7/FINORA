package lk.finora.app.ui.categories;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.accounts.AccountListActivity;

public final class CategoryListActivity extends AppCompatActivity {
    private CategoryListViewModel model;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(CategoryListViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_category_list);
        findViewById(R.id.records_add).setOnClickListener(view -> open(0));
        findViewById(R.id.records_retry).setOnClickListener(view -> model.load());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (AccountListActivity.verify(this, model.work)) model.load();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        AccountListActivity.showListState(this, model.work, model.rows.isEmpty());
        LinearLayout rows = findViewById(R.id.records_rows);
        rows.removeAllViews();
        addGroup(rows, "INCOME", R.string.category_income);
        addGroup(rows, "EXPENSE", R.string.category_expense);
    }

    private void addGroup(LinearLayout rows, String kind, int title) {
        var group = CategoryListViewModel.group(model.rows, kind);
        if (group.isEmpty()) return;
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextAppearance(android.R.style.TextAppearance_Material_Title);
        ViewCompat.setAccessibilityHeading(heading, true);
        rows.addView(heading);
        for (var item : group) {
            rows.addView(CategoryListAdapter.row(this, rows, item,
                    () -> { if (!item.isDefault()) open(item.id()); },
                    () -> AccountListActivity.confirm(this, model.work, R.string.category_remove_message,
                            () -> model.delete(item))));
        }
    }

    private void open(long id) {
        if (AccountListActivity.verify(this, model.work) && !model.work.busy()) {
            startActivity(new Intent(this, CategoryFormActivity.class).putExtra(AccountListActivity.ITEM_ID, id));
        }
    }
}
