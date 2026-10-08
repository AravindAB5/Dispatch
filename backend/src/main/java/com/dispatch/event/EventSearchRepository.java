package com.dispatch.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.stereotype.Repository;

/**
 * Keyset ("seek") pagination over the event stream. Unlike OFFSET pagination, the cost of fetching the
 * next page does not grow with how deep the client has scrolled, and no COUNT(*) over a large,
 * constantly growing table is needed. Backed by the (occurred_at DESC, id DESC) index.
 */
@Repository
public class EventSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<Event> search(EventFilter filter, EventCursor after, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> root = query.from(Event.class);
        root.fetch("service", JoinType.LEFT);
        root.fetch("device", JoinType.LEFT);

        Path<Instant> occurredAt = root.get("occurredAt");
        Path<Long> id = root.get("id");
        List<Predicate> predicates = new ArrayList<>();

        if (filter.eventTypes() != null && !filter.eventTypes().isEmpty()) {
            predicates.add(root.get("eventType").in(filter.eventTypes()));
        }
        if (filter.severities() != null && !filter.severities().isEmpty()) {
            predicates.add(root.get("severity").in(filter.severities()));
        }
        if (filter.serviceId() != null) {
            predicates.add(cb.equal(root.get("service").get("id"), filter.serviceId()));
        }
        if (filter.deviceId() != null) {
            predicates.add(cb.equal(root.get("device").get("id"), filter.deviceId()));
        }
        if (filter.sourceType() != null) {
            predicates.add(cb.equal(root.get("sourceType"), filter.sourceType()));
        }
        if (filter.query() != null && !filter.query().isBlank()) {
            String pattern = "%" + escapeLike(filter.query().trim().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("sourceId")), pattern, '\\'),
                    cb.like(cb.lower(root.get("message")), pattern, '\\'),
                    cb.like(cb.lower(root.get("eventType")), pattern, '\\')));
        }
        if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(occurredAt, filter.from()));
        }
        if (filter.to() != null) {
            predicates.add(cb.lessThan(occurredAt, filter.to()));
        }
        if (after != null) {
            predicates.add(cb.or(
                    cb.lessThan(occurredAt, after.occurredAt()),
                    cb.and(cb.equal(occurredAt, after.occurredAt()), cb.lessThan(id, after.id()))));
        }

        query.select(root)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.desc(occurredAt), cb.desc(id));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
