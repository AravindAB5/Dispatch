package com.dispatch.simulator;

import java.time.Instant;
import java.util.List;

public record SimulatorStatus(
        boolean running,
        Instant startedAt,
        long intervalMs,
        long eventsGenerated,
        int maxConcurrentProblems,
        List<String> activeProblems) {
}
