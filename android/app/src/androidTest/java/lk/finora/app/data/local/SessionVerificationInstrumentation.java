package lk.finora.app.data.local;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
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

/** Read-only check after UI login with a disposable account and an explicit app force-stop. */
@SuppressWarnings("deprecation")
public final class SessionVerificationInstrumentation extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            verifyStoredSession(getTargetContext());
            result.putString("stream", "PASS: encrypted token pair reopened and access token accepted");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError failure) {
            // Report only the check stage. Never forward framework exception messages.
            result.putString("stream", "FAIL: stored session verification (" +
                    failure.getClass().getSimpleName() + ")");
            finish(Activity.RESULT_CANCELED, result);
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
