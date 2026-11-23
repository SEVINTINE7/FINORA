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
 * saved with PUT /me (T07.5); Admin/Staff have no /me, so only the app locale changes.
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
}
