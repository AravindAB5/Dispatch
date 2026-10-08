package com.dispatch.rule.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.dispatch.event.EventSeverity;
import com.dispatch.event.SourceType;
import com.dispatch.incident.IncidentSeverity;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.ComparisonOperator;
import com.dispatch.support.Fixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator(List.of(
            new EventTypeCondition(), new MetricThresholdCondition(), new SeverityAtLeastCondition()));

    private static EventFacts facts(String type, EventSeverity severity, Map<String, Object> metadata) {
        return new EventFacts(type, severity, SourceType.DEVICE, "DEVICE-042", 3L, 42L, metadata);
    }

    @Nested
    class EventTypeRules {

        @Test
        void matchesEventsOfTheConfiguredType() {
            AlertRule rule = Fixtures.offlineRule(1);
            assertThat(evaluator.triggeredRules(facts("DEVICE_OFFLINE", EventSeverity.CRITICAL, Map.of()),
                    List.of(rule))).containsExactly(rule);
        }

        @Test
        void ignoresOtherEventTypes() {
            assertThat(evaluator.triggeredRules(facts("DEVICE_HEARTBEAT", EventSeverity.INFO, Map.of()),
                    List.of(Fixtures.offlineRule(1)))).isEmpty();
        }
    }

    @Nested
    class MetricThresholdRules {

        private final AlertRule cpuAbove90 = Fixtures.cpuRule(2);

        @ParameterizedTest(name = "cpu={0} -> {1}")
        @CsvSource({"94, true", "90.01, true", "90, false", "85, false"})
        void comparesTheMetricAgainstTheThreshold(String cpu, boolean expected) {
            EventFacts event = facts("CPU_USAGE_HIGH", EventSeverity.HIGH, Map.of("cpu", new BigDecimal(cpu)));
            assertThat(evaluator.triggeredRules(event, List.of(cpuAbove90)).contains(cpuAbove90)).isEqualTo(expected);
        }

        @Test
        void greaterOrEqualMatchesExactlyAtTheThreshold() {
            AlertRule rule = Fixtures.withId(AlertRule.onMetric("Memory", "MEMORY_USAGE_HIGH", "memory",
                    ComparisonOperator.GTE, new BigDecimal("90"), IncidentSeverity.MEDIUM), 3);
            assertThat(evaluator.triggeredRules(facts("MEMORY_USAGE_HIGH", EventSeverity.MEDIUM, Map.of("memory", 90)),
                    List.of(rule))).containsExactly(rule);
        }

        @Test
        void lessThanOperatorsWorkForLowWaterMarks() {
            AlertRule rule = Fixtures.withId(AlertRule.onMetric("Low battery", "BATTERY_REPORT", "battery",
                    ComparisonOperator.LT, new BigDecimal("15"), IncidentSeverity.LOW), 4);
            assertThat(evaluator.triggeredRules(facts("BATTERY_REPORT", EventSeverity.INFO, Map.of("battery", 9)),
                    List.of(rule))).containsExactly(rule);
            assertThat(evaluator.triggeredRules(facts("BATTERY_REPORT", EventSeverity.INFO, Map.of("battery", 40)),
                    List.of(rule))).isEmpty();
        }

        @Test
        void readsNumericStringsAndNestedPaths() {
            AlertRule nested = Fixtures.withId(AlertRule.onMetric("Root disk", "DISK_REPORT", "disk.root",
                    ComparisonOperator.GT, new BigDecimal("85"), IncidentSeverity.LOW), 5);
            assertThat(evaluator.triggeredRules(facts("CPU_USAGE_HIGH", EventSeverity.HIGH, Map.of("cpu", "94.5")),
                    List.of(cpuAbove90))).containsExactly(cpuAbove90);
            assertThat(evaluator.triggeredRules(
                    facts("DISK_REPORT", EventSeverity.LOW, Map.of("disk", Map.of("root", 91))),
                    List.of(nested))).containsExactly(nested);
        }

        @Test
        void neverMatchesWhenTheMetricIsMissingOrNotNumeric() {
            assertThat(evaluator.triggeredRules(facts("CPU_USAGE_HIGH", EventSeverity.HIGH, Map.of()),
                    List.of(cpuAbove90))).isEmpty();
            assertThat(evaluator.triggeredRules(facts("CPU_USAGE_HIGH", EventSeverity.HIGH, Map.of("cpu", "very high")),
                    List.of(cpuAbove90))).isEmpty();
        }

        @Test
        void requiresTheConfiguredEventType() {
            assertThat(evaluator.triggeredRules(facts("MEMORY_USAGE_HIGH", EventSeverity.HIGH, Map.of("cpu", 99)),
                    List.of(cpuAbove90))).isEmpty();
        }
    }

    @Nested
    class SeverityRules {

        @Test
        void matchesEventsAtOrAboveTheMinimumSeverity() {
            AlertRule rule = Fixtures.withId(
                    AlertRule.onSeverity("High or worse", EventSeverity.HIGH, IncidentSeverity.HIGH), 6);
            assertThat(evaluator.triggeredRules(facts("ANY", EventSeverity.CRITICAL, Map.of()), List.of(rule)))
                    .containsExactly(rule);
            assertThat(evaluator.triggeredRules(facts("ANY", EventSeverity.HIGH, Map.of()), List.of(rule)))
                    .containsExactly(rule);
            assertThat(evaluator.triggeredRules(facts("ANY", EventSeverity.MEDIUM, Map.of()), List.of(rule)))
                    .isEmpty();
        }
    }

    @Nested
    class ScopeAndState {

        @Test
        void disabledRulesNeverTrigger() {
            AlertRule rule = Fixtures.offlineRule(1).enabled(false);
            assertThat(evaluator.triggeredRules(facts("DEVICE_OFFLINE", EventSeverity.CRITICAL, Map.of()),
                    List.of(rule))).isEmpty();
        }

        @Test
        void serviceScopedRulesOnlyApplyToTheirService() {
            AlertRule scoped = Fixtures.offlineRule(1).scopedTo(Fixtures.service(99, "Other service"));
            assertThat(evaluator.triggeredRules(facts("DEVICE_OFFLINE", EventSeverity.CRITICAL, Map.of()),
                    List.of(scoped))).isEmpty();

            AlertRule sameService = Fixtures.offlineRule(2).scopedTo(Fixtures.service(3, "Device Gateway"));
            assertThat(evaluator.triggeredRules(facts("DEVICE_OFFLINE", EventSeverity.CRITICAL, Map.of()),
                    List.of(sameService))).containsExactly(sameService);
        }

        @Test
        void recoveryMatchingIncludesDisabledRules() {
            AlertRule disabled = Fixtures.offlineRule(1).enabled(false);
            assertThat(evaluator.recoveredRules(facts("DEVICE_RECOVERED", EventSeverity.INFO, Map.of()),
                    List.of(disabled))).containsExactly(disabled);
        }

        @Test
        void returnsEveryMatchingRule() {
            AlertRule offline = Fixtures.offlineRule(1);
            AlertRule catchAll = Fixtures.withId(
                    AlertRule.onSeverity("Catch-all", EventSeverity.CRITICAL, IncidentSeverity.HIGH), 7);
            assertThat(evaluator.triggeredRules(facts("DEVICE_OFFLINE", EventSeverity.CRITICAL, Map.of()),
                    List.of(offline, catchAll))).containsExactly(offline, catchAll);
        }
    }

    @Test
    void refusesToStartWhenAConditionTypeHasNoEvaluator() {
        assertThatThrownBy(() -> new RuleEvaluator(List.of(new EventTypeCondition())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No RuleConditionEvaluator registered");
    }
}
