package lk.finora.app.ui.help;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import lk.finora.app.R;
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
        var scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.finora_surface));
        var page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int spacing = Math.round(16 * getResources().getDisplayMetrics().density);
        page.setPadding(spacing, spacing, spacing, spacing);
        addText(page, R.string.faq_title, true);
        addSection(page, R.string.faq_login, R.string.faq_login_body);
        addSection(page, R.string.faq_navigation, R.string.finance_help_text);
        addSection(page, R.string.faq_language, R.string.faq_language_body);
        addSection(page, R.string.faq_privacy, R.string.faq_privacy_body);
        scroll.addView(page);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        if (session != null) AccountListActivity.verify(this, session);
    }

    @Override protected void onDestroy() {
        if (session != null) session.close();
        super.onDestroy();
    }

    private void addSection(LinearLayout page, @StringRes int heading, @StringRes int body) {
        addText(page, heading, true);
        addText(page, body, false);
    }

    private void addText(LinearLayout page, @StringRes int value, boolean heading) {
        var label = new TextView(this);
        label.setText(value);
        label.setTextSize(heading ? 20 : 16);
        label.setTextColor(getColor(R.color.finora_ink));
        ViewCompat.setAccessibilityHeading(label, heading);
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        page.addView(label, params);
    }
}
