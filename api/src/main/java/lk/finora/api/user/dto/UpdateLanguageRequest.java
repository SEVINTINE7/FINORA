package lk.finora.api.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** PATCH /api/v1/me (D60): changes only the app language, so it can never overwrite a newer name. */
public record UpdateLanguageRequest(

        @NotNull @Pattern(regexp = "EN|SI", message = "must be EN or SI")
        String language) {
}
