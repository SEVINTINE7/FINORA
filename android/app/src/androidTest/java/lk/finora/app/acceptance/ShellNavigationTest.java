package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.main.MainActivity;

@RunWith(AndroidJUnit4.class)
public final class ShellNavigationTest {
    private static final int[] TABS = {R.string.home_tab, R.string.records_tab, R.string.plan_tab,
            R.string.insights_tab, R.string.more_tab};

    @Test public void labeledTabsAndUpPreserveOrigin() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Isolated emulator opt-in required",
                "true".equals(args.getString("finoraIsolatedEmulator")));
        assertTrue("Emulator hardware required",
                "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        var context = instrumentation.getTargetContext();
        assertEquals("lk.finora.app.acceptance", context.getPackageName());
        var tokens = TokenStore.open(context);
        assertNull("Preserve the existing session", tokens.session());
        var originalLocale = AppCompatDelegate.getApplicationLocales();
        int originalMode = AppCompatDelegate.getDefaultNightMode();
        String language = args.getString("shellLanguage", "en");
        int nightMode = "dark".equals(args.getString("shellTheme"))
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(nightMode);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language));
        });
        tokens.save("isolated-shell-access", "isolated-shell-refresh", "USER", language.toUpperCase(java.util.Locale.ROOT));
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                var nav = (BottomNavigationView) activity.findViewById(R.id.shell_navigation);
                assertEquals(5, nav.getMenu().size());
                assertEquals(R.string.home_tab, nav.getSelectedItemId());
                assertEquals(language, activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                assertTrue((activity.getWindow().getAttributes().flags
                        & WindowManager.LayoutParams.FLAG_SECURE) != 0);
                for (int tab : TABS) {
                    View item = nav.findViewById(tab);
                    assertEquals(activity.getString(tab), item.getContentDescription());
                    assertTrue("Tab width below minimum touch target", item.getWidth()
                            >= activity.getResources().getDimensionPixelSize(R.dimen.finora_touch));
                    assertLabelFits((ViewGroup) item, activity.getString(tab));
                }
            });
            openFormAndReturn();
            onView(withId(R.id.shell_add_transaction)).check(matches(isDisplayed()));
            onView(withId(R.string.records_tab)).perform(click());
            openFormAndReturn();
            onView(withText(R.string.sms_csv_import)).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withText(R.string.sms_csv_import)).check(matches(isDisplayed()));
            for (int tab : new int[]{R.string.plan_tab, R.string.insights_tab, R.string.more_tab}) {
                onView(withId(tab)).perform(click());
                scenario.onActivity(activity -> {
                    var nav = (BottomNavigationView) activity.findViewById(R.id.shell_navigation);
                    assertEquals(tab, nav.getSelectedItemId());
                });
                onView(withId(R.id.shell_add_transaction)).check(matches(not(isDisplayed())));
            }
        } finally {
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                AppCompatDelegate.setApplicationLocales(originalLocale);
                AppCompatDelegate.setDefaultNightMode(originalMode);
            });
        }
    }

    private static void openFormAndReturn() {
        onView(withId(R.id.shell_add_transaction)).perform(click());
        onView(withText(R.string.transaction_open_manual)).check(matches(isDisplayed()));
        onView(withId(R.id.shell_toolbar)).check(matches(isDisplayed()));
        onView(withContentDescription(R.string.shell_navigate_up)).perform(click());
    }

    private static void assertLabelFits(ViewGroup item, String title) {
        TextView label = visibleLabel(item, title);
        assertNotNull("Missing visible navigation label " + title, label);
        var layout = label.getLayout();
        assertNotNull(layout);
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals("Truncated label " + title, 0, layout.getEllipsisCount(line));
        }
        assertEquals("Missing label characters " + title, title.length(),
                layout.getLineEnd(layout.getLineCount() - 1));
    }

    private static TextView visibleLabel(ViewGroup group, String title) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (!child.isShown()) continue;
            if (child instanceof TextView text && title.contentEquals(text.getText())) return text;
            if (child instanceof ViewGroup nested) {
                TextView label = visibleLabel(nested, title);
                if (label != null) return label;
            }
        }
        return null;
    }
}
