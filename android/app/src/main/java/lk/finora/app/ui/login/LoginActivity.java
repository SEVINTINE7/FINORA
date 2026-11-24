package lk.finora.app.ui.login;

import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.repo.LoginRepository.Result;
import lk.finora.app.ui.common.StateView;
import lk.finora.app.ui.auth.SessionNavigation;
import lk.finora.app.ui.register.RegisterActivity;
import lk.finora.app.util.LoginInput;
import lk.finora.app.util.LocaleHelper;

public final class LoginActivity extends SecureActivity {
    private EditText email;
    private EditText password;
    private Button submit;
    private Button languageSelector;
    private ImageButton visibility;
    private FieldFeedback emailFeedback;
    private FieldFeedback passwordFeedback;
    private StateView feedback;
    private LoginViewModel model;
    private Button createAccount;
    // Set when the user comes back from a successful sign-up; shown instead of an empty state
    private boolean registered;
    private final ActivityResultLauncher<Intent> register = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null) return;
                email.setText(data.getStringExtra(RegisterActivity.EXTRA_EMAIL));
                password.requestFocus();
                registered = true;
                feedback.success(R.string.register_done);
            });

    private androidx.appcompat.app.AlertDialog consentDialog;
    private final ActivityResultLauncher<String> consentExport = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), destination -> {
                if (destination == null) showConsent();
                else model.exportConsentData(destination);
            });

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
        ViewGroup sheet = (ViewGroup) submit.getParent();
        languageSelector = LocaleHelper.selector(this, LocaleHelper::apply);
        sheet.addView(languageSelector, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        var activate = new android.widget.Button(this);
        activate.setText(R.string.staff_activate);
        activate.setOnClickListener(view -> startActivity(new android.content.Intent(this,
                lk.finora.app.ui.staff.StaffAccessActivity.class)));
        sheet.addView(activate, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        configureInsets();
        emailFeedback = new FieldFeedback(email, findViewById(R.id.email_field),
                findViewById(R.id.email_error), null);
        passwordFeedback = new FieldFeedback(password, findViewById(R.id.password_field),
                findViewById(R.id.password_error), this::revealSubmitWithKeyboard);
        feedback = new StateView(findViewById(R.id.login_feedback));
        ViewCompat.setAccessibilityHeading(findViewById(R.id.login_heading), true);

        model = new ViewModelProvider(this).get(LoginViewModel.class);
        model.state().observe(this, this::render);
        model.restore(getIntent().getBooleanExtra(SessionNavigation.STORAGE_ERROR, false));
        submit.setOnClickListener(view -> signIn(model));
        createAccount = findViewById(R.id.create_account);
        createAccount.setOnClickListener(view -> register.launch(new Intent(this, RegisterActivity.class)));
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
        View brand = findViewById(R.id.login_brand_content);
        View headline = findViewById(R.id.login_brand_headline);
        View artwork = findViewById(R.id.login_brand_art);
        int margin = getResources().getDimensionPixelSize(R.dimen.finora_outer_margin);
        int maxWidth = getResources().getDimensionPixelSize(R.dimen.finora_login_content_width);
        int bottomSpacing = sheet.getPaddingBottom();

        root.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int available = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
            int gutter = Math.max(margin, (available - maxWidth) / 2);
            if (sheet.getPaddingStart() != gutter) {
                sheet.setPaddingRelative(gutter, sheet.getPaddingTop(), gutter,
                        sheet.getPaddingBottom());
                brand.setPaddingRelative(gutter, brand.getPaddingTop(), gutter,
                        brand.getPaddingBottom());
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            boolean keyboardChanged = view.getPaddingBottom() != keyboard;
            int heroVisibility = keyboard == 0 ? View.VISIBLE : View.GONE;
            headline.setVisibility(heroVisibility);
            artwork.setVisibility(heroVisibility);
            view.setPadding(safe.left, safe.top, safe.right, keyboard);
            sheet.setPaddingRelative(sheet.getPaddingStart(), sheet.getPaddingTop(),
                    sheet.getPaddingEnd(), bottomSpacing + (keyboard == 0 ? safe.bottom : 0));
            if (keyboardChanged && keyboard > 0 && password.hasFocus()) {
                revealSubmitWithKeyboard();
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void revealSubmitWithKeyboard() {
        View root = findViewById(R.id.login_root);
        submit.post(() -> {
            if (password.hasFocus() && root.getPaddingBottom() > 0) {
                submit.requestRectangleOnScreen(
                        new Rect(0, 0, submit.getWidth(), submit.getHeight()), true);
            }
        });
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
        View feedbackView = findViewById(R.id.login_feedback);
        boolean retry = state.result == Result.RESTORE_UNAVAILABLE;
        feedbackView.setOnClickListener(retry ? view -> model.retryRestore() : null);
        feedbackView.setClickable(retry);
        feedbackView.setFocusable(retry);
        submit.setEnabled(!state.loading);
        languageSelector.setEnabled(!state.loading);
        email.setEnabled(!state.loading);
        password.setEnabled(!state.loading);
        visibility.setEnabled(!state.loading);
        createAccount.setEnabled(!state.loading);
        if (state.loading) {
            registered = false;
            feedback.loading(R.string.signing_in);
        } else if (state.result == null) {
            if (registered) {
                feedback.success(R.string.register_done);
            } else {
                feedback.clear();
            }
        } else if (state.result == Result.CONSENT_REQUIRED) {
            password.getText().clear();
            feedback.clear();
            showConsent();
        } else if (state.result == Result.CONSENT_RESTORED) {
            feedback.success(R.string.consent_restored);
        } else if (state.result == Result.CONSENT_ACCOUNT_DELETED) {
            feedback.success(R.string.consent_deleted);
        } else if (state.result == Result.CONSENT_EXPORT_SAVED) {
            feedback.success(R.string.settings_export_saved);
        } else if (state.result == Result.CONSENT_RETRY) {
            feedback.error(R.string.consent_retry);
        } else if (state.result == Result.SUCCESS) {
            password.getText().clear();
            if (!SessionNavigation.openHome(this)) {
                feedback.error(R.string.session_expired);
            }
        } else {
            feedback.error(errorMessage(state.result));
        }
    }

    private void showConsent() {
        if (isFinishing() || model.state().getValue().result != Result.CONSENT_REQUIRED
                || (consentDialog != null && consentDialog.isShowing())) return;
        var content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        var terms = new TextView(this);
        terms.setText(R.string.register_consent_text);
        content.addView(terms);
        var agree = new android.widget.CheckBox(this);
        agree.setText(R.string.register_consent);
        content.addView(agree);
        var accept = new Button(this);
        accept.setText(R.string.consent_continue);
        accept.setEnabled(false);
        content.addView(accept);
        agree.setOnCheckedChangeListener((button, checked) -> accept.setEnabled(checked));
        accept.setOnClickListener(view -> {
            consentDialog.dismiss();
            model.acceptConsent();
        });
        var export = new Button(this);
        export.setText(R.string.settings_export_data);
        content.addView(export);
        export.setOnClickListener(view -> {
            consentDialog.dismiss();
            consentExport.launch("finora-export-" + java.time.LocalDate.now(
                    java.time.ZoneId.of("Asia/Colombo")) + ".json");
        });
        var delete = new Button(this);
        delete.setText(R.string.settings_delete);
        content.addView(delete);
        delete.setOnClickListener(view -> {
            consentDialog.dismiss();
            confirmConsentDeletion();
        });
        var scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        consentDialog = showSecure(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.consent_recover_title).setView(scroll)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> model.cancelConsent())
                .setOnCancelListener(dialog -> model.cancelConsent()).create());
    }

    private void confirmConsentDeletion() {
        var secret = new EditText(this);
        secret.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        secret.setSaveEnabled(false);
        secret.setHint(R.string.password);
        secret.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        var dialog = showSecure(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_delete).setMessage(R.string.settings_delete_confirm).setView(secret)
                .setNegativeButton(android.R.string.cancel, (ignored, which) -> showConsent())
                .setOnCancelListener(ignored -> showConsent())
                .setPositiveButton(R.string.settings_delete_action, null).create());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = secret.getText().toString();
            if (value.isBlank() || value.length() > 72) {
                secret.setError(getString(R.string.invalid_input));
                return;
            }
            secret.getText().clear();
            dialog.dismiss();
            model.deleteConsentData(value);
        });
    }

    private static int errorMessage(Result result) {
        switch (result) {
            case INVALID_CREDENTIALS: return R.string.invalid_credentials;
            case ACCOUNT_DISABLED: return R.string.account_disabled;
            case INVALID_INPUT: return R.string.invalid_input;
            case NETWORK_ERROR: return R.string.network_error;
            case SESSION_EXPIRED: return R.string.session_expired;
            case RESTORE_UNAVAILABLE: return R.string.restore_unavailable;
            case UNSUPPORTED_ROLE: return R.string.unsupported_role;
            case STORAGE_ERROR: return R.string.storage_error;
            case CONFIGURATION_ERROR: return R.string.configuration_error;
            default: return R.string.server_error;
        }
    }

    private static final class FieldFeedback implements TextWatcher {
        private final View outline;
        private final TextView message;

        FieldFeedback(EditText input, View outline, TextView message, Runnable onFocus) {
            this.outline = outline;
            this.message = message;
            input.setOnFocusChangeListener((view, focused) -> {
                outline.setActivated(focused);
                if (focused && onFocus != null) {
                    onFocus.run();
                }
            });
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
