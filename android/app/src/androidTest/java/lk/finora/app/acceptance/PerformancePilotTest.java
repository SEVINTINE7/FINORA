package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Process;
import android.os.UserManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.ui.Money;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.budgets.BudgetListActivity;
import lk.finora.app.ui.insights.InsightsActivity;
import lk.finora.app.ui.login.LoginActivity;
import lk.finora.app.ui.main.MainActivity;

import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PerformancePilotTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private enum Route {
        HOME(R.string.home_tab, MainActivity.class),
        // T62/D65: Records is a tab of the user shell, measured like Home.
        RECORDS(R.string.records_tab, MainActivity.class),
        PLAN(R.string.budget_list_title, BudgetListActivity.class),
        INSIGHTS(R.string.insight_open, InsightsActivity.class);

        final int button;
        final Class<? extends Activity> destination;

        Route(int button, Class<? extends Activity> destination) {
            this.button = button;
            this.destination = destination;
        }
    }

    @Test public void measure() throws Exception {
        var runner = InstrumentationRegistry.getInstrumentation();
        var args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Explicit isolated performance run only", "true".equals(args.getString("finoraPerformancePilot")));
        Context app = runner.getTargetContext();
        boolean handset = "true".equals(args.getString("finoraPerformanceHandset"));
        boolean emulator = "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE);
        boolean preflight = "true".equals(args.getString("finoraPerformanceEmulatorPreflight"));
        if (preflight) assertTrue(emulator && !handset);
        assertEquals(handset || preflight ? "lk.finora.app.t57handset" : "lk.finora.app.acceptance",
                app.getPackageName());
        assertTrue(BuildConfig.DEBUG && "http://127.0.0.1:8080/".equals(BuildConfig.API_BASE_URL));
        assertEquals(!handset, emulator);
        long serial = app.getSystemService(UserManager.class).getSerialNumberForUser(Process.myUserHandle());
        assertEquals(args.getString("finoraTestUserSerial"), Long.toString(serial));
        if (handset) {
            assertTrue(Build.VERSION.SDK_INT == 34 && serial > 0);
            assertEquals("HMD CREST", Build.MODEL);
            assertEquals(Build.FINGERPRINT, args.getString("finoraBuildFingerprint"));
            assertTrue(app.checkSelfPermission(android.Manifest.permission.READ_SMS)
                    == android.content.pm.PackageManager.PERMISSION_DENIED);
        }
        assertEquals(YearMonth.of(2026, 11), YearMonth.now(ZoneId.of("Asia/Colombo")));
        var sessions = TokenStore.open(app);
        String coldIndex = args.getString("finoraFreshIndex");
        if (coldIndex != null) {
            assertTrue("An existing synthetic session is required", sessions.session() != null);
            sample(app, Route.HOME, "fresh-process-launch", Integer.parseInt(coldIndex),
                    started -> { started.run(); ActivityScenario.launch(LoginActivity.class); });
            return;
        }
        var prior = sessions.session();
        if (prior != null) assertTrue(sessions.clear(prior));
        String password;
        try (var reader = new BufferedReader(new InputStreamReader(
                app.openFileInput("t55-performance-input"), StandardCharsets.UTF_8))) {
            password = reader.readLine();
        } finally {
            app.deleteFile("t55-performance-input");
        }
        assertTrue(password != null && !password.isBlank());
        try (var scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.email)).perform(act(view -> ((EditText) view)
                    .setText("finora-t55-perf-worker@example.invalid")));
            onView(withId(R.id.password)).perform(act(view -> ((EditText) view).setText(password)));
            onView(withId(R.id.sign_in)).perform(act(View::performClick));
            assertTrue(UiDevice.getInstance(runner).wait(Until.hasObject(By.res(app.getPackageName(),
                    "dashboard_income_amount").text(Money.rupees(app, new java.math.BigDecimal("60000.00")))), 20_000));
            boolean traceCheck = "true".equals(args.getString("finoraTraceCheck"));
            String traceRoute = args.getString("finoraTraceRoute");
            for (Route route : Route.values()) {
                if (traceRoute != null && route != Route.valueOf(traceRoute)) continue;
                int attempts = traceCheck ? (traceRoute == null ? 1 : 3) : handset || preflight ? 35 : 15;
                for (int n = 1; n <= attempts; n++) {
                    tab(switch (route) {
                        case HOME -> R.string.records_tab;
                        case RECORDS -> R.string.home_tab;
                        case PLAN -> R.string.plan_tab;
                        case INSIGHTS -> R.string.insights_tab;
                    });
                    sample(app, route, traceCheck ? "trace-check" : n <= 5 ? "warmup" : "warm", n <= 5 ? n : n - 5,
                            started -> onView(route == Route.HOME || route == Route.RECORDS
                                    ? withId(route.button)
                                    : org.hamcrest.Matchers.allOf(withText(route.button), isAssignableFrom(android.widget.Button.class))).perform(act(view -> { started.run(); view.performClick(); })));
                    if (route.destination != MainActivity.class) pressBack();
                }
            }
            if ("true".equals(args.getString("finoraHttpProbe"))) measureHttp(sessions);
        }
    }

    private static void measureHttp(TokenStore sessions) throws java.io.IOException {
        var client = new lk.finora.app.data.api.ApiClient(BuildConfig.API_BASE_URL, sessions);
        var origin = okhttp3.HttpUrl.get(BuildConfig.API_BASE_URL);
        for (String route : new String[]{"dashboard", "transactions", "budgets", "insights", "score"}) {
            String identifier = "t55-" + java.util.UUID.randomUUID();
            var uri = origin.newBuilder().addPathSegments("api/v1/" + route);
            if ("transactions".equals(route)) {
                uri.addQueryParameter("from", "2026-11-01").addQueryParameter("to", "2026-11-30");
            } else {
                uri.addQueryParameter("month", "2026-11");
            }
            var request = new okhttp3.Request.Builder().url(uri.build())
                    .header("X-Finora-Perf-Id", identifier).build();
            long start = android.os.SystemClock.elapsedRealtimeNanos();
            int status = 0;
            try (var response = client.newCall(request).execute()) {
                status = response.code();
                if (response.body() != null) {
                    var sink = okio.Okio.blackhole();
                    response.body().source().readAll(sink);
                }
            } finally {
                long end = android.os.SystemClock.elapsedRealtimeNanos();
                android.util.Log.i("FinoraPerfHttp", identifier + "," + route + "," + status + "," + start + "," + end);
            }
            assertEquals("Expected an authenticated read-only HTTP probe", 200, status);
        }
    }

    private static void sample(Context app, Route route, String cohort, int index,
            Consumer<Runnable> action) throws InterruptedException {
        var runner = InstrumentationRegistry.getInstrumentation();
        String key = route.name() + "-" + cohort + "-" + index;
        var probe = new RenderedContentProbe(view -> usable(app, route, view), key,
                "trace-check".equals(cohort) || "fresh-process-launch".equals(cohort));
        var monitor = ActivityLifecycleMonitorRegistry.getInstance();
        var attached = new AtomicBoolean();
        Consumer<Activity> watch = activity -> {
            if (route.destination.isInstance(activity) && attached.compareAndSet(false, true)) {
                probe.watch(activity);
            }
        };
        ActivityLifecycleCallback callback = (activity, stage) -> {
            if (stage == Stage.RESUMED) watch.accept(activity);
        };
        runner.runOnMainSync(() -> monitor.addLifecycleCallback(callback));
        Runnable start = () -> {
            probe.markAction();
            if (route.destination == MainActivity.class) {
                for (Activity activity : monitor.getActivitiesInStage(Stage.RESUMED)) watch.accept(activity);
            }
        };
        try {
            action.accept(() -> {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) start.run();
                else runner.runOnMainSync(start);
            });
            var result = probe.await(TIMEOUT);
            report(route.name() + "," + cohort + "," + index + ","
                    + result.outcome() + "," + result.actionNs() + "," + result.contentReadyNs()
                    + "," + result.frameCommittedNs());
        } catch (RuntimeException | AssertionError failure) {
            report(route.name() + "," + cohort + "," + index + ",FAILED,0,0,0");
            throw failure;
        } finally {
            runner.runOnMainSync(() -> monitor.removeLifecycleCallback(callback));
        }
    }

    private static void report(String row) {
        android.util.Log.i("FinoraPerfPilot", row);
        var status = new android.os.Bundle();
        status.putString("finoraTiming", row);
        InstrumentationRegistry.getInstrumentation().sendStatus(2, status);
    }

    private static boolean usable(Context app, Route route, View root) {
        Predicate<String> visible = text -> hasText(root, text::equals);
        return switch (route) {
            case HOME -> visible.test(Money.rupees(app, new java.math.BigDecimal("60000.00")))
                    && visible.test(Money.rupees(app, new java.math.BigDecimal("40000.00")));
            case RECORDS -> visible.test(lk.finora.app.ui.transactions.TransactionRows.day(app, "2026-11-30",
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo"))))
                    && hasText(root, text -> text.contains(app.getString(R.string.form_rupees_prefix).trim()));
            case PLAN -> visible.test("Shopping")
                    && hasText(root, text -> text.contains(Money.rupees(app, new java.math.BigDecimal("5000.00"))));
            case INSIGHTS -> root.findViewById(R.id.insights_score_value) instanceof TextView score
                    && score.getText().toString().matches("[0-9]{1,3}")
                    && score.getGlobalVisibleRect(new Rect())
                    && root.findViewById(R.id.insights_status).getVisibility() == View.GONE
                    && root.findViewById(R.id.insights_parts) instanceof ViewGroup parts
                    && parts.getChildCount() == 5;
        };
    }

    private static boolean hasText(View view, Predicate<String> predicate) {
        if (!view.isShown()) return false;
        if (view instanceof TextView text && predicate.test(text.getText().toString())
                && text.getGlobalVisibleRect(new Rect())) return true;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), predicate)) return true;
            }
        }
        return false;
    }

    private static void tab(int label) {
        onView(withId(label)).perform(act(View::performClick));
    }

    private static ViewAction act(Consumer<View> operation) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "dispatch isolated benchmark action"; }
            @Override public void perform(UiController controller, View view) { operation.accept(view); }
        };
    }
}
