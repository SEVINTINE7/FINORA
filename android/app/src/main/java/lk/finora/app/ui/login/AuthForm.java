package lk.finora.app.ui.login;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.widget.TextViewCompat;

import lk.finora.app.R;
import lk.finora.app.util.RegisterInput;

/**
 * T69.2/D72 shared pieces of the Login-style screens (Register, Password reset): the navy window with its
 * insets, outlined fields with inline errors, the show-password button and the live password checklist.
 */
public final class AuthForm {
    private AuthForm() {}

    /** Navy window, light status icons, and Login's inset handling (the headline hides while typing). */
    public static void window(Activity activity, View root, View sheet, View... hideWithKeyboard) {
        activity.getWindow().setBackgroundDrawableResource(R.color.finora_navy);
        var controller = WindowCompat.getInsetsController(activity.getWindow(), root);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars((activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_YES);
        int bottom = sheet.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            for (View hidden : hideWithKeyboard) hidden.setVisibility(keyboard == 0 ? View.VISIBLE : View.GONE);
            view.setPadding(safe.left, safe.top, safe.right, keyboard);
            sheet.setPaddingRelative(sheet.getPaddingStart(), sheet.getPaddingTop(), sheet.getPaddingEnd(),
                    bottom + (keyboard == 0 ? safe.bottom : 0));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** An outlined field: focus ring, red outline and message on error, error read by TalkBack. */
    public static final class Field implements TextWatcher {
        public final EditText input;
        private final View outline;
        private final TextView message;

        public Field(EditText input, View outline, TextView message) {
            this.input = input;
            this.outline = outline;
            this.message = message;
            input.setOnFocusChangeListener((view, focused) -> outline.setActivated(focused));
            input.addTextChangedListener(this);
            ViewCompat.setAccessibilityDelegate(input, new AccessibilityDelegateCompat() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    if (message.getVisibility() == View.VISIBLE) {
                        info.setContentInvalid(true);
                        info.setError(message.getText());
                    }
                }
            });
        }

        /** Shows {@code text} under the field, or clears it when 0. Returns true when there is no error. */
        public boolean show(int text) {
            outline.setSelected(text != 0);
            message.setVisibility(text == 0 ? View.GONE : View.VISIBLE);
            if (text != 0) message.setText(text);
            return text == 0;
        }

        public String text() { return input.getText().toString(); }

        @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable text) {
            if (message.getVisibility() == View.VISIBLE) show(0);
        }
    }

    /** The eye button next to a password field. Passwords start hidden. */
    public static void visibility(ImageButton button, EditText... inputs) {
        for (EditText input : inputs) input.setTypeface(Typeface.DEFAULT);
        button.setOnClickListener(view -> {
            boolean show = inputs[0].getTransformationMethod() instanceof PasswordTransformationMethod;
            for (EditText input : inputs) {
                int start = input.getSelectionStart();
                int end = input.getSelectionEnd();
                input.setTransformationMethod(show ? null : PasswordTransformationMethod.getInstance());
                if (start >= 0 && end >= 0) input.setSelection(start, end);
            }
            button.setImageResource(show ? R.drawable.finora_ic_eye_off : R.drawable.finora_ic_eye);
            button.setContentDescription(button.getContext().getString(
                    show ? R.string.login_hide_password : R.string.login_show_password));
        });
    }

    /** Hides typed passwords again, e.g. before sending them. */
    public static void hide(ImageButton button, EditText... inputs) {
        if (inputs[0].getTransformationMethod() instanceof PasswordTransformationMethod) return;
        button.performClick();
    }

    /** Ticks each D09 rule as it is met, in words as well as by icon. */
    public static void rules(String password, TextView length, TextView letter, TextView digit) {
        rule(length, R.string.password_rule_length, RegisterInput.hasLength(password));
        rule(letter, R.string.password_rule_letter, RegisterInput.hasLetter(password));
        rule(digit, R.string.password_rule_digit, RegisterInput.hasDigit(password));
    }

    private static void rule(TextView row, int label, boolean met) {
        var context = row.getContext();
        int color = ContextCompat.getColor(context, met ? R.color.finora_green : R.color.finora_muted);
        row.setTextColor(color);
        weight(row, met);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(met ? R.drawable.finora_ic_check : R.drawable.finora_ic_dot, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(row, ColorStateList.valueOf(color));
        String name = context.getString(label);
        row.setContentDescription(context.getString(met ? R.string.password_rule_met : R.string.password_rule_missing, name));
    }

    /** Bold or regular in the app's own font (setTypeface(null, …) would fall back to the system font). */
    public static void weight(TextView view, boolean bold) {
        Typeface base = androidx.core.content.res.ResourcesCompat.getFont(view.getContext(), R.font.finora_body);
        view.setTypeface(Typeface.create(base, bold ? Typeface.BOLD : Typeface.NORMAL));
    }

    /** Runs {@code change} after every edit of {@code input}. */
    public static void onChange(EditText input, Runnable change) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) { change.run(); }
        });
    }
}
