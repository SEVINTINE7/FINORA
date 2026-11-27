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
