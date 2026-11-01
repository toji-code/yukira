package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.workspace.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service orchestrating cross-entity research continuity, tagging, saved filters,
 * comparison versioning, and the unified investigation queue (Tasks 191–195).
 */
@Service
public class InvestorResearchWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(InvestorResearchWorkspaceService.class);

    private final InvestorService investorService;
    private final InvestorResearchTagRepository tagRepository;
    private final InvestorSavedFilterRepository filterRepository;
    private final InvestorComparisonResearchVersionRepository comparisonVersionRepository;
    private final InvestorInvestigationActionRepository actionRepository;
    private final InvestorComparisonResearchRepository comparisonResearchRepository;
    private final InvestorPortfolioResearchSnapshotRepository portfolioSnapshotRepository;
    private final InvestorWatchlistRepository watchlistRepository;
    private final InvestorResearchThesisVersionRepository thesisRepository;
    private final InvestorMonitoringEventRepository monitoringEventRepository;

    public InvestorResearchWorkspaceService(
        InvestorService investorService,
        InvestorResearchTagRepository tagRepository,
        InvestorSavedFilterRepository filterRepository,
        InvestorComparisonResearchVersionRepository comparisonVersionRepository,
        InvestorInvestigationActionRepository actionRepository,
        InvestorComparisonResearchRepository comparisonResearchRepository,
        InvestorPortfolioResearchSnapshotRepository portfolioSnapshotRepository,
        InvestorWatchlistRepository watchlistRepository,
        InvestorResearchThesisVersionRepository thesisRepository,
        InvestorMonitoringEventRepository monitoringEventRepository
    ) {
        this.investorService = investorService;
        this.tagRepository = tagRepository;
        this.filterRepository = filterRepository;
        this.comparisonVersionRepository = comparisonVersionRepository;
        this.actionRepository = actionRepository;
        this.comparisonResearchRepository = comparisonResearchRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.watchlistRepository = watchlistRepository;
        this.thesisRepository = thesisRepository;
        this.monitoringEventRepository = monitoringEventRepository;
    }

    // --- TASK 191: UNIFIED RESEARCH TIMELINE ---
    @Transactional(readOnly = true)
    public List<InvestorResearchTimelineEventDto> getUnifiedTimeline(String auth0Subject, int limit) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        List<InvestorResearchTimelineEventDto> events = new ArrayList<>();

        // Watchlist events
        try {
            List<InvestorWatchlist> watchlists = watchlistRepository.findByInvestor(investor);
            for (InvestorWatchlist w : watchlists) {
                events.add(new InvestorResearchTimelineEventDto(
                    "WL_" + w.getId(),
                    "WATCHLIST_ADDED",
                    "Added " + (w.getSchemeOption() != null && w.getSchemeOption().getPlan() != null ? w.getSchemeOption().getPlan().getScheme().getName() : "Fund") + " to Watchlist",
                    "Tracked with target status: " + w.getStatus(),
                    "SCHEME_OPTION",
                    w.getSchemeOption() != null ? String.valueOf(w.getSchemeOption().getId()) : "",
                    w.getSchemeOption() != null ? "/funds/" + w.getSchemeOption().getPlan().getScheme().getId() : "",
                    w.getAddedAt() != null ? w.getAddedAt() : ZonedDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.debug("Error aggregating watchlist events: {}", e.getMessage());
        }

        // Thesis events
        try {
            List<InvestorResearchThesisVersion> theses = thesisRepository.findByInvestorIdOrderByCreatedAtDesc(investor.getId());
            for (InvestorResearchThesisVersion t : theses) {
                events.add(new InvestorResearchTimelineEventDto(
                    "TH_" + t.getId(),
                    "THESIS_UPDATED",
                    "Investment Thesis Documented",
                    t.getThesisBody() != null && t.getThesisBody().length() > 80 ? t.getThesisBody().substring(0, 80) + "..." : t.getThesisBody(),
                    "SCHEME_OPTION",
                    t.getSchemeOption() != null ? String.valueOf(t.getSchemeOption().getId()) : "",
                    t.getSchemeOption() != null && t.getSchemeOption().getPlan() != null && t.getSchemeOption().getPlan().getScheme() != null ? "/funds/" + t.getSchemeOption().getPlan().getScheme().getId() : "",
                    t.getCreatedAt() != null ? t.getCreatedAt().toZonedDateTime() : ZonedDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.debug("Error aggregating thesis events: {}", e.getMessage());
        }

        // Portfolio snapshots
        try {
            List<InvestorPortfolioResearchSnapshot> snapshots = portfolioSnapshotRepository.findByInvestorOrderByCreatedAtDesc(investor);
            for (InvestorPortfolioResearchSnapshot s : snapshots) {
                events.add(new InvestorResearchTimelineEventDto(
                    "SNAP_" + s.getId(),
                    "SNAPSHOT_CREATED",
                    "Captured " + s.getSnapshotTitle(),
                    "Total Value: " + s.getTotalValue() + ", XIRR: " + s.getXirrValue(),
                    "PORTFOLIO_SNAPSHOT",
                    String.valueOf(s.getId()),
                    "/portfolio",
                    s.getCreatedAt() != null ? s.getCreatedAt() : ZonedDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.debug("Error aggregating snapshot events: {}", e.getMessage());
        }

        // Monitoring triggers
        try {
            List<InvestorMonitoringEvent> monEvents = monitoringEventRepository.findByInvestorOrderByTriggeredAtDesc(investor);
            for (InvestorMonitoringEvent me : monEvents) {
                events.add(new InvestorResearchTimelineEventDto(
                    "MON_" + me.getId(),
                    "MONITORING_TRIGGER",
                    "Monitoring Alert: " + me.getTitle(),
                    me.getSummaryText(),
                    "MONITORING_EVENT",
                    String.valueOf(me.getId()),
                    "/monitoring",
                    me.getTriggeredAt() != null ? me.getTriggeredAt() : ZonedDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.debug("Error aggregating monitoring events: {}", e.getMessage());
        }

        // Investigation actions
        try {
            List<InvestorInvestigationAction> actions = actionRepository.findByInvestorOrderByCreatedAtDesc(investor);
            for (InvestorInvestigationAction a : actions) {
                events.add(new InvestorResearchTimelineEventDto(
                    "ACT_" + a.getId(),
                    "ACTION_" + a.getStatus(),
                    "Action: " + a.getActionTitle(),
                    a.getActionDescription(),
                    a.getTargetType(),
                    a.getTargetId(),
                    "",
                    a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt()
                ));
            }
        } catch (Exception e) {
            log.debug("Error aggregating action events: {}", e.getMessage());
        }

        // Sort descending by occurredAt
        return events.stream()
            .sorted(Comparator.comparing(InvestorResearchTimelineEventDto::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit > 0 ? limit : 50)
            .collect(Collectors.toList());
    }

    // --- TASK 192: RESEARCH TAGS ---
    @Transactional(readOnly = true)
    public List<InvestorResearchTagDto> getTagsForTarget(String auth0Subject, String targetType, String targetId) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return tagRepository.findByInvestorAndTargetTypeAndTargetId(investor, targetType, targetId).stream()
            .map(t -> new InvestorResearchTagDto(t.getId(), t.getTagName(), t.getTargetType(), t.getTargetId(), t.getCreatedAt()))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvestorResearchTagDto> getAllTags(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return tagRepository.findByInvestor(investor).stream()
            .map(t -> new InvestorResearchTagDto(t.getId(), t.getTagName(), t.getTargetType(), t.getTargetId(), t.getCreatedAt()))
            .collect(Collectors.toList());
    }

    @Transactional
    public InvestorResearchTagDto addTag(String auth0Subject, String tagName, String targetType, String targetId) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        String cleanTag = tagName != null ? tagName.trim().toLowerCase() : "general";

        return tagRepository.findByInvestorAndTagNameAndTargetTypeAndTargetId(investor, cleanTag, targetType, targetId)
            .map(t -> new InvestorResearchTagDto(t.getId(), t.getTagName(), t.getTargetType(), t.getTargetId(), t.getCreatedAt()))
            .orElseGet(() -> {
                InvestorResearchTag newTag = new InvestorResearchTag();
                newTag.setInvestor(investor);
                newTag.setTagName(cleanTag);
                newTag.setTargetType(targetType != null ? targetType.toUpperCase() : "SCHEME_OPTION");
                newTag.setTargetId(targetId);
                InvestorResearchTag saved = tagRepository.save(newTag);
                return new InvestorResearchTagDto(saved.getId(), saved.getTagName(), saved.getTargetType(), saved.getTargetId(), saved.getCreatedAt());
            });
    }

    @Transactional
    public void deleteTag(String auth0Subject, Long tagId) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        tagRepository.findById(tagId).ifPresent(tag -> {
            if (tag.getInvestor().getId().equals(investor.getId())) {
                tagRepository.delete(tag);
            }
        });
    }

    // --- TASK 193: SAVED DISCOVERY FILTERS ---
    @Transactional(readOnly = true)
    public List<InvestorSavedFilterDto> getSavedFilters(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return filterRepository.findByInvestorOrderByUpdatedAtDesc(investor).stream()
            .map(f -> new InvestorSavedFilterDto(f.getId(), f.getFilterName(), f.getFilterCriteriaJson(), f.getFilterType(), f.getCreatedAt(), f.getUpdatedAt()))
            .collect(Collectors.toList());
    }

    @Transactional
    public InvestorSavedFilterDto saveFilter(String auth0Subject, String filterName, String criteriaJson, String filterType) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorSavedFilter filter = new InvestorSavedFilter();
        filter.setInvestor(investor);
        filter.setFilterName(filterName != null ? filterName.trim() : "Custom Screener Filter");
        filter.setFilterCriteriaJson(criteriaJson != null ? criteriaJson : "{}");
        filter.setFilterType(filterType != null ? filterType.toUpperCase() : "DISCOVERY");
        InvestorSavedFilter saved = filterRepository.save(filter);
        return new InvestorSavedFilterDto(saved.getId(), saved.getFilterName(), saved.getFilterCriteriaJson(), saved.getFilterType(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional
    public void deleteFilter(String auth0Subject, Long filterId) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        filterRepository.findById(filterId).ifPresent(f -> {
            if (f.getInvestor().getId().equals(investor.getId())) {
                filterRepository.delete(f);
            }
        });
    }

    // --- TASK 194: COMPARISON REVISION HISTORY ---
    @Transactional(readOnly = true)
    public List<InvestorComparisonVersionDto> getComparisonVersions(String auth0Subject, Long comparisonResearchId) {
        InvestorComparisonResearch comp = comparisonResearchRepository.findById(comparisonResearchId).orElse(null);
        if (comp == null) return List.of();
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        if (!comp.getInvestor().getId().equals(investor.getId())) return List.of();

        return comparisonVersionRepository.findByComparisonResearchOrderByVersionNumberDesc(comp).stream()
            .map(v -> new InvestorComparisonVersionDto(
                v.getId(),
                v.getComparisonResearch().getId(),
                v.getVersionNumber(),
                v.getSchemeOptionIdsCsv(),
                v.getAsOfDate(),
                v.getBenchmarkCode(),
                v.getCompatibilityStatus(),
                v.getMetricsSummaryJson(),
                v.getInvestorThesis(),
                v.getInvestorNotes(),
                v.getVersionReason(),
                v.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }

    // --- TASK 195: INVESTIGATION ACTIONS ---
    @Transactional(readOnly = true)
    public List<InvestorInvestigationActionDto> getActions(String auth0Subject, String status) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        List<InvestorInvestigationAction> list = status != null && !status.isBlank()
            ? actionRepository.findByInvestorAndStatusOrderByCreatedAtDesc(investor, status.toUpperCase())
            : actionRepository.findByInvestorOrderByCreatedAtDesc(investor);

        return list.stream().map(this::toActionDto).collect(Collectors.toList());
    }

    @Transactional
    public InvestorInvestigationActionDto createAction(String auth0Subject, CreateInvestigationActionRequest req) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorInvestigationAction a = new InvestorInvestigationAction();
        a.setInvestor(investor);
        a.setActionTitle(req.actionTitle() != null ? req.actionTitle() : "Follow-up Investigation");
        a.setActionDescription(req.actionDescription());
        a.setTargetType(req.targetType() != null ? req.targetType().toUpperCase() : "FUND");
        a.setTargetId(req.targetId() != null ? req.targetId() : "");
        a.setPriority(req.priority() != null ? req.priority().toUpperCase() : "MEDIUM");
        a.setDueDate(req.dueDate());
        a.setStatus("OPEN");

        InvestorInvestigationAction saved = actionRepository.save(a);
        log.info("Created investigation action #{} for investor {}: '{}'", saved.getId(), investor.getId(), saved.getActionTitle());
        return toActionDto(saved);
    }

    @Transactional
    public InvestorInvestigationActionDto updateActionStatus(String auth0Subject, Long actionId, String status, String outcomeNotes) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorInvestigationAction a = actionRepository.findById(actionId)
            .orElseThrow(() -> new IllegalArgumentException("Action not found with id: " + actionId));
        if (!a.getInvestor().getId().equals(investor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized action update");
        }

        if (status != null) {
            String newStatus = status.toUpperCase();
            a.setStatus(newStatus);
            if ("COMPLETED".equals(newStatus) && a.getCompletedAt() == null) {
                a.setCompletedAt(ZonedDateTime.now());
            }
        }
        if (outcomeNotes != null) {
            a.setOutcomeNotes(outcomeNotes);
        }

        InvestorInvestigationAction saved = actionRepository.save(a);
        return toActionDto(saved);
    }

    private InvestorInvestigationActionDto toActionDto(InvestorInvestigationAction a) {
        return new InvestorInvestigationActionDto(
            a.getId(),
            a.getActionTitle(),
            a.getActionDescription(),
            a.getTargetType(),
            a.getTargetId(),
            a.getStatus(),
            a.getPriority(),
            a.getDueDate(),
            a.getCompletedAt(),
            a.getOutcomeNotes(),
            a.getCreatedAt(),
            a.getUpdatedAt()
        );
    }
}

