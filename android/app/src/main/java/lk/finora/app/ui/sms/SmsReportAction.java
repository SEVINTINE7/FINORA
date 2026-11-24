package lk.finora.app.ui.sms;

import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import lk.finora.app.data.repo.SmsDiagnosticRepository;
import lk.finora.app.data.repo.SmsDiagnosticRepository.Code;
import lk.finora.app.ui.SecureActivity;
import lk.finora.app.sms.SmsInboxReader.BankCode;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

final class SmsReportAction {
    private final SecureActivity owner;
    private final Button button;
    private final BooleanSupplier permitted;
    private final IntConsumer message;
    private Code code;
    private BankCode bank;
    private Call active;
    private AlertDialog dialog;

    SmsReportAction(SecureActivity owner, Button button, BooleanSupplier permitted, IntConsumer message) {
        this.owner = owner;
        this.button = button;
        this.permitted = permitted;
        this.message = message;
        button.setOnClickListener(view -> confirm());
    }

    void set(Code value) {
        set(value, null);
    }

    void set(Code value, BankCode approvedBank) {
        reset();
        code = value;
        bank = approvedBank;
        button.setVisibility(code == null ? View.GONE : View.VISIBLE);
    }

    private void confirm() {
        if (!permitted.getAsBoolean() || code == null || active != null) return;
        Code selected = code;
        BankCode selectedBank = bank;
        var preview = SecureActivity.showSecure(new AlertDialog.Builder(owner).setTitle(R.string.sms_report_problem)
                .setMessage(owner.getString(R.string.sms_report_preview,
                        selectedBank == null ? owner.getString(R.string.sms_diagnostic_unknown) : selectedBank.name(),
                        selected.name()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sms_report_send, null).create());
        dialog = preview;
        preview.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (dialog != preview) return;
            send(selected, selectedBank);
            preview.dismiss();
        });
    }

    private void send(Code selected, BankCode selectedBank) {
        if (!permitted.getAsBoolean() || code != selected || bank != selectedBank || active != null) return;
        var requests = ProtectedRequests.currentUser();
        if (requests == null || !permitted.getAsBoolean()) return;
        Call call;
        try {
            call = SmsDiagnosticRepository.prepare(requests, selected, selectedBank);
        } catch (IllegalStateException ended) {
            permitted.getAsBoolean();
            return;
        }
        active = call;
        button.setEnabled(false);
        call.enqueue(new Callback() {
            @Override public void onFailure(Call request, IOException failure) { complete(false); }
            @Override public void onResponse(Call request, Response response) {
                try (response) { complete(response.code() == 204); }
            }
            private void complete(boolean success) {
                owner.runOnUiThread(() -> {
                    if (active != call || !permitted.getAsBoolean()) return;
                    active = null;
                    button.setEnabled(true);
                    if (success) reset();
                    message.accept(success ? R.string.sms_report_sent : R.string.sms_report_failed);
                });
            }
        });
    }

    void reset() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
        if (active != null) active.cancel();
        active = null;
        code = null;
        bank = null;
        button.setEnabled(true);
        button.setVisibility(View.GONE);
    }
}
