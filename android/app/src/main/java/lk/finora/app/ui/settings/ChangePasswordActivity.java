package lk.finora.app.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.auth.HomeViewModel;
import lk.finora.app.ui.auth.SessionNavigation;

/**
 * T68.2/D72 Change password. On success the API revokes every session (planned in T74), so the app signs out
 * here too and the user signs in again with the new password.
 */
public final class ChangePasswordActivity extends SecureActivity {
    private ChangePasswordViewModel model;
    private HomeViewModel session;
    private Session entry;
    private EditText current;
    private EditText next;
    private EditText repeat;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(ChangePasswordViewModel.class);
        session = new ViewModelProvider(this).get(HomeViewModel.class);
        TokenStore store = TokenStore.current();
        entry = store == null ? null : store.verifiedSession();
        if (entry == null) {
            SessionNavigation.openLogin(this, false);
            return;
        }
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_change_password);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.password_heading), true);
        current = findViewById(R.id.password_current_input);
        next = findViewById(R.id.password_new_input);
        repeat = findViewById(R.id.password_repeat_input);
        findViewById(R.id.records_save).setOnClickListener(view -> save());
        session.signedOut().observe(this, failed -> {
            if (failed != null) SessionNavigation.openLogin(this, failed);
        });
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onResume() {
        super.onResume();
        if (!model.changed && !session.signingOut()) AccountListActivity.verify(this, model.work);
    }

    @Override protected void onStop() {
        if (!model.changed) clear();
        super.onStop();
    }

    private void clear() {
        for (EditText field : new EditText[]{current, next, repeat}) if (field != null) field.setText("");
    }

    private void render() {
        if (model.changed) {
            clear();
            findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
            Toast.makeText(this, R.string.password_changed, Toast.LENGTH_LONG).show();
            session.logout(entry);
            return;
        }
        if (!AccountListActivity.verify(this, model.work)) return;
        boolean busy = model.work.busy();
        for (EditText field : new EditText[]{current, next, repeat}) field.setEnabled(!busy);
        findViewById(R.id.records_save).setEnabled(!busy);
        TextView status = findViewById(R.id.records_status);
        int message = busy ? R.string.records_loading : model.work.error;
        status.setText(message == 0 ? "" : getString(message));
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (!busy && model.work.error == R.string.profile_wrong_password) current.setText("");
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy()) return;
        String old = current.getText().toString();
        String fresh = next.getText().toString();
        switch (ProfileRules.password(old, fresh, repeat.getText().toString())) {
            case CURRENT_MISSING -> FormFields.error(current, R.string.password_current_needed);
            case WEAK -> FormFields.error(next, R.string.register_password_rule);
            case SAME -> FormFields.error(next, R.string.password_same);
            case MISMATCH -> FormFields.error(repeat, R.string.password_mismatch);
            case NONE -> model.change(old, fresh);
        }
    }
}
