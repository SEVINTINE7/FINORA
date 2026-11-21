package lk.finora.app.ui.main;

import lk.finora.app.R;
import lk.finora.app.ui.auth.ProtectedHomeActivity;

public final class MainActivity extends ProtectedHomeActivity {
    @Override protected String requiredRole() { return "USER"; }
    @Override protected int titleResource() { return R.string.home_user; }
}
