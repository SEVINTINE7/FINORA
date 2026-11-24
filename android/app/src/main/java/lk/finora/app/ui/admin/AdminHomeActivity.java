package lk.finora.app.ui.admin;

import lk.finora.app.R;
import lk.finora.app.ui.auth.ProtectedHomeActivity;

public final class AdminHomeActivity extends ProtectedHomeActivity {
    @Override protected String requiredRole() { return "ADMIN"; }
    @Override protected int titleResource() { return R.string.home_admin; }

    @Override protected void addNavigation(android.widget.LinearLayout layout, android.widget.TextView title,
            android.os.Bundle state) {
        var diagnostics = new android.widget.Button(this);
        diagnostics.setText(R.string.sms_diagnostic_admin);
        diagnostics.setOnClickListener(view -> startActivity(new android.content.Intent(this,
                lk.finora.app.ui.support.DiagnosticQueueActivity.class)));
        layout.addView(diagnostics, 1);
        var staff = new android.widget.Button(this);
        staff.setText(R.string.staff_manage);
        staff.setOnClickListener(view -> {
            var screen = lk.finora.app.ui.staff.StaffAccessActivity.class;
            startActivity(new android.content.Intent(this, screen)
                    .putExtra(lk.finora.app.ui.staff.StaffAccessActivity.ADMINISTRATION, true));
        });
        layout.addView(staff);
    }
}
