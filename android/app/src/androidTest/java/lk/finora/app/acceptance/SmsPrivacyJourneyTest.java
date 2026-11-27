package lk.finora.app.acceptance;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.UserManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.login.LoginActivity;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SmsPrivacyJourneyTest {
    private static final String ACCOUNT = "Synthetic SMS wallet";
    private static final String[] AMOUNTS = {"1250.75", "4500.25", "750.50"};
    private Context app;
    private UiDevice device;

    @Test public void pasteAndInboxUseStructuredRequests() throws Exception {
        var runner = InstrumentationRegistry.getInstrumentation();
        var args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Explicit wire capture opt-in required", "true".equals(args.getString("finoraSmsCapture")));
        app = runner.getTargetContext();
        assertEquals("lk.finora.app.acceptance", app.getPackageName());
        assertTrue(BuildConfig.DEBUG && "http://127.0.0.1:8080/".equals(BuildConfig.API_BASE_URL));
        assertTrue("Emulator required", "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE));
        assertEquals(args.getString("finoraTestUserSerial"), Long.toString(app.getSystemService(UserManager.class)
                .getSerialNumberForUser(Process.myUserHandle())));
        List<String> input;
        try (var reader = new BufferedReader(new InputStreamReader(
                app.openFileInput("t55-sms-input"), StandardCharsets.UTF_8))) {
            input = reader.lines().collect(java.util.stream.Collectors.toList());
        } finally {
            app.deleteFile("t55-sms-input");
        }
        assertEquals("Expected one disposable login and three fixtures", 5, input.size());
        assertTrue("Expected disposable SMS identity", input.get(0).matches("finora-t55-sms-[a-z0-9]+@example\\.invalid"));
        device = UiDevice.getInstance(runner);
        var sessions = TokenStore.open(app);
        var prior = sessions.session();
        if (prior != null) assertTrue(sessions.clear(prior));
        try (var scenario = ActivityScenario.launch(LoginActivity.class)) {
            enter(R.id.email, input.get(0));
            enter(R.id.password, input.get(1));
            click(R.id.sign_in);
            waitFor(By.desc(app.getString(R.string.records_tab)), "Login");
            action(onView(withId(R.string.records_tab)), View::performClick);
            // T62/D65: SMS access opens from the Records header's import button.
            action(onView(withId(R.id.records_import)), View::performClick);
            onView(withText(R.string.sms_access_title)).inRoot(isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            click(R.id.sms_paste_entry_action);
            for (int i = 0; i < AMOUNTS.length; i++) {
                paste(input.get(i + 2));
                waitFor(By.text(expected(i)), "Paste extracted fields " + i);
                if (i == 0) {
                    openPreview(R.id.sms_paste_preview);
                    action(onView(withId(android.R.id.button2)).inRoot(isDialog()), View::performClick);
                }
                openPreview(R.id.sms_paste_preview);
                selectRow(i);
                save();
                if (i == 0) {
                    waitFor(button(R.string.sms_batch_retry), "Explicit retry shown");
                    action(onView(withText(R.string.sms_batch_retry)).inRoot(isDialog()), View::performClick);
                }
                waitFor(By.textContains(app.getString(R.string.sms_batch_saved, i == 0 ? 0 : 1)), "Paste save " + i);
                action(onView(withId(android.R.id.button1)).inRoot(isDialog()), View::performClick);
            }
            paste("T55_DIAGNOSTIC_RAW_MARKER");
            waitFor(button(R.string.sms_report_problem), "Diagnostic action");
            click(R.id.sms_diagnostic_report);
            action(onView(withId(android.R.id.button2)).inRoot(isDialog()), View::performClick);
            click(R.id.sms_diagnostic_report);
            action(onView(withId(android.R.id.button1)).inRoot(isDialog()), View::performClick);
            waitFor(By.text(app.getString(R.string.sms_report_sent)), "Diagnostic sent");
            pressBack();
            click(R.id.sms_scan);
            for (int i = 0; i < AMOUNTS.length; i++) {
                waitFor(By.textContains(expected(i)), "Inbox extracted fields " + i);
            }
            openPreview(R.id.sms_scan_preview);
            for (int i = 0; i < AMOUNTS.length; i++) selectRow(i);
            save();
            waitFor(By.textContains(app.getString(R.string.sms_batch_saved, 0)), "Inbox duplicates");
            action(onView(withId(android.R.id.button1)).inRoot(isDialog()), View::performClick);
        } finally {
            var current = sessions.session();
            if (current != null) assertTrue(sessions.clear(current));
            runner.runOnMainSync(() -> app.getSystemService(ClipboardManager.class).clearPrimaryClip());
        }
    }

    private String expected(int index) {
        String fields = app.getString(R.string.sms_extracted_record, AMOUNTS[index],
                index == 2 ? "2026-11-22" : "2026-11-21",
                app.getString(index == 2 ? R.string.sms_direction_credit : R.string.sms_direction_debit));
        return index == 0 ? fields + "\n" + app.getString(R.string.sms_extracted_merchant, "TEST SUPERMARKET") : fields;
    }

    private void paste(String value) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipData.newPlainText("Synthetic fixture", value)));
        click(R.id.sms_paste_action);
    }

    private void openPreview(int id) {
        click(id);
        waitFor(button(R.string.sms_batch_save), "Preview ready");
    }

    private void selectRow(int index) {
        var row = By.text(app.getString(R.string.sms_preview_selected, expected(index)));
        waitFor(row, "Preview row " + index);
        device.findObject(row).click();
        waitFor(button(R.string.sms_preview_apply), "Preview editor");
        action(onView(new TypeSafeMatcher<View>() {
            @Override protected boolean matchesSafely(View view) {
                if (!(view instanceof Spinner) || !(view.getParent() instanceof ViewGroup parent)) return false;
                for (int i = 0; i < parent.getChildCount(); i++) {
                    if (parent.getChildAt(i) instanceof TextView label && label.getLabelFor() == view.getId()
                            && app.getString(R.string.transaction_account).contentEquals(label.getText())) return true;
                }
                return false;
            }
            @Override public void describeTo(Description description) { description.appendText("account selector"); }
        }).inRoot(isDialog()), view -> {
            var spinner = (Spinner) view;
            assertEquals("One synthetic account required", 2, spinner.getCount());
            assertEquals(ACCOUNT, spinner.getItemAtPosition(1));
            spinner.setSelection(1);
        });
        action(onView(withText(R.string.sms_preview_apply)).inRoot(isDialog()), View::performClick);
    }

    private void save() {
        action(onView(withId(android.R.id.button1)).inRoot(isDialog()), View::performClick);
        waitFor(button(R.string.sms_batch_save), "Confirmation open");
        action(onView(withId(android.R.id.button1)).inRoot(isDialog()), View::performClick);
    }

    private BySelector button(int label) {
        return By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(app.getString(label)),
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE));
    }

    private void waitFor(BySelector selector, String name) {
        assertTrue(name, device.wait(Until.hasObject(selector), 20_000));
        android.util.Log.i("FinoraSmsPrivacy", "PASS " + name);
    }

    private static void enter(int id, String value) {
        action(onView(withId(id)), view -> ((EditText) view).setText(value));
    }

    private static void click(int id) {
        action(onView(withId(id)), View::performClick);
    }

    private static void action(ViewInteraction target, Consumer<View> operation) {
        target.perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(View.class); }
            @Override public String getDescription() { return "exercise isolated SMS test control"; }
            @Override public void perform(UiController controller, View view) { operation.accept(view); }
        });
    }
}
