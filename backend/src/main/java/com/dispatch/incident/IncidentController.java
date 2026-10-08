package com.dispatch.incident;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.dispatch.auth.CurrentUser;
import com.dispatch.common.web.PageResponse;
import com.dispatch.common.web.Paging;
import com.dispatch.incident.dto.CommentResponse;
import com.dispatch.incident.dto.HistoryEntryResponse;
import com.dispatch.incident.dto.IncidentDetail;
import com.dispatch.incident.dto.IncidentRequests;
import com.dispatch.incident.dto.IncidentSummary;
import com.dispatch.incident.dto.RelatedEventsResponse;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incident queries and operator commands. Commands are modelled as sub-resources
 * ({@code /assignee}, {@code /status}, {@code /severity}) so each one is validated and audited on its own.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentQueryService queryService;
    private final IncidentService incidentService;

    public IncidentController(IncidentQueryService queryService, IncidentService incidentService) {
        this.queryService = queryService;
        this.incidentService = incidentService;
    }

    @GetMapping
    public PageResponse<IncidentSummary> search(
            @RequestParam(name = "status", required = false) List<IncidentStatus> statuses,
            @RequestParam(name = "severity", required = false) List<IncidentSeverity> severities,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        IncidentFilter filter = new IncidentFilter(statuses, severities, serviceId, deviceId, assigneeId, unassigned,
                query, createdFrom, createdTo);
        return queryService.search(filter, IncidentSort.parse(sort), Paging.page(page), Paging.size(size));
    }

    @GetMapping("/{id}")
    public IncidentDetail get(@PathVariable Long id) {
        return queryService.get(id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntryResponse> history(@PathVariable Long id) {
        return queryService.history(id);
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable Long id) {
        return queryService.comments(id);
    }

    @GetMapping("/{id}/events")
    public RelatedEventsResponse events(@PathVariable Long id, @RequestParam(required = false) Integer limit) {
        return queryService.relatedEvents(id, Paging.size(limit == null ? 50 : limit));
    }

    @PostMapping
    public ResponseEntity<IncidentDetail> declare(@Valid @RequestBody IncidentRequests.Declare request,
            @AuthenticationPrincipal Jwt jwt) {
        IncidentDetail created = incidentService.declare(request, CurrentUser.id(jwt));
        return ResponseEntity.created(URI.create("/api/v1/incidents/" + created.id())).body(created);
    }

    @PutMapping("/{id}/assignee")
    public IncidentDetail assign(@PathVariable Long id, @Valid @RequestBody IncidentRequests.Assign request,
            @AuthenticationPrincipal Jwt jwt) {
        return incidentService.assign(id, request.assigneeId(), CurrentUser.id(jwt));
    }

    @PutMapping("/{id}/status")
    public IncidentDetail changeStatus(@PathVariable Long id, @Valid @RequestBody IncidentRequests.ChangeStatus request,
            @AuthenticationPrincipal Jwt jwt) {
        return incidentService.changeStatus(id, request.status(), request.note(), CurrentUser.id(jwt));
    }

    @PutMapping("/{id}/severity")
    public IncidentDetail changeSeverity(@PathVariable Long id,
            @Valid @RequestBody IncidentRequests.ChangeSeverity request, @AuthenticationPrincipal Jwt jwt) {
        return incidentService.changeSeverity(id, request.severity(), CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
            @Valid @RequestBody IncidentRequests.AddComment request, @AuthenticationPrincipal Jwt jwt) {
        CommentResponse comment = incidentService.addComment(id, request.body(), CurrentUser.id(jwt));
        return ResponseEntity.created(URI.create("/api/v1/incidents/%d/comments/%d".formatted(id, comment.id())))
                .body(comment);
    }
}
