package com.dispatch.rule;

/**
 * Supported rule conditions. Each type has exactly one
 * {@link com.dispatch.rule.evaluation.RuleConditionEvaluator}; adding a new rule type means adding a
 * constant here plus an evaluator bean (the {@code RuleEvaluator} refuses to start if one is missing).
 */
public enum RuleConditionType {
    /** Fires when an event of the given type arrives, e.g. DEVICE_OFFLINE. */
    EVENT_TYPE,
    /** Fires when a numeric metadata value crosses a threshold, e.g. cpu &gt; 90 on CPU_USAGE_HIGH. */
    METRIC_THRESHOLD,
    /** Fires when an event's reported severity is at least the configured level. */
    SEVERITY_AT_LEAST
}
