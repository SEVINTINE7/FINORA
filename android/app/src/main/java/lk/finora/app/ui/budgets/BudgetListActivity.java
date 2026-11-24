package lk.finora.app.ui.budgets;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.api.BudgetDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.ui.accounts.AccountListActivity;

/** Monthly budgets with progress bars (T17.3). */
public final class BudgetListActivity extends AppCompatActivity {
    static final String MONTH = "budget_month";
    static final String TAKEN = "budget_taken_categories";
    private BudgetListViewModel model;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(BudgetListViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_budget_list);
        findViewById(R.id.records_add).setOnClickListener(view -> open(0));
        findViewById(R.id.records_retry).setOnClickListener(view -> model.load());
        findViewById(R.id.budget_previous).setOnClickListener(view -> model.shiftMonth(-1));
        findViewById(R.id.budget_next).setOnClickListener(view -> model.shiftMonth(1));
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (AccountListActivity.verify(this, model.work)) model.load();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        AccountListActivity.showListState(this, model.work, model.rows.isEmpty());
        ((TextView) findViewById(R.id.budget_month)).setText(monthLabel(model.month));
        findViewById(R.id.budget_previous).setEnabled(!model.work.busy());
        findViewById(R.id.budget_next).setEnabled(!model.work.busy());
        LinearLayout rows = findViewById(R.id.records_rows);
        rows.removeAllViews();
        for (BudgetDto item : model.rows) rows.addView(row(rows, item));
    }

    private View row(LinearLayout parent, BudgetDto item) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_budget, parent, false);
        String name = categoryName(model.categoryById.get(item.categoryId()));
        ((TextView) row.findViewById(R.id.record_name)).setText(name);

        int color = getColor(BudgetListViewModel.stateColor(item.state()));
        ProgressBar progress = row.findViewById(R.id.budget_progress);
        progress.setProgress(BudgetListViewModel.percent(item.used(), item.limitAmount()));
        progress.setProgressTintList(ColorStateList.valueOf(color));

        String details = item.remaining().signum() < 0
                ? getString(R.string.budget_over_by, plain(item.used()), plain(item.limitAmount()),
                        plain(item.remaining().negate()))
                : getString(R.string.budget_left, plain(item.used()), plain(item.limitAmount()),
                        plain(item.remaining()));
        ((TextView) row.findViewById(R.id.record_details)).setText(details);
        TextView state = row.findViewById(R.id.budget_state);
        int stateLabel = BudgetListViewModel.stateLabel(item.state());
        if (stateLabel == R.string.budget_state_near) {
            var locale = getResources().getConfiguration().getLocales().get(0);
            String threshold = java.text.NumberFormat.getPercentInstance(locale).format(new java.math.BigDecimal("0.80"));
            state.setText(getString(stateLabel, threshold));
        } else {
            state.setText(stateLabel);
        }
        state.setTextColor(color);
        // TalkBack reads the bar as part of one sentence instead of a bare percentage
        progress.setContentDescription(name + ". " + details);

        View edit = row.findViewById(R.id.record_edit);
        View remove = row.findViewById(R.id.record_remove);
        edit.setContentDescription(getString(R.string.records_edit_named, name));
        remove.setContentDescription(getString(R.string.records_remove_named, name));
        edit.setOnClickListener(view -> open(item.id()));
        remove.setOnClickListener(view -> AccountListActivity.confirm(this, model.work,
                R.string.budget_remove_message, () -> model.delete(item.id())));
        return row;
    }

    private void open(long id) {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy()) return;
        long[] taken = new long[model.rows.size()];
        for (int i = 0; i < taken.length; i++) taken[i] = model.rows.get(i).categoryId();
        startActivity(new Intent(this, BudgetFormActivity.class)
                .putExtra(AccountListActivity.ITEM_ID, id)
                .putExtra(MONTH, model.month.toString())
                .putExtra(TAKEN, taken));
    }

    private String categoryName(CategoryDto category) {
        if (category == null) return getString(R.string.budget_unknown_category);
        String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
        return "si".equals(language) && category.nameSi() != null && !category.nameSi().trim().isEmpty()
                ? category.nameSi() : category.name();
    }

    /** Full month name and year in the app language, e.g. "November 2026" (also used by bills). */
    public static String monthLabel(java.time.YearMonth month) {
        return month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()));
    }

    private static String plain(java.math.BigDecimal value) {
        return value.setScale(2).toPlainString();
    }
}
