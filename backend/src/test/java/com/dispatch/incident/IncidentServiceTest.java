package com.dispatch.incident;

import static com.dispatch.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import com.dispatch.common.error.BusinessRuleException;
import com.dispatch.common.error.ConflictException;
import com.dispatch.device.DeviceRepository;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.EventTypes;
import com.dispatch.health.HealthService;
import com.dispatch.incident.dto.IncidentDetail;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.support.Fixtures;
import com.dispatch.user.Role;
import com.dispatch.user.User;
import com.dispatch.user.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    IncidentRepository incidentRepository;
    @Mock
    IncidentCommentRepository commentRepository;
    @Mock
    MonitoredServiceRepository serviceRepository;
    @Mock
    DeviceRepository deviceRepository;
    @Mock
    UserService userService;
    @Mock
    IncidentHistoryRepository historyRepository;
    @Mock
    FingerprintLock fingerprintLock;
    @Mock
    HealthService healthService;
    @Mock
    ApplicationEventPublisher publisher;

    private IncidentService service;
    private Incident incident;
    private final User aravind = Fixtures.user(2, "Aravind B", Role.OPERATOR);
    private final User priya = Fixtures.user(3, "Priya Raman", Role.OPERATOR);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new IncidentService(incidentRepository, commentRepository, serviceRepository, deviceRepository,
                userService, new IncidentHistoryRecorder(historyRepository, clock), fingerprintLock, healthService,
                new IncidentMapper(), publisher, clock);

        var gateway = Fixtures.service(3, "Device Gateway");
        var device = Fixtures.device(8, "DEVICE-108", gateway);
        var trigger = Fixtures.deviceEvent(1, EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL, device, "{}");
        incident = Fixtures.withId(Incident.fromRule(Fixtures.offlineRule(1), trigger, "DEVICE-108 is offline",
                "rule:1|device:8"), 41);
        when(incidentRepository.findDetailedById(41L)).thenReturn(Optional.of(incident));
        when(userService.require(2L)).thenReturn(aravind);
    }

    private IncidentHistory lastHistory() {
        ArgumentCaptor<IncidentHistory> captor = ArgumentCaptor.forClass(IncidentHistory.class);
        verify(historyRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void acknowledgingMovesTheIncidentToInvestigatingAndAuditsTheChange() {
        IncidentDetail detail = service.changeStatus(41L, IncidentStatus.INVESTIGATING, null, 2L);

        assertThat(detail.status()).isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(detail.acknowledgedAt()).isEqualTo(NOW);
        IncidentHistory history = lastHistory();
        assertThat(history.getAction()).isEqualTo(HistoryAction.STATUS_CHANGED);
        assertThat(history.getFromValue()).isEqualTo("OPEN");
        assertThat(history.getToValue()).isEqualTo("INVESTIGATING");
        assertThat(history.getActor()).isEqualTo(aravind);
        verify(healthService).reconcile(incident.getService(), incident.getDevice());
        verify(publisher).publishEvent(any(IncidentChangedEvent.class));
    }

    @Test
    void resolvingRecordsTheNoteInTheTimeline() {
        service.changeStatus(41L, IncidentStatus.RESOLVED, "Replaced faulty SFP module", 2L);

        assertThat(incident.getResolutionNote()).isEqualTo("Replaced faulty SFP module");
        assertThat(lastHistory().getMessage())
                .isEqualTo("Status changed from OPEN to RESOLVED: Replaced faulty SFP module");
        ArgumentCaptor<IncidentChangedEvent> published = ArgumentCaptor.forClass(IncidentChangedEvent.class);
        verify(publisher).publishEvent(published.capture());
        assertThat(published.getValue().type()).isEqualTo(IncidentChangedEvent.ChangeType.RESOLVED);
    }

    @Test
    void reopeningIsRefusedWhenAnotherIncidentAlreadyTracksTheSameProblem() {
        incident.transitionTo(IncidentStatus.RESOLVED, NOW, null);
        Incident newer = Fixtures.withId(Incident.fromRule(Fixtures.offlineRule(1), incident.getSourceEvent(),
                "DEVICE-108 is offline", "rule:1|device:8"), 57);
        when(incidentRepository.findActiveByFingerprint("rule:1|device:8")).thenReturn(Optional.of(newer));

        assertThatThrownBy(() -> service.changeStatus(41L, IncidentStatus.OPEN, null, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("INC-57");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        verify(fingerprintLock).acquire("rule:1|device:8");
    }

    @Test
    void reopeningIsRecordedAsReopened() {
        incident.transitionTo(IncidentStatus.RESOLVED, NOW, null);
        when(incidentRepository.findActiveByFingerprint("rule:1|device:8")).thenReturn(Optional.empty());

        service.changeStatus(41L, IncidentStatus.OPEN, null, 2L);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(lastHistory().getAction()).isEqualTo(HistoryAction.REOPENED);
    }

    @Test
    void invalidTransitionsAreRejectedWithoutSideEffects() {
        incident.transitionTo(IncidentStatus.RESOLVED, NOW, null);

        assertThatThrownBy(() -> service.changeStatus(41L, IncidentStatus.INVESTIGATING, null, 2L))
                .isInstanceOf(BusinessRuleException.class);
        verify(historyRepository, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void assignmentIsAuditedAndRepeatingItIsANoOp() {
        when(userService.require(3L)).thenReturn(priya);

        service.assign(41L, 3L, 2L);
        assertThat(incident.getAssignee()).isEqualTo(priya);
        assertThat(lastHistory().getMessage()).isEqualTo("Assigned to Priya Raman");

        service.assign(41L, 3L, 2L);
        verify(historyRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void deactivatedUsersCannotBeAssigned() {
        priya.setActive(false);
        when(userService.require(3L)).thenReturn(priya);

        assertThatThrownBy(() -> service.assign(41L, 3L, 2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "ASSIGNEE_INACTIVE");
    }

    @Test
    void commentsAreStoredAndNoted() {
        when(commentRepository.save(any(IncidentComment.class)))
                .thenAnswer(invocation -> Fixtures.withId(invocation.getArgument(0, IncidentComment.class), 9));

        var comment = service.addComment(41L, "  Checked switch port 14.  ", 2L);

        assertThat(comment.body()).isEqualTo("Checked switch port 14.");
        assertThat(comment.author().name()).isEqualTo("Aravind B");
        assertThat(lastHistory().getAction()).isEqualTo(HistoryAction.COMMENT_ADDED);
    }
}
