package lk.finora.api.staff;

import lk.finora.api.support.IntegrationTest;
import lk.finora.api.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class FirstAdminBootstrapTest extends IntegrationTest {
    @Autowired private FirstAdminBootstrap bootstrap;
    @Autowired private PasswordEncoder encoder;

    @Test void oneAdminIsSeededWithoutReplacingItsCredentials() {
        String first = "bootstrap-fixture@example.test";
        String password = "Synthetic8-bootstrap-fixture";
        bootstrap.initialize(first, password);
        bootstrap.initialize("another-fixture@example.test", password + "2");
        var user = userRepository.findByEmail(first).orElseThrow();
        assertEquals(Role.ADMIN, user.getRole());
        assertTrue(encoder.matches(password, user.getPasswordHash()));
        assertNotEquals(password, user.getPasswordHash());
        assertFalse(userRepository.existsByEmail("another-fixture@example.test"));
    }

    @Test void incompleteOrUnsafeConfigurationFailsBeforeTouchingTheDatabase() {
        assertThrows(IllegalStateException.class, () -> bootstrap.initialize("bad", "short"));
        assertThrows(IllegalStateException.class, () -> bootstrap.initialize("fixture@example.test", null));
    }
}
