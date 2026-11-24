package lk.finora.app.ui.sms;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import java.util.Arrays;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import lk.finora.app.sms.SmsInboxReader;
import lk.finora.app.sms.SmsInboxReader.Status;
import lk.finora.app.ui.importer.CsvImportActivity;
import lk.finora.app.ui.transactions.TransactionFormActivity;

public final class SmsAccessActivity extends AppCompatActivity {
    private static final String PERMISSION = Manifest.permission.READ_SMS;
    private static final String REQUESTED = "read_sms_requested";
    private final ActivityResultLauncher<String> permissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> renderPermission());
    private final Runnable sessionChanged = () -> runOnUiThread(this::verifySession);

    private TokenStore store;
    private Object entryIdentity;
    private SharedPreferences preferences;
    private boolean declared;
    private Button allow;
    private Button settings;
    private TextView status;
    private Button scan;
    private TextView scanResult;
    private Button previewButton;
    private SmsTransactionPreview preview;
    private final SmsLocalTask scanTask = new SmsLocalTask();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = TokenStore.current();
        Session session = store == null ? null : store.verifiedSession();
        if (session == null || !"USER".equals(session.role)) {
            finish();
            return;
        }
        entryIdentity = session.loginIdentity();
        declared = declaresReadSms();
        preferences = getSharedPreferences("sms_access", MODE_PRIVATE);
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_sms_access);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.sms_title), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.sms_alternatives_title), true);
        allow = findViewById(R.id.sms_allow);
        settings = findViewById(R.id.sms_settings);
        status = findViewById(R.id.sms_status);
        scan = findViewById(R.id.sms_scan);
        scanResult = findViewById(R.id.sms_scan_result);
        preview = new SmsTransactionPreview(this, store, entryIdentity, "SMS");
        previewButton = findViewById(R.id.sms_scan_preview);
        previewButton.setOnClickListener(view -> { if (verifySession() && canScan()) preview.open(); });
        scan.setOnClickListener(view -> scanInbox());
        allow.setOnClickListener(view -> requestInboxAccess());
        settings.setOnClickListener(view -> openAppSettings());
        findViewById(R.id.sms_no_thanks_action).setOnClickListener(view -> finish());
        findViewById(R.id.sms_paste_entry_action).setOnClickListener(view -> openFallback(PasteSmsActivity.class));
        findViewById(R.id.sms_csv_import_action).setOnClickListener(view -> openFallback(CsvImportActivity.class));
        findViewById(R.id.sms_manual_entry_action).setOnClickListener(view -> openFallback(TransactionFormActivity.class));
        renderPermission();
    }

    private void openFallback(Class<? extends AppCompatActivity> destination) {
        if (verifySession()) {
            startActivity(new Intent(this, destination));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isFinishing() && entryIdentity != null) {
            store.addSessionListener(sessionChanged);
            verifySession();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderPermission();
    }

    @Override
    protected void onStop() {
        clearScan();
        if (entryIdentity != null) {
            store.removeSessionListener(sessionChanged);
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        scanTask.close();
        if (preview != null) preview.close();
        super.onDestroy();
    }

    private boolean verifySession() {
        if (isFinishing() || entryIdentity == null) {
            return false;
        }
        Session current = store.verifiedSession();
        if (current == null || current.loginIdentity() != entryIdentity || !"USER".equals(current.role)) {
            clearScan();
            finish();
            return false;
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private boolean declaresReadSms() {
        try {
            String[] permissions = getPackageManager()
                    .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            return permissions != null && Arrays.asList(permissions).contains(PERMISSION);
        } catch (PackageManager.NameNotFoundException failure) {
            return false;
        }
    }

    private SmsPermissionState permissionState() {
        return SmsPermissionState.resolve(declared,
                checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED,
                preferences.getBoolean(REQUESTED, false),
                shouldShowRequestPermissionRationale(PERMISSION));
    }

    private void renderPermission() {
        if (status == null || !verifySession()) {
            return;
        }
        SmsPermissionState state = permissionState();
        allow.setEnabled(state.canRequest());
        settings.setVisibility(state == SmsPermissionState.SETTINGS_AVAILABLE ? View.VISIBLE : View.GONE);
        scan.setVisibility(state == SmsPermissionState.GRANTED ? View.VISIBLE : View.GONE);
        if (state != SmsPermissionState.GRANTED) clearScan();
        status.setText(switch (state) {
            case UNAVAILABLE -> R.string.sms_permission_unavailable;
            case GRANTED -> R.string.sms_access_granted;
            case FIRST_REQUEST -> R.string.sms_access_optional;
            case DENIED_WITH_RATIONALE -> R.string.sms_access_denied;
            case SETTINGS_AVAILABLE -> R.string.sms_permission_settings;
        });
    }

    private boolean canScan() {
        Session current = store.verifiedSession();
        return current != null && current.loginIdentity() == entryIdentity && "USER".equals(current.role)
                && checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    private void scanInbox() {
        if (!verifySession() || !canScan()) return;
        preview.clear();
        previewButton.setVisibility(View.GONE);
        scan.setEnabled(false);
        scanResult.setText(R.string.records_loading);
        scanTask.start((signal, cancelled) -> {
            try {
                var parser = SmsLocalResults.load(this, store, entryIdentity, signal, cancelled);
                Instant now = Instant.now();
                return new SmsInboxReader(parser, getContentResolver(), this::canScan)
                        .read(now.minus(31, ChronoUnit.DAYS), now, SmsInboxReader.MAX_ROWS, signal, cancelled);
            } catch (IOException invalidBundle) {
                return new SmsInboxReader.Scan(Status.UNAVAILABLE, java.util.Collections.emptyList());
            }
        }, result -> {
            if (!verifySession()) return;
            if (!canScan()) { renderPermission(); return; }
            scan.setEnabled(true);
            switch (result.status()) {
                case NO_FORMATS -> scanResult.setText(R.string.sms_no_formats);
                case COMPLETE -> {
                    if (result.results().isEmpty()) {
                        scanResult.setText(R.string.sms_no_matching_messages);
                    } else {
                        var text = new StringBuilder();
                        for (var parsed : result.results()) {
                            if (text.length() != 0) text.append("\n\n");
                            text.append(SmsLocalResults.text(this, parsed));
                        }
                        scanResult.setText(text);
                        var parsedRows = new java.util.ArrayList<lk.finora.app.sms.SmsParseResult.Parsed>();
                        for (var row : result.results()) {
                            if (row instanceof lk.finora.app.sms.SmsParseResult.Parsed parsed) parsedRows.add(parsed);
                        }
                        preview.replace(parsedRows);
                        if (!parsedRows.isEmpty()) previewButton.setVisibility(View.VISIBLE);
                    }
                }
                default -> scanResult.setText(R.string.sms_scan_failed);
            }
        }, () -> {
            if (!verifySession()) return;
            renderPermission();
            if (canScan()) scanResult.setText(R.string.sms_scan_failed);
            scan.setEnabled(true);
        });
    }

    private void clearScan() {
        if (preview != null) preview.clear();
        if (previewButton != null) previewButton.setVisibility(View.GONE);
        scanTask.cancel();
        if (scan != null) scan.setEnabled(true);
        if (scanResult != null) scanResult.setText("");
    }

    private void requestInboxAccess() {
        if (!verifySession()) {
            return;
        }
        SmsPermissionState state = permissionState();
        if (!state.canRequest()) {
            renderPermission();
            return;
        }
        allow.setEnabled(false);
        preferences.edit().putBoolean(REQUESTED, true).apply();
        permissionRequest.launch(PERMISSION);
    }

    private void openAppSettings() {
        if (!verifySession() || permissionState() != SmsPermissionState.SETTINGS_AVAILABLE) {
            return;
        }
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException failure) {
            status.setText(R.string.sms_settings_unavailable);
        }
    }
}
