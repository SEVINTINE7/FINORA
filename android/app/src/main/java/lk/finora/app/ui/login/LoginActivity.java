package lk.finora.app.ui.login;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.data.repo.LoginRepository.Result;
import lk.finora.app.ui.common.StateView;
import lk.finora.app.util.LoginInput;

public final class LoginActivity extends ComponentActivity {
    private EditText email;
    private EditText password;
    private Button submit;
    private StateView feedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        View root = findViewById(R.id.login_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return windowInsets;
        });

        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        submit = findViewById(R.id.sign_in);
        feedback = new StateView(findViewById(R.id.login_feedback));

        LoginViewModel model = new ViewModelProvider(this).get(LoginViewModel.class);
        model.state().observe(this, this::render);
        submit.setOnClickListener(view -> {
            String address = email.getText().toString().trim();
            String secret = password.getText().toString();
            if (!LoginInput.hasEmail(address)) {
                email.setError(getString(R.string.invalid_email));
                email.requestFocus();
            } else if (!LoginInput.hasPassword(secret)) {
                password.setError(getString(R.string.required_password));
                password.requestFocus();
            } else {
                model.submit(address, secret);
            }
        });
    }

    private void render(LoginViewModel.State state) {
        submit.setEnabled(!state.loading);
        email.setEnabled(!state.loading);
        password.setEnabled(!state.loading);
        if (state.loading) {
            feedback.loading(R.string.signing_in);
        } else if (state.result == null) {
            feedback.clear();
        } else if (state.result == Result.SUCCESS) {
            password.getText().clear();
            feedback.empty(R.string.signed_in);
        } else {
            feedback.error(errorMessage(state.result));
        }
    }

    private static int errorMessage(Result result) {
        switch (result) {
            case INVALID_CREDENTIALS: return R.string.invalid_credentials;
            case ACCOUNT_DISABLED: return R.string.account_disabled;
            case INVALID_INPUT: return R.string.invalid_input;
            case NETWORK_ERROR: return R.string.network_error;
            case STORAGE_ERROR: return R.string.storage_error;
            case CONFIGURATION_ERROR: return R.string.configuration_error;
            default: return R.string.server_error;
        }
    }
}
