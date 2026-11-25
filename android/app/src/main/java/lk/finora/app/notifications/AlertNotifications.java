package lk.finora.app.notifications;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.ui.login.LoginActivity;

/**
 * T34.5 alert notifications. The push from the server carries only the alert type; the text is a fixed
 * string per type, so the lock screen never shows amounts, names or merchants (T34.3).
 */
public final class AlertNotifications {
    public static final String CHANNEL = "finora_alerts";

    private AlertNotifications() {}

    /** The message for a server alert type, or 0 for an unknown type (ignored). */
    public static int message(String type) {
        if (type == null) return 0;
        switch (type) {
            case "BUDGET_NEAR": return R.string.alert_budget_near;
            case "BUDGET_OVER": return R.string.alert_budget_over;
            case "BILL_DUE": return R.string.alert_bill_due;
            case "GOAL_DATE_NEAR": return R.string.alert_goal_near;
            default: return 0;
        }
    }

    /** Safe to call often; Android keeps the user's channel settings. */
    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.alert_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** Android 13+ needs the runtime permission; older versions only need notifications switched on. */
    public static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return context.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    /** Shows one alert, only while a USER is signed in on this phone and notifications are allowed. */
    static void show(Context context, String type) {
        int message = message(type);
        if (message == 0 || ProtectedRequests.currentUser() == null || !allowed(context)) return;
        Context localized = inAppLanguage(context);
        createChannel(localized);
        Intent open = new Intent(context, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(localized, CHANNEL)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(localized.getString(R.string.app_name))
                .setContentText(localized.getString(message))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(type.hashCode(), notification);
    }

    private static Context inAppLanguage(Context context) {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (locales.isEmpty() || locales.get(0) == null) return context;
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(locales.get(0));
        return context.createConfigurationContext(configuration);
    }
}
