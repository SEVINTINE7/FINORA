package lk.finora.app.acceptance;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.main.MainActivity;
import lk.finora.app.ui.transactions.TransactionFormActivity;

/**
 * T62.6 review screenshots. FLAG_SECURE blocks adb screencap by design, so each screen is drawn in-process
 * into a bitmap instead; the window flag stays on. Needs a local dev API (adb reverse tcp:8080 tcp:8080)
 * with a synthetic account passed as finoraShotEmail / finoraShotPassword; it never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T62ScreenshotTest {
    @Test public void captureRedesignedScreens() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T62 screenshot run only", "true".equals(args.getString("finoraT62Shots")));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        var tokens = TokenStore.open(app);
        assertNull("Preserve the existing session", tokens.session());
        JSONObject login = login(args.getString("finoraShotEmail"), args.getString("finoraShotPassword"));
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            setLocales(app, language);
        });
        tokens.save(login.getString("accessToken"), login.getString("refreshToken"), "USER",
                language.toUpperCase(Locale.ROOT));
        File out = app.getExternalFilesDir("t62");
        assertNotNull(out);
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            settle();
            scenario.onActivity(activity -> capture(activity, new File(out, tag + "-home-1.png")));
            scenario.onActivity(activity -> {
                ScrollView scroll = activity.findViewById(R.id.dashboard_scroll);
                scroll.scrollTo(0, scroll.getChildAt(0).getHeight() / 2 - scroll.getHeight() / 3);
            });
            settle();
            scenario.onActivity(activity -> capture(activity, new File(out, tag + "-home-2.png")));
            scenario.onActivity(activity -> {
                ScrollView scroll = activity.findViewById(R.id.dashboard_scroll);
                scroll.fullScroll(View.FOCUS_DOWN);
            });
            settle();
            scenario.onActivity(activity -> capture(activity, new File(out, tag + "-home-3.png")));
            scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.shell_navigation))
                    .setSelectedItemId(R.string.records_tab));
            settle();
            scenario.onActivity(activity -> capture(activity, new File(out, tag + "-records.png")));
        }
        try (var form = ActivityScenario.launch(TransactionFormActivity.class)) {
            settle();
            form.onActivity(activity -> capture(activity, new File(out, tag + "-form.png")));
        } finally {
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    static void setLocales(android.content.Context app, String tags) {
        var manager = app.getSystemService(android.app.LocaleManager.class);
        if (android.os.Build.VERSION.SDK_INT >= 33 && manager != null) {
            manager.setApplicationLocales(android.os.LocaleList.forLanguageTags(tags));
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags));
        }
    }

    static void settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(3_000);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    static void capture(Activity activity, File file) {
        View root = activity.getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        try (OutputStream stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    static JSONObject login(String email, String password) throws Exception {
        assertNotNull("finoraShotEmail required", email);
        var connection = (HttpURLConnection) new URL("http://127.0.0.1:8080/api/v1/auth/login").openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        var body = new JSONObject().put("email", email).put("password", password).toString();
        try (var stream = connection.getOutputStream()) {
            stream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        assertTrue("Login failed: " + connection.getResponseCode(), connection.getResponseCode() == 200);
        try (var stream = connection.getInputStream()) {
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
