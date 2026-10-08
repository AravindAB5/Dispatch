package com.dispatch.rule.dto;

import com.dispatch.rule.AlertRule;

public record RuleRef(Long id, String name, String condition, String recoveryEventType, boolean autoResolve) {

    public static RuleRef from(AlertRule rule) {
        return rule == null ? null
                : new RuleRef(rule.getId(), rule.getName(), rule.conditionSummary(), rule.getRecoveryEventType(),
                        rule.isAutoResolve());
    }
}
