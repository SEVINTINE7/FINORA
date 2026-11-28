package lk.finora.app.ui.help;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import lk.finora.app.R;
import lk.finora.app.databinding.ActivityFinoraFaqBinding;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.ui.support.SupportActivity;

/** T68.3/D72 Help: localized FAQ for the available USER features as expandable entries, then a Support link. */
public final class FaqActivity extends SecureActivity {
    private static final String OPEN = "faq_open";
    /** Entries in screen order: include id, icon, question, answer. */
    static final int[][] ENTRIES = {
            {R.id.faq_login, R.drawable.finora_ic_lock, R.string.faq_login, R.string.faq_login_body},
            {R.id.faq_navigation, R.drawable.finora_nav_records, R.string.faq_navigation, R.string.finance_help_text},
            {R.id.faq_language, R.drawable.finora_ic_language, R.string.faq_language, R.string.faq_language_body},
            {R.id.faq_privacy, R.drawable.finora_ic_shield, R.string.faq_privacy, R.string.faq_privacy_body},
    };
    private Work session;
    private boolean[] open = new boolean[ENTRIES.length];

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new Work(() -> {});
        if (!AccountListActivity.verify(this, session)) return;
        session.changes.observe(this, ignored -> AccountListActivity.verify(this, session));
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        var binding = ActivityFinoraFaqBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setAccessibilityHeading(binding.faqHeading, true);
        ViewCompat.setAccessibilityHeading(binding.faqTitle, true);
        ViewCompat.setAccessibilityHeading(binding.faqMoreTitle, true);
        if (state != null && state.getBooleanArray(OPEN) != null) open = state.getBooleanArray(OPEN);
        else open[0] = true;  // the first answer is open, as in the approved mockup
        for (int i = 0; i < ENTRIES.length; i++) bind(i);
        binding.faqOpenSupport.setOnClickListener(view -> startActivity(new Intent(this, SupportActivity.class)));
    }

    private void bind(int index) {
        int[] entry = ENTRIES[index];
        View item = findViewById(entry[0]);
        ((ImageView) item.findViewById(R.id.faq_icon)).setImageResource(entry[1]);
        TextView question = item.findViewById(R.id.faq_question);
        question.setText(entry[2]);
        ((TextView) item.findViewById(R.id.faq_answer)).setText(entry[3]);
        View header = item.findViewById(R.id.faq_header);
        ViewCompat.setAccessibilityHeading(header, true);
        header.setOnClickListener(view -> {
            open[index] = !open[index];
            render(index);
        });
        render(index);
    }

    private void render(int index) {
        View item = findViewById(ENTRIES[index][0]);
        boolean expanded = open[index];
        item.findViewById(R.id.faq_answer).setVisibility(expanded ? View.VISIBLE : View.GONE);
        item.findViewById(R.id.faq_chevron).setRotation(expanded ? 180f : 0f);
        ViewCompat.setStateDescription(item.findViewById(R.id.faq_header), getString(expanded
                ? R.string.records_actions_expanded : R.string.records_actions_collapsed));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBooleanArray(OPEN, open);
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
