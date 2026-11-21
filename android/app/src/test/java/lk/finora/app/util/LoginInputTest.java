package lk.finora.app.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginInputTest {
    @Test
    public void acceptsEmailAndRejectsMalformedInput() {
        assertTrue(LoginInput.hasEmail("user@example.test"));
        assertFalse(LoginInput.hasEmail("@example.test"));
        assertFalse(LoginInput.hasEmail("user a@example.test"));
        assertFalse(LoginInput.hasEmail("user@@example.test"));
    }

    @Test
    public void rejectsBlankPasswordsWithoutEnforcingRegistrationRules() {
        assertFalse(LoginInput.hasPassword(""));
        assertFalse(LoginInput.hasPassword("   "));
        assertTrue(LoginInput.hasPassword("existing password"));
    }
}
