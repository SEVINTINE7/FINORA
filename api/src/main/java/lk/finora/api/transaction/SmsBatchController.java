package lk.finora.api.transaction;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.finora.api.security.CurrentUser;
import lk.finora.api.transaction.dto.SmsBatchRequest;
import lk.finora.api.transaction.dto.SmsBatchResponse;

@RestController
class SmsBatchController {
    private final SmsBatchService service;

    SmsBatchController(SmsBatchService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/transactions/batch")
    SmsBatchResponse save(@Valid @RequestBody SmsBatchRequest request) {
        return service.save(CurrentUser.id(), request.items());
    }
}
