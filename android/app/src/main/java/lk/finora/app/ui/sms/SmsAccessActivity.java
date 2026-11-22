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
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import java.util.Arrays;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;

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
        setContentView(R.layout.activity_sms_access);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.sms_title), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.sms_alternatives_title), true);
        allow = findViewById(R.id.sms_allow);
        settings = findViewById(R.id.sms_settings);
        status = findViewById(R.id.sms_status);
        allow.setOnClickListener(view -> requestInboxAccess());
        settings.setOnClickListener(view -> openAppSettings());
        findViewById(R.id.sms_no_thanks_action).setOnClickListener(view -> finish());
        renderPermission();
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
        if (entryIdentity != null) {
            store.removeSessionListener(sessionChanged);
        }
        super.onStop();
    }

    private boolean verifySession() {
        if (isFinishing() || entryIdentity == null) {
            return false;
        }
        Session current = store.verifiedSession();
        if (current == null || current.loginIdentity() != entryIdentity || !"USER".equals(current.role)) {
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
        allow.setEnabled(state == SmsPermissionState.FIRST_REQUEST
                || state == SmsPermissionState.DENIED_WITH_RATIONALE);
        settings.setVisibility(state == SmsPermissionState.SETTINGS_AVAILABLE ? View.VISIBLE : View.GONE);
        status.setText(switch (state) {
            case UNAVAILABLE -> R.string.sms_permission_unavailable;
            case GRANTED -> R.string.sms_access_granted;
            case FIRST_REQUEST -> R.string.sms_access_optional;
            case DENIED_WITH_RATIONALE -> R.string.sms_access_denied;
            case SETTINGS_AVAILABLE -> R.string.sms_permission_settings;
        });
    }

    private void requestInboxAccess() {
        if (!verifySession()) {
            return;
        }
        SmsPermissionState state = permissionState();
        if (state != SmsPermissionState.FIRST_REQUEST && state != SmsPermissionState.DENIED_WITH_RATIONALE) {
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
