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
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.FormChoices;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.accounts.AccountListActivity;

/**
 * T65 review run against a local dev API and a synthetic account (finoraShotEmail / finoraShotPassword) holding
 * the T65 demo accounts: Commercial Bank, Cash wallet, Debit card in use and "Old savings" archived. Checks the
 * In use / Archived sections, the archived chip without a remove button, tap-to-edit, the remove confirmation and
 * the two-column type tiles, and captures screenshots. Never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T65ScreenshotTest {
    @Test public void accountAndCategoryScreens() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T65 run only", "true".equals(args.getString("finoraT65Shots")));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        boolean checks = !"false".equals(args.getString("finoraT65Checks"));
        var tokens = TokenStore.open(app);
        assertNull("Preserve the existing session", tokens.session());
        var login = T62ScreenshotTest.login(args.getString("finoraShotEmail"), args.getString("finoraShotPassword"));
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        tokens.save(login.getString("accessToken"), login.getString("refreshToken"), "USER",
                language.toUpperCase(Locale.ROOT));
        File out = app.getExternalFilesDir("t65");
        assertNotNull(out);
        try {
            accounts(out, tag, checks);
        } finally {
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    private static List<String> names(Activity activity, int rowsId) {
        List<String> names = new ArrayList<>();
        ViewGroup rows = activity.findViewById(rowsId);
        for (int i = 0; i < rows.getChildCount(); i++) {
            names.add(((TextView) rows.getChildAt(i).findViewById(R.id.record_name)).getText().toString());
        }
        return names;
    }

    private void accounts(File out, String tag, boolean checks) throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        var device = UiDevice.getInstance(instrumentation);
        var monitor = instrumentation.addMonitor(AccountFormActivity.class.getName(), null, false);
        try (var list = ActivityScenario.launch(AccountListActivity.class)) {
            T62ScreenshotTest.settle();
            list.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(out, tag + "-accounts.png")));
            if (checks) {
                list.onActivity(activity -> {
                    assertEquals(List.of("Commercial Bank", "Cash wallet", "Debit card"), names(activity, R.id.records_rows));
                    assertEquals(List.of("Old savings"), names(activity, R.id.account_archived_rows));
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.account_archived_note).getVisibility());
                    assertEquals(activity.getResources().getQuantityString(R.plurals.account_count, 3, 3),
                            ((TextView) activity.findViewById(R.id.account_in_use_count)).getText().toString());
                    View inUse = ((ViewGroup) activity.findViewById(R.id.records_rows)).getChildAt(0);
                    assertEquals(View.VISIBLE, inUse.findViewById(R.id.record_remove).getVisibility());
                    assertEquals(View.GONE, inUse.findViewById(R.id.account_archived).getVisibility());
                    String spoken = String.valueOf(inUse.findViewById(R.id.account_open).getContentDescription());
                    assertEquals("Commercial Bank. " + app.getString(R.string.account_summary,
                            app.getString(R.string.account_bank), "45000.00"), spoken);
                    View archived = ((ViewGroup) activity.findViewById(R.id.account_archived_rows)).getChildAt(0);
                    assertEquals(View.GONE, archived.findViewById(R.id.record_remove).getVisibility());
                    assertEquals(View.VISIBLE, archived.findViewById(R.id.account_archived).getVisibility());
                });
                // The remove button asks first; cancelling keeps the account.
                list.onActivity(activity -> ((ViewGroup) activity.findViewById(R.id.records_rows)).getChildAt(2)
                        .findViewById(R.id.record_remove).performClick());
                var cancel = device.wait(Until.findObject(By.res("android:id/button2")), 5_000);
                assertNotNull("Remove asks for confirmation", cancel);
                cancel.click();
                T62ScreenshotTest.settle();
                list.onActivity(activity -> assertEquals(3, ((ViewGroup) activity.findViewById(R.id.records_rows)).getChildCount()));
            }
            list.onActivity(activity -> ((ScrollView) activity.findViewById(R.id.account_scroll)).fullScroll(View.FOCUS_DOWN));
            T62ScreenshotTest.settle();
            list.onActivity(activity -> T62ScreenshotTest.capture(activity, new File(out, tag + "-accounts-end.png")));

            // Tap the first card: the form opens in edit mode with its values and the Bank tile.
            list.onActivity(activity -> ((ViewGroup) activity.findViewById(R.id.records_rows)).getChildAt(0)
                    .findViewById(R.id.account_open).performClick());
            var form = instrumentation.waitForMonitorWithTimeout(monitor, 10_000);
            assertNotNull("Tapping an account opens it", form);
            T62ScreenshotTest.settle();
            instrumentation.runOnMainSync(() -> {
                if (checks) {
                    assertEquals(app.getString(R.string.account_edit),
                            ((TextView) form.findViewById(R.id.account_form_heading)).getText().toString());
                    assertEquals("Commercial Bank", ((EditText) form.findViewById(R.id.form_name)).getText().toString());
                    assertEquals("45000.00", ((EditText) form.findViewById(R.id.account_balance)).getText().toString());
                    FormChoices type = form.findViewById(R.id.form_kind);
                    assertEquals(0, type.getSelectedItemPosition());
                    // Two equal tiles per row.
                    View first = type.getChildAt(0);
                    View second = type.getChildAt(1);
                    View third = type.getChildAt(2);
                    assertEquals(first.getWidth(), second.getWidth());
                    assertEquals(first.getTop(), second.getTop());
                    assertTrue(third.getTop() > first.getTop());
                    assertTrue(first.getWidth() > type.getWidth() / 3);
                }
                T62ScreenshotTest.capture(form, new File(out, tag + "-account-edit.png"));
                form.finish();
            });
        } finally {
            instrumentation.removeMonitor(monitor);
        }

        try (var add = ActivityScenario.launch(AccountFormActivity.class)) {
            T62ScreenshotTest.settle();
            add.onActivity(activity -> {
                if (checks) {
                    assertEquals(app.getString(R.string.account_add),
                            ((TextView) activity.findViewById(R.id.account_form_heading)).getText().toString());
                    assertEquals(View.GONE, activity.findViewById(R.id.records_retry).getVisibility());
                }
                T62ScreenshotTest.capture(activity, new File(out, tag + "-account-add.png"));
            });
            if (checks) {
                // An empty name is refused in place, without leaving the form.
                add.onActivity(activity -> activity.findViewById(R.id.records_save).performClick());
                T62ScreenshotTest.settle();
                add.onActivity(activity -> {
                    assertTrue(!activity.isFinishing());
                    T62ScreenshotTest.capture(activity, new File(out, tag + "-account-add-error.png"));
                });
            }
        }

        // The archived account still opens for editing.
        var archived = new Intent(app, AccountFormActivity.class).putExtra(AccountListActivity.ITEM_ID, 6L);
        try (var edit = ActivityScenario.launch(archived)) {
            T62ScreenshotTest.settle();
            edit.onActivity(activity -> {
                if (checks) {
                    assertEquals("Old savings", ((EditText) activity.findViewById(R.id.form_name)).getText().toString());
                    assertTrue(activity.findViewById(R.id.records_save).isEnabled());
                }
            });
        }
    }
}
