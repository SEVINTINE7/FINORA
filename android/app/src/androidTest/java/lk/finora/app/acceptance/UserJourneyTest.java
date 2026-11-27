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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.api.AccountDto;
import lk.finora.app.data.api.GoalDto;
import lk.finora.app.data.api.PlanningTransportFixture;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.CategoryRepository;
import lk.finora.app.data.repo.GoalRepository;
import lk.finora.app.data.repo.TransactionRepository;
import lk.finora.app.ui.planning.PlanningActivity;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
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
import static androidx.test.espresso.action.ViewActions.click;
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
            assertFalse("Handset mode requires physical hardware", emulator);
            assertTrue("Handset mode requires an isolated acceptance package", isAcceptancePackage());
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

    private boolean isAcceptancePackage() {
        return "lk.finora.app.acceptance".equals(app.getPackageName())
                || "lk.finora.app.t57handset".equals(app.getPackageName());
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
        org.junit.Assume.assumeTrue("Explicit isolated layout probe only",
                ("true".equals(arguments.getString("finoraIsolatedHandset")) || "true".equals(arguments.getString("finoraIsolatedEmulator")))
                        && "true".equals(arguments.getString("finoraCheckFaqLayout")));
        float scale = Float.parseFloat(arguments.getString("finoraExpectedFontScale", "1.3"));
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            chooseTestLanguage("en");
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            var problems = new java.util.ArrayList<String>();
            checkFaqLayout("en", scale, problems);
            tab(R.string.more_tab);
            clickText(R.string.language_selector);
            dialogChoice(app.getString(R.string.language_sinhala));
            var sinhala = new android.content.res.Configuration(app.getResources().getConfiguration());
            sinhala.setLocale(java.util.Locale.forLanguageTag("si"));
            await(By.desc(app.createConfigurationContext(sinhala).getString(R.string.more_tab)));
            checkFaqLayout("si", scale, problems);
            assertTrue(String.join("\n", problems), problems.isEmpty());
        }
    }

    @Test public void bilingualUserRoutesFitText() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Explicit isolated layout walkthrough only",
                isTextFitTarget(arguments) && "true".equals(arguments.getString("finoraCheckUserTextFit")));
        float scale = Float.parseFloat(arguments.getString("finoraExpectedFontScale"));
        var problems = new java.util.ArrayList<String>();
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            chooseTestLanguage("en");
            checkScreenText(LoginActivity.class, "en", scale, problems);
            checkRecoveryText("en", scale, problems);
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            checkUserRoutes("en", scale, problems);
            tab(R.string.more_tab);
            clickText(R.string.language_selector);
            dialogChoice(app.getString(R.string.language_sinhala));
            var sinhala = new android.content.res.Configuration(app.getResources().getConfiguration());
            sinhala.setLocale(java.util.Locale.forLanguageTag("si"));
            await(By.desc(app.createConfigurationContext(sinhala).getString(R.string.more_tab)));
            checkUserRoutes("si", scale, problems);
            signOut();
            checkScreenText(LoginActivity.class, "si", scale, problems);
            checkRecoveryText("si", scale, problems);
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test public void bilingualRoleRoutesFitText() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Explicit isolated layout role fixtures only",
                isTextFitTarget(arguments) && "true".equals(arguments.getString("finoraCheckRoleTextFit")));
        float scale = Float.parseFloat(arguments.getString("finoraExpectedFontScale"));
        var problems = new java.util.ArrayList<String>();
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            chooseTestLanguage("en");
            signInAdmin("delivery-admin@example.test", "Synthetic9-delivery-admin");
            checkRoleRoutes(true, "en", scale, problems);
            clickText(R.string.language_selector);
            dialogChoice(app.getString(R.string.language_sinhala));
            checkRoleRoutes(true, "si", scale, problems);
            signOut();
            signInStaff(REVIEWER, SUPPORT_FIXTURE_PASSWORD);
            checkRoleRoutes(false, "si", scale, problems);
            clickText(R.string.language_selector);
            dialogChoice(app.getString(R.string.language_english));
            checkRoleRoutes(false, "en", scale, problems);
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    private static boolean isTextFitTarget(android.os.Bundle arguments) {
        return "true".equals(arguments.getString("finoraIsolatedEmulator"))
                || ("true".equals(arguments.getString("finoraIsolatedHandset"))
                        && "true".equals(arguments.getString("finoraT39Handset")));
    }

    @Test public void bilingualRegistrationTextFits() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Explicit isolated emulator registration text check only",
                "true".equals(arguments.getString("finoraIsolatedEmulator"))
                        && "true".equals(arguments.getString("finoraCheckRegistrationTextFit")));
        float scale = Float.parseFloat(arguments.getString("finoraExpectedFontScale"));
        var problems = new java.util.ArrayList<String>();
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            for (String language : new String[]{"en", "si"}) {
                chooseTestLanguage(language);
                awaitActivity(LoginActivity.class);
                clickId(R.id.create_account);
                checkScreenText(RegisterActivity.class, language, scale, problems);
                clickId(R.id.register_back);
                awaitActivity(LoginActivity.class);
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test public void stageAdminTalkBackFocus() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Explicit isolated listener session only",
                isTextFitTarget(arguments) && "true".equals(arguments.getString("finoraAdminListener")));
        var automation = instrumentation.getUiAutomation(
                android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        var manager = app.getSystemService(android.view.accessibility.AccessibilityManager.class);
        long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
        while (!manager.isTouchExplorationEnabled() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100);
        }
        assertTrue("TalkBack touch exploration must be enabled", manager.isTouchExplorationEnabled());
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            chooseTestLanguage("en");
            signInAdmin("delivery-admin@example.test", "Synthetic9-delivery-admin");
            for (String language : new String[]{"en", "si"}) {
                chooseTestLanguage(language);
                awaitActivity(lk.finora.app.ui.admin.AdminHomeActivity.class);
                var config = new android.content.res.Configuration(app.getResources().getConfiguration());
                config.setLocale(java.util.Locale.forLanguageTag(language));
                var localized = app.createConfigurationContext(config);
                SystemClock.sleep(2_500);
                automation = instrumentation.getUiAutomation(
                        android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
                for (int id : new int[]{R.string.admin_manage_users, R.string.admin_default_categories,
                        R.string.staff_manage, R.string.sms_diagnostic_admin}) {
                    String label = localized.getString(id);
                    var root = automation.getRootInActiveWindow();
                    assertNotNull(root);
                    assertEquals(app.getPackageName(), root.getPackageName().toString());
                    var node = root.findAccessibilityNodeInfosByText(label).stream()
                            .filter(item -> item.isVisibleToUser() && item.isClickable() && item.getText() != null
                                    && label.equalsIgnoreCase(item.getText().toString()))
                            .findFirst().orElseThrow();
                    if (node.isAccessibilityFocused()) {
                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
                        node.refresh();
                    }
                    node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                    assertTrue(node.refresh());
                    assertTrue(node.isAccessibilityFocused());
                    android.util.Log.i("FinoraTextFitTest", "Listener focus " + language + ": " + label);
                    SystemClock.sleep(4_500);
                }
            }
        }
    }

    /** T34.5: alert switches load and save through /me; refusing notifications keeps the app working. */
    @Test public void alertSwitchesSaveAndRefusedNotificationsKeepTheAppWorking() {
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            tab(R.string.more_tab);
            clickText(R.string.settings_title);
            awaitActivity(lk.finora.app.ui.settings.SettingsActivity.class);
            for (int id : new int[]{R.id.settings_alert_budget, R.id.settings_alert_bill, R.id.settings_alert_goal}) {
                awaitEnabled(id);
                onView(withId(id)).check(matches(isChecked()));
            }
            clickId(R.id.settings_alert_bill);
            awaitEnabled(R.id.settings_alert_bill);
            onView(withId(R.id.settings_alert_bill)).check(matches(not(isChecked())));

            // A fresh screen reads the saved values back from the server
            pressBack();
            awaitActivity(MainActivity.class);
            clickText(R.string.settings_title);
            awaitActivity(lk.finora.app.ui.settings.SettingsActivity.class);
            awaitEnabled(R.id.settings_alert_bill);
            onView(withId(R.id.settings_alert_budget)).check(matches(isChecked()));
            onView(withId(R.id.settings_alert_bill)).check(matches(not(isChecked())));
            onView(withId(R.id.settings_alert_goal)).check(matches(isChecked()));

            if (Build.VERSION.SDK_INT >= 33) {
                assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));
                onView(withId(R.id.settings_notifications_status)).perform(reveal()).check(matches(isDisplayed()));
                clickId(R.id.settings_notifications_allow);
                clickSmsPermissionChoice("deny");
                awaitActivity(lk.finora.app.ui.settings.SettingsActivity.class);
                assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));
                onView(withId(R.id.settings_notifications_status)).perform(reveal()).check(matches(isDisplayed()));
            }

            // Still fully usable after the refusal: switches save and other screens open
            awaitEnabled(R.id.settings_alert_goal);
            clickId(R.id.settings_alert_goal);
            awaitEnabled(R.id.settings_alert_goal);
            onView(withId(R.id.settings_alert_goal)).check(matches(not(isChecked())));
            pressBack();
            awaitActivity(MainActivity.class);
            tab(R.string.plan_tab);
            clickText(R.string.budget_list_title);
            awaitActivity(lk.finora.app.ui.budgets.BudgetListActivity.class);
            awaitText(app.getString(R.string.records_empty));
        }
    }

    private static void awaitEnabled(int id) {
        long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
        while (true) {
            try {
                onView(withId(id)).perform(reveal()).check(matches(isEnabled()));
                return;
            } catch (RuntimeException | AssertionError notYet) {
                if (SystemClock.elapsedRealtime() > deadline) throw notYet;
                SystemClock.sleep(200);
            }
        }
    }

    /** Opt-in: leaves a signed-in USER with one budget, bill and goal for a human TalkBack listen (D57). */
    @Test public void stagePlanningTalkBackFixture() {
        org.junit.Assume.assumeTrue("Opt-in planning fixture only",
                "true".equals(InstrumentationRegistry.getArguments().getString("finoraStagePlanning")));
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            tab(R.string.plan_tab);
            clickText(R.string.budget_list_title);
            awaitActivity(lk.finora.app.ui.budgets.BudgetListActivity.class);
            clickId(R.id.records_add);
            awaitActivity(lk.finora.app.ui.budgets.BudgetFormActivity.class);
            select(R.id.form_kind, "Food");
            enter(R.id.budget_limit, "100.00");
            clickId(R.id.records_save);
            awaitText(app.getString(R.string.budget_state_ok));
            pressBack();
            awaitActivity(MainActivity.class);
            clickText(R.string.bill_list_title);
            awaitActivity(lk.finora.app.ui.bills.BillListActivity.class);
            clickId(R.id.records_add);
            awaitActivity(lk.finora.app.ui.bills.BillFormActivity.class);
            enter(R.id.form_name, "Synthetic rent");
            enter(R.id.bill_amount, "5000.00");
            enter(R.id.bill_due_day, "31");
            clickId(R.id.records_save);
            awaitText(app.getString(R.string.bill_unpaid));
            pressBack();
            awaitActivity(MainActivity.class);
            clickText(R.string.goal_list_title);
            awaitActivity(lk.finora.app.ui.goals.GoalListActivity.class);
            clickId(R.id.records_add);
            awaitActivity(lk.finora.app.ui.goals.GoalFormActivity.class);
            enter(R.id.form_name, "Synthetic laptop");
            enter(R.id.goal_target, "1000.00");
            enter(R.id.goal_target_date,
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).plusDays(60).toString());
            clickId(R.id.records_save);
            awaitActivity(lk.finora.app.ui.goals.GoalListActivity.class);
            onView(withContentDescription(app.getString(R.string.goal_open_named, "Synthetic laptop")))
                    .perform(activateControl());
            awaitActivity(lk.finora.app.ui.goals.GoalDetailActivity.class);
            enter(R.id.contribution_amount, "250.00");
            clickId(R.id.contribution_add);
            expandActions("LKR 250.00");
            keepAccessibilityFixture = true;
        }
    }

    /**
     * T17.3 / T18.4 / T19.3 emulator acceptance (D57): list and form flows, 409 on a stale budget form,
     * rotation keeps typed values, TalkBack labels in the accessibility tree, Sinhala rendering.
     */
    @Test public void planningScreensBudgetsBillsGoals() throws Exception {
        String identity = UUID.randomUUID().toString().replace("-", "");
        var colombo = java.time.ZoneId.of("Asia/Colombo");
        String month = java.time.YearMonth.now(colombo).toString();
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);

            // T17.3 budgets: add, progress label, rotation keeps the typed limit, edit
            tab(R.string.plan_tab);
            clickText(R.string.budget_list_title);
            awaitActivity(lk.finora.app.ui.budgets.BudgetListActivity.class);
            awaitText(app.getString(R.string.records_empty));
            clickId(R.id.records_add);
            awaitActivity(lk.finora.app.ui.budgets.BudgetFormActivity.class);
            select(R.id.form_kind, "Food");
            enter(R.id.budget_limit, "100.00");
            device.setOrientationLeft();
            awaitActivity(lk.finora.app.ui.budgets.BudgetFormActivity.class);
            onView(withId(R.id.budget_limit)).check(matches(withText("100.00")));
            device.setOrientationNatural();
            awaitActivity(lk.finora.app.ui.budgets.BudgetFormActivity.class);
            clickId(R.id.records_save);
            // T63.2: the card shows "Rs. 0.00 of Rs. 100.00" and speaks the exact LKR sentence; tap edits.
            String left = app.getString(R.string.budget_left, "0.00", "100.00", "100.00");
            String rs = app.getString(R.string.form_rupees_prefix).trim() + " ";
            awaitText(app.getString(R.string.budget_used_of, rs + "0.00", rs + "100.00"));
            awaitText(app.getString(R.string.budget_state_ok));
            await(By.desc("Food. " + left));
            onView(withContentDescription("Food. " + left)).perform(activateControl());
            awaitActivity(lk.finora.app.ui.budgets.BudgetFormActivity.class);
            onView(withId(R.id.budget_limit)).check(matches(withText("100.00")));
            enter(R.id.budget_limit, "250.00");
            clickId(R.id.records_save);
            String raised = "Food. " + app.getString(R.string.budget_left, "0.00", "250.00", "250.00");
            await(By.desc(raised));
            device.setOrientationLeft();
            awaitActivity(lk.finora.app.ui.budgets.BudgetListActivity.class);
            onView(withContentDescription(raised)).perform(reveal()).check(matches(isDisplayed()));
            device.setOrientationNatural();
            pressBack();
            awaitActivity(MainActivity.class);
        }

        // T17.3 409: a form opened before Food was taken (another device) gets the duplicate message
        try (var ignored = ActivityScenario.launch(new android.content.Intent(app,
                lk.finora.app.ui.budgets.BudgetFormActivity.class)
                .putExtra("budget_month", month).putExtra("budget_taken_categories", new long[0]))) {
            awaitActivity(lk.finora.app.ui.budgets.BudgetFormActivity.class);
            select(R.id.form_kind, "Food");
            enter(R.id.budget_limit, "300.00");
            clickId(R.id.records_save);
            awaitText(app.getString(R.string.budget_duplicate));
            awaitActivity(lk.finora.app.ui.budgets.BudgetFormActivity.class);
        }

        // T18.4 bills: due day 31 shows the month's last day, paid toggle updates the summary and status
        String due = java.time.YearMonth.now(colombo).atEndOfMonth().format(java.time.format.DateTimeFormatter
                .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault()));
        try (var ignored = ActivityScenario.launch(lk.finora.app.ui.bills.BillListActivity.class)) {
            awaitText(app.getString(R.string.records_empty));
            clickId(R.id.records_add);
            awaitActivity(lk.finora.app.ui.bills.BillFormActivity.class);
            enter(R.id.form_name, "Synthetic rent");
            enter(R.id.bill_amount, "5000.00");
            enter(R.id.bill_due_day, "31");
            device.setOrientationLeft();
            awaitActivity(lk.finora.app.ui.bills.BillFormActivity.class);
            onView(withId(R.id.form_name)).check(matches(withText("Synthetic rent")));
            onView(withId(R.id.bill_due_day)).check(matches(withText("31")));
            device.setOrientationNatural();
            awaitActivity(lk.finora.app.ui.bills.BillFormActivity.class);
            clickId(R.id.records_save);
            awaitText(app.getString(R.string.bill_details, "5000.00", due));
            awaitText(app.getString(R.string.bill_unpaid));
            awaitText(app.getString(R.string.bill_summary, "5000.00", "0.00", "5000.00"));
            String paidLabel = app.getString(R.string.bill_paid_named, "Synthetic rent");
            onView(withContentDescription(paidLabel)).perform(activateControl());
            awaitText(app.getString(R.string.bill_summary, "5000.00", "5000.00", "0.00"));
            awaitText(app.getString(R.string.bill_paid));
            onView(withContentDescription(paidLabel)).check(matches(isChecked()));
            onView(withContentDescription(paidLabel)).perform(activateControl());
            awaitText(app.getString(R.string.bill_summary, "5000.00", "0.00", "5000.00"));
            onView(withContentDescription(paidLabel)).check(matches(not(isChecked())));
            await(By.desc(app.getString(R.string.records_actions_named, "Synthetic rent")));
        }

        // T19.3 goals: list → detail, add/remove contributions, progress, rotation on detail
        String target = java.time.LocalDate.now(colombo).plusDays(60).toString();
        try (var ignored = ActivityScenario.launch(lk.finora.app.ui.goals.GoalListActivity.class)) {
            awaitText(app.getString(R.string.records_empty));
            clickId(R.id.records_add);
            awaitActivity(lk.finora.app.ui.goals.GoalFormActivity.class);
            enter(R.id.form_name, "Synthetic laptop");
            enter(R.id.goal_target, "1000.00");
            enter(R.id.goal_target_date, target);
            clickId(R.id.records_save);
            awaitActivity(lk.finora.app.ui.goals.GoalListActivity.class);
            await(By.textContains(app.getString(R.string.goal_progress_text, "0.00", "1000.00", "1000.00", 0)));
            onView(withContentDescription(app.getString(R.string.goal_open_named, "Synthetic laptop")))
                    .perform(activateControl());
            awaitActivity(lk.finora.app.ui.goals.GoalDetailActivity.class);
            awaitText(app.getString(R.string.goal_no_contributions));
            enter(R.id.contribution_amount, "250.00");
            clickId(R.id.contribution_add);
            await(By.textContains(app.getString(R.string.goal_progress_text, "250.00", "1000.00", "750.00", 25)));
            enter(R.id.contribution_amount, "100.00");
            clickId(R.id.contribution_add);
            await(By.textContains(app.getString(R.string.goal_progress_text, "350.00", "1000.00", "650.00", 35)));
            device.setOrientationLeft();
            awaitActivity(lk.finora.app.ui.goals.GoalDetailActivity.class);
            await(By.textContains(app.getString(R.string.goal_progress_text, "350.00", "1000.00", "650.00", 35)));
            device.setOrientationNatural();
            awaitActivity(lk.finora.app.ui.goals.GoalDetailActivity.class);
            String remove = app.getString(R.string.records_remove_named, "");
            expandActions("LKR 250.00");
            onView(allOf(withContentDescription(startsWith(remove)),
                    withContentDescription(containsString("LKR 250.00")))).perform(reveal(), activateControl());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            await(By.textContains(app.getString(R.string.goal_progress_text, "100.00", "1000.00", "900.00", 10)));
            pressBack();
            awaitActivity(lk.finora.app.ui.goals.GoalListActivity.class);
            await(By.textContains(app.getString(R.string.goal_progress_text, "100.00", "1000.00", "900.00", 10)));
        }

        // Sinhala: switch while the budget list is open (as the Settings picker does), then the other lists
        var configuration = new android.content.res.Configuration(app.getResources().getConfiguration());
        configuration.setLocale(java.util.Locale.forLanguageTag("si"));
        var si = app.createConfigurationContext(configuration);
        try (var ignored = ActivityScenario.launch(lk.finora.app.ui.budgets.BudgetListActivity.class)) {
            awaitText(app.getString(R.string.budget_left, "0.00", "250.00", "250.00"));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> LocaleHelper.apply("SI"));
            awaitText(si.getString(R.string.budget_left, "0.00", "250.00", "250.00"));
            awaitText(si.getString(R.string.budget_state_ok));
        }
        try (var ignored = ActivityScenario.launch(lk.finora.app.ui.bills.BillListActivity.class)) {
            awaitText(si.getString(R.string.bill_summary, "5000.00", "0.00", "5000.00"));
            awaitText(si.getString(R.string.bill_unpaid));
            await(By.desc(si.getString(R.string.bill_paid_named, "Synthetic rent")));
        }
        try (var ignored = ActivityScenario.launch(lk.finora.app.ui.goals.GoalListActivity.class)) {
            await(By.textContains(si.getString(R.string.goal_progress_text, "100.00", "1000.00", "900.00", 10)));
            await(By.desc(si.getString(R.string.goal_open_named, "Synthetic laptop")));
        } finally {
            device.setOrientationNatural();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> LocaleHelper.apply("EN"));
        }
    }

    private void chooseTestLanguage(String language) {
        var config = new android.content.res.Configuration(app.getResources().getConfiguration());
        config.setLocale(java.util.Locale.ENGLISH);
        String english = app.createConfigurationContext(config).getString(R.string.language_selector);
        config.setLocale(java.util.Locale.forLanguageTag("si"));
        var sinhala = app.createConfigurationContext(config);
        onView(anyOf(withText(english), withText(sinhala.getString(R.string.language_selector))))
                .perform(reveal(), activateControl());
        dialogChoice(sinhala.getString("en".equals(language) ? R.string.language_english : R.string.language_sinhala));
    }

    private void checkRecoveryText(String language, float scale, java.util.List<String> problems) {
        clickId(R.id.forgot_password);
        checkScreenText(lk.finora.app.ui.login.PasswordResetActivity.class, language, scale, problems);
        typeFixture(onView(withHint(R.string.admin_new_email)), "synthetic-layout@example.test");
        clickText(R.string.reset_request);
        awaitText(app.getString(R.string.reset_check_mail));
        checkScreenText(lk.finora.app.ui.login.PasswordResetActivity.class, language, scale, problems);
        pressBack();
        awaitActivity(LoginActivity.class);
    }

    private void checkRoleRoutes(boolean admin, String language, float scale, java.util.List<String> problems) {
        var home = admin ? lk.finora.app.ui.admin.AdminHomeActivity.class
                : lk.finora.app.ui.staff.StaffHomeActivity.class;
        var diagnostics = lk.finora.app.ui.support.DiagnosticQueueActivity.class;
        checkScreenText(home, language, scale, problems);
        if (admin) {
            for (int label : new int[]{R.string.admin_manage_users, R.string.admin_default_categories,
                    R.string.admin_manage_admins, R.string.staff_manage}) {
                clickText(label);
                checkScreenText(label == R.string.staff_manage ? lk.finora.app.ui.staff.StaffAccessActivity.class
                        : lk.finora.app.ui.admin.AdminDirectoryActivity.class, language, scale, problems);
                pressBack();
                awaitActivity(home);
            }
        } else {
            clickText(R.string.support_tickets);
            checkScreenText(lk.finora.app.ui.support.SupportActivity.class, language, scale, problems);
            pressBack();
            awaitActivity(home);
        }
        clickText(admin ? R.string.sms_diagnostic_admin : R.string.sms_diagnostic_queue);
        checkScreenText(diagnostics, language, scale, problems);
        if (admin) {
            clickText(R.string.sms_format_sample_check);
            checkScreenText(lk.finora.app.ui.support.FormatSampleActivity.class, language, scale, problems);
            pressBack();
            awaitActivity(diagnostics);
        }
        pressBack();
        awaitActivity(home);
    }

    private void checkUserRoutes(String language, float scale, java.util.List<String> problems) {
        tab(R.string.home_tab);
        checkScreenText(MainActivity.class, language, scale, problems);
        tab(R.string.records_tab);
        checkScreenText(MainActivity.class, language, scale, problems);
        clickId(R.id.shell_add_transaction);
        checkScreenText(lk.finora.app.ui.transactions.TransactionFormActivity.class, language, scale, problems);
        pressBack();
        awaitActivity(MainActivity.class);
        openImport(R.string.sms_csv_import);
        checkScreenText(lk.finora.app.ui.importer.CsvImportActivity.class, language, scale, problems);
        pressBack();
        awaitActivity(MainActivity.class);
        openImport(R.string.sms_access_title);
        checkScreenText(lk.finora.app.ui.sms.SmsAccessActivity.class, language, scale, problems);
        clickId(R.id.sms_paste_entry_action);
        checkScreenText(lk.finora.app.ui.sms.PasteSmsActivity.class, language, scale, problems);
        pressBack();
        awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);
        pressBack();
        awaitActivity(MainActivity.class);

        tab(R.string.plan_tab);
        checkScreenText(MainActivity.class, language, scale, problems);
        checkRoute(R.string.planning_guide, PlanningActivity.class, null, language, scale, problems);
        checkRoute(R.string.planning_simulator, PlanningActivity.class, null, language, scale, problems);
        checkRoute(R.string.budget_list_title, lk.finora.app.ui.budgets.BudgetListActivity.class,
                lk.finora.app.ui.budgets.BudgetFormActivity.class, language, scale, problems);
        checkRoute(R.string.bill_list_title, lk.finora.app.ui.bills.BillListActivity.class,
                lk.finora.app.ui.bills.BillFormActivity.class, language, scale, problems);
        checkRoute(R.string.goal_list_title, lk.finora.app.ui.goals.GoalListActivity.class,
                lk.finora.app.ui.goals.GoalFormActivity.class, language, scale, problems);
        clickText(R.string.goal_list_title);
        awaitActivity(lk.finora.app.ui.goals.GoalListActivity.class);
        if ("en".equals(language)) {
            clickId(R.id.records_add);
            awaitActivity(lk.finora.app.ui.goals.GoalFormActivity.class);
            enter(R.id.form_name, "Synthetic text-fit goal");
            enter(R.id.goal_target, "25000.00");
            clickId(R.id.records_save);
            awaitActivity(lk.finora.app.ui.goals.GoalListActivity.class);
        }
        awaitText("Synthetic text-fit goal");
        clickId(R.id.goal_open);
        checkScreenText(lk.finora.app.ui.goals.GoalDetailActivity.class, language, scale, problems);
        pressBack();
        awaitActivity(lk.finora.app.ui.goals.GoalListActivity.class);
        pressBack();
        awaitActivity(MainActivity.class);

        tab(R.string.insights_tab);
        checkScreenText(MainActivity.class, language, scale, problems);
        checkRoute(R.string.insight_open, lk.finora.app.ui.insights.InsightsActivity.class,
                null, language, scale, problems);
        checkRoute(R.string.advice_title, lk.finora.app.ui.advice.AdviceActivity.class,
                null, language, scale, problems);

        tab(R.string.more_tab);
        checkScreenText(MainActivity.class, language, scale, problems);
        checkRoute(R.string.account_list_title, lk.finora.app.ui.accounts.AccountListActivity.class,
                lk.finora.app.ui.accounts.AccountFormActivity.class, language, scale, problems);
        checkRoute(R.string.category_list_title, lk.finora.app.ui.categories.CategoryListActivity.class,
                lk.finora.app.ui.categories.CategoryFormActivity.class, language, scale, problems);
        checkRoute(R.string.settings_title, lk.finora.app.ui.settings.SettingsActivity.class,
                null, language, scale, problems);
        checkRoute(R.string.history_title, lk.finora.app.ui.history.HistoryActivity.class,
                null, language, scale, problems);
        checkRoute(R.string.support_tickets, lk.finora.app.ui.support.SupportActivity.class,
                null, language, scale, problems);
    }

    private void checkRoute(int label, Class<? extends android.app.Activity> screen,
            Class<? extends android.app.Activity> form, String language, float scale,
            java.util.List<String> problems) {
        clickText(label);
        checkScreenText(screen, language, scale, problems);
        if (form != null) {
            clickId(R.id.records_add);
            checkScreenText(form, language, scale, problems);
            pressBack();
            awaitActivity(screen);
        }
        pressBack();
        awaitActivity(MainActivity.class);
    }

    private static void checkScreenText(Class<? extends android.app.Activity> screen, String language,
            float scale, java.util.List<String> problems) {
        awaitActivity(screen);
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var localeReady = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
        do {
            instrumentation.runOnMainSync(() -> localeReady.set(
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .stream().anyMatch(activity -> screen.isInstance(activity)
                                    && language.equals(activity.getResources().getConfiguration()
                                            .getLocales().get(0).getLanguage()))));
            if (localeReady.get()) break;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertTrue("Expected " + screen.getSimpleName() + " in " + language, localeReady.get());
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            var activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .stream().filter(screen::isInstance).findFirst().orElseThrow();
            var config = activity.getResources().getConfiguration();
            assertEquals(language, config.getLocales().get(0).getLanguage());
            assertEquals(scale, config.fontScale, 0.01f);
            String night = InstrumentationRegistry.getArguments().getString("finoraExpectedNightMode");
            if (night != null) {
                int expected = "yes".equals(night) ? android.content.res.Configuration.UI_MODE_NIGHT_YES
                        : android.content.res.Configuration.UI_MODE_NIGHT_NO;
                assertEquals(expected, config.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
            }
            String context = screen.getSimpleName() + "/" + language + "/" + scale;
            assertTrue("No visible text checked on " + context,
                    checkVisibleText(activity.findViewById(android.R.id.content), context, problems) > 0);
            android.util.Log.i("FinoraTextFitTest", "Checked " + context);
        });
    }

    private static int checkVisibleText(View view, String context, java.util.List<String> problems) {
        if (!view.isShown()) return 0;
        int count = 0;
        if (view instanceof TextView text && !(text instanceof EditText) && text.length() > 0) {
            var layout = text.getLayout();
            if (layout != null && text.getWidth() > 0 && text.getHeight() > 0) {
                count++;
                String label = context + ": " + text.getText();
                if (layout.getHeight() > text.getHeight() - text.getCompoundPaddingTop()
                        - text.getCompoundPaddingBottom() + 1) {
                    problems.add("Text height exceeded on " + label);
                }
                for (int line = 0; line < layout.getLineCount(); line++) {
                    if (layout.getEllipsisCount(line) != 0 || layout.getLineMax(line) > layout.getWidth() + 1f) {
                        problems.add("Text width exceeded on " + label);
                        break;
                    }
                }
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                count += checkVisibleText(group.getChildAt(i), context, problems);
            }
        }
        return count;
    }

    private void checkFaqLayout(String language, float scale, java.util.List<String> problems) {
        tab(R.string.more_tab);
        clickText(R.string.finance_help);
        checkScreenText(lk.finora.app.ui.help.FaqActivity.class, language, scale, problems);
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            var activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .stream().filter(lk.finora.app.ui.help.FaqActivity.class::isInstance).findFirst().orElseThrow();
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
            await(By.desc(app.createConfigurationContext(sinhala).getString(R.string.more_tab)));
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

    @Test public void adminManagesAdminsAndPromotesAnIsolatedSyntheticUser() {
        org.junit.Assume.assumeTrue("Requires the disposable ADMIN fixture",
                "true".equals(InstrumentationRegistry.getArguments().getString("finoraAdminFixture")));
        String identity = UUID.randomUUID().toString().replace("-", "");
        String userEmail = "t51-" + identity + "@example.test";
        String createdEmail = "admin-" + identity + "@example.test";
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);
            signOut();
            signInAdmin("delivery-admin@example.test", "Synthetic9-delivery-admin");
            clickText(R.string.admin_manage_admins);
            awaitButton(R.string.admin_create_admin);
            clickText(R.string.admin_create_admin);
            typeFixture(onView(withHint(R.string.admin_new_email)).inRoot(isDialog()), createdEmail);
            typeFixture(onView(withHint(R.string.admin_new_name)).inRoot(isDialog()), "Synthetic new administrator");
            typeFixture(onView(withHint(R.string.admin_new_password)).inRoot(isDialog()), "Synthetic2026");
            typeFixture(onView(withHint(R.string.admin_confirm_password)).inRoot(isDialog()), "Synthetic2026");
            confirm();
            await(By.textContains(createdEmail));
            pressBack();
            awaitActivity(lk.finora.app.ui.admin.AdminHomeActivity.class);
            signOut();
            signInAdmin(createdEmail, "Synthetic2026");
            signOut();
            signInAdmin("delivery-admin@example.test", "Synthetic9-delivery-admin");
            clickText(R.string.admin_manage_admins);
            await(By.textContains(createdEmail));
            String disable = app.getString(R.string.admin_disable_user) + ". Synthetic new administrator · "
                    + createdEmail + " · " + app.getString(R.string.support_active);
            onView(withContentDescription(disable)).perform(reveal(), activateControl());
            confirm();
            await(By.descContains(createdEmail + " · " + app.getString(R.string.admin_inactive)));
            pressBack();
            awaitActivity(lk.finora.app.ui.admin.AdminHomeActivity.class);
            clickText(R.string.admin_manage_users);
            await(By.textContains(userEmail));
            String makeStaff = app.getString(R.string.admin_make_staff) + ". Synthetic UI user · "
                    + userEmail + " · " + app.getString(R.string.support_active);
            onView(withContentDescription(makeStaff)).perform(reveal(), activateControl());
            typeFixture(onView(withHint(R.string.admin_promote_confirmation)).inRoot(isDialog()), "DELETE_FINANCIAL_DATA");
            confirm();
            assertTrue(device.wait(Until.gone(By.textContains(userEmail)), UI_TIMEOUT_MS));
            pressBack();
            awaitActivity(lk.finora.app.ui.admin.AdminHomeActivity.class);
            signOut();
            signInStaff(userEmail, "Test-" + identity + "-9");
        }
    }

    @Test public void recoveryOpensInBothLanguagesAndRejectsAnInvalidCode() {
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            clickId(R.id.forgot_password);
            awaitActivity(lk.finora.app.ui.login.PasswordResetActivity.class);
            typeFixture(onView(withHint(R.string.admin_new_email)), "unknown-" + UUID.randomUUID() + "@example.test");
            clickText(R.string.reset_request);
            awaitText(app.getString(R.string.reset_check_mail));
            typeFixture(onView(withHint(R.string.reset_code)), "012345");
            typeFixture(onView(withHint(R.string.admin_new_password)), "Synthetic2026");
            onView(allOf(withText(R.string.reset_confirm), isAssignableFrom(android.widget.Button.class)))
                    .perform(reveal(), activateControl());
            awaitText(app.getString(R.string.reset_code_invalid));
            pressBack();
            awaitActivity(LoginActivity.class);
            chooseTestLanguage("si");
            clickId(R.id.forgot_password);
            awaitActivity(lk.finora.app.ui.login.PasswordResetActivity.class);
            var config = new android.content.res.Configuration(app.getResources().getConfiguration());
            config.setLocale(java.util.Locale.forLanguageTag("si"));
            awaitText(app.createConfigurationContext(config).getString(R.string.reset_request));
        }
    }

    @Test public void successfulResetReturnsToLoginWithConfirmation() {
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            clickId(R.id.forgot_password);
            awaitActivity(lk.finora.app.ui.login.PasswordResetActivity.class);
            var instrumentation = InstrumentationRegistry.getInstrumentation();
            instrumentation.runOnMainSync(() -> {
                var active = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).stream().findFirst().orElseThrow();
                assertTrue(active instanceof lk.finora.app.ui.login.PasswordResetActivity);
                var api = (lk.finora.app.data.api.AuthApi) java.lang.reflect.Proxy.newProxyInstance(
                        lk.finora.app.data.api.AuthApi.class.getClassLoader(),
                        new Class<?>[]{lk.finora.app.data.api.AuthApi.class}, (proxy, method, ignored) ->
                                switch (method.getName()) {
                                    case "requestPasswordReset" -> resetReply(202);
                                    case "resetPassword" -> resetReply(204);
                                    default -> throw new AssertionError("Unexpected request in reset fixture");
                                });
                try {
                    var field = active.getClass().getDeclaredField("api");
                    field.setAccessible(true);
                    field.set(active, api);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError("Unable to attach the isolated reset fixture", exception);
                }
            });
            typeFixture(onView(withHint(R.string.admin_new_email)), "synthetic-reset@example.test");
            clickText(R.string.reset_request);
            awaitText(app.getString(R.string.reset_check_mail));
            typeFixture(onView(withHint(R.string.reset_code)), "012345");
            typeFixture(onView(withHint(R.string.admin_new_password)), "Synthetic2026");
            onView(allOf(withText(R.string.reset_confirm), isAssignableFrom(android.widget.Button.class)))
                    .perform(reveal(), activateControl());
            awaitActivity(LoginActivity.class);
            onView(withId(R.id.state_message)).check(matches(withText(R.string.reset_success)))
                    .perform(reveal()).check(matches(isDisplayed()));
            onView(withId(R.id.password)).check(matches(withText("")));
        }
    }

    private static retrofit2.Call<Void> resetReply(int status) {
        return new retrofit2.Call<>() {
            private boolean invoked;
            private boolean cancelled;

            @Override public retrofit2.Response<Void> execute() {
                invoked = true;
                return retrofit2.Response.success(status, null);
            }
            @Override public void enqueue(retrofit2.Callback<Void> callback) {
                if (!cancelled) callback.onResponse(this, execute());
            }
            @Override public boolean isExecuted() { return invoked; }
            @Override public void cancel() { cancelled = true; }
            @Override public boolean isCanceled() { return cancelled; }
            @Override public retrofit2.Call<Void> clone() { return resetReply(status); }
            @Override public okhttp3.Request request() {
                return new okhttp3.Request.Builder().url(BuildConfig.API_BASE_URL).build();
            }
            @Override public okio.Timeout timeout() { return okio.Timeout.NONE; }
        };
    }

    @Test public void adminListsCreatesAndDisablesStaff() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Requires a disposable ADMIN bootstrap",
                "true".equals(arguments.getString("finoraAdminFixture")));
        String adminEmail = "delivery-admin@example.test";
        String adminPassword = "Synthetic9-delivery-admin";
        String staffEmail = "staff-" + UUID.randomUUID() + "@example.test";
        String fullName = "Synthetic provisioning staff";
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInAdmin(adminEmail, adminPassword);
            clickText(R.string.staff_manage);
            awaitButton(R.string.staff_create);
            clickText(R.string.staff_create);
            typeFixture(onView(withHint(R.string.email)).inRoot(isDialog()), staffEmail);
            typeFixture(onView(withHint(R.string.staff_full_name)).inRoot(isDialog()), fullName);
            confirm();
            awaitText(app.getString(R.string.staff_temporary));
            var temporary = new java.util.concurrent.atomic.AtomicReference<String>();
            onView(withId(android.R.id.message)).inRoot(isDialog()).check((view, failure) -> {
                if (failure != null) throw failure;
                String message = ((android.widget.TextView) view).getText().toString();
                int newline = message.indexOf('\n');
                assertTrue("Missing temporary fixture credential", newline == 32);
                temporary.set(message.substring(0, newline));
            });
            confirm();
            awaitText(app.getString(R.string.staff_account_summary, fullName, staffEmail));
            pressBack();
            awaitActivity(lk.finora.app.ui.admin.AdminHomeActivity.class);
            signOut();

            onView(withText(R.string.staff_activate)).check(
                    androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            clickId(R.id.login_sign_in_options);
            dialogChoice(app.getString(R.string.staff_activate));
            awaitActivity(lk.finora.app.ui.staff.StaffAccessActivity.class);
            typeFixture(onView(withHint(R.string.email)), staffEmail);
            typeFixture(onView(withHint(R.string.staff_temporary)), temporary.getAndSet(null));
            typeFixture(onView(withHint(R.string.staff_new_password)), "Synthetic9-replacement");
            clickText(R.string.staff_activate);
            awaitText(app.getString(R.string.staff_activation_done));
            assertNull("Activation must not create a session", sessions.session());
            pressBack();
            awaitActivity(LoginActivity.class);
            signInStaff(staffEmail, "Synthetic9-replacement");
            signOut();

            signInAdmin(adminEmail, adminPassword);
            clickText(R.string.staff_manage);
            String disable = app.getString(R.string.staff_account_summary,
                    app.getString(R.string.staff_disable), staffEmail);
            await(By.desc(disable));
            onView(withContentDescription(disable)).perform(reveal(), activateControl());
            onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(withText(staffEmail)));
            confirm();
            await(By.desc(app.getString(R.string.staff_account_summary,
                    app.getString(R.string.staff_enable), staffEmail)));
        }
    }

    private static void typeFixture(androidx.test.espresso.ViewInteraction field, String value) {
        field.perform(reveal(), new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(EditText.class); }
            @Override public String getDescription() { return "enter disposable fixture input"; }
            @Override public void perform(UiController controller, View view) { ((EditText) view).setText(value); }
        });
    }

    private static void signInAdmin(String email, String password) {
        enter(R.id.email, email);
        enter(R.id.password, password);
        clickId(R.id.sign_in);
        awaitActivity(lk.finora.app.ui.admin.AdminHomeActivity.class);
    }

    @Test public void adminDashboardOpensWorkingUserAndCategoryManagement() {
        org.junit.Assume.assumeTrue("Requires a disposable ADMIN bootstrap",
                "true".equals(InstrumentationRegistry.getArguments().getString("finoraAdminFixture")));
        String identity = UUID.randomUUID().toString().replace("-", "");
        String email = "t51-" + identity + "@example.test";
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);
            signOut();
            signInAdmin("delivery-admin@example.test", "Synthetic9-delivery-admin");
            clickText(R.string.admin_manage_users);
            typeFixture(onView(withHint(R.string.admin_user_search)), email);
            clickText(R.string.admin_search);
            awaitButton(R.string.admin_disable_user);
            clickText(R.string.admin_disable_user);
            confirm();
            awaitButton(R.string.admin_enable_user);
            clickText(R.string.admin_enable_user);
            confirm();
            awaitButton(R.string.admin_disable_user);
            pressBack();
            awaitActivity(lk.finora.app.ui.admin.AdminHomeActivity.class);
            clickText(R.string.admin_default_categories);
            awaitButton(R.string.records_add);
            clickText(R.string.records_add);
            String name = "Synthetic default " + identity.substring(0, 8);
            String sinhala = "පරීක්ෂණ " + identity.substring(0, 8);
            typeFixture(onView(withHint(R.string.admin_category_name_en)).inRoot(isDialog()), name);
            typeFixture(onView(withHint(R.string.admin_category_name_si)).inRoot(isDialog()), sinhala);
            confirm();
            String row = name + " · " + sinhala + " · " + app.getString(R.string.support_active);
            awaitText(row);
            onView(withContentDescription(app.getString(R.string.records_edit) + ". " + row))
                    .perform(reveal(), activateControl());
            typeFixture(onView(withHint(R.string.admin_category_name_en)).inRoot(isDialog()), name + " edited");
            confirm();
            row = name + " edited · " + sinhala + " · " + app.getString(R.string.support_active);
            awaitText(row);
            onView(withContentDescription(app.getString(R.string.records_remove) + ". " + row))
                    .perform(reveal(), activateControl());
            confirm();
            assertTrue(device.wait(Until.gone(By.text(row)), UI_TIMEOUT_MS));
            pressBack();
            awaitActivity(lk.finora.app.ui.admin.AdminHomeActivity.class);
            clickText(R.string.sms_diagnostic_admin);
            awaitButton(R.string.sms_format_sample_check);
            clickText(R.string.sms_format_sample_check);
            typeFixture(onView(withHint(R.string.sms_format_synthetic_sample)), "Synthetic unmatched text");
            clickText(R.string.sms_format_sample_check);
            awaitText(app.getString(R.string.sms_format_sample_result,
                    app.getString(R.string.sms_format_approved_match), app.getString(R.string.sms_format_sample_unmatched)));
        }
    }

    @Test public void userExportsThroughSystemPickerAndConfirmsAccountDeletion() {
        org.junit.Assume.assumeTrue("Creates a synthetic export on the isolated emulator",
                "true".equals(InstrumentationRegistry.getArguments().getString("finoraDataRightsFixture")));
        String identity = UUID.randomUUID().toString().replace("-", "");
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);
            tab(R.string.more_tab);
            clickText(R.string.settings_title);
            awaitActivity(lk.finora.app.ui.settings.SettingsActivity.class);
            clickText(R.string.settings_export_data);
            var save = device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("Save",
                    java.util.regex.Pattern.CASE_INSENSITIVE))), UI_TIMEOUT_MS);
            assertNotNull("System document picker did not offer Save", save);
            save.click();
            awaitText(app.getString(R.string.settings_export_saved));
            clickText(R.string.settings_delete);
            confirm();
            typeFixture(onView(withHint(R.string.password)).inRoot(isDialog()), "Test-" + identity + "-9");
            confirm();
            awaitActivity(LoginActivity.class);
        }
    }

    @Test public void adminCanDiscardAnUnpublishedNsbDraft() {
        org.junit.Assume.assumeTrue("Requires a disposable ADMIN bootstrap",
                "true".equals(InstrumentationRegistry.getArguments().getString("finoraAdminFixture")));
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInAdmin("delivery-admin@example.test", "Synthetic9-delivery-admin");
            clickText(R.string.sms_diagnostic_admin);
            awaitButton(R.string.sms_format_draft);
            clickText(R.string.sms_format_draft);
            typeFixture(onView(withHint(R.string.sms_format_revision_number)).inRoot(isDialog()), "999");
            confirm();
            String draft = app.getString(R.string.sms_format_revision, 999, app.getString(R.string.support_draft_state));
            awaitText(draft);
            onView(withContentDescription(app.getString(R.string.records_remove) + ". " + draft))
                    .perform(reveal(), activateControl());
            confirm();
            assertTrue("Draft still visible after discard", device.wait(Until.gone(By.text(draft)), UI_TIMEOUT_MS));
            awaitText(app.getString(R.string.sms_format_revision, 1, app.getString(R.string.support_active)));
        }
    }

    @Test public void withdrawnUserMustExplicitlyRestoreConsentBeforeFreshSignIn() {
        org.junit.Assume.assumeTrue("Requires an isolated consent fixture",
                "true".equals(InstrumentationRegistry.getArguments().getString("finoraConsentFixture")));
        String identity = UUID.randomUUID().toString().replace("-", "");
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);
            tab(R.string.more_tab);
            clickText(R.string.settings_title);
            awaitActivity(lk.finora.app.ui.settings.SettingsActivity.class);
            clickText(R.string.settings_withdraw);
            onView(withText(R.string.settings_withdraw_action)).inRoot(isDialog()).perform(activateControl());
            awaitActivity(LoginActivity.class);
            enter(R.id.email, "t51-" + identity + "@example.test");
            enter(R.id.password, "Test-" + identity + "-9");
            clickId(R.id.sign_in);
            awaitText(app.getString(R.string.consent_recover_title));
            onView(withText(R.string.consent_continue)).inRoot(isDialog()).perform(reveal())
                    .check(matches(not(isEnabled())));
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(activateControl());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> LocaleHelper.apply("SI"));
            var configuration = new android.content.res.Configuration(app.getResources().getConfiguration());
            configuration.setLocale(java.util.Locale.forLanguageTag("si"));
            var sinhala = app.createConfigurationContext(configuration);
            awaitText(sinhala.getString(R.string.sign_in));
            enter(R.id.email, "t51-" + identity + "@example.test");
            enter(R.id.password, "Test-" + identity + "-9");
            clickId(R.id.sign_in);
            awaitText(sinhala.getString(R.string.consent_recover_title));
            onView(withText(R.string.consent_recover_title)).inRoot(isDialog()).check(unclippedConsentLabel());
            onView(withText(R.string.consent_continue)).inRoot(isDialog()).perform(reveal())
                    .check(matches(not(isEnabled())));
            onView(withText(R.string.register_consent)).inRoot(isDialog()).perform(reveal(), activateControl());
            onView(withText(R.string.consent_continue)).inRoot(isDialog()).perform(reveal(), activateControl());
            awaitText(sinhala.getString(R.string.consent_restored));
            onView(withText(R.string.consent_restored)).perform(reveal()).check(unclippedConsentLabel());
            awaitActivity(LoginActivity.class);
            enter(R.id.password, "Test-" + identity + "-9");
            clickId(R.id.sign_in);
            awaitActivity(lk.finora.app.ui.main.MainActivity.class);
            tab(R.string.more_tab);
            clickText(R.string.settings_title);
            awaitActivity(lk.finora.app.ui.settings.SettingsActivity.class);
            clickText(R.string.settings_delete);
            onView(withId(android.R.id.message)).inRoot(isDialog())
                    .check(matches(withText(R.string.settings_delete_confirm))).check(unclippedConsentLabel());
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(activateControl());
        }
    }

    private static androidx.test.espresso.ViewAssertion unclippedConsentLabel() {
        return (view, failure) -> {
            if (failure != null) throw failure;
            assertTrue(isDisplayed().matches(view));
            var label = (android.widget.TextView) view;
            var layout = label.getLayout();
            assertNotNull(layout);
            assertTrue(layout.getHeight() <= label.getHeight() - label.getCompoundPaddingTop()
                    - label.getCompoundPaddingBottom());
            for (int line = 0; line < layout.getLineCount(); line++) {
                assertEquals(0, layout.getEllipsisCount(line));
                assertTrue(layout.getLineMax(line) <= layout.getWidth() + 1f);
            }
        };
    }

    @Test public void adminEditsValidatesPublishesActivatesAndRollsBackNsbDraft() {
        org.junit.Assume.assumeTrue("Requires a disposable ADMIN bootstrap",
                "true".equals(InstrumentationRegistry.getArguments().getString("finoraAdminFixture")));
        try (var screen = ActivityScenario.launch(LoginActivity.class)) {
            signInAdmin("delivery-admin@example.test", "Synthetic9-delivery-admin");
            clickText(R.string.sms_diagnostic_admin);
            awaitButton(R.string.sms_format_draft);
            clickText(R.string.sms_format_draft);
            typeFixture(onView(withHint(R.string.sms_format_revision_number)).inRoot(isDialog()), "991");
            confirm();
            String draft = app.getString(R.string.sms_format_revision, 991, app.getString(R.string.support_draft_state));
            awaitText(draft);
            formatRevisionAction(R.string.records_edit, draft);
            awaitActivity(lk.finora.app.ui.support.FormatSampleActivity.class);
            assertTrue("Draft could not be loaded", device.wait(
                    Until.hasObject(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(
                            app.getString(R.string.records_save)), java.util.regex.Pattern.CASE_INSENSITIVE))
                            .enabled(true)), UI_TIMEOUT_MS));
            clickText(R.string.records_save);
            awaitText(draft);
            formatRevisionAction(R.string.sms_format_validate_approved, draft);
            confirm();
            awaitText(app.getString(R.string.sms_format_fixture_result, 3, app.getString(R.string.sms_format_sample_matched)));
            formatRevisionAction(R.string.sms_format_publish, draft);
            confirm();
            String published = app.getString(R.string.sms_format_revision, 991, app.getString(R.string.sms_format_published));
            awaitText(published);
            formatRevisionAction(R.string.sms_format_activate, published);
            confirm();
            String active = app.getString(R.string.sms_format_revision, 991, app.getString(R.string.support_active));
            awaitText(active);
            String prior = app.getString(R.string.sms_format_revision, 1, app.getString(R.string.sms_format_published));
            formatRevisionAction(R.string.sms_format_activate, prior);
            confirm();
            awaitText(app.getString(R.string.sms_format_revision, 1, app.getString(R.string.support_active)));
        }
    }

    private void formatRevisionAction(int label, String revision) {
        onView(withContentDescription(app.getString(label) + ". " + revision)).perform(reveal(), activateControl());
    }

    @Test public void registrationRequiresBothConsentCheckboxesInEachLanguage() {
        try (var screen = ActivityScenario.launch(RegisterActivity.class)) {
            checkConsentGate();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> LocaleHelper.apply("SI"));
            var sinhala = new android.content.res.Configuration(app.getResources().getConfiguration());
            sinhala.setLocale(java.util.Locale.forLanguageTag("si"));
            awaitText(app.createConfigurationContext(sinhala).getString(R.string.register_consent_heading));
            checkConsentGate();
        }
    }

    private static void checkConsentGate() {
        for (int field : new int[]{R.id.register_email, R.id.register_name, R.id.register_password,
                R.id.register_language, R.id.register_consent_heading, R.id.register_over18, R.id.register_consent}) {
            onView(withId(field)).perform(reveal()).check(matches(isDisplayed()));
        }
        onView(withText(R.string.register_consent_text)).perform(reveal()).check(matches(isDisplayed()));
        onView(withId(R.id.register_submit)).check(matches(not(isEnabled())));
        clickId(R.id.register_over18);
        onView(withId(R.id.register_submit)).check(matches(not(isEnabled())));
        clickId(R.id.register_consent);
        onView(withId(R.id.register_submit)).check(matches(isEnabled()));
        clickId(R.id.register_over18);
        onView(withId(R.id.register_submit)).check(matches(not(isEnabled())));
        clickId(R.id.register_consent);
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
            pressBack();
            clickId(R.id.login_sign_in_options);
            onView(withText(R.string.staff_activate)).inRoot(isDialog()).check((view, failure) -> {
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
            clickId(R.id.shell_add_transaction);
            awaitId("form_account");
            select(R.id.form_account, "Synthetic wallet");
            select(R.id.form_type, app.getString(R.string.category_expense));
            select(R.id.form_category, "Food");
            enter(R.id.form_amount, "12.34");
            enter(R.id.form_merchant, MERCHANT);
            clickId(R.id.form_date);
            onView(withId(com.google.android.material.R.id.confirm_button)).inRoot(isDialog()).perform(activateControl());
            clickId(R.id.form_save);
            await(By.desc(app.getString(R.string.records_tab)));
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
            await(By.descContains(MERCHANT));
            onView(withContentDescription(containsString(MERCHANT))).perform(activateControl());
            awaitActivity(lk.finora.app.ui.transactions.TransactionFormActivity.class);
            awaitId("form_amount");
            enter(R.id.form_amount, "15.17");
            clickId(R.id.form_save);
            await(By.textContains("15.17"));
            onView(withContentDescription(containsString(MERCHANT)))
                    .perform(androidx.test.espresso.action.ViewActions.longClick());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            awaitText(app.getString(R.string.records_empty));
            openImport(R.string.sms_access_title);
            onView(withText(R.string.sms_manual_entry)).check(matches(isDisplayed()));
            clickText(R.string.sms_no_thanks);
            assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));
            await(By.desc(app.getString(R.string.records_tab)));
        }
    }

    @Test public void dailyGuideAndGoalSimulationFromPlanUseTheSignedInOwner() throws Exception {
        org.junit.Assume.assumeTrue("Planning integration requires the acceptance APK",
                isAcceptancePackage());
        String identity = UUID.randomUUID().toString().replace("-", "");
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo"));
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);
            tab(R.string.plan_tab);
            clickText(R.string.planning_guide);
            clickText(R.string.planning_calculate);
            awaitText(app.getString(R.string.planning_date_missing));
            typeFixture(onView(withHint(R.string.planning_funds)), "55000.00");
            typeFixture(onView(withHint(R.string.planning_savings)), "5000.00");
            typeFixture(onView(withHint(R.string.planning_arrears)), "3000.00");
            typeFixture(onView(withHint(R.string.planning_income_date)), today.plusDays(10).toString());
            clickText(R.string.planning_calculate);
            await(By.textContains(app.getString(R.string.planning_tracked, "0.00", 0)));
            device.setOrientationLeft();
            awaitActivity(lk.finora.app.ui.planning.PlanningActivity.class);
            onView(withHint(R.string.planning_funds)).check(matches(withText("55000.00")));
            device.setOrientationNatural();
            awaitActivity(lk.finora.app.ui.planning.PlanningActivity.class);
            clickText(R.string.planning_confirm_bills);
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            await(By.textContains(app.getString(R.string.planning_allowance, "4700.00", 10)));
            pressBack();
            awaitActivity(MainActivity.class);

            clickText(R.string.goal_list_title);
            awaitId("records_add");
            clickId(R.id.records_add);
            enter(R.id.form_name, "Synthetic emergency reserve");
            enter(R.id.goal_target, "120000.00");
            enter(R.id.goal_target_date, today.plusMonths(8).toString());
            clickId(R.id.records_save);
            awaitActivity(lk.finora.app.ui.goals.GoalListActivity.class);
            awaitText("Synthetic emergency reserve");
            pressBack();
            awaitActivity(MainActivity.class);

            clickText(R.string.planning_simulator);
            awaitText("Synthetic emergency reserve");
            typeFixture(onView(withHint(R.string.planning_baseline)), "25000.00");
            typeFixture(onView(withHint(R.string.planning_adjustment)), "-5000.00");
            clickText(R.string.planning_calculate);
            await(By.textContains(app.getString(R.string.planning_monthly, "20000.00")));
            await(By.textContains(app.getString(R.string.planning_finish,
                    java.time.YearMonth.from(today).plusMonths(6).atEndOfMonth().toString(), 6)));
            device.setOrientationLeft();
            awaitActivity(lk.finora.app.ui.planning.PlanningActivity.class);
            onView(withHint(R.string.planning_adjustment)).check(matches(withText("-5000.00")));
            device.setOrientationNatural();
            awaitActivity(lk.finora.app.ui.planning.PlanningActivity.class);
            await(By.textContains(app.getString(R.string.planning_monthly, "20000.00")));
            typeFixture(onView(withHint(R.string.planning_adjustment)), "-25000.00");
            clickText(R.string.planning_calculate);
            await(By.textContains(app.getString(R.string.planning_no_capacity)));
            pressBack();
            awaitActivity(MainActivity.class);
            signOut();

            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            tab(R.string.plan_tab);
            clickText(R.string.planning_simulator);
            awaitText(app.getString(R.string.planning_no_goals));
            assertFalse(device.hasObject(By.text("Synthetic emergency reserve")));
        } finally {
            device.setOrientationNatural();
        }
    }

    @Test public void rotatingDuringSimulationKeepsRestoredCutControlsLocked() throws Exception {
        var responded = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completed = new AtomicBoolean();
        try (var login = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            seedPlanningGoal(true);
            try (var transport = new PlanningTransportFixture(sessions, chain -> {
                var request = chain.request();
                var response = chain.proceed(request);
                if (!"POST".equals(request.method()) || !"/api/v1/simulate".equals(request.url().encodedPath())) {
                    return response;
                }
                responded.countDown();
                try {
                    if (!release.await(8, TimeUnit.SECONDS)) throw new IOException("Simulation fixture timed out");
                    completed.set(response.isSuccessful());
                    return response;
                } catch (InterruptedException interrupted) {
                    response.close();
                    Thread.currentThread().interrupt();
                    throw new IOException("Simulation fixture interrupted", interrupted);
                } catch (IOException failure) {
                    response.close();
                    throw failure;
                }
            }); var planning = ActivityScenario.<PlanningActivity>launch(PlanningActivity.simulation(app))) {
                awaitText("Synthetic planning reserve");
                typeFixture(onView(withHint(R.string.planning_baseline)), "25000.00");
                typeFixture(onView(withHint(R.string.planning_adjustment)), "-5000.00");
                clickText(R.string.planning_add_cut);
                onView(withContentDescription(R.string.planning_category))
                        .perform(selectSpinnerItem("Food"));
                typeFixture(onView(withHint(R.string.planning_reduction)), "1500.00");
                var original = new AtomicReference<PlanningActivity>();
                planning.onActivity(original::set);
                clickText(R.string.planning_calculate);
                try {
                    assertTrue("Real simulation response reached the hold", responded.await(4, TimeUnit.SECONDS));
                    device.setOrientationLeft();
                    var recreated = new AtomicBoolean();
                    long deadline = SystemClock.elapsedRealtime() + 3000;
                    do {
                        planning.onActivity(current -> recreated.set(current != original.get()
                                && current.getResources().getConfiguration().orientation
                                == android.content.res.Configuration.ORIENTATION_LANDSCAPE));
                        if (!recreated.get()) SystemClock.sleep(25);
                    } while (!recreated.get() && SystemClock.elapsedRealtime() < deadline);
                    assertTrue("Rotation must recreate the landscape screen", recreated.get());
                    onView(withHint(R.string.planning_baseline)).check(matches(allOf(withText("25000.00"), not(isEnabled()))));
                    onView(withHint(R.string.planning_adjustment)).check(matches(allOf(withText("-5000.00"), not(isEnabled()))));
                    onView(withContentDescription(R.string.planning_goal)).check(matches(not(isEnabled())));
                    onView(withContentDescription(R.string.planning_category)).check(matches(not(isEnabled())));
                    onView(withHint(R.string.planning_reduction)).check(matches(allOf(withText("1500.00"), not(isEnabled()))));
                    onView(withText(R.string.planning_remove_cut)).check(matches(not(isEnabled())));
                    onView(withText(R.string.planning_add_cut)).check(matches(not(isEnabled())));
                    onView(withText(R.string.planning_calculate)).check(matches(not(isEnabled())));
                    assertFalse(completed.get());
                } finally {
                    release.countDown();
                }
                onView(withHint(R.string.planning_reduction)).perform(new ViewAction() {
                    @Override public Matcher<View> getConstraints() { return isAssignableFrom(EditText.class); }
                    @Override public String getDescription() { return "wait for the held calculation to complete"; }
                    @Override public void perform(UiController controller, View view) {
                        long deadline = SystemClock.elapsedRealtime() + 3000;
                        while (!view.isEnabled() && SystemClock.elapsedRealtime() < deadline) {
                            controller.loopMainThreadForAtLeast(25);
                        }
                        assertTrue("Cut amount becomes editable after completion", view.isEnabled());
                    }
                });
                assertTrue(completed.get());
                onView(withHint(R.string.planning_reduction)).check(matches(isEnabled()));
                onView(withContentDescription(R.string.planning_category)).check(matches(isEnabled()));
                onView(withText(R.string.planning_remove_cut)).check(matches(isEnabled()));
                device.setOrientationNatural();
                await(By.textContains(app.getString(R.string.planning_monthly, "21500.00")));
            }
        } finally {
            release.countDown();
            device.setOrientationNatural();
        }
    }

    @Test public void editingAfterInitialChoiceFailureKeepsRetryAvailable() throws Exception {
        var loads = new AtomicInteger();
        try (var login = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            seedPlanningGoal(false);
            try (var transport = new PlanningTransportFixture(sessions, chain -> {
                var request = chain.request();
                if ("GET".equals(request.method()) && "/api/v1/categories".equals(request.url().encodedPath())
                        && loads.incrementAndGet() == 1) {
                    return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                            .code(503).message("Synthetic choice load failure")
                            .body(ResponseBody.create("", null)).build();
                }
                return chain.proceed(request);
            }); var planning = ActivityScenario.<PlanningActivity>launch(PlanningActivity.simulation(app))) {
                awaitButton(R.string.records_retry);
                typeFixture(onView(withHint(R.string.planning_baseline)), "17500.00");
                onView(withText(R.string.records_retry)).perform(reveal()).check(matches(isEnabled()));
                assertEquals("Editing never automatically retries choice loading", 1, loads.get());
                clickText(R.string.records_retry);
                awaitText("Synthetic planning reserve");
                assertEquals(2, loads.get());
                onView(withHint(R.string.planning_baseline)).check(matches(withText("17500.00")));
                onView(withText(R.string.planning_calculate)).check(matches(isEnabled()));
            }
        }
    }

    private void seedPlanningGoal(boolean withPastExpense) throws IOException {
        var requests = AccountRepository.ProtectedRequests.currentUser();
        assertNotNull("Synthetic owner must be signed in", requests);
        var goalRepo = new GoalRepository(requests);
        var colombo = ZoneId.of("Asia/Colombo");
        try (var response = goalRepo.create(new GoalDto.Write("Synthetic planning reserve", new BigDecimal("120000.00"),
                LocalDate.now(colombo).plusMonths(8).toString(), false)).execute()) {
            GoalRepository.readGoal(response);
        }
        if (!withPastExpense) return;
        AccountDto wallet;
        try (var response = new AccountRepository(requests)
                .create(new AccountDto.Write("Synthetic planning wallet", "CASH", BigDecimal.ZERO)).execute()) {
            wallet = AccountRepository.read(response, AccountDto.class);
        }
        long food;
        try (var response = new CategoryRepository(requests).list().execute()) {
            food = CategoryRepository.rows(response).stream().filter(c -> "Food".equals(c.name()) && "EXPENSE".equals(c.kind()))
                    .findFirst().orElseThrow().id();
        }
        var date = YearMonth.now(colombo).minusMonths(1).atDay(5);
        try (var response = new TransactionRepository(requests).create(new TransactionRepository.Edit(wallet.id(), food,
                "EXPENSE", new BigDecimal("6500.00"), date.toString(), null, null)).execute()) {
            TransactionRepository.readRow(response);
        }
    }

    @Test public void syntheticPasteFeedsInsightsAndPlanningSupportsSinhala() {
        org.junit.Assume.assumeTrue("Integrated journey requires the acceptance APK",
                isAcceptancePackage());
        var runner = InstrumentationRegistry.getInstrumentation();
        var clipboard = app.getSystemService(ClipboardManager.class);
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo"));
        String account = "Synthetic salary wallet";
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            tab(R.string.more_tab);
            clickText(R.string.account_list_title);
            awaitId("records_add");
            clickId(R.id.records_add);
            enter(R.id.form_name, account);
            enter(R.id.account_balance, "0.00");
            clickId(R.id.records_save);
            awaitText(account);
            pressBack();
            awaitActivity(MainActivity.class);

            tab(R.string.records_tab);
            clickId(R.id.shell_add_transaction);
            select(R.id.form_account, account);
            select(R.id.form_type, app.getString(R.string.category_income));
            enter(R.id.form_amount, "85000.00");
            clickId(R.id.form_date);
            onView(withId(com.google.android.material.R.id.confirm_button)).inRoot(isDialog()).perform(activateControl());
            clickId(R.id.form_save);
            awaitActivity(MainActivity.class);

            openImport(R.string.sms_access_title);
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);
            awaitId("sms_paste_entry_action");
            clickId(R.id.sms_paste_entry_action);
            awaitActivity(lk.finora.app.ui.sms.PasteSmsActivity.class);
            awaitId("sms_paste_action");
            String sms = "Dear MR TEST USER,LKR 1,250.75 Debited from your A/c XXXXXXXX0000 on "
                    + today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu", java.util.Locale.ROOT))
                    + " at 14:27.AvlBal LKR 25,000.00. @ TEST SUPERMARKET. ATM POS Transaction."
                    + "Thank you for banking with us.Call Centre 1972.";
            runner.runOnMainSync(() -> clipboard.setPrimaryClip(ClipData.newPlainText("Synthetic NSB fixture", sms)));
            clickId(R.id.sms_paste_action);
            String extracted = app.getString(R.string.sms_extracted_record, "1250.75", today.toString(),
                    app.getString(R.string.sms_direction_debit))
                    + "\n" + app.getString(R.string.sms_extracted_merchant, "TEST SUPERMARKET");
            awaitText(extracted);
            clickId(R.id.sms_paste_preview);
            String selectedRow = app.getString(R.string.sms_preview_selected, extracted);
            await(By.text(selectedRow));
            onData(equalTo(selectedRow)).inRoot(isDialog()).perform(click());
            onView(new org.hamcrest.TypeSafeMatcher<View>() {
                @Override protected boolean matchesSafely(View view) {
                    if (!(view instanceof android.widget.Spinner) || !(view.getParent() instanceof ViewGroup parent)) {
                        return false;
                    }
                    for (int i = 0; i < parent.getChildCount(); i++) {
                        if (parent.getChildAt(i) instanceof TextView label && label.getLabelFor() == view.getId()
                                && app.getString(R.string.transaction_account).contentEquals(label.getText())) return true;
                    }
                    return false;
                }
                @Override public void describeTo(org.hamcrest.Description description) {
                    description.appendText("SMS preview account selector");
                }
            }).inRoot(isDialog()).perform(new ViewAction() {
                @Override public Matcher<View> getConstraints() { return isAssignableFrom(android.widget.Spinner.class); }
                @Override public String getDescription() { return "select the disposable wallet"; }
                @Override public void perform(UiController controller, View view) {
                    var picker = (android.widget.Spinner) view;
                    assertEquals(account, picker.getItemAtPosition(1));
                    picker.setSelection(1);
                }
            });
            onView(withText(R.string.sms_preview_apply)).inRoot(isDialog()).perform(activateControl());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            awaitButton(R.string.sms_batch_save);
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            await(By.textContains(app.getString(R.string.sms_batch_saved, 1)));
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            pressBack();
            pressBack();
            awaitActivity(MainActivity.class);

            tab(R.string.home_tab);
            await(By.desc(app.getString(R.string.dashboard_income, "85000.00")));
            await(By.desc(app.getString(R.string.dashboard_expense, "1250.75")));
            tab(R.string.insights_tab);
            clickText(R.string.insight_open);
            awaitShown(app.getString(R.string.score_spending_within));
            pressBack();
            awaitActivity(MainActivity.class);
            clickText(R.string.advice_title);
            awaitShown(app.getString(R.string.advice_rules));
            pressBack();
            awaitActivity(MainActivity.class);

            var config = new android.content.res.Configuration(app.getResources().getConfiguration());
            config.setLocale(java.util.Locale.forLanguageTag("si"));
            var sinhala = app.createConfigurationContext(config);
            chooseTestLanguage("si");
            await(By.desc(sinhala.getString(R.string.plan_tab)));
            awaitActivity(MainActivity.class);
            onView(withId(R.string.plan_tab))
                    .perform(activateControl());
            onView(allOf(isClickable(), withText(sinhala.getString(R.string.planning_guide))))
                    .perform(reveal(), activateControl());
            onView(withHint(sinhala.getString(R.string.planning_funds))).perform(reveal()).check(matches(isDisplayed()));
            onView(allOf(isClickable(), withText(sinhala.getString(R.string.planning_assumptions))))
                    .perform(reveal(), activateControl());
            onView(withText(sinhala.getString(R.string.planning_guide_assumptions))).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(activateControl());
            pressBack();
            awaitActivity(MainActivity.class);
            onView(allOf(isClickable(), withText(sinhala.getString(R.string.planning_simulator))))
                    .perform(reveal(), activateControl());
            awaitText(sinhala.getString(R.string.planning_no_goals));
        } finally {
            runner.runOnMainSync(() -> {
                clipboard.clearPrimaryClip();
                LocaleHelper.apply("EN");
            });
        }
    }

    @Test public void smsRefusalKeepsEveryFallbackReachable() {
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            tab(R.string.records_tab);
            openImport(R.string.sms_access_title);
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);
            onView(withText(R.string.sms_access_explanation)).perform(reveal()).check(matches(isDisplayed()));
            assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));

            clickId(R.id.sms_paste_entry_action);
            awaitActivity(lk.finora.app.ui.sms.PasteSmsActivity.class);
            pressBack();
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);

            clickId(R.id.sms_csv_import_action);
            awaitActivity(lk.finora.app.ui.importer.CsvImportActivity.class);
            pressBack();
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);

            clickId(R.id.sms_manual_entry_action);
            awaitActivity(lk.finora.app.ui.transactions.TransactionFormActivity.class);
            pressBack();
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);

            clickId(R.id.sms_no_thanks_action);
            awaitActivity(MainActivity.class);
            assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));
        }
    }

    @Test public void smsRuntimeDenialThenGrantUpdatesScanAvailability() {
        var arguments = InstrumentationRegistry.getArguments();
        // Android kills the app on permission revocation. Run this method alone on a disposable AVD.
        org.junit.Assume.assumeTrue("Permission changes require a standalone disposable-emulator run",
                "true".equals(arguments.getString("finoraIsolatedEmulator"))
                        && "true".equals(arguments.getString("finoraSmsPermissionMutation")));
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            tab(R.string.records_tab);
            openImport(R.string.sms_access_title);
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);

            clickId(R.id.sms_allow);
            clickSmsPermissionChoice("deny");
            awaitText(app.getString(R.string.sms_access_denied));
            assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));
            assertFalse(device.hasObject(By.res(app.getPackageName(), "sms_scan")));

            clickId(R.id.sms_allow);
            clickSmsPermissionChoice("allow");
            awaitText(app.getString(R.string.sms_access_granted));
            assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.READ_SMS));
            onView(withId(R.id.sms_scan)).perform(reveal()).check(matches(isDisplayed()));
        }
    }

    @Test public void smsPermanentDenialRecoversThroughAppSettings() {
        var arguments = InstrumentationRegistry.getArguments();
        org.junit.Assume.assumeTrue("Permission changes require a standalone disposable-emulator run",
                "true".equals(arguments.getString("finoraIsolatedEmulator"))
                        && "true".equals(arguments.getString("finoraSmsPermissionMutation")));
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(UUID.randomUUID().toString().replace("-", ""));
            tab(R.string.records_tab);
            openImport(R.string.sms_access_title);
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);
            for (int attempt = 0; attempt < 2; attempt++) {
                clickId(R.id.sms_allow);
                clickSmsPermissionChoice("deny(?:_and_dont_ask_again)?");
                awaitText(app.getString(attempt == 0 ? R.string.sms_access_denied : R.string.sms_permission_settings));
            }
            assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.READ_SMS));
            clickId(R.id.sms_settings);
            clickSystemText("Permissions");
            clickSystemText("SMS");
            clickSystemText("Allow");

            for (int back = 0; back < 3; back++) device.pressBack();
            awaitActivity(lk.finora.app.ui.sms.SmsAccessActivity.class);
            awaitText(app.getString(R.string.sms_access_granted));
            assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.READ_SMS));
            onView(withId(R.id.sms_scan)).perform(reveal()).check(matches(isDisplayed()));
        }
    }

    private void clickSystemText(String text) {
        var control = device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS);
        assertNotNull("Expected Android Settings control: " + text, control);
        control.click();
    }

    private void clickSmsPermissionChoice(String choice) {
        var selector = By.res(java.util.regex.Pattern.compile(
                "(?:com\\.android|com\\.google\\.android)\\.permissioncontroller:id/permission_"
                        + choice + "_button"));
        var button = device.wait(Until.findObject(selector), UI_TIMEOUT_MS);
        assertNotNull("Expected SMS permission dialog choice: " + choice, button);
        button.click();
    }

    @Test public void diagnosticRequiresConfirmationAfterCancel() {
        String identity = UUID.randomUUID().toString().replace("-", "");
        try (var ignored = ActivityScenario.launch(LoginActivity.class)) {
            signInNewUser(identity);
            if ("SI".equals(InstrumentationRegistry.getArguments().getString("finoraDiagnosticLocale"))) {
                clickText(R.string.language_selector);
                dialogChoice(app.getString(R.string.language_sinhala));
                var sinhala = new android.content.res.Configuration(app.getResources().getConfiguration());
                sinhala.setLocale(java.util.Locale.forLanguageTag("si"));
                await(By.desc(app.createConfigurationContext(sinhala).getString(R.string.records_tab)));
            }
            tab(R.string.records_tab);
            openImport(R.string.sms_access_title);
            clickText(R.string.sms_paste);
            awaitId("sms_paste_action");
            var instrumentation = InstrumentationRegistry.getInstrumentation();
            instrumentation.runOnMainSync(() -> app.getSystemService(ClipboardManager.class)
                    .setPrimaryClip(ClipData.newPlainText("Test message", "Synthetic diagnostic fixture")));
            try {
                onView(withId(R.id.sms_paste_action)).perform(reveal(), activateControl());
                awaitText(app.getString(R.string.sms_report_problem));
                onView(withId(R.id.sms_diagnostic_report)).perform(reveal(), activateControl());
                onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(withText(
                        app.getString(R.string.sms_report_preview, app.getString(R.string.sms_diagnostic_unknown),
                                "FORMAT_UNRECOGNIZED"))));
                onView(withId(android.R.id.message)).inRoot(isDialog()).check((view, failure) -> {
                    if (failure != null) throw failure;
                    var message = (android.widget.TextView) view;
                    var layout = message.getLayout();
                    assertNotNull(layout);
                    assertTrue(layout.getHeight() <= message.getHeight() - message.getCompoundPaddingTop()
                            - message.getCompoundPaddingBottom());
                    for (int line = 0; line < layout.getLineCount(); line++) {
                        assertEquals(0, layout.getEllipsisCount(line));
                        assertTrue(layout.getLineMax(line) <= layout.getWidth() + 1f);
                    }
                });
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
        // The user shell keeps sign-out in its More tab (T62); Admin and Staff homes show it directly.
        if (onUserShell()) tab(R.string.more_tab);
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
        await(By.desc(app.getString(R.string.more_tab)));
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

    private void expandActions(String row) {
        await(By.descContains(row));
        onView(allOf(withId(R.id.record_actions), withContentDescription(containsString(row))))
                .perform(reveal(), activateControl());
    }

    private void awaitText(String text) { await(By.text(text)); }
    /** Waits for a text view, scrolling it into view first (landscape lists can push rows below the fold). */
    private static void awaitShown(String text) {
        long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
        while (true) {
            try {
                onView(withText(text)).perform(reveal()).check(matches(isDisplayed()));
                return;
            } catch (RuntimeException | AssertionError missing) {
                if (SystemClock.elapsedRealtime() > deadline) throw missing;
                SystemClock.sleep(200);
            }
        }
    }
    private void awaitButton(int label) {
        await(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(app.getString(label)),
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)));
    }
    private void await(BySelector selector) {
        assertTrue("Expected FINORA screen state did not appear", device.wait(Until.hasObject(selector), UI_TIMEOUT_MS));
    }

    private static boolean onUserShell() {
        var found = new java.util.concurrent.atomic.AtomicBoolean();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> found.set(
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).stream()
                        .anyMatch(MainActivity.class::isInstance)));
        return found.get();
    }

    /** T62/D65: CSV import and SMS access open from the Records header's import button. */
    private static void openImport(int label) {
        tab(R.string.records_tab);
        clickId(R.id.records_import);
        onView(withText(label)).inRoot(isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
    }

    private static void tab(int label) {
        onView(withId(label)).perform(activateControl());
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
        onView(withId(id)).perform(reveal(), selectFormChoice(label));
    }

    private static ViewAction selectFormChoice(String label) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isEnabled(); }
            @Override public String getDescription() { return "select the synthetic fixture choice"; }
            @Override public void perform(UiController controller, View view) {
                if (view instanceof lk.finora.app.ui.FormChoices choices) {
                    long deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS;
                    do {
                        for (int i = 0; i < choices.getChildCount(); i++) {
                            var chip = (com.google.android.material.chip.Chip) choices.getChildAt(i);
                            if (label.contentEquals(chip.getText())) {
                                chip.performClick();
                                controller.loopMainThreadUntilIdle();
                                return;
                            }
                        }
                        controller.loopMainThreadForAtLeast(50);
                    } while (SystemClock.elapsedRealtime() < deadline);
                    fail("Expected synthetic fixture chip is unavailable");
                } else {
                    selectSpinnerItem(label).perform(controller, view);
                }
            }
        };
    }

    private static ViewAction selectSpinnerItem(String label) {
        return new ViewAction() {
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
        };
    }

    private static void enter(int id, String value) {
        typeFixture(onView(withId(id)), value);
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
