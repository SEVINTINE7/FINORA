package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import lk.finora.app.R;
import lk.finora.app.data.api.PlanningTransportFixture;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.FormChoices;
import lk.finora.app.ui.accounts.AccountFormActivity;
import lk.finora.app.ui.bills.BillFormActivity;
import lk.finora.app.ui.budgets.BudgetFormActivity;
import lk.finora.app.ui.categories.CategoryFormActivity;
import lk.finora.app.ui.goals.GoalFormActivity;
import lk.finora.app.ui.transactions.TransactionFormActivity;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(AndroidJUnit4.class)
public final class FinancialFormTest {
    @Test public void chipsDateInlineErrorsAndExactSave() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Isolated emulator opt-in required", "true".equals(args.getString("finoraIsolatedEmulator")));
        assertTrue("Android 13+ emulator required", Build.VERSION.SDK_INT >= 33
                && ("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE)));
        var target = instrumentation.getTargetContext();
        assertEquals("lk.finora.app.acceptance", target.getPackageName());
        var tokens = TokenStore.open(target);
        assertNull("Preserve an existing session", tokens.session());
        var localeManager = target.getSystemService(android.app.LocaleManager.class);
        var oldLocales = localeManager.getApplicationLocales();
        int oldTheme = AppCompatDelegate.getDefaultNightMode();
        String locale = args.getString("formLanguage", "en");
        int mode = "dark".equals(args.getString("formTheme"))
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(mode);
            localeManager.setApplicationLocales(android.os.LocaleList.forLanguageTags(locale));
        });
        tokens.save("isolated-form-access", "isolated-form-refresh", "USER", locale.toUpperCase(Locale.ROOT));
        var session = tokens.session();
        var posted = new AtomicReference<String>();
        try (var transport = new PlanningTransportFixture(tokens, chain -> {
            var request = chain.request();
            assertEquals("Bearer isolated-form-access", request.header("Authorization"));
            String response = switch (request.method() + " " + request.url().encodedPath()) {
                case "GET /api/v1/accounts" -> "[{\"id\":1,\"name\":\"Synthetic wallet\",\"type\":\"CASH\",\"openingBalance\":0}]";
                case "GET /api/v1/categories" -> "[{\"id\":2,\"name\":\"Food\",\"nameSi\":\"ආහාර\",\"kind\":\"EXPENSE\"}]";
                case "POST /api/v1/transactions" -> {
                    var body = new okio.Buffer();
                    request.body().writeTo(body);
                    assertTrue(posted.compareAndSet(null, body.readUtf8()));
                    yield "{\"id\":1,\"accountId\":1,\"categoryId\":2,\"type\":\"EXPENSE\","
                            + "\"amount\":1250.75,\"txnDate\":\"2026-11-18\"}";
                }
                default -> throw new IllegalArgumentException("Unexpected fixture route " + request.url().encodedPath());
            };
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                    .message("OK").body(ResponseBody.create(response, null)).build();
        })) {
            verifyForm(AccountFormActivity.class, R.id.account_balance, R.id.form_kind, true);
            verifyForm(BillFormActivity.class, R.id.bill_amount, R.id.form_kind, true);
            verifyForm(BudgetFormActivity.class, R.id.budget_limit, R.id.form_kind, true);
            verifyForm(CategoryFormActivity.class, R.id.category_name_si, R.id.form_kind, false);
            verifyForm(GoalFormActivity.class, R.id.goal_target, 0, true);
            verifyTransaction(locale, target.getPackageName(), posted);
        } finally {
            tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                localeManager.setApplicationLocales(oldLocales);
                AppCompatDelegate.setDefaultNightMode(oldTheme);
            });
        }
    }

    private static void verifyForm(Class<? extends AppCompatActivity> form, int fieldId, int chipsId,
                                   boolean money) {
        try (var scenario = ActivityScenario.launch(form)) {
            scenario.onActivity(activity -> {
                var field = activity.<EditText>findViewById(fieldId);
                var outline = (TextInputLayout) field.getParent().getParent();
                assertEquals(TextInputLayout.BOX_BACKGROUND_OUTLINE, outline.getBoxBackgroundMode());
                if (money) assertEquals(activity.getString(R.string.form_rupees_prefix), outline.getPrefixText().toString());
                if (chipsId != 0) assertTrue(activity.findViewById(chipsId) instanceof FormChoices);
                assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            });
        }
    }

    private static void verifyTransaction(String locale, String packageName, AtomicReference<String> posted)
            throws Exception {
        try (var scenario = ActivityScenario.launch(TransactionFormActivity.class)) {
            var instrumentation = InstrumentationRegistry.getInstrumentation();
            var device = UiDevice.getInstance(instrumentation);
            assertTrue(device.wait(Until.hasObject(By.res(packageName, "form_type")), 10_000));
            scenario.onActivity(activity -> {
                assertEquals(locale, activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
                var amount = activity.<EditText>findViewById(R.id.form_amount);
                var outline = (TextInputLayout) amount.getParent().getParent();
                assertEquals(TextInputLayout.BOX_BACKGROUND_OUTLINE, outline.getBoxBackgroundMode());
                assertEquals(activity.getString(R.string.form_rupees_prefix), outline.getPrefixText().toString());
                ((Spinner) activity.findViewById(R.id.form_account)).setSelection(1);
            });
            onView(allOf(withText(R.string.category_expense), isEnabled())).perform(scrollTo(), click());
            onView(withText("si".equals(locale) ? "ආහාර" : "Food")).perform(scrollTo(), click());
            onView(withId(R.id.form_save)).perform(scrollTo(), click());
            onView(withId(R.id.form_amount_error)).perform(scrollTo()).check(matches(isDisplayed()));
            assertNull(posted.get());
            scenario.onActivity(activity -> activity.<EditText>findViewById(R.id.form_amount).setText("1250.75"));
            onView(withId(R.id.form_date)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                var picker = activity.getSupportFragmentManager().getFragments().stream()
                        .filter(MaterialDatePicker.class::isInstance).map(MaterialDatePicker.class::cast)
                        .findFirst().orElseThrow();
                assertTrue((picker.requireDialog().getWindow().getAttributes().flags
                        & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            });
            onView(withId(com.google.android.material.R.id.confirm_button)).inRoot(isDialog()).perform(click());
            var date = new AtomicReference<String>();
            scenario.onActivity(activity -> date.set(activity.<android.widget.Button>findViewById(R.id.form_date)
                    .getText().toString()));
            scenario.recreate();
            onView(withId(R.id.form_amount)).check(matches(withText("1250.75")));
            scenario.onActivity(activity -> {
                var categories = activity.<FormChoices>findViewById(R.id.form_category);
                assertEquals("si".equals(locale) ? "ආහාර" : "Food", categories.getSelectedItem().toString());
            });
            onView(withId(R.id.form_save)).perform(scrollTo(), click());
            assertTrue(device.wait(Until.gone(By.res(packageName, "form_save")), 10_000));
            assertNotNull(posted.get());
            var payload = new JSONObject(posted.get());
            assertEquals(new BigDecimal("1250.75"), new BigDecimal(payload.getString("amount")));
            assertEquals(2, payload.getLong("categoryId"));
            assertEquals(date.get(), payload.getString("txnDate"));
        }
    }
}
