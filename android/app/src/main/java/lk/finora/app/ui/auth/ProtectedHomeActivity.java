package lk.finora.app.ui.auth;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import java.util.HashMap;
import java.util.Map;

import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;

public abstract class ProtectedHomeActivity extends SecureActivity {
    private static final String RETURN_FOCUS = "returnFocus";
    private static final String ROUTE_RETURNED = "routeReturned";
    private static final long TALKBACK_WINDOW_SETTLE_MS = 500;
    private final Map<Integer, Button> focusRoutes = new HashMap<>();
    private int returnFocus;
    private boolean routeReturned;
    private Runnable pendingFocusRestore;
    private final ActivityResultLauncher<Intent> focusRoute =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                routeReturned = true;
                restoreRouteFocus();
            });
    private Session entry;
    private HomeViewModel model;
    private TokenStore observedStore;
    private final Runnable sessionChanged = () -> runOnUiThread(this::verifyEntry);

    protected abstract String requiredRole();
    protected abstract int titleResource();

    protected boolean showPlaceholder() { return true; }

    protected void addNavigation(LinearLayout layout, TextView title, Bundle state) {}

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        entry = currentSession();
        if (!RoleGuard.permits(requiredRole(), entry == null ? null : entry.role)) {
            SessionNavigation.openLogin(this, false);
            return;
        }

        if (state != null) {
            returnFocus = state.getInt(RETURN_FOCUS);
            routeReturned = state.getBoolean(ROUTE_RETURNED);
        }
        model = new ViewModelProvider(this).get(HomeViewModel.class);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding * 2, padding, padding);
        layout.setBackgroundColor(getColor(R.color.finora_ink));
        TextView title = new TextView(this);
        title.setText(titleResource());
        title.setTextColor(getColor(R.color.finora_on_navy));
        title.setTextSize(28);
        layout.addView(title);
        TextView message = new TextView(this);
        message.setText(R.string.home_placeholder);
        message.setTextColor(getColor(R.color.finora_on_navy));
        message.setGravity(Gravity.CENTER);
        if (showPlaceholder()) {
            layout.addView(message, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        addLanguageSelector(layout);
        Button logout = new Button(this);
        logout.setText(R.string.sign_out);
        logout.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.finora_mint)));
        layout.addView(logout);
        addNavigation(layout, title, state);
        logout.setOnClickListener(view -> {
            layout.setVisibility(View.INVISIBLE);
            model.logout(entry);
        });
        if (model.signingOut()) {
            layout.setVisibility(View.INVISIBLE);
        }
        setContentView(layout);
        model.signedOut().observe(this, failed -> {
            if (failed != null) {
                SessionNavigation.openLogin(this, failed);
            }
        });
    }

    protected final void bindFocusReturningRoute(Button button, int routeId, Class<?> screen) {
        focusRoutes.put(routeId, button);
        button.setOnClickListener(view -> {
            returnFocus = routeId;
            focusRoute.launch(new Intent(this, screen));
        });
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) restoreRouteFocus();
        else cancelRouteFocus();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) cancelRouteFocus();
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) cancelRouteFocus();
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt(RETURN_FOCUS, returnFocus);
        state.putBoolean(ROUTE_RETURNED, routeReturned);
        super.onSaveInstanceState(state);
    }

    private void cancelRouteFocus() {
        if (pendingFocusRestore != null) {
            getWindow().getDecorView().removeCallbacks(pendingFocusRestore);
            pendingFocusRestore = null;
        }
    }

    // Let TalkBack process the returned window before restoring its originating control.
    @android.annotation.SuppressLint("AccessibilityFocus")
    private void restoreRouteFocus() {
        if (!routeReturned || !hasWindowFocus() || isFinishing()) return;
        Button route = focusRoutes.get(returnFocus);
        routeReturned = false;
        returnFocus = 0;
        AccessibilityManager accessibility = getSystemService(AccessibilityManager.class);
        if (route == null || !route.isShown() || accessibility == null || !accessibility.isTouchExplorationEnabled()) {
            return;
        }
        pendingFocusRestore = () -> {
            pendingFocusRestore = null;
            if (!hasWindowFocus() || !route.isShown() || !accessibility.isTouchExplorationEnabled()) return;
            route.requestRectangleOnScreen(new Rect(0, 0, route.getWidth(), route.getHeight()), true);
            route.setFocusableInTouchMode(true);
            route.requestFocus();
            // TalkBack can retain the previous button's focus even after input focus moves.
            route.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        };
        getWindow().getDecorView().postDelayed(pendingFocusRestore, TALKBACK_WINDOW_SETTLE_MS);
    }

    private void addLanguageSelector(LinearLayout layout) {
        layout.addView(ProfileLanguageButton.create(this, entry, model::signingOut));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isFinishing()) {
            observedStore = TokenStore.current();
            if (observedStore != null) {
                observedStore.addSessionListener(sessionChanged);
            }
        }
    }

    @Override
    protected void onStop() {
        if (observedStore != null) {
            observedStore.removeSessionListener(sessionChanged);
            observedStore = null;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verifyEntry();
    }

    private void verifyEntry() {
        if (observedStore == null || isFinishing() || model.signingOut()) {
            return;
        }
        Session current = currentSession();
        if (entry == null || !entry.sameAccount(current)
                || !RoleGuard.permits(requiredRole(), current.role)) {
            SessionNavigation.openLogin(this, false);
        }
    }

    private static Session currentSession() {
        TokenStore store = TokenStore.current();
        return store == null ? null : store.verifiedSession();
    }
}
