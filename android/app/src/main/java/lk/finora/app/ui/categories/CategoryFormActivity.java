package lk.finora.app.ui.categories;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

public final class CategoryFormActivity extends AppCompatActivity {
    private CategoryFormViewModel model;
    private boolean filled;
    private EditText name;
    private EditText nameSi;
    private Spinner kind;
    private Spinner need;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(CategoryFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_category_form);
        name = findViewById(R.id.form_name);
        nameSi = findViewById(R.id.category_name_si);
        kind = AccountFormActivity.spinner(this, R.id.form_kind, R.string.category_income, R.string.category_expense);
        need = AccountFormActivity.spinner(this, R.id.category_classification, R.string.category_unspecified,
                R.string.category_need, R.string.category_want);
        AccountFormActivity.watch(name, text -> model.name = text);
        AccountFormActivity.watch(nameSi, text -> model.nameSi = text);
        findViewById(R.id.records_save).setOnClickListener(view -> save());
        findViewById(R.id.records_retry).setOnClickListener(view -> {
            if (model.work.ambiguousCreate) {
                AccountFormActivity.reconcile(this, model.work, CategoryListActivity.class);
            } else if (!model.ready) {
                model.load(model.id);
            } else {
                save();
            }
        });
        model.work.changes.observe(this, ignored -> render());
        if (!model.ready && !model.work.busy()) {
            model.load(getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        AccountListActivity.verify(this, model.work);
    }

    @Override protected void onPause() {
        if (filled && model.work.isCurrent()) {
            model.kind = kind.getSelectedItemPosition();
            model.need = need.getSelectedItemPosition();
        }
        super.onPause();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.saved) { finish(); return; }
        if (model.ready && !filled) {
            name.setText(model.name);
            nameSi.setText(model.nameSi);
            if (model.kind >= 0) kind.setSelection(model.kind);
            if (model.need >= 0) need.setSelection(model.need);
            filled = true;
        }
        boolean enabled = AccountFormActivity.formState(this, model.work, model.ready);
        name.setEnabled(enabled);
        nameSi.setEnabled(enabled);
        kind.setEnabled(enabled && model.id == 0);
        need.setEnabled(enabled);
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || !model.ready || model.work.busy()) return;
        String value = name.getText().toString().trim();
        if (value.isEmpty() || value.length() > 60) {
            name.setError(getString(R.string.records_name_invalid));
            return;
        }
        if (nameSi.length() > 60) {
            nameSi.setError(getString(R.string.category_name_si_invalid));
            return;
        }
        model.kind = kind.getSelectedItemPosition();
        model.need = need.getSelectedItemPosition();
        model.save();
    }
}
