package lk.finora.app.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.repo.StaffStatsRepository;
import lk.finora.app.ui.staff.StaffHomeActivity;
import lk.finora.app.ui.staff.StaffStatsViewModel;

/**
 * T70 review run: Staff home with the counts unavailable, then with sample counts. Uses a throwaway local STAFF
 * session and no API: run it without adb reverse, so the counts request cannot reach a server. Never runs by
 * default, and it refuses to replace a session already on the device.
 */
@RunWith(AndroidJUnit4.class)
public final class T70ScreenshotTest {
    @Test public void staffHome() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T70 run only", "true".equals(args.getString("finoraT70Shots")));
        assertEquals("http://127.0.0.1:8080/", BuildConfig.API_BASE_URL);
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        File out = app.getExternalFilesDir("t70");
        assertNotNull(out);
        TokenStore store = TokenStore.open(app);
        assertTrue("Sign out on the device first", store.session() == null);
        store.save("t70-local-only", "t70-local-only", "STAFF", "si".equals(language) ? "SI" : "EN");
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        try (var screen = ActivityScenario.launch(StaffHomeActivity.class)) {
            await(screen, a -> {
                View problem = a.findViewById(R.id.staff_counts_problem);
                return problem != null && problem.getVisibility() == View.VISIBLE;
            }, "counts unavailable");
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t70-staff-home-offline-" + tag + ".png")));
            screen.onActivity(a -> {
                var model = new ViewModelProvider((androidx.activity.ComponentActivity) a).get(StaffStatsViewModel.class);
                set(model, "stats", new StaffStatsRepository.Stats(3L, 5L));
                try {
                    var work = (lk.finora.app.ui.accounts.AccountListViewModel.Work) field(model, "work").get(model);
                    work.error = 0;
                    work.changes.setValue(work.changes.getValue() + 1);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                assertEquals("3", ((TextView) a.findViewById(R.id.staff_count_mine)).getText().toString());
                assertEquals(View.GONE, a.findViewById(R.id.staff_counts_problem).getVisibility());
                T62ScreenshotTest.capture(a, new File(out, "t70-staff-home-" + tag + ".png"));
                drawWhole(a.findViewById(R.id.staff_home), new File(out, "t70-staff-home-full-" + tag + ".png"));
            });
        } finally {
            var session = store.session();
            if (session != null) store.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    private static java.lang.reflect.Field field(Object target, String name) throws NoSuchFieldException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void set(Object target, String name, Object value) {
        try {
            field(target, name).set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static <A extends Activity> void await(ActivityScenario<A> scenario, Predicate<A> ready, String name) {
        long end = SystemClock.uptimeMillis() + 30_000;
        var done = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < end) {
            scenario.onActivity(activity -> done.set(ready.test(activity)));
            if (done.get()) break;
            SystemClock.sleep(300);
        }
        assertTrue(name, done.get());
        T62ScreenshotTest.settle();
    }

    /** Draws the whole scrolling page on the window background. */
    private static void drawWhole(View root, File file) {
        View page = root instanceof ScrollView scroll ? scroll.getChildAt(0) : root;
        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
        var canvas = new Canvas(bitmap);
        canvas.drawColor(page.getContext().getColor(R.color.finora_navy));
        page.draw(canvas);
        try (var stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
