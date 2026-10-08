package com.dispatch.incident;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.dispatch.common.error.FieldValidationException;

import org.springframework.data.domain.Sort;

/** Whitelisted sort orders for the incident list (never sort by raw client-supplied property names). */
public enum IncidentSort {
    NEWEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    OLDEST(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))),
    UPDATED(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))),
    SEVERITY(Sort.by(Sort.Order.desc("severityRank"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    private final Sort sort;

    IncidentSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }

    public static IncidentSort parse(String value) {
        if (value == null || value.isBlank()) {
            return NEWEST;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(values()).map(v -> v.name().toLowerCase()).collect(Collectors.joining(", "));
            throw new FieldValidationException("sort", "must be one of: " + allowed);
        }
    }
}
