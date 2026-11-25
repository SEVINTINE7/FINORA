package lk.finora.api.notification;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used while FIREBASE_CREDENTIALS is not set (D57): events are still recorded, nothing is pushed. */
class NoopPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(NoopPushSender.class);

    @Override
    public Result send(List<String> tokens, AlertType type) {
        log.debug("Push disabled (no FIREBASE_CREDENTIALS); {} alert not sent", type);
        return Result.notDelivered();
    }
}
