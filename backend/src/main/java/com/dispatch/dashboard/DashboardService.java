package com.dispatch.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.dispatch.dashboard.dto.DashboardSummary;
import com.dispatch.dashboard.dto.DashboardSummary.ActiveIncidents;
import com.dispatch.dashboard.dto.DashboardSummary.HealthCounts;
import com.dispatch.dashboard.dto.DashboardSummary.ResponseTimes;
import com.dispatch.dashboard.dto.DashboardSummary.Throughput;
import com.dispatch.dashboard.dto.DashboardSummary.Volume;
import com.dispatch.dashboard.dto.TrendPoint;
import com.dispatch.dashboard.dto.VolumePoint;
import com.dispatch.device.DeviceRepository;
import com.dispatch.device.DeviceStatus;
import com.dispatch.event.EventRepository;
import com.dispatch.incident.IncidentRepository;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.service.MonitoredServiceRepository;
import com.dispatch.service.ServiceStatus;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational aggregates. Simple counts use Spring Data; time-bucketed reports use SQL directly
 * (generate_series + FILTER), which is far clearer than the equivalent JPQL.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int RESPONSE_TIME_WINDOW_DAYS = 7;

    private final IncidentRepository incidentRepository;
    private final EventRepository eventRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final DeviceRepository deviceRepository;
    private final JdbcClient jdbc;
    private final Clock clock;

    public DashboardService(IncidentRepository incidentRepository, EventRepository eventRepository,
            MonitoredServiceRepository serviceRepository, DeviceRepository deviceRepository, JdbcClient jdbc,
            Clock clock) {
        this.incidentRepository = incidentRepository;
        this.eventRepository = eventRepository;
        this.serviceRepository = serviceRepository;
        this.deviceRepository = deviceRepository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public DashboardSummary summary() {
        Instant now = clock.instant();

        Map<IncidentSeverity, Long> bySeverity = new EnumMap<>(IncidentSeverity.class);
        for (Object[] row : incidentRepository.countBySeverityRaw(IncidentStatus.ACTIVE)) {
            bySeverity.put((IncidentSeverity) row[0], (Long) row[1]);
        }
        Map<IncidentStatus, Long> byStatus = new EnumMap<>(IncidentStatus.class);
        for (Object[] row : incidentRepository.countByStatusRaw()) {
            byStatus.put((IncidentStatus) row[0], (Long) row[1]);
        }
        long open = byStatus.getOrDefault(IncidentStatus.OPEN, 0L);
        long investigating = byStatus.getOrDefault(IncidentStatus.INVESTIGATING, 0L);
        ActiveIncidents active = new ActiveIncidents(open + investigating,
                bySeverity.getOrDefault(IncidentSeverity.CRITICAL, 0L),
                bySeverity.getOrDefault(IncidentSeverity.HIGH, 0L),
                bySeverity.getOrDefault(IncidentSeverity.MEDIUM, 0L),
                bySeverity.getOrDefault(IncidentSeverity.LOW, 0L),
                open, investigating,
                incidentRepository.countByStatusInAndAssigneeIsNull(IncidentStatus.ACTIVE));

        Instant dayAgo = now.minus(Duration.ofHours(24));
        Throughput throughput = new Throughput(incidentRepository.countByCreatedAtGreaterThanEqual(dayAgo),
                incidentRepository.countByResolvedAtGreaterThanEqual(dayAgo));

        Volume volume = new Volume(eventRepository.countByOccurredAtGreaterThanEqual(now.minus(Duration.ofHours(1))),
                eventRepository.countByOccurredAtGreaterThanEqual(dayAgo));

        return new DashboardSummary(active, throughput, responseTimes(now), volume, serviceHealth(), deviceHealth(),
                now);
    }

    /** Hourly buckets aligned to the caller's time zone so chart labels fall on whole local hours. */
    public List<TrendPoint> incidentTrend(int hours, ZoneId zone) {
        Instant now = clock.instant();
        return jdbc.sql("""
                        WITH buckets AS (
                            SELECT generate_series(
                                date_trunc('hour', :now, :tz) - make_interval(hours => :hours - 1),
                                date_trunc('hour', :now, :tz),
                                interval '1 hour') AS bucket_start
                        )
                        SELECT b.bucket_start,
                               count(i.id) FILTER (WHERE i.severity = 'CRITICAL') AS critical,
                               count(i.id) FILTER (WHERE i.severity = 'HIGH')     AS high,
                               count(i.id) FILTER (WHERE i.severity = 'MEDIUM')   AS medium,
                               count(i.id) FILTER (WHERE i.severity = 'LOW')      AS low,
                               (SELECT count(*) FROM incidents r
                                 WHERE r.resolved_at >= b.bucket_start
                                   AND r.resolved_at < b.bucket_start + interval '1 hour') AS resolved
                        FROM buckets b
                        LEFT JOIN incidents i
                               ON i.created_at >= b.bucket_start
                              AND i.created_at < b.bucket_start + interval '1 hour'
                        GROUP BY b.bucket_start
                        ORDER BY b.bucket_start
                        """)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("tz", zone.getId())
                .param("hours", hours)
                .query((rs, rowNum) -> new TrendPoint(
                        rs.getObject("bucket_start", OffsetDateTime.class).toInstant(),
                        rs.getLong("critical"), rs.getLong("high"), rs.getLong("medium"), rs.getLong("low"),
                        rs.getLong("resolved")))
                .list();
    }

    /** Events per minute; "problems" counts events of MEDIUM severity or above. */
    public List<VolumePoint> eventVolume(int minutes) {
        Instant now = clock.instant();
        return jdbc.sql("""
                        WITH buckets AS (
                            SELECT generate_series(
                                date_trunc('minute', :now) - make_interval(mins => :minutes - 1),
                                date_trunc('minute', :now),
                                interval '1 minute') AS bucket_start
                        )
                        SELECT b.bucket_start,
                               count(e.id) AS total,
                               count(e.id) FILTER (WHERE e.severity IN ('MEDIUM', 'HIGH', 'CRITICAL')) AS problems
                        FROM buckets b
                        LEFT JOIN events e
                               ON e.occurred_at >= b.bucket_start
                              AND e.occurred_at < b.bucket_start + interval '1 minute'
                        GROUP BY b.bucket_start
                        ORDER BY b.bucket_start
                        """)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("minutes", minutes)
                .query((rs, rowNum) -> new VolumePoint(
                        rs.getObject("bucket_start", OffsetDateTime.class).toInstant(),
                        rs.getLong("total"), rs.getLong("problems")))
                .list();
    }

    private ResponseTimes responseTimes(Instant now) {
        OffsetDateTime since = OffsetDateTime.ofInstant(now.minus(Duration.ofDays(RESPONSE_TIME_WINDOW_DAYS)),
                ZoneOffset.UTC);
        Double mtta = jdbc.sql("""
                        SELECT avg(extract(epoch FROM acknowledged_at - created_at))
                        FROM incidents WHERE acknowledged_at >= :since
                        """)
                .param("since", since).query(Double.class).optional().orElse(null);
        Double mttr = jdbc.sql("""
                        SELECT avg(extract(epoch FROM resolved_at - created_at))
                        FROM incidents WHERE resolved_at >= :since
                        """)
                .param("since", since).query(Double.class).optional().orElse(null);
        return new ResponseTimes(mtta == null ? null : Math.round(mtta), mttr == null ? null : Math.round(mttr),
                RESPONSE_TIME_WINDOW_DAYS);
    }

    private HealthCounts serviceHealth() {
        Map<ServiceStatus, Long> counts = new EnumMap<>(ServiceStatus.class);
        for (Object[] row : serviceRepository.countByStatusRaw()) {
            counts.put((ServiceStatus) row[0], (Long) row[1]);
        }
        long healthy = counts.getOrDefault(ServiceStatus.HEALTHY, 0L);
        long degraded = counts.getOrDefault(ServiceStatus.DEGRADED, 0L);
        long down = counts.getOrDefault(ServiceStatus.DOWN, 0L);
        return new HealthCounts(healthy + degraded + down, healthy, degraded, down);
    }

    private HealthCounts deviceHealth() {
        Map<DeviceStatus, Long> counts = new EnumMap<>(DeviceStatus.class);
        for (Object[] row : deviceRepository.countByStatusRaw()) {
            counts.put((DeviceStatus) row[0], (Long) row[1]);
        }
        long online = counts.getOrDefault(DeviceStatus.ONLINE, 0L);
        long degraded = counts.getOrDefault(DeviceStatus.DEGRADED, 0L);
        long offline = counts.getOrDefault(DeviceStatus.OFFLINE, 0L);
        return new HealthCounts(online + degraded + offline, online, degraded, offline);
    }
}
