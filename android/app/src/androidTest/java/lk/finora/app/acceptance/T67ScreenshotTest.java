package lk.finora.app.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.ui.importer.CsvImportActivity;
import lk.finora.app.ui.sms.PasteSmsActivity;
import lk.finora.app.ui.sms.SmsAccessActivity;

/**
 * T67 review run against a local dev API and the synthetic demo account (finoraShotEmail / finoraShotPassword).
 * Uses only synthetic NSB messages and a synthetic CSV file in Downloads; never saves a transaction (every review is
 * cancelled). Checks the privacy panel, parsed fields, the review sheet's missing-account note and Save count, and
 * the CSV summary. With finoraT67Scan=true it grants READ_SMS last (the script revokes it) and captures the inbox
 * scan of the injected messages. Never runs by default.
 */
@RunWith(AndroidJUnit4.class)
public final class T67ScreenshotTest {
    private static final String SMS = "Dear MR TEST USER,LKR 1,250.75 Debited from your A/c XXXXXXXX0000 on 21/11/2026 "
            + "at 14:27.AvlBal LKR 25,000.00. @ TEST SUPERMARKET. ATM POS Transaction.Thank you for banking with us."
            + "Call Centre 1972.";
    private static final String CSV_NAME = "finora-november.csv";
    private static final String CSV = "date,type,amount,category,account,merchant,note\n"
            + "2026-11-01,INCOME,85000.00,Salary,Commercial Bank,,November salary\n"
            + "2026-11-02,EXPENSE,3120.00,Food,Cash wallet,Keells Super,\n"
            + "2026-11-03,EXPENSE,1999.00,Mobile/Internet,Commercial Bank,Dialog,\n"
            + "2026-11-05,EXPENSE,25000.00,Rent,Commercial Bank,Boarding rent,\n"
            + "2026-11-06,EXPENSE,450.00,Transport,Wallet 2,PickMe,\n"
            + "2026-11-07,EXPENSE,0.00,Entertainment,Cash wallet,Cinema,\n"
            + "2026-11-08,EXPENSE,1850.00,Food,Debit card,Keells Super,\n"
            + "2026-11-10,EXPENSE,2400.00,Food delivery,Debit card,Uber Eats,\n"
            + "2026-11-12,EXPENSE,6500.00,Shopping,Debit card,Odel,\n"
            + "2026-11-15,INCOME,12000.00,Part-time,Commercial Bank,,Tutoring\n"
            + "2026-11-18,EXPENSE,3000.00,Gym,Cash wallet,Power World,\n"
            + "2026-11-20,EXPENSE,700.00,Transport,Cash wallet,Uber,\n";

