package com.dispatch.service;

import java.time.Instant;

import com.dispatch.common.persistence.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A service being monitored (e.g. "Device Gateway"). Named MonitoredService to avoid confusion with
 * Spring's {@code @Service} stereotype. Its {@link ServiceStatus} is derived from active incidents.
 */
@Entity
@Table(name = "services")
public class MonitoredService extends AuditedEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Environment environment;

    @Column(name = "owner_team", length = 120)
    private String ownerTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ServiceStatus status = ServiceStatus.HEALTHY;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    protected MonitoredService() {
    }

    public MonitoredService(String name, String description, Environment environment, String ownerTeam) {
        this.name = name;
        this.description = description;
        this.environment = environment;
        this.ownerTeam = ownerTeam;
        this.statusChangedAt = Instant.now();
    }

    /** Stable machine name used as the {@code sourceId} of service-level events, e.g. "content-delivery-api". */
    public String slug() {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** @return true when the status actually changed */
    public boolean changeStatus(ServiceStatus newStatus, Instant at) {
        if (this.status == newStatus) {
            return false;
        }
        this.status = newStatus;
        this.statusChangedAt = at;
        return true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public void setOwnerTeam(String ownerTeam) {
        this.ownerTeam = ownerTeam;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(Instant statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }
}
