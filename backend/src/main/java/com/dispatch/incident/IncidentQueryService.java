package com.dispatch.incident;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.dispatch.common.error.NotFoundException;
import com.dispatch.common.web.PageResponse;
import com.dispatch.event.EventMapper;
import com.dispatch.incident.dto.CommentResponse;
import com.dispatch.incident.dto.HistoryEntryResponse;
import com.dispatch.incident.dto.IncidentDetail;
import com.dispatch.incident.dto.IncidentSummary;
import com.dispatch.incident.dto.RelatedEventsResponse;
import com.dispatch.incident.dto.RelatedEventsResponse.RelatedEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentCommentRepository commentRepository;
    private final IncidentHistoryRepository historyRepository;
    private final IncidentMapper mapper;
    private final EventMapper eventMapper;

    public IncidentQueryService(IncidentRepository incidentRepository,
            IncidentEventRepository incidentEventRepository, IncidentCommentRepository commentRepository,
            IncidentHistoryRepository historyRepository, IncidentMapper mapper, EventMapper eventMapper) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.mapper = mapper;
        this.eventMapper = eventMapper;
    }

    public PageResponse<IncidentSummary> search(IncidentFilter filter, IncidentSort sort, int page, int size) {
        Page<Incident> result = incidentRepository.findAll(IncidentSpecifications.matching(filter),
                PageRequest.of(page, size, sort.sort()));
        return PageResponse.from(result, mapper::toSummary);
    }

    public IncidentDetail get(Long id) {
        return mapper.toDetail(require(id));
    }

    public List<HistoryEntryResponse> history(Long id) {
        requireExists(id);
        return historyRepository.findForIncident(id).stream().map(HistoryEntryResponse::from).toList();
    }

    public List<CommentResponse> comments(Long id) {
        requireExists(id);
        return commentRepository.findForIncident(id).stream().map(CommentResponse::from).toList();
    }

    public RelatedEventsResponse relatedEvents(Long id, int limit) {
        requireExists(id);
        Map<IncidentEventRelation, Long> counts = new EnumMap<>(IncidentEventRelation.class);
        for (Object[] row : incidentEventRepository.countByRelationRaw(id)) {
            counts.put((IncidentEventRelation) row[0], (Long) row[1]);
        }
        List<RelatedEvent> items = incidentEventRepository.findForIncident(id, PageRequest.of(0, limit)).stream()
                .map(link -> new RelatedEvent(link.getRelation(), link.getLinkedAt(),
                        eventMapper.toResponse(link.getEvent(), List.of())))
                .toList();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new RelatedEventsResponse(total,
                counts.getOrDefault(IncidentEventRelation.TRIGGER, 0L),
                counts.getOrDefault(IncidentEventRelation.OCCURRENCE, 0L),
                counts.getOrDefault(IncidentEventRelation.RECOVERY, 0L),
                items);
    }

    private Incident require(Long id) {
        return incidentRepository.findDetailedById(id).orElseThrow(() -> NotFoundException.of("Incident", id));
    }

    private void requireExists(Long id) {
        if (!incidentRepository.existsById(id)) {
            throw NotFoundException.of("Incident", id);
        }
    }
}
