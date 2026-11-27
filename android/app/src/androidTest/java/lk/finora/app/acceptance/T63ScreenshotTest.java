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
import lk.finora.app.ui.budgets.BudgetFormActivity;
import lk.finora.app.ui.budgets.BudgetListActivity;

/**
 * T63 review run against a local dev API and a synthetic account (finoraShotEmail / finoraShotPassword).
 * Checks the budget card order, tap-to-edit and long-press remove (cancelled), and captures screenshots.
 * Never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T63ScreenshotTest {
    @Test public void budgetScreens() throws Exception {
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
        } finally {
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }
}
