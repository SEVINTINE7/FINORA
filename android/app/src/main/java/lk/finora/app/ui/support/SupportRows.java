package lk.finora.app.ui.support;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import lk.finora.app.R;
import lk.finora.app.data.repo.SupportRepository.Topic;

/** T68.3/D72 topic icons and the request status chip (icon + word, never colour alone). */
public final class SupportRows {
    private SupportRows() {}

    public static int icon(Topic topic) {
        if (topic == null) return R.drawable.finora_ic_mail;
        return switch (topic) {
            case SIGN_IN -> R.drawable.finora_ic_lock;
            case NAVIGATION -> R.drawable.finora_nav_records;
            case SMS_PERMISSION -> R.drawable.finora_ic_cat_phone;
            case LANGUAGE -> R.drawable.finora_ic_language;
        };
    }

    /** The word for a ticket or saved-reply state, as the screens have always shown it. */
    public static int stateLabel(String key) {
        return switch (key) {
            case "OPEN" -> R.string.sms_diagnostic_open;
            case "IN_PROGRESS" -> R.string.support_in_progress;
            case "CLOSED" -> R.string.support_closed;
            case "DRAFT" -> R.string.support_draft_state;
            case "ACTIVE" -> R.string.support_active;
            case "RETIRED" -> R.string.support_retired;
            case "ACKNOWLEDGED" -> R.string.sms_diagnostic_acknowledged;
            default -> R.string.sms_diagnostic_unknown;
        };
    }

    /** Open: green info mark; Active: green tick; In progress, Draft, Acknowledged: amber clock; Retired: grey archive;
     *  Closed and others: grey tick. */
    public static void status(TextView chip, String key) {
        Context context = chip.getContext();
        int surface;
        int ink;
        int icon;
        switch (key) {
            case "OPEN" -> {
                surface = R.color.finora_success_surface;
                ink = R.color.finora_green;
                icon = R.drawable.finora_ic_info;
            }
            case "ACTIVE" -> {
                surface = R.color.finora_success_surface;
                ink = R.color.finora_green;
                icon = R.drawable.finora_ic_check;
            }
            case "IN_PROGRESS", "DRAFT", "ACKNOWLEDGED" -> {
                surface = R.color.finora_warn_fill;
                ink = R.color.finora_warn_ink;
                icon = R.drawable.finora_ic_clock;
            }
            case "RETIRED" -> {
                surface = R.color.finora_neutral_surface;
                ink = R.color.finora_ink;
                icon = R.drawable.finora_ic_archive;
            }
            default -> {
                surface = R.color.finora_neutral_surface;
                ink = R.color.finora_ink;
                icon = R.drawable.finora_ic_check;
            }
        }
        chip.setText(stateLabel(key));
        chip.setBackgroundTintList(ColorStateList.valueOf(context.getColor(surface)));
        chip.setTextColor(context.getColor(ink));
        var drawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, icon);
        if (drawable != null) {
            int size = Math.round(14 * context.getResources().getDisplayMetrics().density);
            drawable = drawable.mutate();
            drawable.setBounds(0, 0, size, size);
        }
        chip.setCompoundDrawablesRelative(drawable, null, null, null);
        TextViewCompat.setCompoundDrawableTintList(chip, ColorStateList.valueOf(context.getColor(ink)));
    }
}
