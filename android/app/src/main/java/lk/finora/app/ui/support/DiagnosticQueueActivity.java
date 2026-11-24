package lk.finora.app.ui.support;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;

public final class DiagnosticQueueActivity extends SecureActivity {
    private DiagnosticQueueViewModel model;
    private LinearLayout rows;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(DiagnosticQueueViewModel.class);
        if (!model.work.isCurrent()) {
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        rows.setPadding(padding, padding, padding, padding);
        scroll.addView(rows);
        setContentView(scroll);
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onStart() {
        super.onStart();
        if (!isFinishing()) {
            render();
            model.load();
        }
    }

    private void render() {
        if (!model.work.isCurrent()) {
            if (rows != null) rows.removeAllViews();
            finish();
            return;
        }
        rows.removeAllViews();
        var heading = label(getString(R.string.sms_diagnostic_queue));
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        if (model.work.busy()) {
            label(getString(R.string.records_loading));
            return;
        }
        if (model.work.error != 0) label(getString(model.work.error));
        if (model.notice != 0) label(getString(model.notice));
        addButton(R.string.records_retry, model::load);
        if (model.admin) {
            addButton(R.string.sms_format_activate, () -> confirm(R.string.sms_format_activate,
                    () -> model.setFormatActive(true)));
            addButton(R.string.sms_format_disable, () -> confirm(R.string.sms_format_disable,
                    () -> model.setFormatActive(false)));
        }
        if (model.groups.isEmpty() && model.escalations.isEmpty()) label(getString(R.string.records_empty));
        for (var group : model.groups) {
            String summary = getString(R.string.sms_diagnostic_count, bank(group.bankCode()),
                    group.errorCode().name(), group.formatVersion() == null ? getString(R.string.sms_diagnostic_unknown)
                            : group.formatVersion().toString(), group.reports());
            label(summary);
            confirmRow(R.string.sms_diagnostic_flag, summary, () -> model.flag(group));
        }
        for (var escalation : model.escalations) {
            boolean open = "OPEN".equals(escalation.status());
            String summary = getString(R.string.sms_diagnostic_escalation, bank(escalation.bankCode()),
                    escalation.errorCode().name(), getString(open ? R.string.sms_diagnostic_open
                            : R.string.sms_diagnostic_acknowledged));
            label(summary);
            confirmRow(open ? R.string.sms_diagnostic_ack : R.string.sms_diagnostic_close,
                    summary, () -> model.advance(escalation));
        }
    }

    private String bank(String value) {
        return value == null ? getString(R.string.sms_diagnostic_unknown) : value;
    }

    private TextView label(String text) {
        var view = new TextView(this);
        view.setText(text);
        rows.addView(view);
        return view;
    }

    private void addButton(int text, Runnable action) {
        var button = new Button(this);
        button.setText(text);
        button.setOnClickListener(view -> action.run());
        rows.addView(button);
    }

    private void confirmRow(int text, String summary, Runnable action) {
        addButton(text, () -> confirm(text, summary, action));
        View child = rows.getChildAt(rows.getChildCount() - 1);
        child.setContentDescription(getString(text) + ". " + summary);
    }

    private void confirm(int text, Runnable action) {
        confirm(text, getString(R.string.sms_format_nsb), action);
    }

    private void confirm(int text, String summary, Runnable action) {
        if (!model.work.isCurrent()) return;
        showSecure(new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text).setMessage(summary)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (model.work.isCurrent()) action.run();
                }).create());
    }
}
