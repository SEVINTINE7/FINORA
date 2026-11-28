package lk.finora.app.ui.support;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;

import lk.finora.app.R;

/**
 * T70.3/D72 builds the STAFF list screens (activity_staff_list): section headings and cards with an icon, title,
 * subtitle, a status chip or a count, optional body texts and up to two actions.
 */
final class StaffCards {
    /** Icon colours, as on the More tab: green, amber for problems, red for destructive entries. */
    enum Tone { ACCENT, WARN, NEUTRAL }

    private final Activity activity;
    private final LinearLayout list;

    StaffCards(Activity activity) {
        this.activity = activity;
        this.list = activity.findViewById(R.id.staff_list);
    }

    /** Fills the fixed parts of the screen: heading, note text and icon. */
    static void frame(Activity activity, int heading, CharSequence note, int noteIcon) {
        TextView title = activity.findViewById(R.id.staff_list_heading);
        title.setText(heading);
        ViewCompat.setAccessibilityHeading(title, true);
        TextView text = activity.findViewById(R.id.staff_list_note);
        text.setText(note);
        text.setCompoundDrawablesRelativeWithIntrinsicBounds(noteIcon, 0, 0, 0);
    }

    /** Loading, error or notice line, retry button and refresh state. */
    static void status(Activity activity, boolean busy, int error, int notice) {
        TextView status = activity.findViewById(R.id.staff_list_status);
        int message = busy ? R.string.records_loading : error != 0 ? error : notice;
        status.setText(message == 0 ? "" : activity.getString(message));
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        activity.findViewById(R.id.staff_list_retry).setVisibility(!busy && error != 0 ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.staff_list_reload).setEnabled(!busy);
    }

    void clear() { list.removeAllViews(); }

    void section(CharSequence text) {
        var heading = new TextView(activity);
        heading.setTextAppearance(R.style.FinoraText_Label);
        heading.setText(text);
        int pad = dp(4);
        heading.setPaddingRelative(pad, 0, pad, 0);
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(20);
        params.bottomMargin = dp(8);
        ViewCompat.setAccessibilityHeading(heading, true);
        list.addView(heading, params);
    }

    /** A plain line in the list, for empty states and hints. */
    TextView line(CharSequence text) {
        var view = new TextView(activity);
        view.setTextAppearance(R.style.FinoraText_Body);
        view.setTextColor(activity.getColor(R.color.finora_muted));
        view.setText(text);
        int pad = dp(4);
        view.setPaddingRelative(pad, 0, pad, 0);
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        params.bottomMargin = dp(8);
        list.addView(view, params);
        return view;
    }

    /** Adds a card; {@code description} is what TalkBack reads for the card's header. */
    View card(int icon, Tone tone, CharSequence title, CharSequence subtitle, CharSequence description) {
        View card = activity.getLayoutInflater().inflate(R.layout.item_staff_card, list, false);
        ImageView image = card.findViewById(R.id.link_icon);
        image.setImageResource(icon);
        int surface = switch (tone) {
            case ACCENT -> R.color.finora_success_surface;
            case WARN -> R.color.finora_warn_fill;
            case NEUTRAL -> R.color.finora_neutral_surface;
        };
        int ink = switch (tone) {
            case ACCENT -> R.color.finora_green;
            case WARN -> R.color.finora_warn_ink;
            case NEUTRAL -> R.color.finora_ink;
        };
        image.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(surface)));
        image.setImageTintList(ColorStateList.valueOf(activity.getColor(ink)));
        ((TextView) card.findViewById(R.id.link_title)).setText(title);
        TextView sub = card.findViewById(R.id.link_text);
        sub.setText(subtitle);
        sub.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
        card.findViewById(R.id.staff_card_head).setContentDescription(description);
        list.addView(card);
        return card;
    }

    /** Status chip (icon + word) for a ticket, saved-reply or flag state. */
    static void chip(View card, String state) {
        TextView chip = card.findViewById(R.id.staff_card_chip);
        chip.setVisibility(View.VISIBLE);
        SupportRows.status(chip, state);
    }

    static void count(View card, long value) {
        card.findViewById(R.id.staff_card_count_box).setVisibility(View.VISIBLE);
        ((TextView) card.findViewById(R.id.staff_card_count)).setText(String.valueOf(value));
    }

    /** A quoted text under the header (saved-reply copy); Sinhala uses the Sinhala font through its locale. */
    void quote(View card, CharSequence text) {
        LinearLayout body = card.findViewById(R.id.staff_card_body);
        var view = new TextView(activity);
        view.setTextAppearance(R.style.FinoraText_Body);
        view.setTextColor(activity.getColor(R.color.finora_ink));
        view.setText(text);
        view.setBackgroundResource(R.drawable.finora_quote_bar);
        view.setPaddingRelative(dp(12), dp(2), 0, dp(2));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        body.addView(view, params);
    }

    /** Lays this card's actions out one per line, for cards with long or many actions. */
    static void stack(View card) {
        ((LinearLayout) card.findViewById(R.id.staff_card_actions)).setOrientation(LinearLayout.VERTICAL);
    }

    /** A filled or outlined action; TalkBack hears the action and the card it belongs to. */
    MaterialButton action(View card, int text, int icon, boolean filled, CharSequence summary, Runnable run) {
        LinearLayout actions = card.findViewById(R.id.staff_card_actions);
        actions.setVisibility(View.VISIBLE);
        var button = (MaterialButton) activity.getLayoutInflater().inflate(
                filled ? R.layout.item_staff_action : R.layout.item_staff_action_outlined, actions, false);
        button.setText(text);
        if (icon != 0) button.setIconResource(icon);
        button.setContentDescription(activity.getString(text) + ". " + summary);
        button.setOnClickListener(view -> run.run());
        var params = (LinearLayout.LayoutParams) button.getLayoutParams();
        if (actions.getOrientation() == LinearLayout.VERTICAL) {
            params.width = -1;
            params.weight = 0;
            if (actions.getChildCount() > 0) params.topMargin = dp(4);
        } else if (actions.getChildCount() > 0) params.setMarginStart(dp(8));
        actions.addView(button, params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
