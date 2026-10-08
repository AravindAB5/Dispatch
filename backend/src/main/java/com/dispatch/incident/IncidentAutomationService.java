package com.dispatch.incident;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.dispatch.event.Event;
import com.dispatch.incident.IncidentChangedEvent.ChangeType;
import com.dispatch.incident.dto.IncidentAction;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.AlertRuleRepository;
import com.dispatch.rule.evaluation.EventFacts;
import com.dispatch.rule.evaluation.RuleEvaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns rule matches into incident changes.
 * <ul>
 *   <li><b>Trigger</b>: an event that matches a rule opens an incident, unless an unresolved incident with
 *       the same fingerprint already exists. In that case the event is attached to it as an occurrence
 *       (de-duplication), so a device reporting OFFLINE every few seconds yields one incident.</li>
 *   <li><b>Recovery</b>: an event matching a rule's recovery type resolves the active incident when the rule
 *       allows auto-resolution <em>and</em> nobody has started working on it (status OPEN). If an operator is
 *       already investigating, or the rule requires confirmation, the incident is only flagged as recovered
 *       and left for a human to close.</li>
 *   <li><b>Recurrence</b>: if the problem fires again after a recovery flag, the flag is cleared.</li>
 * </ul>
 */
@Service
public class IncidentAutomationService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAutomationService.class);

    private final AlertRuleRepository ruleRepository;
    private final RuleEvaluator ruleEvaluator;
    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentHistoryRecorder history;
    private final FingerprintLock fingerprintLock;
    private final IncidentMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public IncidentAutomationService(AlertRuleRepository ruleRepository, RuleEvaluator ruleEvaluator,
            IncidentRepository incidentRepository, IncidentEventRepository incidentEventRepository,
            IncidentHistoryRecorder history, FingerprintLock fingerprintLock, IncidentMapper mapper,
            ApplicationEventPublisher publisher, Clock clock) {
        this.ruleRepository = ruleRepository;
        this.ruleEvaluator = ruleEvaluator;
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.history = history;
        this.fingerprintLock = fingerprintLock;
        this.mapper = mapper;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Must run inside the transaction that persisted the event. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<IncidentAction> process(Event event, EventFacts facts) {
        List<AlertRule> candidates = ruleRepository.findCandidatesFor(facts.eventType());
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<IncidentAction> actions = new ArrayList<>();
        // Recoveries first: an event that both clears one problem and signals another is handled in order.
        List<AlertRule> recovered = ruleEvaluator.recoveredRules(facts, candidates);
        for (AlertRule rule : recovered) {
            recover(rule, event, facts).ifPresent(actions::add);
        }
        for (AlertRule rule : ruleEvaluator.triggeredRules(facts, candidates)) {
            // A recovery signal never re-opens the problem it just cleared (possible with severity rules).
            if (!recovered.contains(rule)) {
                actions.add(trigger(rule, event, facts));
            }
        }
        return actions;
    }

    private IncidentAction trigger(AlertRule rule, Event event, EventFacts facts) {
        String fingerprint = IncidentFingerprint.of(rule, facts);
        fingerprintLock.acquire(fingerprint);

        Optional<Incident> existing = incidentRepository.findActiveByFingerprint(fingerprint);
        if (existing.isPresent()) {
            Incident incident = existing.get();
            incident.recordOccurrence(event.getOccurredAt());
            incident.touch(now());
            link(incident, event, IncidentEventRelation.OCCURRENCE);
            IncidentAction.Type type = IncidentAction.Type.DEDUPLICATED;
            if (incident.getRecoveredAt() != null) {
                incident.clearRecovery();
                history.system(incident, HistoryAction.RECURRED, null, null,
                        "Problem recurred after a recovery signal (%s)".formatted(event.getEventType()));
                type = IncidentAction.Type.RECURRED;
            }
            publish(ChangeType.UPDATED, incident);
            return IncidentAction.of(type, incident, rule);
        }

        Incident incident = Incident.fromRule(rule, event, IncidentTitles.render(rule, event, facts), fingerprint);
        incidentRepository.save(incident);
        link(incident, event, IncidentEventRelation.TRIGGER);
        history.system(incident, HistoryAction.CREATED, null, incident.getSeverity().name(),
                "Incident created by rule \"%s\" from a %s event".formatted(rule.getName(), event.getEventType()));
        publish(ChangeType.CREATED, incident);
        log.info("Opened {} ({}) from event {} via rule '{}'", incident.getKey(), incident.getSeverity(),
                event.getId(), rule.getName());
        return IncidentAction.of(IncidentAction.Type.CREATED, incident, rule);
    }

    private Optional<IncidentAction> recover(AlertRule rule, Event event, EventFacts facts) {
        String fingerprint = IncidentFingerprint.of(rule, facts);
        fingerprintLock.acquire(fingerprint);

        Optional<Incident> active = incidentRepository.findActiveByFingerprint(fingerprint);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        Incident incident = active.get();
        link(incident, event, IncidentEventRelation.RECOVERY);
        Instant now = now();

        if (rule.isAutoResolve() && incident.getStatus() == IncidentStatus.OPEN) {
            incident.autoResolve(now, "Automatically resolved: %s received from %s"
                    .formatted(event.getEventType(), event.getSourceId()));
            history.system(incident, HistoryAction.AUTO_RESOLVED, IncidentStatus.OPEN.name(),
                    IncidentStatus.RESOLVED.name(),
                    "Automatically resolved after %s was received".formatted(event.getEventType()));
            publish(ChangeType.RESOLVED, incident);
            log.info("Auto-resolved {} on {}", incident.getKey(), event.getEventType());
            return Optional.of(IncidentAction.of(IncidentAction.Type.AUTO_RESOLVED, incident, rule));
        }

        if (incident.getRecoveredAt() == null) {
            incident.markRecovered(now);
            incident.touch(now);
            String reason = rule.isAutoResolve()
                    ? "it is already being investigated"
                    : "rule \"%s\" requires operator confirmation".formatted(rule.getName());
            history.system(incident, HistoryAction.RECOVERY_DETECTED, null, null,
                    "Recovery signal %s received; left %s because %s"
                            .formatted(event.getEventType(), incident.getStatus(), reason));
            publish(ChangeType.UPDATED, incident);
        }
        return Optional.of(IncidentAction.of(IncidentAction.Type.RECOVERY_DETECTED, incident, rule));
    }

    private void link(Incident incident, Event event, IncidentEventRelation relation) {
        incidentEventRepository.save(new IncidentEvent(incident, event, relation, now()));
    }

    private void publish(ChangeType type, Incident incident) {
        publisher.publishEvent(new IncidentChangedEvent(type, mapper.toSummary(incident)));
    }

    private Instant now() {
        return clock.instant();
    }
}
