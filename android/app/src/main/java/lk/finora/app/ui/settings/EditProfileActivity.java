package lk.finora.app.ui.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.FormFields;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.util.LoginInput;
import lk.finora.app.util.RegisterInput;

/** T68.2/D72 Edit profile: full name and email address. The email step uses the T74 endpoints. */
public final class EditProfileActivity extends SecureActivity {
    private EditProfileViewModel model;
    private EditText name;
    private EditText email;
    private EditText password;
    private EditText code;
    private boolean filled;
    private String shownEmail;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(EditProfileViewModel.class);
        if (!AccountListActivity.verify(this, model.work)) return;
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_edit_profile);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.profile_heading), true);
        name = findViewById(R.id.profile_name_input);
        email = findViewById(R.id.profile_email_input);
        password = findViewById(R.id.profile_password_input);
        code = findViewById(R.id.profile_code_input);
        email.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                FormFields.clearError(email);
                showPasswordStep();
            }
        });
        findViewById(R.id.records_save).setOnClickListener(view -> save());
        findViewById(R.id.records_retry).setOnClickListener(view -> model.load());
        findViewById(R.id.profile_code_confirm).setOnClickListener(view -> confirm());
        model.work.changes.observe(this, ignored -> render());
        model.load();
    }

    @Override protected void onResume() {
        super.onResume();
        AccountListActivity.verify(this, model.work);
    }

    @Override protected void onStop() {
        // The password is never kept on screen once the user leaves (same as the delete dialog).
        if (password != null) password.setText("");
        super.onStop();
    }

    private void showPasswordStep() {
        boolean changed = model.ready && model.pendingEmail == null
                && ProfileRules.emailChanged(model.email, email.getText().toString());
        findViewById(R.id.profile_password_group).setVisibility(changed ? View.VISIBLE : View.GONE);
    }

    private void render() {
        if (!AccountListActivity.verify(this, model.work)) return;
        if (model.ready && !filled) {
            name.setText(model.name);
            email.setText(model.email);
            shownEmail = model.email;
            filled = true;
        }
        if (filled && model.email != null && !model.email.equals(shownEmail)) {
            // A confirmed email change: show the saved address and drop the password.
            email.setText(model.email);
            shownEmail = model.email;
            password.setText("");
            code.setText("");
        }
        boolean busy = model.work.busy();
        boolean codeStep = model.pendingEmail != null;
        View codeGroup = findViewById(R.id.profile_code_group);
        codeGroup.setVisibility(codeStep ? View.VISIBLE : View.GONE);
        if (codeStep) {
            ((TextView) findViewById(R.id.profile_code_help))
                    .setText(getString(R.string.profile_code_help, model.pendingEmail));
            password.setText("");
        }
        showPasswordStep();
        boolean editable = model.ready && !busy && !codeStep;
        name.setEnabled(editable);
        email.setEnabled(editable);
        password.setEnabled(editable);
        code.setEnabled(codeStep && !busy);
        findViewById(R.id.profile_code_confirm).setEnabled(codeStep && !busy);
        findViewById(R.id.records_save).setEnabled(editable);
        findViewById(R.id.records_retry).setVisibility(!model.ready && !busy && model.work.error != 0
                ? View.VISIBLE : View.GONE);
        TextView status = findViewById(R.id.records_status);
        int message = busy ? R.string.records_loading : model.work.error != 0 ? model.work.error : model.notice;
        status.setText(message == 0 ? "" : getString(message));
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
    }

    private void save() {
        if (!AccountListActivity.verify(this, model.work) || !model.ready || model.work.busy()) return;
        String typedName = name.getText().toString();
        String typedEmail = email.getText().toString().trim();
        String secret = password.getText().toString();
        boolean ok = true;
        if (!RegisterInput.hasName(typedName)) {
            FormFields.error(name, R.string.register_name_invalid);
            ok = false;
        }
        if (!LoginInput.hasEmail(typedEmail)) {
            FormFields.error(email, R.string.invalid_email);
            ok = false;
        }
        boolean emailChanged = ProfileRules.emailChanged(model.email, typedEmail);
        if (ok && emailChanged && (secret.isEmpty() || secret.length() > 72)) {
            FormFields.error(password, R.string.profile_password_needed);
            ok = false;
        }
        if (!ok) return;
        model.save(typedName, typedEmail, secret);
    }

    private void confirm() {
        if (!AccountListActivity.verify(this, model.work) || model.work.busy()) return;
        String typed = code.getText().toString().trim();
        if (!ProfileRules.hasCode(typed)) {
            FormFields.error(code, R.string.profile_code_invalid);
            return;
        }
        model.confirm(typed);
    }
}
