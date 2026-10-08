package com.dispatch.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = {"service", "device"})
    Optional<Event> findWithRefsById(Long id);

    @Query("select distinct e.eventType from Event e order by e.eventType")
    List<String> findDistinctEventTypes();

    long countByOccurredAtGreaterThanEqual(Instant since);
}
