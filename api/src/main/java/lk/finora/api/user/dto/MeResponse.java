package lk.finora.api.user.dto;

/**
 * The logged-in user's own profile. consentVersion is null when consent was withdrawn (D15).
 * alerts holds the T34 per-type opt-outs (true = send that alert type).
 */
public record MeResponse(String email, String fullName, String language, String consentVersion,
        AlertSettings alerts) {
}
