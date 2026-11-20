package lk.finora.api.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One calculated number and where it came from (T21.2): the amount, the period it covers
 * and how many records were added up. Later the AI advice check (T27) compares against these.
 */
public record Metric(BigDecimal amount, LocalDate from, LocalDate to, int count) {
}
