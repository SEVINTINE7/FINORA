package lk.finora.api.insight.dto;

import java.time.LocalDate;
import java.util.Map;

/** One spending pattern with the numbers behind it. The app builds the text from type + facts. */
public record InsightResponse(Long id, String type, String month, LocalDate from, LocalDate to,
        Map<String, Object> facts) {
}
