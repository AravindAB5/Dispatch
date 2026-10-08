package com.dispatch.incident;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, Long> {

    @Query("""
            select c from IncidentComment c join fetch c.author
            where c.incident.id = :incidentId
            order by c.createdAt asc, c.id asc
            """)
    List<IncidentComment> findForIncident(Long incidentId);
}
