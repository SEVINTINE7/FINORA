package lk.finora.api.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // All refresh tokens of a user that are still usable (used when consent is withdrawn)
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}
