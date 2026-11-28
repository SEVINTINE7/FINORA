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
import androidx.lifecycle.ViewModelProvider;

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
import lk.finora.app.ui.planning.PlanningActivity;
import lk.finora.app.ui.planning.PlanningViewModel;

/**
 * T64 review run against a local dev API and a synthetic account (finoraShotEmail / finoraShotPassword) holding the
 * T64.2 demo goals: "Laptop for study" (focus, by 30 Jun 2027), "Emergency fund", "Trip to Ella" (reached) and
 * "New phone" (cancelled). Checks order, focus badge, tap-to-open, the contribution date button and the deadline
 * toggle, then the T64.3 planning guide and simulation (bill confirmation, More/Less, cuts, status messages), and
 * captures screenshots. Never runs by default.
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
            if (!"false".equals(args.getString("finoraT64Goals"))) goals(out, tag, checks);
            planning(out, tag, checks);
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
    private static File failures;

    /** Waits up to 10 s for a condition checked on the main thread; captures the screen when it fails. */
    private static void until(ActivityScenario<PlanningActivity> screen, String what,
                              java.util.function.Predicate<PlanningActivity> condition) {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10_000;
        var met = new java.util.concurrent.atomic.AtomicBoolean();
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            screen.onActivity(activity -> met.set(condition.test(activity)));
            if (!met.get()) android.os.SystemClock.sleep(100);
        } while (!met.get() && android.os.SystemClock.elapsedRealtime() < deadline);
        if (!met.get() && failures != null) {
            screen.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(failures, "failed-wait.png")));
        }
        assertTrue(what, met.get());
    }

    private static boolean shown(android.app.Activity activity, int id) {
        return activity.findViewById(id).getVisibility() == View.VISIBLE;
    }

    private static String spoken(android.app.Activity activity, int id) {
        return String.valueOf(activity.findViewById(id).getContentDescription());
    }

    private static String text(android.app.Activity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    private static void scrollDown(ActivityScenario<PlanningActivity> screen) {
        screen.onActivity(activity -> ((ScrollView) activity.findViewById(R.id.planning_scroll)).fullScroll(View.FOCUS_DOWN));
        T62ScreenshotTest.settle();
    }

    /**
     * T64.3 planning on the same synthetic account: guide 55,000 / 5,000 / 3,000 with an income date ten days away,
     * confirm tracked bills; simulation on "Laptop for study" (99,000 left) with 25,000 now, Less 5,000 and a
     * 2,000 category cut, then Less 27,000 for the no-capacity message.
     */
    private void planning(File out, String tag, boolean checks) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        var device = UiDevice.getInstance(instrumentation);
        failures = out;
        try (var guide = ActivityScenario.<PlanningActivity>launch(new Intent(app, PlanningActivity.class))) {
            T62ScreenshotTest.settle();
            guide.onActivity(activity -> {
                if (checks) {
                    assertEquals(app.getString(R.string.transaction_choose_date), text(activity, R.id.planning_income_date));
                    assertEquals(View.GONE, activity.findViewById(R.id.planning_income_clear).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.planning_result_card).getVisibility());
                }
                T62ScreenshotTest.capture(activity, new File(out, tag + "-guide-empty.png"));
            });
            if (checks) {
                // The calendar opens; cancelling leaves no date.
                guide.onActivity(activity -> activity.findViewById(R.id.planning_income_date).performClick());
                assertTrue("Income calendar opens", device.wait(Until.hasObject(
                        By.res(java.util.regex.Pattern.compile(".*:id/confirm_button"))), 5_000));
                device.pressBack();
                T62ScreenshotTest.settle();
            }
            String next = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).plusDays(10).toString();
            guide.onActivity(activity -> {
                var model = new ViewModelProvider(activity).get(PlanningViewModel.class);
                try {
                    var field = PlanningViewModel.class.getDeclaredField("incomeDate");
                    field.setAccessible(true);
                    field.set(model, next);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
                ((EditText) activity.findViewById(R.id.planning_funds)).setText("55000.00");
                ((EditText) activity.findViewById(R.id.planning_savings)).setText("5000.00");
                ((EditText) activity.findViewById(R.id.planning_arrears)).setText("3000.00");
            });
            guide.recreate();
            guide.onActivity(activity -> {
                if (checks) {
                    assertEquals("55000.00", text(activity, R.id.planning_funds));
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.planning_income_clear).getVisibility());
                }
                activity.findViewById(R.id.planning_calculate).performClick();
            });
            until(guide, "Tracked bills need confirmation", a -> shown(a, R.id.planning_confirm));
            T62ScreenshotTest.settle();
            guide.onActivity(activity -> {
                if (checks) {
                    assertEquals(app.getString(R.string.planning_confirm_needed), text(activity, R.id.planning_message));
                    String bills = spoken(activity, R.id.planning_bills_summary);
                    assertTrue(bills, bills.contains(app.getString(R.string.planning_bills_title)));
                    assertEquals(View.GONE, activity.findViewById(R.id.planning_result_card).getVisibility());
                }
                T62ScreenshotTest.capture(activity, new File(out, tag + "-guide-bills.png"));
                activity.findViewById(R.id.planning_confirm).performClick();
            });
            var ok = device.wait(Until.findObject(By.res("android:id/button1")), 5_000);
            assertNotNull("Confirm dialog", ok);
            ok.click();
            until(guide, "Allowance shows", a -> shown(a, R.id.planning_result_card));
            T62ScreenshotTest.settle();
            guide.onActivity(activity -> {
                if (checks) {
                    String card = spoken(activity, R.id.planning_result_card);
                    assertTrue(card, card.contains(app.getString(R.string.planning_date_entered, next)));
                    String[] allowance = app.getString(R.string.planning_allowance, "\u0000", 10).split("\u0000");
                    for (String part : allowance) assertTrue(card, card.contains(part));
                    assertEquals(View.GONE, activity.findViewById(R.id.planning_confirm).getVisibility());
                    assertEquals(app.getString(R.string.planning_bills_confirmed), text(activity, R.id.planning_bills_title));
                    String sub = text(activity, R.id.planning_result_sub);
                    assertTrue(sub, sub.startsWith(app.getResources().getQuantityString(R.plurals.planning_for_days, 10, 10)));
                }
                T62ScreenshotTest.capture(activity, new File(out, tag + "-guide-result.png"));
            });
            scrollDown(guide);
            guide.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(out, tag + "-guide-result-end.png")));
            if (checks) {
                // Editing a number clears the result and the bills card.
                guide.onActivity(activity -> {
                    ((EditText) activity.findViewById(R.id.planning_arrears)).setText("3500.00");
                    assertEquals(View.GONE, activity.findViewById(R.id.planning_result_card).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.planning_bills_card).getVisibility());
                });
            }
        }

        try (var sim = ActivityScenario.<PlanningActivity>launch(PlanningActivity.simulation(app))) {
            until(sim, "Goals load", a -> ((android.widget.Spinner) a.findViewById(R.id.planning_goal)).getCount() > 0);
            T62ScreenshotTest.settle();
            sim.onActivity(activity -> {
                if (checks) {
                    assertEquals(R.id.planning_more, ((MaterialButtonToggleGroup) activity.findViewById(R.id.planning_direction))
                            .getCheckedButtonId());
                    assertEquals("Laptop for study", String.valueOf(((android.widget.Spinner) activity
                            .findViewById(R.id.planning_goal)).getSelectedItem()));
                }
                T62ScreenshotTest.capture(activity, new File(out, tag + "-sim-empty.png"));
                ((EditText) activity.findViewById(R.id.planning_baseline)).setText("25000.00");
                activity.findViewById(R.id.planning_less).performClick();
                ((EditText) activity.findViewById(R.id.planning_adjustment)).setText("5000.00");
                activity.findViewById(R.id.planning_add_cut).performClick();
                ViewGroup cuts = activity.findViewById(R.id.planning_cuts);
                assertEquals(1, cuts.getChildCount());
                // Food has October spending on the demo account; a cut above it is refused by the API.
                android.widget.Spinner category = cuts.getChildAt(0).findViewById(R.id.cut_category);
                // Rows list expense categories in API order, shown in the screen language; find Food by its English name.
                var model = new ViewModelProvider(activity).get(PlanningViewModel.class);
                try {
                    var field = PlanningViewModel.class.getDeclaredField("categories");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var all = (List<lk.finora.app.data.api.CategoryDto>) field.get(model);
                    var expenses = all.stream().filter(c -> "EXPENSE".equals(c.kind())).map(c -> c.name())
                            .collect(java.util.stream.Collectors.toList());
                    assertTrue(expenses.toString(), expenses.contains("Food"));
                    category.setSelection(expenses.indexOf("Food"));
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
                ((EditText) cuts.getChildAt(0).findViewById(R.id.cut_amount)).setText("2000.00");
            });
            // The spinner reports a new selection on a later frame (which clears any result): calculate after it.
            T62ScreenshotTest.settle();
            sim.onActivity(activity -> activity.findViewById(R.id.planning_calculate).performClick());
            until(sim, "Estimate shows", a -> shown(a, R.id.planning_result_card));
            T62ScreenshotTest.settle();
            sim.onActivity(activity -> {
                if (checks) {
                    String card = spoken(activity, R.id.planning_result_card);
                    assertTrue(card, card.contains(app.getString(R.string.planning_monthly, "22000.00")));
                    assertTrue(card, card.contains(app.getString(R.string.planning_remaining, "99000.00")));
                    assertEquals(app.getResources().getQuantityString(R.plurals.planning_in_months, 5, 5),
                            text(activity, R.id.planning_result_sub));
                    assertEquals(View.GONE, activity.findViewById(R.id.planning_message).getVisibility());
                }
                T62ScreenshotTest.capture(activity, new File(out, tag + "-sim-result.png"));
            });
            scrollDown(sim);
            sim.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(out, tag + "-sim-result-end.png")));
            if (checks) {
                // Less 27,000 with the 2,000 cut leaves no monthly savings: its own message.
                sim.onActivity(activity -> {
                    ((EditText) activity.findViewById(R.id.planning_adjustment)).setText("27000.00");
                    assertEquals(View.GONE, activity.findViewById(R.id.planning_result_card).getVisibility());
                    activity.findViewById(R.id.planning_calculate).performClick();
                });
                until(sim, "No capacity shows", a -> shown(a, R.id.planning_message));
                sim.onActivity(activity -> ((ScrollView) activity.findViewById(R.id.planning_scroll)).fullScroll(View.FOCUS_UP));
                T62ScreenshotTest.settle();
                sim.onActivity(activity -> {
                    assertEquals(app.getString(R.string.planning_no_capacity), text(activity, R.id.planning_message));
                    T62ScreenshotTest.capture(activity, new File(out, tag + "-sim-no-capacity.png"));
                    ViewGroup cuts = activity.findViewById(R.id.planning_cuts);
                    cuts.getChildAt(0).findViewById(R.id.cut_remove).performClick();
                    assertEquals(0, cuts.getChildCount());
                });
            }
        }
    }
}
