package com.dispatch.rule.evaluation;

import com.dispatch.rule.AlertRule;
import com.dispatch.rule.RuleConditionType;

import org.springframework.stereotype.Component;

/** {@code WHEN event severity >= S [AND event type = X]}. */
@Component
public class SeverityAtLeastCondition implements RuleConditionEvaluator {

    @Override
    public RuleConditionType type() {
        return RuleConditionType.SEVERITY_AT_LEAST;
    }

    @Override
    public boolean matches(AlertRule rule, EventFacts facts) {
        if (rule.getMinEventSeverity() == null || facts.severity() == null) {
            return false;
        }
        if (rule.getEventType() != null && !rule.getEventType().equalsIgnoreCase(facts.eventType())) {
            return false;
        }
        return facts.severity().isAtLeast(rule.getMinEventSeverity());
    }
}
