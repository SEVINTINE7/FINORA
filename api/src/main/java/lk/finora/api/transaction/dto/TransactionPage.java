package lk.finora.api.transaction.dto;

import java.util.List;

/** One page of the transaction list. page starts at 0. */
public record TransactionPage(List<TransactionResponse> items, int page, int size, long totalItems, int totalPages) {
}
