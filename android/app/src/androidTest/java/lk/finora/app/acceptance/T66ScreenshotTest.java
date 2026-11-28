package lk.finora.app.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.FormChoices;
import lk.finora.app.ui.advice.AdviceActivity;
import lk.finora.app.ui.history.HistoryActivity;
import lk.finora.app.ui.insights.InsightRules;
import lk.finora.app.ui.insights.InsightsActivity;
import lk.finora.app.ui.insights.ScoreDetailsActivity;
import lk.finora.app.ui.insights.ScoreRules;

/**
 * T66 review run against a local dev API and the synthetic demo account (finoraShotEmail / finoraShotPassword),
 * seeded on 28 Nov with October 2026 wants on weekends and income days and score declarations, so October has a
 * score of 81 and three patterns. Checks the score ring text, the five parts, the pattern cards, the Score details
 * answers (stored "no debt" shows as No) and the invalid-date error, the Advice banner, goal chip and rating row,
 * and the History cards newest first. Captures screenshots; never saves anything. Never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T66ScreenshotTest {
    @Test public void insightsAdviceAndHistoryScreens() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T66 run only", "true".equals(args.getString("finoraT66Shots")));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        boolean checks = !"false".equals(args.getString("finoraT66Checks"));
        var tokens = TokenStore.open(app);
        assertNull("Preserve the existing session", tokens.session());
        var login = T62ScreenshotTest.login(args.getString("finoraShotEmail"), args.getString("finoraShotPassword"));
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        tokens.save(login.getString("accessToken"), login.getString("refreshToken"), "USER",
                language.toUpperCase(Locale.ROOT));
        File out = app.getExternalFilesDir("t66");
        assertNotNull(out);
        try {
            insights(out, tag, checks);
            scoreDetails(out, tag, checks);
            advice(out, tag, checks);
            history(out, tag, checks);
        } finally {
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    /** Polls on the main thread until the activity satisfies the condition (network results arrive async). */
    private static <A extends Activity> void await(ActivityScenario<A> scenario, Predicate<A> ready, String name) {
        long end = SystemClock.uptimeMillis() + 90_000;
        var done = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < end) {
            scenario.onActivity(activity -> done.set(ready.test(activity)));
            if (done.get()) break;
            SystemClock.sleep(500);
        }
        assertTrue(name, done.get());
        T62ScreenshotTest.settle();
    }

    private static void scroll(Activity activity, int scrollId, int targetId) {
        ScrollView scroll = activity.findViewById(scrollId);
        View target = activity.findViewById(targetId);
        int top = 0;
        for (View v = target; v != null && v != scroll; v = (View) v.getParent()) top += v.getTop();
        scroll.scrollTo(0, Math.max(0, top - 24));
    }

    private void insights(File out, String tag, boolean checks) {
        try (var screen = ActivityScenario.launch(InsightsActivity.class)) {
            await(screen, a -> a.findViewById(R.id.switch_previous).isEnabled()
                    && ((ViewGroup) a.findViewById(R.id.insights_parts)).getChildCount() == 5, "current month loaded");
            // Insights opens on the current month; the seeded patterns are in October.
            screen.onActivity(a -> a.findViewById(R.id.switch_previous).performClick());
            await(screen, a -> ((ViewGroup) a.findViewById(R.id.insights_results)).getChildCount() == 3
                    && ((ViewGroup) a.findViewById(R.id.insights_parts)).getChildCount() == 5, "October loaded");
            screen.onActivity(a -> {
                if (checks) {
                    assertEquals(a.getString(R.string.score_total, "81"),
                            ((TextView) a.findViewById(R.id.insights_score_text)).getText().toString());
                    assertEquals("81", ((TextView) a.findViewById(R.id.insights_score_value)).getText().toString());
                    View wants = ((ViewGroup) a.findViewById(R.id.insights_results)).getChildAt(0);
                    Locale locale = a.getResources().getConfiguration().getLocales().get(0);
                    String share = InsightRules.percent(new BigDecimal("0.4300"), locale);
                    assertTrue(((TextView) wants.findViewById(R.id.insight_text)).getText().toString().contains(share));
                    assertTrue(String.valueOf(wants.findViewById(R.id.insight_chart).getContentDescription()).contains(share));
                    View first = ((ViewGroup) a.findViewById(R.id.insights_parts)).getChildAt(0);
                    assertTrue(String.valueOf(first.findViewById(R.id.part_row).getContentDescription())
                            .startsWith(a.getString(R.string.score_spending)));
                    assertEquals(View.VISIBLE, a.findViewById(R.id.insights_advice).getVisibility());
                }
                T62ScreenshotTest.capture(a, new File(out, tag + "-insights.png"));
                scroll(a, R.id.insights_scroll, R.id.insights_parts_header);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, tag + "-insights-parts.png"));
                scroll(a, R.id.insights_scroll, R.id.insights_patterns_header);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, tag + "-insights-patterns.png"));
                ((ScrollView) a.findViewById(R.id.insights_scroll)).fullScroll(View.FOCUS_DOWN);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, tag + "-insights-end.png")));
        }
    }

    private void scoreDetails(File out, String tag, boolean checks) {
        var app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var intent = new Intent(app, ScoreDetailsActivity.class).putExtra(ScoreDetailsActivity.MONTH, "2026-10");
        try (ActivityScenario<ScoreDetailsActivity> screen = ActivityScenario.launch(intent)) {
            await(screen, a -> a.findViewById(R.id.score_save).isEnabled(), "declarations loaded");
            screen.onActivity(a -> {
                if (checks) {
                    assertEquals("30000.00", ((EditText) a.findViewById(R.id.score_reserve)).getText().toString());
                    // Stored noApplicableDebt / noApplicableGoals = true read as "No" to the positive questions.
                    assertEquals(ScoreRules.NO, ((FormChoices) a.findViewById(R.id.score_debt)).getSelectedItemPosition());
                    assertEquals(ScoreRules.NO, ((FormChoices) a.findViewById(R.id.score_goals)).getSelectedItemPosition());
                    assertEquals(View.VISIBLE, a.findViewById(R.id.score_date_clear).getVisibility());
                }
                T62ScreenshotTest.capture(a, new File(out, tag + "-score-details.png"));
                // An amount without a date is refused in place; nothing is sent.
                a.findViewById(R.id.score_date_clear).performClick();
                a.findViewById(R.id.score_save).performClick();
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                if (checks) {
                    assertTrue(!a.isFinishing());
                    assertNotNull(((TextInputLayout) a.findViewById(R.id.score_reserve_box)).getError());
                }
                T62ScreenshotTest.capture(a, new File(out, tag + "-score-details-error.png"));
            });
        }
    }

    private void advice(File out, String tag, boolean checks) {
        var app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var intent = new Intent(app, AdviceActivity.class).putExtra(AdviceActivity.MONTH, "2026-10");
        try (ActivityScenario<AdviceActivity> screen = ActivityScenario.launch(intent)) {
            await(screen, a -> ((ViewGroup) a.findViewById(R.id.advice_items)).getChildCount() > 0, "advice loaded");
            screen.onActivity(a -> {
                if (checks) {
                    assertEquals(a.getString(R.string.advice_rules),
                            ((TextView) a.findViewById(R.id.advice_banner_title)).getText().toString());
                    ViewGroup goals = a.findViewById(R.id.advice_goals);
                    Chip near = (Chip) goals.getChildAt(0);
                    assertEquals("Laptop for study", near.getText().toString());
                    assertTrue("Nearest goal chosen", near.isChecked());
                    assertEquals(a.getString(R.string.advice_goal_none),
                            ((Chip) goals.getChildAt(goals.getChildCount() - 1)).getText().toString());
                    View first = ((ViewGroup) a.findViewById(R.id.advice_items)).getChildAt(0);
                    assertEquals(a.getString(R.string.advice_section_goal),
                            ((TextView) first.findViewById(R.id.advice_item_title)).getText().toString());
                    assertTrue(((ViewGroup) first.findViewById(R.id.advice_figures)).getChildCount() > 0);
                    assertEquals(View.VISIBLE, a.findViewById(R.id.advice_rating_card).getVisibility());
                    assertTrue(!a.findViewById(R.id.advice_submit).isEnabled());
                }
                T62ScreenshotTest.capture(a, new File(out, tag + "-advice.png"));
                ((ScrollView) a.findViewById(R.id.advice_scroll)).scrollTo(0,
                        ((ViewGroup) a.findViewById(R.id.advice_items)).getChildAt(1).getTop()
                                + a.findViewById(R.id.advice_items).getTop());
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, tag + "-advice-mid.png"));
                // Choosing a rating enables Submit; the test never submits.
                MaterialButtonToggleGroup rating = a.findViewById(R.id.advice_rating);
                rating.check(rating.getChildAt(3).getId());
                if (checks) assertTrue(a.findViewById(R.id.advice_submit).isEnabled());
                ((ScrollView) a.findViewById(R.id.advice_scroll)).fullScroll(View.FOCUS_DOWN);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, tag + "-advice-end.png")));
        }
    }

    private void history(File out, String tag, boolean checks) {
        try (var screen = ActivityScenario.launch(HistoryActivity.class)) {
            await(screen, a -> ((ViewGroup) a.findViewById(R.id.history_rows)).getChildCount() > 0, "history loaded");
            screen.onActivity(a -> {
                if (checks) {
                    ViewGroup rows = a.findViewById(R.id.history_rows);
                    assertEquals(View.VISIBLE, a.findViewById(R.id.history_chart_card).getVisibility());
                    assertTrue(String.valueOf(a.findViewById(R.id.history_chart).getContentDescription())
                            .startsWith(a.getString(R.string.history_chart)));
                    // Newest first: October (score 81) leads.
                    assertEquals("81", ((TextView) rows.getChildAt(0).findViewById(R.id.month_badge_value))
                            .getText().toString());
                    assertTrue(((ViewGroup) rows.getChildAt(0).findViewById(R.id.month_patterns)).getChildCount() == 3);
                }
                T62ScreenshotTest.capture(a, new File(out, tag + "-history.png"));
                scroll(a, R.id.history_scroll, R.id.history_months_header);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, tag + "-history-months.png"));
                ((ScrollView) a.findViewById(R.id.history_scroll)).fullScroll(View.FOCUS_DOWN);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, tag + "-history-end.png")));
        }
    }
}
