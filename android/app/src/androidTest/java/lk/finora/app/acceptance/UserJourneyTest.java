package lk.finora.app.acceptance;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioButton;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.login.LoginActivity;
import lk.finora.app.ui.register.RegisterActivity;
import lk.finora.app.util.LocaleHelper;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UserJourneyTest {
    private static final long UI_TIMEOUT_MS = 12_000;
    private static final String MERCHANT = "Synthetic UI purchase";
    private Context app;
    private UiDevice device;
    private TokenStore sessions;

    @Before public void isolatedEmulatorOnly() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        assertEquals("An explicit isolated-emulator opt-in is required", "true",
                InstrumentationRegistry.getArguments().getString("finoraIsolatedEmulator"));
        assertTrue("Connected test suite is restricted to emulator hardware",
                "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        app = instrumentation.getTargetContext();
        assertTrue(BuildConfig.DEBUG);
        assertEquals("http://127.0.0.1:8080/", BuildConfig.API_BASE_URL);
        device = UiDevice.getInstance(instrumentation);
        assertEquals("Revoke READ_SMS on this isolated emulator before running this suite",
                PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));
        sessions = TokenStore.open(app);
        clearSession();
        instrumentation.runOnMainSync(() -> LocaleHelper.apply("EN"));
    }

    @After public void clearSession() throws Exception {
        if (sessions != null) {
            var current = sessions.session();
            if (current != null) assertTrue(sessions.clear(current));
        }
    }

    @Test public void loginAndLanguageDialogUseSecureWindows() {
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            screen.onActivity(activity -> assertTrue((activity.getWindow().getAttributes().flags
                    & WindowManager.LayoutParams.FLAG_SECURE) != 0));
            onView(allOf(withText(R.string.language_selector), isClickable())).perform(activateControl());
            onView(withText(R.string.language_english)).inRoot(isDialog()).check((view, failure) -> {
                if (failure != null) throw failure;
                var params = (WindowManager.LayoutParams) view.getRootView().getLayoutParams();
                assertTrue((params.flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            });
        }
    }

    @Test public void registerLoginAndManageFinancialRecordsWithSmsDenied() {
        String identity = UUID.randomUUID().toString().replace("-", "");
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.create_account)).perform(reveal(), activateControl());
            awaitActivity(RegisterActivity.class);
            enter(R.id.register_email, "t51-" + identity + "@example.test");
            enter(R.id.register_name, "Synthetic UI user");
            enter(R.id.register_password, "Test-" + identity + "-9");
            onView(withId(R.id.register_over18)).perform(reveal(), activateControl()).check(matches(isChecked()));
            onView(withId(R.id.register_consent)).perform(reveal(), activateControl()).check(matches(isChecked()));
            onView(withId(R.id.register_submit)).perform(reveal(), activateControl());
            awaitActivity(LoginActivity.class);
            onView(withId(R.id.email)).check(matches(withText("t51-" + identity + "@example.test")));
            enter(R.id.password, "Test-" + identity + "-9");
            clickId(R.id.sign_in);
            awaitText(app.getString(R.string.more_tab));
            tab(R.string.more_tab);
            clickText(R.string.finance_help);
            onView(withText(R.string.faq_title)).check(matches(isDisplayed()));
            onView(withText(R.string.faq_login)).check(matches(isDisplayed()));
            pressBack();
            clickText(R.string.support_tickets);
            clickText(R.string.support_create);
            onView(withText(R.string.support_language)).inRoot(isDialog()).perform(click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            await(By.textContains("· Language · Open"));
            pressBack();
            clickText(R.string.account_list_title);
            awaitId("records_add");
            clickId(R.id.records_add);
            enter(R.id.form_name, "Synthetic wallet");
            enter(R.id.account_balance, "0.00");
            clickId(R.id.records_save);
            awaitText("Synthetic wallet");
            pressBack();

            tab(R.string.records_tab);
            clickText(R.string.transaction_open_manual);
            awaitId("form_account");
            select(R.id.form_account, "Synthetic wallet");
            select(R.id.form_type, app.getString(R.string.category_expense));
            select(R.id.form_category, "Food");
            enter(R.id.form_amount, "12.34");
            enter(R.id.form_merchant, MERCHANT);
            clickId(R.id.form_date);
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            clickId(R.id.form_save);
            awaitText(app.getString(R.string.records_tab));
            tab(R.string.home_tab);
            awaitText("Expenses: LKR 12.34");

            tab(R.string.plan_tab);
            clickText(R.string.budget_list_title);
            clickId(R.id.records_add);
            select(R.id.form_kind, "Food");
            enter(R.id.budget_limit, "100.00");
            clickId(R.id.records_save);
            awaitText("Used LKR 12.34 of LKR 100.00 · LKR 87.66 left");
            pressBack();

            tab(R.string.records_tab);
            clickText(R.string.transaction_title);
            String edit = app.getString(R.string.records_edit_named, "");
            await(By.descContains(MERCHANT));
            onView(allOf(withContentDescription(startsWith(edit)),
                    withContentDescription(containsString(MERCHANT)))).perform(click());
            enter(R.id.form_amount, "15.17");
            clickId(R.id.form_save);
            await(By.textContains("15.17"));
            String remove = app.getString(R.string.records_remove_named, "");
            onView(allOf(withContentDescription(startsWith(remove)),
                    withContentDescription(containsString(MERCHANT)))).perform(click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            awaitText(app.getString(R.string.records_empty));
            pressBack();
            clickText(R.string.sms_access_title);
            onView(withText(R.string.sms_manual_entry)).check(matches(isDisplayed()));
            clickText(R.string.sms_no_thanks);
            assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));
            awaitText(app.getString(R.string.records_tab));
        }
    }

    private void awaitId(String id) { await(By.res(app.getPackageName(), id)); }

    private static void awaitActivity(Class<? extends android.app.Activity> expected) {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var resumed = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
        do {
            instrumentation.runOnMainSync(() -> resumed.set(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).stream().anyMatch(expected::isInstance)));
            if (resumed.get()) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Expected resumed activity: " + expected.getSimpleName());
    }

    private void awaitText(String text) { await(By.text(text)); }
    private void await(BySelector selector) {
        assertTrue("Expected FINORA screen state did not appear", device.wait(Until.hasObject(selector), UI_TIMEOUT_MS));
    }

    private static void tab(int label) {
        onView(allOf(withText(label), isAssignableFrom(RadioButton.class))).perform(click());
    }

    private static void clickText(int label) {
        onView(allOf(withText(label), isClickable())).perform(reveal(), click());
    }

    private static void clickId(int id) {
        onView(withId(id)).perform(reveal(), click());
    }

    private static ViewAction activateControl() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return allOf(isEnabled(), isClickable());
            }
            @Override public String getDescription() { return "activate the view's click action"; }
            @Override public void perform(UiController controller, View view) {
                view.performClick();
            }
        };
    }

    private static void select(int id, String label) {
        onView(withId(id)).perform(reveal(), new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return allOf(isAssignableFrom(android.widget.Spinner.class), isEnabled());
            }
            @Override public String getDescription() { return "select the synthetic fixture choice"; }
            @Override public void perform(UiController controller, View view) {
                var spinner = (android.widget.Spinner) view;
                for (int i = 0; i < spinner.getCount(); i++) {
                    if (label.equals(String.valueOf(spinner.getItemAtPosition(i)))) {
                        spinner.setSelection(i);
                        controller.loopMainThreadUntilIdle();
                        return;
                    }
                }
                fail("Expected synthetic fixture choice is unavailable");
            }
        });
    }

    private static void enter(int id, String value) {
        onView(withId(id)).perform(reveal(), new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(EditText.class); }
            @Override public String getDescription() { return "enter disposable fixture input"; }
            @Override public void perform(UiController controller, View view) { ((EditText) view).setText(value); }
        });
    }

    private static ViewAction reveal() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "show the form control"; }
            @Override public void perform(UiController controller, View view) {
                if (!isDisplayingAtLeast(90).matches(view)) scrollTo().perform(controller, view);
            }
        };
    }
}
