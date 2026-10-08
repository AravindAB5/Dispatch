package com.dispatch.notification;

/** STOMP destinations clients can subscribe to. */
public final class RealtimeTopics {

    public static final String EVENTS = "/topic/events";
    public static final String INCIDENTS = "/topic/incidents";
    public static final String SERVICES = "/topic/services";
    public static final String DEVICES = "/topic/devices";

    private RealtimeTopics() {
    }
}
