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
import android.widget.EditText;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import lk.finora.app.R;
import lk.finora.app.ui.login.PasswordResetActivity;
import lk.finora.app.ui.register.RegisterActivity;

/**
 * T69 review run: captures Register (top, whole page, filled, with an error) and both Password reset steps.
 * No account is created and no mail is sent: Register is never submitted, and the reset screen talks to an
 * in-process fake that answers 202. Never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T69ScreenshotTest {
    @Test public void registerAndPasswordReset() {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T69 run only", "true".equals(args.getString("finoraT69Shots")));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        File out = app.getExternalFilesDir("t69");
        assertNotNull(out);
        try {
            register(out, tag);
            reset(out, tag);
        } finally {
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    private void register(File out, String tag) {
        try (var screen = ActivityScenario.launch(RegisterActivity.class)) {
            await(screen, a -> a.findViewById(R.id.register_submit) != null, "register shown");
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, "t69-register-" + tag + ".png"));
                ((EditText) a.findViewById(R.id.register_name)).setText("Nimal Perera");
                ((EditText) a.findViewById(R.id.register_email)).setText("nimal@example.test");
                ((EditText) a.findViewById(R.id.register_password)).setText("Lanka2026");
                a.findViewById(R.id.register_over18).performClick();
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                assertTrue(a.findViewById(R.id.register_submit).isEnabled() == false);
                drawWhole(a.findViewById(R.id.register_root), new File(out, "t69-register-full-" + tag + ".png"));
                a.findViewById(R.id.register_consent).performClick();
                ((EditText) a.findViewById(R.id.register_email)).setText("nimal.example.test");
                ((EditText) a.findViewById(R.id.register_password)).setText("lanka");
                a.findViewById(R.id.register_submit).performClick();
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.register_email_error).getVisibility());
                drawWhole(a.findViewById(R.id.register_root), new File(out, "t69-register-errors-" + tag + ".png"));
            });
        }
    }

    private void reset(File out, String tag) {
        try (var screen = ActivityScenario.launch(PasswordResetActivity.class)) {
            await(screen, a -> a.findViewById(R.id.reset_action) != null, "reset shown");
            screen.onActivity(a -> {
                attachFake(a);
                T62ScreenshotTest.capture(a, new File(out, "t69-reset-email-" + tag + ".png"));
                ((EditText) a.findViewById(R.id.reset_email)).setText("nimal@example.test");
                a.findViewById(R.id.reset_action).performClick();
            });
            await(screen, a -> a.findViewById(R.id.reset_code_group).getVisibility() == View.VISIBLE, "code step");
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, "t69-reset-code-" + tag + ".png")));
            screen.onActivity(a -> {
                ((EditText) a.findViewById(R.id.reset_code_input)).setText("482913");
                ((EditText) a.findViewById(R.id.reset_password)).setText("Lanka2026");
                ((EditText) a.findViewById(R.id.reset_repeat)).setText("Lanka2025");
                a.findViewById(R.id.reset_action).performClick();
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.reset_repeat_error).getVisibility());
                drawWhole(a.findViewById(R.id.reset_root), new File(out, "t69-reset-mismatch-" + tag + ".png"));
            });
            assertTrue(screen.getState() != Lifecycle.State.DESTROYED);
        }
    }

    /** Replaces the screen's API with a fake that accepts the code request, like the reset journey test. */
    private static void attachFake(Activity active) {
        var api = (lk.finora.app.data.api.AuthApi) java.lang.reflect.Proxy.newProxyInstance(
                lk.finora.app.data.api.AuthApi.class.getClassLoader(),
                new Class<?>[]{lk.finora.app.data.api.AuthApi.class}, (proxy, method, ignored) -> {
                    if (!"requestPasswordReset".equals(method.getName())) throw new AssertionError("Unexpected request");
                    return reply(202);
                });
        try {
            var field = active.getClass().getDeclaredField("api");
            field.setAccessible(true);
            field.set(active, api);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static retrofit2.Call<Void> reply(int status) {
        var request = new okhttp3.Request.Builder().url("http://127.0.0.1/").build();
        var raw = new okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(status).message("fixture").body(okhttp3.ResponseBody.create(new byte[0], null)).build();
        var response = retrofit2.Response.<Void>success(null, raw);
        return new retrofit2.Call<>() {
            private boolean executed;
            private boolean canceled;
            @Override public retrofit2.Response<Void> execute() { executed = true; return response; }
            @Override public void enqueue(retrofit2.Callback<Void> callback) {
                executed = true;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResponse(this, response));
            }
            @Override public boolean isExecuted() { return executed; }
            @Override public void cancel() { canceled = true; }
            @Override public boolean isCanceled() { return canceled; }
            @Override public retrofit2.Call<Void> clone() { return reply(status); }
            @Override public okhttp3.Request request() { return request; }
            @Override public okio.Timeout timeout() { return okio.Timeout.NONE; }
        };
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

    /** Draws the whole scrolling page on the navy window colour. */
    private static void drawWhole(View root, File file) {
        View page = root instanceof ScrollView scroll ? scroll.getChildAt(0) : root;
        Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight() + root.getPaddingTop(), Bitmap.Config.ARGB_8888);
        var canvas = new Canvas(bitmap);
        canvas.drawColor(page.getContext().getColor(R.color.finora_navy));
        canvas.translate(0, root.getPaddingTop());
        page.draw(canvas);
        try (var stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
