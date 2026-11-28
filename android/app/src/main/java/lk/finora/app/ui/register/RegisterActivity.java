package lk.finora.app.ui.register;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;

import lk.finora.app.R;
import lk.finora.app.data.repo.RegisterRepository.Result;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.common.StateView;
import lk.finora.app.ui.login.AuthForm;
import lk.finora.app.util.LoginInput;
import lk.finora.app.util.RegisterInput;

/**
 * Sign-up (T07.4, redesigned in T69.2): name, email, password with a live rule checklist, app language,
 * the consent card, and the over-18 and consent ticks. Create account stays disabled until both are ticked.
 * On success the user goes back to sign-in with the email filled in.
 */
public final class RegisterActivity extends SecureActivity {
    public static final String EXTRA_EMAIL = "lk.finora.app.registered_email";
    /** Titles for the five consent bullets, in the order of {@code register_consent_text}. */
    static final int[][] CONSENT = {
            {R.string.register_consent_records, R.drawable.finora_nav_insights},
            {R.string.register_consent_sms, R.drawable.finora_ic_cat_phone},
            {R.string.register_consent_ai, R.drawable.finora_ic_star},
            {R.string.register_consent_bank, R.drawable.finora_ic_bank},
            {R.string.register_consent_control, R.drawable.finora_ic_delete},
    };

    private RegisterViewModel model;
    private AuthForm.Field name;
    private AuthForm.Field email;
    private AuthForm.Field password;
    private MaterialButtonToggleGroup language;
    private CheckBox over18;
    private CheckBox consent;
    private Button submit;
    private StateView feedback;

    @Override protected boolean showsUp() { return false; }
    @Override protected boolean hasScreenInsets() { return true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_register);
        AuthForm.window(this, findViewById(R.id.register_root), findViewById(R.id.register_sheet),
                findViewById(R.id.register_headline), findViewById(R.id.register_brand_art));
        ViewCompat.setAccessibilityHeading(findViewById(R.id.register_heading), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.register_consent_heading), true);
        name = new AuthForm.Field(findViewById(R.id.register_name), findViewById(R.id.register_name_field),
                findViewById(R.id.register_name_error));
        email = new AuthForm.Field(findViewById(R.id.register_email), findViewById(R.id.register_email_field),
                findViewById(R.id.register_email_error));
        password = new AuthForm.Field(findViewById(R.id.register_password), findViewById(R.id.register_password_field),
                findViewById(R.id.register_password_error));
        AuthForm.visibility(findViewById(R.id.register_password_visibility), password.input);
        AuthForm.onChange(password.input, this::renderRules);
        renderRules();
        language = findViewById(R.id.register_language);
        if (state == null) {
            String current = getResources().getConfiguration().getLocales().get(0).getLanguage();
            language.check("si".equals(current) ? R.id.register_language_si : R.id.register_language_en);
        }
        over18 = findViewById(R.id.register_over18);
        consent = findViewById(R.id.register_consent);
        submit = findViewById(R.id.register_submit);
        feedback = new StateView(findViewById(R.id.register_feedback));
        bindConsent();

        model = new ViewModelProvider(this).get(RegisterViewModel.class);
        over18.setOnCheckedChangeListener((box, checked) -> updateSubmit());
        consent.setOnCheckedChangeListener((box, checked) -> updateSubmit());
        for (var field : new AuthForm.Field[]{name, email, password}) {
            AuthForm.onChange(field.input, model::acknowledge);
        }
        submit.setOnClickListener(view -> submit());
        View.OnClickListener back = view -> finish();
        findViewById(R.id.register_back).setOnClickListener(back);
        ImageButton up = findViewById(R.id.register_back_button);
        up.setContentDescription(getString(R.string.register_have_account));
        up.setOnClickListener(back);
        model.state().observe(this, this::render);
    }

    /** The five consent bullets keep their approved wording; each gets an icon and a short title. */
    private void bindConsent() {
        LinearLayout rows = findViewById(R.id.register_consent_rows);
        String[] bullets = getString(R.string.register_consent_text).split("\n");
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < bullets.length; i++) {
            String body = bullets[i].replaceFirst("^\\s*•\\s*", "").trim();
            var row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            var rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = Math.round(12 * density);
            if (bullets.length == CONSENT.length) {
                var icon = new ImageView(this);
                int size = Math.round(32 * density);
                icon.setImageResource(CONSENT[i][1]);
                icon.setBackgroundResource(R.drawable.finora_circle);
                icon.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.finora_success_surface)));
                icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.finora_green)));
                int pad = Math.round(7 * density);
                icon.setPadding(pad, pad, pad, pad);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                var iconParams = new LinearLayout.LayoutParams(size, size);
                iconParams.setMarginEnd(Math.round(12 * density));
                row.addView(icon, iconParams);
            }
            var texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            if (bullets.length == CONSENT.length) {
                var title = new TextView(this);
                title.setTextAppearance(R.style.FinoraText_RowTitle);
                title.setText(CONSENT[i][0]);
                texts.addView(title);
            }
            var text = new TextView(this);
            text.setTextAppearance(R.style.FinoraText_Caption);
            text.setTextColor(getColor(R.color.finora_muted));
            text.setLineSpacing(0, 1.15f);
            text.setText(body);
            texts.addView(text);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            rows.addView(row, rowParams);
        }
    }

    private void renderRules() {
        AuthForm.rules(password.text(), findViewById(R.id.register_rule_length),
                findViewById(R.id.register_rule_letter), findViewById(R.id.register_rule_digit));
    }

    private void updateSubmit() {
        boolean ready = RegisterInput.canSubmit(over18.isChecked(), consent.isChecked(), model.busy());
        submit.setEnabled(ready);
        findViewById(R.id.register_submit_hint).setVisibility(
                over18.isChecked() && consent.isChecked() ? View.GONE : View.VISIBLE);
    }

    private void submit() {
        if (!RegisterInput.canSubmit(over18.isChecked(), consent.isChecked(), model.busy())) return;
        String address = email.text().trim();
        String fullName = name.text().trim();
        String secret = password.text();
        boolean nameOk = name.show(RegisterInput.hasName(fullName) ? 0 : R.string.register_name_invalid);
        boolean emailOk = email.show(LoginInput.hasEmail(address) ? 0 : R.string.invalid_email);
        boolean passwordOk = password.show(RegisterInput.hasStrongPassword(secret) ? 0 : R.string.register_password_rule);
        if (!nameOk) name.input.requestFocus();
        else if (!emailOk) email.input.requestFocus();
        else if (!passwordOk) password.input.requestFocus();
        if (!nameOk || !emailOk || !passwordOk) return;
        AuthForm.hide(findViewById(R.id.register_password_visibility), password.input);
        String code = language.getCheckedButtonId() == R.id.register_language_si ? "SI" : "EN";
        model.submit(address, secret, fullName, code, true);
    }

    private void render(RegisterViewModel.State state) {
        boolean editable = !state.loading;
        for (var field : new AuthForm.Field[]{name, email, password}) field.input.setEnabled(editable);
        for (int i = 0; i < language.getChildCount(); i++) language.getChildAt(i).setEnabled(editable);
        over18.setEnabled(editable);
        consent.setEnabled(editable);
        updateSubmit();
        if (state.loading) {
            feedback.loading(R.string.register_working);
        } else if (state.result == null) {
            feedback.clear();
        } else if (state.result == Result.SUCCESS) {
            password.input.getText().clear();
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_EMAIL, email.text().trim()));
            finish();
        } else if (state.result == Result.EMAIL_TAKEN) {
            email.show(R.string.register_email_taken);
            feedback.clear();
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
