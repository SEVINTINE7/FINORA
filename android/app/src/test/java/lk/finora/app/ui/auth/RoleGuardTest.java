package lk.finora.app.ui.auth;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoleGuardTest {
    @Test
    public void destinationsAcceptOnlyTheirVerifiedRole() {
        assertTrue(RoleGuard.permits("USER", "USER"));
        assertTrue(RoleGuard.permits("ADMIN", "ADMIN"));
        assertTrue(RoleGuard.permits("STAFF", "STAFF"));
        assertFalse(RoleGuard.permits("ADMIN", "USER"));
        assertFalse(RoleGuard.permits("STAFF", "USER"));
        assertFalse(RoleGuard.permits("ADMIN", null));
        assertFalse(RoleGuard.permits("UNKNOWN", "UNKNOWN"));
    }
}
