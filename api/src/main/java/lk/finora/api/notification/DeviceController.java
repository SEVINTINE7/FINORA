package lk.finora.api.notification;

import jakarta.validation.Valid;
import lk.finora.api.notification.dto.DeviceRequest;
import lk.finora.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** T34.1: register or remove this phone's push token. USER only (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody DeviceRequest request) {
        deviceService.register(CurrentUser.id(), request.token());
    }

    /** The token goes in the body, not the URL, so it never appears in access logs. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Valid @RequestBody DeviceRequest request) {
        deviceService.remove(CurrentUser.id(), request.token());
    }
}
