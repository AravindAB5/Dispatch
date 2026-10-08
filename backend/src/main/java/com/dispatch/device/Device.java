package com.dispatch.device;

import java.time.Instant;

import com.dispatch.common.persistence.AuditedEntity;
import com.dispatch.service.MonitoredService;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code @DynamicUpdate} writes only changed columns, so recording "last seen" never rewrites the status
 * column with a value read earlier.
 */
@Entity
@Table(name = "devices")
@DynamicUpdate
public class Device extends AuditedEntity {

    /** External identifier used by event producers, e.g. {@code DEVICE-042}. */
    @Column(nullable = false, length = 64)
    private String identifier;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "device_type", nullable = false, length = 64)
    private String deviceType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService service;

    @Column(length = 120)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceStatus status = DeviceStatus.ONLINE;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected Device() {
    }

    public Device(String identifier, String name, String deviceType, MonitoredService service, String location) {
        this.identifier = normalizeIdentifier(identifier);
        this.name = name;
        this.deviceType = deviceType;
        this.service = service;
        this.location = location;
    }

    public static String normalizeIdentifier(String identifier) {
        return identifier == null ? null : identifier.trim().toUpperCase();
    }

    /** @return true when the status actually changed */
    public boolean changeStatus(DeviceStatus newStatus) {
        if (status == newStatus) {
            return false;
        }
        status = newStatus;
        return true;
    }

    public void recordSeen(Instant at) {
        if (lastSeenAt == null || at.isAfter(lastSeenAt)) {
            lastSeenAt = at;
        }
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public MonitoredService getService() {
        return service;
    }

    public void setService(MonitoredService service) {
        this.service = service;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
