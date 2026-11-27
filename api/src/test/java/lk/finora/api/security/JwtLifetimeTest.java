package lk.finora.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

/** D07/T53: exact access-token lifetime, without sleeps or production credentials. */
class JwtLifetimeTest {
    private static final Instant ISSUED = Instant.parse("2026-11-27T00:00:00Z");
    private static final String FIXTURE_KEY = "test-only-lifetime-key-" + "0123456789abcdef".repeat(3);

    private JwtService serviceAt(Instant time) {
        return new JwtService(FIXTURE_KEY, Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test void issuedClaimsHaveExactlyThirtyMinutesForEveryRole() {
        JwtService service = serviceAt(ISSUED);
        assertThat(JwtService.ACCESS_TOKEN_SECONDS).isEqualTo(1800);
        for (String role : List.of("USER", "STAFF", "ADMIN")) {
            var claims = service.readToken(service.createAccessToken(42L, role, 7L));
            assertThat(claims.getIssuedAt().toInstant()).isEqualTo(ISSUED);
            assertThat(claims.getExpiration().toInstant()).isEqualTo(ISSUED.plusSeconds(1800));
            assertThat(claims.getSubject()).isEqualTo("42");
            assertThat(claims.get("role", String.class)).isEqualTo(role);
            assertThat(claims.get("staffGeneration", Long.class)).isEqualTo(7L);
        }
    }

    @Test void acceptedOneMillisecondBeforeExpiryButNotAtOrAfterIt() {
        String token = serviceAt(ISSUED).createAccessToken(42L, "USER");
        Instant expiry = ISSUED.plusSeconds(1800);
        assertThat(serviceAt(expiry.minusMillis(1)).readToken(token).getSubject()).isEqualTo("42");
        assertThatThrownBy(() -> serviceAt(expiry).readToken(token)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> serviceAt(expiry.plusMillis(1)).readToken(token)).isInstanceOf(JwtException.class);
    }

    @Test void correctlySignedTokenWithoutExpiryIsRejected() {
        String token = Jwts.builder().subject("42").claim("role", "USER")
                .signWith(Keys.hmacShaKeyFor(FIXTURE_KEY.getBytes(StandardCharsets.UTF_8))).compact();
        assertThatThrownBy(() -> serviceAt(ISSUED).readToken(token)).isInstanceOf(JwtException.class);
    }
}
