package lk.finora.app.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import lk.finora.app.R;
import lk.finora.app.data.repo.AlertRepository;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

/** T34.5: each server alert type maps to one fixed, amount-free text; /me alert flags are read correctly. */
public class AlertNotificationsTest {

    @Test
    public void everyServerTypeHasItsOwnFixedMessage() {
        assertEquals(R.string.alert_budget_near, AlertNotifications.message("BUDGET_NEAR"));
        assertEquals(R.string.alert_budget_over, AlertNotifications.message("BUDGET_OVER"));
        assertEquals(R.string.alert_bill_due, AlertNotifications.message("BILL_DUE"));
        assertEquals(R.string.alert_goal_near, AlertNotifications.message("GOAL_DATE_NEAR"));
    }

    @Test
    public void unknownOrMissingTypesShowNothing() {
        assertEquals(0, AlertNotifications.message(null));
        assertEquals(0, AlertNotifications.message(""));
        assertEquals(0, AlertNotifications.message("LKR 5000.00"));
    }

    @Test
    public void readsAlertFlagsFromMe() throws IOException {
        AlertRepository.Settings settings = AlertRepository.settings(response(200,
                "{\"email\":\"a@example.test\",\"alerts\":{\"budget\":false,\"bill\":true,\"goal\":false}}"));
        assertFalse(settings.budget);
        assertTrue(settings.bill);
        assertFalse(settings.goal);
    }

    @Test
    public void missingFlagsOrErrorsAreFailures() {
        assertThrows(IOException.class, () -> AlertRepository.settings(response(200, "{\"email\":\"a@example.test\"}")));
        assertThrows(IOException.class, () -> AlertRepository.settings(response(400,
                "{\"code\":\"VALIDATION_ERROR\"}")));
    }

    private static Response response(int code, String json) {
        return new Response.Builder()
                .request(new Request.Builder().url("http://127.0.0.1:8080/api/v1/me").build())
                .protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(ResponseBody.create(json, MediaType.get("application/json")))
                .build();
    }
}
