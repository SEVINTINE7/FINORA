package lk.finora.app.ui.sms;

import android.app.DatePickerDialog;
import android.os.CancellationSignal;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.sms.SmsParseResult.Parsed;
import okhttp3.Call;
import okhttp3.Response;

final class SmsTransactionPreview {
    private record Choices(List<AccountDto> accounts, List<CategoryDto> categories, boolean available) {}
    private interface Read<T> { T read(Response response) throws IOException; }

    private static final Choices UNAVAILABLE = new Choices(List.of(), List.of(), false);
    private final AppCompatActivity activity;
    private final TokenStore store;
    private final Object login;
    private final SmsLocalTask lookup = new SmsLocalTask();
    private final ArrayList<SmsPreviewDraft> drafts = new ArrayList<>();
    private AlertDialog dialog;
    private DatePickerDialog datePicker;

    SmsTransactionPreview(AppCompatActivity activity, TokenStore store, Object login) {
        this.activity = activity;
        this.store = store;
        this.login = login;
    }

    void replace(List<Parsed> rows) {
        clear();
        for (Parsed row : rows) drafts.add(new SmsPreviewDraft(row));
    }

    void open() {
        ProtectedRequests requests;
        synchronized (store) {
            var current = store.verifiedSession();
            if (current == null || current.loginIdentity() != login) return;
            requests = ProtectedRequests.currentUser();
        }
        if (drafts.isEmpty() || requests == null) return;
        var loading = new AlertDialog.Builder(activity).setTitle(R.string.sms_transaction_preview)
                .setMessage(R.string.records_loading).setNegativeButton(android.R.string.cancel, null).create();
        display(loading);
        loading.setOnDismissListener(ignored -> lookup.cancel());
        lookup.start((signal, cancelled) -> {
            try {
                var accounts = read(new AccountRepository(requests).list(), signal, AccountRepository::rows);
                if (cancelled.getAsBoolean()) return UNAVAILABLE;
                var categories = read(new CategoryRepository(requests).list(), signal, CategoryRepository::rows);
                return new Choices(accounts, categories, true);
            } catch (IOException | IllegalStateException failure) {
                return UNAVAILABLE;
            }
        }, result -> showRows(requests, result), () -> showRows(requests, UNAVAILABLE));
    }

    private static <T> T read(Call call, CancellationSignal signal, Read<T> decode) throws IOException {
        signal.setOnCancelListener(call::cancel);
        try (var response = call.execute()) {
            return decode.read(response);
        } finally {
            signal.setOnCancelListener(null);
        }
    }

    private void showRows(ProtectedRequests requests, Choices choices) {
        if (!requests.isCurrent() || drafts.isEmpty()) return;
        var list = panel();
        text(list, R.string.sms_transaction_preview);
        text(list, R.string.sms_preview_unavailable);
        if (!choices.available()) text(list, R.string.sms_preview_choices_unavailable);
        String[] rows = new String[drafts.size()];
        for (int index = 0; index < rows.length; index++) {
            var draft = drafts.get(index);
            rows[index] = activity.getString(draft.selected() ? R.string.sms_preview_selected
                    : R.string.sms_preview_deselected, SmsLocalResults.text(activity, draft.fields()));
        }
        display(new AlertDialog.Builder(activity).setCustomTitle(list)
                .setItems(rows, (ignored, index) -> {
                    if (requests.isCurrent() && index < drafts.size()) edit(requests, choices, index);
                }).setNegativeButton(android.R.string.cancel, null).create());
    }

