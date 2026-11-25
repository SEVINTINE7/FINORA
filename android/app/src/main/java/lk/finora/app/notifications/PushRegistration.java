package lk.finora.app.notifications;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import lk.finora.app.BuildConfig;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.AlertRepository;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * T34.5: sends this phone's FCM token to the API after a USER signs in. Firebase is configured from
 * BuildConfig (gradle properties, D57); without it push stays off and nothing here runs.
 * Push is optional: every failure is ignored and the app keeps working.
 */
public final class PushRegistration {
    private static final Callback IGNORE = new Callback() {
        @Override public void onFailure(Call call, IOException failure) {}
        @Override public void onResponse(Call call, Response response) { response.close(); }
    };

    private PushRegistration() {}

    public static boolean configured() {
        return !BuildConfig.FCM_APP_ID.isEmpty() && !BuildConfig.FCM_API_KEY.isEmpty()
                && !BuildConfig.FCM_PROJECT_ID.isEmpty() && !BuildConfig.FCM_SENDER_ID.isEmpty();
    }

    public static void register(Context context) {
        if (!configured() || ProtectedRequests.currentUser() == null) return;
        try {
            messaging(context).getToken().addOnSuccessListener(PushRegistration::send);
        } catch (RuntimeException unavailable) {
            // No Google Play services or bad config: alerts stay off, the app is unaffected
        }
    }

    /** After sign-out: the old token stops working, so a later push cannot reach this phone. */
    public static void forget() {
        if (!configured()) return;
        try {
            FirebaseApp.getInstance();
            FirebaseMessaging.getInstance().deleteToken();
        } catch (RuntimeException neverStarted) {
            // Firebase never started on this run, so this phone has no token to remove
        }
    }

    static void send(String token) {
        ProtectedRequests requests = ProtectedRequests.currentUser();
        if (requests == null || token == null || token.isEmpty()) return;
        try {
            new AlertRepository(requests).registerDevice(token).enqueue(IGNORE);
        } catch (IllegalStateException signedOut) {
            // The login ended while the token was being fetched
        }
    }

    private static FirebaseMessaging messaging(Context context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context.getApplicationContext(), new FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FCM_APP_ID)
                    .setApiKey(BuildConfig.FCM_API_KEY)
                    .setProjectId(BuildConfig.FCM_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                    .build());
        }
        FirebaseMessaging messaging = FirebaseMessaging.getInstance();
        messaging.setAutoInitEnabled(true);
        return messaging;
    }
}
