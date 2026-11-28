package lk.finora.app.ui.support;

import android.os.Bundle;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import lk.finora.app.R;
import lk.finora.app.data.repo.SmsDiagnosticRepository;
import lk.finora.app.ui.SecureActivity;

public final class DiagnosticQueueActivity extends SecureActivity {
    private DiagnosticQueueViewModel model;
    private StaffCards cards;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(DiagnosticQueueViewModel.class);
        if (!model.work.isCurrent()) {
            finish();
            return;
        }
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_staff_list);
        cards = new StaffCards(this);
        StaffCards.frame(this, model.admin ? R.string.sms_diagnostic_admin : R.string.sms_diagnostic_queue,
                getString(R.string.staff_diag_note), R.drawable.finora_ic_shield);
        findViewById(R.id.staff_list_reload).setOnClickListener(view -> model.load());
        findViewById(R.id.staff_list_retry).setOnClickListener(view -> model.load());
        model.work.changes.observe(this, ignored -> render());
    }

    @Override protected void onStart() {
        super.onStart();
        if (!isFinishing()) {
            render();
            model.load();
        }
    }

    /** T70.3/D72: grouped parser failures as cards (counts only, rule 4); ADMIN also sees flags and NSB revisions. */
    private void render() {
        if (!model.work.isCurrent()) {
            if (cards != null) cards.clear();
            finish();
            return;
        }
        boolean busy = model.work.busy();
        StaffCards.status(this, busy, model.work.error, model.notice);
        cards.clear();
        if (busy) return;
        for (int index = 0; index < model.sampleResults.size(); index++) {
            cards.line(getString(R.string.sms_format_fixture_result, index + 1, getString(
                    model.sampleResults.get(index) ? R.string.sms_format_sample_matched
                            : R.string.sms_format_sample_unmatched)));
        }
        if (model.admin) renderFormats();
        if (model.admin) {
            if (!model.escalations.isEmpty()) cards.section(getString(R.string.staff_diag_flags));
        } else if (model.work.error == 0) {
            cards.section(getString(R.string.staff_diag_groups));
        }
        if (model.groups.isEmpty() && model.escalations.isEmpty() && model.work.error == 0) {
            cards.line(getString(model.admin ? R.string.staff_diag_no_flags : R.string.staff_diag_none));
        }
        for (var group : model.groups) {
            String bank = bank(group.bankCode());
            String version = group.formatVersion() == null ? getString(R.string.sms_diagnostic_unknown)
                    : group.formatVersion().toString();
            String summary = getString(R.string.sms_diagnostic_count, bank, group.errorCode().name(), version,
                    group.reports());
            View card = cards.card(R.drawable.finora_ic_alert, StaffCards.Tone.WARN, problem(group.errorCode()),
                    group.bankCode() == null ? getString(R.string.staff_diag_bank_unknown)
                            : getString(R.string.staff_diag_meta, bank, version),
                    getString(R.string.staff_diag_description, problem(group.errorCode()), bank, version,
                            group.reports()));
            StaffCards.count(card, group.reports());
            cards.action(card, R.string.sms_diagnostic_flag, R.drawable.finora_ic_flag, false, summary,
                    () -> confirm(R.string.sms_diagnostic_flag, summary, () -> model.flag(group)));
        }
        if (!model.admin && !model.groups.isEmpty()) cards.line(getString(R.string.staff_diag_hint));
        for (var escalation : model.escalations) {
            boolean open = "OPEN".equals(escalation.status());
            String bank = bank(escalation.bankCode());
            String summary = getString(R.string.sms_diagnostic_escalation, bank, escalation.errorCode().name(),
                    getString(open ? R.string.sms_diagnostic_open : R.string.sms_diagnostic_acknowledged));
            View card = cards.card(R.drawable.finora_ic_flag, StaffCards.Tone.WARN, problem(escalation.errorCode()),
                    bank, summary);
            StaffCards.chip(card, escalation.status());
            int action = open ? R.string.sms_diagnostic_ack : R.string.sms_diagnostic_close;
            cards.action(card, action, open ? R.drawable.finora_ic_tick : R.drawable.finora_ic_check, true, summary,
                    () -> confirm(action, summary, () -> model.advance(escalation)));
        }
    }

    /** ADMIN tools: draft a revision, test a format locally, and one card per NSB deployment revision. */
    private void renderFormats() {
        cards.section(getString(R.string.staff_diag_formats));
        View tools = cards.card(R.drawable.finora_ic_file, StaffCards.Tone.ACCENT, getString(R.string.sms_format_nsb),
                null, getString(R.string.sms_format_nsb));
        StaffCards.stack(tools);
        String nsb = getString(R.string.sms_format_nsb);
        cards.action(tools, R.string.sms_format_sample_check, R.drawable.finora_ic_scan, true, nsb, () -> startActivity(
                new android.content.Intent(this, FormatSampleActivity.class)));
        cards.action(tools, R.string.sms_format_draft, R.drawable.finora_ic_add, false, nsb, this::draft);
        for (var revision : model.revisions) {
            String summary = getString(R.string.sms_format_revision, revision.version(),
                    getString(revision.active() ? R.string.support_active
                            : revision.published() ? R.string.sms_format_published : R.string.support_draft_state));
            View card = cards.card(R.drawable.finora_ic_file, revision.active() ? StaffCards.Tone.ACCENT
                    : StaffCards.Tone.NEUTRAL, summary, null, summary);
            StaffCards.stack(card);
            cards.action(card, R.string.sms_format_validate_approved, R.drawable.finora_ic_check, false, summary,
                    () -> confirm(R.string.sms_format_validate_approved, summary, () -> model.validate(revision.version())));
            if (!revision.published()) {
                cards.action(card, R.string.records_edit, R.drawable.finora_ic_edit, false, summary,
                        () -> startActivity(new android.content.Intent(this, FormatSampleActivity.class)
                                .putExtra(FormatSampleActivity.REVISION, revision.version())));
                cards.action(card, R.string.sms_format_publish, R.drawable.finora_ic_tick, true, summary,
                        () -> confirm(R.string.sms_format_publish, summary, () -> model.publish(revision.version())));
                cards.action(card, R.string.records_remove, R.drawable.finora_ic_delete, false, summary,
                        () -> confirm(R.string.records_remove, summary, () -> model.discard(revision.version())));
            } else if (!revision.active()) {
                cards.action(card, R.string.sms_format_activate, R.drawable.finora_ic_tick, true, summary,
                        () -> confirm(R.string.sms_format_activate, summary, () -> model.activate(revision.version())));
            } else {
                cards.action(card, R.string.sms_format_disable, R.drawable.finora_ic_pause, false, summary,
                        () -> confirm(R.string.sms_format_disable, summary, () -> model.deactivate(revision.version())));
            }
        }
    }

    private String problem(SmsDiagnosticRepository.Code code) {
        return getString(switch (code) {
            case FORMAT_UNRECOGNIZED -> R.string.staff_diag_unrecognized;
            case FORMAT_AMBIGUOUS -> R.string.staff_diag_ambiguous;
            case REQUIRED_FIELD_INVALID -> R.string.staff_diag_field;
            case FORMAT_UNAVAILABLE -> R.string.staff_diag_unavailable;
        });
    }

    private String bank(String value) {
        return value == null ? getString(R.string.sms_diagnostic_unknown) : value;
    }

    private void draft() {
        if (!model.work.isCurrent()) return;
        var revision = new android.widget.EditText(this);
        revision.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        revision.setHint(R.string.sms_format_revision_number);
        var dialog = showSecure(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.sms_format_draft).setView(revision)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create());
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                int value = Integer.parseInt(revision.getText().toString());
                if (value < 2) throw new NumberFormatException();
                if (model.work.isCurrent()) model.draft(value);
                dialog.dismiss();
            } catch (NumberFormatException invalid) {
                revision.setError(getString(R.string.records_invalid));
            }
        });
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
