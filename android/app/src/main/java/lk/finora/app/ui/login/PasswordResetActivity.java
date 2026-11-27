package lk.finora.app.ui.login;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import java.nio.charset.StandardCharsets;
import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.AuthApi;
import lk.finora.app.databinding.ActivityFinoraScreenBinding;
import lk.finora.app.ui.ScreenControls;
import lk.finora.app.ui.SecureActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class PasswordResetActivity extends SecureActivity {
    private AuthApi api;
    private EditText email;
    private EditText code;
    private EditText password;
    private TextView heading;
    private TextView feedback;
    private Button action;
    private boolean confirming;
    private Call<Void> pending;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        api = new ApiClient(BuildConfig.API_BASE_URL, () -> null).auth();
        var binding = ActivityFinoraScreenBinding.inflate(getLayoutInflater());
        var page = binding.screenPage;
        heading = ScreenControls.text(this);
        ViewCompat.setAccessibilityHeading(heading, true);
        page.addView(heading);
        email = field(R.string.admin_new_email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        code = field(R.string.reset_code, InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        password = field(R.string.admin_new_password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ScreenControls.addField(page, email);
        ScreenControls.addField(page, code);
        ScreenControls.addField(page, password);
        feedback = ScreenControls.text(this);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(feedback);
        action = ScreenControls.action(this);
        action.setOnClickListener(view -> submit());
        page.addView(action);
        setContentView(binding.getRoot());
        if (saved != null) {
            confirming = saved.getBoolean("confirming");
            email.setText(saved.getString("email", ""));
        }
        render();
    }

    private EditText field(int hint, int inputType) {
        var input = ScreenControls.field(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setSaveEnabled(false);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        return input;
    }

    private void render() {
        heading.setText(confirming ? R.string.reset_confirm : R.string.reset_open);
        action.setText(confirming ? R.string.reset_confirm : R.string.reset_request);
        ((View) code.getParent().getParent()).setVisibility(confirming ? View.VISIBLE : View.GONE);
        ((View) password.getParent().getParent()).setVisibility(confirming ? View.VISIBLE : View.GONE);
        email.setEnabled(!confirming && pending == null);
        code.setEnabled(pending == null);
        password.setEnabled(pending == null);
        action.setEnabled(pending == null);
    }

    private void submit() {
        if (pending != null) return;
        String address = email.getText().toString().trim();
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches() || address.length() > 254) {
            email.setError(getString(R.string.invalid_email));
            return;
        }
        if (confirming) {
            String digits = code.getText().toString();
            String secret = password.getText().toString();
            if (!digits.matches("[0-9]{6}")) {
                code.setError(getString(R.string.reset_code_invalid));
                return;
            }
            if (!secret.matches("^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$")
                    || secret.getBytes(StandardCharsets.UTF_8).length > 72) {
                password.setError(getString(R.string.admin_password_rule));
                return;
            }
            pending = api.resetPassword(new AuthApi.ResetPassword(address, digits, secret));
        } else {
            pending = api.requestPasswordReset(new AuthApi.ForgotPassword(address));
        }
        feedback.setText(R.string.records_loading);
        render();
        pending.enqueue(new Callback<>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.errorBody() != null) response.errorBody().close();
                if (pending != call || isFinishing()) return;
                pending = null;
                if (!confirming && response.code() == 202) {
                    confirming = true;
                    feedback.setText(R.string.reset_check_mail);
                } else if (confirming && response.code() == 204) {
                    code.setText("");
                    password.setText("");
                    setResult(RESULT_OK);
                    finish();
                    return;
                } else {
                    feedback.setText(confirming && response.code() == 400
                            ? R.string.reset_code_invalid : R.string.reset_try_again);
                }
                render();
            }

            @Override public void onFailure(Call<Void> call, Throwable error) {
                if (pending != call || isFinishing()) return;
                pending = null;
                feedback.setText(R.string.reset_try_again);
                render();
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("confirming", confirming);
        state.putString("email", email.getText().toString());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (pending != null) pending.cancel();
        pending = null;
        super.onDestroy();
    }
}
