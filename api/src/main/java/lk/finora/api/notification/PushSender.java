package lk.finora.api.notification;

import java.util.List;

/** Sends one alert to a user's phones (T34.3). Implementations never put amounts or names in the push. */
public interface PushSender {

    Result send(List<String> tokens, AlertType type);

    /** delivered = at least one phone accepted it; invalidTokens = tokens FCM no longer knows. */
    record Result(boolean delivered, List<String> invalidTokens) {
        public static Result notDelivered() {
            return new Result(false, List.of());
        }
    }
}
