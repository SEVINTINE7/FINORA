package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
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
public class D58GoalPickerTest {
    private static final long WAIT_MS = 20_000;

    @Test
    public void focusGoalAndGeneralAdvice() throws Exception {
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
            if ("si".equals(args.getString("finoraD58Language"))) {
                enterCredentials("worker", workerPassword);
                await(device, By.desc("විශ්ලේෂණ"), "Sinhala profile insights tab");
                tab(R.string.insights_tab);
                activate(withText(R.string.advice_title));
                scrollTo(device, By.text("ඉලක්කයක් නැත (සාමාන්‍ය උපදෙස්)"), "Sinhala general-advice control");
                scrollTo(device, By.text("ඔබේ ඉලක්කය සඳහා සැලැස්ම"), "Sinhala goal heading");
                return;
            }
            exerciseGoals(app, device, workerPassword);
            activate(withText(R.string.sign_out));
            openAdvice(device, "irregular", irregularPassword);
            await(device, By.text(app.getString(R.string.advice_empty)), "second owner empty advice");
            assertTrue("Second owner has no first-owner goals", !device.hasObject(By.text("Near synthetic goal")));
            pressBack();
            activate(withText(R.string.sign_out));
        } finally {
            var current = sessions.session();
            if (current != null) assertTrue("Unable to clear evaluation session", sessions.clear(current));
        }
    }

    private static void enterCredentials(String profile, String password) {
        replace(R.id.email, "finora-t55-perf-" + profile + "@example.invalid");
        replace(R.id.password, password);
        activate(withId(R.id.sign_in));
    }

    private static void signIn(UiDevice device, String profile, String password) {
        enterCredentials(profile, password);
        await(device, By.textContains("LKR"), profile + " Home");
    }

    private static void openAdvice(UiDevice device, String profile, String password) {
        signIn(device, profile, password);
        tab(R.string.insights_tab);
        activate(withText(R.string.advice_title));
    }

    private static void exerciseGoals(Context app, UiDevice device, String password) {
        openAdvice(device, "worker", password);
        var near = By.text("Near synthetic goal");
        var later = By.text("Later synthetic goal");
        await(device, near, "goal picker");
        assertTrue("Nearest active goal selected", device.findObject(near).isChecked());
        assertTrue("Completed goal hidden", !device.hasObject(By.text("Completed synthetic goal")));
        var goal = By.text(app.getString(R.string.advice_section_goal));
        scrollTo(device, goal, "default goal coaching");
        scrollTo(device, By.textContains("LKR 900.00"), "default target amount");
        toTop(device);
        activate(withText("Later synthetic goal"));
        scrollTo(device, By.textContains("LKR 1800.00"), "alternate target amount");
        toTop(device);
        activate(withText(R.string.advice_goal_none));
        scrollTo(device, By.text(app.getString(R.string.advice_rules)), "general canonical advice");
        assertTrue("General advice omits focus section", !device.hasObject(goal));
        pressBack();
    }

    private static void toTop(UiDevice device) {
        var scroll = device.findObject(By.clazz(android.widget.ScrollView.class));
        assertTrue("advice scroll container", scroll != null);
        for (int i = 0; i < 6 && scroll.scroll(androidx.test.uiautomator.Direction.UP, 0.7f); i++) {
            // Move back to the picker before the next action.
        }
    }

    private static void scrollTo(UiDevice device, androidx.test.uiautomator.BySelector selector, String name) {
        if (!device.wait(Until.hasObject(selector), 2500)) {
            var scroll = device.findObject(By.clazz(android.widget.ScrollView.class));
            assertTrue("advice scroll container", scroll != null);
            for (int i = 0; i < 6 && !device.hasObject(selector); i++) {
                if (!scroll.scroll(androidx.test.uiautomator.Direction.DOWN, 0.65f)) break;
            }
        }
        await(device, selector, name);
    }

    private static void await(UiDevice device, androidx.test.uiautomator.BySelector selector, String name) {
        assertTrue(name, device.wait(Until.hasObject(selector), WAIT_MS));
        android.util.Log.i("FinoraT55", "PASS " + name);
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
