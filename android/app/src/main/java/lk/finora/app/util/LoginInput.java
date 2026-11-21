package lk.finora.app.util;

public final class LoginInput {
    private LoginInput() {}

    public static boolean hasEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1 && at == email.lastIndexOf('@')
                && email.chars().noneMatch(Character::isWhitespace);
    }

    public static boolean hasPassword(String password) {
        return !password.trim().isEmpty();
    }
}
