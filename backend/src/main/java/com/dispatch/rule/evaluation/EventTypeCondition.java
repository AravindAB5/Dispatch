package com.dispatch.rule.evaluation;

import com.dispatch.rule.AlertRule;
import com.dispatch.rule.RuleConditionType;

import org.springframework.stereotype.Component;

/** {@code WHEN event type = X}. */
@Component
public class EventTypeCondition implements RuleConditionEvaluator {

    @Override
    public RuleConditionType type() {
        return RuleConditionType.EVENT_TYPE;
    }

    @Override
    public boolean matches(AlertRule rule, EventFacts facts) {
        return rule.getEventType() != null && rule.getEventType().equalsIgnoreCase(facts.eventType());
    }
}
