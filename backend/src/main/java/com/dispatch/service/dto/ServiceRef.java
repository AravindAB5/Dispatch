package com.dispatch.service.dto;

import com.dispatch.service.MonitoredService;
import com.dispatch.service.ServiceStatus;

/** Compact service reference embedded in other resources. */
public record ServiceRef(Long id, String name, ServiceStatus status) {

    public static ServiceRef from(MonitoredService service) {
        return service == null ? null : new ServiceRef(service.getId(), service.getName(), service.getStatus());
    }
}
