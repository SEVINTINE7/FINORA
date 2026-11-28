package lk.finora.app.ui.sms;

import android.app.DatePickerDialog;
import android.os.CancellationSignal;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.CategoryDto;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.data.repo.TransactionRepository;
import lk.finora.app.data.repo.TransactionRepository.SmsBatchItem;
import lk.finora.app.data.repo.TransactionRepository.SmsBatchResult;
import lk.finora.app.data.repo.TransactionRepository.SmsTransaction;
import lk.finora.app.databinding.ItemSmsReviewBinding;
import lk.finora.app.databinding.ViewSmsReviewBinding;
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
    private record SaveResult(SmsBatchResult report, boolean rejected) {}
    private final String source;
    private final SmsLocalTask save = new SmsLocalTask(12);
    private final SmsPendingBatch pending;
    private final SmsLocalTask lookup = new SmsLocalTask();
    private final ArrayList<SmsPreviewDraft> drafts = new ArrayList<>();
    private AlertDialog dialog;
    private DatePickerDialog datePicker;

    SmsTransactionPreview(AppCompatActivity activity, TokenStore store, Object login, String source) {
        this.activity = activity;
        this.store = store;
        this.login = login;
        this.source = source;
        pending = new androidx.lifecycle.ViewModelProvider(activity).get(SmsPendingBatch.class);
        pending.bind(login);
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
        if (requests == null) return;
        if (hasPending()) {
            confirm(requests, pending.items(), true);
            return;
        }
        if (drafts.isEmpty()) return;
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

    /** T67.2/D71: one full-screen sheet with a card per transaction; account, category and tick are chosen in place. */
    private void showRows(ProtectedRequests requests, Choices choices) {
        if (!requests.isCurrent() || drafts.isEmpty()) return;
        var inflater = activity.getLayoutInflater();
        var sheet = ViewSmsReviewBinding.inflate(inflater);
        androidx.core.view.ViewCompat.setAccessibilityHeading(sheet.reviewTitle, true);
        sheet.reviewUnavailable.setVisibility(choices.available() ? View.GONE : View.VISIBLE);
        var accounts = choices.accounts().stream().filter(item -> !item.archived())
                .collect(java.util.stream.Collectors.toList());
        boolean sinhala = "si".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
        var review = new AlertDialog.Builder(activity).setView(sheet.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sms_batch_save, (ignored, which) -> confirmSelection(requests, choices))
                .create();
        var warnings = new ArrayList<View>();
        Runnable counts = () -> {
            int selected = 0;
            for (int i = 0; i < drafts.size(); i++) {
                var draft = drafts.get(i);
                if (draft.selected()) selected++;
                warnings.get(i).setVisibility(choices.available() && draft.selected() && draft.accountId() == null
                        ? View.VISIBLE : View.GONE);
            }
            sheet.reviewCount.setText(activity.getString(R.string.sms_review_selected, selected, drafts.size()));
            var save = review.getButton(AlertDialog.BUTTON_POSITIVE);
            if (save != null) {
                save.setText(activity.getResources().getQuantityString(R.plurals.sms_review_save, selected, selected));
                save.setEnabled(selected > 0);
            }
        };
        for (int index = 0; index < drafts.size(); index++) {
            final int at = index;
            var draft = drafts.get(at);
            var card = ItemSmsReviewBinding.inflate(inflater, sheet.reviewCards, false);
            var fields = draft.fields();
            String title = ImportRows.title(activity, fields);
            ImportRows.bind(card.reviewRow, activity, ImportRows.income(fields), title,
                    ImportRows.date(activity, fields.date()), fields.amount());
            card.reviewRow.importCheck.setVisibility(View.VISIBLE);
            card.reviewRow.importCheck.setChecked(draft.selected());
            card.reviewRow.importCheck.setContentDescription(activity.getString(R.string.sms_review_include, title));
            card.reviewRow.importCheck.setOnCheckedChangeListener((button, checked) -> {
                if (!requests.isCurrent() || at >= drafts.size()) return;
                var current = drafts.get(at);
                drafts.set(at, current.edit(current.fields().amount(), current.fields().date(),
                        current.fields().merchant() == null ? "" : current.fields().merchant(),
                        current.accountId(), current.categoryId(), checked));
                counts.run();
            });
            var categories = choices.categories().stream().filter(item -> draft.categoryKind().equals(item.kind()))
                    .collect(java.util.stream.Collectors.toList());
            choose(card.reviewAccount, accounts.stream().map(AccountDto::name).collect(java.util.stream.Collectors.toList()),
                    accounts.stream().map(AccountDto::id).collect(java.util.stream.Collectors.toList()), draft.accountId(),
                    choices.available(), id -> {
                        var current = drafts.get(at);
                        drafts.set(at, current.edit(current.fields().amount(), current.fields().date(),
                                current.fields().merchant() == null ? "" : current.fields().merchant(),
                                id, current.categoryId(), current.selected()));
                        counts.run();
                    }, requests, at);
            choose(card.reviewCategory, categories.stream().map(item -> sinhala && item.nameSi() != null
                            && !item.nameSi().isBlank() ? item.nameSi() : item.name())
                            .collect(java.util.stream.Collectors.toList()),
                    categories.stream().map(CategoryDto::id).collect(java.util.stream.Collectors.toList()), draft.categoryId(),
                    choices.available(), id -> {
                        var current = drafts.get(at);
                        drafts.set(at, current.edit(current.fields().amount(), current.fields().date(),
                                current.fields().merchant() == null ? "" : current.fields().merchant(),
                                current.accountId(), id, current.selected()));
                    }, requests, at);
            card.reviewEdit.setOnClickListener(view -> {
                if (requests.isCurrent() && at < drafts.size()) edit(requests, choices, at);
            });
            warnings.add(card.reviewWarning);
            sheet.reviewCards.addView(card.getRoot());
        }
        display(review);
        var window = review.getWindow();
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(R.color.finora_surface);
        }
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            var button = review.getButton(which);
            if (button != null) button.setTextColor(activity.getColorStateList(R.color.finora_dialog_action));
        }
        counts.run();
    }

    /** Fills a picker with "None selected" plus the names, selects the current id and reports changes. */
    private void choose(Spinner picker, List<String> names, List<Long> ids, Long current, boolean enabled,
                        java.util.function.Consumer<Long> changed, ProtectedRequests requests, int at) {
        var values = new ArrayList<String>();
        values.add(activity.getString(R.string.sms_preview_no_selection));
        values.addAll(names);
        var adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(adapter);
        int position = ids.indexOf(current) + 1;
        picker.setSelection(Math.max(position, 0), false);
        picker.setEnabled(enabled);
        picker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int index, long id) {
                if (!requests.isCurrent() || at >= drafts.size() || !enabled) return;
                changed.accept(index == 0 ? null : ids.get(index - 1));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void confirmSelection(ProtectedRequests requests, Choices choices) {
        if (!requests.isCurrent()) return;
        var selected = new ArrayList<SmsBatchItem>();
        for (var draft : drafts) {
            if (!draft.selected()) continue;
            if (draft.accountId() == null || draft.fields().date().getYear() < 1000
                    || draft.fields().date().getYear() > 9999) {
                cannotSave(requests, choices);
                return;
            }
            var fields = draft.fields();
            selected.add(new SmsBatchItem(draft.dedupHash(), new SmsTransaction(draft.accountId(),
                    draft.categoryId(), draft.categoryKind(), fields.amount(), fields.date().toString(),
                    fields.merchant(), source)));
        }
        if (selected.isEmpty()) {
            cannotSave(requests, choices);
            return;
        }
        confirm(requests, List.copyOf(selected), false);
    }

    private void cannotSave(ProtectedRequests requests, Choices choices) {
        display(new AlertDialog.Builder(activity).setMessage(R.string.sms_batch_invalid_selection)
                .setPositiveButton(android.R.string.ok, (ignored, which) -> showRows(requests, choices)).create());
    }

    private void confirm(ProtectedRequests requests, List<SmsBatchItem> items, boolean retry) {
        if (!requests.isCurrent()) return;
        int message = retry ? R.plurals.sms_batch_uncertain : R.plurals.sms_batch_confirm;
        display(new AlertDialog.Builder(activity).setTitle(R.string.sms_batch_save)
                .setMessage(activity.getResources().getQuantityString(message, items.size(), items.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(retry ? R.string.sms_batch_retry : R.string.sms_batch_save,
                        (ignored, which) -> submit(requests, items)).create());
    }

    private void submit(ProtectedRequests requests, List<SmsBatchItem> items) {
        if (!requests.isCurrent()) return;
        pending.retain(items);
        display(new AlertDialog.Builder(activity).setMessage(R.string.records_loading)
                .setCancelable(false).create());
        save.start((signal, cancelled) -> {
            try {
                var call = new TransactionRepository(requests).saveSmsBatch(items);
                var result = read(call, signal, response -> {
                    if (response.code() == 400 || response.code() == 403) return new SaveResult(null, true);
                    return new SaveResult(TransactionRepository.readSmsBatch(response, items.size()), false);
                });
                return result;
            } catch (IOException | RuntimeException failure) {
                return new SaveResult(null, false);
            }
        }, result -> showSaveResult(requests, items, result),
                () -> showSaveResult(requests, items, new SaveResult(null, false)));
    }

    private void showSaveResult(ProtectedRequests requests, List<SmsBatchItem> items, SaveResult result) {
        if (!requests.isCurrent()) return;
        if (result.report() == null && !result.rejected()) {
            confirm(requests, items, true);
            return;
        }
        pending.clear();
        if (result.rejected()) {
            display(new AlertDialog.Builder(activity).setMessage(R.string.sms_batch_rejected)
                    .setPositiveButton(android.R.string.ok, (ignored, which) -> open()).create());
            return;
        }
        int saved = 0;
        var duplicates = new ArrayList<String>();
        var matches = new StringBuilder();
        for (var item : result.report().items()) {
            if ("SAVED".equals(item.status())) saved++;
            else duplicates.add(Integer.toString(item.index()));
            if (!item.possibleManualMatch().isEmpty()) {
                matches.append("\n").append(activity.getString(R.string.sms_batch_manual,
                        item.index(), item.possibleManualMatch().toString()));
            }
        }
        var summary = new StringBuilder(activity.getString(R.string.sms_batch_saved, saved));
        if (!duplicates.isEmpty()) summary.append("\n").append(activity.getString(
                R.string.sms_batch_duplicates, String.join(", ", duplicates)));
        summary.append(matches);
        drafts.clear();
        display(new AlertDialog.Builder(activity).setTitle(R.string.sms_batch_save)
                .setMessage(summary.toString()).setPositiveButton(android.R.string.ok, null).create());
    }

    /** Amount, date and merchant; account, category and the tick are on the review card. */
    private void edit(ProtectedRequests requests, Choices choices, int index) {
        var draft = drafts.get(index);
        var fields = draft.fields();
        var form = panel();
        var amount = editText(form, R.string.transaction_amount, fields.amount().toPlainString(),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 16);
        var date = new com.google.android.material.button.MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        var selectedDate = new LocalDate[]{fields.date()};
        date.setText(activity.getString(R.string.sms_preview_date, ImportRows.date(activity, fields.date())));
        date.setIconResource(R.drawable.finora_ic_calendar);
        date.setMinHeight(ImportRows.dp(activity, 48));
        date.setOnClickListener(view -> {
            var day = selectedDate[0];
            datePicker = new DatePickerDialog(activity, (ignored, year, month, selection) -> {
                if (!requests.isCurrent()) return;
                selectedDate[0] = LocalDate.of(year, month + 1, selection);
                date.setText(activity.getString(R.string.sms_preview_date, ImportRows.date(activity, selectedDate[0])));
            }, day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
            SecureActivity.showSecure(datePicker);
        });
        form.addView(date);
        var merchant = editText(form, R.string.transaction_merchant, fields.merchant(),
                InputType.TYPE_CLASS_TEXT, 100);
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
            var current = drafts.get(index);
            drafts.set(index, current.edit(value, selectedDate[0], merchant.getText().toString().trim(),
                    current.accountId(), current.categoryId(), current.selected()));
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

    private void display(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next;
        SecureActivity.showSecure(next);
    }

    boolean hasPending() { return !pending.items().isEmpty(); }

    void clear() { stop(false); }

    void stop(boolean changingConfiguration) {
        lookup.cancel();
        save.cancel();
        if (!changingConfiguration) pending.clear();
        drafts.clear();
        if (datePicker != null) datePicker.dismiss();
        datePicker = null;
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    void close(boolean changingConfiguration) {
        stop(changingConfiguration);
        lookup.close();
        save.close();
    }
}
