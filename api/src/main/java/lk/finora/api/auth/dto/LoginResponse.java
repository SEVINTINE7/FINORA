package lk.finora.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A normal token pair or a password-authenticated, one-action consent grant. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(String accessToken, String refreshToken, long expiresIn, String role, String language,
                            String restrictedToken, String consentVersion) {
    public LoginResponse(String accessToken, String refreshToken, long expiresIn, String role, String language) {
        this(accessToken, refreshToken, expiresIn, role, language, null, null);
    }
}
