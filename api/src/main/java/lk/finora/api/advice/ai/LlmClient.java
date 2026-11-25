package lk.finora.api.advice.ai;

import java.util.List;

/** Server-only provider boundary; callers never receive unchecked text as deliverable advice. */
public interface LlmClient {
    String provider();
    Result generate(FactSheet facts, List<AdviceItem> eligibleActions);

    enum Failure { UNCONFIGURED, HTTP_ERROR, TIMEOUT, IO_ERROR, INTERRUPTED, INVALID_RESPONSE }
    record Result(String reply, Failure failure, int httpAttempts) {
        public Result(String reply, Failure failure) { this(reply, failure, 0); }
        public static Result failed(Failure reason) { return new Result(null, reason); }
        public static Result received(String reply) { return new Result(reply, null); }
        Result withHttpAttempts(int attempts) { return new Result(reply, failure, attempts); }
        public boolean success() { return failure == null && reply != null; }
    }
}
