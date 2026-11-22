package lk.finora.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class LocaleHelperTest {
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
