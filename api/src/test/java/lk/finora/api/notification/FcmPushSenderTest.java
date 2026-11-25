package lk.finora.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T34.3: the FCM payload carries only the alert type, and rejected tokens are reported for clean-up. */
class FcmPushSenderTest {

    @Test
    @SuppressWarnings("unchecked")
    void messageIsDataOnlyWithJustTheType() throws Exception {
        Message message = FcmPushSender.message("synthetic-token", AlertType.BUDGET_OVER);

        Field data = Message.class.getDeclaredField("data");
        data.setAccessible(true);
        assertThat((Map<String, String>) data.get(message)).containsExactly(Map.entry("type", "BUDGET_OVER"));
        Field notification = Message.class.getDeclaredField("notification");
        notification.setAccessible(true);
        assertThat(notification.get(message)).isNull();
    }

    @Test
    void reportsDeliveryAndUnregisteredTokens() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        SendResponse ok = mock(SendResponse.class);
        when(ok.isSuccessful()).thenReturn(true);
        SendResponse gone = mock(SendResponse.class);
        FirebaseMessagingException unregistered = mock(FirebaseMessagingException.class);
        when(unregistered.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(gone.getException()).thenReturn(unregistered);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(ok, gone));
        when(batch.getSuccessCount()).thenReturn(1);
        when(messaging.sendEach(anyList())).thenReturn(batch);

        PushSender.Result result = new FcmPushSender(messaging).send(List.of("good", "stale"), AlertType.BILL_DUE);

        assertThat(result.delivered()).isTrue();
        assertThat(result.invalidTokens()).containsExactly("stale");
    }

    @Test
    void transientFailureIsNotDeliveredAndKeepsTokens() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        FirebaseMessagingException down = mock(FirebaseMessagingException.class);
        when(down.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(messaging.sendEach(anyList())).thenThrow(down);

        PushSender.Result result = new FcmPushSender(messaging).send(List.of("good"), AlertType.BILL_DUE);

        assertThat(result.delivered()).isFalse();
        assertThat(result.invalidTokens()).isEmpty();
    }

    @Test
    void noTokensMeansNoCall() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        assertThat(new FcmPushSender(messaging).send(List.of(), AlertType.GOAL_DATE_NEAR).delivered()).isFalse();
        verify(messaging, never()).sendEach(anyList());
    }

    @Test
    void noopSenderNeverDelivers() {
        assertThat(new NoopPushSender().send(List.of("t"), AlertType.BUDGET_NEAR).delivered()).isFalse();
    }
}
