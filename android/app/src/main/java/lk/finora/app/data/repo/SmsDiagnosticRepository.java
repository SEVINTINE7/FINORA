package lk.finora.app.data.repo;

import lk.finora.app.sms.SmsParseResult;
import lk.finora.app.sms.SmsInboxReader.BankCode;
import okhttp3.Call;
import okhttp3.Request;
import java.util.Map;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;

public final class SmsDiagnosticRepository {
    public enum Code {
        FORMAT_UNRECOGNIZED, FORMAT_AMBIGUOUS, REQUIRED_FIELD_INVALID, FORMAT_UNAVAILABLE;

        public static Code from(SmsParseResult.ManualReview review) {
            return switch (review.reason()) {
                case UNKNOWN_FORMAT -> FORMAT_UNRECOGNIZED;
                case AMBIGUOUS_FORMAT, CONFLICTING_INPUT -> FORMAT_AMBIGUOUS;
                case INVALID_FIELDS, UNSUPPORTED_CURRENCY -> REQUIRED_FIELD_INVALID;
                case INPUT_TOO_LARGE, LIMIT_REACHED, CANCELLED -> null;
            };
        }
    }

    private SmsDiagnosticRepository() {}

    public static Call prepare(AccountRepository.ProtectedRequests requests, Code code, BankCode bank) {
        var url = requests.origin().newBuilder().encodedPath("/api/v1/support/sms-diagnostics").build();
        return requests.call(new Request.Builder().url(url).post(reportBody(code, bank)).build());
    }

    static okhttp3.RequestBody reportBody(Code code, BankCode bank) {
        return AccountRepository.jsonIncludingNulls(new Report(bank, null, code));
    }

    private record Report(BankCode bankCode, Integer formatVersion, Code errorCode) {}

    public record Group(String bankCode, Integer formatVersion, Code errorCode, long reports) {}
    public record Escalation(long id, String bankCode, Code errorCode, String status) {}
    public record FormatRevision(int version, boolean published, boolean active) {}
    private record Flag(String bankCode, Code errorCode) {}

    public static Call groups(ProtectedRequests requests) {
        return requests.call(request(requests, "/api/v1/staff/sms-diagnostics").build());
    }

    public static Call flag(ProtectedRequests requests, Group group) {
        return requests.call(request(requests, "/api/v1/staff/sms-diagnostics/escalations")
                .post(AccountRepository.jsonIncludingNulls(new Flag(group.bankCode(), group.errorCode()))).build());
    }

    public static Call escalations(ProtectedRequests requests) {
        return requests.call(request(requests, "/api/v1/admin/sms-diagnostics/escalations").build());
    }

    public static Call advance(ProtectedRequests requests, Escalation escalation) {
        String next = switch (escalation.status()) {
            case "OPEN" -> "ACKNOWLEDGED";
            case "ACKNOWLEDGED" -> "CLOSED";
            default -> throw new IllegalArgumentException("Invalid diagnostic escalation");
        };
        return requests.call(request(requests, "/api/v1/admin/sms-diagnostics/escalations/" + escalation.id())
                .patch(AccountRepository.json(Map.of("status", next))).build());
    }

    public static Call revisions(ProtectedRequests requests) {
        return requests.call(request(requests, "/api/v1/admin/sms-formats/NSB/revisions").build());
    }

    public static Call changeRevision(ProtectedRequests requests, int revision, String action) {
        var url = requests.origin().newBuilder().encodedPath("/api/v1/admin/sms-formats/NSB/revisions")
                .addPathSegment(Integer.toString(revision));
        if (!action.isEmpty()) url.addPathSegment(action);
        return requests.call(new Request.Builder().url(url.build()).post(AccountRepository.json(Map.of())).build());
    }

    public record Definition(String definition) {}
    public record Validation(Boolean[] samples) {}

    public static Call readDraft(ProtectedRequests requests, int revision) {
        return requests.call(request(requests, draftPath(revision)).build());
    }

    public static Call editDraft(ProtectedRequests requests, int revision, String definition) {
        return requests.call(request(requests, draftPath(revision))
                .put(AccountRepository.json(new Definition(definition))).build());
    }

    private static String draftPath(int revision) {
        return "/api/v1/admin/sms-formats/NSB/revisions/" + revision;
    }

    public static Call discardRevision(ProtectedRequests requests, int revision) {
        return requests.call(request(requests, "/api/v1/admin/sms-formats/NSB/revisions/" + revision).delete().build());
    }

    private static Request.Builder request(ProtectedRequests requests, String path) {
        return new Request.Builder().url(requests.origin().newBuilder().encodedPath(path).build());
    }
}
