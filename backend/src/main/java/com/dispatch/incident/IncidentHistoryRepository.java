package com.dispatch.incident;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IncidentHistoryRepository extends JpaRepository<IncidentHistory, Long> {

    @Query("""
            select h from IncidentHistory h left join fetch h.actor
            where h.incident.id = :incidentId
            order by h.createdAt asc, h.id asc
            """)
    List<IncidentHistory> findForIncident(Long incidentId);
}
