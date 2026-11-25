package lk.finora.api.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Creates and checks access tokens (JWT, HS256). Lifetime 30 minutes (decision D07). */
@Service
public class JwtService {

    public static final long ACCESS_TOKEN_SECONDS = 30 * 60;

    private final SecretKey key;

    public JwtService(@Value("${finora.jwt.secret}") String secret) {
        // HS256 needs a secret of at least 32 characters
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String role) {
        return createAccessToken(userId, role, 0);
    }

    public String createAccessToken(Long userId, String role, long staffSessionGeneration) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("staffGeneration", staffSessionGeneration)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ACCESS_TOKEN_SECONDS * 1000))
                .signWith(key)
                .compact();
    }

    /** Returns the token's claims. Throws an exception if the token is expired, changed or not ours. */
    public Claims readToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
