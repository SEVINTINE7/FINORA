package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.main.MainActivity;
import lk.finora.app.ui.settings.ChangePasswordActivity;
import lk.finora.app.ui.settings.EditProfileActivity;
import lk.finora.app.ui.settings.SettingsActivity;
import lk.finora.app.ui.help.FaqActivity;
import lk.finora.app.ui.support.SupportActivity;
import lk.finora.app.ui.support.SupportNewActivity;

/**
 * T68 review run against a local dev API. Registers a disposable synthetic user (or signs in finoraShotEmail),
 * then captures the More tab, Settings (full scroll), Edit profile, Change password, and the email-change answer
 * while the T74 endpoint is missing (404 → "not available yet"). Changes no saved data.
 * Never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T68ScreenshotTest {
    @Test public void moreSettingsProfileAndPassword() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T68 run only", "true".equals(args.getString("finoraT68Shots")));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        var tokens = TokenStore.open(app);
        assertNull("Preserve the existing session", tokens.session());
        String email = args.getString("finoraShotEmail");
        String password = args.getString("finoraShotPassword");
        if (email == null) {
            email = "t68-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
            password = "Synthetic9-" + UUID.randomUUID().toString().substring(0, 8);
            register(email, password, "Nimal Perera", language.toUpperCase(Locale.ROOT));
        }
        var login = T62ScreenshotTest.login(email, password);
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        tokens.save(login.getString("accessToken"), login.getString("refreshToken"), "USER",
                language.toUpperCase(Locale.ROOT));
        File out = app.getExternalFilesDir("t68");
        assertNotNull(out);
        try {
            more(out, tag);
            settings(out, tag);
            profile(out, tag);
            password(out, tag);
            help(out, tag);
            support(out, tag);
        } finally {
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    private void more(File out, String tag) {
        try (var screen = ActivityScenario.launch(MainActivity.class)) {
            await(screen, a -> a.findViewById(R.id.shell_navigation) != null, "shell shown");
            onView(withId(R.string.more_tab)).perform(click());
            await(screen, a -> a.findViewById(R.id.profile_email).getVisibility() == View.VISIBLE, "profile loaded");
            screen.onActivity(a -> {
                assertTrue(((TextView) a.findViewById(R.id.profile_email)).getText().toString().contains("@"));
                T62ScreenshotTest.capture(a, new File(out, "t68-more-" + tag + ".png"));
                View panel = a.findViewById(R.id.more_panel);
                drawWhole((View) panel.getParent(), new File(out, "t68-more-full-" + tag + ".png"));
            });
        }
    }

    private void settings(File out, String tag) {
        try (var screen = ActivityScenario.launch(SettingsActivity.class)) {
            await(screen, a -> a.findViewById(R.id.settings_alert_bill).isEnabled()
                    && a.findViewById(R.id.profile_email).getVisibility() == View.VISIBLE, "settings loaded");
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, "t68-settings-" + tag + ".png"));
                drawWhole(a.findViewById(R.id.settings_content), new File(out, "t68-settings-full-" + tag + ".png"));
            });
        }
    }

    private void profile(File out, String tag) {
        try (var screen = ActivityScenario.launch(EditProfileActivity.class)) {
            await(screen, a -> ((EditText) a.findViewById(R.id.profile_email_input)).length() > 0, "profile filled");
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t68-profile-" + tag + ".png")));
            // A changed email shows the password step; before T74 the API answers 404 → "not available yet".
            screen.onActivity(a -> {
                ((EditText) a.findViewById(R.id.profile_email_input)).setText("t68-new@example.test");
                ((EditText) a.findViewById(R.id.profile_password_input)).setText("not-sent-before-t74");
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.profile_password_group).getVisibility());
                T62ScreenshotTest.capture(a, new File(out, "t68-profile-email-" + tag + ".png"));
                a.findViewById(R.id.records_save).performClick();
            });
            await(screen, a -> a.findViewById(R.id.records_status).getVisibility() == View.VISIBLE
                    && !a.getString(R.string.records_loading).contentEquals(
                            ((TextView) a.findViewById(R.id.records_status)).getText()), "email answer");
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t68-profile-answer-" + tag + ".png")));
        }
    }

    private void password(File out, String tag) {
        try (var screen = ActivityScenario.launch(ChangePasswordActivity.class)) {
            await(screen, a -> a.findViewById(R.id.password_current_input) != null, "password shown");
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, "t68-password-" + tag + ".png"));
                ((EditText) a.findViewById(R.id.password_current_input)).setText("Current9pass");
                ((EditText) a.findViewById(R.id.password_new_input)).setText("Newer9pass");
                ((EditText) a.findViewById(R.id.password_repeat_input)).setText("Newer9pasz");
                a.findViewById(R.id.records_save).performClick();
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t68-password-error-" + tag + ".png")));
        }
    }

    private void help(File out, String tag) {
        try (var screen = ActivityScenario.launch(FaqActivity.class)) {
            await(screen, a -> a.findViewById(R.id.faq_open_support) != null, "help shown");
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, "t68-help-" + tag + ".png"));
                drawWhole(a.findViewById(R.id.faq_page), new File(out, "t68-help-full-" + tag + ".png"));
            });
        }
    }

    /** Sends one standard Sign-in request for the disposable user, then shows the list and its thread. */
    private void support(File out, String tag) {
        try (var screen = ActivityScenario.launch(SupportActivity.class)) {
            await(screen, a -> a.findViewById(R.id.support_empty).getVisibility() == View.VISIBLE
                    || ((android.view.ViewGroup) a.findViewById(R.id.support_list)).getChildCount() > 0, "support list");
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t68-support-empty-" + tag + ".png")));
        }
        var create = ActivityScenario.launch(SupportNewActivity.class);
        await(create, a -> ((android.view.ViewGroup) a.findViewById(R.id.support_topics)).getChildCount() == 4, "topics");
        create.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t68-support-new-" + tag + ".png")));
        create.onActivity(a -> ((android.view.ViewGroup) a.findViewById(R.id.support_topics)).getChildAt(0).performClick());
        T62ScreenshotTest.settle();
        create.onActivity(a -> {
            assertTrue(a.findViewById(R.id.support_send).isEnabled());
            T62ScreenshotTest.capture(a, new File(out, "t68-support-new-chosen-" + tag + ".png"));
            a.findViewById(R.id.support_send).performClick();
        });
        long end = SystemClock.uptimeMillis() + 60_000;
        while (create.getState() != androidx.lifecycle.Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < end) {
            SystemClock.sleep(300);
        }
        assertEquals("request sent", androidx.lifecycle.Lifecycle.State.DESTROYED, create.getState());
        try (var screen = ActivityScenario.launch(SupportActivity.class)) {
            await(screen, a -> ((android.view.ViewGroup) a.findViewById(R.id.support_list)).getChildCount() > 0, "card");
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, "t68-support-list-" + tag + ".png"));
                ((android.view.ViewGroup) a.findViewById(R.id.support_list)).getChildAt(0).performClick();
            });
            await(screen, a -> a.findViewById(R.id.support_thread_group).getVisibility() == View.VISIBLE
                    && a.findViewById(R.id.support_status).getVisibility() == View.GONE, "thread");
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t68-support-thread-" + tag + ".png")));
        }
    }

    private static <A extends Activity> void await(ActivityScenario<A> scenario, Predicate<A> ready, String name) {
        long end = SystemClock.uptimeMillis() + 60_000;
        var done = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < end) {
            scenario.onActivity(activity -> done.set(ready.test(activity)));
            if (done.get()) break;
            SystemClock.sleep(500);
        }
        assertTrue(name, done.get());
        T62ScreenshotTest.settle();
    }

    /** Draws a scrolling page in full height on its window background. */
    private static void drawWhole(View content, File file) {
        View page = content instanceof ScrollView scroll ? scroll.getChildAt(0) : content;
        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
        var canvas = new Canvas(bitmap);
        canvas.drawColor(page.getContext().getColor(R.color.finora_surface));
        page.draw(canvas);
        try (var stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void register(String email, String password, String name, String language) throws Exception {
        var connection = (HttpURLConnection) new URL("http://127.0.0.1:8080/api/v1/auth/register").openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        var body = new JSONObject().put("email", email).put("password", password).put("fullName", name)
                .put("language", language).put("over18", true).put("consentVersion", "1.0").toString();
        try (var stream = connection.getOutputStream()) {
            stream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals("Register failed", 201, connection.getResponseCode());
    }
}
