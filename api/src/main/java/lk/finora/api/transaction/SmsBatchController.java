package lk.finora.api.transaction;

import java.util.ArrayList;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.security.CurrentUser;
import lk.finora.api.transaction.dto.SmsBatchRequest;
import lk.finora.api.transaction.dto.SmsBatchResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
class SmsBatchController {
    private final SmsBatchService service;

    private final JsonMapper mapper;

    SmsBatchController(SmsBatchService service, JsonMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/api/v1/transactions/batch")
    SmsBatchResponse save(@Valid @RequestBody SmsBatchRequest request) {
        var items = new ArrayList<SmsBatchRequest.Item>(request.items().size());
        for (int index = 0; index < request.items().size(); index++) {
            try {
                items.add(mapper.treeToValue(request.items().get(index), SmsBatchRequest.Item.class));
            } catch (JacksonException invalidItem) {
                throw ApiException.badRequest("Item " + (index + 1) + ": Invalid transaction fields");
            }
        }
        return service.save(CurrentUser.id(), items);
    }
}