    @Test public void smsAndCsvImportScreens() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit T67 run only", "true".equals(args.getString("finoraT67Shots")));
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        String language = args.getString("finoraShotLanguage", "en");
        boolean dark = "dark".equals(args.getString("finoraShotTheme"));
        String tag = args.getString("finoraShotTag", language + (dark ? "-dark" : "-light"));
        var tokens = TokenStore.open(app);
        assertNull("Preserve the existing session", tokens.session());
        assertEquals("Start without SMS access", PackageManager.PERMISSION_DENIED,
                app.checkSelfPermission(Manifest.permission.READ_SMS));
        var login = T62ScreenshotTest.login(args.getString("finoraShotEmail"), args.getString("finoraShotPassword"));
        instrumentation.runOnMainSync(() -> {
            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            T62ScreenshotTest.setLocales(app, language);
        });
        tokens.save(login.getString("accessToken"), login.getString("refreshToken"), "USER",
                language.toUpperCase(Locale.ROOT));
        File out = app.getExternalFilesDir("t67");
        assertNotNull(out);
        try {
            access(out, tag);
            paste(out, tag);
            csv(out, tag);
            if ("true".equals(args.getString("finoraT67Scan"))) scan(out, tag);
        } finally {
            instrumentation.runOnMainSync(() -> app.getSystemService(ClipboardManager.class).clearPrimaryClip());
            var session = tokens.session();
            if (session != null) tokens.clear(session);
            instrumentation.runOnMainSync(() -> {
                T62ScreenshotTest.setLocales(app, "");
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }

    private static <A extends Activity> void await(ActivityScenario<A> scenario, Predicate<A> ready, String name) {
        long end = SystemClock.uptimeMillis() + 60_000;
        var done = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < end) {
            scenario.onActivity(activity -> done.set(ready.test(activity)));
            if (done.get()) break;
            SystemClock.sleep(500);
        }
        assertTrue(name, done.get());
        T62ScreenshotTest.settle();
    }

    /** The review sheet is a secure dialog (system screenshots are blank), so draw its own window. */
    private static void captureTop(File file) {
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.review_scroll))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(new androidx.test.espresso.ViewAction() {
                    @Override public org.hamcrest.Matcher<View> getConstraints() {
                        return androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(View.class);
                    }
                    @Override public String getDescription() { return "draw the review sheet"; }
                    @Override public void perform(androidx.test.espresso.UiController controller, View view) {
                        View root = view.getRootView();
                        var bitmap = android.graphics.Bitmap.createBitmap(root.getWidth(), root.getHeight(),
                                android.graphics.Bitmap.Config.ARGB_8888);
                        root.draw(new android.graphics.Canvas(bitmap));
                        try (var stream = new java.io.FileOutputStream(file)) {
                            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream));
                        } catch (java.io.IOException failure) {
                            throw new AssertionError(failure);
                        }
                    }
                });
    }

    private void access(File out, String tag) {
        try (var screen = ActivityScenario.launch(SmsAccessActivity.class)) {
            await(screen, a -> a.findViewById(R.id.sms_allow).getVisibility() == View.VISIBLE, "access shown");
            screen.onActivity(a -> {
                assertEquals(3, ((ViewGroup) a.findViewById(R.id.sms_points)).getChildCount());
                assertEquals(View.GONE, a.findViewById(R.id.sms_scan_card).getVisibility());
                T62ScreenshotTest.capture(a, new File(out, tag + "-sms-access.png"));
                ((ScrollView) a.findViewById(R.id.sms_scroll)).fullScroll(View.FOCUS_DOWN);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, tag + "-sms-access-end.png")));
        }
    }

    private void paste(File out, String tag) {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        instrumentation.runOnMainSync(() -> app.getSystemService(ClipboardManager.class)
                .setPrimaryClip(ClipData.newPlainText("Synthetic NSB fixture", SMS)));
        try (var screen = ActivityScenario.launch(PasteSmsActivity.class)) {
            T62ScreenshotTest.settle();
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, tag + "-paste-empty.png")));
            screen.onActivity(a -> a.findViewById(R.id.sms_paste_action).performClick());
            await(screen, a -> a.findViewById(R.id.sms_paste_preview).getVisibility() == View.VISIBLE, "parsed");
            screen.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.sms_paste_found).getVisibility());
                assertTrue(String.valueOf(a.findViewById(R.id.sms_paste_fields_card).getContentDescription())
                        .contains("TEST SUPERMARKET"));
                T62ScreenshotTest.capture(a, new File(out, tag + "-paste.png"));
                a.findViewById(R.id.sms_paste_preview).performClick();
            });
            review(out, tag + "-paste-review");
        }
    }

    /** Captures the review sheet before and after choosing an account, then cancels. */
    private void review(File out, String name) {
        var device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        var app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Review sheet", device.wait(Until.hasObject(By.text(app.getString(R.string.sms_review_intro))), 30_000));
        SystemClock.sleep(1_500);
        captureTop(new File(out, name + ".png"));
        var picker = device.findObject(By.res(app.getPackageName(), "review_account"));
        assertNotNull(picker);
        picker.click();
        var choice = device.wait(Until.findObject(By.text("Cash wallet")), 10_000);
        assertNotNull(choice);
        choice.click();
        SystemClock.sleep(1_500);
        captureTop(new File(out, name + "-ready.png"));
        device.findObject(By.res("android", "button2")).click();
        SystemClock.sleep(1_000);
    }

    private android.net.Uri writeCsv() throws Exception {
        var resolver = InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver();
        var downloads = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        resolver.delete(downloads, MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{CSV_NAME});
        var values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, CSV_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
        var uri = resolver.insert(downloads, values);
        assertNotNull(uri);
        try (var stream = resolver.openOutputStream(uri)) {
            assertNotNull(stream);
            stream.write(CSV.getBytes(StandardCharsets.UTF_8));
        }
        return uri;
    }

    private void csv(File out, String tag) throws Exception {
        writeCsv();
        var device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try (var screen = ActivityScenario.launch(CsvImportActivity.class)) {
            T62ScreenshotTest.settle();
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, tag + "-csv-empty.png")));
            screen.onActivity(a -> a.findViewById(R.id.csv_choose).performClick());
            var file = device.wait(Until.findObject(By.text(CSV_NAME)), 15_000);
            if (file == null) {
                // DocumentsUI may open elsewhere; go to Downloads from its roots list.
                var roots = device.wait(Until.findObject(By.desc(java.util.regex.Pattern.compile("Show roots|Open navigation drawer|.*roots.*"))), 5_000);
                if (roots != null) roots.click();
                var downloads = device.wait(Until.findObject(By.text("Downloads")), 5_000);
                assertNotNull("Downloads root", downloads);
                downloads.click();
                file = device.wait(Until.findObject(By.text(CSV_NAME)), 10_000);
            }
            assertNotNull("CSV in picker", file);
            file.click();
            await(screen, a -> a.findViewById(R.id.csv_bar).getVisibility() == View.VISIBLE, "csv preview");
            screen.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.csv_list);
                assertEquals(13, list.getAdapter().getItemCount());
                assertEquals(CSV_NAME, ((TextView) a.findViewById(R.id.csv_file_name)).getText().toString());
                assertEquals("10", ((TextView) a.findViewById(R.id.csv_ready).findViewById(R.id.tile_value)).getText().toString());
                assertEquals("2", ((TextView) a.findViewById(R.id.csv_fix).findViewById(R.id.tile_value)).getText().toString());
                assertTrue(!a.findViewById(R.id.csv_import).isEnabled());
                T62ScreenshotTest.capture(a, new File(out, tag + "-csv.png"));
            });
            // Tick the visible ready rows except the rent payment; the test never imports.
            screen.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.csv_list);
                for (int i = 0; i < list.getChildCount(); i++) {
                    CheckBox box = list.getChildAt(i).findViewById(R.id.import_check);
                    TextView title = list.getChildAt(i).findViewById(R.id.import_title);
                    if (box != null && box.getVisibility() == View.VISIBLE && box.isEnabled()
                            && !"Boarding rent".contentEquals(title.getText())) box.setChecked(true);
                }
                list.scrollToPosition(6);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                RecyclerView list = a.findViewById(R.id.csv_list);
                for (int i = 0; i < list.getChildCount(); i++) {
                    CheckBox box = list.getChildAt(i).findViewById(R.id.import_check);
                    TextView title = list.getChildAt(i).findViewById(R.id.import_title);
                    if (box != null && box.getVisibility() == View.VISIBLE && box.isEnabled()
                            && !"Boarding rent".contentEquals(title.getText())) box.setChecked(true);
                }
                assertTrue(a.findViewById(R.id.csv_import).isEnabled());
                T62ScreenshotTest.capture(a, new File(out, tag + "-csv-rows.png"));
                ((RecyclerView) a.findViewById(R.id.csv_list)).scrollToPosition(12);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> T62ScreenshotTest.capture(a, new File(out, tag + "-csv-end.png")));
        }
    }

    private void scan(File out, String tag) {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var app = instrumentation.getTargetContext();
        instrumentation.getUiAutomation().grantRuntimePermission(app.getPackageName(), Manifest.permission.READ_SMS);
        try (var screen = ActivityScenario.launch(SmsAccessActivity.class)) {
            await(screen, a -> a.findViewById(R.id.sms_scan).getVisibility() == View.VISIBLE, "scan shown");
            screen.onActivity(a -> a.findViewById(R.id.sms_scan).performClick());
            await(screen, a -> a.findViewById(R.id.sms_scan_preview).getVisibility() == View.VISIBLE, "scan done");
            screen.onActivity(a -> {
                assertEquals(View.GONE, a.findViewById(R.id.sms_allow).getVisibility());
                T62ScreenshotTest.capture(a, new File(out, tag + "-sms-scan.png"));
                ScrollView scroll = a.findViewById(R.id.sms_scroll);
                scroll.scrollTo(0, a.findViewById(R.id.sms_found).getTop() - 24);
            });
            T62ScreenshotTest.settle();
            screen.onActivity(a -> {
                T62ScreenshotTest.capture(a, new File(out, tag + "-sms-scan-found.png"));
                a.findViewById(R.id.sms_scan_preview).performClick();
            });
            review(out, tag + "-scan-review");
        }
    }

}
