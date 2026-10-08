package com.dispatch.incident;

import static com.dispatch.support.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dispatch.common.error.BusinessRuleException;
import com.dispatch.event.EventSeverity;
import com.dispatch.event.EventTypes;
import com.dispatch.support.Fixtures;
import com.dispatch.user.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Lifecycle rules enforced by the Incident aggregate itself. */
class IncidentTest {

    private Incident newIncident() {
        var gateway = Fixtures.service(3, "Device Gateway");
        var device = Fixtures.device(8, "DEVICE-108", gateway);
        var trigger = Fixtures.deviceEvent(1, EventTypes.DEVICE_OFFLINE, EventSeverity.CRITICAL, device, "{}");
        return Fixtures.withId(Incident.fromRule(Fixtures.offlineRule(1), trigger, "DEVICE-108 is offline",
                "rule:1|device:8"), 41);
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "OPEN, INVESTIGATING, true",
            "OPEN, RESOLVED, true",
            "INVESTIGATING, RESOLVED, true",
            "INVESTIGATING, OPEN, true",
            "RESOLVED, OPEN, true",
            "RESOLVED, INVESTIGATING, false",
            "OPEN, OPEN, false"})
    void allowsOnlyValidStatusTransitions(IncidentStatus from, IncidentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void acknowledgingRecordsTheFirstAcknowledgementOnly() {
        Incident incident = newIncident();
        incident.transitionTo(IncidentStatus.INVESTIGATING, NOW, null);
        incident.transitionTo(IncidentStatus.OPEN, NOW.plusSeconds(60), null);
        incident.transitionTo(IncidentStatus.INVESTIGATING, NOW.plusSeconds(120), null);

        assertThat(incident.getAcknowledgedAt()).isEqualTo(NOW);
    }

    @Test
    void resolvingStoresTheTimeAndNote_reopeningClearsThem() {
        Incident incident = newIncident();
        incident.markRecovered(NOW);
        incident.transitionTo(IncidentStatus.RESOLVED, NOW, "  Replaced SFP module ");

        assertThat(incident.getResolvedAt()).isEqualTo(NOW);
        assertThat(incident.getResolutionNote()).isEqualTo("Replaced SFP module");

        incident.transitionTo(IncidentStatus.OPEN, NOW.plusSeconds(10), null);
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getResolutionNote()).isNull();
        assertThat(incident.getRecoveredAt()).isNull();
    }

    @Test
    void rejectsInvalidTransitionsWithAStableErrorCode() {
        Incident incident = newIncident();
        incident.transitionTo(IncidentStatus.RESOLVED, NOW, null);

        assertThatThrownBy(() -> incident.transitionTo(IncidentStatus.INVESTIGATING, NOW, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATUS_TRANSITION");
        assertThatThrownBy(() -> incident.transitionTo(IncidentStatus.RESOLVED, NOW, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "STATUS_UNCHANGED");
    }

    @Test
    void resolvedIncidentsCannotBeReassignedOrReprioritised() {
        Incident incident = newIncident();
        incident.transitionTo(IncidentStatus.RESOLVED, NOW, null);

        assertThatThrownBy(() -> incident.assignTo(Fixtures.user(2, "Aravind B", Role.OPERATOR)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "INCIDENT_RESOLVED");
        assertThatThrownBy(() -> incident.changeSeverity(IncidentSeverity.LOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void occurrencesIncrementTheCountAndKeepTheLatestTimestamp() {
        Incident incident = newIncident();
        incident.recordOccurrence(NOW.plusSeconds(30));
        incident.recordOccurrence(NOW.plusSeconds(10)); // late-arriving event

        assertThat(incident.getEventCount()).isEqualTo(3);
        assertThat(incident.getLastEventAt()).isEqualTo(NOW.plusSeconds(30));
    }
}
