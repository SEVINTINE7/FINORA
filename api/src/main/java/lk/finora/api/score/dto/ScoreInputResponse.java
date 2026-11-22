package lk.finora.api.score.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

public record ScoreInputResponse(String month, BigDecimal emergencyReserve, LocalDate reserveAsOf,
        Boolean noApplicableDebt, Boolean noApplicableGoals, Instant confirmedAt) {}
