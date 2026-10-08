package com.dispatch.common.error;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dispatch.common.web.RequestIdFilter;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 problem details with Dispatch's extension members:
 * {@code code} (stable machine-readable error code), {@code requestId}, {@code timestamp}
 * and, for validation failures, {@code errors}.
 */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://dispatch.local/problems/" + code.toLowerCase().replace('_', '-')));
        enrich(problem, code);
        return problem;
    }

    public static ProblemDetail validation(List<FieldErrorDetail> errors) {
        ProblemDetail problem = of(org.springframework.http.HttpStatus.BAD_REQUEST, "Validation failed",
                errors.size() == 1
                        ? errors.getFirst().field() + ": " + errors.getFirst().message()
                        : "The request has " + errors.size() + " invalid fields",
                "VALIDATION_FAILED");
        problem.setProperty("errors", errors);
        return problem;
    }

    public static void enrich(ProblemDetail problem, String code) {
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
    }

    /** Flattened map form, for writers that serialize outside Spring MVC (security filters). */
    public static Map<String, Object> toMap(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (problem.getType() != null) {
            body.put("type", problem.getType().toString());
        }
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}
