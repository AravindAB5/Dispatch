package com.dispatch.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Validation failure discovered in service code (cross-field rules, unknown references) that should be
 * reported exactly like a Bean Validation failure: 400 with per-field errors.
 */
public class FieldValidationException extends ApiException {

    private final List<FieldErrorDetail> errors;

    public FieldValidationException(List<FieldErrorDetail> errors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        this.errors = List.copyOf(errors);
    }

    public FieldValidationException(String field, String message) {
        this(List.of(new FieldErrorDetail(field, message)));
    }

    public List<FieldErrorDetail> getErrors() {
        return errors;
    }
}
