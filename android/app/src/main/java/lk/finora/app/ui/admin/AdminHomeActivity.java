package lk.finora.app.ui.admin;

import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.StaffProvisioningRepository;
import lk.finora.app.data.repo.StaffProvisioningRepository.AdminCounts;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.auth.ProtectedHomeActivity;

public final class AdminHomeActivity extends ProtectedHomeActivity {
    private Work countsWork;
    private AdminCounts counts;
    @Override protected String requiredRole() { return "ADMIN"; }
    @Override protected int titleResource() { return R.string.home_admin; }
    @Override protected boolean showPlaceholder() { return false; }

    @Override protected void addNavigation(android.widget.LinearLayout layout, android.widget.TextView title,
            android.os.Bundle state) {
        var diagnostics = new android.widget.Button(this);
        diagnostics.setText(R.string.sms_diagnostic_admin);
        diagnostics.setOnClickListener(view -> startActivity(new android.content.Intent(this,
                lk.finora.app.ui.support.DiagnosticQueueActivity.class)));
        layout.addView(diagnostics);
        var staff = new android.widget.Button(this);
        staff.setText(R.string.staff_manage);
        staff.setOnClickListener(view -> {
            var screen = lk.finora.app.ui.staff.StaffAccessActivity.class;
            startActivity(new android.content.Intent(this, screen)
                    .putExtra(lk.finora.app.ui.staff.StaffAccessActivity.ADMINISTRATION, true));
        });
        layout.addView(staff);
        var summary = new android.widget.TextView(this);
        summary.setTextColor(getColor(R.color.finora_on_navy));
        summary.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        layout.addView(summary, 1);
        countsWork = new Work(AccountRepository.ProtectedRequests.currentAdmin(), () -> counts = null);
        var retry = new android.widget.Button(this);
        retry.setText(R.string.records_retry);
        retry.setOnClickListener(view -> loadCounts());
        layout.addView(retry);
        countsWork.changes.observe(this, ignored -> {
            retry.setVisibility(countsWork.error == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
            if (!countsWork.isCurrent()) summary.setText("");
            else if (countsWork.busy()) summary.setText(R.string.records_loading);
            else if (countsWork.error != 0) summary.setText(countsWork.error);
            else if (counts != null) summary.setText(getString(R.string.admin_stats_counts, counts.activeUsers(),
                    counts.disabledUsers(), counts.staff(), counts.openTickets(), counts.activeFormats()));
        });
    }

    @Override protected void onResume() { super.onResume(); loadCounts(); }

    private void loadCounts() {
        if (countsWork == null || isFinishing()) return;
        var repository = new StaffProvisioningRepository(countsWork.requests);
        countsWork.run(repository::counts, response -> {
            var data = AccountRepository.read(response, AdminCounts.class);
            for (Long count : new Long[]{data.activeUsers(), data.disabledUsers(), data.staff(),
                    data.openTickets(), data.activeFormats()}) {
                if (count == null || count < 0) throw new java.io.IOException("Invalid ADMIN counts");
            }
            return data;
        }, data -> counts = data, false);
    }

    @Override protected void onDestroy() {
        if (countsWork != null) countsWork.close();
        super.onDestroy();
    }
}
