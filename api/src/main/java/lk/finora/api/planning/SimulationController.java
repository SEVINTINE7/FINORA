package lk.finora.api.planning;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lk.finora.api.planning.dto.SimulationRequest;
import lk.finora.api.planning.dto.SimulationResponse;
import lk.finora.api.security.CurrentUser;

@RestController
@RequestMapping("/api/v1")
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    @PostMapping("/simulate")
    public SimulationResponse simulate(
            @Valid @RequestBody SimulationRequest request) {
        return service.simulate(CurrentUser.id(), request);
    }
}