package lk.finora.api.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import lk.finora.api.common.error.ApiException;
import lk.finora.api.security.CurrentUser;
import lk.finora.api.user.Consent;
import lk.finora.api.user.ConsentRepository;

/**
 * Test-only endpoint (lives in src/test, so it is never in the real app). It is a normal USER
 * endpoint that shows the ownership pattern: find by id AND the caller's id, else 404.
 */
@RestController
public class TestOwnershipController {

    public record ConsentDto(Long id, String version) {
    }

    private final ConsentRepository consentRepository;

    public TestOwnershipController(ConsentRepository consentRepository) {
        this.consentRepository = consentRepository;
    }

    @GetMapping("/api/v1/test/consents/{id}")
    public ConsentDto getConsent(@PathVariable Long id) {
        Consent consent = consentRepository.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("Consent not found"));
        return new ConsentDto(consent.getId(), consent.getVersion());
    }
}
