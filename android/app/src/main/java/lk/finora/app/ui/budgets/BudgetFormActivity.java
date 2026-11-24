package lk.finora.app.ui.budgets;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/** Add or edit a budget for one expense category and month (T17.3). */
public final class BudgetFormActivity extends AppCompatActivity {
    private BudgetFormViewModel model;
    private boolean filled;
    private Spinner category;
    private EditText limit;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(BudgetFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_budget_form);
        category = findViewById(R.id.form_kind);
        limit = findViewById(R.id.budget_limit);
        AccountFormActivity.watch(limit, text -> model.limit = text);
        findViewById(R.id.records_save).setOnClickListener(view -> save());
        findViewById(R.id.records_retry).setOnClickListener(view -> {
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
        if (filled && model.work.isCurrent()) model.category = Math.max(0, category.getSelectedItemPosition());
        super.onPause();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.saved) { finish(); return; }
        if (model.month != null) {
            ((TextView) findViewById(R.id.budget_month))
                    .setText(BudgetListActivity.monthLabel(YearMonth.parse(model.month)));
        }
        if (model.ready && !filled) {
            category.setAdapter(adapter(model.options));
            if (model.category < model.options.size()) category.setSelection(model.category);
            limit.setText(model.limit);
            filled = true;
        }
        boolean enabled = AccountFormActivity.formState(this, model.work, model.ready);
        TextView status = findViewById(R.id.records_status);
        if (model.work.error == R.string.records_duplicate) {
            status.setText(R.string.budget_duplicate);
        } else if (model.ready && model.options.isEmpty() && !model.work.busy()) {
            status.setText(R.string.budget_no_categories);
        }
        findViewById(R.id.records_save).setEnabled(enabled && !model.options.isEmpty()
                && !model.work.ambiguousCreate);
        category.setEnabled(enabled);
        limit.setEnabled(enabled);
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
            amount = BudgetFormViewModel.parseLimit(limit.getText().toString());
        } catch (IllegalArgumentException failure) {
            limit.setError(getString(R.string.budget_limit_invalid));
            return;
        }
        model.category = Math.max(0, category.getSelectedItemPosition());
        model.save(amount);
    }
}
