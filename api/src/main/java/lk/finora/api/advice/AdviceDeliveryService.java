package lk.finora.api.advice;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lk.finora.api.common.error.ApiException;
import lk.finora.api.security.CurrentUser;

@Service
public class AdviceDeliveryService {
    private final AdviceDeliveryRepository deliveries;
    private final AdvicePipelineService pipeline;

    public AdviceDeliveryService(AdviceDeliveryRepository deliveries, AdvicePipelineService pipeline) {
        this.deliveries = deliveries;
        this.pipeline = pipeline;
    }

    public AdviceDeliveryRepository.Delivered get(String month, String mode) {
        return get(month, mode, null);
    }

    /** D58: advice is generated and stored once per month and focus goal (null = no goal). */
    public AdviceDeliveryRepository.Delivered get(String month, String mode, Long goalId) {
        if (month == null || !month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])"))
            throw ApiException.badRequest("month must be YYYY-MM with a year from 1000 to 9999");
        if (!"V1".equals(mode)) throw ApiException.badRequest("mode must be V1");
        long owner = CurrentUser.id();
        if (goalId != null && (goalId < 1 || !deliveries.ownsGoal(owner, goalId))) throw ApiException.notFound("Goal not found");
        return deliveries.find(owner, month, goalId).orElseGet(() -> generate(owner, month, goalId));
    }

    private AdviceDeliveryRepository.Delivered generate(long owner, String month, Long goalId) {
        String token = UUID.randomUUID().toString();
        if (!deliveries.claimGeneration(owner, month, goalId, token)) {
            return deliveries.find(owner, month, goalId).orElseThrow(AdviceDeliveryService::generationInProgress);
        }
        try {
            // The lease is committed before contacting a provider. No database lock spans the call.
            var result = goalId == null ? pipeline.forCurrentUser(month) : pipeline.forCurrentUser(month, goalId);
            if (result.advice().isEmpty()) {
                return deliveries.find(owner, month, goalId).orElseGet(() ->
                        new AdviceDeliveryRepository.Delivered(month, null, "RULE", false, List.of(), null));
            }
            if (!("AI".equals(result.source()) && result.verified()
                    || "RULE".equals(result.source()) && !result.verified())) {
                throw new IllegalStateException("Unexpected advice verification result");
            }
            return deliveries.publish(owner, month, goalId, token, result);
        } finally {
            deliveries.releaseGeneration(owner, month, goalId, token);
        }
    }

    static ApiException generationInProgress() {
        return new ApiException("ADVICE_PENDING", HttpStatus.SERVICE_UNAVAILABLE,
                "Advice is being prepared. Retry shortly.");
    }

    public void rate(long id, int value) {
        if (id < 1 || value < 1 || value > 5) throw ApiException.badRequest("Invalid advice ID or rating");
        deliveries.rate(CurrentUser.id(), id, value);
    }
}
