package lk.finora.app.ui.register;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import lk.finora.app.R;
import lk.finora.app.data.repo.RegisterRepository.Result;

public class RegisterActivityTest {
    @Test public void eachFailureHasItsOwnMessage() {
        assertEquals(R.string.register_email_taken, RegisterActivity.message(Result.EMAIL_TAKEN));
        assertEquals(R.string.register_under_18, RegisterActivity.message(Result.UNDER_18));
        assertEquals(R.string.invalid_input, RegisterActivity.message(Result.INVALID_INPUT));
        assertEquals(R.string.network_error, RegisterActivity.message(Result.NETWORK_ERROR));
        assertEquals(R.string.server_error, RegisterActivity.message(Result.SERVER_ERROR));
    }
}
