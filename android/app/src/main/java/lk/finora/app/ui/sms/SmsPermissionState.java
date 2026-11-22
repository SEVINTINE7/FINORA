package lk.finora.app.ui.sms;

enum SmsPermissionState {
    UNAVAILABLE, GRANTED, FIRST_REQUEST, DENIED_WITH_RATIONALE, SETTINGS_AVAILABLE;

    static SmsPermissionState resolve(boolean declared, boolean granted, boolean requestedBefore,
            boolean showRationale) {
        if (!declared) {
            return UNAVAILABLE;
        }
        if (granted) {
            return GRANTED;
        }
        if (showRationale) {
            return DENIED_WITH_RATIONALE;
        }
        return requestedBefore ? SETTINGS_AVAILABLE : FIRST_REQUEST;
    }
}
