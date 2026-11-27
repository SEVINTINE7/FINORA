package lk.finora.app.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
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

    protected boolean hasScreenInsets() {
        return false;
    }

    public static <D extends Dialog> D showSecure(D dialog) {
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        return dialog;
    }
}
