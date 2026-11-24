package lk.finora.app.ui.auth;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;

public abstract class ProtectedHomeActivity extends AppCompatActivity {
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
