package com.dispatch.incident;

import java.time.Clock;

import com.dispatch.user.User;

import org.springframework.stereotype.Component;

/** Appends audit entries to an incident's timeline. */
@Component
public class IncidentHistoryRecorder {

    private final IncidentHistoryRepository repository;
    private final Clock clock;

    public IncidentHistoryRecorder(IncidentHistoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Records a change made by Dispatch itself (no human actor). */
    public void system(Incident incident, HistoryAction action, String from, String to, String message) {
        record(incident, null, action, from, to, message);
    }

    public void record(Incident incident, User actor, HistoryAction action, String from, String to,
            String message) {
        repository.save(new IncidentHistory(incident, actor, action, from, to, message, clock.instant()));
    }
}
