package com.dispatch.rule;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    /**
     * Candidate rules for an incoming event: rules triggered by its type, rules it recovers, and
     * severity-based rules. Narrowing in SQL keeps evaluation cheap as the rule set grows.
     * Disabled rules are included so that incidents opened before a rule was disabled can still recover.
     */
    @Query("""
            select r from AlertRule r left join fetch r.service
            where r.eventType = :eventType
               or r.recoveryEventType = :eventType
               or r.conditionType = :severityCondition
            """)
    List<AlertRule> findCandidates(String eventType, RuleConditionType severityCondition);

    default List<AlertRule> findCandidatesFor(String eventType) {
        return findCandidates(eventType, RuleConditionType.SEVERITY_AT_LEAST);
    }

    @EntityGraph(attributePaths = "service")
    List<AlertRule> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "service")
    Optional<AlertRule> findWithServiceById(Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
