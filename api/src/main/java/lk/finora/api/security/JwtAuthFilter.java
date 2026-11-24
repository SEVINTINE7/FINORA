package lk.finora.api.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.finora.api.common.error.ErrorCodes;
import lk.finora.api.common.error.ErrorResponse;
import lk.finora.api.user.User;
import lk.finora.api.user.UserRepository;
import lk.finora.api.user.UserStatus;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs once per request. If there is a valid "Authorization: Bearer <token>" header, the user is
 * marked as logged in. If the token is missing or bad we do nothing, and Spring Security
 * returns 401 for protected paths. A disabled account gets 403 even if its token is still valid (T06.3).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository, JsonMapper jsonMapper) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = null;
            try {
                claims = jwtService.readToken(token);
            } catch (JwtException | IllegalArgumentException e) {
                // Expired or invalid token: stay logged out
                SecurityContextHolder.clearContext();
            }

            if (claims != null) {
                Long userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);

                // Tokens live 30 minutes, so check the account on every request:
                // an admin may have disabled it after the token was issued
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.getStatus() == UserStatus.DISABLED) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(403,
                            ErrorCodes.ACCOUNT_DISABLED, "Account is disabled", request.getRequestURI()));
                    return;
                }

                Long generation = claims.get("staffGeneration", Long.class);
                if (user != null && user.getRole().name().equals(role)
                        && (!"STAFF".equals(role) || user.getStaffSessionGeneration() == (generation == null ? 0 : generation))) {
                    // The principal is the user id; read it with CurrentUser.id() or @AuthenticationPrincipal Long userId
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                // user == null (account deleted): stay logged out, so protected paths give 401
            }
        }

        chain.doFilter(request, response);
    }
}
