package lk.finora.app.t55;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Trace;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.math.BigDecimal;
import lk.finora.app.R;
import lk.finora.app.ui.Money;
import lk.finora.app.ui.main.MainActivity;

/** Opt-in debug-only startup marker for the canonical November synthetic worker. */
public final class ColdHomeApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        if (!"lk.finora.app.t57handset".equals(getPackageName()) || !Trace.isEnabled()) return;
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof MainActivity) watchHome(activity);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private static void watchHome(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        String income = Money.rupees(activity, new BigDecimal("60000.00"));
        String expenses = Money.rupees(activity, new BigDecimal("40000.00"));
        decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (visibleAmount(decor, R.id.dashboard_income_amount, income)
                        && visibleAmount(decor, R.id.dashboard_expense_amount, expenses)) {
                    decor.getViewTreeObserver().removeOnPreDrawListener(this);
                    Trace.beginSection("T55-external-home-usable");
                    Trace.endSection();
                }
                return true;
            }
        });
    }

    private static boolean visibleAmount(View root, int id, String expected) {
        return root.findViewById(id) instanceof TextView amount && amount.isShown()
                && amount.getGlobalVisibleRect(new android.graphics.Rect())
                && expected.contentEquals(amount.getText());
    }
}
