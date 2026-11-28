package lk.finora.app.ui.support;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import lk.finora.app.R;
import lk.finora.app.data.repo.SupportRepository.Topic;
import lk.finora.app.ui.SecureActivity;

/**
 * T68.3/D72 New support request: pick one of four topics, read the exact standard sentence that will be sent,
 * then Send. No confirm dialog (Sevindu, D72): the screen already shows what is sent and nothing else is.
 */
public final class SupportNewActivity extends SecureActivity {
    private static final String CHOSEN = "support_topic";
    private SupportViewModel model;
    private Topic chosen;
    private boolean sending;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(SupportViewModel.class);
        if (!model.work.isCurrent() || model.staff) {
            finish();
            return;
        }
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_support_new);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.support_new_heading), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.support_new_question), true);
        if (state != null && state.getString(CHOSEN) != null) chosen = Topic.valueOf(state.getString(CHOSEN));
        RadioGroup group = findViewById(R.id.support_topics);
        int pad = Math.round(14 * getResources().getDisplayMetrics().density);
        for (Topic topic : Topic.values()) {
            var tile = new RadioButton(this);
            tile.setId(View.generateViewId());
            tile.setTag(topic);
            tile.setText(topic.label(this));
            tile.setButtonDrawable(null);
            tile.setBackgroundResource(R.drawable.finora_topic_tile);
            tile.setMinHeight(Math.round(60 * getResources().getDisplayMetrics().density));
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(pad, pad, pad, pad);
            tile.setTextAppearance(R.style.FinoraText_RowTitle);
            tile.setCompoundDrawablePadding(pad);
            tile.setCompoundDrawablesRelativeWithIntrinsicBounds(SupportRows.icon(topic), 0, 0, 0);
            TextViewCompat.setCompoundDrawableTintList(tile, ColorStateList.valueOf(getColor(R.color.finora_green)));
            var params = new RadioGroup.LayoutParams(-1, -2);
            params.bottomMargin = pad * 4 / 7;
            group.addView(tile, params);
            if (topic == chosen) tile.setChecked(true);
        }
        group.setOnCheckedChangeListener((radio, id) -> {
            View tile = radio.findViewById(id);
            chosen = tile == null ? null : (Topic) tile.getTag();
            render();
        });
        findViewById(R.id.support_send).setOnClickListener(view -> {
            if (chosen == null || model.work.busy() || !model.work.isCurrent()) return;
            sending = true;
            model.create(chosen);
        });
        model.work.changes.observe(this, ignored -> render());
        render();
    }

    private void render() {
        if (!model.work.isCurrent()) {
            finish();
            return;
        }
        boolean busy = model.work.busy();
        if (sending && !busy) {
            sending = false;
            if (model.work.error == 0) {
                Toast.makeText(this, R.string.support_sent_toast, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        TextView preview = findViewById(R.id.support_preview);
        preview.setText(chosen == null ? getString(R.string.support_pick_topic) : chosen.body());
        RadioGroup group = findViewById(R.id.support_topics);
        for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(!busy);
        findViewById(R.id.support_send).setEnabled(chosen != null && !busy);
        TextView status = findViewById(R.id.records_status);
        int message = busy ? R.string.records_loading : model.work.error;
        status.setText(message == 0 ? "" : getString(message));
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (chosen != null) state.putString(CHOSEN, chosen.name());
    }
}
