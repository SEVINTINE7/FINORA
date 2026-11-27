package lk.finora.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import lk.finora.api.account.FinanceWriteLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.finora.api.user.ConsentRecoveryService;

import lk.finora.api.auth.dto.LoginRequest;
import lk.finora.api.auth.dto.LoginResponse;
import lk.finora.api.auth.dto.RegisterRequest;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.common.error.ErrorCodes;
import lk.finora.api.security.JwtService;
import lk.finora.api.user.Consent;
import lk.finora.api.user.ConsentRepository;
import lk.finora.api.user.Language;
import lk.finora.api.user.Role;
import lk.finora.api.user.User;
import lk.finora.api.user.UserRepository;
import lk.finora.api.user.UserStatus;

@Service
public class AuthService {

    /** Current consent text version (decision D12). */
    public static final String CONSENT_VERSION = "1.0";

    /** Refresh tokens last 14 days (decision D07). */
    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(14);

    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ConsentRecoveryService recovery;
    private final FinanceWriteLock owners;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    @PersistenceContext
    private EntityManager entities;

    public AuthService(UserRepository userRepository, ConsentRepository consentRepository,
            RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            ConsentRecoveryService recovery, FinanceWriteLock owners, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.recovery = recovery;
        this.owners = owners;
        this.jdbc = jdbc;
    }

    @Transactional
    public Long register(RegisterRequest request) {
        if (!request.over18()) {
            throw new ApiException(ErrorCodes.UNDER_18, HttpStatus.BAD_REQUEST, "You must be 18 or older to use FINORA");
        }
        if (!CONSENT_VERSION.equals(request.consentVersion())) {
            throw ApiException.badRequest("Please accept the latest privacy consent (version " + CONSENT_VERSION + ")");
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCodes.EMAIL_TAKEN, HttpStatus.CONFLICT, "This email is already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setLanguage(Language.valueOf(request.language()));
        user.setRole(Role.USER);
        userRepository.save(user);

        Consent consent = new Consent();
        consent.setUserId(user.getId());
        consent.setVersion(request.consentVersion());
        consent.setOver18(true);
        consent.setAcceptedAt(Instant.now());
        consentRepository.save(consent);

        return user.getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        // T61: read credentials under the same account-row lock as password reset.
        User user = userRepository.findByEmailForUpdate(email).orElse(null);

        // Same error for unknown email and wrong password, so nobody can find out which emails are registered
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCodes.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Email or password is wrong");
        }
        // A reset may finish while BCrypt is checking the old hash. Serialize issuance with that reset.
        var hashes = jdbc.queryForList("SELECT password_hash FROM users WHERE id = ? FOR UPDATE",
                String.class, user.getId());
        if (hashes.isEmpty() || !user.getPasswordHash().equals(hashes.get(0))) {
            throw new ApiException(ErrorCodes.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED, "Email or password is wrong");
        }
        // The locking read does not refresh a previously managed JPA entity. Read the
        // current role, status, language and session generation while the row is locked.
        entities.refresh(user);
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ApiException(ErrorCodes.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN, "This account has been disabled");
        }

        if (user.getRole() == Role.USER) {
            owners.acquire(user.getId());
            long generation = currentGeneration(user.getId());
            if (recovery.withdrawn(user.getId())) {
                return new LoginResponse(null, null, ConsentRecoveryService.LIFETIME_SECONDS, null,
                        user.getLanguage().name(), recovery.issue(user.getId()), CONSENT_VERSION);
            }
            return createTokens(user, generation);
        }
        return createTokens(user, user.getStaffSessionGeneration());
    }

    /** Swaps a refresh token for a new pair. The old refresh token can never be used again (rotation, D07). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoginResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(refreshToken)).orElse(null);

        if (stored == null || stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCodes.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED,
                    "Refresh token is invalid or expired. Please log in again");
        }

        owners.acquire(stored.getUserId());
        if (jdbc.update("UPDATE refresh_tokens SET revoked_at = UTC_TIMESTAMP(6) WHERE id = ? AND revoked_at IS NULL "
                + "AND expires_at > UTC_TIMESTAMP(6)", stored.getId()) != 1) {
            throw invalidRefresh();
        }
        User user = userRepository.findById(stored.getUserId()).orElseThrow();
        entities.refresh(user);
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ApiException(ErrorCodes.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN, "This account has been disabled");
        }
        long generation = currentGeneration(user.getId());
        if (stored.getStaffSessionGeneration() != generation || (user.getRole() == Role.USER && recovery.withdrawn(user.getId()))) {
            throw invalidRefresh();
        }
        return createTokens(user, generation);
    }

    /** Revokes the refresh token. Unknown or already revoked tokens are ignored. */
    @Transactional
    public void logout(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(refreshToken)).orElse(null);
        if (stored != null && stored.getRevokedAt() == null) {
            stored.setRevokedAt(Instant.now());
            refreshTokenRepository.save(stored);
        }
    }

    private long currentGeneration(long id) {
        var generations = jdbc.queryForList(
                "SELECT staff_session_generation FROM users WHERE id = ? AND status = 'ACTIVE'", Long.class, id);
        if (generations.isEmpty()) {
            throw new ApiException(ErrorCodes.ACCOUNT_DISABLED, HttpStatus.FORBIDDEN, "This account has been disabled");
        }
        return generations.get(0);
    }

    private static ApiException invalidRefresh() {
        return new ApiException(ErrorCodes.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED, "Please log in again");
    }

    private LoginResponse createTokens(User user, long generation) {
        String accessToken = jwtService.createAccessToken(user.getId(), user.getRole().name(),
                generation);

        // The refresh token is 32 random bytes. The app keeps the token; we keep only its hash.
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setTokenHash(sha256(refreshToken));
        stored.setStaffSessionGeneration(generation);
        stored.setExpiresAt(Instant.now().plus(REFRESH_TOKEN_LIFETIME));
        refreshTokenRepository.save(stored);

        return new LoginResponse(accessToken, refreshToken, JwtService.ACCESS_TOKEN_SECONDS,
                user.getRole().name(), user.getLanguage().name());
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
