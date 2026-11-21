package lk.finora.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

// T37 specifies EncryptedSharedPreferences. Keep this deprecated API behind the store.
@SuppressWarnings("deprecation")
public final class TokenStore {
    private static final String ACCESS = "access_token";
    private static final String REFRESH = "refresh_token";
    private final SharedPreferences preferences;

    public TokenStore(Context context) throws GeneralSecurityException, IOException {
        MasterKey key = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        preferences = EncryptedSharedPreferences.create(context, "finora_session", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    public String accessToken() {
        return preferences.getString(ACCESS, null);
    }

    public void save(String accessToken, String refreshToken) throws IOException {
        if (!preferences.edit().putString(ACCESS, accessToken)
                .putString(REFRESH, refreshToken).commit()) {
            throw new IOException("Could not persist session");
        }
    }
}
