package lk.finora.app.ui.auth;

import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.function.BooleanSupplier;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore.Session;
import lk.finora.app.util.LocaleHelper;

/**
 * Language button shared by the home and settings screens. For a USER the choice applies at once and is
 * saved with PATCH /me (T07.5, D60); Admin/Staff have no /me, so only the app locale changes.
 */
public final class ProfileLanguageButton {
    private ProfileLanguageButton() {}

    public static Button create(AppCompatActivity activity, Session entry, BooleanSupplier paused) {
        if (!"USER".equals(entry.role)) {
            return LocaleHelper.selector(activity, LocaleHelper::apply);
        }
        LocaleHelper.ProfileLanguage profile =
                new ViewModelProvider(activity).get(LocaleHelper.ProfileLanguage.class);
        Button selector = LocaleHelper.selector(activity, profile::select);
        profile.initialize(entry);
        profile.state().observe(activity, selection -> {
            if (paused.getAsBoolean() || !profile.isCurrent()) {
                return;
            }
            selector.setEnabled(!selection.busy);
            selector.setText(selection.busy ? R.string.language_syncing : R.string.language_selector);
            LocaleHelper.apply(selection.language);
            if (selection.failed) {
                Toast.makeText(activity, R.string.language_save_failed, Toast.LENGTH_LONG).show();
            }
        });
        return selector;
    }
    /**
     * T68.2/D72: Settings shows the same language choice as an English / Sinhala segmented toggle. It saves
     * through the same PATCH /me path and goes back to the saved language when the save fails.
     */
    public static void bindToggle(AppCompatActivity activity, Session entry, BooleanSupplier paused,
            com.google.android.material.button.MaterialButtonToggleGroup group, int english, int sinhala,
            android.widget.TextView status) {
        LocaleHelper.ProfileLanguage profile =
                new ViewModelProvider(activity).get(LocaleHelper.ProfileLanguage.class);
        boolean[] rendering = {false};
        group.addOnButtonCheckedListener((toggle, id, checked) -> {
            if (checked && !rendering[0]) profile.select(id == sinhala ? "SI" : "EN");
        });
        profile.initialize(entry);
        profile.state().observe(activity, selection -> {
            if (paused.getAsBoolean() || !profile.isCurrent()) {
                return;
            }
            rendering[0] = true;
            group.check("SI".equals(selection.language) ? sinhala : english);
            rendering[0] = false;
            for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(!selection.busy);
            status.setText(selection.busy ? R.string.language_syncing : R.string.settings_language_info);
            LocaleHelper.apply(selection.language);
            if (selection.failed) {
                Toast.makeText(activity, R.string.language_save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }
}
