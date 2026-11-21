package lk.finora.app.ui.auth;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.local.TokenStore.Session;

public abstract class ProtectedHomeActivity extends ComponentActivity {
    private Session entry;

    protected abstract String requiredRole();
    protected abstract int titleResource();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        entry = currentSession();
        if (entry == null || !requiredRole().equals(entry.role)) {
            SessionNavigation.openLogin(this, false);
            return;
        }

        HomeViewModel model = new ViewModelProvider(this).get(HomeViewModel.class);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding * 2, padding, padding);
        layout.setBackgroundColor(getColor(R.color.finora_ink));
        TextView title = new TextView(this);
        title.setText(titleResource());
        title.setTextColor(0xffffffff);
        title.setTextSize(28);
        layout.addView(title);
        TextView message = new TextView(this);
        message.setText(R.string.home_placeholder);
        message.setTextColor(0xffffffff);
        message.setGravity(Gravity.CENTER);
        layout.addView(message, new LinearLayout.LayoutParams(-1, 0, 1));
        if ("USER".equals(requiredRole())) {
            TextView home = new TextView(this);
            home.setText(R.string.home_tab);
            home.setGravity(Gravity.CENTER);
            home.setTextColor(0xffffffff);
            layout.addView(home);
        }
        Button logout = new Button(this);
        logout.setText(R.string.sign_out);
        logout.setBackgroundTintList(ColorStateList.valueOf(0xffdff2e4));
        layout.addView(logout);
        logout.setOnClickListener(view -> {
            layout.setVisibility(View.INVISIBLE);
            model.logout(entry);
        });
        setContentView(layout);
        model.signedOut().observe(this, failed -> {
            if (failed != null) {
                SessionNavigation.openLogin(this, failed);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing() && (entry == null || currentSession() != entry)) {
            SessionNavigation.openLogin(this, false);
        }
    }

    private static Session currentSession() {
        TokenStore store = TokenStore.current();
        return store == null ? null : store.verifiedSession();
    }
}
