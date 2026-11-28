package lk.finora.app.util;

/** Client-side checks that mirror the API's register rules (D09), so the user sees problems early. */
public final class RegisterInput {
    private RegisterInput() {}

    /** 8–72 characters with at least one letter and one digit (D09; BCrypt reads at most 72). */
    public static boolean hasStrongPassword(String password) {
        return hasLength(password) && hasLetter(password) && hasDigit(password);
    }

    /** 8–72 characters (T69.2 checklist row). */
    public static boolean hasLength(String password) {
        return password.length() >= 8 && password.length() <= 72;
    }

    /** At least one ASCII letter, as the API checks. */
    public static boolean hasLetter(String password) {
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return true;
        }
        return false;
    }

    /** At least one digit 0–9. */
    public static boolean hasDigit(String password) {
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= '0' && c <= '9') return true;
        }
        return false;
    }

    public static boolean hasName(String name) {
        String trimmed = name.trim();
        return !trimmed.isEmpty() && trimmed.length() <= 100;
    }

    /** Submit is allowed only when both boxes are ticked (T07.4). */
    public static boolean canSubmit(boolean over18, boolean consent, boolean busy) {
        return over18 && consent && !busy;
    }
}
