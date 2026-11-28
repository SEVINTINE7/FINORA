package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.UserManager;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.login.LoginActivity;

@RunWith(AndroidJUnit4.class)
public class EvaluationJourneysTest {
    private static final long WAIT_MS = 20_000;

    @Test
    public void twoSyntheticProfiles() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var args = InstrumentationRegistry.getArguments();
        Context app = instrumentation.getTargetContext();
        org.junit.Assume.assumeTrue("Explicit evaluation opt-in required",
                "true".equals(args.getString("finoraT55Evaluation")));
        assertTrue(BuildConfig.DEBUG);
        assertEquals("http://127.0.0.1:8080/", BuildConfig.API_BASE_URL);
        assertEquals("lk.finora.app.acceptance", app.getPackageName());
        assertTrue("Emulator hardware required", "ranchu".equals(Build.HARDWARE)
                || "goldfish".equals(Build.HARDWARE));
        long serial = app.getSystemService(UserManager.class).getSerialNumberForUser(Process.myUserHandle());
        assertEquals("Wrong emulator Android user", args.getString("finoraTestUserSerial"), Long.toString(serial));
        assertEquals("Benchmark month prerequisite", YearMonth.of(2026, 11),
                YearMonth.now(ZoneId.of("Asia/Colombo")));

        String workerPassword;
        String irregularPassword;
        try (var input = new BufferedReader(new InputStreamReader(
                app.openFileInput("t55-session-input"), StandardCharsets.UTF_8))) {
            workerPassword = input.readLine();
            irregularPassword = input.readLine();
        } finally {
            app.deleteFile("t55-session-input");
        }
        assertTrue("Session input missing", workerPassword != null && !workerPassword.isBlank()
                && irregularPassword != null && !irregularPassword.isBlank());
        var sessions = TokenStore.open(app);
        var prior = sessions.session();
        if (prior != null) assertTrue("Unable to clear previous evaluation session", sessions.clear(prior));
        var device = UiDevice.getInstance(instrumentation);
        try (var scenario = ActivityScenario.launch(LoginActivity.class)) {
            exercise(app, device, "worker", workerPassword, "60000.00", "40000.00");
            signOut();
            exercise(app, device, "irregular", irregularPassword, "25750.00", "11000.00");
            signOut();
        } finally {
            var current = sessions.session();
            if (current != null) assertTrue("Unable to clear evaluation session", sessions.clear(current));
        }
    }

    private static void exercise(Context app, UiDevice device, String profile, String password,
            String income, String expense) {
        String email = "finora-t55-perf-" + profile + "@example.invalid";
        replace(R.id.email, email);
        replace(R.id.password, password);
        activate(withId(R.id.sign_in));
        await(device, By.desc(app.getString(R.string.dashboard_income, income)), profile + " Home income");
        await(device, By.desc(app.getString(R.string.dashboard_expense, expense)), profile + " Home expense");
        // T62/D65: the Records tab is the list itself, grouped under day headers.
        tab(R.string.records_tab);
        await(device, By.text(lk.finora.app.ui.transactions.TransactionRows.day(app, "2026-11-30",
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")))), profile + " Records November row");
        tab(R.string.plan_tab);
        activate(withText(R.string.budget_list_title));
        await(device, By.textContains("Shopping"), profile + " Shopping budget");
        pressBack();
        activate(withText(R.string.bill_list_title));
        await(device, By.textContains("SYNTHETIC " + profile + " recurring bill"), profile + " bill");
        pressBack();
        activate(withText(R.string.goal_list_title));
        String goal = "SYNTHETIC " + profile + " goal";
        await(device, By.text(goal), profile + " goal");
        activate(withContentDescription(org.hamcrest.Matchers.startsWith(goal + ". ")));
        String date = java.time.LocalDate.of(2026, profile.equals("worker") ? 11 : 10, 20)
                .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                        .withLocale(java.util.Locale.getDefault()));
        String amount = profile.equals("worker") ? "2000.00" : "500.00";
        await(device, By.desc(app.getString(R.string.goal_contribution_row, date, amount)),
                profile + " contribution");
        pressBack();
        pressBack();
        tab(R.string.insights_tab);
        activate(withText(R.string.insight_open));
        await(device, By.textContains("out of 100"), profile + " score");
        pressBack();
        activate(withText(R.string.advice_title));
        await(device, By.text(app.getString(R.string.advice_rules)), profile + " rule advice");
        pressBack();
    }

    private static void await(UiDevice device, androidx.test.uiautomator.BySelector selector, String name) {
        assertTrue(name, device.wait(Until.hasObject(selector), WAIT_MS));
        android.util.Log.i("FinoraT55", "PASS " + name);
    }

    private static void signOut() {
        tab(R.string.more_tab);
        activate(withText(R.string.sign_out));
    }

    private static void tab(int label) {
        activate(withId(label));
    }

    private static void replace(int id, String value) {
        onView(withId(id)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(EditText.class); }
            @Override public String getDescription() { return "enter disposable test input"; }
            @Override public void perform(UiController controller, View view) {
                ((EditText) view).setText(value);
            }
        });
    }

    private static void activate(Matcher<View> target) {
        onView(target).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return androidx.test.espresso.matcher.ViewMatchers.isClickable(); }
            @Override public String getDescription() { return "activate the FINORA control"; }
            @Override public void perform(UiController controller, View view) {
                view.performClick();
            }
        });
    }
}
