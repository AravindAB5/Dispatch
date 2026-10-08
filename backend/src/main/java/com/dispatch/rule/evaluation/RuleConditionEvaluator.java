package com.dispatch.rule.evaluation;

import com.dispatch.rule.AlertRule;
import com.dispatch.rule.RuleConditionType;

/**
 * Strategy for one {@link RuleConditionType}. Implementations are stateless Spring beans and are
 * discovered automatically by {@link RuleEvaluator}.
 */
public interface RuleConditionEvaluator {

    RuleConditionType type();

    boolean matches(AlertRule rule, EventFacts facts);
}
