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
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.SmsDiagnosticRepository;
import lk.finora.app.data.repo.SmsDiagnosticRepository.Code;
import lk.finora.app.sms.SmsParseResult;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class PasteSmsActivity extends SecureActivity {
    private final SmsLocalTask task = new SmsLocalTask();
    private final Runnable sessionChanged = () -> runOnUiThread(this::verifySession);
    private TokenStore store;
    private Object loginIdentity;
    private TextView result;
    private Button paste;
    private Button previewButton;
    private SmsTransactionPreview preview;
    private Button reportButton;
    private Code reportCode;
    private Call reportCall;
    private androidx.appcompat.app.AlertDialog reportDialog;

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
        result = findViewById(R.id.sms_paste_result);
        paste = findViewById(R.id.sms_paste_action);
        preview = new SmsTransactionPreview(this, store, loginIdentity, "PASTE");
        previewButton = findViewById(R.id.sms_paste_preview);
        previewButton.setOnClickListener(view -> { if (verifySession()) preview.open(); });
        if (preview.hasPending()) previewButton.setVisibility(android.view.View.VISIBLE);
        paste.setOnClickListener(view -> paste());
        reportButton = findViewById(R.id.sms_diagnostic_report);
        reportButton.setOnClickListener(view -> confirmReport());
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
        resetReport();
        previewButton.setVisibility(android.view.View.GONE);
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
                return new PasteResult(SmsLocalResults.text(this, parsed),
                        parsed instanceof SmsParseResult.Parsed value ? value : null,
                        parsed instanceof SmsParseResult.ManualReview review ? Code.from(review) : null);
            } catch (IOException invalidBundle) {
                return new PasteResult(getString(R.string.sms_scan_failed), null, null);
            }
        }, text -> {
            if (!verifySession()) return;
            result.setText(text.label());
            reportCode = text.code();
            reportButton.setVisibility(reportCode == null ? android.view.View.GONE : android.view.View.VISIBLE);
            if (text.parsed() != null) {
                preview.replace(java.util.List.of(text.parsed()));
                previewButton.setVisibility(android.view.View.VISIBLE);
            }
            paste.setEnabled(true);
        }, () -> {
            if (!verifySession()) return;
            result.setText(R.string.sms_scan_failed);
            paste.setEnabled(true);
        });
    }

    private record PasteResult(String label, SmsParseResult.Parsed parsed, Code code) {}

    private void confirmReport() {
        if (!verifySession() || reportCode == null || reportCall != null) return;
        Code confirmed = reportCode;
        reportDialog = showSecure(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.sms_report_problem)
                .setMessage(getString(R.string.sms_report_preview, confirmed.name()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sms_report_send, (dialog, which) -> sendReport(confirmed)).create());
    }

    private void sendReport(Code confirmed) {
        if (!verifySession() || reportCode != confirmed) return;
        var requests = ProtectedRequests.currentUser();
        if (requests == null || !verifySession()) return;
        Call call;
        try {
            call = SmsDiagnosticRepository.prepare(requests, confirmed);
        } catch (IllegalStateException ended) {
            verifySession();
            return;
        }
        reportCall = call;
        reportButton.setEnabled(false);
        call.enqueue(new Callback() {
            @Override public void onFailure(Call request, IOException failure) { complete(false); }
            @Override public void onResponse(Call request, Response response) {
                try (response) { complete(response.code() == 204); }
            }
            private void complete(boolean success) {
                runOnUiThread(() -> {
                    if (reportCall != call || !verifySession()) return;
                    reportCall = null;
                    reportButton.setEnabled(true);
                    if (success) resetReport();
                    result.setText(success ? R.string.sms_report_sent : R.string.sms_report_failed);
                });
            }
        });
    }

    private void resetReport() {
        if (reportDialog != null) reportDialog.dismiss();
        reportDialog = null;
        if (reportCall != null) reportCall.cancel();
        reportCall = null;
        reportCode = null;
        if (reportButton != null) {
            reportButton.setEnabled(true);
            reportButton.setVisibility(android.view.View.GONE);
        }
    }

    private void clear() { clear(false); }

    private void clear(boolean changingConfiguration) {
        resetReport();
        if (preview != null) preview.stop(changingConfiguration);
        if (previewButton != null) previewButton.setVisibility(android.view.View.GONE);
        task.cancel();
        if (result != null) result.setText("");
        if (paste != null) paste.setEnabled(true);
    }
}
