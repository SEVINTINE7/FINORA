package lk.finora.api.auth.dto;

/** expiresIn = seconds until the access token expires. */
public record LoginResponse(String accessToken, String refreshToken, long expiresIn, String role, String language) {
}
