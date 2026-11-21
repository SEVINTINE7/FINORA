package lk.finora.app.ui.login;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.data.repo.LoginRepository.Result;
import lk.finora.app.ui.common.StateView;
import lk.finora.app.util.LoginInput;

public final class LoginActivity extends ComponentActivity {
    private EditText email;
    private EditText password;
    private Button submit;
    private ImageButton visibility;
    private FieldFeedback emailFeedback;
    private FieldFeedback passwordFeedback;
    private StateView feedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_login);
        WindowCompat.getInsetsController(getWindow(), findViewById(R.id.login_root))
                .setAppearanceLightNavigationBars(true);
        configureInsets();

        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        password.setTypeface(Typeface.DEFAULT);
        visibility = findViewById(R.id.password_visibility);
        visibility.setOnClickListener(view -> setPasswordVisible(
                password.getTransformationMethod() instanceof PasswordTransformationMethod));
        submit = findViewById(R.id.sign_in);
        emailFeedback = new FieldFeedback(email, findViewById(R.id.email_field),
                findViewById(R.id.email_error));
        passwordFeedback = new FieldFeedback(password, findViewById(R.id.password_field),
                findViewById(R.id.password_error));
        feedback = new StateView(findViewById(R.id.login_feedback));
        ViewCompat.setAccessibilityHeading(findViewById(R.id.login_heading), true);

        LoginViewModel model = new ViewModelProvider(this).get(LoginViewModel.class);
        model.state().observe(this, this::render);
        submit.setOnClickListener(view -> signIn(model));
        email.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_NEXT) {
                password.requestFocus();
                return true;
            }
            return false;
        });
        password.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                if (submit.isEnabled()) {
                    signIn(model);
                }
                return true;
            }
            return false;
        });
    }

    private void configureInsets() {
        View root = findViewById(R.id.login_root);
        View sheet = findViewById(R.id.login_sheet);
        int bottomSpacing = sheet.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            view.setPadding(safe.left, safe.top, safe.right, 0);
            sheet.setPaddingRelative(sheet.getPaddingStart(), sheet.getPaddingTop(),
                    sheet.getPaddingEnd(), bottomSpacing + Math.max(safe.bottom, keyboard));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void signIn(LoginViewModel model) {
        String address = email.getText().toString().trim();
        String secret = password.getText().toString();
        boolean emailValid = LoginInput.hasEmail(address);
        boolean passwordValid = LoginInput.hasPassword(secret);
        emailFeedback.show(emailValid ? 0 : R.string.invalid_email);
        passwordFeedback.show(passwordValid ? 0 : R.string.required_password);
        feedback.clear();

        if (!emailValid) {
            email.requestFocus();
        } else if (!passwordValid) {
            password.requestFocus();
        } else {
            setPasswordVisible(false);
            WindowCompat.getInsetsController(getWindow(), submit).hide(WindowInsetsCompat.Type.ime());
            model.submit(address, secret);
        }
    }

    private void setPasswordVisible(boolean visible) {
        int start = password.getSelectionStart();
        int end = password.getSelectionEnd();
        password.setTransformationMethod(visible ? null : PasswordTransformationMethod.getInstance());
        if (start >= 0 && end >= 0) {
            password.setSelection(start, end);
        }
        visibility.setImageResource(visible ? R.drawable.finora_ic_eye_off : R.drawable.finora_ic_eye);
        visibility.setContentDescription(getString(visible
                ? R.string.login_hide_password : R.string.login_show_password));
    }

    private void render(LoginViewModel.State state) {
        submit.setEnabled(!state.loading);
        email.setEnabled(!state.loading);
        password.setEnabled(!state.loading);
        visibility.setEnabled(!state.loading);
        if (state.loading) {
            feedback.loading(R.string.signing_in);
        } else if (state.result == null) {
            feedback.clear();
        } else if (state.result == Result.SUCCESS) {
            password.getText().clear();
            feedback.success(R.string.signed_in);
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

    private static final class FieldFeedback implements TextWatcher {
        private final View outline;
        private final TextView message;

        FieldFeedback(EditText input, View outline, TextView message) {
            this.outline = outline;
            this.message = message;
            input.setOnFocusChangeListener((view, focused) -> outline.setActivated(focused));
            input.addTextChangedListener(this);
            ViewCompat.setAccessibilityDelegate(input, new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    if (message.getVisibility() == View.VISIBLE) {
                        info.setContentInvalid(true);
                        info.setError(message.getText());
                    }
                }
            });
        }

        void show(int text) {
            outline.setSelected(text != 0);
            if (text == 0) {
                message.setVisibility(View.GONE);
            } else {
                message.setText(text);
                message.setVisibility(View.VISIBLE);
            }
        }

        @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable text) {
            if (message.getVisibility() == View.VISIBLE) {
                show(0);
            }
        }
    }
}
