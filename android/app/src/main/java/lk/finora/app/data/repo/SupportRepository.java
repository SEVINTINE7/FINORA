package lk.finora.app.data.repo;

import android.content.Context;
import java.util.Map;
import lk.finora.app.R;
import lk.finora.app.data.repo.AccountRepository.ProtectedRequests;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class SupportRepository {
    public enum Topic {
        SIGN_IN(R.string.support_sign_in, "Sign-in", "I need help signing in."),
        NAVIGATION(R.string.support_navigation, "Navigation", "I need help finding a feature."),
        SMS_PERMISSION(R.string.support_sms, "SMS permission", "I need help with SMS permission."),
        LANGUAGE(R.string.support_language, "Language", "I need help changing the app language.");

        private final int label;
        private final String subject;
        private final String body;
        Topic(int label, String subject, String body) {
            this.label = label;
            this.subject = subject;
            this.body = body;
        }
        public String label(Context context) { return context.getString(label); }
        public static Topic matching(String subject) {
            for (Topic option : values()) if (option.subject.equals(subject)) return option;
            return null;
        }
    }

    public record Ticket(long id, String subject, String status) {}
    public record Reply(String body) {}
    public record Revision(long id, Topic topic, String state, String english, String sinhala) {}
    private final ProtectedRequests requests;

    public SupportRepository(ProtectedRequests requests) { this.requests = requests; }

    public Call tickets(boolean staff) { return get(staff ? "/api/v1/staff/tickets" : "/api/v1/support/tickets"); }
    public Call catalog() { return get("/api/v1/staff/reply-catalog"); }
    public Call replies(long id) { return get("/api/v1/support/tickets/" + id + "/replies"); }
    public Call create(Topic topic) {
        return post("/api/v1/support/tickets", Map.of("subject", topic.subject, "body", topic.body));
    }
    public Call claim(long id) { return post("/api/v1/staff/tickets/" + id + "/claim", null); }
    public Call close(long id) { return post("/api/v1/staff/tickets/" + id + "/close", null); }
    public Call reply(long id, long revision, String language) {
        return post("/api/v1/staff/tickets/" + id + "/replies", Map.of("revision", revision, "language", language));
    }
    public Call draft(Topic topic) { return post("/api/v1/staff/reply-catalog", Map.of("topic", topic.name())); }
    public Call approve(long id) { return post("/api/v1/staff/reply-catalog/" + id + "/approve", null); }
    public Call retire(long id) { return post("/api/v1/staff/reply-catalog/" + id + "/retire", null); }

    private Call get(String path) { return requests.call(request(path).build()); }
    private Call post(String path, Object content) {
        return requests.call(request(path).post(content == null ? RequestBody.create(new byte[0])
                : AccountRepository.json(content)).build());
    }
    private Request.Builder request(String path) {
        return new Request.Builder().url(requests.origin().newBuilder().encodedPath(path).build());
    }
}
