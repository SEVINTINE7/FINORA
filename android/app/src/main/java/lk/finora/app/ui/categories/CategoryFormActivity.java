package lk.finora.app.ui.categories;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.FormChoices;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

public final class CategoryFormActivity extends SecureActivity {
    private CategoryFormViewModel model;
    private boolean filled;
    private EditText name;
    private EditText nameSi;
    private FormChoices kind;
    private FormChoices need;
    private boolean editing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(CategoryFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_category_form);
        name = findViewById(R.id.form_name);
        nameSi = findViewById(R.id.category_name_si);
        kind = AccountFormActivity.choices(this, R.id.form_kind, R.string.category_expense, R.string.category_income);
        need = AccountFormActivity.choices(this, R.id.category_classification, R.string.category_need,
                R.string.category_want, R.string.category_unspecified);
        // T65.3/D69: both choices as tiles; the chosen tile shows a tick.
        kind.setColumns(2);
        need.setColumns(3);
        kind.onSelectionChanged(this::preview);
        need.onSelectionChanged(this::preview);
        editing = getIntent().getLongExtra(AccountListActivity.ITEM_ID, 0) != 0;
        TextView heading = findViewById(R.id.category_form_heading);
        heading.setText(editing ? R.string.category_edit : R.string.category_add);
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        ((TextView) findViewById(R.id.category_type_note)).setText(editing
                ? R.string.category_type_fixed : R.string.category_type_note);
        AccountFormActivity.watch(name, text -> { model.name = text; preview(); });
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
        TextView status = findViewById(R.id.records_status);
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
        boolean retry = !model.work.busy() && (model.work.error != 0 || model.work.ambiguousCreate);
        findViewById(R.id.records_retry).setVisibility(retry ? View.VISIBLE : View.GONE);
        preview();
        name.setEnabled(enabled);
        nameSi.setEnabled(enabled);
        kind.setEnabled(enabled && model.id == 0);
        need.setEnabled(enabled);
    }

    /** Live preview of the row as the list will show it. */
    private void preview() {
        if (name == null || kind == null || need == null) return;
        String text = name.getText().toString().trim();
        int kindIndex = kind.getSelectedItemPosition();
        int needIndex = need.getSelectedItemPosition();
        boolean income = kindIndex >= 0 && "INCOME".equals(CategoryFormViewModel.KINDS[kindIndex]);
        String needOrWant = needIndex < 0 ? null : CategoryFormViewModel.NEEDS[needIndex];
        int kindLabel = income ? R.string.category_income : R.string.category_expense;
        ((TextView) findViewById(R.id.category_preview_name)).setText(text);
        ((TextView) findViewById(R.id.category_preview_kind)).setText(kindLabel);
        ImageView icon = findViewById(R.id.category_preview_icon);
        CategoryListAdapter.icon(this, icon, text, income);
        CategoryListAdapter.needChip(this, findViewById(R.id.category_preview_need), needOrWant);
        int needLabel = CategoryRules.needLabel(needOrWant);
        findViewById(R.id.category_preview).setContentDescription(getString(R.string.category_preview) + ": "
                + text + ", " + getString(kindLabel) + (needLabel == 0 ? "" : ", " + getString(needLabel)));
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || !model.ready || model.work.busy()) return;
        String value = name.getText().toString().trim();
        if (value.isEmpty() || value.length() > 60) {
            FormFields.error(name, R.string.records_name_invalid);
            return;
        }
        if (nameSi.length() > 60) {
            FormFields.error(nameSi, R.string.category_name_si_invalid);
            return;
        }
        model.kind = kind.getSelectedItemPosition();
        model.need = need.getSelectedItemPosition();
        model.save();
    }
}
