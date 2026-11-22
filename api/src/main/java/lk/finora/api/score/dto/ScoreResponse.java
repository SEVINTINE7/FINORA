package lk.finora.api.score.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** D20 score and numeric provenance. Unavailable values are never silently replaced with zero. */
public record ScoreResponse(String month, String modelVersion, String status, Integer total,
        int scoredWeight, int missingWeight, int notApplicableWeight, LocalDate reserveAsOf,
        List<Part> parts) {
    public record Part(String key, int weight, String status, BigDecimal points, String reasonKey,
            Map<String, BigDecimal> facts) {}
}
