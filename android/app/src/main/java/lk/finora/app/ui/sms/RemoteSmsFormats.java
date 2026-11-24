package lk.finora.app.ui.sms;

import java.io.IOException;

import lk.finora.app.sms.SmsParser;
import lk.finora.app.sms.SmsTemplateLoader;
import okhttp3.Call;

final class RemoteSmsFormats {
    interface Disablement {
        // Null means read the last known state without changing it.
        boolean resolve(Boolean disabled) throws IOException;
    }

    private RemoteSmsFormats() {}

    static SmsParser load(Call call, SmsParser bundled, Disablement disablement) throws IOException {
        SmsParser remote = fetchApproved(call, bundled);
        if (remote != null) {
            disablement.resolve(remote.approvedSenderIds().isEmpty());
            return remote;
        }
        if (!disablement.resolve(null)) return bundled;
        return SmsTemplateLoader.load(new java.io.ByteArrayInputStream(
                "{\"schemaVersion\":1,\"templates\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static SmsParser fetchApproved(Call call, SmsParser bundled) {
        try (var response = call.execute()) {
            var body = response.body();
            if (response.code() != 200 || body == null || body.contentType() == null
                    || !body.contentType().type().equals("application")
                    || !body.contentType().subtype().equals("json")) return null;
            var candidate = SmsTemplateLoader.load(body.byteStream());
            return candidate.approvedSenderIds().isEmpty() || candidate.hasSameTemplates(bundled)
                    ? candidate : null;
        } catch (IOException unavailable) {
            return null;
        }
    }
}
