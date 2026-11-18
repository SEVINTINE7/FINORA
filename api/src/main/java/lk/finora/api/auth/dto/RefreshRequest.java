package lk.finora.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for both /auth/refresh and /auth/logout. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
