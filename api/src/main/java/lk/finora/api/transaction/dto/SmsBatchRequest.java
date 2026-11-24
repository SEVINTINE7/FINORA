package lk.finora.api.transaction.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record SmsBatchRequest(@NotNull @Size(min = 1, max = 200) List<JsonNode> items) {
    public record Item(@NotNull @Pattern(regexp = "[0-9a-f]{64}") String dedupHash,
                       @NotNull TransactionRequest transaction) {}
}
