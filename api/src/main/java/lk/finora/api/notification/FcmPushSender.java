package lk.finora.api.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Firebase Cloud Messaging via the Admin SDK (T34.3). Sends a data-only message holding just the alert
 * type; the app turns it into a fixed, amount-free notification in the user's language.
 */
class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String APP_NAME = "finora";

    private final FirebaseMessaging messaging;

    FcmPushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    /** Reads the service-account JSON from the path in FIREBASE_CREDENTIALS. */
    static FcmPushSender fromFile(String path) throws IOException {
        FirebaseApp app = null;
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (APP_NAME.equals(existing.getName())) {
                app = existing;
            }
        }
        if (app == null) {
            try (InputStream in = new FileInputStream(path)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in)).build();
                app = FirebaseApp.initializeApp(options, APP_NAME);
            }
        }
        return new FcmPushSender(FirebaseMessaging.getInstance(app));
    }

    @Override
    public Result send(List<String> tokens, AlertType type) {
        if (tokens.isEmpty()) {
            return Result.notDelivered();
        }
        List<Message> messages = new ArrayList<>();
        for (String token : tokens) {
            messages.add(message(token, type));
        }
        try {
            BatchResponse batch = messaging.sendEach(messages);
            List<String> invalid = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                var response = batch.getResponses().get(i);
                if (!response.isSuccessful() && isInvalidToken(response.getException())) {
                    invalid.add(tokens.get(i));
                }
            }
            return new Result(batch.getSuccessCount() > 0, invalid);
        } catch (FirebaseMessagingException failure) {
            // Never log tokens; the event stays unsent and is retried
            log.warn("FCM send failed: {}", failure.getMessagingErrorCode());
            return Result.notDelivered();
        }
    }

    /** Data-only: just the type. No notification block, so no text, amounts or names leave the server. */
    static Message message(String token, AlertType type) {
        return Message.builder().setToken(token).putData("type", type.name())
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(24L * 60 * 60 * 1000).build())
                .build();
    }

    private static boolean isInvalidToken(FirebaseMessagingException failure) {
        if (failure == null) {
            return false;
        }
        MessagingErrorCode code = failure.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH;
    }
}
