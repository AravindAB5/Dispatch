package com.dispatch.rule.evaluation;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.dispatch.rule.AlertRule;
import com.dispatch.rule.RuleConditionType;

import org.springframework.stereotype.Component;

/**
 * Decides which rules an event triggers and which rules it recovers. Pure and side-effect free:
 * turning matches into incidents is the job of {@code IncidentAutomationService}.
 */
@Component
public class RuleEvaluator {

    private final Map<RuleConditionType, RuleConditionEvaluator> evaluators =
            new EnumMap<>(RuleConditionType.class);

    public RuleEvaluator(List<RuleConditionEvaluator> evaluators) {
        for (RuleConditionEvaluator evaluator : evaluators) {
            if (this.evaluators.put(evaluator.type(), evaluator) != null) {
                throw new IllegalStateException("Duplicate evaluator for " + evaluator.type());
            }
        }
        for (RuleConditionType type : RuleConditionType.values()) {
            if (!this.evaluators.containsKey(type)) {
                throw new IllegalStateException("No RuleConditionEvaluator registered for " + type);
            }
        }
    }

    /** Enabled, in-scope rules whose condition matches the event. */
    public List<AlertRule> triggeredRules(EventFacts facts, Collection<AlertRule> rules) {
        return rules.stream()
                .filter(AlertRule::isEnabled)
                .filter(rule -> inScope(rule, facts))
                .filter(rule -> evaluators.get(rule.getConditionType()).matches(rule, facts))
                .toList();
    }

    /**
     * In-scope rules for which this event is the recovery signal. Disabled rules are deliberately
     * included so incidents opened before a rule was disabled can still be recovered.
     */
    public List<AlertRule> recoveredRules(EventFacts facts, Collection<AlertRule> rules) {
        return rules.stream()
                .filter(rule -> rule.getRecoveryEventType() != null
                        && rule.getRecoveryEventType().equalsIgnoreCase(facts.eventType()))
                .filter(rule -> inScope(rule, facts))
                .toList();
    }

    private static boolean inScope(AlertRule rule, EventFacts facts) {
        return rule.getService() == null || rule.getService().getId().equals(facts.serviceId());
    }
}
