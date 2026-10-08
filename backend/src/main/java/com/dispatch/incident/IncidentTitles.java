package com.dispatch.incident;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dispatch.event.Event;
import com.dispatch.event.SourceType;
import com.dispatch.rule.AlertRule;
import com.dispatch.rule.evaluation.EventFacts;

/**
 * Renders incident titles from a rule's template, e.g. {@code "{source} is offline"} becomes
 * {@code "DEVICE-108 is offline"}. Unknown placeholders are left untouched.
 */
public final class IncidentTitles {

    public static final String DEFAULT_TEMPLATE = "{rule}: {source}";
    public static final List<String> PLACEHOLDERS =
            List.of("source", "device", "service", "rule", "eventType", "metric", "value");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
    private static final int MAX_LENGTH = 200;

    private IncidentTitles() {
    }

    public static String render(AlertRule rule, Event event, EventFacts facts) {
        String template = rule.getTitleTemplate() == null || rule.getTitleTemplate().isBlank()
                ? DEFAULT_TEMPLATE
                : rule.getTitleTemplate();

        Map<String, String> values = new HashMap<>();
        values.put("rule", rule.getName());
        values.put("source", sourceLabel(event));
        values.put("device", event.getDevice() != null ? event.getDevice().getIdentifier() : event.getSourceId());
        values.put("service", event.getService() != null ? event.getService().getName() : "unknown service");
        values.put("eventType", event.getEventType());
        values.put("metric", rule.getMetricKey() == null ? "" : rule.getMetricKey());
        values.put("value", rule.getMetricKey() == null ? ""
                : facts.metric(rule.getMetricKey()).map(v -> v.stripTrailingZeros().toPlainString()).orElse(""));

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        String title = rendered.toString().trim();
        return title.length() <= MAX_LENGTH ? title : title.substring(0, MAX_LENGTH - 1) + "…";
    }

    private static String sourceLabel(Event event) {
        if (event.getDevice() != null) {
            return event.getDevice().getIdentifier();
        }
        if (event.getSourceType() == SourceType.SERVICE && event.getService() != null) {
            return event.getService().getName();
        }
        return event.getSourceId();
    }
}
