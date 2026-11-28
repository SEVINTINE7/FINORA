package lk.finora.app.ui.sms;

import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import java.io.IOException;

import lk.finora.app.R;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.data.repo.SmsDiagnosticRepository.Code;
import lk.finora.app.sms.SmsParseResult;

public final class PasteSmsActivity extends SecureActivity {
    private final SmsLocalTask task = new SmsLocalTask();
    private final Runnable sessionChanged = () -> runOnUiThread(this::verifySession);
    private TokenStore store;
    private Object loginIdentity;
    private TextView result;
    private Button paste;
    private Button previewButton;
    private SmsTransactionPreview preview;
    private SmsReportAction report;
    private android.view.View found;
    private android.widget.LinearLayout fields;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = TokenStore.current();
        var session = store == null ? null : store.verifiedSession();
        if (session == null || !"USER".equals(session.role)) {
            finish();
            return;
        }
        loginIdentity = session.loginIdentity();
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_paste_sms);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.sms_paste_title), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.sms_paste_found_heading), true);
        android.widget.LinearLayout steps = findViewById(R.id.sms_paste_steps);
        ImportRows.step(this, steps, 1, R.string.sms_paste_step_1);
        ImportRows.step(this, steps, 2, R.string.sms_paste_step_2);
        ImportRows.step(this, steps, 3, R.string.sms_paste_step_3);
        found = findViewById(R.id.sms_paste_found);
        fields = findViewById(R.id.sms_paste_fields);
        result = findViewById(R.id.sms_paste_result);
        SmsAccessActivity.showWhenText(result);
        paste = findViewById(R.id.sms_paste_action);
        preview = new SmsTransactionPreview(this, store, loginIdentity, "PASTE");
        previewButton = findViewById(R.id.sms_paste_preview);
        previewButton.setOnClickListener(view -> { if (verifySession()) preview.open(); });
        if (preview.hasPending()) previewButton.setVisibility(android.view.View.VISIBLE);
        paste.setOnClickListener(view -> paste());
        report = new SmsReportAction(this, findViewById(R.id.sms_diagnostic_report), this::verifySession, result::setText);
    }

    @Override protected void onStart() {
        super.onStart();
        if (verifySession()) store.addSessionListener(sessionChanged);
    }

    @Override protected void onResume() {
        super.onResume();
        verifySession();
    }

    @Override protected void onStop() {
        clear(isChangingConfigurations());
        if (loginIdentity != null) store.removeSessionListener(sessionChanged);
        super.onStop();
    }

    @Override protected void onDestroy() {
        task.close();
        if (preview != null) preview.close(isChangingConfigurations());
        super.onDestroy();
    }

    private boolean verifySession() {
        if (isFinishing() || loginIdentity == null) return false;
        var current = store.verifiedSession();
        if (current != null && current.loginIdentity() == loginIdentity && "USER".equals(current.role)) return true;
        clear();
        finish();
        return false;
    }

    private void paste() {
        if (!verifySession()) return;
        if (preview.hasPending()) {
            preview.open();
            return;
        }
        preview.clear();
        if (report != null) report.reset();
        previewButton.setVisibility(android.view.View.GONE);
        showFields(null);
        var clipboard = getSystemService(ClipboardManager.class);
        CharSequence message;
        try {
            var clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() != 1
                    || !clip.getDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                result.setText(R.string.sms_paste_plain_text);
                return;
            }
            message = clip.getItemAt(0).getText();
        } catch (SecurityException unavailable) {
            result.setText(R.string.sms_paste_plain_text);
            return;
        }
        if (message == null || message.length() == 0) {
            result.setText(R.string.sms_paste_plain_text);
            return;
        }
        if (message.length() > 2048) {
            result.setText(R.string.sms_needs_review);
            return;
        }
        String raw = message.toString();
        paste.setEnabled(false);
        result.setText(R.string.records_loading);
        task.start((signal, cancelled) -> {
            try {
                var parser = SmsLocalResults.load(this, store, loginIdentity, signal, cancelled);
                if (parser.approvedSenderIds().isEmpty()) {
                    return new PasteResult(getString(R.string.sms_no_formats) + "\n"
                            + getString(R.string.sms_needs_review), null, Code.FORMAT_UNAVAILABLE);
                }
                var parsed = SmsPasteInput.parse(parser, raw, cancelled);
                return new PasteResult(parsed instanceof SmsParseResult.Parsed ? "" : SmsLocalResults.text(this, parsed),
                        parsed instanceof SmsParseResult.Parsed value ? value : null,
                        parsed instanceof SmsParseResult.ManualReview review ? Code.from(review) : null);
            } catch (IOException invalidBundle) {
                return new PasteResult(getString(R.string.sms_scan_failed), null, null);
            }
        }, text -> {
            if (!verifySession()) return;
            result.setText(text.label());
            report.set(text.code());
            if (text.parsed() != null) {
                showFields(text.parsed());
                preview.replace(java.util.List.of(text.parsed()));
                previewButton.setText(getResources().getQuantityString(R.plurals.sms_review_count, 1, 1));
                previewButton.setVisibility(android.view.View.VISIBLE);
            }
            paste.setEnabled(true);
        }, () -> {
            if (!verifySession()) return;
            result.setText(R.string.sms_scan_failed);
            paste.setEnabled(true);
        });
    }

    /** T67/D71: the parsed fields as Amount/Date/Type/Merchant rows; spoken as the same one-line summary. */
    private void showFields(SmsParseResult.Parsed parsed) {
        fields.removeAllViews();
        found.setVisibility(parsed == null ? android.view.View.GONE : android.view.View.VISIBLE);
        if (parsed == null) return;
        ImportRows.field(this, fields, R.string.sms_field_amount, lk.finora.app.ui.Money.rupees(this, parsed.amount()));
        ImportRows.field(this, fields, R.string.sms_field_date, ImportRows.date(this, parsed.date()));
        ImportRows.field(this, fields, R.string.sms_field_type, getString(ImportRows.income(parsed)
                ? R.string.sms_type_income : R.string.sms_type_expense));
        ImportRows.field(this, fields, R.string.sms_field_merchant, parsed.merchant() == null
                ? getString(R.string.sms_field_none) : parsed.merchant());
        findViewById(R.id.sms_paste_fields_card).setContentDescription(SmsLocalResults.text(this, parsed));
    }

    private record PasteResult(String label, SmsParseResult.Parsed parsed, Code code) {}

    private void clear() { clear(false); }

    private void clear(boolean changingConfiguration) {
        if (report != null) report.reset();
        if (preview != null) preview.stop(changingConfiguration);
        if (previewButton != null) previewButton.setVisibility(android.view.View.GONE);
        if (found != null) showFields(null);
        task.cancel();
        if (result != null) result.setText("");
        if (paste != null) paste.setEnabled(true);
    }
}
