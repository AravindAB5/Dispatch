package com.dispatch.incident;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    /** List queries fetch the to-one associations they render in a single SQL statement (no N+1). */
    @Override
    @EntityGraph(attributePaths = {"service", "device", "assignee"})
    Page<Incident> findAll(Specification<Incident> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"service", "device", "assignee", "rule", "createdBy"})
    Optional<Incident> findDetailedById(Long id);

    Optional<Incident> findFirstByFingerprintAndStatusIn(String fingerprint, Collection<IncidentStatus> statuses);

    /** The single unresolved incident for a fingerprint, if any (enforced by a partial unique index). */
    default Optional<Incident> findActiveByFingerprint(String fingerprint) {
        return findFirstByFingerprintAndStatusIn(fingerprint, IncidentStatus.ACTIVE);
    }

    // ---- health derivation -----------------------------------------------------------------

    /** (severity, deviceId) of unresolved incidents still impacting the service (recovered ones excluded). */
    @Query("""
            select i.severity, d.id from Incident i left join i.device d
            where i.service.id = :serviceId and i.status in :statuses and i.recoveredAt is null
            """)
    List<Object[]> findImpactingSignalsForServiceRaw(Long serviceId, Collection<IncidentStatus> statuses);

    @Query("""
            select i.severity from Incident i
            where i.device.id = :deviceId and i.status in :statuses and i.recoveredAt is null
            """)
    List<IncidentSeverity> findImpactingSeveritiesForDevice(Long deviceId, Collection<IncidentStatus> statuses);

    // ---- aggregates ------------------------------------------------------------------------

    @Query("""
            select i.service.id, i.severity, count(i) from Incident i
            where i.status in :statuses and i.service is not null
            group by i.service.id, i.severity
            """)
    List<Object[]> countByServiceAndSeverityRaw(Collection<IncidentStatus> statuses);

    @Query("""
            select i.device.id, count(i) from Incident i
            where i.status in :statuses and i.device.id in :deviceIds
            group by i.device.id
            """)
    List<Object[]> countByDeviceRaw(Collection<Long> deviceIds, Collection<IncidentStatus> statuses);

    @Query("""
            select i.rule.id, i.status, count(i) from Incident i
            where i.rule is not null
            group by i.rule.id, i.status
            """)
    List<Object[]> countByRuleAndStatusRaw();

    @Query("select i.severity, count(i) from Incident i where i.status in :statuses group by i.severity")
    List<Object[]> countBySeverityRaw(Collection<IncidentStatus> statuses);

    @Query("select i.status, count(i) from Incident i group by i.status")
    List<Object[]> countByStatusRaw();

    long countByStatusInAndAssigneeIsNull(Collection<IncidentStatus> statuses);

    long countByCreatedAtGreaterThanEqual(Instant since);

    long countByResolvedAtGreaterThanEqual(Instant since);
}
