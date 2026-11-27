package lk.finora.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import lk.finora.app.R;
import lk.finora.app.databinding.FinoraScreenActionBinding;
import lk.finora.app.databinding.FinoraScreenFieldBinding;
import lk.finora.app.databinding.FinoraScreenTextBinding;

public final class ScreenControls {
    private ScreenControls() {}

    public static EditText field(Context context) {
        var outline = FinoraScreenFieldBinding.inflate(LayoutInflater.from(context)).getRoot();
        outline.setHintEnabled(false);
        return outline.getEditText();
    }

    public static void addField(ViewGroup destination, EditText field) {
        var outline = (TextInputLayout) field.getParent().getParent();
        outline.setHintEnabled(true);
        destination.addView(outline, new android.widget.LinearLayout.LayoutParams(-1, -2));
    }

    public static Button action(Context context) {
        return FinoraScreenActionBinding.inflate(LayoutInflater.from(context)).getRoot();
    }

    public static Button homeAction(Context context) {
        var button = (com.google.android.material.button.MaterialButton) action(context);
        button.setTextColor(context.getColor(R.color.finora_mint));
        button.setStrokeColor(android.content.res.ColorStateList.valueOf(context.getColor(R.color.finora_mint)));
        return button;
    }

    public static TextView text(Context context) {
        return FinoraScreenTextBinding.inflate(LayoutInflater.from(context)).getRoot();
    }
}
