package lk.finora.api.advice;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lk.finora.api.common.error.ApiException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/advice")
public class AdviceDeliveryController {
    private final AdviceDeliveryService service;
    public record Rating(int rating) {}

    public AdviceDeliveryController(AdviceDeliveryService service) { this.service = service; }

    @GetMapping
    public AdviceDeliveryRepository.Delivered get(@RequestParam String month, @RequestParam String mode) {
        return service.get(month, mode);
    }

    @PostMapping("/{id}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    public Rating rate(@PathVariable long id, @RequestBody JsonNode body) {
        JsonNode score = body.path("rating");
        if (!body.isObject() || body.size() != 1 || !score.isIntegralNumber() || !score.canConvertToInt()) {
            throw ApiException.badRequest("rating must be an integer from 1 to 5");
        }
        int value = score.intValue();
        service.rate(id, value);
        return new Rating(value);
    }
}
