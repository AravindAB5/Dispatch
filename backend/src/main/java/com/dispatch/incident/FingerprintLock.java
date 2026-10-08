package com.dispatch.incident;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * Serialises concurrent processing of the same problem.
 * <p>
 * Two events for the same fingerprint may arrive at the same moment on different threads. Each
 * transaction takes a PostgreSQL transaction-scoped advisory lock on the fingerprint before looking for
 * an active incident, so the second transaction sees the incident created by the first instead of
 * creating a duplicate. The lock is released automatically on commit or rollback. The partial unique
 * index {@code ux_incidents_active_fingerprint} remains as a last line of defence.
 */
@Component
public class FingerprintLock {

    private static final RowCallbackHandler IGNORE = rs -> {
    };

    private final JdbcTemplate jdbcTemplate;

    public FingerprintLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire(String fingerprint) {
        jdbcTemplate.query("select pg_advisory_xact_lock(hashtext(?))", IGNORE, fingerprint);
    }
}
