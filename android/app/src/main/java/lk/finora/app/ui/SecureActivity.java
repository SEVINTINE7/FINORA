package lk.finora.app.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import lk.finora.app.R;

/** Protects FINORA screens before their first frame and app-owned dialog windows before display. */
public abstract class SecureActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(getResources().getBoolean(R.bool.finora_light_bars));
        if (!hasScreenInsets()) {
            var content = findViewById(android.R.id.content);
            int types = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime();
            ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
                Insets safe = insets.getInsets(types);
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                // The content container owns these insets, including for legacy fit-system-windows views.
                return new WindowInsetsCompat.Builder(insets).setInsets(types, Insets.NONE).build();
            });
            ViewCompat.requestApplyInsets(content);
        }
    }

    @Override public void onContentChanged() {
        super.onContentChanged();
        if (!showsUp()) return;
        FrameLayout content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() != 1) return;

        // Keep the screen root in place so view IDs, saved state and focus routes remain intact.
        int height = getResources().getDimensionPixelSize(R.dimen.finora_control_height);
        var screen = content.getChildAt(0);
        var params = (FrameLayout.LayoutParams) screen.getLayoutParams();
        params.topMargin += height;
        screen.setLayoutParams(params);

        var toolbar = new MaterialToolbar(this);
        toolbar.setId(R.id.shell_toolbar);
        toolbar.setBackgroundColor(getColor(R.color.finora_surface));
        toolbar.setNavigationIcon(R.drawable.finora_nav_up);
        toolbar.setNavigationIconTint(getColor(R.color.finora_ink));
        toolbar.setNavigationContentDescription(R.string.shell_navigate_up);
        toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        content.addView(toolbar, new FrameLayout.LayoutParams(-1, height, Gravity.TOP));
    }

    protected boolean showsUp() {
        return true;
    }

    protected boolean hasScreenInsets() {
        return false;
    }

    public static <D extends Dialog> D showSecure(D dialog) {
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        return dialog;
    }
}
