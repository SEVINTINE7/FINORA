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
    private int columns;
    private boolean columnListener;

    public FormChoices(Context context, AttributeSet attributes) {
        super(context, attributes);
        setSingleSelection(true);
        setSelectionRequired(true);
        int spacing = getResources().getDimensionPixelSize(R.dimen.finora_space_xs);
        setChipSpacing(spacing);
        setOnCheckedStateChangeListener((group, checked) -> {
            markTiles();
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

    /** T62: hides a placeholder choice (such as "Choose") while keeping its position and value. */
    public void hideChoice(int position) {
        if (position >= 0 && position < getChildCount()) getChildAt(position).setVisibility(GONE);
    }

    /** T62: shows a tinted icon at the start of one choice. */
    public void setChoiceIcon(int position, int icon) {
        if (position < 0 || position >= getChildCount()) return;
        var chip = (Chip) getChildAt(position);
        chip.setChipIconResource(icon);
        chip.setTag(R.id.form_kind, icon);
        chip.setChipIconVisible(true);
        chip.setChipIconSize(getResources().getDisplayMetrics().density * 18);
        chip.setChipIconTint(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{getContext().getColor(R.color.finora_navy), getContext().getColor(R.color.finora_muted)}));
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

    /** T65/D69: lays the choices out as equal-width tiles, this many per row (0 = natural chip widths). */
    public void setColumns(int count) {
        columns = count;
        if (!columnListener) {
            columnListener = true;
            addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> sizeColumns());
        }
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < getChildCount(); i++) {
            var chip = (Chip) getChildAt(i);
            chip.setChipMinHeight(count > 0 ? 52 * density : 32 * density);
        }
        markTiles();
        requestLayout();
    }

    /** Tiles: the chosen one shows a tick in place of its icon and bold text, so the choice never relies on colour. */
    private void markTiles() {
        if (columns <= 0) return;
        for (int i = 0; i < getChildCount(); i++) {
            var chip = (Chip) getChildAt(i);
            Object icon = chip.getTag(R.id.form_kind);
            if (chip.isChecked()) chip.setChipIconResource(R.drawable.finora_ic_tick);
            else if (icon instanceof Integer resource) chip.setChipIconResource(resource);
            chip.setTypeface(null, chip.isChecked() ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void sizeColumns() {
        if (columns <= 0 || getWidth() == 0) return;
        int available = getWidth() - getPaddingStart() - getPaddingEnd();
        int width = (available - getChipSpacingHorizontal() * (columns - 1)) / columns;
        boolean changed = false;
        for (int i = 0; i < getChildCount(); i++) {
            var params = getChildAt(i).getLayoutParams();
            if (params != null && params.width != width) {
                params.width = width;
                changed = true;
            }
        }
        if (changed) post(this::requestLayout);
    }

    public void onSelectionChanged(Runnable listener) {
        selectionChanged = listener;
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setEnabled(enabled);
    }
}
