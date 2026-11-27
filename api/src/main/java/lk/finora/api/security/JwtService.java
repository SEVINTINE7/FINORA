package lk.finora.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Creates and checks access tokens (JWT, HS256). Lifetime 30 minutes (decision D07). */
@Service
public class JwtService {

    public static final long ACCESS_TOKEN_SECONDS = 30 * 60;

    private final SecretKey key;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${finora.jwt.secret}") String secret) {
        this(secret, Clock.systemUTC());
    }

    // Package-private clock seam for deterministic D07 lifetime checks.
    JwtService(String secret, Clock clock) {
        this.clock = clock;
        // HS256 needs a secret of at least 32 characters
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String role) {
        return createAccessToken(userId, role, 0);
    }

    public String createAccessToken(Long userId, String role, long staffSessionGeneration) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("staffGeneration", staffSessionGeneration)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_SECONDS)))
                .signWith(key)
                .compact();
    }

    /** Returns the token's claims. Throws an exception if the token is expired, changed or not ours. */
    public Claims readToken(String token) {
        Date now = Date.from(clock.instant());
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> now)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // At the expiry instant the token is no longer valid (D07).
        if (claims.getExpiration() == null || !claims.getExpiration().after(now)) {
            throw new JwtException("Access token expired or missing expiry");
        }
        return claims;
    }
}
