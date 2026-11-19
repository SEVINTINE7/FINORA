package lk.finora.api.user.dto;

/** The logged-in user's own profile. consentVersion is null when consent was withdrawn (D15). */
public record MeResponse(String email, String fullName, String language, String consentVersion) {
}
