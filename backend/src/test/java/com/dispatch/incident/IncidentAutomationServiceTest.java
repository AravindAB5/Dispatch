package com.dispatch.incident;

import static com.dispatch.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dispatch.device.Device;
import com.dispatch.event.Event;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.EventTypes;
import com.dispatch.incident.dto.IncidentAction;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.AlertRuleRepository;
import com.dispatch.rule.evaluation.EventFacts;
import com.dispatch.rule.evaluation.EventTypeCondition;
import com.dispatch.rule.evaluation.MetricThresholdCondition;
import com.dispatch.rule.evaluation.RuleEvaluator;
import com.dispatch.rule.evaluation.SeverityAtLeastCondition;
import com.dispatch.service.MonitoredService;
import com.dispatch.support.Fixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class IncidentAutomationServiceTest {

    @Mock
    AlertRuleRepository ruleRepository;
    @Mock
    IncidentRepository incidentRepository;
    @Mock
    IncidentEventRepository linkRepository;
    @Mock
    IncidentHistoryRepository historyRepository;
    @Mock
    FingerprintLock fingerprintLock;
    @Mock
    ApplicationEventPublisher publisher;

    private IncidentAutomationService automation;

    private final MonitoredService gateway = Fixtures.service(3, "Device Gateway");
    private final Device device108 = Fixtures.device(8, "DEVICE-108", gateway);
    private final AlertRule offlineRule = Fixtures.offlineRule(1);
    private final AlertRule cpuRule = Fixtures.cpuRule(2);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RuleEvaluator evaluator = new RuleEvaluator(List.of(
                new EventTypeCondition(), new MetricThresholdCondition(), new SeverityAtLeastCondition()));
        automation = new IncidentAutomationService(ruleRepository, evaluator, incidentRepository, linkRepository,
                new IncidentHistoryRecorder(historyRepository, clock), fingerprintLock, new IncidentMapper(),
                publisher, clock);
        lenient().when(ruleRepository.findCandidatesFor(anyString())).thenReturn(List.of(offlineRule, cpuRule));
    }

    private Event offlineEvent(long id) {
        return Fixtures.deviceEvent(id, EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL, device108, "{}");
    }

    private Event recoveredEvent(long id) {
        return Fixtures.deviceEvent(id, EventTypes.DEVICE_RECOVERED, EventSeverity.INFO, device108, "{}");
    }

    private List<IncidentAction> process(Event event, Map<String, Object> metadata) {
        return automation.process(event, EventFacts.of(event, metadata));
    }

    private Incident activeIncident(IncidentStatus status) {
        Incident incident = Fixtures.withId(
                Incident.fromRule(offlineRule, offlineEvent(500), "DEVICE-108 is offline", "rule:1|device:8"), 77);
        if (status == IncidentStatus.INVESTIGATING) {
            incident.transitionTo(IncidentStatus.INVESTIGATING, NOW.minusSeconds(60), null);
        }
        return incident;
    }

    @Nested
    class Triggering {

        @Test
        void opensAnIncidentWhenARuleMatchesAndNoneIsActive() {
            when(incidentRepository.findActiveByFingerprint("rule:1|device:8")).thenReturn(Optional.empty());
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> Fixtures.withId(invocation.getArgument(0, Incident.class), 100));

            List<IncidentAction> actions = process(offlineEvent(1), Map.of());

            assertThat(actions).singleElement().satisfies(action -> {
                assertThat(action.type()).isEqualTo(IncidentAction.Type.CREATED);
                assertThat(action.title()).isEqualTo("DEVICE-108 is offline");
                assertThat(action.severity()).isEqualTo(IncidentSeverity.CRITICAL);
                assertThat(action.status()).isEqualTo(IncidentStatus.OPEN);
            });
            verify(fingerprintLock).acquire("rule:1|device:8");

            ArgumentCaptor<IncidentEvent> link = ArgumentCaptor.forClass(IncidentEvent.class);
            verify(linkRepository).save(link.capture());
            assertThat(link.getValue().getRelation()).isEqualTo(IncidentEventRelation.TRIGGER);

            ArgumentCaptor<IncidentHistory> history = ArgumentCaptor.forClass(IncidentHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getAction()).isEqualTo(HistoryAction.CREATED);
            assertThat(history.getValue().getActor()).isNull();

            ArgumentCaptor<IncidentChangedEvent> published = ArgumentCaptor.forClass(IncidentChangedEvent.class);
            verify(publisher).publishEvent(published.capture());
            assertThat(published.getValue().type()).isEqualTo(IncidentChangedEvent.ChangeType.CREATED);
            assertThat(published.getValue().incident().key()).isEqualTo("INC-100");
        }

        @Test
        void rendersMetricValuesIntoTheTitle() {
            when(incidentRepository.findActiveByFingerprint(anyString())).thenReturn(Optional.empty());
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> Fixtures.withId(invocation.getArgument(0, Incident.class), 101));
            Event cpu = Fixtures.deviceEvent(2, EventTypes.CPU_USAGE_HIGH, EventSeverity.HIGH, device108, "{}");

            List<IncidentAction> actions = process(cpu, Map.of("cpu", 94));

            assertThat(actions).singleElement()
                    .satisfies(action -> assertThat(action.title()).isEqualTo("High CPU usage on DEVICE-108 (94%)"));
        }

        @Test
        void doesNothingWhenNoRuleMatches() {
            Event cpu = Fixtures.deviceEvent(2, EventTypes.CPU_USAGE_HIGH, EventSeverity.LOW, device108, "{}");

            assertThat(process(cpu, Map.of("cpu", 85))).isEmpty();
            verify(incidentRepository, never()).save(any());
            verify(publisher, never()).publishEvent(any());
        }
    }

    @Nested
    class Deduplication {

        @Test
        void attachesRepeatedEventsToTheActiveIncidentInsteadOfOpeningAnother() {
            Incident existing = activeIncident(IncidentStatus.OPEN);
            when(incidentRepository.findActiveByFingerprint("rule:1|device:8")).thenReturn(Optional.of(existing));

            List<IncidentAction> first = process(offlineEvent(2), Map.of());
            List<IncidentAction> second = process(offlineEvent(3), Map.of());
            List<IncidentAction> third = process(offlineEvent(4), Map.of());

            assertThat(List.of(first, second, third)).allSatisfy(actions -> assertThat(actions).singleElement()
                    .satisfies(a -> {
                        assertThat(a.type()).isEqualTo(IncidentAction.Type.DEDUPLICATED);
                        assertThat(a.incidentId()).isEqualTo(77L);
                    }));
            assertThat(existing.getEventCount()).isEqualTo(4);
            verify(incidentRepository, never()).save(any());
            verify(historyRepository, never()).save(any());

            ArgumentCaptor<IncidentEvent> links = ArgumentCaptor.forClass(IncidentEvent.class);
            verify(linkRepository, org.mockito.Mockito.times(3)).save(links.capture());
            assertThat(links.getAllValues()).extracting(IncidentEvent::getRelation)
                    .containsOnly(IncidentEventRelation.OCCURRENCE);
        }

        @Test
        void separateSourcesGetSeparateIncidents() {
            Device device042 = Fixtures.device(42, "DEVICE-042", gateway);
            when(incidentRepository.findActiveByFingerprint(anyString())).thenReturn(Optional.empty());
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> Fixtures.withId(invocation.getArgument(0, Incident.class), 100));

            process(offlineEvent(1), Map.of());
            process(Fixtures.deviceEvent(2, EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL, device042, "{}"),
                    Map.of());

            verify(fingerprintLock).acquire("rule:1|device:8");
            verify(fingerprintLock).acquire("rule:1|device:42");
        }

        @Test
        void aRecurrenceAfterARecoverySignalClearsTheRecoveredFlag() {
            Incident existing = activeIncident(IncidentStatus.INVESTIGATING);
            existing.markRecovered(NOW.minusSeconds(30));
            when(incidentRepository.findActiveByFingerprint("rule:1|device:8")).thenReturn(Optional.of(existing));

            List<IncidentAction> actions = process(offlineEvent(5), Map.of());

            assertThat(actions).singleElement()
                    .satisfies(a -> assertThat(a.type()).isEqualTo(IncidentAction.Type.RECURRED));
            assertThat(existing.getRecoveredAt()).isNull();
            ArgumentCaptor<IncidentHistory> history = ArgumentCaptor.forClass(IncidentHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getAction()).isEqualTo(HistoryAction.RECURRED);
        }
    }

    @Nested
    class Recovery {

        @Test
        void autoResolvesAnOpenIncident() {
            Incident existing = activeIncident(IncidentStatus.OPEN);
            when(incidentRepository.findActiveByFingerprint("rule:1|device:8")).thenReturn(Optional.of(existing));

            List<IncidentAction> actions = process(recoveredEvent(9), Map.of());

            assertThat(actions).singleElement()
                    .satisfies(a -> assertThat(a.type()).isEqualTo(IncidentAction.Type.AUTO_RESOLVED));
            assertThat(existing.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
            assertThat(existing.getResolvedAt()).isEqualTo(NOW);
            assertThat(existing.getResolutionNote()).contains("DEVICE_RECOVERED");

            ArgumentCaptor<IncidentHistory> history = ArgumentCaptor.forClass(IncidentHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getAction()).isEqualTo(HistoryAction.AUTO_RESOLVED);
            ArgumentCaptor<IncidentChangedEvent> published = ArgumentCaptor.forClass(IncidentChangedEvent.class);
            verify(publisher).publishEvent(published.capture());
            assertThat(published.getValue().type()).isEqualTo(IncidentChangedEvent.ChangeType.RESOLVED);
        }

        @Test
        void leavesAnIncidentUnderInvestigationOpenButFlagsTheRecovery() {
            Incident existing = activeIncident(IncidentStatus.INVESTIGATING);
            when(incidentRepository.findActiveByFingerprint("rule:1|device:8")).thenReturn(Optional.of(existing));

            List<IncidentAction> actions = process(recoveredEvent(9), Map.of());

            assertThat(actions).singleElement()
                    .satisfies(a -> assertThat(a.type()).isEqualTo(IncidentAction.Type.RECOVERY_DETECTED));
            assertThat(existing.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
            assertThat(existing.getRecoveredAt()).isEqualTo(NOW);
            ArgumentCaptor<IncidentHistory> history = ArgumentCaptor.forClass(IncidentHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getAction()).isEqualTo(HistoryAction.RECOVERY_DETECTED);
            assertThat(history.getValue().getMessage()).contains("already being investigated");
        }

        @Test
        void neverAutoResolvesWhenTheRuleRequiresConfirmation() {
            AlertRule outage = Fixtures.outageRule(5);
            when(ruleRepository.findCandidatesFor(anyString())).thenReturn(List.of(outage));
            Event down = Fixtures.serviceEvent(1, EventTypes.SERVICE_DOWN, EventSeverity.CRITICAL, gateway);
            Incident existing = Fixtures.withId(Incident.fromRule(outage, down, "Device Gateway is down",
                    "rule:5|service:3"), 88);
            when(incidentRepository.findActiveByFingerprint("rule:5|service:3")).thenReturn(Optional.of(existing));

            Event recovered = Fixtures.serviceEvent(2, EventTypes.SERVICE_RECOVERED, EventSeverity.INFO, gateway);
            List<IncidentAction> actions = automation.process(recovered, EventFacts.of(recovered, Map.of()));

            assertThat(actions).singleElement()
                    .satisfies(a -> assertThat(a.type()).isEqualTo(IncidentAction.Type.RECOVERY_DETECTED));
            assertThat(existing.getStatus()).isEqualTo(IncidentStatus.OPEN);
            assertThat(existing.getRecoveredAt()).isEqualTo(NOW);
        }

        @Test
        void aRecoverySignalNeverReopensTheProblemItJustCleared() {
            // A severity rule matches any HIGH event, including a HIGH-severity recovery signal.
            AlertRule severityRule = Fixtures.withId(
                    AlertRule.onSeverity("High or worse", EventSeverity.HIGH, IncidentSeverity.HIGH)
                            .recoveredBy("ALERT_CLEARED"), 9);
            when(ruleRepository.findCandidatesFor(anyString())).thenReturn(List.of(severityRule));
            Incident existing = Fixtures.withId(
                    Incident.fromRule(severityRule, offlineEvent(500), "High event", "rule:9|device:8"), 90);
            when(incidentRepository.findActiveByFingerprint("rule:9|device:8")).thenReturn(Optional.of(existing));
            Event cleared = Fixtures.deviceEvent(7, "ALERT_CLEARED", EventSeverity.HIGH, device108, "{}");

            List<IncidentAction> actions = automation.process(cleared, EventFacts.of(cleared, Map.of()));

            assertThat(actions).singleElement()
                    .satisfies(a -> assertThat(a.type()).isEqualTo(IncidentAction.Type.AUTO_RESOLVED));
            verify(incidentRepository, never()).save(any());
        }

        @Test
        void isANoOpWhenThereIsNothingToRecover() {
            when(incidentRepository.findActiveByFingerprint("rule:1|device:8")).thenReturn(Optional.empty());

            assertThat(process(recoveredEvent(9), Map.of())).isEmpty();
            verify(linkRepository, never()).save(any());
            verify(publisher, never()).publishEvent(any());
        }
    }
}
