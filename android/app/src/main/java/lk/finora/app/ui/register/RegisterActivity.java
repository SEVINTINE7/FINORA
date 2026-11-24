package lk.finora.app.ui.register;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.data.repo.RegisterRepository.Result;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.common.StateView;
import lk.finora.app.util.LoginInput;
import lk.finora.app.util.RegisterInput;

/**
 * Sign-up (T07.4): email, name, password, language, over-18 and consent ticks. Submit stays disabled until
 * both boxes are ticked. On success the user goes back to sign-in with the email filled in.
 */
public final class RegisterActivity extends AppCompatActivity {
    public static final String EXTRA_EMAIL = "lk.finora.app.registered_email";
    private static final String[] LANGUAGES = {"EN", "SI"};

    private RegisterViewModel model;
    private EditText email;
    private EditText name;
    private EditText password;
    private Spinner language;
    private CheckBox over18;
    private CheckBox consent;
    private Button submit;
    private StateView feedback;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_register);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.register_heading), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.register_consent_heading), true);
        email = findViewById(R.id.register_email);
        name = findViewById(R.id.register_name);
        password = findViewById(R.id.register_password);
        language = findViewById(R.id.register_language);
        over18 = findViewById(R.id.register_over18);
        consent = findViewById(R.id.register_consent);
        submit = findViewById(R.id.register_submit);
        feedback = new StateView(findViewById(R.id.register_feedback));

        ArrayAdapter<String> languages = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.language_english), getString(R.string.language_sinhala)});
        languages.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        language.setAdapter(languages);
        if (state == null) {
            String current = getResources().getConfiguration().getLocales().get(0).getLanguage();
            language.setSelection("si".equals(current) ? 1 : 0);
        }

        model = new ViewModelProvider(this).get(RegisterViewModel.class);
        over18.setOnCheckedChangeListener((box, checked) -> updateSubmit());
        consent.setOnCheckedChangeListener((box, checked) -> updateSubmit());
        for (EditText field : new EditText[]{email, name, password}) {
            AccountFormActivity.watch(field, text -> model.acknowledge());
        }
        submit.setOnClickListener(view -> submit());
        findViewById(R.id.register_back).setOnClickListener(view -> finish());
        model.state().observe(this, this::render);
    }

    private void updateSubmit() {
        submit.setEnabled(RegisterInput.canSubmit(over18.isChecked(), consent.isChecked(), model.busy()));
    }

    private void submit() {
        if (!RegisterInput.canSubmit(over18.isChecked(), consent.isChecked(), model.busy())) return;
        String address = email.getText().toString().trim();
        String fullName = name.getText().toString().trim();
        String secret = password.getText().toString();
        boolean ok = true;
        if (!LoginInput.hasEmail(address)) {
            email.setError(getString(R.string.invalid_email));
            ok = false;
        }
        if (!RegisterInput.hasName(fullName)) {
            name.setError(getString(R.string.register_name_invalid));
            ok = false;
        }
        if (!RegisterInput.hasStrongPassword(secret)) {
            password.setError(getString(R.string.register_password_rule));
            ok = false;
        }
        if (!ok) return;
        model.submit(address, secret, fullName, LANGUAGES[language.getSelectedItemPosition()], true);
    }

    private void render(RegisterViewModel.State state) {
        boolean editable = !state.loading;
        email.setEnabled(editable);
        name.setEnabled(editable);
        password.setEnabled(editable);
        language.setEnabled(editable);
        over18.setEnabled(editable);
        consent.setEnabled(editable);
        updateSubmit();
        if (state.loading) {
            feedback.loading(R.string.register_working);
        } else if (state.result == null) {
            feedback.clear();
        } else if (state.result == Result.SUCCESS) {
            password.getText().clear();
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_EMAIL, email.getText().toString().trim()));
            finish();
        } else {
            feedback.error(message(state.result));
        }
    }

    static int message(Result result) {
        switch (result) {
            case EMAIL_TAKEN: return R.string.register_email_taken;
            case UNDER_18: return R.string.register_under_18;
            case INVALID_INPUT: return R.string.invalid_input;
            case NETWORK_ERROR: return R.string.network_error;
            default: return R.string.server_error;
        }
    }
}
