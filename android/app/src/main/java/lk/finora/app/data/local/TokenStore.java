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
    private static final String ROLE = "role";
    private static final String LANGUAGE = "language";
    private static volatile TokenStore instance;

    private final SharedPreferences preferences;
    private volatile Session published;
    private volatile Session verified;

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
            published = new Session(access, refresh, preferences.getString(ROLE, null),
                    preferences.getString(LANGUAGE, null));
        }
    }

    public String accessToken() {
        Session session = published;
        return session == null ? null : session.access;
    }

    public synchronized void save(String accessToken, String refreshToken) throws IOException {
        save(accessToken, refreshToken, null, null);
    }

    public synchronized void save(String accessToken, String refreshToken,
                                  String role, String language) throws IOException {
        persist(new Session(accessToken, refreshToken, role, language));
    }

    private void persist(Session replacement) throws IOException {
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
        verified = replacement != null && replacement.supported() ? replacement : null;
    }

    public static TokenStore current() {
        return instance;
    }

    public Session session() {
        return published;
    }

    public Session verifiedSession() {
        Session session = verified;
        return session == published ? session : null;
    }

    public synchronized boolean clear(Session expected) throws IOException {
        if (published != expected || expected == null) {
            return false;
        }
        verified = null;
        published = null;
        if (!commit(null)) {
            throw new IOException("Could not clear persisted session");
        }
        return true;
    }

    private boolean commit(Session session) {
        SharedPreferences.Editor editor = preferences.edit();
        if (session == null) {
            editor.remove(ACCESS).remove(REFRESH).remove(ROLE).remove(LANGUAGE);
        } else {
            editor.putString(ACCESS, session.access).putString(REFRESH, session.refresh)
                    .putString(ROLE, session.role).putString(LANGUAGE, session.language);
        }
        return editor.commit();
    }

    public static final class Session {
        public final String access;
        public final String refresh;
        public final String role;
        public final String language;

        private Session(String access, String refresh, String role, String language) {
            this.access = access;
            this.refresh = refresh;
            this.role = role;
            this.language = language;
        }

        public boolean supported() {
            return isSupportedRole(role) && ("EN".equals(language) || "SI".equals(language));
        }

        public static boolean isSupportedRole(String role) {
            return "USER".equals(role) || "ADMIN".equals(role) || "STAFF".equals(role);
        }
    }
}
