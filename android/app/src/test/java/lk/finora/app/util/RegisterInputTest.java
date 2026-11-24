package lk.finora.app.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RegisterInputTest {
    @Test public void passwordFollowsD09() {
        assertTrue(RegisterInput.hasStrongPassword("Test1234"));
        assertTrue(RegisterInput.hasStrongPassword("a1234567"));
        assertFalse(RegisterInput.hasStrongPassword("Test123"));       // 7 characters
        assertFalse(RegisterInput.hasStrongPassword("abcdefgh"));      // no digit
        assertFalse(RegisterInput.hasStrongPassword("12345678"));      // no letter
        assertFalse(RegisterInput.hasStrongPassword("a1" + "x".repeat(71))); // 73 > 72
    }

    @Test public void nameIsRequiredAndAtMost100() {
        assertTrue(RegisterInput.hasName(" Nimal Perera "));
        assertFalse(RegisterInput.hasName("   "));
        assertFalse(RegisterInput.hasName("n".repeat(101)));
    }

    @Test public void submitNeedsBothTicksAndNoRequestRunning() {
        assertTrue(RegisterInput.canSubmit(true, true, false));
        assertFalse(RegisterInput.canSubmit(true, false, false));
        assertFalse(RegisterInput.canSubmit(false, true, false));
        assertFalse(RegisterInput.canSubmit(false, false, false));
        assertFalse(RegisterInput.canSubmit(true, true, true));
    }
}
