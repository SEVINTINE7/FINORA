package lk.finora.app.ui.admin;

import lk.finora.app.R;
import lk.finora.app.ui.auth.ProtectedHomeActivity;

public final class AdminHomeActivity extends ProtectedHomeActivity {
    @Override protected String requiredRole() { return "ADMIN"; }
    @Override protected int titleResource() { return R.string.home_admin; }
}
