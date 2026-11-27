package lk.finora.app.ui;

import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import lk.finora.app.R;

public final class FormFields {
    private FormFields() {}

    public static void error(EditText field, int label) {
        outline(field).setError(field.getContext().getText(label));
        field.requestFocus();
    }

    public static void clearError(EditText field) {
        if (field != null && field.getParent() != null
                && field.getParent().getParent() instanceof TextInputLayout outline) {
            outline.setError(null);
        }
    }

    public static void calendar(EditText field, Runnable open) {
        var outline = outline(field);
        outline.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        outline.setEndIconDrawable(R.drawable.finora_nav_plan);
        outline.setEndIconContentDescription(R.string.transaction_choose_date);
        outline.setEndIconOnClickListener(view -> open.run());
    }

    private static TextInputLayout outline(EditText field) {
        return (TextInputLayout) field.getParent().getParent();
    }
}
