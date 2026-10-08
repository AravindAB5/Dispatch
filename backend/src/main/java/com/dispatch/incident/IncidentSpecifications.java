package com.dispatch.incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

/** Translates an {@link IncidentFilter} into a single JPA Criteria predicate. */
public final class IncidentSpecifications {

    private static final Pattern INCIDENT_KEY = Pattern.compile("^(?:INC-)?(\\d{1,18})$", Pattern.CASE_INSENSITIVE);

    private IncidentSpecifications() {
    }

    public static Specification<Incident> matching(IncidentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
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
            if (filter.assigneeId() != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), filter.assigneeId()));
            }
            if (filter.unassignedOnly()) {
                predicates.add(cb.isNull(root.get("assignee")));
            }
            if (filter.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.createdTo()));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String term = filter.query().trim();
                String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
                var device = root.join("device", JoinType.LEFT);
                List<Predicate> textMatches = new ArrayList<>();
                textMatches.add(cb.like(cb.lower(root.get("title")), pattern, '\\'));
                textMatches.add(cb.like(cb.lower(device.get("identifier")), pattern, '\\'));
                Matcher key = INCIDENT_KEY.matcher(term);
                if (key.matches()) {
                    textMatches.add(cb.equal(root.get("id"), Long.valueOf(key.group(1))));
                }
                predicates.add(cb.or(textMatches.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
