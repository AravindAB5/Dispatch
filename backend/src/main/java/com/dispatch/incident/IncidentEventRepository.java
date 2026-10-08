package com.dispatch.incident;

import java.util.Collection;
import java.util.List;

import com.dispatch.incident.dto.EventIncidentLinkRow;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, Long> {

    @Query("""
            select ie from IncidentEvent ie
              join fetch ie.event e
              left join fetch e.service
              left join fetch e.device
            where ie.incident.id = :incidentId
            order by e.occurredAt desc, e.id desc
            """)
    List<IncidentEvent> findForIncident(Long incidentId, Pageable pageable);

    long countByIncidentId(Long incidentId);

    @Query("""
            select ie.relation, count(ie) from IncidentEvent ie
            where ie.incident.id = :incidentId group by ie.relation
            """)
    List<Object[]> countByRelationRaw(Long incidentId);

    /** Incidents linked to a page of events, fetched in one query for the event stream. */
    @Query("""
            select new com.dispatch.incident.dto.EventIncidentLinkRow(
                ie.event.id, i.id, i.title, i.severity, i.status, ie.relation)
            from IncidentEvent ie join ie.incident i
            where ie.event.id in :eventIds
            """)
    List<EventIncidentLinkRow> findLinksForEvents(Collection<Long> eventIds);
}
