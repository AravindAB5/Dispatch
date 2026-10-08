package com.dispatch.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException("%s %s was not found".formatted(resource, id));
    }
}
