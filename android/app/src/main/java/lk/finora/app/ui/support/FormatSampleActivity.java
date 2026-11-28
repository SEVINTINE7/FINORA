package lk.finora.app.ui.support;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;

import java.io.IOException;

import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.SmsDiagnosticRepository;
import lk.finora.app.sms.SmsParseResult.Parsed;
import lk.finora.app.sms.SmsTemplateLoader;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;

/**
 * T70.3/D72 Test a format (ADMIN): checks a candidate NSB format against a made-up SMS on this phone and shows two
 * results with icon and words. With a draft revision it also loads and saves that draft's definition.
 */
public final class FormatSampleActivity extends SecureActivity {
    static final String REVISION = "formatRevision";
    private ProtectedRequests admin;
    private Work work;
    private Button save;
    private int revision;
    private boolean loaded;
    private EditText definition;
    private EditText sample;
    private TextView status;
    private final Runnable sessionChange = () -> runOnUiThread(this::checkSession);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        admin = ProtectedRequests.currentAdmin();
        if (!checkSession()) return;
        revision = getIntent().getIntExtra(REVISION, 0);
        getWindow().setBackgroundDrawableResource(R.color.finora_surface);
        setContentView(R.layout.activity_format_sample);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.format_heading), true);
        definition = field(R.id.format_definition, 65536);
        sample = field(R.id.format_sample, 2048);
        status = findViewById(R.id.format_status);
        findViewById(R.id.format_check).setOnClickListener(view -> checkSample());
        TextWatcher invalidateResult = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                clearResult();
            }
            @Override public void afterTextChanged(Editable text) {}
        };
        definition.addTextChangedListener(invalidateResult);
        sample.addTextChangedListener(invalidateResult);
        if (revision > 0) {
            save = findViewById(R.id.format_save);
            save.setVisibility(View.VISIBLE);
            save.setOnClickListener(view -> saveDraft());
            work = new Work(admin, () -> {
                definition.setText("");
                sample.setText("");
            });
            work.changes.observe(this, ignored -> {
                if (!checkSession()) return;
                save.setEnabled(loaded && !work.busy());
                definition.setEnabled(loaded && !work.busy());
                int message = work.busy() ? R.string.records_loading : work.error;
                status.setText(message == 0 ? "" : getString(message));
                status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
            });
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (!checkSession()) return;
        admin.addListener(sessionChange);
        if (revision > 0) {
            work.run(() -> SmsDiagnosticRepository.readDraft(admin, revision), response -> {
                var draft = AccountRepository.read(response, SmsDiagnosticRepository.Definition.class);
                if (draft.definition() == null || draft.definition().length() > 65536) {
                    throw new IOException("Invalid draft response");
                }
                return draft.definition();
            }, value -> {
                definition.setText(value);
                loaded = true;
            }, false);
            return;
        }
        try (var input = okio.Okio.buffer(okio.Okio.source(getAssets().open("sms/bank_formats.json")))) {
            definition.setText(input.readUtf8());
        } catch (IOException failure) {
            showStatus(R.string.records_failed);
        }
    }

    @Override protected void onStop() {
        if (admin != null) admin.removeListener(sessionChange);
        if (definition != null) {
            if (revision == 0) definition.setText("");
            sample.setText("");
            clearResult();
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (work != null) work.close();
        super.onDestroy();
    }

    private void saveDraft() {
        if (!loaded || !checkSession()) return;
        String candidate = definition.getText().toString();
        work.run(() -> SmsDiagnosticRepository.editDraft(admin, revision, candidate), Work::empty,
                ignored -> finish(), false);
    }

    private boolean checkSession() {
        if (admin != null && admin.isCurrent()) return true;
        finish();
        return false;
    }

    private EditText field(int id, int limit) {
        EditText field = findViewById(id);
        field.setSaveEnabled(false);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        field.setOnFocusChangeListener((view, focused) -> view.setActivated(focused));
        return field;
    }

    private void showStatus(int message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    private void clearResult() {
        findViewById(R.id.format_result).setVisibility(View.GONE);
        if (work == null || !work.busy()) status.setVisibility(View.GONE);
    }

    private void checkSample() {
        if (!checkSession()) return;
        try (var input = getAssets().open("sms/bank_formats.json")) {
            var approved = SmsTemplateLoader.load(input);
            var verdict = FormatSampleCheck.check(approved, definition.getText().toString(), sample.getText().toString());
            boolean matches = verdict.approvedDefinition();
            boolean parsed = verdict.outcome() instanceof Parsed;
            result(findViewById(R.id.format_result_approval), matches,
                    matches ? R.string.sms_format_approved_match : R.string.sms_format_candidate_unapproved);
            result(findViewById(R.id.format_result_parsed), parsed,
                    parsed ? R.string.sms_format_sample_matched : R.string.sms_format_sample_unmatched);
            status.setVisibility(View.GONE);
            findViewById(R.id.format_result).setVisibility(View.VISIBLE);
        } catch (IOException | IllegalArgumentException failure) {
            findViewById(R.id.format_result).setVisibility(View.GONE);
            showStatus(R.string.records_invalid);
        }
    }

    /** One result line: green tick or red cross, plus the words (never colour alone). */
    private void result(TextView line, boolean good, int text) {
        int ink = getColor(good ? R.color.finora_green : R.color.finora_error);
        line.setText(text);
        line.setTextColor(ink);
        line.setCompoundDrawablesRelativeWithIntrinsicBounds(good ? R.drawable.finora_ic_check
                : R.drawable.finora_ic_close, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(line, ColorStateList.valueOf(ink));
    }
}
