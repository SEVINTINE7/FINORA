package lk.finora.api.dashboard.dto;

import java.math.BigDecimal;

/** One pie slice: money spent in a category. categoryId and names are null for uncategorised records. */
public record CategoryTotal(Long categoryId, String name, String nameSi, BigDecimal total) {
}
