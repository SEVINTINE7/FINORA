package lk.finora.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.CopyOnWriteArrayList;

// T37 specifies EncryptedSharedPreferences. Keep this deprecated API behind the store.
@SuppressWarnings("deprecation")
public final class TokenStore {
    private static final String ACCESS = "access_token";
    private static final String REFRESH = "refresh_token";
    private static final String ROLE = "role";
    private static final String LANGUAGE = "language";
    private static final String BLOCKED = "session_blocked";
    private static volatile TokenStore instance;

    private final SharedPreferences preferences;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
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
        if (preferences.getString(BLOCKED, null) == null && access != null && !access.trim().isEmpty()
                && refresh != null && !refresh.trim().isEmpty()) {
            published = new Session(new Object(), access, refresh, preferences.getString(ROLE, null),
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
        persist(new Session(new Object(), accessToken, refreshToken, role, language));
    }

    public synchronized Session replace(Session expected, String access, String refresh,
                                        String role, String language) throws IOException {
        if (published != expected || expected == null) {
            return null;
        }
        Session replacement = new Session(expected.identity, access, refresh, role, language);
        persist(replacement);
        return replacement;
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
        notifySessionChanged();
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

    public void addSessionListener(Runnable listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeSessionListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifySessionChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    public synchronized boolean clear(Session expected) throws IOException {
        if (published != expected || expected == null) {
            return false;
        }
        verified = null;
        published = null;
        notifySessionChanged();
        try {
            if (!commit(null)) {
                throw new IOException("Could not clear persisted session");
            }
        } catch (IOException | RuntimeException failure) {
            // If deletion fails, separately try to make the old record unusable on restart.
            try {
                if (!preferences.edit().putString(BLOCKED, "1").commit()) {
                    failure.addSuppressed(new IOException("Could not block persisted session"));
                }
            } catch (RuntimeException markerFailure) {
                if (markerFailure != failure) {
                    failure.addSuppressed(markerFailure);
                }
            }
            throw failure;
        }
        return true;
    }

    public synchronized LogoutResult clearAccount(Session expected) {
        Session current = published;
        if (expected == null || !expected.sameAccount(current)) {
            return new LogoutResult(null, null);
        }
        try {
            clear(current);
            return new LogoutResult(current, null);
        } catch (IOException | RuntimeException failure) {
            return new LogoutResult(current, failure);
        }
    }

    public static final class LogoutResult {
        public final Session invalidated;
        public final Exception storageFailure;

        private LogoutResult(Session invalidated, Exception storageFailure) {
            this.invalidated = invalidated;
            this.storageFailure = storageFailure;
        }
    }

    private boolean commit(Session session) {
        SharedPreferences.Editor editor = preferences.edit().remove(BLOCKED);
        if (session == null) {
            editor.remove(ACCESS).remove(REFRESH).remove(ROLE).remove(LANGUAGE);
        } else {
            editor.putString(ACCESS, session.access).putString(REFRESH, session.refresh)
                    .putString(ROLE, session.role).putString(LANGUAGE, session.language);
        }
        return editor.commit();
    }

    public static final class Session {
        private final Object identity;
        public final String access;
        public final String refresh;
        public final String role;
        public final String language;

        private Session(Object identity, String access, String refresh, String role, String language) {
            this.identity = identity;
            this.access = access;
            this.refresh = refresh;
            this.role = role;
            this.language = language;
        }

        public boolean sameAccount(Session other) {
            return other != null && identity == other.identity;
        }

        public boolean supported() {
            return isSupportedRole(role) && ("EN".equals(language) || "SI".equals(language));
        }

        public static boolean isSupportedRole(String role) {
            return "USER".equals(role) || "ADMIN".equals(role) || "STAFF".equals(role);
        }
    }
}
