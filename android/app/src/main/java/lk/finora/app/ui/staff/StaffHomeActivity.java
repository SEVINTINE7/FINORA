package lk.finora.app.ui.staff;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import lk.finora.app.R;
import lk.finora.app.ui.ScreenControls;
import lk.finora.app.ui.auth.ProtectedHomeActivity;

public final class StaffHomeActivity extends ProtectedHomeActivity {
    private StaffStatsViewModel stats;

    @Override protected String requiredRole() { return "STAFF"; }
    @Override protected int titleResource() { return R.string.home_staff; }
    @Override protected boolean showPlaceholder() { return false; }

    @Override protected void addNavigation(LinearLayout layout, TextView title, Bundle state) {
        stats = new ViewModelProvider(this).get(StaffStatsViewModel.class);
        var counts = new TextView(this);
        counts.setTextColor(getColor(R.color.finora_on_navy));
        counts.setTextSize(18);
        layout.addView(counts, 1, new LinearLayout.LayoutParams(-1, -2));
        var retry = ScreenControls.homeAction(this);
        retry.setText(R.string.records_retry);
        retry.setOnClickListener(view -> stats.load());
        layout.addView(retry, 2, new LinearLayout.LayoutParams(-1, -2));
        var diagnostics = ScreenControls.homeAction(this);
        diagnostics.setText(R.string.sms_diagnostic_queue);
        diagnostics.setOnClickListener(view -> startActivity(new android.content.Intent(this,
                lk.finora.app.ui.support.DiagnosticQueueActivity.class)));
        layout.addView(diagnostics);
        var support = ScreenControls.homeAction(this);
        support.setText(R.string.support_tickets);
        bindFocusReturningRoute(support, R.string.support_tickets, lk.finora.app.ui.support.SupportActivity.class);
        layout.addView(support);
        stats.work.changes.observe(this, ignored -> {
            var result = stats.stats;
            int error = stats.work.error;
            boolean current = stats.work.isCurrent();
            if (!current) counts.setText("");
            else if (stats.work.busy()) counts.setText(R.string.records_loading);
            else if (error != 0) counts.setText(error);
            else if (result != null) counts.setText(getString(R.string.staff_queue_counts,
                    result.myOpenTickets(), result.unassignedTickets()));
            retry.setVisibility(current && error != 0 && !stats.work.busy() ? View.VISIBLE : View.GONE);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (!isFinishing() && stats != null) stats.load();
    }
}
