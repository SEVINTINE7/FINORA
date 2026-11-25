package lk.finora.api.notification;

import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** FCM when FIREBASE_CREDENTIALS points to a service-account file, otherwise a no-op sender (D57). */
@Configuration
class PushConfig {

    @Bean
    PushSender pushSender(@Value("${finora.firebase.credentials:}") String credentials) throws IOException {
        if (credentials == null || credentials.isBlank()) {
            LoggerFactory.getLogger(PushConfig.class)
                    .info("FIREBASE_CREDENTIALS not set: alerts are recorded but not pushed");
            return new NoopPushSender();
        }
        return FcmPushSender.fromFile(credentials.trim());
    }
}
