package lk.finora.app.ui.support;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import lk.finora.app.R;
import lk.finora.app.databinding.ActivityFinoraScreenBinding;
import lk.finora.app.ui.ScreenControls;
import lk.finora.app.data.repo.AccountRepository;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.SmsDiagnosticRepository;
import lk.finora.app.ui.accounts.AccountListViewModel.Work;
import lk.finora.app.sms.SmsParseResult.Parsed;
import lk.finora.app.sms.SmsTemplateLoader;
import lk.finora.app.ui.SecureActivity;

public final class FormatSampleActivity extends SecureActivity {
    static final String REVISION = "formatRevision";
    private ProtectedRequests admin;
    private Work work;
    private Button save;
    private int revision;
    private boolean loaded;
    private EditText definition;
    private EditText sample;
    private TextView result;
    private final Runnable sessionChange = () -> runOnUiThread(this::checkSession);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        admin = ProtectedRequests.currentAdmin();
        if (!checkSession()) return;
        revision = getIntent().getIntExtra(REVISION, 0);
        var binding = ActivityFinoraScreenBinding.inflate(getLayoutInflater());
        var page = binding.screenPage;
        page.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        var title = ScreenControls.text(this);
        title.setText(R.string.sms_format_sample_check);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        page.addView(title);
        definition = field(R.string.sms_format_candidate, 65536);
        sample = field(R.string.sms_format_synthetic_sample, 2048);
        ScreenControls.addField(page, definition);
        ScreenControls.addField(page, sample);
        var check = ScreenControls.action(this);
        check.setText(R.string.sms_format_sample_check);
        check.setOnClickListener(view -> checkSample());
        page.addView(check);
        result = ScreenControls.text(this);
        result.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(result);
        TextWatcher invalidateResult = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                result.setText("");
            }
            @Override public void afterTextChanged(Editable text) {}
        };
        definition.addTextChangedListener(invalidateResult);
        sample.addTextChangedListener(invalidateResult);
        if (revision > 0) {
            save = ScreenControls.action(this);
            save.setText(R.string.records_save);
            save.setOnClickListener(view -> saveDraft());
            page.addView(save, 1);
            work = new Work(admin, () -> {
                definition.setText("");
                sample.setText("");
            });
            work.changes.observe(this, ignored -> {
                if (!checkSession()) return;
                save.setEnabled(loaded && !work.busy());
                definition.setEnabled(loaded && !work.busy());
                if (work.busy()) result.setText(R.string.records_loading);
                else if (work.error != 0) result.setText(work.error);
            });
        }
        setContentView(binding.getRoot());
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
            result.setText(R.string.records_failed);
        }
    }

    @Override protected void onStop() {
        if (admin != null) admin.removeListener(sessionChange);
        if (definition != null) {
            if (revision == 0) definition.setText("");
            sample.setText("");
            result.setText("");
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

    private EditText field(int title, int limit) {
        var field = ScreenControls.field(this);
        field.setHint(title);
        field.setSaveEnabled(false);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        field.setMinLines(2);
        field.setMaxLines(8);
        return field;
    }

    private void checkSample() {
        if (!checkSession()) return;
        try (var input = getAssets().open("sms/bank_formats.json")) {
            var approved = SmsTemplateLoader.load(input);
            var verdict = FormatSampleCheck.check(approved, definition.getText().toString(), sample.getText().toString());
            int approval = verdict.approvedDefinition() ? R.string.sms_format_approved_match
                    : R.string.sms_format_candidate_unapproved;
            int parsed = verdict.outcome() instanceof Parsed ? R.string.sms_format_sample_matched
                    : R.string.sms_format_sample_unmatched;
            result.setText(getString(R.string.sms_format_sample_result, getString(approval), getString(parsed)));
        } catch (IOException | IllegalArgumentException failure) {
            result.setText(R.string.records_invalid);
        }
    }
}
