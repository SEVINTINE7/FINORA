package lk.finora.api.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** An FCM registration token for this phone (T34.1). */
public record DeviceRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "[A-Za-z0-9:_\\-]+", message = "must be an FCM token")
        String token) {
}
