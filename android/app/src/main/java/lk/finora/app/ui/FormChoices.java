package lk.finora.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import lk.finora.app.R;

/** Single selection with the existing form option order and values. */
public final class FormChoices extends ChipGroup {
    private ArrayAdapter<?> options;
    private Runnable selectionChanged;

    public FormChoices(Context context, AttributeSet attributes) {
        super(context, attributes);
        setSingleSelection(true);
        setSelectionRequired(true);
        int spacing = getResources().getDimensionPixelSize(R.dimen.finora_space_xs);
        setChipSpacing(spacing);
        setOnCheckedStateChangeListener((group, checked) -> {
            if (selectionChanged != null) selectionChanged.run();
        });
    }

    public void setAdapter(ArrayAdapter<?> adapter) {
        removeAllViews();
        options = adapter;
        for (int i = 0; i < adapter.getCount(); i++) {
            var chip = new Chip(getContext());
            chip.setId(View.generateViewId());
            chip.setSaveEnabled(false);
            chip.setText(String.valueOf(adapter.getItem(i)));
            chip.setCheckable(true);
            chip.setEnabled(isEnabled());
            chip.setEnsureMinTouchTargetSize(true);
            chip.ensureAccessibleTouchTarget(getResources().getDimensionPixelSize(R.dimen.finora_touch));
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.setChipBackgroundColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{getContext().getColor(R.color.finora_mint), getContext().getColor(R.color.finora_field)}));
            chip.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{getContext().getColor(R.color.finora_navy), getContext().getColor(R.color.finora_ink)}));
            addView(chip);
        }
        if (adapter.getCount() > 0) setSelection(0);
    }

    public void setSelection(int position) {
        if (position >= 0 && position < getChildCount()) check(getChildAt(position).getId());
    }

    public int getSelectedItemPosition() {
        return indexOfChild(findViewById(getCheckedChipId()));
    }

    public Object getSelectedItem() {
        int selected = getSelectedItemPosition();
        return options == null || selected < 0 ? null : options.getItem(selected);
    }

    public void onSelectionChanged(Runnable listener) {
        selectionChanged = listener;
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setEnabled(enabled);
    }
}
