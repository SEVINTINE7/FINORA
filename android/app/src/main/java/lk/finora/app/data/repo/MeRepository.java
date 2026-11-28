package lk.finora.app.data.repo;

import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;

/** The signed-in user's own account (T07.5). Language changes go through LocaleHelper.ProfileLanguage. */
public final class MeRepository {
    private final ProtectedRequests requests;

    public MeRepository(ProtectedRequests requests) {
        this.requests = requests;
    }

    /** GET /api/v1/me; read the greeting name with {@link #firstName(okhttp3.Response)} (T62). */
    public Call profile() {
        return requests.call(new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1/me").build()).get().build());
    }

    /** The first word of the profile's full name, or an empty string when none is set. */
    public static String firstName(okhttp3.Response response) throws java.io.IOException {
        Profile profile = AccountRepository.read(response, Profile.class);
        return firstWord(profile.fullName);
    }

    static String firstWord(String fullName) {
        if (fullName == null) return "";
        String trimmed = fullName.strip();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static final class Profile {
        String fullName;
        String email;
        String language;
    }

    /** The parts of GET /api/v1/me that Settings and the More tab show (T68). */
    public record Details(String fullName, String email, String language) {
        /** Up to two initials for the profile badge, from the name or else the email. */
        public String initials() {
            String source = fullName == null || fullName.isBlank() ? email : fullName;
            if (source == null || source.isBlank()) return "";
            StringBuilder letters = new StringBuilder();
            for (String word : source.strip().split("\s+")) {
                if (word.isEmpty() || letters.length() == 2) continue;
                letters.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
            }
            return letters.toString();
        }
    }

    public static Details details(okhttp3.Response response) throws java.io.IOException {
        Profile profile = AccountRepository.read(response, Profile.class);
        return new Details(profile.fullName, profile.email, profile.language);
    }

    /** PUT /api/v1/me {fullName, language}: 200 with the profile (T07). PATCH only changes the language (D60). */
    public Call updateProfile(String fullName, String language) {
        return requests.call(new Request.Builder().url(meUrl(requests.origin()))
                .put(AccountRepository.json(java.util.Map.of("fullName", fullName, "language", language))).build());
    }

    /**
     * Planned in T74 (D72), see docs/api-contract.md: POST /api/v1/me/password {currentPassword, newPassword}
     * returns 204 and revokes every session. Until the API has it the server answers 404.
     */
    public Call changePassword(String currentPassword, String newPassword) {
        return requests.call(new Request.Builder().url(passwordUrl(requests.origin()))
                .post(AccountRepository.json(java.util.Map.of("currentPassword", currentPassword,
                        "newPassword", newPassword))).build());
    }

    /** Planned in T74 (D72): POST /api/v1/me/email {newEmail, password} returns 202 and emails a code. */
    public Call requestEmailChange(String newEmail, String password) {
        return requests.call(new Request.Builder().url(emailUrl(requests.origin()))
                .post(AccountRepository.json(java.util.Map.of("newEmail", newEmail, "password", password))).build());
    }

    /** Planned in T74 (D72): POST /api/v1/me/email/confirm {newEmail, code} returns 200 with the profile. */
    public Call confirmEmailChange(String newEmail, String code) {
        return requests.call(new Request.Builder().url(emailConfirmUrl(requests.origin()))
                .post(AccountRepository.json(java.util.Map.of("newEmail", newEmail, "code", code))).build());
    }

    static HttpUrl meUrl(HttpUrl origin) {
        return origin.newBuilder().addPathSegments("api/v1/me").build();
    }

    static HttpUrl passwordUrl(HttpUrl origin) {
        return origin.newBuilder().addPathSegments("api/v1/me/password").build();
    }

    static HttpUrl emailUrl(HttpUrl origin) {
        return origin.newBuilder().addPathSegments("api/v1/me/email").build();
    }

    static HttpUrl emailConfirmUrl(HttpUrl origin) {
        return origin.newBuilder().addPathSegments("api/v1/me/email/confirm").build();
    }

    /** POST /api/v1/me/consent/withdraw: 204; the server revokes every refresh token (D15). */
    public Call withdrawConsent() {
        return requests.call(new Request.Builder().url(withdrawUrl(requests.origin()))
                .post(RequestBody.create(new byte[0])).build());
    }

    static HttpUrl withdrawUrl(HttpUrl origin) {
        return origin.newBuilder().addPathSegments("api/v1/me/consent/withdraw").build();
    }

    public Call exportData() {
        return requests.call(new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1/me/export").build()).get().build());
    }

    public Call deleteData(String password) {
        return requests.call(new Request.Builder().url(requests.origin().newBuilder()
                .addPathSegments("api/v1/me").build())
                .delete(AccountRepository.json(java.util.Map.of("password", password))).build());
    }
}
