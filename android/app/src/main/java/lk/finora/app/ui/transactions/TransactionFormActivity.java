package lk.finora.app.ui.transactions;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.transactions.TransactionFormValidator.Draft;
import lk.finora.app.ui.transactions.TransactionFormValidator.Field;

public final class TransactionFormActivity extends SecureActivity {
    static final String ID = "transaction_id";
    private TransactionFormViewModel model;
    private Spinner account;
    private Spinner type;
    private Spinner category;
    private EditText amount;
    private EditText merchant;
    private EditText note;
    private Button date;
    private View fields;
    private String selectedDate;
    private String categoryType;
    private boolean initialized;
    private boolean updatingCategory;
    private long id;
    private Map<Field, Integer> errorViews;

    private final androidx.activity.result.ActivityResultLauncher<Intent> records =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (verify() && model.retry.uncertain && result.getResultCode() == RESULT_OK) {
                    model.retry.reviewed = true;
                    render();
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(TransactionFormViewModel.class);
        if (!verify()) return;
        id = getIntent().getLongExtra(ID, 0);
        if (id < 0) { finish(); return; }
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_transaction_form);
        fields = findViewById(R.id.form_fields);
        fields.setSaveFromParentEnabled(false);
        TextView title = findViewById(R.id.form_title);
        title.setText(id == 0 ? R.string.transaction_open_manual : R.string.records_edit);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        account = findViewById(R.id.form_account);
        type = findViewById(R.id.form_type);
        category = findViewById(R.id.form_category);
        amount = findViewById(R.id.form_amount);
        merchant = findViewById(R.id.form_merchant);
        note = findViewById(R.id.form_note);
        date = findViewById(R.id.form_date);
        errorViews = new EnumMap<>(Field.class);
        errorViews.put(Field.ACCOUNT, R.id.form_account_error);
        errorViews.put(Field.TYPE, R.id.form_type_error);
        errorViews.put(Field.CATEGORY, R.id.form_category_error);
        errorViews.put(Field.AMOUNT, R.id.form_amount_error);
        errorViews.put(Field.DATE, R.id.form_date_error);
        errorViews.put(Field.MERCHANT, R.id.form_merchant_error);
        errorViews.put(Field.NOTE, R.id.form_note_error);
        date.setOnClickListener(view -> pickDate());
        findViewById(R.id.form_retry).setOnClickListener(view -> model.load(id));
        findViewById(R.id.form_save).setOnClickListener(view -> save());
        findViewById(R.id.form_review).setOnClickListener(view -> {
            if (verify() && !model.work.busy()) {
                records.launch(new Intent(this, TransactionActivity.class)
                        .putExtra(TransactionActivity.REVIEW_CREATE, true));
            }
        });
        model.work.changes.observe(this, ignored -> render());
        model.load(id);
    }

    @Override protected void onResume() {
        super.onResume();
        verify();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        captureDraft();
        super.onSaveInstanceState(state);
    }

    private boolean verify() {
        return model != null && AccountListActivity.verify(this, model.work);
    }

    private void render() {
        if (!verify()) return;
        if (model.saved) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        boolean ready = model.draft != null;
        boolean busy = model.work.busy();
        if (ready && !initialized) bindDraft();
        fields.setVisibility(ready ? View.VISIBLE : View.GONE);
        for (View control : new View[]{account, type, category, amount, merchant, note, date}) {
            control.setEnabled(!busy);
        }
        TextView status = findViewById(R.id.form_status);
        boolean noAccount = ready && account.getCount() == 1;
        int message = busy ? (ready ? R.string.transaction_saving : R.string.records_loading)
                : model.retry.uncertain ? R.string.transaction_save_uncertain
                : model.work.error != 0 ? model.work.error
                : noAccount ? R.string.transaction_no_accounts : 0;
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) status.setText(message);
        for (var entry : errorViews.entrySet()) {
            TextView error = findViewById(entry.getValue());
            Integer resource = model.errors.get(entry.getKey());
            error.setVisibility(resource == null ? View.GONE : View.VISIBLE);
            if (resource != null) error.setText(resource);
        }
        findViewById(R.id.form_retry).setVisibility(!ready && !busy && model.work.error != 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.form_review).setVisibility(!busy && model.retry.uncertain ? View.VISIBLE : View.GONE);
        Button save = findViewById(R.id.form_save);
        save.setVisibility(ready ? View.VISIBLE : View.GONE);
        save.setEnabled(!busy && !noAccount && (!model.retry.uncertain || model.retry.reviewed));
        save.setText(model.retry.uncertain ? R.string.transaction_submit_again : R.string.records_save);
    }

    private void bindDraft() {
        Draft draft = model.draft;
        List<Choice<Long>> accounts = new ArrayList<>();
        accounts.add(new Choice<>(null, getString(R.string.transaction_choose)));
        for (var item : model.accounts) {
            if (TransactionFormValidator.isSelectable(item, model.originalAccount)) {
                accounts.add(new Choice<>(item.id(), item.archived()
                        ? getString(R.string.transaction_archived_name, item.name()) : item.name()));
            }
        }
        options(account, accounts, draft.account());
        List<Choice<String>> types = new ArrayList<>();
        types.add(new Choice<>(null, getString(R.string.transaction_choose)));
        types.add(new Choice<>("INCOME", getString(R.string.category_income)));
        types.add(new Choice<>("EXPENSE", getString(R.string.category_expense)));
        options(type, types, draft.type());
        updateCategories(draft.type(), draft.category());
        amount.setText(draft.amount());
        merchant.setText(draft.merchant());
        note.setText(draft.note());
        selectedDate = draft.date();
        showDate();
        initialized = true;
        AdapterView.OnItemSelectedListener selection = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (parent == type && !Objects.equals(value(type), categoryType)) {
                    updateCategories(value(type), null);
                }
                captureDraft();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        account.setOnItemSelectedListener(selection);
        type.setOnItemSelectedListener(selection);
        category.setOnItemSelectedListener(selection);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { captureDraft(); }
            @Override public void afterTextChanged(Editable text) {}
        };
        for (EditText field : new EditText[]{amount, merchant, note}) field.addTextChangedListener(watcher);
    }

    private void updateCategories(String kind, Long selected) {
        updatingCategory = true;
        categoryType = kind;
        List<Choice<Long>> choices = new ArrayList<>();
        choices.add(new Choice<>(null, getString(R.string.category_unspecified)));
        boolean sinhala = "si".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        for (var item : model.categories) {
            if (Objects.equals(item.kind(), kind)) {
                String name = sinhala && item.nameSi() != null && !item.nameSi().trim().isEmpty()
                        ? item.nameSi() : item.name();
                choices.add(new Choice<>(item.id(), name));
            }
        }
        options(category, choices, selected);
        updatingCategory = false;
    }

    private void captureDraft() {
        if (!initialized || updatingCategory || !model.work.isCurrent() || model.work.busy()) return;
        model.draft = new Draft(value(account), value(category), value(type), amount.getText().toString(),
                selectedDate, merchant.getText().toString(), note.getText().toString());
    }

    private void pickDate() {
        if (!verify() || model.work.busy()) return;
        LocalDate initial = selectedDate.isEmpty() ? LocalDate.now() : LocalDate.parse(selectedDate);
        showSecure(new DatePickerDialog(this, (picker, year, month, day) -> {
            if (!verify() || model.work.busy()) return;
            selectedDate = LocalDate.of(year, month + 1, day).toString();
            showDate();
            captureDraft();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()));
    }

    private void showDate() {
        date.setText(selectedDate.isEmpty() ? getString(R.string.transaction_choose_date) : selectedDate);
    }

    private void save() {
        if (!verify() || model.work.busy()) return;
        captureDraft();
        char decimal = DecimalFormatSymbols.getInstance(
                getResources().getConfiguration().getLocales().get(0)).getDecimalSeparator();
        if (model.retry.uncertain) {
            if (!model.retry.reviewed) return;
            showSecure(new AlertDialog.Builder(this).setMessage(R.string.transaction_confirm_resubmit)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.transaction_submit_again, (dialog, which) -> {
                        if (verify()) model.submit(decimal, true);
                    }).create());
        } else {
            model.submit(decimal, false);
        }
    }

    private record Choice<T>(T value, String name) {
        @Override public String toString() { return name; }
    }

    private <T> void options(Spinner spinner, List<Choice<T>> choices, T selected) {
        var adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int i = 0; i < choices.size(); i++) {
            if (Objects.equals(choices.get(i).value(), selected)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(Spinner spinner) {
        Choice<T> chosen = (Choice<T>) spinner.getSelectedItem();
        return chosen == null ? null : chosen.value();
    }
}
