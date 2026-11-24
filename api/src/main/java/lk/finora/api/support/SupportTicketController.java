package lk.finora.api.support;

import java.util.List;
import lk.finora.api.common.error.ApiException;
import lk.finora.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
final class SupportTicketController {
    private final SupportTicketService tickets;

    SupportTicketController(SupportTicketService tickets) { this.tickets = tickets; }

    @PostMapping("/api/v1/support/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    TicketId create(@RequestBody JsonNode payload) {
        if (!payload.isObject() || payload.size() != 2 || !payload.path("subject").isString()
                || !payload.path("body").isString()) {
            throw ApiException.badRequest("Choose a supported help topic");
        }
        return new TicketId(tickets.create(CurrentUser.id(), payload.path("subject").asString(),
                payload.path("body").asString()));
    }

    @GetMapping("/api/v1/support/tickets")
    List<SupportTicketService.Ticket> own() { return tickets.own(CurrentUser.id()); }

    @GetMapping("/api/v1/support/tickets/{id}/replies")
    List<SupportTicketService.Reply> replies(@PathVariable long id) { return tickets.replies(CurrentUser.id(), id); }

    @GetMapping("/api/v1/staff/tickets")
    List<SupportTicketService.Ticket> queue() { return tickets.staff(CurrentUser.id()); }

    @PostMapping("/api/v1/staff/tickets/{id}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void claim(@PathVariable long id) { tickets.claim(CurrentUser.id(), id); }

    @PostMapping("/api/v1/staff/tickets/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@PathVariable long id) { tickets.close(CurrentUser.id(), id); }

    @PostMapping("/api/v1/staff/tickets/{id}/replies")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reply(@PathVariable long id, @RequestBody JsonNode payload) {
        if (!payload.isObject() || payload.size() != 2 || !payload.path("revision").isIntegralNumber()
                || !payload.path("revision").canConvertToLong() || !payload.path("language").isString()) {
            throw ApiException.badRequest("Choose a saved reply and language");
        }
        tickets.reply(CurrentUser.id(), id, payload.path("revision").asLong(), payload.path("language").asString());
    }

    @GetMapping("/api/v1/staff/reply-catalog")
    List<SupportTicketService.Revision> catalog() { return tickets.catalog(); }

    @PostMapping("/api/v1/staff/reply-catalog")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void draft(@RequestBody JsonNode payload) {
        if (!payload.isObject() || payload.size() != 1 || !payload.path("topic").isString()) {
            throw ApiException.badRequest("Choose a saved reply topic");
        }
        tickets.draft(CurrentUser.id(), payload.path("topic").asString());
    }

    @PostMapping("/api/v1/staff/reply-catalog/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable long id) { tickets.review(CurrentUser.id(), id); }

    @PostMapping("/api/v1/staff/reply-catalog/{id}/retire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retire(@PathVariable long id) { tickets.retire(id); }

    record TicketId(long id) {}
}
