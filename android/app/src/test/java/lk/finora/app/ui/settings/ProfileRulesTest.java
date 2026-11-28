package lk.finora.app.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import lk.finora.app.R;
import lk.finora.app.ui.settings.ProfileRules.PasswordProblem;

/** T68.2/D72 checks for Change password and Edit profile, and the T74 answer messages. */
public class ProfileRulesTest {
    @Test public void passwordChangeNeedsCurrentStrongDifferentAndRepeated() {
        assertEquals(PasswordProblem.CURRENT_MISSING, ProfileRules.password("", "Newpass12", "Newpass12"));
        assertEquals(PasswordProblem.CURRENT_MISSING, ProfileRules.password("a".repeat(73), "Newpass12", "Newpass12"));
        assertEquals(PasswordProblem.WEAK, ProfileRules.password("Oldpass12", "short1", "short1"));
        assertEquals(PasswordProblem.WEAK, ProfileRules.password("Oldpass12", "lettersonly", "lettersonly"));
        assertEquals(PasswordProblem.SAME, ProfileRules.password("Oldpass12", "Oldpass12", "Oldpass12"));
        assertEquals(PasswordProblem.MISMATCH, ProfileRules.password("Oldpass12", "Newpass12", "Newpass13"));
        assertEquals(PasswordProblem.NONE, ProfileRules.password("Oldpass12", "Newpass12", "Newpass12"));
    }

    @Test public void emailCompareIgnoresCaseAndSpaces() {
        assertFalse(ProfileRules.emailChanged("Nimal@Example.com", " nimal@example.com "));
        assertTrue(ProfileRules.emailChanged("nimal@example.com", "nimal.p@example.com"));
        assertTrue(ProfileRules.emailChanged(null, "nimal@example.com"));
    }

    @Test public void nameCompareIgnoresOuterSpaces() {
        assertFalse(ProfileRules.nameChanged("Nimal Perera", "  Nimal Perera "));
        assertTrue(ProfileRules.nameChanged("Nimal Perera", "Nimal P. Perera"));
    }

    @Test public void codeIsSixDigits() {
        assertTrue(ProfileRules.hasCode("012345"));
        assertFalse(ProfileRules.hasCode("12345"));
        assertFalse(ProfileRules.hasCode("12a456"));
        assertFalse(ProfileRules.hasCode("1234567"));
    }

    @Test public void plannedApiAnswersHaveTheirOwnMessages() {
        assertEquals(R.string.profile_not_available, ProfileRules.message(404, "NOT_FOUND"));
        assertEquals(R.string.profile_wrong_password, ProfileRules.message(400, "INVALID_CREDENTIALS"));
        assertEquals(R.string.profile_email_taken, ProfileRules.message(409, "EMAIL_TAKEN"));
        assertEquals(R.string.profile_code_wrong, ProfileRules.message(400, "INVALID_EMAIL_CODE"));
        assertEquals(R.string.profile_too_many, ProfileRules.message(429, "TOO_MANY_REQUESTS"));
        assertEquals(0, ProfileRules.message(400, "VALIDATION_ERROR"));
    }
}
