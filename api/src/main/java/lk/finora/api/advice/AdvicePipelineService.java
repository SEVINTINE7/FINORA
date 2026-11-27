package lk.finora.api.advice;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;

import lk.finora.api.advice.ai.AdviceItem;
import lk.finora.api.advice.ai.FactSheet;
import lk.finora.api.advice.ai.LlmClient;
import lk.finora.api.advice.rules.RuleAdviceEngine;
import lk.finora.api.advice.rules.RuleAdviceService;
import lk.finora.api.advice.verify.AdviceVerifier;
import lk.finora.api.advice.verify.VerificationSink;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.security.CurrentUser;
import lk.finora.api.user.Role;

/** Preparatory V1 service; T30 delivery is separate. No user id can be supplied by a caller. */
public class AdvicePipelineService {
    public record Delivery(String source, boolean verified, String reason, List<AdviceItem> advice) {}
    private final RuleAdviceService rules;
    private final List<LlmClient> providers;
    private final AdviceVerifier verifier;
    private final VerificationSink outcomes;
    private final StructuredCoachingPrototype structured;

    public AdvicePipelineService(RuleAdviceService rules, List<LlmClient> providers, AdviceVerifier verifier, VerificationSink outcomes) {
        this(rules, providers, verifier, outcomes, null);
    }

    static AdvicePipelineService forStructuredEvaluation(RuleAdviceService rules, LlmClient provider,
            VerificationSink outcomes) {
        return new AdvicePipelineService(rules, List.of(provider), new AdviceVerifier(), outcomes,
                new StructuredCoachingPrototype());
    }

    private AdvicePipelineService(RuleAdviceService rules, List<LlmClient> providers, AdviceVerifier verifier,
            VerificationSink outcomes, StructuredCoachingPrototype structured) {
        this.rules = rules;
        this.providers = List.copyOf(providers);
        this.verifier = verifier;
        this.outcomes = outcomes;
        this.structured = structured;
        if (providers.size() > 2 || providers.stream().map(LlmClient::provider).distinct().count() != providers.size()
                || providers.stream().anyMatch(p -> !Set.of("GROQ", "GEMINI").contains(p.provider())))
            throw new IllegalArgumentException("Provider order must contain unique GROQ/GEMINI entries");
    }

    public Delivery forCurrentUser(String month) {
        return forCurrentUser(month, null);
    }

    /** goalId must already be verified as the current user's goal. */
    public Delivery forCurrentUser(String month, Long goalId) {
        Long userId;
        try {
            userId = CurrentUser.id();
        } catch (IllegalStateException noSession) {
            throw new ApiException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (CurrentUser.role() != Role.USER) throw new ApiException("FORBIDDEN", HttpStatus.FORBIDDEN, "Access denied");
        var facts = rules.facts(userId, month, goalId);
        List<AdviceItem> eligible = RuleAdviceEngine.generate(facts);
        if (eligible.isEmpty()) return new Delivery("RULE", false, "NO_ELIGIBLE_ACTIONS", eligible);
        if (structured != null) return structuredDelivery(userId, month, facts);
        for (LlmClient provider : providers) {
            LlmClient.Result result;
            try { result = provider.generate(facts, eligible); }
            catch (RuntimeException ignored) { result = LlmClient.Result.failed(LlmClient.Failure.INVALID_RESPONSE); }
            if (result == null || !result.success()) {
                outcomes.record(userId, month, provider.provider(), false, AdviceVerifier.Reason.PROVIDER_FAILURE);
                continue;
            }
            var verdict = verifier.verify(result.reply(), facts);
            outcomes.record(userId, month, provider.provider(), verdict.verified(), verdict.reason());
            if (verdict.verified()) return new Delivery("AI", true, "ACCEPTED", verdict.advice());
            // Failed verification goes straight to rules, not another attempt to obtain an acceptable claim.
            return new Delivery("RULE", false, verdict.reason().name(), eligible);
        }
        return new Delivery("RULE", false, providers.isEmpty() ? "PROVIDERS_DISABLED" : "PROVIDERS_FAILED", eligible);
    }

    private Delivery structuredDelivery(long userId, String month, FactSheet facts) {
        var eligible = structured.eligibleFocus(facts).stream().map(Enum::name).toList();
        if (eligible.isEmpty()) {
            return new Delivery("RULE", false, "NO_ELIGIBLE_FOCUS", structured.deliver(facts, null).forDisplay());
        }
        var provider = providers.get(0);
        LlmClient.Result reply;
        try {
            reply = provider.chooseCoachingFocus(eligible);
        } catch (RuntimeException ignored) {
            reply = LlmClient.Result.failed(LlmClient.Failure.INVALID_RESPONSE);
        }
        boolean received = reply != null && reply.success();
        var delivery = structured.deliver(facts, received ? reply.reply() : null);
        var reason = delivery.personalized() ? AdviceVerifier.Reason.ACCEPTED
                : received ? AdviceVerifier.Reason.INVALID_SHAPE : AdviceVerifier.Reason.PROVIDER_FAILURE;
        outcomes.record(userId, month, provider.provider(), delivery.personalized(), reason);
        return new Delivery(delivery.personalized() ? "AI" : "RULE", delivery.personalized(),
                reason.name(), delivery.forDisplay());
    }
}
