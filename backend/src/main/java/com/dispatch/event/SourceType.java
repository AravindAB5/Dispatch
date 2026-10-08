package com.dispatch.event;

/**
 * DEVICE events reference a registered device by identifier, SERVICE events a service by id,
 * EXTERNAL events come from integrations that are not modelled in Dispatch.
 */
public enum SourceType {
    DEVICE,
    SERVICE,
    EXTERNAL
}
