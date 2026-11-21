package lk.finora.app.ui.staff;

import lk.finora.app.R;
import lk.finora.app.ui.auth.ProtectedHomeActivity;

public final class StaffHomeActivity extends ProtectedHomeActivity {
    @Override protected String requiredRole() { return "STAFF"; }
    @Override protected int titleResource() { return R.string.home_staff; }
}
