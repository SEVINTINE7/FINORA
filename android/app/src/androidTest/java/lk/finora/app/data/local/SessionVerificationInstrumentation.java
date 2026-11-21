package lk.finora.app.data.local;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import lk.finora.app.BuildConfig;
import lk.finora.app.data.api.ApiClient;
import lk.finora.app.data.api.SessionAuthenticator.Outcome;
import lk.finora.app.data.local.TokenStore.Session;
import lk.finora.app.ui.admin.AdminHomeActivity;
import lk.finora.app.ui.login.LoginActivity;
import lk.finora.app.ui.staff.StaffHomeActivity;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Checks a disposable session after UI login and an explicit app force-stop. */
@SuppressWarnings("deprecation")
public final class SessionVerificationInstrumentation extends Instrumentation {
    private boolean verifyAutomaticRefresh;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        verifyAutomaticRefresh = arguments != null && "true".equals(arguments.getString("t08VerifyRefresh"));
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (verifyAutomaticRefresh) {
                verifyAutomaticRefresh(getTargetContext());
                verifyInternalRoleGuards(getTargetContext());
                result.putString("stream", "PASS: protected request retried with persisted rotated credentials\n"
                        + "PASS: verified USER blocked by both internal role guards");
            } else {
                verifyStoredSession(getTargetContext());
                result.putString("stream", "PASS: encrypted token pair reopened and access token accepted");
            }
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError failure) {
            // Report only the check stage. Never forward framework exception messages.
            String reason = failure instanceof ProbeFailure ? failure.getMessage()
                    : failure.getClass().getSimpleName();
            result.putString("stream", "FAIL: stored session verification (" + reason + ")");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    // This mode changes only the current disposable USER session. The harness controls the first 401.
    private static void verifyAutomaticRefresh(Context context) throws Exception {
        require(BuildConfig.DEBUG && "http://127.0.0.1:8080/".equals(BuildConfig.API_BASE_URL));
        TokenStore store = TokenStore.open(context);
        Session stored = store.session();
        require(stored != null && "USER".equals(stored.role));
        ApiClient client = new ApiClient(BuildConfig.API_BASE_URL, store);
        if (client.sessions().refresh(stored) != Outcome.RESTORED) {
            throw new ProbeFailure("bootstrap refresh failed");
        }
        Session before = store.verifiedSession();
        Request request = new Request.Builder().url(BuildConfig.API_BASE_URL + "api/v1/me").build();
        boolean[] invoked = {false};
        boolean[] tagged = {false};
        OkHttpClient observed = client.http().newBuilder().authenticator((route, response) -> {
            invoked[0] = true;
            tagged[0] = response.request().tag(Session.class) != null;
            return client.sessions().authenticate(route, response);
        }).build();
        try (Response response = observed.newCall(request).execute()) {
            if (response.code() != 200 || response.priorResponse() == null
                    || response.priorResponse().code() != 401 || response.priorResponse().priorResponse() != null) {
                throw new ProbeFailure("HTTP " + response.code() + ", authenticator=" + invoked[0]
                        + ", session tag=" + tagged[0]);
            }
        }
        Session rotated = store.verifiedSession();
        require(before != null && before.sameAccount(rotated) && !before.access.equals(rotated.access)
                && !before.refresh.equals(rotated.refresh));
        verifyStoredSession(context);
    }

    private static final class ProbeFailure extends Exception {
        ProbeFailure(String reason) {
            super(reason);
        }
    }

    private void verifyInternalRoleGuards(Context context) throws Exception {
        Session session = TokenStore.open(context).verifiedSession();
        require(session != null && "USER".equals(session.role));
        ActivityMonitor login = addMonitor(LoginActivity.class.getName(), null, true);
        try {
            verifyInternalRedirect(context, AdminHomeActivity.class, login, 1);
            verifyInternalRedirect(context, StaffHomeActivity.class, login, 2);
        } finally {
            removeMonitor(login);
        }
    }

    private void verifyInternalRedirect(Context context, Class<? extends Activity> home,
            ActivityMonitor login, int expectedRedirects) throws ProbeFailure {
        Activity blocked = startActivitySync(new Intent(context, home)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("role", "ADMIN"));
        if (!blocked.isFinishing() || login.getHits() != expectedRedirects) {
            throw new ProbeFailure("internal guard failed for " + home.getSimpleName());
        }
    }

    private static void verifyStoredSession(Context context) throws Exception {
        String access = TokenStore.open(context).accessToken();
        require(access != null && !access.trim().isEmpty());

        MasterKey key = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        SharedPreferences encrypted = EncryptedSharedPreferences.create(context, "finora_session", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        String refresh = encrypted.getString("refresh_token", null);
        require(refresh != null && !refresh.trim().isEmpty());
        require(access.equals(encrypted.getString("access_token", null)));

        File preferences = new File(context.getApplicationInfo().dataDir,
                "shared_prefs/finora_session.xml");
        String raw = new String(Files.readAllBytes(preferences.toPath()), StandardCharsets.UTF_8);
        require(!raw.contains(access) && !raw.contains(refresh));
        require(!raw.contains("access_token") && !raw.contains("refresh_token"));

        HttpURLConnection connection = (HttpURLConnection) new URL(
                BuildConfig.API_BASE_URL + "api/v1/me").openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization", "Bearer " + access);
        try {
            require(connection.getResponseCode() == 200);
        } finally {
            connection.disconnect();
        }
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }
}
