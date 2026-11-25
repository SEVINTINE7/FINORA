package lk.finora.app.notifications;

import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** T34.5: receives the data-only alert push ({type}) and FCM token changes. */
public final class FinoraMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        AlertNotifications.show(this, message.getData().get("type"));
    }

    @Override
    public void onNewToken(@NonNull String token) {
        PushRegistration.send(token);
    }
}
