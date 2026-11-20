package lk.finora.api.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContributionResponse(Long id, Long goalId, BigDecimal amount, LocalDate date) {
}
