package lk.finora.app.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.CompoundButton;
import androidx.appcompat.widget.SwitchCompat;
import lk.finora.app.data.repo.AlertRepository;
import lk.finora.app.notifications.AlertNotifications;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.databinding.ActivitySettingsBinding;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.auth.HomeViewModel;
import lk.finora.app.ui.auth.ProfileLanguageButton;
import lk.finora.app.ui.auth.SessionNavigation;

/**
 * Account settings for a USER (T07.5): language (applies at once, saved via PATCH /me, D60), withdraw consent,
 * and the entries to data download and deletion.
 */
public final class SettingsActivity extends SecureActivity {
    private SettingsViewModel model;
    private HomeViewModel session;
    private Session entry;
    private AlertDialog sensitiveDialog;
    private boolean renderingAlerts;
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> renderNotifications());
    private final androidx.activity.result.ActivityResultLauncher<String> exportTarget =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (model == null || !model.awaitingExportDestination) return;
                model.awaitingExportDestination = false;
                if (uri != null && AccountListActivity.verify(this, model.work)) {
                    model.exportData(getContentResolver(), uri);
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(SettingsViewModel.class);
        session = new ViewModelProvider(this).get(HomeViewModel.class);
        TokenStore store = TokenStore.current();
        entry = store == null ? null : store.verifiedSession();
        if (entry == null) {
            SessionNavigation.openLogin(this, false);
            return;
        }
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        var binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        for (int heading : new int[]{R.id.settings_heading, R.id.settings_language_heading,
                R.id.settings_privacy_heading}) {
            ViewCompat.setAccessibilityHeading(findViewById(heading), true);
        }
        ((FrameLayout) findViewById(R.id.settings_language))
                .addView(ProfileLanguageButton.create(this, entry, session::signingOut));
        bindAlerts();
        findViewById(R.id.settings_withdraw).setOnClickListener(view -> confirmWithdraw());
        var export = binding.settingsExport;
        export.setOnClickListener(view -> {
            if (!AccountListActivity.verify(this, model.work) || model.work.busy()
                    || model.awaitingExportDestination) return;
            model.awaitingExportDestination = true;
            exportTarget.launch("finora-export-" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")) + ".json");
        });
        findViewById(R.id.settings_delete).setOnClickListener(view -> confirmDeletion());
        session.signedOut().observe(this, failed -> {
            if (failed != null) SessionNavigation.openLogin(this, failed);
        });
        model.work.changes.observe(this, ignored -> render());
        model.alertWork.changes.observe(this, ignored -> render());
    }

    /** T34.5: three alert switches and the Android 13+ notification permission. */
    private void bindAlerts() {
        ViewCompat.setAccessibilityHeading(findViewById(R.id.settings_alerts_heading), true);
        CompoundButton.OnCheckedChangeListener changed = (button, checked) -> {
            if (renderingAlerts || model.alertSettings == null) return;
            model.saveAlerts(new AlertRepository.Settings(isOn(R.id.settings_alert_budget),
                    isOn(R.id.settings_alert_bill), isOn(R.id.settings_alert_goal)));
        };
        for (int id : new int[]{R.id.settings_alert_budget, R.id.settings_alert_bill, R.id.settings_alert_goal}) {
            ((SwitchCompat) findViewById(id)).setOnCheckedChangeListener(changed);
        }
        findViewById(R.id.settings_notifications_allow).setOnClickListener(view -> allowNotifications());
        AlertNotifications.createChannel(this);
        model.loadAlerts();
    }

    private boolean isOn(int id) {
        return ((SwitchCompat) findViewById(id)).isChecked();
    }

    private void allowNotifications() {
        String permission = android.Manifest.permission.POST_NOTIFICATIONS;
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(permission)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
                && (!model.askedNotifications || shouldShowRequestPermissionRationale(permission))) {
            model.askedNotifications = true;
            notificationPermission.launch(permission);
            return;
        }
        // Refused for good, or switched off in Android settings: only the system screen can turn it back on
        startActivity(new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    private void renderAlerts() {
        AlertRepository.Settings settings = model.alertSettings;
        boolean enabled = settings != null && !model.alertWork.busy();
        renderingAlerts = true;
        int[] ids = {R.id.settings_alert_budget, R.id.settings_alert_bill, R.id.settings_alert_goal};
        boolean[] values = settings == null ? new boolean[3]
                : new boolean[]{settings.budget, settings.bill, settings.goal};
        for (int i = 0; i < ids.length; i++) {
            SwitchCompat toggle = findViewById(ids[i]);
            if (!model.alertWork.busy()) toggle.setChecked(values[i]);
            toggle.setEnabled(enabled);
        }
        renderingAlerts = false;
        renderNotifications();
    }

    private void renderNotifications() {
        int visibility = AlertNotifications.allowed(this) ? View.GONE : View.VISIBLE;
        findViewById(R.id.settings_notifications_status).setVisibility(visibility);
        findViewById(R.id.settings_notifications_allow).setVisibility(visibility);
    }

    @Override protected void onResume() {
        super.onResume();
        if (model != null && !model.withdrawn && !model.deleted && !session.signingOut()) {
            AccountListActivity.verify(this, model.work);
            if (findViewById(R.id.settings_notifications_allow) != null) {
                renderNotifications();
                model.loadAlerts();
            }
        }
    }

    private void confirmDeletion() {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy()) return;
        sensitiveDialog = showSecure(new AlertDialog.Builder(this).setTitle(R.string.settings_delete)
                .setMessage(R.string.settings_delete_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> promptDeletionPassword()).create());
    }

    private void promptDeletionPassword() {
        if (!AccountListActivity.verify(this, model.work)) return;
        var password = new EditText(this);
        password.setHint(R.string.password);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setSaveEnabled(false);
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        sensitiveDialog = showSecure(new AlertDialog.Builder(this).setTitle(R.string.settings_delete)
                .setView(password).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_delete_action, null).create());
        sensitiveDialog.setOnDismissListener(ignored -> password.setText(""));
        sensitiveDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = password.getText().toString();
            if (value.isBlank() || value.length() > 72) {
                password.setError(getString(R.string.records_invalid));
                return;
            }
            model.deleteData(value);
            sensitiveDialog.dismiss();
        });
    }

    @Override protected void onStop() {
        if (sensitiveDialog != null) sensitiveDialog.dismiss();
        super.onStop();
    }

    private void confirmWithdraw() {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy()) return;
        showSecure(new AlertDialog.Builder(this)
                .setTitle(R.string.settings_withdraw)
                .setMessage(R.string.settings_withdraw_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_withdraw_action, (dialog, which) -> model.withdrawConsent())
                .create());
    }

    private void render() {
        if (model.withdrawn || model.deleted || session.signingOut()) {
            // Consent is gone and the server revoked the refresh tokens: end the local session too (D15)
            findViewById(R.id.settings_content).setVisibility(View.INVISIBLE);
            session.logout(entry);
            return;
        }
        if (!AccountListActivity.verify(this, model.work)) return;
        boolean busy = model.work.busy();
        findViewById(R.id.settings_withdraw).setEnabled(!busy);
        findViewById(R.id.settings_delete).setEnabled(!busy);
        renderAlerts();
        TextView status = findViewById(R.id.records_status);
        int message = busy ? R.string.records_loading : model.work.error != 0 ? model.work.error
                : model.alertWork.error != 0 ? model.alertWork.error
                : model.exported ? R.string.settings_export_saved : 0;
        status.setText(message == 0 ? "" : getString(message));
    }
}
