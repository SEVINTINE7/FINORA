package lk.finora.app.ui.auth;

import lk.finora.app.data.local.TokenStore.Session;

final class RoleGuard {
    private RoleGuard() {}

    static boolean permits(String required, String actual) {
        return Session.isSupportedRole(required) && required.equals(actual);
    }
}
