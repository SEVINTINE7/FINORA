package lk.finora.api.user.dto;

import jakarta.validation.constraints.NotNull;

/** T34.4: which alert types the user wants. All three are required on PUT /api/v1/me/alerts. */
public record AlertSettings(@NotNull Boolean budget, @NotNull Boolean bill, @NotNull Boolean goal) {
}
