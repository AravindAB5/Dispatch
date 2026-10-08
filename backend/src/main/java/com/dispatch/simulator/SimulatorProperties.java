package com.dispatch.simulator;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Development-only event simulator. Disabled unless {@code DISPATCH_SIMULATOR_ENABLED=true}; when disabled
 * the simulator beans and endpoints do not exist at all.
 */
@ConfigurationProperties(prefix = "dispatch.simulator")
public record SimulatorProperties(boolean enabled, boolean autoStart, Duration interval, int maxConcurrentProblems) {

    public SimulatorProperties {
        if (interval == null || interval.toMillis() < 250) {
            interval = Duration.ofSeconds(3);
        }
        if (maxConcurrentProblems <= 0) {
            maxConcurrentProblems = 6;
        }
    }
}
