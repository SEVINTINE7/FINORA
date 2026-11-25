package lk.finora.app.ui.staff;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.StaffProvisioningRepository;
import lk.finora.app.data.repo.StaffProvisioningRepository.Provisioned;
import lk.finora.app.data.repo.StaffProvisioningRepository.Staff;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class StaffAccessActivity extends SecureActivity {
    public static final String ADMINISTRATION = "staff_administration";
    private LinearLayout page;
    private Work work;
    private StaffProvisioningRepository repository;
    private List<Staff> accounts = List.of();
    private AlertDialog dialog;
    private Call activation;
    private EditText temporary;
    private EditText password;
    private boolean visible;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        var scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        page.setPadding(padding, padding, padding, padding);
        scroll.addView(page);
        setContentView(scroll);
        if (getIntent().getBooleanExtra(ADMINISTRATION, false)) {
            work = new Work(ProtectedRequests.currentAdmin(), () -> accounts = List.of());
            if (!work.isCurrent()) { finish(); return; }
            repository = new StaffProvisioningRepository(work.requests);
            work.changes.observe(this, ignored -> renderAdmin());
            load();
        } else if (signedIn()) {
            finish();
        } else {
            renderActivation();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        if (work == null && signedIn()) finish();
    }

    @Override protected void onStop() {
        visible = false;
        if (dialog != null) dialog.dismiss();
        if (activation != null) activation.cancel();
        activation = null;
        if (temporary != null) temporary.setText("");
        if (password != null) password.setText("");
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (work != null) work.close();
        super.onDestroy();
    }

    private static boolean signedIn() {
        TokenStore store = TokenStore.current();
        return store != null && store.session() != null;
    }

    private void renderActivation() {
        heading(R.string.staff_activate);
        var email = field(R.string.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        temporary = field(R.string.staff_temporary, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password = field(R.string.staff_new_password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        page.addView(email);
        page.addView(temporary);
        page.addView(password);
        var status = new TextView(this);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(status);
        button(R.string.staff_activate, () -> {
            if (activation != null || signedIn()) return;
            if (lk.finora.app.BuildConfig.API_BASE_URL.isEmpty()) {
                status.setText(R.string.staff_activation_failed);
                return;
            }
            Call call = StaffProvisioningRepository.activate(text(email), text(temporary), text(password));
            temporary.setText("");
            password.setText("");
            activation = call;
            status.setText(R.string.records_loading);
            call.enqueue(new Callback() {
                @Override public void onFailure(Call completed, IOException failure) { finishRequest(false); }
                @Override public void onResponse(Call completed, Response response) {
                    try (response) { finishRequest(response.code() == 204); }
                }
                private void finishRequest(boolean success) {
                    runOnUiThread(() -> {
                        if (activation != call || !visible || signedIn()) return;
                        activation = null;
                        status.setText(success ? R.string.staff_activation_done : R.string.staff_activation_failed);
                    });
                }
            });
        });
    }

    private void load() {
        work.run(repository::list, response -> {
            var items = AccountRepository.read(response, Staff[].class);
            for (var item : items) if (item == null || item.id() < 1 || item.email() == null || item.fullName() == null
                    || (!"ACTIVE".equals(item.status()) && !"DISABLED".equals(item.status()))) {
                throw new IOException("Invalid staff account response");
            }
            return List.of(items);
        }, items -> accounts = items, false);
    }

    private void renderAdmin() {
        page.removeAllViews();
        if (!work.isCurrent()) {
            if (dialog != null) dialog.dismiss();
            finish();
            return;
        }
        heading(R.string.staff_manage);
        if (work.busy()) { label(getString(R.string.records_loading)); return; }
        if (work.error != 0) label(getString(work.error));
        button(R.string.records_retry, this::load);
        button(R.string.staff_create, this::promptProvision);
        for (var item : accounts) {
            label(getString(R.string.staff_account_summary, item.fullName(), item.email()));
            if (item.pending()) {
                button(R.string.staff_reissue, () -> confirm(R.string.staff_reissue, item.email(),
                        () -> reveal(() -> repository.reissue(item.id()))));
            } else {
                boolean enable = "DISABLED".equals(item.status());
                int action = enable ? R.string.staff_enable : R.string.staff_disable;
                button(action, () -> confirm(action, item.email(), () -> work.run(
                        () -> repository.setEnabled(item.id(), enable), Work::empty, unused -> load(), false)));
            }
        }
    }

    private void promptProvision() {
        var form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        var email = field(R.string.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        var name = field(R.string.staff_full_name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(email);
        form.addView(name);
        dialog = showSecure(new AlertDialog.Builder(this).setTitle(R.string.staff_create).setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (prompt, which) ->
                        reveal(() -> repository.provision(text(email), text(name)))).create());
    }

    private void reveal(Supplier<Call> call) {
        work.run(call, response -> {
            var value = AccountRepository.read(response, Provisioned.class);
            if (value.id() < 1 || value.temporaryPassword() == null
                    || !value.temporaryPassword().matches("[A-Za-z0-9_-]{32}")) {
                throw new IOException("Invalid staff provision response");
            }
            return value;
        }, value -> {
            if (visible && work.isCurrent()) {
                dialog = showSecure(new AlertDialog.Builder(this).setTitle(R.string.staff_temporary)
                        .setMessage(getString(R.string.staff_secret_once, value.temporaryPassword()))
                        .setPositiveButton(android.R.string.ok, null).create());
            }
            load();
        }, true);
    }

    private void confirm(int title, String message, Runnable action) {
        dialog = showSecure(new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (prompt, which) -> action.run()).create());
    }

    private EditText field(int name, int inputType) {
        var edit = new EditText(this);
        edit.setHint(name);
        edit.setInputType(inputType);
        edit.setSaveEnabled(false);
        edit.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        edit.setSingleLine();
        return edit;
    }

    private static String text(EditText field) { return field.getText().toString(); }
    private TextView label(String text) {
        var label = new TextView(this);
        label.setText(text);
        page.addView(label);
        return label;
    }
    private void heading(int title) { ViewCompat.setAccessibilityHeading(label(getString(title)), true); }
    private void button(int title, Runnable action) {
        var button = new Button(this);
        button.setText(title);
        button.setOnClickListener(view -> action.run());
        page.addView(button);
    }
}
