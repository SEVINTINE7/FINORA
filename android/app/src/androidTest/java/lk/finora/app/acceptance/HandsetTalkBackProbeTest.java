package lk.finora.app.acceptance;

import android.app.UiAutomation;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** Moves accessibility focus while a listener verifies the actual speech. */
@RunWith(AndroidJUnit4.class)
public class HandsetTalkBackProbeTest {
    @Test public void focusVisibleControl() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var arguments = InstrumentationRegistry.getArguments();
        var target = instrumentation.getTargetContext();
        assumeTrue("Explicit listener probe only", arguments.getString("finoraLabelResource") != null);
        assertEquals("true", arguments.getString("finoraIsolatedHandset"));
        assertEquals("lk.finora.app.acceptance", target.getPackageName());
        long serial = target.getSystemService(UserManager.class).getSerialNumberForUser(Process.myUserHandle());
        assertTrue(serial > 0);
        assertEquals(Long.toString(serial), arguments.getString("finoraTestUserSerial"));
        assertTrue(target.getSystemService(AccessibilityManager.class).isTouchExplorationEnabled());

        var automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        var config = new Configuration(target.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(arguments.getString("finoraProbeLanguage", "en")));
        var localized = target.createConfigurationContext(config);
        var root = automation.getRootInActiveWindow();
        if (root == null || !target.getPackageName().contentEquals(root.getPackageName())) {
            var launch = target.getPackageManager().getLaunchIntentForPackage(target.getPackageName());
            assertNotNull(launch);
            target.startActivity(launch);
            long deadline = SystemClock.elapsedRealtime() + 10_000;
            do {
                SystemClock.sleep(100);
                root = automation.getRootInActiveWindow();
            } while ((root == null || !target.getPackageName().contentEquals(root.getPackageName()))
                    && SystemClock.elapsedRealtime() < deadline);
        }
        if ("true".equals(arguments.getString("finoraOpenRecords"))) {
            var records = findControl(automation, localized, "records_tab", 0, true);
            assertTrue("Could not open Records", records.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            // Let TalkBack finish announcing the screen transition before moving focus again.
            SystemClock.sleep(2_000);
        }
        if ("true".equals(arguments.getString("finoraOpenSms"))) {
            var importer = findControl(automation, localized, "records_import", 0, true);
            assertTrue("Could not open import choices", importer.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            SystemClock.sleep(1_000);
            var sms = findControl(automation, localized, "sms_access_title", 0, true);
            sms.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            assertTrue("Could not open SMS access", sms.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            SystemClock.sleep(2_000);
        }
        if ("true".equals(arguments.getString("finoraProbeBack"))) {
            long now = SystemClock.uptimeMillis();
            for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                assertTrue(automation.injectInputEvent(new KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0),
                        true));
            }
            var expected = findControl(automation, localized, arguments.getString("finoraLabelResource"), 0, true);
            long until = SystemClock.elapsedRealtime() + 3_000;
            do {
                var focused = automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                if (focused != null && target.getPackageName().contentEquals(focused.getPackageName())
                        && expected.getText() != null && focused.getText() != null
                        && expected.getText().toString().equalsIgnoreCase(focused.getText().toString())) {
                    SystemClock.sleep(2_000);
                    return;
                }
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < until);
            throw new AssertionError("Back did not restore accessibility focus to the SMS access control");
        }
        int index = Integer.parseInt(arguments.getString("finoraLabelIndex", "0"));
        var node = findControl(automation, localized, arguments.getString("finoraLabelResource"), index, false);
        if (node.isAccessibilityFocused()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        }
        assertTrue("Could not request accessibility focus",
                node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS));
        assertTrue(node.refresh());
        assertTrue("Expected the control to hold accessibility focus", node.isAccessibilityFocused());
        SystemClock.sleep(5_000);
    }

    private static AccessibilityNodeInfo findControl(UiAutomation automation, Context localized,
            String resource, int index, boolean clickableOnly) {
        assertTrue(index >= 0);
        String packageName = localized.getPackageName();
        int id = localized.getResources().getIdentifier(resource, "string", packageName);
        assertTrue("Expected a FINORA string resource", id != 0);
        String label = localized.getString(id);
        List<AccessibilityNodeInfo> matching = new ArrayList<>();
        long deadline = SystemClock.elapsedRealtime() + 12_000;
        do {
            var root = automation.getRootInActiveWindow();
            matching.clear();
            if (root != null && packageName.contentEquals(root.getPackageName())) {
                for (var candidate : root.findAccessibilityNodeInfosByText(label)) {
                    CharSequence spokenLabel = "records_tab".equals(resource) || "records_import".equals(resource)
                            ? candidate.getContentDescription() : candidate.getText();
                    if (candidate.isVisibleToUser() && spokenLabel != null
                            && (!clickableOnly || candidate.isClickable())
                            && label.equalsIgnoreCase(spokenLabel.toString())) {
                        matching.add(candidate);
                    }
                }
            }
            if (index < matching.size()) break;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertTrue("Expected a matching visible FINORA control", index < matching.size());
        return matching.get(index);
    }
}
