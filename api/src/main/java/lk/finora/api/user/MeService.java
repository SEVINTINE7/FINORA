package lk.finora.api.user;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.auth.RefreshToken;
import lk.finora.api.auth.RefreshTokenRepository;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.user.dto.MeResponse;
import lk.finora.api.user.dto.UpdateMeRequest;

/** The logged-in user's own profile and consent (T07). */
@Service
public class MeService {

    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public MeService(UserRepository userRepository, ConsentRepository consentRepository,
            RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public MeResponse updateMe(Long userId, UpdateMeRequest request) {
        User user = findUser(userId);
        user.setFullName(request.fullName().trim());
        user.setLanguage(Language.valueOf(request.language()));
        userRepository.save(user);
        return toResponse(user);
    }

    /**
     * Marks the active consent as withdrawn and logs the user out on every device by revoking all
     * refresh tokens. Calling it again when nothing is active does nothing.
     */
    @Transactional
    public void withdrawConsent(Long userId) {
        Instant now = Instant.now();

        Consent consent = consentRepository.findFirstByUserIdAndWithdrawnAtIsNullOrderByAcceptedAtDesc(userId)
                .orElse(null);
        if (consent != null) {
            consent.setWithdrawnAt(now);
            consentRepository.save(consent);
        }

        for (RefreshToken token : refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)) {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private MeResponse toResponse(User user) {
        // null when the user withdrew consent: the app then asks to re-consent or delete data (D15)
        String consentVersion = consentRepository
                .findFirstByUserIdAndWithdrawnAtIsNullOrderByAcceptedAtDesc(user.getId())
                .map(Consent::getVersion)
                .orElse(null);
        return new MeResponse(user.getEmail(), user.getFullName(), user.getLanguage().name(), consentVersion);
    }
}
