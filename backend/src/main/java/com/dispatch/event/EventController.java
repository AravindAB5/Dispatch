package com.dispatch.event;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.dispatch.common.web.CursorPage;
import com.dispatch.common.web.Paging;
import com.dispatch.event.dto.EventIngestRequest;
import com.dispatch.event.dto.EventResponse;
import com.dispatch.event.dto.IngestionResponse;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventIngestionService ingestionService;
    private final EventQueryService queryService;

    public EventController(EventIngestionService ingestionService, EventQueryService queryService) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
    }

    /** Event ingestion. Accepts the ingestion API key (X-API-Key) or an ADMIN bearer token. */
    @PostMapping
    public ResponseEntity<IngestionResponse> ingest(@Valid @RequestBody EventIngestRequest request) {
        IngestionResponse response = ingestionService.ingest(request);
        return ResponseEntity.created(URI.create("/api/v1/events/" + response.event().id())).body(response);
    }

    @GetMapping
    public CursorPage<EventResponse> search(
            @RequestParam(name = "type", required = false) List<String> eventTypes,
            @RequestParam(name = "severity", required = false) List<EventSeverity> severities,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        EventFilter filter = new EventFilter(eventTypes, severities, serviceId, deviceId, sourceType, query, from, to);
        return queryService.search(filter, cursor, Paging.size(limit == null ? 50 : limit));
    }

    @GetMapping("/types")
    public List<String> eventTypes() {
        return queryService.eventTypes();
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return queryService.get(id);
    }
}
