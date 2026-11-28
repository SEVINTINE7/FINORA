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

    /**
     * T70.3 live run against a synthetic STAFF fixture (support-reviewer@example.test on a throwaway API reached
     * through adb reverse): request queue, Saved replies, Send a saved reply (EN and SI, never sent) and SMS
     * diagnostics. Nothing is changed on the server.
     */
    @Test public void staffScreensWithLiveData() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T70 live run only", "true".equals(args.getString("finoraT70Live")));
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
        var login = new lk.finora.app.data.api.ApiClient(BuildConfig.API_BASE_URL, () -> null).auth()
                .login(new lk.finora.app.data.api.AuthApi.LoginRequest("support-reviewer@example.test",
                        "Synthetic9-support-suite")).execute().body();
        assertNotNull(login);
        assertEquals("STAFF", login.role);
        store.save(login.accessToken, login.refreshToken, "STAFF", "si".equals(language) ? "SI" : "EN");
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        try {
            long ticket;
            String subject;
            try (var queue = ActivityScenario.launch(lk.finora.app.ui.support.SupportActivity.class)) {
                awaitList(queue);
                queue.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t70-queue-" + tag + ".png")));
                queue.onActivity(a -> drawWhole(a.findViewById(R.id.staff_list_scroll),
                        new File(out, "t70-queue-full-" + tag + ".png")));
                var mine = new java.util.concurrent.atomic.AtomicReference<Object>();
                queue.onActivity(a -> {
                    var model = new ViewModelProvider((androidx.activity.ComponentActivity) a)
                            .get(lk.finora.app.ui.support.SupportViewModel.class);
                    try {
                        for (Object item : (java.util.List<?>) field(model, "tickets").get(model)) {
                            var row = (lk.finora.app.data.repo.SupportRepository.Ticket) item;
                            if ("IN_PROGRESS".equals(row.status()) && mine.get() == null) mine.set(row);
                        }
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                });
                var row = (lk.finora.app.data.repo.SupportRepository.Ticket) mine.get();
                assertNotNull("No request in progress", row);
                ticket = row.id();
                subject = row.subject();
            }
            var catalogIntent = new android.content.Intent(app, lk.finora.app.ui.support.SupportActivity.class)
                    .putExtra(lk.finora.app.ui.support.SupportActivity.CATALOG, true);
            try (var catalog = ActivityScenario.<lk.finora.app.ui.support.SupportActivity>launch(catalogIntent)) {
                awaitList(catalog);
                catalog.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t70-replies-" + tag + ".png")));
                catalog.onActivity(a -> drawWhole(a.findViewById(R.id.staff_list_scroll),
                        new File(out, "t70-replies-full-" + tag + ".png")));
            }
            var replyIntent = new android.content.Intent(app, lk.finora.app.ui.support.StaffReplyActivity.class)
                    .putExtra("ticket", ticket).putExtra("subject", subject);
            try (var reply = ActivityScenario.<Activity>launch(replyIntent)) {
                await(reply, a -> ((android.view.ViewGroup) a.findViewById(R.id.staff_reply_choices)).getChildCount() > 0,
                        "approved replies");
                reply.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t70-reply-empty-" + tag + ".png")));
                reply.onActivity(a -> ((android.view.ViewGroup) a.findViewById(R.id.staff_reply_choices))
                        .getChildAt(0).performClick());
                T62ScreenshotTest.settle();
                reply.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t70-reply-en-" + tag + ".png")));
                reply.onActivity(a -> a.findViewById(R.id.staff_reply_si).performClick());
                T62ScreenshotTest.settle();
                reply.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t70-reply-si-" + tag + ".png")));
            }
            try (var diagnostics = ActivityScenario.launch(lk.finora.app.ui.support.DiagnosticQueueActivity.class)) {
                awaitList(diagnostics);
                diagnostics.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t70-diagnostics-" + tag + ".png")));
                diagnostics.onActivity(a -> drawWhole(a.findViewById(R.id.staff_list_scroll),
                        new File(out, "t70-diagnostics-full-" + tag + ".png")));
            }
        } finally {
            var session = store.session();
            if (session != null) store.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    /** T70.3 ADMIN Test a format, before and after an on-phone check of a made-up SMS (nothing is sent). */
    @Test public void formatSampleWithLiveAdmin() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T70 live run only", "true".equals(args.getString("finoraT70Live")));
        assertEquals("http://127.0.0.1:8080/", BuildConfig.API_BASE_URL);
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        File out = app.getExternalFilesDir("t70");
        TokenStore store = TokenStore.open(app);
        assertTrue("Sign out on the device first", store.session() == null);
        var login = new lk.finora.app.data.api.ApiClient(BuildConfig.API_BASE_URL, () -> null).auth()
                .login(new lk.finora.app.data.api.AuthApi.LoginRequest("delivery-admin@example.test",
                        "Synthetic9-delivery-admin")).execute().body();
        assertNotNull(login);
        store.save(login.accessToken, login.refreshToken, "ADMIN", "si".equals(language) ? "SI" : "EN");
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        try (var screen = ActivityScenario.launch(lk.finora.app.ui.support.FormatSampleActivity.class)) {
            await(screen, a -> ((TextView) a.findViewById(R.id.format_definition)).length() > 0, "approved bundle");
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t70-format-" + tag + ".png")));
            screen.onActivity(a -> {
                ((TextView) a.findViewById(R.id.format_sample)).setText(
                        "Synthetic sample: Rs 1,250.00 debited at SAMPLE STORE on 28/11/2026.");
                a.findViewById(R.id.format_check).performClick();
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.format_result).getVisibility());
                drawWhole(a.findViewById(R.id.format_page), new File(out, "t70-format-result-" + tag + ".png"));
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

    /** Waits until a staff list has loaded (cards shown, no loading line). */
    private static <A extends Activity> void awaitList(ActivityScenario<A> scenario) {
        await(scenario, a -> {
            View status = a.findViewById(R.id.staff_list_status);
            android.view.ViewGroup list = a.findViewById(R.id.staff_list);
            return list != null && list.getChildCount() > 1 && status.getVisibility() == View.GONE;
        }, "staff list loaded");
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
        // Staff home draws its own navy header; the list screens sit on the light surface.
        canvas.drawColor(page.getContext().getColor(root.getId() == R.id.staff_home ? R.color.finora_navy
                : R.color.finora_surface));
        page.draw(canvas);
        try (var stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
