package lk.finora.app.ui.bills;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.GridLayout;

import com.google.android.material.button.MaterialButton;

import java.util.function.IntConsumer;

import lk.finora.app.R;

/** T63.3/D65 one-tap due day: 31 round checkable buttons in a 7-column grid; 0 means nothing chosen yet. */
public final class DueDayPicker extends GridLayout {
    private int day;
    private IntConsumer changed;

    public DueDayPicker(Context context, AttributeSet attributes) {
        super(context, attributes);
        setColumnCount(7);
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(density * 44);
        int gap = Math.round(density * 2);
        var fill = new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{context.getColor(R.color.finora_green), context.getColor(R.color.finora_card_line)});
        var ink = new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{context.getColor(R.color.finora_action_ink), context.getColor(R.color.finora_ink)});
        for (int n = 1; n <= 31; n++) {
            var button = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle);
            button.setSaveEnabled(false);
            button.setText(String.valueOf(n));
            button.setTextSize(15);
            button.setCheckable(true);
            button.setBackgroundTintList(fill);
            button.setTextColor(ink);
            button.setCornerRadius(size / 2);
            button.setInsetTop(0);
            button.setInsetBottom(0);
            button.setPadding(0, 0, 0, 0);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setElevation(0);
            button.setStateListAnimator(null);
            button.setContentDescription(context.getString(R.string.bill_day_number, n));
            final int value = n;
            button.setOnClickListener(view -> select(value, true));
            var params = new LayoutParams(spec(UNDEFINED), spec(UNDEFINED, CENTER, 1f));
            params.width = size;
            params.height = size;
            params.setGravity(Gravity.CENTER);
            params.setMargins(gap, gap, gap, gap);
            addView(button, params);
        }
    }

    /** The chosen day 1–31, or 0. */
    public int getDay() {
        return day;
    }

    public void setDay(int value) {
        select(value >= 1 && value <= 31 ? value : 0, false);
    }

    public void onDayChanged(IntConsumer listener) {
        changed = listener;
    }

    private void select(int value, boolean fromUser) {
        day = value;
        for (int i = 0; i < getChildCount(); i++) ((MaterialButton) getChildAt(i)).setChecked(i + 1 == value);
        if (fromUser && changed != null) changed.accept(value);
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setEnabled(enabled);
    }
}
