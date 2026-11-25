package lk.finora.api.user;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lk.finora.api.account.FinanceWriteLock;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.user.dto.AlertSettings;
import lk.finora.api.user.dto.MeResponse;
import lk.finora.api.user.dto.UpdateMeRequest;

/** The logged-in user's own profile and consent (T07). */
@Service
public class MeService {

    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final ConsentRecoveryService recovery;
    private final FinanceWriteLock owners;

    public MeService(UserRepository userRepository, ConsentRepository consentRepository,
            ConsentRecoveryService recovery, FinanceWriteLock owners) {
        this.userRepository = userRepository;
        this.consentRepository = consentRepository;
        this.recovery = recovery;
        this.owners = owners;
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

    /** T34.4: turns each alert type on or off. Turned-off types get no new alert events. */
    @Transactional
    public MeResponse updateAlerts(Long userId, AlertSettings request) {
        User user = findUser(userId);
        user.setAlertBudget(request.budget());
        user.setAlertBill(request.bill());
        user.setAlertGoal(request.goal());
        userRepository.save(user);
        return toResponse(user);
    }

    /**
     * Marks the active consent as withdrawn and logs the user out on every device by revoking all
     * refresh tokens. Calling it again when nothing is active does nothing.
     */
    @Transactional
    public void withdrawConsent(Long userId) {
        owners.acquire(userId);
        Instant now = Instant.now();

        Consent consent = consentRepository.findFirstByUserIdAndWithdrawnAtIsNullOrderByAcceptedAtDesc(userId)
                .orElse(null);
        if (consent != null) {
            consent.setWithdrawnAt(now);
            consentRepository.saveAndFlush(consent);
            recovery.invalidateNormalSessions(userId);
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
        return new MeResponse(user.getEmail(), user.getFullName(), user.getLanguage().name(), consentVersion,
                new AlertSettings(user.isAlertBudget(), user.isAlertBill(), user.isAlertGoal()));
    }
}
