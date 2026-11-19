package lk.finora.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The fields a user may change on their own profile. Email and password are not changed here. */
public record UpdateMeRequest(

        @NotBlank @Size(max = 100)
        String fullName,

        @NotNull @Pattern(regexp = "EN|SI", message = "must be EN or SI")
        String language) {
}
