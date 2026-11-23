package lk.finora.app.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.auth.HomeViewModel;
import lk.finora.app.ui.auth.ProfileLanguageButton;
import lk.finora.app.ui.auth.SessionNavigation;

/**
 * Account settings for a USER (T07.5): language (applies at once, saved via PUT /me), withdraw consent,
 * and the entry to data deletion (T36, not built yet).
 */
public final class SettingsActivity extends AppCompatActivity {
    private SettingsViewModel model;
    private HomeViewModel session;
    private Session entry;

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
        setContentView(R.layout.activity_settings);
        for (int heading : new int[]{R.id.settings_heading, R.id.settings_language_heading,
                R.id.settings_privacy_heading}) {
            ViewCompat.setAccessibilityHeading(findViewById(heading), true);
        }
        ((FrameLayout) findViewById(R.id.settings_language))
                .addView(ProfileLanguageButton.create(this, entry, session::signingOut));
        findViewById(R.id.settings_withdraw).setOnClickListener(view -> confirmWithdraw());
        findViewById(R.id.settings_delete).setOnClickListener(view -> new AlertDialog.Builder(this)
                .setMessage(R.string.settings_delete_unavailable)
                .setPositiveButton(android.R.string.ok, null).show());
        session.signedOut().observe(this, failed -> {
            if (failed != null) SessionNavigation.openLogin(this, failed);
        });
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!model.withdrawn && !session.signingOut()) AccountListActivity.verify(this, model.work);
    }

    private void confirmWithdraw() {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_withdraw)
                .setMessage(R.string.settings_withdraw_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_withdraw_action, (dialog, which) -> model.withdrawConsent())
                .show();
    }

    private void render() {
        if (model.withdrawn || session.signingOut()) {
            // Consent is gone and the server revoked the refresh tokens: end the local session too (D15)
            findViewById(R.id.settings_content).setVisibility(View.INVISIBLE);
            session.logout(entry);
            return;
        }
        if (!AccountListActivity.verify(this, model.work)) return;
        boolean busy = model.work.busy();
        findViewById(R.id.settings_withdraw).setEnabled(!busy);
        TextView status = findViewById(R.id.records_status);
        int message = busy ? R.string.records_loading : model.work.error;
        status.setText(message == 0 ? "" : getString(message));
    }
}
