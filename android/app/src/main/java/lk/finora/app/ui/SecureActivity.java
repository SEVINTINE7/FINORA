package lk.finora.app.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;

/** Protects FINORA screens before their first frame and app-owned dialog windows before display. */
public abstract class SecureActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(state);
    }

    public static <D extends Dialog> D showSecure(D dialog) {
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        return dialog;
    }
}
