package lk.finora.api.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.finora.api.security.CurrentUser;
import lk.finora.api.user.dto.AlertSettings;
import lk.finora.api.user.dto.MeResponse;
import lk.finora.api.user.dto.UpdateMeRequest;

/** The logged-in user's own account settings. USER role only (T06 rules). */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping
    public MeResponse getMe() {
        return meService.getMe(CurrentUser.id());
    }

    @PutMapping
    public MeResponse updateMe(@Valid @RequestBody UpdateMeRequest request) {
        return meService.updateMe(CurrentUser.id(), request);
    }

    @PutMapping("/alerts")
    public MeResponse updateAlerts(@Valid @RequestBody AlertSettings request) {
        return meService.updateAlerts(CurrentUser.id(), request);
    }

    @PostMapping("/consent/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawConsent() {
        meService.withdrawConsent(CurrentUser.id());
    }
}
