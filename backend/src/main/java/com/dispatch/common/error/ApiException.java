package com.dispatch.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for errors that map to a specific HTTP status and a stable, machine-readable code.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return status.getReasonPhrase();
    }
}
