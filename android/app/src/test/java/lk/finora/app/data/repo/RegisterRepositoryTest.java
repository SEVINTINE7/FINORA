package lk.finora.app.data.repo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import lk.finora.app.data.repo.RegisterRepository.Result;
import okhttp3.HttpUrl;

public class RegisterRepositoryTest {
    @Test public void mapsApiErrorCodes() {
        assertEquals(Result.EMAIL_TAKEN, RegisterRepository.fromCode("EMAIL_TAKEN"));
        assertEquals(Result.UNDER_18, RegisterRepository.fromCode("UNDER_18"));
        assertEquals(Result.INVALID_INPUT, RegisterRepository.fromCode("VALIDATION_ERROR"));
        assertEquals(Result.SERVER_ERROR, RegisterRepository.fromCode("INTERNAL_ERROR"));
        assertEquals(Result.SERVER_ERROR, RegisterRepository.fromCode(null));
    }

    @Test public void consentVersionMatchesD12() {
        assertEquals("1.0", RegisterRepository.CONSENT_VERSION);
    }

    @Test public void withdrawGoesToTheMeEndpoint() {
        HttpUrl url = MeRepository.withdrawUrl(HttpUrl.get("http://127.0.0.1:8080/"));
        assertEquals("/api/v1/me/consent/withdraw", url.encodedPath());
    }
}