    private void edit(ProtectedRequests requests, Choices choices, int index) {
        var draft = drafts.get(index);
        var fields = draft.fields();
        var form = panel();
        var amount = editText(form, R.string.transaction_amount, fields.amount().toPlainString(),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 16);
        var date = new android.widget.Button(activity);
        var selectedDate = new LocalDate[]{fields.date()};
        date.setText(activity.getString(R.string.sms_preview_date, fields.date().toString()));
        date.setOnClickListener(view -> {
            var day = selectedDate[0];
            datePicker = new DatePickerDialog(activity, (ignored, year, month, selection) -> {
                if (!requests.isCurrent()) return;
                selectedDate[0] = LocalDate.of(year, month + 1, selection);
                date.setText(activity.getString(R.string.sms_preview_date, selectedDate[0].toString()));
            }, day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
            var window = datePicker.getWindow();
            if (window != null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            datePicker.show();
        });
        form.addView(date);
        var merchant = editText(form, R.string.transaction_merchant, fields.merchant(),
                InputType.TYPE_CLASS_TEXT, 100);
        var accounts = choices.accounts().stream().filter(item -> !item.archived()).collect(java.util.stream.Collectors.toList());
        var categories = choices.categories().stream()
                .filter(item -> draft.categoryKind().equals(item.kind())).collect(java.util.stream.Collectors.toList());
        boolean sinhala = "si".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
        var account = spinner(form, R.string.transaction_account, accounts.stream().map(AccountDto::name).collect(java.util.stream.Collectors.toList()));
        var category = spinner(form, R.string.transaction_category, categories.stream()
                .map(item -> sinhala && item.nameSi() != null && !item.nameSi().isBlank()
                        ? item.nameSi() : item.name()).collect(java.util.stream.Collectors.toList()));
        for (int i = 0; i < accounts.size(); i++) {
            if (java.util.Objects.equals(draft.accountId(), accounts.get(i).id())) account.setSelection(i + 1);
        }
        for (int i = 0; i < categories.size(); i++) {
            if (java.util.Objects.equals(draft.categoryId(), categories.get(i).id())) category.setSelection(i + 1);
        }
        account.setEnabled(choices.available());
        category.setEnabled(choices.available());
        var select = new CheckBox(activity);
        select.setText(R.string.sms_preview_include);
        select.setChecked(draft.selected());
        form.addView(select);
        var scroll = new ScrollView(activity);
        scroll.addView(form);
        var editor = new AlertDialog.Builder(activity).setTitle(R.string.records_edit).setView(scroll)
                .setNegativeButton(android.R.string.cancel, (ignored, which) -> showRows(requests, choices))
                .setPositiveButton(R.string.sms_preview_apply, null).create();
        editor.setOnCancelListener(ignored -> showRows(requests, choices));
        display(editor);
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!requests.isCurrent() || index >= drafts.size()) return;
            var value = SmsPreviewDraft.amount(amount.getText().toString().trim());
            if (value == null) {
                amount.setError(activity.getString(R.string.transaction_amount_invalid));
                return;
            }
            int a = account.getSelectedItemPosition() - 1;
            int c = category.getSelectedItemPosition() - 1;
            Long accountId = a < 0 ? null : accounts.get(a).id();
            Long categoryId = c < 0 ? null : categories.get(c).id();
            drafts.set(index, draft.edit(value, selectedDate[0], merchant.getText().toString().trim(),
                    choices.available() ? accountId : draft.accountId(),
                    choices.available() ? categoryId : draft.categoryId(), select.isChecked()));
            showRows(requests, choices);
        });
    }

    private LinearLayout panel() {
        var panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = activity.getResources().getDimensionPixelSize(R.dimen.finora_outer_margin);
        panel.setPadding(padding, padding, padding, padding);
        panel.setSaveFromParentEnabled(false);
        return panel;
    }

    private void text(LinearLayout parent, int label) {
        var text = new TextView(activity);
        text.setText(label);
        parent.addView(text);
    }

    private EditText editText(LinearLayout parent, int hint, String value, int inputType, int maxLength) {
        var field = new EditText(activity);
        field.setSaveEnabled(false);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        field.setText(value);
        parent.addView(field);
        return field;
    }

    private Spinner spinner(LinearLayout parent, int label, List<String> names) {
        var values = new ArrayList<String>();
        values.add(activity.getString(R.string.sms_preview_no_selection));
        values.addAll(names);
        var spinner = new Spinner(activity);
        spinner.setId(View.generateViewId());
        spinner.setSaveEnabled(false);
        var title = new TextView(activity);
        title.setText(label);
        title.setLabelFor(spinner.getId());
        parent.addView(title);
        var adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        parent.addView(spinner);
        return spinner;
    }

    private void display(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next;
        var window = next.getWindow();
        if (window != null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        next.show();
    }

    void clear() {
        lookup.cancel();
        drafts.clear();
        if (datePicker != null) datePicker.dismiss();
        datePicker = null;
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    void close() {
        clear();
        lookup.close();
    }
}
