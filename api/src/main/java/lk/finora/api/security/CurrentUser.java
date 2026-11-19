package lk.finora.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import lk.finora.api.user.Role;

/**
 * Who is calling the API, taken from the access token (JwtAuthFilter puts it in the security context).
 *
 * <p>Ownership pattern for user data: always load rows with the caller's id, for example
 * {@code repository.findByIdAndUserId(id, CurrentUser.id())}. If nothing comes back, throw
 * {@code ApiException.notFound(...)}, so another user's row looks the same as a missing one (404, never 403).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The logged-in user's id (the JWT "sub" claim). */
    public static Long id() {
        return (Long) auth().getPrincipal();
    }

    /** The logged-in user's role (the JWT "role" claim). */
    public static Role role() {
        // The filter gives exactly one authority, e.g. "ROLE_USER"
        String authority = auth().getAuthorities().iterator().next().getAuthority();
        return Role.valueOf(authority.substring("ROLE_".length()));
    }

    private static Authentication auth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            // Only happens if called from a public endpoint, which is a programming mistake
            throw new IllegalStateException("No logged-in user");
        }
        return auth;
    }
}
