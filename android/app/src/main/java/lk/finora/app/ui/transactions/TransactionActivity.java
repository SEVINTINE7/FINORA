package lk.finora.app.ui.transactions;

import android.content.Intent;
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

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lk.finora.app.R;
import lk.finora.app.ui.FinancialCard;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.repo.TransactionRepository.Filters;
import lk.finora.app.data.repo.TransactionRepository.Row;
import lk.finora.app.ui.accounts.AccountListActivity;

public final class TransactionActivity extends SecureActivity {
    private TransactionViewModel model;
    private LinearLayout rows;
    private TextView status;
    private Button filter;
    private Button retry;
    private Button previous;
    private Button next;
    static final String REVIEW_CREATE = "review_uncertain_create";
    private boolean review;
    private Button finishReview;
    private final androidx.activity.result.ActivityResultLauncher<Intent> editForm =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (verify() && result.getResultCode() == RESULT_OK) {
                    model.load(model.pagination.page().page());
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(TransactionViewModel.class);
        if (!verify()) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        LinearLayout content = column();
        setContentView(scroll(content));
        review = getIntent().getBooleanExtra(REVIEW_CREATE, false);
        if (review) {
            finishReview = button(content, R.string.transaction_records_reviewed, () -> {
                if (verify() && model.canFinishReview()) {
                    setResult(RESULT_OK);
                    finish();
                }
            });
        }
        TextView title = label(content, R.string.transaction_title);
        title.setTextSize(24);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        filter = button(content, R.string.transaction_filters, this::showFilters);
        status = label(content, R.string.records_loading);
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
        FinancialCard.showState(status, message, !busy && failed);
        retry.setVisibility(failed ? View.VISIBLE : View.GONE);
        filter.setEnabled(model.ready && !busy);
        previous.setEnabled(!busy && page.page() > 0);
        next.setEnabled(!busy && page.page() + 1 < page.totalPages());
        if (finishReview != null) finishReview.setEnabled(model.canFinishReview());
        rows.removeAllViews();
        for (Row row : page.items()) {
            String description = getString(R.string.transaction_row, row.txnDate(),
                    getString(typeLabel(row.type())), row.amount().toPlainString());
            if (row.merchant() != null) description += "\n" + row.merchant();
            var body = column();
            int inset = getResources().getDimensionPixelSize(R.dimen.finora_space_s);
            body.setPadding(inset, 0, inset, inset);
            boolean income = "INCOME".equals(row.type());
            String heading = row.merchant();
            if (heading == null || heading.isBlank()) heading = getString(typeLabel(row.type()));
            TextView title = new TextView(this);
            title.setTextAppearance(R.style.FinoraText_Label);
            title.setText(heading);
            androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
            body.addView(title);
            TextView details = new TextView(this);
            details.setTextAppearance(R.style.FinoraText_Body);
            details.setText(getString(R.string.transaction_card_details, categoryName(row), row.txnDate()));
            body.addView(details);
            TextView amount = new TextView(this);
            amount.setTextAppearance(R.style.FinoraText_Label);
            amount.setTextColor(getColor(income ? R.color.finora_green : R.color.finora_error));
            amount.setGravity(android.view.Gravity.END);
            amount.setText(getString(income ? R.string.transaction_card_credit : R.string.transaction_card_debit,
                    row.amount().toPlainString()));
            amount.setContentDescription(getString(income ? R.string.dashboard_income : R.string.dashboard_expense,
                    row.amount().toPlainString()));
            body.addView(amount);
            int icon = income ? R.drawable.finora_nav_insights : R.drawable.finora_nav_records;
            if (review) {
                rows.addView(FinancialCard.wrap(body, description, icon));
                continue;
            }
            Button edit = button(body, R.string.records_edit, () -> {
                if (verify() && !model.work.busy()) {
                    editForm.launch(new Intent(this, TransactionFormActivity.class)
                            .putExtra(TransactionFormActivity.ID, row.id()));
                }
            });
            edit.setContentDescription(getString(R.string.records_edit_named, description));
            Button remove = button(body, R.string.records_remove, () -> AccountListActivity.confirm(
                    this, model.work, R.string.transaction_remove, () -> model.delete(row.id())));
            remove.setContentDescription(getString(R.string.records_remove_named, description));
            edit.setEnabled(!busy);
            remove.setEnabled(!busy);
            rows.addView(FinancialCard.wrap(body, description, icon, edit, remove));
        }
    }

    private String categoryName(Row row) {
        for (var category : model.categories) {
            if (row.categoryId() != null && category.id() == row.categoryId()) {
                boolean sinhala = "si".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
                return sinhala && category.nameSi() != null && !category.nameSi().isBlank()
                        ? category.nameSi() : category.name();
            }
        }
        return getString(R.string.dashboard_uncategorized);
    }

    private void showFilters() {
        if (!verify()) return;
        Filters current = model.filters;
        LinearLayout fields = column();
        EditText from = input(fields, R.string.transaction_from, current.from(), InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_DATE);
        EditText to = input(fields, R.string.transaction_to, current.to(), from.getInputType());
        Spinner type = types(fields, current.type());
        Spinner account = choices(fields, R.string.account_list_title, accounts(),
                current.accountId());
        Spinner category = choices(fields, R.string.category_list_title, categories(),
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

    private List<Choice<Long>> accounts() {
        List<Choice<Long>> entries = new ArrayList<>();
        entries.add(new Choice<>(null, getString(R.string.transaction_all)));
        for (var account : model.accounts) {
            entries.add(new Choice<>(account.id(), account.archived()
                    ? getString(R.string.transaction_archived_name, account.name()) : account.name()));
        }
        return entries;
    }

    private List<Choice<Long>> categories() {
        List<Choice<Long>> entries = new ArrayList<>();
        entries.add(new Choice<>(null, getString(R.string.transaction_all)));
        boolean sinhala = "si".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        for (var category : model.categories) {
            String name = sinhala && category.nameSi() != null && !category.nameSi().trim().isEmpty()
                    ? category.nameSi() : category.name();
            entries.add(new Choice<>(category.id(), name));
        }
        return entries;
    }

    private Spinner types(LinearLayout fields, String value) {
        List<Choice<String>> entries = new ArrayList<>();
        entries.add(new Choice<>(null, getString(R.string.transaction_all)));
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
        return showSecure(new AlertDialog.Builder(this).setTitle(title).setView(scroll(fields))
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(action, null).create());
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
