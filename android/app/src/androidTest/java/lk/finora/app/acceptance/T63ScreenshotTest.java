package lk.finora.app.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
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

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.FormChoices;
import lk.finora.app.ui.bills.BillFormActivity;
import lk.finora.app.ui.bills.BillListActivity;
import lk.finora.app.ui.bills.DueDayPicker;
import lk.finora.app.ui.budgets.BudgetFormActivity;
import lk.finora.app.ui.budgets.BudgetListActivity;

/**
 * T63 review run against a local dev API and a synthetic account (finoraShotEmail / finoraShotPassword).
 * Checks budget and bill ordering, tap-to-edit, long-press remove (cancelled) and the bill paid toggle,
 * and captures screenshots.
 * Never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T63ScreenshotTest {
    /** One screenshot pass inside a signed-in session with the requested language and theme. */
    private interface Pass {
        void run(File out, String tag, boolean checks) throws Exception;
    }

    @Test public void budgetScreens() throws Exception {
        session(this::budgets);
    }

    @Test public void billScreens() throws Exception {
        session(this::bills);
    }

    private void session(Pass pass) throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T63 run only", "true".equals(args.getString("finoraT63Shots")));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        boolean checks = !"false".equals(args.getString("finoraT63Checks"));
        var tokens = TokenStore.open(app);
        assertNull("Preserve the existing session", tokens.session());
        var login = T62ScreenshotTest.login(args.getString("finoraShotEmail"), args.getString("finoraShotPassword"));
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        tokens.save(login.getString("accessToken"), login.getString("refreshToken"), "USER",
                language.toUpperCase(Locale.ROOT));
        File out = app.getExternalFilesDir("t63");
        assertNotNull(out);
        try {
            pass.run(out, tag, checks);
        } finally {
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    private void budgets(File out, String tag, boolean checks) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        {
            try (var list = ActivityScenario.launch(BudgetListActivity.class)) {
                T62ScreenshotTest.settle();
                list.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(out, tag + "-budgets.png")));
                if (checks) {
                    // Over-limit first, then near, then on track; the summary card is shown.
                    List<String> states = new ArrayList<>();
                    AtomicReference<View> first = new AtomicReference<>();
                    list.onActivity(activity -> {
                        ViewGroup rows = activity.findViewById(R.id.records_rows);
                        for (int i = 0; i < rows.getChildCount(); i++) {
                            states.add(((TextView) rows.getChildAt(i).findViewById(R.id.budget_state)).getText().toString());
                        }
                        first.set(rows.getChildAt(0));
                        assertEquals(View.VISIBLE, activity.findViewById(R.id.budget_summary_card).getVisibility());
                    });
                    assertEquals(app.getString(R.string.budget_chip_over), states.get(0));
                    assertEquals(app.getString(R.string.budget_chip_near), states.get(1));
                    assertEquals(app.getString(R.string.budget_state_ok), states.get(states.size() - 1));
                    // Long-press opens the remove confirmation; cancel leaves the budget in place.
                    list.onActivity(activity -> assertTrue(first.get().performLongClick()));
                    T62ScreenshotTest.settle();
                    var device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation);
                    assertTrue("Long-press asks before removing", device.wait(androidx.test.uiautomator.Until.hasObject(
                            androidx.test.uiautomator.By.text(app.getString(R.string.budget_remove_message))), 5_000));
                    device.pressBack();
                    T62ScreenshotTest.settle();
                    list.onActivity(activity -> assertEquals(states.size(),
                            ((ViewGroup) activity.findViewById(R.id.records_rows)).getChildCount()));
                }
            }
            if (checks) {
                // Tap opens the form in edit mode with the stored limit.
                var monitor = instrumentation.addMonitor(BudgetFormActivity.class.getName(), null, false);
                try (var list = ActivityScenario.launch(BudgetListActivity.class)) {
                    T62ScreenshotTest.settle();
                    list.onActivity(activity -> ((ViewGroup) activity.findViewById(R.id.records_rows))
                            .getChildAt(0).performClick());
                    var form = instrumentation.waitForMonitorWithTimeout(monitor, 10_000);
                    assertNotNull("Tapping a card opens the form", form);
                    T62ScreenshotTest.settle();
                    instrumentation.runOnMainSync(() -> {
                        assertEquals(app.getString(R.string.budget_edit_title),
                                ((TextView) form.findViewById(R.id.form_title)).getText().toString());
                        assertTrue(((EditText) form.findViewById(R.id.budget_limit)).getText().length() > 0);
                    });
                    T62ScreenshotTest.capture(form, new File(out, tag + "-budget-edit.png"));
                    instrumentation.runOnMainSync(form::finish);
                } finally {
                    instrumentation.removeMonitor(monitor);
                }
            }
            var add = new Intent(app, BudgetFormActivity.class).putExtra("budget_month", "2026-11")
                    .putExtra("budget_taken_categories", new long[0]);
            try (var form = ActivityScenario.launch(add)) {
                T62ScreenshotTest.settle();
                form.onActivity(activity -> {
                    FormChoices chips = activity.findViewById(R.id.form_kind);
                    if (chips.getChildCount() > 0) chips.setSelection(0);
                    T62ScreenshotTest.capture(activity, new File(out, tag + "-budget-add.png"));
                });
            }
        }
    }

    /** Bill rows (dividers between them are skipped). */
    private static List<View> rows(Activity activity, int id) {
        ViewGroup group = activity.findViewById(id);
        List<View> result = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).findViewById(R.id.bill_row) != null) result.add(group.getChildAt(i));
        }
        return result;
    }

    private static String name(View row) {
        return ((TextView) row.findViewById(R.id.record_name)).getText().toString();
    }

    /** Expects the T63.3 demo bills of November 2026, seen on 28 Nov 2026. */
    private void bills(File out, String tag, boolean checks) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        var device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation);
        try (var list = ActivityScenario.launch(BillListActivity.class)) {
            T62ScreenshotTest.settle();
            list.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(out, tag + "-bills.png")));
            list.onActivity(activity -> ((ScrollView) activity.findViewById(R.id.bill_scroll)).fullScroll(View.FOCUS_DOWN));
            T62ScreenshotTest.settle();
            list.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(out, tag + "-bills-end.png")));
            if (checks) {
                // Overdue first, then due today, then upcoming; paid ones in their own section.
                List<String> due = new ArrayList<>();
                List<String> status = new ArrayList<>();
                AtomicReference<String> summary = new AtomicReference<>();
                list.onActivity(activity -> {
                    for (View row : rows(activity, R.id.bill_due_rows)) {
                        due.add(name(row));
                        status.add(((TextView) row.findViewById(R.id.bill_status)).getText().toString());
                    }
                    assertEquals(3, rows(activity, R.id.bill_paid_rows).size());
                    summary.set(String.valueOf(activity.findViewById(R.id.bill_summary).getContentDescription()));
                });
                assertEquals(List.of("CEB electricity", "Mobile plan", "Dialog fibre"), due);
                assertEquals(app.getResources().getQuantityString(R.plurals.bill_overdue_days, 8, 8), status.get(0));
                assertEquals(app.getString(R.string.bill_due_today), status.get(1));
                assertEquals(app.getString(R.string.bill_summary, "36189.00", "29240.00", "6949.00"), summary.get());

                // Mark paid moves the bill down; the Paid toggle moves it back.
                list.onActivity(activity -> rows(activity, R.id.bill_due_rows).get(2).findViewById(R.id.bill_paid)
                        .performClick());
                T62ScreenshotTest.settle();
                list.onActivity(activity -> {
                    assertEquals(2, rows(activity, R.id.bill_due_rows).size());
                    List<View> paid = rows(activity, R.id.bill_paid_rows);
                    assertEquals(4, paid.size());
                    View fibre = null;
                    for (View row : paid) if ("Dialog fibre".equals(name(row))) fibre = row;
                    assertNotNull(fibre);
                    fibre.findViewById(R.id.bill_paid).performClick();
                });
                T62ScreenshotTest.settle();
                list.onActivity(activity -> {
                    assertEquals(3, rows(activity, R.id.bill_due_rows).size());
                    assertEquals(3, rows(activity, R.id.bill_paid_rows).size());
                    assertEquals(summary.get(),
                            String.valueOf(activity.findViewById(R.id.bill_summary).getContentDescription()));
                });

                // Long-press asks before removing; cancel keeps the bill.
                list.onActivity(activity -> assertTrue(rows(activity, R.id.bill_due_rows).get(0)
                        .findViewById(R.id.bill_row).performLongClick()));
                T62ScreenshotTest.settle();
                assertTrue("Long-press asks before removing", device.wait(androidx.test.uiautomator.Until.hasObject(
                        androidx.test.uiautomator.By.text(app.getString(R.string.bill_remove_message))), 5_000));
                device.pressBack();
                T62ScreenshotTest.settle();
                list.onActivity(activity -> assertEquals(3, rows(activity, R.id.bill_due_rows).size()));
            }
        }
        if (checks) {
            // Tap opens the form in edit mode with the stored name and due day.
            var monitor = instrumentation.addMonitor(BillFormActivity.class.getName(), null, false);
            try (var list = ActivityScenario.launch(BillListActivity.class)) {
                T62ScreenshotTest.settle();
                list.onActivity(activity -> rows(activity, R.id.bill_due_rows).get(0)
                        .findViewById(R.id.bill_row).performClick());
                var form = instrumentation.waitForMonitorWithTimeout(monitor, 10_000);
                assertNotNull("Tapping a bill opens the form", form);
                T62ScreenshotTest.settle();
                instrumentation.runOnMainSync(() -> {
                    assertEquals(app.getString(R.string.bill_edit_title),
                            ((TextView) form.findViewById(R.id.bill_form_heading)).getText().toString());
                    assertEquals("CEB electricity", ((EditText) form.findViewById(R.id.form_name)).getText().toString());
                    assertEquals(20, ((DueDayPicker) form.findViewById(R.id.bill_due_day)).getDay());
                });
                T62ScreenshotTest.capture(form, new File(out, tag + "-bill-edit.png"));
                instrumentation.runOnMainSync(form::finish);
            } finally {
                instrumentation.removeMonitor(monitor);
            }
        }
        try (var form = ActivityScenario.launch(BillFormActivity.class)) {
            T62ScreenshotTest.settle();
            form.onActivity(activity -> {
                assertEquals(0, ((DueDayPicker) activity.findViewById(R.id.bill_due_day)).getDay());
                T62ScreenshotTest.capture(activity, new File(out, tag + "-bill-add.png"));
            });
            if (checks) {
                // Saving without a due day shows the day error and stays on the form; picking a day clears it.
                form.onActivity(activity -> {
                    DueDayPicker days = activity.findViewById(R.id.bill_due_day);
                    ((EditText) activity.findViewById(R.id.form_name)).setText("Unsaved check");
                    ((EditText) activity.findViewById(R.id.bill_amount)).setText("10.00");
                    activity.findViewById(R.id.records_save).performClick();
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.bill_due_day_error).getVisibility());
                    days.getChildAt(14).performClick();
                    assertEquals(15, days.getDay());
                    assertEquals(View.GONE, activity.findViewById(R.id.bill_due_day_error).getVisibility());
                    assertTrue(((android.widget.Checkable) days.getChildAt(14)).isChecked());
                    assertTrue(!activity.isFinishing());
                });
            }
        }
    }
}

