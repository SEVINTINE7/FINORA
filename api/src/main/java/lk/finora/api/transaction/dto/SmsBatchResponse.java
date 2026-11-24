package lk.finora.api.transaction.dto;

import java.util.List;

public record SmsBatchResponse(List<Item> items) {
    public enum Status { SAVED, DUPLICATE }
    public record Item(int index, Status status, List<Long> possibleManualMatch) {}
}
