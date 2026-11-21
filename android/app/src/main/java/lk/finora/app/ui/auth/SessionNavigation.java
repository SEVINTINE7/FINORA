package lk.finora.app.ui.auth;

import android.app.Activity;
import android.content.Intent;

import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;
import lk.finora.app.ui.admin.AdminHomeActivity;
import lk.finora.app.ui.login.LoginActivity;
import lk.finora.app.ui.main.MainActivity;
import lk.finora.app.ui.staff.StaffHomeActivity;

public final class SessionNavigation {
    public static final String STORAGE_ERROR = "lk.finora.app.storage_error";

    private SessionNavigation() {}

    public static boolean openHome(Activity from) {
        TokenStore store = TokenStore.current();
        Session session = store == null ? null : store.verifiedSession();
        if (session == null) {
            return false;
        }
        Class<?> home;
        switch (session.role) {
            case "USER": home = MainActivity.class; break;
            case "ADMIN": home = AdminHomeActivity.class; break;
            case "STAFF": home = StaffHomeActivity.class; break;
            default: return false;
        }
        replace(from, new Intent(from, home));
        return true;
    }

    public static void openLogin(Activity from, boolean storageError) {
        replace(from, new Intent(from, LoginActivity.class).putExtra(STORAGE_ERROR, storageError));
    }

    private static void replace(Activity from, Intent intent) {
        from.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        from.finish();
    }
}
