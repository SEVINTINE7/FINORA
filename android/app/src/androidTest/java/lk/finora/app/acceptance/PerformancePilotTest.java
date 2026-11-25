package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
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
import android.widget.RadioButton;
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
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.budgets.BudgetListActivity;
import lk.finora.app.ui.insights.InsightsActivity;
import lk.finora.app.ui.login.LoginActivity;
import lk.finora.app.ui.main.MainActivity;
import lk.finora.app.ui.transactions.TransactionActivity;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PerformancePilotTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private enum Route {
        HOME(R.string.home_tab, MainActivity.class),
        RECORDS(R.string.transaction_title, TransactionActivity.class),
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
        Assume.assumeTrue("Dedicated emulator pilot only", "true".equals(args.getString("finoraPerformancePilot")));
        Context app = runner.getTargetContext();
        assertEquals("lk.finora.app.acceptance", app.getPackageName());
        assertTrue(BuildConfig.DEBUG && "http://127.0.0.1:8080/".equals(BuildConfig.API_BASE_URL));
        assertTrue("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        assertEquals(args.getString("finoraTestUserSerial"), Long.toString(app.getSystemService(UserManager.class)
                .getSerialNumberForUser(Process.myUserHandle())));
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
            assertTrue(UiDevice.getInstance(runner).wait(Until.hasObject(By.text(
                    app.getString(R.string.dashboard_income, "60000.00"))), 20_000));
            boolean traceCheck = "true".equals(args.getString("finoraTraceCheck"));
            for (Route route : Route.values()) {
                for (int n = 1; n <= (traceCheck ? 1 : 15); n++) {
                    tab(switch (route) {
                        case HOME, RECORDS -> R.string.records_tab;
                        case PLAN -> R.string.plan_tab;
                        case INSIGHTS -> R.string.insights_tab;
                    });
                    sample(app, route, traceCheck ? "trace-check" : n <= 5 ? "warmup" : "warm", n <= 5 ? n : n - 5,
                            started -> onView(route == Route.HOME
                                    ? allOf(withText(route.button), isAssignableFrom(RadioButton.class))
                                    : withText(route.button)).perform(act(view -> { started.run(); view.performClick(); })));
                    if (route != Route.HOME) pressBack();
                }
            }
        }
    }

    private static void sample(Context app, Route route, String cohort, int index,
            Consumer<Runnable> action) throws InterruptedException {
        var runner = InstrumentationRegistry.getInstrumentation();
        String key = route.name() + "-" + cohort + "-" + index;
        var probe = new RenderedContentProbe(view -> usable(app, route, view), key, "trace-check".equals(cohort));
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
            if (route == Route.HOME) {
                for (Activity activity : monitor.getActivitiesInStage(Stage.RESUMED)) watch.accept(activity);
            }
        };
        try {
            action.accept(() -> {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) start.run();
                else runner.runOnMainSync(start);
            });
            var result = probe.await(TIMEOUT);
            android.util.Log.i("FinoraPerfPilot", route.name() + "," + cohort + "," + index + ","
                    + result.outcome() + "," + result.actionNs() + "," + result.contentReadyNs()
                    + "," + result.frameCommittedNs());
        } catch (RuntimeException | AssertionError failure) {
            android.util.Log.i("FinoraPerfPilot", route.name() + "," + cohort + "," + index + ",FAILED,0,0,0");
            throw failure;
        } finally {
            runner.runOnMainSync(() -> monitor.removeLifecycleCallback(callback));
        }
    }

    private static boolean usable(Context app, Route route, View root) {
        Predicate<String> visible = text -> hasText(root, text::equals);
        return switch (route) {
            case HOME -> visible.test(app.getString(R.string.dashboard_income, "60000.00"))
                    && visible.test(app.getString(R.string.dashboard_expense, "40000.00"));
            case RECORDS -> hasText(root, text -> text.startsWith("2026-11-30") && text.contains("LKR"));
            case PLAN -> visible.test("Shopping")
                    && hasText(root, text -> text.contains("LKR") && text.contains("5000"));
            case INSIGHTS -> hasText(root, text -> text.matches("[0-9]+ out of 100"))
                    && (visible.test(app.getString(R.string.insight_empty))
                        || hasText(root, text -> text.startsWith("Period:")))
                    && !visible.test(app.getString(R.string.records_loading));
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
        onView(allOf(withText(label), isAssignableFrom(RadioButton.class))).perform(act(View::performClick));
    }

    private static ViewAction act(Consumer<View> operation) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "dispatch isolated benchmark action"; }
            @Override public void perform(UiController controller, View view) { operation.accept(view); }
        };
    }
}
