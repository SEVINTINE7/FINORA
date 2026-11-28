package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import okhttp3.HttpUrl;

/** T68.2/D72: the profile calls, including the endpoints planned in T74 (docs/api-contract.md). */
public class MeRepositoryTest {
    private static final HttpUrl ORIGIN = HttpUrl.get("http://127.0.0.1:8080/");

    @Test public void profileAndPlannedEndpointPaths() {
        assertEquals("/api/v1/me", MeRepository.meUrl(ORIGIN).encodedPath());
        assertEquals("/api/v1/me/password", MeRepository.passwordUrl(ORIGIN).encodedPath());
        assertEquals("/api/v1/me/email", MeRepository.emailUrl(ORIGIN).encodedPath());
        assertEquals("/api/v1/me/email/confirm", MeRepository.emailConfirmUrl(ORIGIN).encodedPath());
    }

    @Test public void initialsComeFromTheNameOrElseTheEmail() {
        assertEquals("NP", new MeRepository.Details("Nimal Perera", "n@example.com", "EN").initials());
        assertEquals("NK", new MeRepository.Details("  nimal   kumara silva ", null, "EN").initials());
        assertEquals("S", new MeRepository.Details("Sahan", null, "SI").initials());
        assertEquals("N", new MeRepository.Details(" ", "nimal@example.com", "EN").initials());
        assertEquals("", new MeRepository.Details(null, null, null).initials());
    }
}
