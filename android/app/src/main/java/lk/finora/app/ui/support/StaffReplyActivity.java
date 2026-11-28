package lk.finora.app.ui.support;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;

import lk.finora.app.R;
import lk.finora.app.data.repo.SupportRepository.Revision;
import lk.finora.app.data.repo.SupportRepository.Topic;
import lk.finora.app.ui.SecureActivity;

/**
 * T70.3/D72 Send a saved reply (STAFF): only ACTIVE (approved) replies are offered, as tiles; the language toggle
 * picks the English or Sinhala wording and the preview shows exactly what the user will read. The screen replaces
 * the old chain of dialogs, so Send sends at once (as New support request does, D72).
 */
public final class StaffReplyActivity extends SecureActivity {
    static final String TICKET = "ticket";
    static final String SUBJECT = "subject";
    private static final String CHOSEN = "chosen";
    private SupportViewModel model;
    private long ticket;
    private long chosen;
    private boolean sending;
    private boolean sinhala;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(SupportViewModel.class);
        ticket = getIntent().getLongExtra(TICKET, 0);
        if (!model.work.isCurrent() || !model.staff || ticket < 1) {
            finish();
            return;
        }
        if (state != null) {
            chosen = state.getLong(CHOSEN);
            sending = state.getBoolean("sending");
        }
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_staff_reply);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.staff_reply_heading), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.staff_reply_choose), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.staff_reply_language_label), true);
        bindTicket();
        MaterialButtonToggleGroup language = findViewById(R.id.staff_reply_language);
        language.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            sinhala = id == R.id.staff_reply_si;
            render();
        });
        RadioGroup choices = findViewById(R.id.staff_reply_choices);
        choices.setOnCheckedChangeListener((group, id) -> {
            View tile = group.findViewById(id);
            if (tile != null) chosen = ((Revision) tile.getTag()).id();
            render();
        });
        findViewById(R.id.staff_reply_send).setOnClickListener(view -> send());
        model.work.changes.observe(this, ignored -> render());
        if (state == null || model.catalog.isEmpty()) model.browseReplies();
    }

    private void bindTicket() {
        View card = findViewById(R.id.staff_reply_ticket);
        card.setClickable(false);
        card.findViewById(R.id.support_ticket_chevron).setVisibility(View.GONE);
        Topic topic = Topic.matching(getIntent().getStringExtra(SUBJECT));
        String name = topic == null ? getString(R.string.support_tickets) : topic.label(this);
        ((ImageView) card.findViewById(R.id.link_icon)).setImageResource(SupportRows.icon(topic));
        ((TextView) card.findViewById(R.id.link_title)).setText(name);
        ((TextView) card.findViewById(R.id.link_text)).setText(getString(R.string.support_request_number, ticket));
        SupportRows.status(card.findViewById(R.id.support_ticket_status), "IN_PROGRESS");
        card.setContentDescription(getString(R.string.support_ticket_summary, ticket, name,
                getString(R.string.support_in_progress)));
    }

    private java.util.List<Revision> approved() {
        return model.catalog.stream().filter(item -> "ACTIVE".equals(item.state()))
                .collect(java.util.stream.Collectors.toList());
    }

    private Revision selected() {
        for (var item : approved()) if (item.id() == chosen) return item;
        return null;
    }

    private void send() {
        Revision revision = selected();
        if (revision == null || model.work.busy() || !model.work.isCurrent()) return;
        sending = true;
        model.reply(ticket, revision.id(), sinhala ? "SI" : "EN");
    }

    private void render() {
        if (!model.work.isCurrent()) {
            finish();
            return;
        }
        boolean busy = model.work.busy();
        if (sending && !busy) {
            sending = false;
            if (model.notice != 0) {
                Toast.makeText(this, R.string.staff_reply_sent, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        RadioGroup choices = findViewById(R.id.staff_reply_choices);
        var options = approved();
        if (choices.getChildCount() != options.size()) buildTiles(choices, options);
        for (int i = 0; i < choices.getChildCount(); i++) choices.getChildAt(i).setEnabled(!busy);
        findViewById(R.id.staff_reply_none).setVisibility(!busy && model.work.error == 0 && options.isEmpty()
                ? View.VISIBLE : View.GONE);
        Revision revision = selected();
        TextView preview = findViewById(R.id.staff_reply_preview);
        preview.setText(revision == null ? getString(R.string.staff_reply_pick)
                : sinhala ? revision.sinhala() : revision.english());
        findViewById(R.id.staff_reply_send).setEnabled(revision != null && !busy);
        findViewById(R.id.staff_reply_en).setEnabled(!busy);
        findViewById(R.id.staff_reply_si).setEnabled(!busy);
        TextView status = findViewById(R.id.records_status);
        int message = busy ? R.string.records_loading : model.work.error;
        status.setText(message == 0 ? "" : getString(message));
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
    }

    private void buildTiles(RadioGroup group, java.util.List<Revision> options) {
        group.setOnCheckedChangeListener(null);
        group.removeAllViews();
        int pad = Math.round(14 * getResources().getDisplayMetrics().density);
        for (Revision revision : options) {
            var tile = new RadioButton(this);
            tile.setId(View.generateViewId());
            tile.setTag(revision);
            tile.setText(revision.topic().label(this));
            tile.setButtonDrawable(null);
            tile.setBackgroundResource(R.drawable.finora_topic_tile);
            tile.setMinHeight(Math.round(60 * getResources().getDisplayMetrics().density));
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(pad, pad, pad, pad);
            tile.setTextAppearance(R.style.FinoraText_RowTitle);
            tile.setCompoundDrawablePadding(pad);
            tile.setCompoundDrawablesRelativeWithIntrinsicBounds(SupportRows.icon(revision.topic()), 0, 0, 0);
            TextViewCompat.setCompoundDrawableTintList(tile, ColorStateList.valueOf(getColor(R.color.finora_green)));
            var params = new RadioGroup.LayoutParams(-1, -2);
            params.bottomMargin = pad * 4 / 7;
            group.addView(tile, params);
            if (revision.id() == chosen) tile.setChecked(true);
        }
        group.setOnCheckedChangeListener((radio, id) -> {
            View tile = radio.findViewById(id);
            if (tile != null) chosen = ((Revision) tile.getTag()).id();
            render();
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putLong(CHOSEN, chosen);
        state.putBoolean("sending", sending);
    }
}
