package com.dispatch.event;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.dispatch.common.error.NotFoundException;
import com.dispatch.common.web.CursorPage;
import com.dispatch.event.dto.EventResponse;
import com.dispatch.incident.IncidentEventRepository;
import com.dispatch.incident.dto.EventIncidentLink;
import com.dispatch.incident.dto.EventIncidentLinkRow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventQueryService {

    private final EventRepository eventRepository;
    private final EventSearchRepository searchRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final EventMapper mapper;

    public EventQueryService(EventRepository eventRepository, EventSearchRepository searchRepository,
            IncidentEventRepository incidentEventRepository, EventMapper mapper) {
        this.eventRepository = eventRepository;
        this.searchRepository = searchRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.mapper = mapper;
    }

    public CursorPage<EventResponse> search(EventFilter filter, String cursor, int limit) {
        EventCursor after = cursor == null || cursor.isBlank() ? null : EventCursor.decode(cursor);
        // Fetch one extra row to learn whether another page exists without a COUNT query.
        List<Event> rows = searchRepository.search(filter, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Event> page = hasMore ? rows.subList(0, limit) : rows;

        Map<Long, List<EventIncidentLink>> links = linksFor(page);
        List<EventResponse> items = page.stream()
                .map(event -> mapper.toResponse(event, links.getOrDefault(event.getId(), List.of())))
                .toList();
        String nextCursor = hasMore ? EventCursor.after(page.getLast()).encode() : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    public EventResponse get(Long id) {
        Event event = eventRepository.findWithRefsById(id).orElseThrow(() -> NotFoundException.of("Event", id));
        return mapper.toResponse(event, linksFor(List.of(event)).getOrDefault(id, List.of()));
    }

    public List<String> eventTypes() {
        return eventRepository.findDistinctEventTypes();
    }

    private Map<Long, List<EventIncidentLink>> linksFor(List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = events.stream().map(Event::getId).toList();
        return incidentEventRepository.findLinksForEvents(ids).stream()
                .collect(Collectors.groupingBy(EventIncidentLinkRow::eventId,
                        Collectors.mapping(EventIncidentLinkRow::toLink, Collectors.toList())));
    }
}
