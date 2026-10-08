package com.dispatch.common.error;

import org.springframework.http.HttpStatus;

/**
 * The request is well-formed but violates a domain rule (e.g. an invalid incident status transition).
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
