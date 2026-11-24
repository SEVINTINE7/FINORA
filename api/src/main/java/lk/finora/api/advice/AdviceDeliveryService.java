package lk.finora.api.advice;

import java.util.List;
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
        if (month == null || !month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])"))
            throw ApiException.badRequest("month must be YYYY-MM with a year from 1000 to 9999");
        if (!"V1".equals(mode)) throw ApiException.badRequest("mode must be V1");
        long owner = CurrentUser.id();
        return deliveries.find(owner, month).orElseGet(() -> {
            // Provider execution happens before the short persistence transaction.
            var result = pipeline.forCurrentUser(month);
            if (result.advice().isEmpty()) {
                return deliveries.find(owner, month).orElseGet(() ->
                        new AdviceDeliveryRepository.Delivered(month, null, "RULE", false, List.of(), null));
            }
            if (!("AI".equals(result.source()) && result.verified()
                    || "RULE".equals(result.source()) && !result.verified())) {
                throw new IllegalStateException("Unexpected advice verification result");
            }
            return deliveries.publish(owner, month, result);
        });
    }

    public void rate(long id, int value) {
        if (id < 1 || value < 1 || value > 5) throw ApiException.badRequest("Invalid advice ID or rating");
        deliveries.rate(CurrentUser.id(), id, value);
    }
}
