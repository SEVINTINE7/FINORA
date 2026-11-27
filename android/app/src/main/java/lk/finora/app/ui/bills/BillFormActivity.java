package lk.finora.app.ui.bills;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.databinding.ActivityBillFormBinding;
import lk.finora.app.ui.CategoryIcons;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/** T63.3/D65 add or edit a monthly bill: name, amount, due day (1–31) and an optional expense category (T18.4). */
public final class BillFormActivity extends SecureActivity {
    private BillFormViewModel model;
    private ActivityBillFormBinding view;
    private boolean filled;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(BillFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        view = ActivityBillFormBinding.inflate(getLayoutInflater());
        setContentView(view.getRoot());
        ViewCompat.setAccessibilityHeading(view.billFormHeading, true);
        boolean editing = getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0) != 0;
        view.billFormHeading.setText(editing ? R.string.bill_edit_title : R.string.bill_add);
        AccountFormActivity.watch(view.formName, text -> model.name = text);
        AccountFormActivity.watch(view.billAmount, text -> model.amount = text);
        view.billDueDay.onDayChanged(day -> {
            model.dueDay = Integer.toString(day);
            view.billDueDayError.setVisibility(View.GONE);
        });
        view.recordsSave.setOnClickListener(ignored -> save());
        view.recordsRetry.setOnClickListener(ignored -> {
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
        if (filled && model.work.isCurrent()) model.category = Math.max(0, view.formKind.getSelectedItemPosition());
        super.onPause();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.saved) { finish(); return; }
        if (model.ready && !filled) {
            view.formKind.setAdapter(adapter(model.options));
            view.formKind.setChoiceIcon(0, R.drawable.finora_ic_cat_other);
            for (int i = 0; i < model.options.size(); i++) {
                view.formKind.setChoiceIcon(i + 1, CategoryIcons.forCategory(model.options.get(i).name(), false));
            }
            if (model.category <= model.options.size()) view.formKind.setSelection(model.category);
            view.formName.setText(model.name);
            view.billAmount.setText(model.amount);
            view.billDueDay.setDay(dueDay(model.dueDay));
            filled = true;
        }
        boolean enabled = AccountFormActivity.formState(this, model.work, model.ready);
        view.recordsStatus.setVisibility(view.recordsStatus.getText().length() == 0 ? View.GONE : View.VISIBLE);
        boolean retry = !model.work.busy() && (model.work.error != 0 || model.work.ambiguousCreate);
        view.recordsRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        view.formName.setEnabled(enabled);
        view.billAmount.setEnabled(enabled);
        view.billDueDay.setEnabled(enabled);
        view.formKind.setEnabled(enabled);
    }

    private static int dueDay(String text) {
        try {
            return BillFormViewModel.parseDueDay(text);
        } catch (IllegalArgumentException empty) {
            return 0;
        }
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
        String billName = view.formName.getText().toString().trim();
        if (!BillFormViewModel.validName(billName)) {
            FormFields.error(view.formName, R.string.records_name_invalid);
            return;
        }
        BigDecimal billAmount;
        try {
            billAmount = BillFormViewModel.parseAmount(view.billAmount.getText().toString());
        } catch (IllegalArgumentException failure) {
            FormFields.error(view.billAmount, R.string.budget_limit_invalid);
            return;
        }
        int day = view.billDueDay.getDay();
        if (day == 0) {
            view.billDueDayError.setVisibility(View.VISIBLE);
            view.billDueDay.getChildAt(0).requestFocus();
            return;
        }
        model.category = Math.max(0, view.formKind.getSelectedItemPosition());
        model.save(billName, billAmount, day);
    }
}
