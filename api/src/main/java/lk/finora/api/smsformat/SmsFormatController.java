package lk.finora.api.smsformat;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lk.finora.api.smsformat.dto.SmsFormatsResponse;

@RestController
class SmsFormatController {
    private final SmsFormatService service;

    SmsFormatController(SmsFormatService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/sms-formats")
    ResponseEntity<SmsFormatsResponse> activeFormats() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.activeFormats());
    }
}
