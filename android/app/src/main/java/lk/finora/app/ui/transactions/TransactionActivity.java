package lk.finora.app.ui.transactions;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lk.finora.app.R;
import lk.finora.app.data.repo.TransactionRepository.Edit;
import lk.finora.app.data.repo.TransactionRepository.Filters;
import lk.finora.app.data.repo.TransactionRepository.Row;
import lk.finora.app.ui.accounts.AccountListActivity;

public final class TransactionActivity extends AppCompatActivity {
    private TransactionViewModel model;
    private LinearLayout rows;
    private TextView status;
    private Button filter;
    private Button retry;
    private Button previous;
    private Button next;
    private AlertDialog editor;
    private TextView editStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(TransactionViewModel.class);
        if (!verify()) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        LinearLayout content = column();
        setContentView(scroll(content));
        TextView title = label(content, R.string.transaction_title);
        title.setTextSize(24);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        filter = button(content, R.string.transaction_filters, this::showFilters);
        status = label(content, R.string.records_loading);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        retry = button(content, R.string.records_retry, () -> {
            if (!verify()) return;
            model.retry();
        });
        rows = column();
        content.addView(rows);
        previous = button(content, R.string.transaction_previous, () -> model.load(model.pagination.page().page() - 1));
        next = button(content, R.string.transaction_next, () -> model.load(model.pagination.page().page() + 1));
        model.work.changes.observe(this, ignored -> render());
        model.start();
    }

    @Override protected void onResume() {
        super.onResume();
        verify();
    }

    @Override protected void onDestroy() {
        if (editor != null) {
            editor.setOnDismissListener(null);
            editor.dismiss();
        }
        super.onDestroy();
    }

    private boolean verify() {
        return AccountListActivity.verify(this, model.work);
    }

    private void render() {
        if (!verify()) return;
        boolean busy = model.work.busy();
        boolean failed = model.work.error != 0;
        var page = model.pagination.page();
        int message = busy ? R.string.records_loading : failed ? model.work.error
                : page.items().isEmpty() ? R.string.records_empty : 0;
        status.setText(message == 0 ? "" : getString(message));
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        retry.setVisibility(failed && model.editing == null ? View.VISIBLE : View.GONE);
        filter.setEnabled(model.ready && !busy);
        previous.setEnabled(!busy && page.page() > 0);
        next.setEnabled(!busy && page.page() + 1 < page.totalPages());
        rows.removeAllViews();
        for (Row row : page.items()) {
            String description = getString(R.string.transaction_row, row.txnDate(),
                    getString(typeLabel(row.type())), row.amount().toPlainString());
            if (row.merchant() != null) description += "\n" + row.merchant();
            TextView text = new TextView(this);
            text.setText(description);
            rows.addView(text);
            Button edit = button(rows, R.string.records_edit, () -> model.open(row.id()));
            edit.setContentDescription(getString(R.string.records_edit_named, description));
            Button remove = button(rows, R.string.records_remove, () -> AccountListActivity.confirm(
                    this, model.work, R.string.transaction_remove, () -> model.delete(row.id())));
            remove.setContentDescription(getString(R.string.records_remove_named, description));
            edit.setEnabled(!busy);
            remove.setEnabled(!busy);
        }
        if (model.editing != null) {
            if (editor == null) showEditor(model.editing);
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
            editor.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
            editor.setCancelable(!busy);
            editor.setCanceledOnTouchOutside(false);
            editStatus.setText(message == 0 || (!busy && !failed) ? "" : getString(message));
        } else if (editor != null) {
            editor.dismiss();
            editor = null;
        }
    }

    private void showFilters() {
        if (!verify()) return;
        Filters current = model.filters;
        LinearLayout fields = column();
        EditText from = input(fields, R.string.transaction_from, current.from(), InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE);
        EditText to = input(fields, R.string.transaction_to, current.to(), from.getInputType());
        Spinner type = types(fields, true, current.type());
        Spinner account = choices(fields, R.string.account_list_title, accounts(null, true),
                current.accountId());
        Spinner category = choices(fields, R.string.category_list_title, categories(null, true),
                current.categoryId());
        AlertDialog dialog = dialog(R.string.transaction_filters, fields, R.string.transaction_apply);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!verify()) { dialog.dismiss(); return; }
            try {
                String start = date(from, true);
                String end = date(to, true);
                if (start != null && end != null && start.compareTo(end) > 0) {
                    to.setError(getString(R.string.records_invalid));
                    return;
                }
                model.filter(new Filters(start, end, selection(type), selection(category), selection(account)));
                dialog.dismiss();
            } catch (DateTimeParseException invalidDate) {
                // The invalid field already displays its error.
            }
        });
    }

    private void showEditor(Row row) {
        LinearLayout fields = column();
        Spinner account = choices(fields, R.string.account_list_title, accounts(row.accountId(), false),
                row.accountId());
        Spinner type = types(fields, false, row.type());
        Spinner category = choices(fields, R.string.category_list_title, categories(row.type(), false),
                row.categoryId());
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Long selectedCategory = selection(category);
                setOptions(category, categories(selection(type), false), selectedCategory);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        EditText amount = input(fields, R.string.transaction_amount, row.amount().toPlainString(),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText txnDate = input(fields, R.string.transaction_date, row.txnDate(),
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        EditText merchant = input(fields, R.string.transaction_merchant, row.merchant(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText note = input(fields, R.string.transaction_note, row.note(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editStatus = label(fields, R.string.records_loading);
        editStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        editor = dialog(R.string.records_edit, fields, R.string.records_save);
        editor.setOnDismissListener(ignored -> {
            editor = null;
            if (!isChangingConfigurations() && !model.work.busy()) model.editing = null;
        });
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!verify()) return;
            try {
                Long accountId = selection(account);
                if (accountId == null) {
                    editStatus.setText(R.string.records_invalid);
                    return;
                }
                model.update(TransactionInput.edit(accountId, selection(category), selection(type),
                        value(amount), date(txnDate, false), value(merchant), value(note)));
            } catch (IllegalArgumentException | DateTimeParseException invalid) {
                editStatus.setText(R.string.records_invalid);
            }
        });
    }

    private String date(EditText field, boolean optional) {
        String value = value(field);
        if (optional && value == null) return null;
        try {
            return TransactionInput.date(value == null ? "" : value);
        } catch (DateTimeParseException invalid) {
            field.setError(getString(R.string.records_invalid));
            throw invalid;
        }
    }

    private List<Choice<Long>> accounts(Long original, boolean filterMode) {
        List<Choice<Long>> entries = new ArrayList<>();
        if (filterMode) entries.add(new Choice<>(null, getString(R.string.transaction_all)));
        for (var account : model.accounts) {
            if (filterMode || !account.archived() || Objects.equals(original, account.id())) {
                entries.add(new Choice<>(account.id(), account.archived()
                        ? getString(R.string.transaction_archived_name, account.name()) : account.name()));
            }
        }
        return entries;
    }

    private List<Choice<Long>> categories(String type, boolean filterMode) {
        List<Choice<Long>> entries = new ArrayList<>();
        entries.add(new Choice<>(null, getString(filterMode ? R.string.transaction_all
                : R.string.category_unspecified)));
        boolean sinhala = "si".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        for (var category : model.categories) {
            if (type == null || type.equals(category.kind())) {
                String name = sinhala && category.nameSi() != null && !category.nameSi().trim().isEmpty()
                        ? category.nameSi() : category.name();
                entries.add(new Choice<>(category.id(), name));
            }
        }
        return entries;
    }

    private Spinner types(LinearLayout fields, boolean filterMode, String value) {
        List<Choice<String>> entries = new ArrayList<>();
        if (filterMode) entries.add(new Choice<>(null, getString(R.string.transaction_all)));
        for (String type : new String[]{"INCOME", "EXPENSE"}) {
            entries.add(new Choice<>(type, getString(typeLabel(type))));
        }
        return choices(fields, R.string.category_kind, entries, value);
    }

    private static int typeLabel(String type) {
        return "INCOME".equals(type) ? R.string.category_income : R.string.category_expense;
    }

    private record Choice<T>(T value, String label) {
        @Override public String toString() { return label; }
    }

    private <T> Spinner choices(LinearLayout fields, int title, List<Choice<T>> entries, T value) {
        TextView caption = label(fields, title);
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        caption.setLabelFor(spinner.getId());
        fields.addView(spinner);
        setOptions(spinner, entries, value);
        return spinner;
    }

    private <T> void setOptions(Spinner spinner, List<Choice<T>> entries, T value) {
        ArrayAdapter<Choice<T>> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, entries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int index = 0; index < entries.size(); index++) {
            if (Objects.equals(value, entries.get(index).value())) {
                spinner.setSelection(index);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T selection(Spinner spinner) {
        Choice<T> item = (Choice<T>) spinner.getSelectedItem();
        return item == null ? null : item.value();
    }

    private AlertDialog dialog(int title, LinearLayout fields, int action) {
        return new AlertDialog.Builder(this).setTitle(title).setView(scroll(fields))
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(action, null).show();
    }

    private ScrollView scroll(View content) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        return layout;
    }

    private TextView label(LinearLayout parent, int resource) {
        TextView label = new TextView(this);
        label.setText(resource);
        parent.addView(label);
        return label;
    }

    private Button button(LinearLayout parent, int resource, Runnable action) {
        Button button = new Button(this);
        button.setText(resource);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
        return button;
    }

    private EditText input(LinearLayout parent, int resource, String value, int type) {
        TextView caption = label(parent, resource);
        EditText field = new EditText(this);
        field.setId(View.generateViewId());
        caption.setLabelFor(field.getId());
        field.setInputType(type);
        field.setText(value);
        parent.addView(field);
        return field;
    }

    private static String value(EditText field) {
        String text = field.getText().toString().trim();
        return text.isEmpty() ? null : text;
    }
}
