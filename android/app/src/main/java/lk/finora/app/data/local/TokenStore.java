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
    private static TokenStore instance;

    private final SharedPreferences preferences;
    private volatile Session published;

    public static synchronized TokenStore open(Context context)
            throws GeneralSecurityException, IOException {
        if (instance == null) {
            Context app = context.getApplicationContext();
            MasterKey key = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            instance = new TokenStore(EncryptedSharedPreferences.create(app, "finora_session", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM));
        }
        return instance;
    }

    TokenStore(SharedPreferences preferences) {
        this.preferences = preferences;
        String access = preferences.getString(ACCESS, null);
        String refresh = preferences.getString(REFRESH, null);
        if (access != null && !access.trim().isEmpty()
                && refresh != null && !refresh.trim().isEmpty()) {
            published = new Session(access, refresh);
        }
    }

    public String accessToken() {
        Session session = published;
        return session == null ? null : session.access;
    }

    public synchronized void save(String accessToken, String refreshToken) throws IOException {
        Session replacement = new Session(accessToken, refreshToken);
        try {
            if (!commit(replacement)) {
                throw new IOException("Could not persist session");
            }
        } catch (IOException | RuntimeException failure) {
            // A failed commit can still change preferences in memory. Keep serving only
            // the previously published pair and attempt to restore the encrypted file.
            try {
                if (!commit(published)) {
                    failure.addSuppressed(new IOException("Could not restore previous session"));
                }
            } catch (RuntimeException restorationFailure) {
                if (restorationFailure != failure) {
                    failure.addSuppressed(restorationFailure);
                }
            }
            throw failure;
        }
        published = replacement;
    }

    private boolean commit(Session session) {
        SharedPreferences.Editor editor = preferences.edit();
        if (session == null) {
            editor.remove(ACCESS).remove(REFRESH);
        } else {
            editor.putString(ACCESS, session.access).putString(REFRESH, session.refresh);
        }
        return editor.commit();
    }

    private static final class Session {
        final String access;
        final String refresh;

        Session(String access, String refresh) {
            this.access = access;
            this.refresh = refresh;
        }
    }
}
