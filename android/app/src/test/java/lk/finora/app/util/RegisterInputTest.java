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

    @Test public void checklistRowsTickOneRuleEach() {
        assertFalse(RegisterInput.hasLength("Abc1234"));
        assertTrue(RegisterInput.hasLength("Abc12345"));
        assertFalse(RegisterInput.hasLength("a".repeat(73)));
        assertTrue(RegisterInput.hasLetter("1234567x"));
        assertFalse(RegisterInput.hasLetter("12345678"));
        assertFalse(RegisterInput.hasLetter("ඉලක්කම්12345"));
        assertTrue(RegisterInput.hasDigit("password9"));
        assertFalse(RegisterInput.hasDigit("password"));
        assertTrue(RegisterInput.hasStrongPassword("Lanka2026"));
        assertFalse(RegisterInput.hasStrongPassword("Lanka"));
    }
}
