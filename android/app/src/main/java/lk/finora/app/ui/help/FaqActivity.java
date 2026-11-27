package lk.finora.app.ui.help;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import lk.finora.app.databinding.ActivityFinoraFaqBinding;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/** Localized guidance for the currently available USER features. */
public final class FaqActivity extends SecureActivity {
    private Work session;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new Work(() -> {});
        if (!AccountListActivity.verify(this, session)) return;
        session.changes.observe(this, ignored -> AccountListActivity.verify(this, session));
        var binding = ActivityFinoraFaqBinding.inflate(getLayoutInflater());
        for (int i = 0; i < binding.faqPage.getChildCount(); i++) {
            ViewCompat.setAccessibilityHeading(binding.faqPage.getChildAt(i), i == 0 || i % 2 == 1);
        }
        setContentView(binding.getRoot());
    }

    @Override protected void onResume() {
        super.onResume();
        if (session != null) AccountListActivity.verify(this, session);
    }

    @Override protected void onDestroy() {
        if (session != null) session.close();
        super.onDestroy();
    }

}
