package com.dispatch.rule.evaluation;

import com.dispatch.rule.AlertRule;
import com.dispatch.rule.RuleConditionType;

import org.springframework.stereotype.Component;

/**
 * {@code WHEN event type = X AND metadata[metricKey] <op> threshold}. Events without a numeric value for
 * the metric never match: a missing reading is not evidence of a problem.
 */
@Component
public class MetricThresholdCondition implements RuleConditionEvaluator {

    @Override
    public RuleConditionType type() {
        return RuleConditionType.METRIC_THRESHOLD;
    }

    @Override
    public boolean matches(AlertRule rule, EventFacts facts) {
        if (rule.getEventType() != null && !rule.getEventType().equalsIgnoreCase(facts.eventType())) {
            return false;
        }
        if (rule.getOperator() == null || rule.getThreshold() == null) {
            return false;
        }
        return facts.metric(rule.getMetricKey())
                .map(value -> rule.getOperator().test(value, rule.getThreshold()))
                .orElse(false);
    }
}
