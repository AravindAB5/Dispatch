package com.dispatch.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.dispatch.support.IntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Real STOMP client against the running server: authentication, subscription and after-commit delivery. */
class WebSocketIntegrationTest extends IntegrationTest {

    @Value("${local.server.port}")
    int port;

    private WebSocketStompClient client;

    @BeforeEach
    void createClient() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void stopClient() {
        client.stop();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((Map<String, Object>) payload);
            }
        });
        return messages;
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscribersReceiveIncidentsAndEventsAsTheyHappen() throws Exception {
        StompSession session = connect(operatorToken());
        BlockingQueue<Map<String, Object>> incidents = subscribe(session, "/topic/incidents");
        BlockingQueue<Map<String, Object>> events = subscribe(session, "/topic/events");
        BlockingQueue<Map<String, Object>> services = subscribe(session, "/topic/services");
        Thread.sleep(300); // let the SUBSCRIBE frames reach the broker

        ingest(deviceEvent("DEVICE_OFFLINE", "CRITICAL", "DEVICE-108"));

        Map<String, Object> incident = incidents.poll(10, TimeUnit.SECONDS);
        assertThat(incident).isNotNull();
        assertThat(incident.get("type")).isEqualTo("incident.created");
        assertThat((Map<String, Object>) incident.get("data"))
                .containsEntry("title", "DEVICE-108 is offline")
                .containsEntry("severity", "CRITICAL")
                .containsEntry("status", "OPEN");

        Map<String, Object> event = events.poll(10, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("event.created");
        assertThat((Map<String, Object>) event.get("data")).containsEntry("eventType", "DEVICE_OFFLINE");

        Map<String, Object> service = services.poll(10, TimeUnit.SECONDS);
        assertThat(service).isNotNull();
        assertThat(service.get("type")).isEqualTo("service.updated");
        assertThat((Map<String, Object>) service.get("data")).containsEntry("status", "DEGRADED");

        ingest(deviceEvent("DEVICE_RECOVERED", "INFO", "DEVICE-108"));
        Map<String, Object> resolved = incidents.poll(10, TimeUnit.SECONDS);
        assertThat(resolved).isNotNull();
        assertThat(resolved.get("type")).isEqualTo("incident.resolved");
    }

    @Test
    void connectionsWithoutAValidTokenAreRefused() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect("not-a-jwt")).isInstanceOf(ExecutionException.class);
    }
}
