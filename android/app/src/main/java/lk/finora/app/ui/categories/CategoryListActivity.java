package lk.finora.app.ui.categories;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.databinding.ActivityCategoryListBinding;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

public final class CategoryListActivity extends SecureActivity {
    private CategoryListViewModel model;
    private ActivityCategoryListBinding view;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(CategoryListViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityCategoryListBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.categoryHeading, true);
        ViewCompat.setAccessibilityHeading(view.categoryExpenseHeading, true);
        ViewCompat.setAccessibilityHeading(view.categoryIncomeHeading, true);
        view.recordsAdd.setOnClickListener(ignored -> open(0));
        view.recordsRetry.setOnClickListener(ignored -> model.load());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (AccountListActivity.verify(this, model.work)) model.load();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        AccountListActivity.showListState(this, model.work, model.rows.isEmpty());
        boolean failed = !model.work.busy() && model.work.error != 0;
        view.recordsRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
        fill("EXPENSE", view.categoryExpenseHeader, view.categoryExpenseCount, view.categoryExpenseCard,
                view.categoryExpenseRows);
        fill("INCOME", view.categoryIncomeHeader, view.categoryIncomeCount, view.categoryIncomeCard,
                view.categoryIncomeRows);
    }

    private void fill(String kind, View header, TextView count, View card, LinearLayout rows) {
        var items = CategoryRules.ordered(model.rows, kind);
        header.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        card.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        count.setText(getResources().getQuantityString(R.plurals.category_count, items.size(), items.size()));
        rows.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            rows.addView(CategoryListAdapter.row(this, rows, item, i == 0,
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
