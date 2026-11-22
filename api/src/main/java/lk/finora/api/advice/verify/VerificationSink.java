package lk.finora.api.advice.verify;

/** Stores only a bounded outcome, never rejected text. */
public interface VerificationSink {
    void record(Long userId, String month, String provider, boolean verified, AdviceVerifier.Reason reason);
}
