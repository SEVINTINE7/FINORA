package lk.finora.app.ui.accounts;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.util.function.Consumer;

import lk.finora.app.R;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.FormChoices;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

public final class AccountFormActivity extends SecureActivity {
    private AccountFormViewModel model;
    private boolean filled;
    private EditText name;
    private EditText amount;
    private FormChoices type;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(AccountFormViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_account_form);
        name = findViewById(R.id.form_name);
        amount = findViewById(R.id.account_balance);
        type = choices(this, R.id.form_kind, R.string.account_bank, R.string.account_cash,
                R.string.account_card, R.string.account_wallet);
        watch(name, value -> model.name = value);
        watch(amount, value -> model.amount = value);
        findViewById(R.id.records_save).setOnClickListener(view -> save());
        findViewById(R.id.records_retry).setOnClickListener(view -> {
            if (model.work.ambiguousCreate) {
                reconcile(this, model.work, AccountListActivity.class);
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
        if (filled && model.work.isCurrent()) model.type = type.getSelectedItemPosition();
        super.onPause();
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.saved) { finish(); return; }
        if (model.ready && !filled) {
            name.setText(model.name);
            amount.setText(model.amount);
            if (model.type >= 0) type.setSelection(model.type);
            filled = true;
        }
        boolean enabled = formState(this, model.work, model.ready);
        name.setEnabled(enabled);
        amount.setEnabled(enabled);
        type.setEnabled(enabled);
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || !model.ready || model.work.busy()) return;
        String value = name.getText().toString().trim();
        if (value.isEmpty() || value.length() > 60) {
            FormFields.error(name, R.string.records_name_invalid);
            return;
        }
        BigDecimal balance;
        try {
            balance = AccountFormViewModel.parseBalance(amount.getText().toString());
        } catch (IllegalArgumentException failure) {
            FormFields.error(amount, R.string.account_balance_invalid);
            return;
        }
        model.type = type.getSelectedItemPosition();
        model.save(balance);
    }

    public static boolean formState(AppCompatActivity activity, Work work, boolean ready) {
        boolean enabled = ready && !work.busy();
        activity.findViewById(R.id.records_save).setEnabled(enabled && !work.ambiguousCreate);
        TextView repeat = activity.findViewById(R.id.records_retry);
        repeat.setEnabled(!work.busy() && work.error != 0);
        repeat.setText(work.ambiguousCreate ? R.string.records_reconcile : R.string.records_retry);
        TextView status = activity.findViewById(R.id.records_status);
        int message = work.busy() ? R.string.records_loading : work.error;
        status.setText(message == 0 ? "" : activity.getString(message));
        return enabled;
    }

    public static void reconcile(AppCompatActivity activity, Work work,
            Class<? extends AppCompatActivity> list) {
        if (!AccountListActivity.verify(activity, work)) return;
        showSecure(new AlertDialog.Builder(activity).setMessage(R.string.records_uncertain)
                .setNeutralButton(R.string.records_open_list, (dialog, which) ->
                    activity.startActivity(new Intent(activity, list)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.records_reconciled, (dialog, which) -> {
                    work.ambiguousCreate = false;
                    work.error = 0;
                    work.changed();
                }).create());
    }

    public static FormChoices choices(AppCompatActivity activity, int id, int... labels) {
        FormChoices spinner = activity.findViewById(id);
        String[] values = new String[labels.length];
        for (int i = 0; i < labels.length; i++) values[i] = activity.getString(labels[i]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    public static void watch(EditText input, Consumer<String> change) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                FormFields.clearError(input);
                change.accept(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });
    }
}
