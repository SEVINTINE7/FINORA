package lk.finora.app.util;

/** Client-side checks that mirror the API's register rules (D09), so the user sees problems early. */
public final class RegisterInput {
    private RegisterInput() {}

    /** 8–72 characters with at least one letter and one digit (D09; BCrypt reads at most 72). */
    public static boolean hasStrongPassword(String password) {
        if (password.length() < 8 || password.length() > 72) return false;
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) letter = true;
            if (c >= '0' && c <= '9') digit = true;
        }
        return letter && digit;
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
