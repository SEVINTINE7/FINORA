package lk.finora.app.ui.sms;

import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import java.io.IOException;

import lk.finora.app.R;
import lk.finora.app.data.local.TokenStore;

public final class PasteSmsActivity extends AppCompatActivity {
    private final SmsLocalTask task = new SmsLocalTask();
    private final Runnable sessionChanged = () -> runOnUiThread(this::verifySession);
    private TokenStore store;
    private Object loginIdentity;
    private TextView result;
    private Button paste;
    private Button previewButton;
    private SmsTransactionPreview preview;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = TokenStore.current();
        var session = store == null ? null : store.verifiedSession();
        if (session == null || !"USER".equals(session.role)) {
            finish();
            return;
        }
        loginIdentity = session.loginIdentity();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_paste_sms);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.sms_paste_title), true);
        result = findViewById(R.id.sms_paste_result);
        paste = findViewById(R.id.sms_paste_action);
        preview = new SmsTransactionPreview(this, store, loginIdentity, "PASTE");
        previewButton = findViewById(R.id.sms_paste_preview);
        previewButton.setOnClickListener(view -> { if (verifySession()) preview.open(); });
        paste.setOnClickListener(view -> paste());
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
        clear();
        if (loginIdentity != null) store.removeSessionListener(sessionChanged);
        super.onStop();
    }

    @Override protected void onDestroy() {
        task.close();
        if (preview != null) preview.close();
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
        preview.clear();
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
                            + getString(R.string.sms_needs_review), null);
                }
                var parsed = SmsPasteInput.parse(parser, raw, cancelled);
                return new PasteResult(SmsLocalResults.text(this, parsed),
                        parsed instanceof lk.finora.app.sms.SmsParseResult.Parsed value ? value : null);
            } catch (IOException invalidBundle) {
                return new PasteResult(getString(R.string.sms_scan_failed), null);
            }
        }, text -> {
            if (!verifySession()) return;
            result.setText(text.label());
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

    private record PasteResult(String label, lk.finora.app.sms.SmsParseResult.Parsed parsed) {}

    private void clear() {
        if (preview != null) preview.clear();
        if (previewButton != null) previewButton.setVisibility(android.view.View.GONE);
        task.cancel();
        if (result != null) result.setText("");
        if (paste != null) paste.setEnabled(true);
    }
}
