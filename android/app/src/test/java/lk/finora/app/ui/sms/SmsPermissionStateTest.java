package lk.finora.app.ui.sms;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmsPermissionStateTest {
    @Test
    public void onlyDeclaredPermissionWithFirstRequestOrRationaleCanPrompt() {
        assertFalse(SmsPermissionState.resolve(false, true, false, true).canRequest());
        assertTrue(SmsPermissionState.resolve(true, false, false, false).canRequest());
        assertTrue(SmsPermissionState.resolve(true, false, true, true).canRequest());
        assertFalse(SmsPermissionState.resolve(true, false, true, false).canRequest());
        assertFalse(SmsPermissionState.resolve(true, true, false, false).canRequest());
    }

    @Test
    public void undeclaredPermissionIsUnavailableEvenWithEarlierRequestHistory() {
        assertEquals(SmsPermissionState.UNAVAILABLE,
                SmsPermissionState.resolve(false, false, true, false));
    }

    @Test
    public void grantedPermissionTakesPriorityOverRequestHistory() {
        assertEquals(SmsPermissionState.GRANTED,
                SmsPermissionState.resolve(true, true, false, false));
    }

    @Test
    public void noRationaleBeforeFirstRequestStillAllowsRequest() {
        assertEquals(SmsPermissionState.FIRST_REQUEST,
                SmsPermissionState.resolve(true, false, false, false));
    }

    @Test
    public void deniedPermissionWithRationaleAllowsAnotherRequest() {
        assertEquals(SmsPermissionState.DENIED_WITH_RATIONALE,
                SmsPermissionState.resolve(true, false, true, true));
    }

    @Test
    public void previousRequestWithoutRationaleOffersSettings() {
        assertEquals(SmsPermissionState.SETTINGS_AVAILABLE,
                SmsPermissionState.resolve(true, false, true, false));
    }

    @Test
    public void grantAndRevocationAfterSettingsUseCurrentPermission() {
        assertEquals(SmsPermissionState.SETTINGS_AVAILABLE,
                SmsPermissionState.resolve(true, false, true, false));
        assertEquals(SmsPermissionState.GRANTED,
                SmsPermissionState.resolve(true, true, true, false));
        assertEquals(SmsPermissionState.SETTINGS_AVAILABLE,
                SmsPermissionState.resolve(true, false, true, false));
    }
}
