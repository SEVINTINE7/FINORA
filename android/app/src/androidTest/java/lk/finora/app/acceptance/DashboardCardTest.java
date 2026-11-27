package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.containsString;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import lk.finora.app.R;
import lk.finora.app.data.api.PlanningTransportFixture;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.main.MainActivity;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(AndroidJUnit4.class)
public final class DashboardCardTest {
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    @Test public void rendersExactAmountsAndUpdatesMonth() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var arguments = InstrumentationRegistry.getArguments();
        assumeTrue("Isolated emulator opt-in required", "true".equals(arguments.getString("finoraIsolatedEmulator")));
        assertTrue("Emulator required", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        var context = instrumentation.getTargetContext();
        assertEquals("lk.finora.app.acceptance", context.getPackageName());
        var tokens = TokenStore.open(context);
        assertNull("Preserve the existing session", tokens.session());
        var oldLocale = AppCompatDelegate.getApplicationLocales();
        int oldTheme = AppCompatDelegate.getDefaultNightMode();
        String locale = arguments.getString("dashboardLanguage", "en");
        int theme = "dark".equals(arguments.getString("dashboardTheme"))
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(theme);
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale));
        });
        tokens.save("isolated-dashboard-access", "isolated-dashboard-refresh", "USER", locale.toUpperCase(Locale.ROOT));
        var testSession = tokens.session();
        LocalDate today = LocalDate.now(COLOMBO);
        YearMonth current = YearMonth.from(today);
        try (var transport = new PlanningTransportFixture(tokens, chain -> {
            var request = chain.request();
            assertEquals("GET", request.method());
            assertEquals("Bearer isolated-dashboard-access", request.header("Authorization"));
            String path = request.url().encodedPath();
            String queryMonth = request.url().queryParameter("month");
            String from = request.url().queryParameter("from");
            YearMonth month = queryMonth != null ? YearMonth.parse(queryMonth)
                    : from != null ? YearMonth.from(LocalDate.parse(from)) : current;
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(
                            fixture(path, month, today, current.equals(month)), null)).build();
        }); var scenario = ActivityScenario.launch(MainActivity.class)) {
            var device = UiDevice.getInstance(instrumentation);
            String[] expected = new String[5];
            scenario.onActivity(activity -> {
                assertEquals(locale, activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
                expected[0] = activity.getString(R.string.dashboard_net, "75.25");
                expected[1] = activity.getString(R.string.score_total, "72");
                expected[2] = activity.getString(R.string.budget_left, "25.25", "100.00", "74.75");
                expected[3] = activity.getString(R.string.dashboard_category_amount,
                        "si".equals(locale) ? "ආහාර" : "Food", "25.25");
                expected[4] = activity.getString(R.string.score_unavailable);
            });
            // T62/D65: amounts are shown grouped ("Rs. 75.25"); exact plain values stay in spoken labels.
            assertTrue("Monthly totals visible", device.wait(Until.hasObject(By.desc(expected[0])), 10_000));
            checkText("Synthetic");
            checkText(expected[1]);
            onView(withContentDescription(containsString(expected[2]))).perform(scrollTo())
                    .check(matches(isDisplayed()));
            checkText("Synthetic bill");
            checkText("Synthetic merchant");
            checkText("Synthetic focus goal");
            onView(withContentDescription(expected[3])).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withContentDescription(R.string.dashboard_previous)).perform(scrollTo(), click());
            checkText(expected[4]);
        } finally {
            tokens.clear(testSession);
            instrumentation.runOnMainSync(() -> {
                AppCompatDelegate.setApplicationLocales(oldLocale);
                AppCompatDelegate.setDefaultNightMode(oldTheme);
            });
        }
    }

    private static void checkText(String text) {
        onView(withText(text)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    private static String fixture(String path, YearMonth month, LocalDate today, boolean current) {
        return switch (path) {
            case "/api/v1/dashboard" -> {
                var trend = new ArrayList<String>();
                for (int i = 5; i > 0; i--) {
                    trend.add("{\"month\":\"%s\",\"income\":0,\"expense\":0,\"net\":0}".formatted(month.minusMonths(i)));
                }
                String total = "\"month\":\"%s\",\"income\":100.50,\"expense\":25.25,\"net\":75.25".formatted(month);
                trend.add("{" + total + "}");
                yield "{" + total + ",\"byCategory\":[{\"categoryId\":1,\"name\":\"Food\",\"nameSi\":\"ආහාර\","
                        + "\"total\":25.25}],\"trend\":[" + String.join(",", trend) + "]}";
            }
            case "/api/v1/me" -> "{\"fullName\":\"Synthetic Person\",\"email\":\"synthetic@example.invalid\"}";
            case "/api/v1/score" -> "{\"month\":\"%s\",\"total\":%s,\"modelVersion\":\"D20-v1\"}"
                    .formatted(month, current ? "72" : "null");
            case "/api/v1/categories" -> "[{\"id\":1,\"name\":\"Food\",\"nameSi\":\"ආහාර\"}]";
            case "/api/v1/budgets" -> "[{\"id\":1,\"categoryId\":1,\"month\":\"%s\",\"limitAmount\":100.00,"
                    .formatted(month) + "\"used\":25.25,\"remaining\":74.75,\"state\":\"OK\"}]";
            case "/api/v1/bills" -> current
                    ? "[{\"id\":1,\"name\":\"Synthetic bill\",\"dueDay\":%d,\"dueDate\":\"%s\",\"amount\":19.99}]"
                            .formatted(today.getDayOfMonth(), today) : "[]";
            case "/api/v1/transactions" -> "{\"page\":0,\"totalPages\":1,\"items\":[{\"id\":1,\"accountId\":1,\"type\":\"EXPENSE\","
                    + "\"amount\":25.25,\"merchant\":\"Synthetic merchant\",\"txnDate\":\"" + month.atDay(1) + "\"}]}";
            case "/api/v1/goals" -> "[{\"id\":1,\"name\":\"Synthetic focus goal\",\"status\":\"ACTIVE\",\"saved\":10.10,"
                    + "\"remaining\":89.90,\"targetAmount\":100.00,\"progressPercent\":10}]";
            default -> throw new IllegalArgumentException("Unexpected fixture route " + path);
        };
    }
}
