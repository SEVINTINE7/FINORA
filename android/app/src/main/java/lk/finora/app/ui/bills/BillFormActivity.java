package lk.finora.app.ui.bills;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.FormChoices;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/** Add or edit a monthly bill: name, amount, due day (1–31) and an optional expense category (T18.4). */
public final class BillFormActivity extends SecureActivity {
    private BillFormViewModel model;
    private boolean filled;
    private EditText name;
    private EditText amount;
    private EditText dueDay;
    private FormChoices category;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(BillFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_bill_form);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.bill_form_heading), true);
        name = findViewById(R.id.form_name);
        amount = findViewById(R.id.bill_amount);
        dueDay = findViewById(R.id.bill_due_day);
        category = findViewById(R.id.form_kind);
        AccountFormActivity.watch(name, text -> model.name = text);
        AccountFormActivity.watch(amount, text -> model.amount = text);
        AccountFormActivity.watch(dueDay, text -> model.dueDay = text);
        findViewById(R.id.records_save).setOnClickListener(view -> save());
        findViewById(R.id.records_retry).setOnClickListener(view -> {
            if (model.work.ambiguousCreate) {
                AccountFormActivity.reconcile(this, model.work, BillListActivity.class);
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
        if (filled && model.work.isCurrent()) model.category = Math.max(0, category.getSelectedItemPosition());
        super.onPause();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.saved) { finish(); return; }
        if (model.ready && !filled) {
            category.setAdapter(adapter(model.options));
            if (model.category <= model.options.size()) category.setSelection(model.category);
            name.setText(model.name);
            amount.setText(model.amount);
            dueDay.setText(model.dueDay);
            filled = true;
        }
        boolean enabled = AccountFormActivity.formState(this, model.work, model.ready);
        name.setEnabled(enabled);
        amount.setEnabled(enabled);
        dueDay.setEnabled(enabled);
        category.setEnabled(enabled);
    }

    private ArrayAdapter<String> adapter(List<CategoryDto> options) {
        String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
        List<String> names = new ArrayList<>();
        names.add(getString(R.string.bill_no_category));
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
        String billName = name.getText().toString().trim();
        if (!BillFormViewModel.validName(billName)) {
            FormFields.error(name, R.string.records_name_invalid);
            return;
        }
        BigDecimal billAmount;
        try {
            billAmount = BillFormViewModel.parseAmount(amount.getText().toString());
        } catch (IllegalArgumentException failure) {
            FormFields.error(amount, R.string.budget_limit_invalid);
            return;
        }
        int day;
        try {
            day = BillFormViewModel.parseDueDay(dueDay.getText().toString());
        } catch (IllegalArgumentException failure) {
            FormFields.error(dueDay, R.string.bill_due_day_invalid);
            return;
        }
        model.category = Math.max(0, category.getSelectedItemPosition());
        model.save(billName, billAmount, day);
    }
}
