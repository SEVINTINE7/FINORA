package lk.finora.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import lk.finora.app.data.local.TokenStore.Session;

public class LocaleHelperTest {
    @Test
    public void confirmedLocaleSurvivesStaleSessionMetadataOnlyForSameLogin() throws Exception {
        LocaleHelper.ConfirmedLanguage cache = new LocaleHelper.ConfirmedLanguage();
        Object login = new Object();
        Session stale = session(login, "EN");
        cache.record(stale.loginIdentity(), "SI");
        assertEquals("SI", cache.forLogin(stale.loginIdentity(), stale.language));
        Session other = session(new Object(), "EN");
        assertEquals("EN", cache.forLogin(other.loginIdentity(), other.language));
        assertEquals("EN", cache.forLogin(stale.loginIdentity(), stale.language));
    }

    @Test
    public void rotatedSnapshotNeedsOnlyTheOpaqueLoginKeyToRecoverConfirmedLocale() throws Exception {
        LocaleHelper.ConfirmedLanguage cache = new LocaleHelper.ConfirmedLanguage();
        Object login = new Object();
        Session rotated = session(login, "EN");
        assertSame(login, rotated.loginIdentity());
        assertNotSame(rotated, rotated.loginIdentity());
        // Only the key from the original login is available. No old credential snapshot is needed.
        cache.record(login, "SI");
        assertEquals("SI", cache.forLogin(rotated.loginIdentity(), rotated.language));
    }

    private static Session session(Object identity, String language) throws Exception {
        Constructor<Session> constructor = Session.class.getDeclaredConstructor(Object.class,
                String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(identity, "access", "refresh", "USER", language);
    }

    @Test
    public void updateReadsProfileFirstAndPreservesTheFullName() throws IOException {
        List<LocaleHelper.LanguageUpdate> calls = new ArrayList<>();
        String language = LocaleHelper.synchronize("SI", update -> {
            calls.add(update);
            return profile("Test Name", update == null ? "EN" : "SI");
        });
        assertEquals("SI", language);
        assertEquals(2, calls.size());
        assertNull(calls.get(0));
        assertEquals("Test Name", calls.get(1).fullName);
        assertEquals("SI", calls.get(1).language);
    }

    @Test
    public void readingOrSelectingCurrentLanguageDoesNotUpdateProfile() throws IOException {
        for (String requested : new String[]{null, "SI"}) {
            assertEquals("SI", LocaleHelper.synchronize(requested, update -> {
                assertNull(update);
                return profile("Test Name", "SI");
            }));
        }
    }

    @Test
    public void rejectedOrUnconfirmedUpdateCannotPublishSelectedLanguage() {
        assertThrows(IOException.class, () -> LocaleHelper.synchronize("SI", update -> {
            if (update != null) {
                throw new IOException("offline");
            }
            return profile("Test Name", "EN");
        }));
        assertThrows(IOException.class, () -> LocaleHelper.synchronize("SI",
                update -> profile("Test Name", "EN")));
    }

    private static LocaleHelper.Profile profile(String name, String language) {
        LocaleHelper.Profile profile = new LocaleHelper.Profile();
        profile.fullName = name;
        profile.language = language;
        return profile;
    }
}
