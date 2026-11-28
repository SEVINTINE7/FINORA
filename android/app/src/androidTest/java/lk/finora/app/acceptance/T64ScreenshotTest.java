package lk.finora.app.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.goals.GoalDetailActivity;
import lk.finora.app.ui.goals.GoalFormActivity;
import lk.finora.app.ui.goals.GoalListActivity;

/**
 * T64 review run against a local dev API and a synthetic account (finoraShotEmail / finoraShotPassword) holding the
 * T64.2 demo goals: "Laptop for study" (focus, by 30 Jun 2027), "Emergency fund", "Trip to Ella" (reached) and
 * "New phone" (cancelled). Checks order, focus badge, tap-to-open, the contribution date button and the deadline
 * toggle, and captures screenshots. Never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T64ScreenshotTest {
    @Test public void goalScreens() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T64 run only", "true".equals(args.getString("finoraT64Shots")));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        boolean checks = !"false".equals(args.getString("finoraT64Checks"));
        var tokens = TokenStore.open(app);
        assertNull("Preserve the existing session", tokens.session());
        var login = T62ScreenshotTest.login(args.getString("finoraShotEmail"), args.getString("finoraShotPassword"));
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        tokens.save(login.getString("accessToken"), login.getString("refreshToken"), "USER",
                language.toUpperCase(Locale.ROOT));
        File out = app.getExternalFilesDir("t64");
        assertNotNull(out);
        try {
            goals(out, tag, checks);
        } finally {
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    private static String name(View card) {
        return ((TextView) card.findViewById(R.id.record_name)).getText().toString();
    }

    private void goals(File out, String tag, boolean checks) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        var device = UiDevice.getInstance(instrumentation);
        var monitor = instrumentation.addMonitor(GoalDetailActivity.class.getName(), null, false);
        try (var list = ActivityScenario.launch(GoalListActivity.class)) {
            T62ScreenshotTest.settle();
            list.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(out, tag + "-goals.png")));
            if (checks) {
                // Focus goal first with the only Focus badge, then in progress, reached, cancelled.
                List<String> names = new ArrayList<>();
                List<Integer> badges = new ArrayList<>();
                list.onActivity(activity -> {
                    ViewGroup rows = activity.findViewById(R.id.records_rows);
                    for (int i = 0; i < rows.getChildCount(); i++) {
                        names.add(name(rows.getChildAt(i)));
                        badges.add(rows.getChildAt(i).findViewById(R.id.goal_focus).getVisibility());
                    }
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.goal_summary_card).getVisibility());
                    String card = String.valueOf(rows.getChildAt(0).getContentDescription());
                    assertTrue(card, card.startsWith("Laptop for study. ")
                            && card.contains(app.getString(R.string.goal_progress_text, "81000.00", "180000.00",
                                    "99000.00", 45)));
                });
                assertEquals(List.of("Laptop for study", "Emergency fund", "Trip to Ella", "New phone"), names);
                assertEquals(List.of(View.VISIBLE, View.GONE, View.GONE, View.GONE), badges);
            }
            // Tap the focus goal: the detail shows the Focus badge and today's date on the date button.
            list.onActivity(activity -> ((ViewGroup) activity.findViewById(R.id.records_rows)).getChildAt(0)
                    .performClick());
            var detail = instrumentation.waitForMonitorWithTimeout(monitor, 10_000);
            assertNotNull("Tapping a goal opens it", detail);
            T62ScreenshotTest.settle();
            instrumentation.runOnMainSync(() -> {
                assertEquals("Laptop for study", ((TextView) detail.findViewById(R.id.goal_name)).getText().toString());
                assertEquals(View.VISIBLE, detail.findViewById(R.id.goal_focus).getVisibility());
                assertEquals(app.getString(R.string.goal_today),
                        ((TextView) detail.findViewById(R.id.contribution_date)).getText().toString());
            });
            T62ScreenshotTest.capture(detail, new File(out, tag + "-goal-detail.png"));
            instrumentation.runOnMainSync(() -> ((ScrollView) detail.findViewById(R.id.goal_scroll))
                    .fullScroll(View.FOCUS_DOWN));
            T62ScreenshotTest.settle();
            T62ScreenshotTest.capture(detail, new File(out, tag + "-goal-detail-end.png"));
            if (checks) {
                // The date button opens the protected calendar; cancelling keeps today.
                instrumentation.runOnMainSync(() -> detail.findViewById(R.id.contribution_date).performClick());
                assertTrue("Calendar opens", device.wait(Until.hasObject(
                        By.res(java.util.regex.Pattern.compile(".*:id/confirm_button"))), 5_000));
                device.pressBack();
                T62ScreenshotTest.settle();
            }
            instrumentation.runOnMainSync(detail::finish);
        } finally {
            instrumentation.removeMonitor(monitor);
        }

        // Edit the focus goal: By a date is on, the stored date shows and the monthly hint is 99,000.00 / 7 months.
        var edit = new Intent(app, GoalFormActivity.class).putExtra(AccountListActivity.ITEM_ID, 1L);
        try (var form = ActivityScenario.launch(edit)) {
            T62ScreenshotTest.settle();
            form.onActivity(activity -> {
                if (checks) {
                    assertEquals(R.id.goal_deadline_date,
                            ((MaterialButtonToggleGroup) activity.findViewById(R.id.goal_deadline)).getCheckedButtonId());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.goal_monthly_hint).getVisibility());
                    String hint = ((TextView) activity.findViewById(R.id.goal_monthly_hint)).getText().toString();
                    assertTrue(hint, hint.contains("14,142.86"));
                }
                T62ScreenshotTest.capture(activity, new File(out, tag + "-goal-edit.png"));
            });
        }

        try (var form = ActivityScenario.launch(GoalFormActivity.class)) {
            T62ScreenshotTest.settle();
            form.onActivity(activity -> {
                assertEquals(R.id.goal_deadline_none,
                        ((MaterialButtonToggleGroup) activity.findViewById(R.id.goal_deadline)).getCheckedButtonId());
                assertEquals(View.GONE, activity.findViewById(R.id.goal_date_group).getVisibility());
                T62ScreenshotTest.capture(activity, new File(out, tag + "-goal-add.png"));
            });
            if (checks) {
                // By a date opens the calendar at once; confirming picks tomorrow and shows the date group.
                form.onActivity(activity -> activity.findViewById(R.id.goal_deadline_date).performClick());
                T62ScreenshotTest.settle();
                var confirm = device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/confirm_button"))), 5_000);
                assertNotNull("Calendar opens from By a date", confirm);
                confirm.click();
                T62ScreenshotTest.settle();
                form.onActivity(activity -> {
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.goal_date_group).getVisibility());
                    String chosen = ((TextView) activity.findViewById(R.id.goal_target_date)).getText().toString();
                    assertTrue(chosen, !chosen.equals(activity.getString(R.string.transaction_choose_date)));
                    // Less than a month away: no hint. No deadline hides the date again.
                    ((EditText) activity.findViewById(R.id.goal_target)).setText("120000.00");
                    assertEquals(View.GONE, activity.findViewById(R.id.goal_monthly_hint).getVisibility());
                    activity.findViewById(R.id.goal_deadline_none).performClick();
                    assertEquals(View.GONE, activity.findViewById(R.id.goal_date_group).getVisibility());
                    assertTrue(!activity.isFinishing());
                });
            }
        }
    }
}
