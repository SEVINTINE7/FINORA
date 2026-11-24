package lk.finora.app.acceptance;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.ScrollView;
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
import lk.finora.app.ui.main.MainActivity;
import lk.finora.app.ui.register.RegisterActivity;
import lk.finora.app.util.LocaleHelper;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UserJourneyTest {
    private static final long UI_TIMEOUT_MS = 12_000;
    private static final String MERCHANT = "Synthetic UI purchase";
    private static final String DRAFTER = "support-drafter@example.test";
    private static final String REVIEWER = "support-reviewer@example.test";
    private static final String SUPPORT_FIXTURE_PASSWORD = "Synthetic9-support-suite";
    private Context app;
    private UiDevice device;
    private TokenStore sessions;
    private boolean keepAccessibilityFixture;

    @Before public void requireIsolatedTarget() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var arguments = InstrumentationRegistry.getArguments();
        app = instrumentation.getTargetContext();
        assertTrue(BuildConfig.DEBUG);
        assertEquals("http://127.0.0.1:8080/", BuildConfig.API_BASE_URL);
        boolean emulator = "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE);
        if ("true".equals(arguments.getString("finoraIsolatedHandset"))) {
            assertEquals("Handset mode requires the separate acceptance package",
                    "lk.finora.app.acceptance", app.getPackageName());
            long serial = app.getSystemService(UserManager.class).getSerialNumberForUser(Process.myUserHandle());
            assertTrue("Handset mode requires a known Android user serial", serial > 0);
            assertEquals("Wrong Android test profile", Long.toString(serial),
                    arguments.getString("finoraTestUserSerial"));
        } else {
            assertEquals("An explicit isolated-emulator opt-in is required", "true",
                    arguments.getString("finoraIsolatedEmulator"));
            assertTrue("Emulator mode requires emulator hardware", emulator);
        }
        device = UiDevice.getInstance(instrumentation);
        assertEquals("Revoke READ_SMS on this isolated emulator before running this suite",
                PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));
        sessions = TokenStore.open(app);
        clearSession();
        instrumentation.runOnMainSync(() -> LocaleHelper.apply("EN"));
    }

    @After public void clearSession() throws Exception {
        if (keepAccessibilityFixture) return;
        if (sessions != null) {
            var current = sessions.session();
            if (current != null) assertTrue(sessions.clear(current));
        }
    }

    @Test public void stageHandsetAccessibilityFixture() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Opt-in handset fixture only",
                "true".equals(arguments.getString("finoraIsolatedHandset"))
                        && "true".equals(arguments.getString("finoraStageAccessibility")));
        var screen = ActivityScenario.launch(LoginActivity.class);
        try {
            if ("STAFF".equals(arguments.getString("finoraStageRole"))) {
                signInStaff(DRAFTER, SUPPORT_FIXTURE_PASSWORD);
                openCatalog();
                awaitButton(R.string.support_approve);
                clickText(R.string.support_approve);
                confirm();
                await(By.textContains(app.getString(R.string.support_sign_in) + " · "
                        + app.getString(R.string.support_active)));
                pressBack();
                awaitActivity(lk.finora.app.ui.staff.StaffHomeActivity.class);
                signOut();
                signInStaff(REVIEWER, SUPPORT_FIXTURE_PASSWORD);
            } else {
                signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            }
            keepAccessibilityFixture = true;
        } finally {
            screen.close();
        }
    }

    @Test public void verifyFaqTextLayoutAtLargeScale() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Explicit handset layout probe only",
                "true".equals(arguments.getString("finoraIsolatedHandset"))
                        && "true".equals(arguments.getString("finoraCheckFaqLayout")));
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            checkFaqLayout();
            clickText(R.string.language_selector);
            dialogChoice(app.getString(R.string.language_sinhala));
            var sinhala = new android.content.res.Configuration(app.getResources().getConfiguration());
            sinhala.setLocale(java.util.Locale.forLanguageTag("si"));
            awaitText(app.createConfigurationContext(sinhala).getString(R.string.more_tab));
            checkFaqLayout();
        }
    }

    private void checkFaqLayout() {
        tab(R.string.more_tab);
        clickText(R.string.finance_help);
        awaitActivity(lk.finora.app.ui.help.FaqActivity.class);
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            var activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .stream().filter(lk.finora.app.ui.help.FaqActivity.class::isInstance).findFirst().orElseThrow();
            assertEquals(1.3f, activity.getResources().getConfiguration().fontScale, 0.01f);
            var content = (android.view.ViewGroup) activity.findViewById(android.R.id.content);
            var scroll = (ScrollView) content.getChildAt(0);
            var labels = (android.view.ViewGroup) scroll.getChildAt(0);
            assertEquals(9, labels.getChildCount());
            for (int index = 0; index < labels.getChildCount(); index++) {
                var label = (android.widget.TextView) labels.getChildAt(index);
                assertEquals(index == 0 || index % 2 == 1, androidx.core.view.ViewCompat.isAccessibilityHeading(label));
                var layout = label.getLayout();
                assertNotNull(layout);
                assertTrue(layout.getHeight() <= label.getHeight() - label.getCompoundPaddingTop()
                        - label.getCompoundPaddingBottom());
                for (int line = 0; line < layout.getLineCount(); line++) {
                    assertEquals(0, layout.getEllipsisCount(line));
                    assertTrue("FAQ label " + index + ", line " + line + " exceeds its text width",
                            layout.getLineMax(line) <= layout.getWidth() + 1f);
                }
            }
        });
        pressBack();
        awaitActivity(MainActivity.class);
    }

    @Test public void moreRoutesRestoreAccessibilityFocus() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Requires TalkBack on the isolated device",
                "true".equals(arguments.getString("finoraCheckBackFocus")));
        var sinhala = new android.content.res.Configuration(app.getResources().getConfiguration());
        sinhala.setLocale(java.util.Locale.forLanguageTag("si"));
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            tab(R.string.more_tab);
            checkBackFocus(R.string.finance_help, lk.finora.app.ui.help.FaqActivity.class, MainActivity.class);
            checkBackFocus(R.string.support_tickets, lk.finora.app.ui.support.SupportActivity.class, MainActivity.class);
            clickText(R.string.language_selector);
            dialogChoice(app.getString(R.string.language_sinhala));
            awaitText(app.createConfigurationContext(sinhala).getString(R.string.more_tab));
            tab(R.string.more_tab);
            checkBackFocus(R.string.finance_help, lk.finora.app.ui.help.FaqActivity.class, MainActivity.class);
            checkBackFocus(R.string.support_tickets, lk.finora.app.ui.support.SupportActivity.class, MainActivity.class);
        }
    }

    @Test public void staffSupportRestoresAccessibilityFocus() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Requires TalkBack and isolated STAFF fixtures",
                "true".equals(arguments.getString("finoraCheckBackFocus"))
                        && "true".equals(arguments.getString("finoraSupportFixtures")));
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInStaff(REVIEWER, SUPPORT_FIXTURE_PASSWORD);
            checkBackFocus(R.string.support_tickets, lk.finora.app.ui.support.SupportActivity.class,
                    lk.finora.app.ui.staff.StaffHomeActivity.class);
        }
    }

    private void checkBackFocus(int label, Class<? extends android.app.Activity> screen,
                               Class<? extends android.app.Activity> origin) {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(
                android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        var manager = app.getSystemService(android.view.accessibility.AccessibilityManager.class);
        long serviceDeadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
        while (!manager.isTouchExplorationEnabled() && SystemClock.elapsedRealtime() < serviceDeadline) {
            SystemClock.sleep(100);
        }
        assertTrue("Touch exploration must be enabled", manager.isTouchExplorationEnabled());
        String expected = app.getString(label);
        var root = automation.getRootInActiveWindow();
        assertNotNull(root);
        var button = root.findAccessibilityNodeInfosByText(expected).stream()
                .filter(node -> node.isClickable() && node.getText() != null
                        && expected.equalsIgnoreCase(node.getText().toString()))
                .findFirst().orElseThrow();
        button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        assertTrue(button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
        awaitActivity(screen);
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP}) {
            assertTrue(automation.injectInputEvent(
                    new android.view.KeyEvent(now, now, action, android.view.KeyEvent.KEYCODE_BACK, 0), true));
        }
        awaitActivity(origin);
        long deadline = SystemClock.elapsedRealtime() + 3000;
        CharSequence actual = null;
        do {
            var focused = automation.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            if (focused != null && app.getPackageName().contentEquals(focused.getPackageName())) {
                actual = focused.getText() != null ? focused.getText() : focused.getContentDescription();
                if (actual != null && expected.equalsIgnoreCase(actual.toString())) return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Expected restored focus " + expected + ", observed " + actual);
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
            signInNewUser(identity);
            tab(R.string.more_tab);
            clickText(R.string.finance_help);
            onView(withText(R.string.faq_title)).check(matches(isDisplayed()));
            onView(withText(R.string.faq_login)).check(matches(isDisplayed()));
            pressBack();
            awaitActivity(MainActivity.class);
            clickText(R.string.account_list_title);
            awaitId("records_add");
            clickId(R.id.records_add);
            enter(R.id.form_name, "Synthetic wallet");
            enter(R.id.account_balance, "0.00");
            clickId(R.id.records_save);
            awaitActivity(lk.finora.app.ui.accounts.AccountListActivity.class);
            awaitText("Synthetic wallet");
            pressBack();
            awaitActivity(MainActivity.class);

            tab(R.string.records_tab);
            clickText(R.string.transaction_open_manual);
            awaitId("form_account");
            select(R.id.form_account, "Synthetic wallet");
            select(R.id.form_type, app.getString(R.string.category_expense));
            select(R.id.form_category, "Food");
            enter(R.id.form_amount, "12.34");
            enter(R.id.form_merchant, MERCHANT);
            clickId(R.id.form_date);
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            clickId(R.id.form_save);
            awaitText(app.getString(R.string.records_tab));
            tab(R.string.home_tab);
            awaitText("Expenses: LKR 12.34");

            tab(R.string.plan_tab);
            clickText(R.string.budget_list_title);
            awaitActivity(lk.finora.app.ui.budgets.BudgetListActivity.class);
            awaitText(app.getString(R.string.records_empty));
            clickId(R.id.records_add);
            awaitActivity(lk.finora.app.ui.budgets.BudgetFormActivity.class);
            select(R.id.form_kind, "Food");
            enter(R.id.budget_limit, "100.00");
            clickId(R.id.records_save);
            awaitText(app.getString(R.string.budget_left, "12.34", "100.00", "87.66"));
            pressBack();
            awaitActivity(MainActivity.class);

            tab(R.string.records_tab);
            clickText(R.string.transaction_title);
            String edit = app.getString(R.string.records_edit_named, "");
            await(By.descContains(MERCHANT));
            onView(allOf(withContentDescription(startsWith(edit)),
                    withContentDescription(containsString(MERCHANT)))).perform(activateControl());
            awaitActivity(lk.finora.app.ui.transactions.TransactionFormActivity.class);
            awaitId("form_amount");
            enter(R.id.form_amount, "15.17");
            clickId(R.id.form_save);
            await(By.textContains("15.17"));
            String remove = app.getString(R.string.records_remove_named, "");
            onView(allOf(withContentDescription(startsWith(remove)),
                    withContentDescription(containsString(MERCHANT)))).perform(activateControl());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            awaitText(app.getString(R.string.records_empty));
            pressBack();
            awaitActivity(MainActivity.class);
            clickText(R.string.sms_access_title);
            onView(withText(R.string.sms_manual_entry)).check(matches(isDisplayed()));
            clickText(R.string.sms_no_thanks);
            assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));
            awaitText(app.getString(R.string.records_tab));
        }
    }

    @Test public void diagnosticRequiresConfirmationAfterCancel() {
        String identity = UUID.randomUUID().toString().replace("-", "");
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);
            tab(R.string.records_tab);
            clickText(R.string.sms_access_title);
            clickText(R.string.sms_paste);
            awaitId("sms_paste_action");
            var instrumentation = InstrumentationRegistry.getInstrumentation();
            instrumentation.runOnMainSync(() -> app.getSystemService(ClipboardManager.class)
                    .setPrimaryClip(ClipData.newPlainText("Test message", "Synthetic diagnostic fixture")));
            try {
                onView(withId(R.id.sms_paste_action)).perform(reveal(), activateControl());
                awaitText(app.getString(R.string.sms_report_problem));
                onView(withId(R.id.sms_diagnostic_report)).perform(reveal(), activateControl());
                onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(
                        withText(containsString("FORMAT_UNRECOGNIZED"))));
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(activateControl());
                onView(withId(R.id.sms_diagnostic_report)).check(matches(isEnabled()));
                onView(withId(R.id.sms_diagnostic_report)).perform(reveal(), activateControl());
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
                awaitText(app.getString(R.string.sms_report_sent));
            } finally {
                instrumentation.runOnMainSync(() -> {
                    var clipboard = app.getSystemService(ClipboardManager.class);
                    if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip();
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                });
            }
            pressBack();
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);
        }
    }

    @Test public void approvedBilingualReplySurvivesCatalogRetirement() {
        org.junit.Assume.assumeTrue("Requires the isolated acceptance database with two disposable staff accounts",
                "true".equals(InstrumentationRegistry.getArguments().getString("finoraSupportFixtures")));
        String identity = UUID.randomUUID().toString().replace("-", "");
        String englishReply = "Check your connection and try signing in again.";
        String sinhalaReply = "ඔබේ අන්තර්ජාල සම්බන්ධතාව පරීක්ෂා කර නැවත පිවිසීමට උත්සාහ කරන්න.";
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);
            tab(R.string.more_tab);
            clickText(R.string.support_tickets);
            awaitActivity(lk.finora.app.ui.support.SupportActivity.class);
            awaitButton(R.string.support_create);
            onView(withText(R.string.support_catalog)).check(doesNotExist());
            clickText(R.string.support_create);
            dialogChoice(app.getString(R.string.support_sign_in));
            confirm();
            await(By.textContains(app.getString(R.string.support_sign_in) + " · "
                    + app.getString(R.string.sms_diagnostic_open)));
            pressBack();
            awaitActivity(MainActivity.class);
            signOut();

            signInStaff(DRAFTER, SUPPORT_FIXTURE_PASSWORD);
            openCatalog();
            clickText(R.string.support_draft);
            dialogChoice(app.getString(R.string.support_sign_in));
            confirm();
            awaitButton(R.string.support_approve);
            clickText(R.string.support_approve);
            confirm();
            awaitText(app.getString(R.string.records_missing));
            pressBack();
            awaitActivity(lk.finora.app.ui.staff.StaffHomeActivity.class);
            signOut();

            signInStaff(REVIEWER, SUPPORT_FIXTURE_PASSWORD);
            openCatalog();
            clickText(R.string.support_approve);
            confirm();
            await(By.textContains(app.getString(R.string.support_sign_in) + " · "
                    + app.getString(R.string.support_active)));
            clickText(R.string.support_tickets);
            awaitButton(R.string.support_claim);
            clickText(R.string.support_claim);
            confirm();
            awaitButton(R.string.support_reply);
            sendReply(englishReply);
            sendReply(sinhalaReply);
            clickText(R.string.support_catalog);
            awaitButton(R.string.support_retire);
            clickText(R.string.support_retire);
            confirm();
            await(By.textContains(app.getString(R.string.support_sign_in) + " · "
                    + app.getString(R.string.support_retired)));
            clickText(R.string.support_draft);
            dialogChoice(app.getString(R.string.support_sign_in));
            confirm();
            await(By.textContains(app.getString(R.string.support_sign_in) + " · "
                    + app.getString(R.string.support_draft_state)));
            await(By.textContains(app.getString(R.string.support_sign_in) + " · "
                    + app.getString(R.string.support_retired)));
            pressBack();
            awaitActivity(lk.finora.app.ui.staff.StaffHomeActivity.class);
            signOut();

            enter(R.id.email, "t51-" + identity + "@example.test");
            enter(R.id.password, "Test-" + identity + "-9");
            clickId(R.id.sign_in);
            awaitActivity(MainActivity.class);
            tab(R.string.more_tab);
            clickText(R.string.support_tickets);
            awaitButton(R.string.support_replies);
            clickText(R.string.support_replies);
            awaitText(englishReply);
            awaitText(sinhalaReply);
        }
    }

    private void openCatalog() {
        clickText(R.string.support_tickets);
        awaitActivity(lk.finora.app.ui.support.SupportActivity.class);
        awaitButton(R.string.support_catalog);
        clickText(R.string.support_catalog);
        awaitButton(R.string.support_draft);
    }

    private void sendReply(String text) {
        clickText(R.string.support_reply);
        dialogChoice(app.getString(R.string.support_sign_in));
        dialogChoice(text);
        onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(withText(text)));
        confirm();
        awaitText(app.getString(R.string.sms_diagnostic_updated));
    }

    private void signInStaff(String email, String password) {
        enter(R.id.email, email);
        enter(R.id.password, password);
        clickId(R.id.sign_in);
        awaitActivity(lk.finora.app.ui.staff.StaffHomeActivity.class);
    }

    private void signOut() {
        clickText(R.string.sign_out);
        awaitActivity(LoginActivity.class);
    }

    private static void confirm() {
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
    }

    private static void dialogChoice(String label) {
        onView(isAssignableFrom(android.widget.ListView.class)).inRoot(isDialog()).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(android.widget.ListView.class); }
            @Override public String getDescription() { return "select the visible dialog option"; }
            @Override public void perform(UiController controller, View view) {
                var list = (android.widget.ListView) view;
                for (int index = 0; index < list.getCount(); index++) {
                    if (label.equals(String.valueOf(list.getItemAtPosition(index)))) {
                        list.performItemClick(list.getChildAt(index - list.getFirstVisiblePosition()), index,
                                list.getItemIdAtPosition(index));
                        controller.loopMainThreadUntilIdle();
                        return;
                    }
                }
                fail("Expected dialog option is unavailable");
            }
        });
    }

    private void signInNewUser(String identity) {
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
    }

    private void awaitId(String id) { await(By.res(app.getPackageName(), id)); }

    private static void awaitActivity(Class<? extends android.app.Activity> expected) {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var resumed = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
        do {
            instrumentation.runOnMainSync(() -> resumed.set(ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).stream()
                    .anyMatch(activity -> expected.isInstance(activity) && activity.hasWindowFocus())));
            if (resumed.get()) return;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Expected resumed activity: " + expected.getSimpleName());
    }

    private void awaitText(String text) { await(By.text(text)); }
    private void awaitButton(int label) {
        await(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(app.getString(label)),
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)));
    }
    private void await(BySelector selector) {
        assertTrue("Expected FINORA screen state did not appear", device.wait(Until.hasObject(selector), UI_TIMEOUT_MS));
    }

    private static void tab(int label) {
        onView(allOf(withText(label), isAssignableFrom(RadioButton.class))).perform(activateControl());
    }

    private static void clickText(int label) {
        onView(allOf(withText(label), isClickable())).perform(reveal(), activateControl());
    }

    private static void clickId(int id) {
        onView(withId(id)).perform(reveal(), activateControl());
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
                long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
                do {
                    for (int i = 0; i < spinner.getCount(); i++) {
                        if (label.equals(String.valueOf(spinner.getItemAtPosition(i)))) {
                            spinner.setSelection(i);
                            controller.loopMainThreadUntilIdle();
                            return;
                        }
                    }
                    controller.loopMainThreadForAtLeast(50);
                } while (SystemClock.elapsedRealtime() < deadline);
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
                if (isDisplayingAtLeast(90).matches(view)) return;
                for (var parent = view.getParent(); parent != null; parent = parent.getParent()) {
                    if (parent instanceof ScrollView scroll) {
                        Rect bounds = new Rect(0, 0, view.getWidth(), view.getHeight());
                        scroll.offsetDescendantRectToMyCoords(view, bounds);
                        scroll.scrollTo(scroll.getScrollX(), bounds.centerY() - scroll.getHeight() / 2);
                        controller.loopMainThreadUntilIdle();
                        break;
                    }
                }
                assertTrue("The form control could not be brought into view", isDisplayed().matches(view));
            }
        };
    }
}
