package com.dispatch.user;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.dispatch.incident.IncidentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findAllByOrderByNameAsc();

    long countByRoleAndActiveTrue(Role role);

    @Query("""
            select i.assignee.id, count(i) from Incident i
            where i.assignee is not null and i.status in :statuses
            group by i.assignee.id
            """)
    List<Object[]> countAssignedIncidentsRaw(java.util.Collection<IncidentStatus> statuses);

    default Map<Long, Long> countActiveAssignedIncidents() {
        return countAssignedIncidentsRaw(IncidentStatus.ACTIVE).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
