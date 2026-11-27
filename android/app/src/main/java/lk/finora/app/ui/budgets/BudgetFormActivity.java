package lk.finora.app.ui.budgets;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.databinding.ActivityBudgetFormBinding;
import lk.finora.app.ui.CategoryIcons;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/** T63.2/D65 add or edit a budget for one expense category and month (T17.3). */
public final class BudgetFormActivity extends SecureActivity {
    /** Matches the API's NEAR threshold (T17.2). */
    private static final BigDecimal NEAR = new BigDecimal("0.80");
    private BudgetFormViewModel model;
    private ActivityBudgetFormBinding view;
    private boolean filled;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(BudgetFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityBudgetFormBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.formTitle, true);
        boolean editing = getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0) != 0;
        view.formTitle.setText(editing ? R.string.budget_edit_title : R.string.budget_add);
        var locale = getResources().getConfiguration().getLocales().get(0);
        view.budgetNearHint.setText(getString(R.string.budget_near_hint,
                NumberFormat.getPercentInstance(locale).format(NEAR)));
        AccountFormActivity.watch(view.budgetLimit, text -> model.limit = text);
        view.recordsSave.setOnClickListener(ignored -> save());
        view.recordsRetry.setOnClickListener(ignored -> {
            if (model.work.ambiguousCreate) {
                AccountFormActivity.reconcile(this, model.work, BudgetListActivity.class);
            } else if (!model.ready) {
                startLoad();
            } else {
                save();
            }
        });
        model.work.changes.observe(this, ignored -> render());
        if (!model.ready && !model.work.busy()) startLoad();
    }

    private void startLoad() {
        List<Long> taken = new ArrayList<>();
        long[] ids = getIntent().getLongArrayExtra(BudgetListActivity.TAKEN);
        if (ids != null) for (long id : ids) taken.add(id);
        String month = getIntent().getStringExtra(BudgetListActivity.MONTH);
        model.load(getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0),
                month == null ? YearMonth.now().toString() : month, taken);
    }

    @Override protected void onResume() {
        super.onResume();
        AccountListActivity.verify(this, model.work);
    }

    @Override protected void onPause() {
        if (filled && model.work.isCurrent()) model.category = Math.max(0, view.formKind.getSelectedItemPosition());
        super.onPause();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.saved) { finish(); return; }
        if (model.month != null) {
            view.budgetMonth.setText(getString(R.string.budget_for_month,
                    BudgetListActivity.monthLabel(YearMonth.parse(model.month))));
        }
        if (model.ready && !filled) {
            view.formKind.setAdapter(adapter(model.options));
            for (int i = 0; i < model.options.size(); i++) {
                view.formKind.setChoiceIcon(i, CategoryIcons.forCategory(model.options.get(i).name(), false));
            }
            if (model.category < model.options.size()) view.formKind.setSelection(model.category);
            view.budgetLimit.setText(model.limit);
            filled = true;
        }
        boolean enabled = AccountFormActivity.formState(this, model.work, model.ready);
        if (model.work.error == R.string.records_duplicate) {
            view.recordsStatus.setText(R.string.budget_duplicate);
        } else if (model.ready && model.options.isEmpty() && !model.work.busy()) {
            view.recordsStatus.setText(R.string.budget_no_categories);
        }
        view.recordsStatus.setVisibility(view.recordsStatus.getText().length() == 0 ? View.GONE : View.VISIBLE);
        boolean retry = !model.work.busy() && (model.work.error != 0 || model.work.ambiguousCreate);
        view.recordsRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        view.budgetCategoryHint.setVisibility(model.ready && model.id == 0 && !model.options.isEmpty()
                ? View.VISIBLE : View.GONE);
        view.recordsSave.setEnabled(enabled && !model.options.isEmpty() && !model.work.ambiguousCreate);
        view.formKind.setEnabled(enabled);
        view.budgetLimit.setEnabled(enabled);
    }

    private ArrayAdapter<String> adapter(List<CategoryDto> options) {
        String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
        List<String> names = new ArrayList<>();
        for (CategoryDto item : options) {
            names.add("si".equals(language) && item.nameSi() != null && !item.nameSi().trim().isEmpty()
                    ? item.nameSi() : item.name());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || !model.ready || model.work.busy()) return;
        BigDecimal amount;
        try {
            amount = BudgetFormViewModel.parseLimit(view.budgetLimit.getText().toString());
        } catch (IllegalArgumentException failure) {
            FormFields.error(view.budgetLimit, R.string.budget_limit_invalid);
            return;
        }
        model.category = Math.max(0, view.formKind.getSelectedItemPosition());
        model.save(amount);
    }
}
