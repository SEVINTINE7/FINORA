package lk.finora.app.ui.settings;

import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository.HttpFailure;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.util.RegisterInput;

/**
 * T68.2/D72 checks for Edit profile and Change password, and the messages for the planned T74 API answers
 * (see docs/api-contract.md). Before T74 is deployed the API answers 404 for the email and password calls.
 */
public final class ProfileRules {
    private ProfileRules() {}

    /** What is wrong with a password change, checked in this order. */
    public enum PasswordProblem { NONE, CURRENT_MISSING, WEAK, SAME, MISMATCH }

    public static PasswordProblem password(String current, String next, String repeat) {
        if (current.isEmpty() || current.length() > 72) return PasswordProblem.CURRENT_MISSING;
        if (!RegisterInput.hasStrongPassword(next)) return PasswordProblem.WEAK;
        if (next.equals(current)) return PasswordProblem.SAME;
        if (!next.equals(repeat)) return PasswordProblem.MISMATCH;
        return PasswordProblem.NONE;
    }

    /** Emails compare without case, as the API does for sign-in and EMAIL_TAKEN. */
    public static boolean emailChanged(String saved, String typed) {
        return saved == null || !saved.trim().equalsIgnoreCase(typed.trim());
    }

    public static boolean nameChanged(String saved, String typed) {
        return saved == null || !saved.strip().equals(typed.strip());
    }

    /** The emailed confirmation code: exactly six digits (same shape as the T61 reset code). */
    public static boolean hasCode(String code) {
        return code.matches("[0-9]{6}");
    }

    public static int message(Exception failure) {
        if (failure instanceof HttpFailure http) {
            int specific = message(http.status, http.code);
            if (specific != 0) return specific;
        }
        return Work.message(failure);
    }

    /** A message for the T74 answers, or 0 to use the general record messages. */
    static int message(int status, String code) {
        if (status == 404) return R.string.profile_not_available;
        if (status == 429 || "TOO_MANY_REQUESTS".equals(code)) return R.string.profile_too_many;
        if ("INVALID_CREDENTIALS".equals(code)) return R.string.profile_wrong_password;
        if ("EMAIL_TAKEN".equals(code) || "CONFLICT".equals(code)) return R.string.profile_email_taken;
        if ("INVALID_EMAIL_CODE".equals(code)) return R.string.profile_code_wrong;
        return 0;
    }
}
