package lk.finora.app.acceptance;

import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
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

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import lk.finora.app.R;
import lk.finora.app.data.api.PlanningTransportFixture;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.transactions.TransactionActivity;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(AndroidJUnit4.class)
public final class FinancialListCardTest {
    @Test public void exactAmountAndConfirmedRemoval() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Isolated emulator opt-in required", "true".equals(args.getString("finoraIsolatedEmulator")));
        assertTrue("Emulator required", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        var target = instrumentation.getTargetContext();
        assertEquals("lk.finora.app.acceptance", target.getPackageName());
        var tokens = TokenStore.open(target);
        assertNull("Preserve an existing session", tokens.session());
        var localeManager = Build.VERSION.SDK_INT >= 33
                ? target.getSystemService(android.app.LocaleManager.class) : null;
        var oldLocales = localeManager == null ? null : localeManager.getApplicationLocales();
        var oldLocale = AppCompatDelegate.getApplicationLocales();
        int oldTheme = AppCompatDelegate.getDefaultNightMode();
        String locale = args.getString("listLanguage", "en");
        int mode = "dark".equals(args.getString("listTheme"))
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(mode);
            if (Build.VERSION.SDK_INT >= 33 && localeManager != null) {
                localeManager.setApplicationLocales(android.os.LocaleList.forLanguageTags(locale));
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale));
            }
        });
        tokens.save("isolated-list-access", "isolated-list-refresh", "USER", locale.toUpperCase(Locale.ROOT));
        var session = tokens.session();
        var deletions = new AtomicInteger();
        var loads = new AtomicInteger();
        var allowFirstResponse = new CountDownLatch(1);
        try (var transport = new PlanningTransportFixture(tokens, chain -> {
            var request = chain.request();
            assertEquals("Bearer isolated-list-access", request.header("Authorization"));
            String route = request.method() + " " + request.url().encodedPath();
            if ("GET /api/v1/transactions".equals(route) && loads.getAndIncrement() == 0) {
                try {
                    if (!allowFirstResponse.await(10, TimeUnit.SECONDS)) {
                        throw new java.io.IOException("Initial fixture wait expired");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new java.io.InterruptedIOException();
                }
                return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(503).message("Fixture unavailable").body(ResponseBody.create("", null)).build();
            }
            String response = switch (route) {
                case "GET /api/v1/accounts", "GET /api/v1/categories" -> "[]";
                case "GET /api/v1/transactions" -> deletions.get() == 0
                        ? "{\"page\":0,\"totalPages\":1,\"items\":[{\"id\":8,\"accountId\":1,"
                            + "\"type\":\"EXPENSE\",\"amount\":1250.75,\"merchant\":\"Synthetic shop\","
                            + "\"txnDate\":\"2026-11-18\"}]}"
                        : "{\"page\":0,\"totalPages\":0,\"items\":[]}";
                case "DELETE /api/v1/transactions/8" -> {
                    deletions.incrementAndGet();
                    yield "";
                }
                default -> throw new IllegalArgumentException("Unexpected fixture route " + route);
            };
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(response.isEmpty() ? 204 : 200).message("OK")
                    .body(ResponseBody.create(response, null)).build();
        }); var scenario = ActivityScenario.launch(TransactionActivity.class)) {
            var device = UiDevice.getInstance(instrumentation);
            String[] labels = new String[4];
            scenario.onActivity(activity -> {
                assertEquals(locale, activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
                labels[0] = lk.finora.app.ui.Money.signed(activity, new java.math.BigDecimal("1250.75"), false);
                labels[1] = activity.getString(R.string.records_empty);
                labels[2] = activity.getString(R.string.records_loading);
                labels[3] = activity.getString(R.string.records_failed);
            });
            assertTrue(device.wait(Until.hasObject(By.text(labels[2])), 10_000));
            allowFirstResponse.countDown();
            assertTrue(device.wait(Until.hasObject(By.text(labels[3])), 10_000));
            onView(withText(R.string.records_retry)).perform(click());
            assertTrue(device.wait(Until.hasObject(By.text("Synthetic shop")), 10_000));
            onView(allOf(withId(R.id.row_amount), withText(labels[0]))).check(matches(isDisplayed()));
            // T62/D65: swipe a row to remove it; cancelling restores the row and deletes nothing.
            onView(withText("Synthetic shop")).perform(androidx.test.espresso.action.ViewActions.swipeLeft());
            onView(withText(R.string.transaction_remove)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());
            assertEquals(0, deletions.get());
            onView(withText("Synthetic shop")).check(matches(isDisplayed()));
            // Long-press is the non-gesture route to the same confirmation.
            onView(withText("Synthetic shop")).perform(androidx.test.espresso.action.ViewActions.longClick());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            assertTrue(device.wait(Until.hasObject(By.text(labels[1])), 10_000));
            assertEquals(1, deletions.get());
        } finally {
            allowFirstResponse.countDown();
            tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                if (Build.VERSION.SDK_INT >= 33 && localeManager != null) {
                    localeManager.setApplicationLocales(oldLocales);
                } else {
                    AppCompatDelegate.setApplicationLocales(oldLocale);
                }
                AppCompatDelegate.setDefaultNightMode(oldTheme);
            });
        }
    }
}
