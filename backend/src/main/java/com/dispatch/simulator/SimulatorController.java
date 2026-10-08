package com.dispatch.simulator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only registered when the simulator is enabled; otherwise these endpoints return 404. */
@RestController
@RequestMapping("/api/v1/simulator")
@ConditionalOnProperty(prefix = "dispatch.simulator", name = "enabled", havingValue = "true")
public class SimulatorController {

    private final EventSimulator simulator;

    public SimulatorController(EventSimulator simulator) {
        this.simulator = simulator;
    }

    @GetMapping
    public SimulatorStatus status() {
        return simulator.status();
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    public SimulatorStatus start() {
        return simulator.start();
    }

    @PostMapping("/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public SimulatorStatus stop() {
        return simulator.stop();
    }
}
