package com.dispatch.notification;

import java.time.Clock;

import com.dispatch.device.DeviceStatusChangedEvent;
import com.dispatch.event.EventRecordedEvent;
import com.dispatch.incident.IncidentChangedEvent;
import com.dispatch.service.ServiceStatusChangedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges domain events to WebSocket subscribers.
 * <p>
 * Listeners run <b>after the transaction commits</b> ({@link TransactionalEventListener}), so a client is never
 * told about an incident that was rolled back, and any follow-up REST request it makes will see the data.
 * Delivery failures are logged and never affect the business operation that triggered them.
 */
@Component
public class RealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);

    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public RealtimeNotifier(SimpMessagingTemplate messaging, Clock clock) {
        this.messaging = messaging;
        this.clock = clock;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onEventRecorded(EventRecordedEvent event) {
        send(RealtimeTopics.EVENTS, "event.created", event.event());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onIncidentChanged(IncidentChangedEvent event) {
        send(RealtimeTopics.INCIDENTS, event.type().messageType(), event.incident());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onServiceStatusChanged(ServiceStatusChangedEvent event) {
        send(RealtimeTopics.SERVICES, "service.updated", event);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onDeviceStatusChanged(DeviceStatusChangedEvent event) {
        send(RealtimeTopics.DEVICES, "device.updated", event);
    }

    private void send(String destination, String type, Object data) {
        try {
            messaging.convertAndSend(destination, new RealtimeMessage(type, data, clock.instant()));
        } catch (MessagingException e) {
            log.warn("Failed to broadcast {} to {}: {}", type, destination, e.getMessage());
        }
    }
}
