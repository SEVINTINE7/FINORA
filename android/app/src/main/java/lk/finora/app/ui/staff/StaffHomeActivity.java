package lk.finora.app.ui.staff;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.databinding.PanelStaffHomeBinding;
import lk.finora.app.ui.auth.ProtectedHomeActivity;
import lk.finora.app.ui.settings.SettingsRows;
import lk.finora.app.ui.support.DiagnosticQueueActivity;
import lk.finora.app.ui.support.SupportActivity;
import lk.finora.app.util.LocaleHelper;

/**
 * T70.2/D72 Staff home: queue counts in a navy header, then Work rows (requests, saved replies, SMS diagnostics),
 * Account rows (Language, Sign out) and the rule 4 note. Staff never see user transactions.
 */
public final class StaffHomeActivity extends ProtectedHomeActivity {
    private StaffStatsViewModel stats;
    private View language;
    private Button logout;

    @Override protected String requiredRole() { return "STAFF"; }
    @Override protected int titleResource() { return R.string.home_staff; }
    @Override protected boolean showPlaceholder() { return false; }

    @Override protected void placeAccountControls(LinearLayout layout, View language, Button logout) {
        // The Account rows trigger the shell's language picker and sign-out button.
        this.language = language;
        this.logout = logout;
    }

    @Override protected void addNavigation(LinearLayout layout, TextView title, Bundle state) {
        stats = new ViewModelProvider(this).get(StaffStatsViewModel.class);
        getWindow().setBackgroundDrawableResource(R.drawable.finora_staff_window);
        WindowCompat.getInsetsController(getWindow(), layout)
                .setAppearanceLightNavigationBars(getResources().getBoolean(R.bool.finora_light_bars));
        layout.setPadding(0, 0, 0, 0);
        layout.setBackground(null);
        title.setVisibility(View.GONE);
        setTitle(R.string.home_staff);

        var home = PanelStaffHomeBinding.inflate(getLayoutInflater(), layout, false);
        layout.addView(home.getRoot(), new LinearLayout.LayoutParams(-1, 0, 1));
        ViewCompat.setAccessibilityHeading(home.staffTitle, true);
        ViewCompat.setAccessibilityHeading(home.staffWorkHeading, true);
        ViewCompat.setAccessibilityHeading(home.staffAccountHeading, true);

        View requests = home.staffRequests.getRoot();
        View replies = home.staffReplies.getRoot();
        View diagnostics = home.staffDiagnostics.getRoot();
        View languageRow = home.staffLanguage.getRoot();
        View signOut = home.staffSignOut.getRoot();
        SettingsRows.bind(requests, R.drawable.finora_ic_mail, R.string.support_tickets,
                R.string.staff_requests_text, SettingsRows.Tone.ACCENT);
        SettingsRows.bind(replies, R.drawable.finora_ic_cat_book, R.string.support_catalog,
                R.string.staff_replies_text, SettingsRows.Tone.ACCENT);
        SettingsRows.bind(diagnostics, R.drawable.finora_ic_trend, R.string.sms_diagnostic_queue,
                R.string.staff_diagnostics_text, SettingsRows.Tone.ACCENT);
        SettingsRows.bind(languageRow, R.drawable.finora_ic_language, R.string.language_selector,
                0, SettingsRows.Tone.NEUTRAL);
        SettingsRows.bind(signOut, R.drawable.finora_ic_sign_out, R.string.sign_out, 0, SettingsRows.Tone.DANGER);

        bindFocusReturningRoute(requests, R.string.support_tickets, SupportActivity.class);
        replies.setOnClickListener(view -> startActivity(
                new Intent(this, SupportActivity.class).putExtra(SupportActivity.CATALOG, true)));
        diagnostics.setOnClickListener(view -> startActivity(new Intent(this, DiagnosticQueueActivity.class)));
        languageRow.setOnClickListener(view -> { if (language != null) language.performClick(); });
        signOut.setOnClickListener(view -> { if (logout != null) logout.performClick(); });

        var languageState = new ViewModelProvider(this).get(LocaleHelper.ProfileLanguage.class);
        languageState.state().observe(this, selection -> {
            SettingsRows.subtitle(languageRow, getString(selection.busy ? R.string.language_syncing
                    : "SI".equals(selection.language) ? R.string.language_sinhala : R.string.language_english));
            SettingsRows.enable(languageRow, !selection.busy);
        });

        home.staffCountsRetry.setOnClickListener(view -> stats.load());
        stats.work.changes.observe(this, ignored -> renderCounts(home));
    }

    private void renderCounts(PanelStaffHomeBinding home) {
        var result = stats.stats;
        boolean current = stats.work.isCurrent();
        boolean busy = stats.work.busy();
        int error = current && !busy ? stats.work.error : 0;
        boolean known = current && !busy && error == 0 && result != null;
        String none = getString(R.string.staff_count_none);
        home.staffCountMine.setText(known ? String.valueOf(result.myOpenTickets()) : none);
        home.staffCountWaiting.setText(known ? String.valueOf(result.unassignedTickets()) : none);
        home.staffCounts.setContentDescription(known ? getString(R.string.staff_counts_description,
                result.myOpenTickets(), result.unassignedTickets())
                : getString(busy ? R.string.records_loading : R.string.staff_home_today));
        home.staffCountsProblem.setVisibility(error != 0 ? View.VISIBLE : View.GONE);
        if (error != 0) home.staffCountsState.setText(error);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing() && stats != null) stats.load();
    }
}
