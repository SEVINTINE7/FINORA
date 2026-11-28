package lk.finora.app.ui.settings;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import lk.finora.app.R;
import lk.finora.app.data.repo.MeRepository;

/** T68/D72 rows on the More tab and Settings (layout item_import_link) and the profile card. */
public final class SettingsRows {
    private SettingsRows() {}

    /** Icon colours: green for money and profile, grey for app and help entries, red for destructive ones. */
    public enum Tone { ACCENT, NEUTRAL, DANGER }

    public static void bind(View row, int icon, int title, int text, Tone tone) {
        var context = row.getContext();
        ImageView image = row.findViewById(R.id.link_icon);
        image.setImageResource(icon);
        int surface = switch (tone) {
            case ACCENT -> R.color.finora_success_surface;
            case NEUTRAL -> R.color.finora_neutral_surface;
            case DANGER -> R.color.finora_error_surface;
        };
        int ink = switch (tone) {
            case ACCENT -> R.color.finora_green;
            case NEUTRAL -> R.color.finora_ink;
            case DANGER -> R.color.finora_error;
        };
        image.setBackgroundTintList(ColorStateList.valueOf(context.getColor(surface)));
        image.setImageTintList(ColorStateList.valueOf(context.getColor(ink)));
        TextView heading = row.findViewById(R.id.link_title);
        heading.setText(title);
        if (tone == Tone.DANGER) heading.setTextColor(context.getColor(R.color.finora_error));
        subtitle(row, text == 0 ? null : context.getString(text));
    }

    public static void subtitle(View row, CharSequence text) {
        TextView line = row.findViewById(R.id.link_text);
        line.setText(text);
        line.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
    }

    /** Disables a row and dims it, so a busy request cannot be started twice. */
    public static void enable(View row, boolean enabled) {
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.5f);
    }

    /** Fills the profile card; before /me answers it shows a neutral title and no email. */
    public static void profile(View card, MeRepository.Details details) {
        var context = card.getContext();
        TextView initials = card.findViewById(R.id.profile_initials);
        TextView name = card.findViewById(R.id.profile_name);
        TextView email = card.findViewById(R.id.profile_email);
        boolean known = details != null;
        String full = known && details.fullName() != null ? details.fullName().strip() : "";
        name.setText(full.isEmpty() ? context.getString(R.string.profile_unknown) : full);
        email.setText(known && details.email() != null ? details.email() : "");
        email.setVisibility(email.getText().length() == 0 ? View.GONE : View.VISIBLE);
        String letters = known ? details.initials() : "";
        initials.setText(letters);
        initials.setVisibility(letters.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }
}
