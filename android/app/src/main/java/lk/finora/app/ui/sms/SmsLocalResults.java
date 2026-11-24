package lk.finora.app.ui.sms;

import android.content.Context;
import android.os.CancellationSignal;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import lk.finora.app.BuildConfig;
import lk.finora.app.R;
import lk.finora.app.data.api.SessionClient;
import lk.finora.app.data.local.TokenStore;
import lk.finora.app.sms.SmsParseResult;
import lk.finora.app.sms.SmsParser;
import lk.finora.app.sms.SmsTemplateLoader;
import okhttp3.HttpUrl;
import okhttp3.Request;

final class SmsLocalResults {
    private SmsLocalResults() {}

    private static final Object FORMAT_FETCH = new Object();

    static SmsParser load(Context context, TokenStore store, Object login, CancellationSignal signal,
                          BooleanSupplier cancelled) throws IOException {
        var client = SessionClient.get(store);
        synchronized (FORMAT_FETCH) {
            SmsParser bundled;
            try (var input = context.getAssets().open("sms/bank_formats.json")) {
                bundled = SmsTemplateLoader.load(input);
            }
            var origin = HttpUrl.get(BuildConfig.API_BASE_URL);
            var url = origin.newBuilder().encodedPath("/api/v1/sms-formats").build();
            var request = new Request.Builder().url(url).get().build();
            okhttp3.Call call;
            synchronized (store) {
                requireCurrent(store, login, cancelled);
                call = client.newCall(request);
            }
            call.timeout().timeout(1100, TimeUnit.MILLISECONDS);
            signal.setOnCancelListener(call::cancel);
            try {
                var state = context.getSharedPreferences("sms_formats", Context.MODE_PRIVATE);
                String key = url.toString();
                return RemoteSmsFormats.load(call, bundled, disabled -> {
                    synchronized (store) {
                        requireCurrent(store, login, cancelled);
                        boolean previous = state.getBoolean(key, false);
                        if (disabled != null && disabled != previous
                                && !state.edit().putBoolean(key, disabled).commit()) {
                            throw new IOException("Could not save SMS format state");
                        }
                        return disabled == null ? previous : disabled;
                    }
                });
            } finally {
                signal.setOnCancelListener(null);
            }
        }
    }

    private static void requireCurrent(TokenStore store, Object login, BooleanSupplier cancelled) throws IOException {
        var session = store.verifiedSession();
        if (cancelled.getAsBoolean() || session == null || session.loginIdentity() != login
                || !"USER".equals(session.role)) throw new IOException("SMS operation ended");
    }

    static String text(Context context, SmsParseResult result) {
        if (result instanceof SmsParseResult.Parsed parsed) {
            String record = context.getString(R.string.sms_extracted_record,
                    parsed.amount().toPlainString(), parsed.date().toString(),
                    context.getString(parsed.direction() == SmsParseResult.Direction.DEBIT
                            ? R.string.sms_direction_debit : R.string.sms_direction_credit));
            return parsed.merchant() == null ? record
                    : record + "\n" + context.getString(R.string.sms_extracted_merchant, parsed.merchant());
        }
        return context.getString(R.string.sms_needs_review);
    }
}
