package lk.finora.app.ui.login;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import java.nio.charset.StandardCharsets;

import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.AuthApi;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.common.StateView;
import lk.finora.app.util.RegisterInput;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Password reset (T69.2): step 1 asks for the email and sends a code; step 2 takes the 6-digit code and the
 * new password twice. The answer never says whether an account exists. Success returns to sign-in.
 */
public final class PasswordResetActivity extends SecureActivity {
    private AuthApi api;
    private AuthForm.Field email;
    private AuthForm.Field code;
    private AuthForm.Field password;
    private AuthForm.Field repeat;
    private Button action;
    private StateView feedback;
    private boolean confirming;
    private boolean resending;
    private Call<Void> pending;

    @Override protected boolean showsUp() { return false; }
    @Override protected boolean hasScreenInsets() { return true; }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        api = new ApiClient(BuildConfig.API_BASE_URL, () -> null).auth();
        setContentView(R.layout.activity_password_reset);
        AuthForm.window(this, findViewById(R.id.reset_root), findViewById(R.id.reset_sheet),
                findViewById(R.id.reset_headline), findViewById(R.id.reset_brand_art));
        ViewCompat.setAccessibilityHeading(findViewById(R.id.reset_heading), true);
        email = new AuthForm.Field(findViewById(R.id.reset_email), findViewById(R.id.reset_email_field),
                findViewById(R.id.reset_email_error));
        code = new AuthForm.Field(findViewById(R.id.reset_code_input), findViewById(R.id.reset_code_field),
                findViewById(R.id.reset_code_error));
        password = new AuthForm.Field(findViewById(R.id.reset_password), findViewById(R.id.reset_password_field),
                findViewById(R.id.reset_password_error));
        repeat = new AuthForm.Field(findViewById(R.id.reset_repeat), findViewById(R.id.reset_repeat_field),
                findViewById(R.id.reset_repeat_error));
        AuthForm.visibility(findViewById(R.id.reset_password_visibility), password.input);
        AuthForm.visibility(findViewById(R.id.reset_repeat_visibility), repeat.input);
        AuthForm.onChange(password.input, this::renderRules);
        renderRules();
        feedback = new StateView(findViewById(R.id.reset_feedback));
        action = findViewById(R.id.reset_action);
        action.setOnClickListener(view -> submit());
        findViewById(R.id.reset_resend).setOnClickListener(view -> resend());
        findViewById(R.id.reset_back).setOnClickListener(view -> finish());
        findViewById(R.id.reset_back_button).setOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                // Back from step 2 returns to step 1 so a mistyped email can be fixed.
                if (confirming && pending == null) {
                    confirming = false;
                    feedback.clear();
                    render();
                } else {
                    finish();
                }
            }
        });
        if (saved != null) {
            confirming = saved.getBoolean("confirming");
            email.input.setText(saved.getString("email", ""));
        }
        render();
    }

    private void renderRules() {
        AuthForm.rules(password.text(), findViewById(R.id.reset_rule_length),
                findViewById(R.id.reset_rule_letter), findViewById(R.id.reset_rule_digit));
    }

    private void render() {
        boolean idle = pending == null;
        ((TextView) findViewById(R.id.reset_headline)).setText(
                confirming ? R.string.reset_headline_code : R.string.reset_headline_email);
        ((TextView) findViewById(R.id.reset_heading)).setText(confirming ? R.string.reset_code_title : R.string.reset_title);
        TextView intro = findViewById(R.id.reset_intro);
        intro.setText(confirming ? getString(R.string.reset_code_sent, email.text().trim()) : getString(R.string.reset_intro));
        findViewById(R.id.reset_email_group).setVisibility(confirming ? View.GONE : View.VISIBLE);
        findViewById(R.id.reset_code_group).setVisibility(confirming ? View.VISIBLE : View.GONE);
        findViewById(R.id.reset_note).setVisibility(confirming ? View.GONE : View.VISIBLE);
        int green = getColor(R.color.finora_green);
        int line = getColor(R.color.finora_card_line);
        findViewById(R.id.reset_step1_bar).setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));
        findViewById(R.id.reset_step2_bar).setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(confirming ? green : line));
        TextView one = findViewById(R.id.reset_step1_label);
        TextView two = findViewById(R.id.reset_step2_label);
        AuthForm.weight(one, !confirming);
        AuthForm.weight(two, confirming);
        two.setTextColor(getColor(confirming ? R.color.finora_ink : R.color.finora_muted));
        findViewById(R.id.reset_steps).setContentDescription(getString(R.string.reset_step_of, confirming ? 2 : 1));
        action.setText(confirming ? R.string.reset_confirm : R.string.reset_request);
        email.input.setEnabled(idle);
        code.input.setEnabled(idle);
        password.input.setEnabled(idle);
        repeat.input.setEnabled(idle);
        findViewById(R.id.reset_resend).setEnabled(idle);
        action.setEnabled(idle);
    }

    private boolean emailValid(String address) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches() && address.length() <= 254;
    }

    private void submit() {
        if (pending != null) return;
        String address = email.text().trim();
        if (!confirming) {
            if (!email.show(emailValid(address) ? 0 : R.string.invalid_email)) {
                email.input.requestFocus();
                return;
            }
            send(api.requestPasswordReset(new AuthApi.ForgotPassword(address)));
            return;
        }
        String digits = code.text();
        String secret = password.text();
        boolean codeOk = code.show(digits.matches("[0-9]{6}") ? 0 : R.string.reset_code_invalid);
        boolean strong = RegisterInput.hasStrongPassword(secret)
                && secret.getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean passwordOk = password.show(strong ? 0 : R.string.register_password_rule);
        boolean repeatOk = !passwordOk || repeat.show(secret.equals(repeat.text()) ? 0 : R.string.password_mismatch);
        if (!codeOk) code.input.requestFocus();
        else if (!passwordOk) password.input.requestFocus();
        else if (!repeatOk) repeat.input.requestFocus();
        if (!codeOk || !passwordOk || !repeatOk) return;
        AuthForm.hide(findViewById(R.id.reset_password_visibility), password.input);
        AuthForm.hide(findViewById(R.id.reset_repeat_visibility), repeat.input);
        send(api.resetPassword(new AuthApi.ResetPassword(address, digits, secret)));
    }

    private void resend() {
        if (pending != null || !confirming) return;
        resending = true;
        send(api.requestPasswordReset(new AuthApi.ForgotPassword(email.text().trim())));
    }

    private void send(Call<Void> call) {
        pending = call;
        feedback.loading(R.string.records_loading);
        render();
        call.enqueue(new Callback<>() {
            @Override public void onResponse(Call<Void> done, Response<Void> response) {
                if (response.errorBody() != null) response.errorBody().close();
                if (pending != done || isFinishing()) return;
                pending = null;
                boolean wasResend = resending;
                resending = false;
                if (!confirming && response.code() == 202) {
                    confirming = true;
                    feedback.clear();
                    code.input.requestFocus();
                } else if (wasResend && response.code() == 202) {
                    code.input.setText("");
                    feedback.success(R.string.reset_resent);
                } else if (confirming && !wasResend && response.code() == 204) {
                    code.input.setText("");
                    password.input.setText("");
                    repeat.input.setText("");
                    setResult(RESULT_OK);
                    finish();
                    return;
                } else if (confirming && !wasResend && response.code() == 400) {
                    feedback.clear();
                    code.show(R.string.reset_code_invalid);
                } else {
                    feedback.error(R.string.reset_try_again);
                }
                render();
            }

            @Override public void onFailure(Call<Void> done, Throwable error) {
                if (pending != done || isFinishing()) return;
                pending = null;
                resending = false;
                feedback.error(R.string.reset_try_again);
                render();
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("confirming", confirming);
        state.putString("email", email.text());
        super.onSaveInstanceState(state);
    }

    @Override protected void onStop() {
        super.onStop();
        if (isFinishing()) {
            password.input.setText("");
            repeat.input.setText("");
        }
    }

    @Override protected void onDestroy() {
        if (pending != null) pending.cancel();
        pending = null;
        super.onDestroy();
    }
}
