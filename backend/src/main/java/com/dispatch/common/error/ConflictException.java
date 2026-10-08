package com.dispatch.common.error;

import org.springframework.http.HttpStatus;

/**
 * The request conflicts with the current state of a resource (duplicates, concurrent edits).
 */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
