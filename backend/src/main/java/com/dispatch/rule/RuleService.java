package com.dispatch.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dispatch.common.error.ConflictException;
import com.dispatch.common.error.FieldErrorDetail;
import com.dispatch.common.error.FieldValidationException;
import com.dispatch.common.error.NotFoundException;
import com.dispatch.incident.IncidentRepository;
import com.dispatch.incident.IncidentStatus;
import com.dispatch.incident.IncidentTitles;
import com.dispatch.rule.dto.RuleRequest;
import com.dispatch.rule.dto.RuleResponse;
import com.dispatch.service.MonitoredService;
import com.dispatch.service.MonitoredServiceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RuleService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final AlertRuleRepository ruleRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;

    public RuleService(AlertRuleRepository ruleRepository, MonitoredServiceRepository serviceRepository,
            IncidentRepository incidentRepository) {
        this.ruleRepository = ruleRepository;
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
    }

    public List<RuleResponse> list() {
        Map<Long, long[]> counts = incidentCounts();
        return ruleRepository.findAllByOrderByNameAsc().stream()
                .map(rule -> toResponse(rule, counts))
                .toList();
    }

    public RuleResponse get(Long id) {
        return toResponse(require(id), incidentCounts());
    }

    @Transactional
    public RuleResponse create(RuleRequest request) {
        validate(request);
        if (ruleRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ConflictException("RULE_NAME_TAKEN", "A rule named \"" + request.name().trim()
                    + "\" already exists");
        }
        AlertRule rule = AlertRule.blank();
        apply(rule, request);
        return toResponse(ruleRepository.save(rule), Map.of());
    }

    @Transactional
    public RuleResponse update(Long id, RuleRequest request) {
        AlertRule rule = require(id);
        validate(request);
        if (ruleRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw new ConflictException("RULE_NAME_TAKEN", "A rule named \"" + request.name().trim()
                    + "\" already exists");
        }
        apply(rule, request);
        return toResponse(rule, incidentCounts());
    }

    @Transactional
    public RuleResponse setEnabled(Long id, boolean enabled) {
        AlertRule rule = require(id);
        rule.setEnabled(enabled);
        return toResponse(rule, incidentCounts());
    }

    /** Incidents created by a deleted rule are kept; their rule reference is cleared (ON DELETE SET NULL). */
    @Transactional
    public void delete(Long id) {
        ruleRepository.delete(require(id));
    }

    private AlertRule require(Long id) {
        return ruleRepository.findWithServiceById(id).orElseThrow(() -> NotFoundException.of("Rule", id));
    }

    private void apply(AlertRule rule, RuleRequest r) {
        MonitoredService service = null;
        if (r.serviceId() != null) {
            service = serviceRepository.findById(r.serviceId())
                    .orElseThrow(() -> new FieldValidationException("serviceId",
                            "service " + r.serviceId() + " does not exist"));
        }
        rule.redefine(r.name().trim(), blankToNull(r.description()), r.conditionType(), blankToNull(r.eventType()),
                blankToNull(r.metricKey()), r.operator(), r.threshold(), r.minEventSeverity(), service,
                r.incidentSeverity(), blankToNull(r.titleTemplate()), blankToNull(r.recoveryEventType()),
                r.autoResolve() == null || r.autoResolve(), r.enabled() == null || r.enabled());
    }

    /** Cross-field rules that Bean Validation annotations cannot express. */
    static void validate(RuleRequest r) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        switch (r.conditionType()) {
            case EVENT_TYPE -> requireText(errors, "eventType", r.eventType(), "an event type rule");
            case METRIC_THRESHOLD -> {
                requireText(errors, "eventType", r.eventType(), "a metric threshold rule");
                requireText(errors, "metricKey", r.metricKey(), "a metric threshold rule");
                if (r.operator() == null) {
                    errors.add(new FieldErrorDetail("operator", "is required for a metric threshold rule"));
                }
                if (r.threshold() == null) {
                    errors.add(new FieldErrorDetail("threshold", "is required for a metric threshold rule"));
                }
            }
            case SEVERITY_AT_LEAST -> {
                if (r.minEventSeverity() == null) {
                    errors.add(new FieldErrorDetail("minEventSeverity", "is required for a severity rule"));
                }
            }
        }
        if (r.recoveryEventType() != null && r.recoveryEventType().equals(r.eventType())) {
            errors.add(new FieldErrorDetail("recoveryEventType", "must differ from the triggering event type"));
        }
        if (r.titleTemplate() != null) {
            Matcher matcher = PLACEHOLDER.matcher(r.titleTemplate());
            while (matcher.find()) {
                if (!IncidentTitles.PLACEHOLDERS.contains(matcher.group(1))) {
                    errors.add(new FieldErrorDetail("titleTemplate", "unknown placeholder {" + matcher.group(1)
                            + "}; supported: " + String.join(", ", IncidentTitles.PLACEHOLDERS)));
                    break;
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static void requireText(List<FieldErrorDetail> errors, String field, String value, String what) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldErrorDetail(field, "is required for " + what));
        }
    }

    /** ruleId → [active, total]. */
    private Map<Long, long[]> incidentCounts() {
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : incidentRepository.countByRuleAndStatusRaw()) {
            long[] c = counts.computeIfAbsent((Long) row[0], k -> new long[2]);
            long n = (Long) row[2];
            if (((IncidentStatus) row[1]).isActive()) {
                c[0] += n;
            }
            c[1] += n;
        }
        return counts;
    }

    private static RuleResponse toResponse(AlertRule rule, Map<Long, long[]> counts) {
        long[] c = counts.getOrDefault(rule.getId(), new long[2]);
        return RuleResponse.from(rule, c[0], c[1]);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
