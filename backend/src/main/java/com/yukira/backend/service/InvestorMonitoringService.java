package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.DataQualityFreshnessDto;
import com.yukira.backend.dto.monitoring.*;
import com.yukira.backend.dto.portfolio.PortfolioDriftAnalysisDto;
import com.yukira.backend.dto.portfolio.intelligence.ConsolidatedPortfolioSecurityDto;
import com.yukira.backend.dto.portfolio.intelligence.InvestorPortfolioIntelligenceResponseDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.portfolio.InvestorPortfolioIntelligenceService;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service orchestrating Continuous Fund & Portfolio Monitoring in Project YUKIRA (Task 12 & Task 13).
 *
 * Epistemic & Methodological Guardrails:
 * 1. Zero financial math or event generation in frontend presentation tier.
 * 2. Deterministic backend-authoritative triggers based on stored empirical observations.
 * 3. Strict alignment with AuthoritativeMetricRegistryService:
 *    - RSK-01 = Annualized Volatility
 *    - RSK-02 = Downside Semideviation
 *    - RSK-03 = Maximum Drawdown (Peak-to-Trough Capital Loss, negative decimal convention)
 * 4. Strict discrimination between genuine investment changes and data quality / disclosure timing artifacts.
 * 5. Deduplication: Stable dedup hashes prevent repeating identical alerts on each evaluation cycle.
 * 6. Explicit execution ledger: Tracks evaluation runs, status, and telemetry.
 * 7. Strict investor data isolation: All queries and mutations are investor-scoped.
 * 8. Non-advisory: Monitoring is an investigation checkpoint, NEVER an automated buy/sell recommendation.
 */
@Service
@Transactional
public class InvestorMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(InvestorMonitoringService.class);

    private final InvestorService investorService;
    private final InvestorRepository investorRepository;
    private final InvestorWatchlistRepository watchlistRepository;
    private final InvestorPortfolioHoldingRepository portfolioHoldingRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final SchemeRepository schemeRepository;
    private final InvestorMonitoringEventRepository eventRepository;
    private final InvestorMonitoringConditionRepository conditionRepository;
    private final InvestorMonitoringEventNoteRepository noteRepository;
    private final InvestorMonitoringExecutionRepository executionRepository;
    private final AuthoritativeMetricRegistryService authoritativeRegistry;
    private final MonitoringMetricRegistryService metricConfigService;
    private final DataQualityFreshnessService freshnessService;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;
    private final InvestorPortfolioService investorPortfolioService;
    private final InvestorPortfolioIntelligenceService portfolioIntelligenceService;
    private final InvestorMonitoringConditionHistoryRepository conditionHistoryRepository;
    private final NotificationDeliveryService notificationDeliveryService;

    // Concurrency guard per investor ID to prevent overlapping runs for the same investor
    private final ConcurrentHashMap<Long, Boolean> activeInvestorLocks = new ConcurrentHashMap<>();

    @Autowired
    public InvestorMonitoringService(
        InvestorService investorService,
        InvestorRepository investorRepository,
        InvestorWatchlistRepository watchlistRepository,
        InvestorPortfolioHoldingRepository portfolioHoldingRepository,
        SchemeOptionRepository schemeOptionRepository,
        SchemeRepository schemeRepository,
        InvestorMonitoringEventRepository eventRepository,
        InvestorMonitoringConditionRepository conditionRepository,
        InvestorMonitoringEventNoteRepository noteRepository,
        InvestorMonitoringExecutionRepository executionRepository,
        AuthoritativeMetricRegistryService authoritativeRegistry,
        MonitoringMetricRegistryService metricConfigService,
        DataQualityFreshnessService freshnessService,
        @Autowired(required = false) PortfolioSnapshotRepository snapshotRepository,
        @Autowired(required = false) PortfolioHoldingRepository holdingRepository,
        @Autowired(required = false) CalculationRunRepository calculationRunRepository,
        @Autowired(required = false) MetricResultRepository metricResultRepository,
        @Autowired(required = false) InvestorPortfolioService investorPortfolioService,
        @Autowired(required = false) InvestorPortfolioIntelligenceService portfolioIntelligenceService,
        @Autowired(required = false) InvestorMonitoringConditionHistoryRepository conditionHistoryRepository,
        @Autowired(required = false) NotificationDeliveryService notificationDeliveryService
    ) {
        this.investorService = investorService;
        this.investorRepository = investorRepository;
        this.watchlistRepository = watchlistRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.schemeRepository = schemeRepository;
        this.eventRepository = eventRepository;
        this.conditionRepository = conditionRepository;
        this.noteRepository = noteRepository;
        this.executionRepository = executionRepository;
        this.authoritativeRegistry = authoritativeRegistry;
        this.metricConfigService = metricConfigService;
        this.freshnessService = freshnessService;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
        this.investorPortfolioService = investorPortfolioService;
        this.portfolioIntelligenceService = portfolioIntelligenceService;
        this.conditionHistoryRepository = conditionHistoryRepository;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    /**
     * Retrieves the high-level monitoring status and summary metrics for an investor.
     */
    @Transactional(readOnly = true)
    public MonitoringOverviewDto getMonitoringOverview(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);

        List<String> activeStatuses = List.of("NEW", "ACKNOWLEDGED", "INVESTIGATING");
        long totalActive = eventRepository.countByInvestorAndLifecycleStatusIn(investor, activeStatuses);
        long highCount = eventRepository.countByInvestorAndSeverityInAndLifecycleStatusIn(
            investor, List.of("HIGH"), activeStatuses);
        long medCount = eventRepository.countByInvestorAndSeverityInAndLifecycleStatusIn(
            investor, List.of("MEDIUM"), activeStatuses);
        long lowCount = eventRepository.countByInvestorAndSeverityInAndLifecycleStatusIn(
            investor, List.of("LOW"), activeStatuses);
        long infoCount = eventRepository.countByInvestorAndSeverityInAndLifecycleStatusIn(
            investor, List.of("INFO"), activeStatuses);

        long awaitingAck = eventRepository.countByInvestorAndLifecycleStatus(investor, "NEW");
        long investigating = eventRepository.countByInvestorAndLifecycleStatus(investor, "INVESTIGATING");
        long resolved = eventRepository.countByInvestorAndLifecycleStatus(investor, "RESOLVED");
        long dismissed = eventRepository.countByInvestorAndLifecycleStatus(investor, "DISMISSED");

        long triggeredTheses = eventRepository.countByInvestorAndEventClassificationAndLifecycleStatusIn(
            investor, "THESIS_CONDITION_TRIGGERED", activeStatuses);
        long freshnessWarnings = eventRepository.countByInvestorAndEventClassificationAndLifecycleStatusIn(
            investor, "DATA_FRESHNESS_CHANGE", activeStatuses);

        int monitoredFunds = watchlistRepository.findByInvestorId(investor.getId()).size();
        int monitoredPositions = portfolioHoldingRepository.findByInvestorId(investor.getId()).size();

        // Execution status information
        Optional<InvestorMonitoringExecution> latestExecOpt = executionRepository.findTopByInvestorOrderByStartedAtDesc(investor);
        ZonedDateTime latestEval = latestExecOpt.map(InvestorMonitoringExecution::getStartedAt).orElse(null);
        String latestExecStatus = latestExecOpt.map(InvestorMonitoringExecution::getExecutionStatus).orElse("NOT_EVALUATED");
        long unavailableCount = latestExecOpt.map(e -> (long) e.getUnavailableEvidenceCount()).orElse(0L);
        long staleCount = latestExecOpt.map(e -> (long) e.getStaleSourceCount()).orElse(freshnessWarnings);

        // Next scheduled evaluation is daily at 4:00 AM IST
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(istZone);
        ZonedDateTime scheduledToday = nowIst.with(LocalTime.of(4, 0));
        ZonedDateTime nextScheduled = nowIst.isBefore(scheduledToday) ? scheduledToday : scheduledToday.plusDays(1);

        return new MonitoringOverviewDto(
            totalActive,
            highCount,
            medCount,
            lowCount,
            infoCount,
            awaitingAck,
            investigating,
            resolved,
            dismissed,
            triggeredTheses,
            freshnessWarnings,
            monitoredFunds,
            monitoredPositions,
            latestEval,
            latestExecStatus,
            nextScheduled,
            unavailableCount,
            staleCount,
            "Monitoring event counts reflect factual empirical threshold detections and data quality warnings. Event counts are NEVER combined into a composite fund or portfolio rating."
        );
    }

    /**
     * Retrieves filtered and paginated monitoring events for an investor.
     */
    @Transactional(readOnly = true)
    public Page<MonitoringEventDto> getFilteredEvents(
        String auth0Subject,
        Long schemeId,
        Long schemeOptionId,
        String classification,
        String severity,
        String status,
        String scope,
        int page,
        int size
    ) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        Page<InvestorMonitoringEvent> eventPage = eventRepository.findFilteredEvents(
            investor, schemeId, schemeOptionId, classification, severity, status, scope, pageable
        );
        return eventPage.map(this::mapToEventDto);
    }

    /**
     * Retrieves full detail for a single monitoring event.
     */
    @Transactional(readOnly = true)
    public Optional<MonitoringEventDto> getEventDetail(Long eventId, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return eventRepository.findByIdAndInvestor(eventId, investor).map(this::mapToEventDto);
    }

    /**
     * Updates the lifecycle status of a monitoring event with resolution/investigation notes.
     */
    public MonitoringEventDto updateLifecycle(Long eventId, MonitoringLifecycleUpdateRequest request, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorMonitoringEvent event = eventRepository.findByIdAndInvestor(eventId, investor)
            .orElseThrow(() -> new IllegalArgumentException("Monitoring event not found or access denied for ID: " + eventId));

        if (request.lifecycleStatus() != null && !request.lifecycleStatus().isBlank()) {
            String newStatus = request.lifecycleStatus().toUpperCase();
            event.setLifecycleStatus(newStatus);
            ZonedDateTime now = ZonedDateTime.now();

            switch (newStatus) {
                case "ACKNOWLEDGED" -> event.setAcknowledgedAt(now);
                case "INVESTIGATING" -> event.setInvestigatingAt(now);
                case "RESOLVED" -> {
                    event.setResolvedAt(now);
                    if (request.resolutionNote() != null) {
                        event.setResolutionNote(request.resolutionNote());
                    }
                }
                case "DISMISSED" -> {
                    event.setDismissedAt(now);
                    if (request.resolutionNote() != null) {
                        event.setResolutionNote(request.resolutionNote());
                    }
                }
                default -> {}
            }
        }

        if (request.investigationNote() != null && !request.investigationNote().isBlank()) {
            InvestorMonitoringEventNote note = new InvestorMonitoringEventNote(
                event, request.investigationNote(), "INVESTOR"
            );
            noteRepository.save(note);
            event.getNotes().add(0, note);
        }

        InvestorMonitoringEvent saved = eventRepository.save(event);
        return mapToEventDto(saved);
    }

    /**
     * Adds an investor investigation note to an existing monitoring event.
     */
    public MonitoringEventNoteDto addEventNote(Long eventId, String noteText, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorMonitoringEvent event = eventRepository.findByIdAndInvestor(eventId, investor)
            .orElseThrow(() -> new IllegalArgumentException("Monitoring event not found for ID: " + eventId));

        if (noteText == null || noteText.trim().isEmpty()) {
            throw new IllegalArgumentException("Investigation note cannot be empty.");
        }

        InvestorMonitoringEventNote note = new InvestorMonitoringEventNote(event, noteText.trim(), "INVESTOR");
        InvestorMonitoringEventNote saved = noteRepository.save(note);
        return new MonitoringEventNoteDto(saved.getId(), event.getId(), saved.getNoteText(), saved.getAuthor(), saved.getCreatedAt());
    }

    /**
     * Retrieves all investor-defined monitoring conditions.
     */
    @Transactional(readOnly = true)
    public List<MonitoringConditionDto> getConditions(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return conditionRepository.findByInvestorOrderByCreatedAtDesc(investor).stream()
            .map(this::mapToConditionDto)
            .collect(Collectors.toList());
    }

    /**
     * Creates a new investor-defined monitoring condition with unit normalization.
     */
    public MonitoringConditionDto createCondition(MonitoringConditionRequest req, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);

        if (req.conditionName() == null || req.conditionName().isBlank()) {
            throw new IllegalArgumentException("Condition name is required.");
        }
        if (req.evidenceField() == null || req.evidenceField().isBlank()) {
            throw new IllegalArgumentException("Evidence field is required.");
        }
        if (req.operator() == null || req.operator().isBlank()) {
            throw new IllegalArgumentException("Comparison operator is required.");
        }

        String fieldCode = req.evidenceField().trim().toUpperCase();

        InvestorMonitoringCondition condition = new InvestorMonitoringCondition();
        condition.setInvestor(investor);
        condition.setConditionName(req.conditionName().trim());
        condition.setTargetScope(req.targetScope() != null ? req.targetScope() : "SCHEME_OPTION");
        condition.setEvidenceField(fieldCode);
        condition.setOperator(req.operator().trim().toUpperCase());
        condition.setThresholdValue(req.thresholdValue());

        // Unit normalization
        String unit = req.thresholdUnit() != null ? req.thresholdUnit() : "PERCENTAGE";
        condition.setThresholdUnit(unit);
        condition.setThresholdNumeric(metricConfigService.normalizeThreshold(fieldCode, req.thresholdNumeric(), unit));
        condition.setThresholdClassification(req.thresholdClassification() != null ? req.thresholdClassification() : "INVESTOR_DEFINED_THRESHOLD");
        condition.setTimeWindow(req.timeWindow() != null ? req.timeWindow() : "3Y");

        condition.setAssociatedThesisKey(req.associatedThesisKey());
        condition.setThesisReconsiderationGuidance(req.thesisReconsiderationGuidance());
        condition.setIsEnabled(req.isEnabled() != null ? req.isEnabled() : true);
        condition.setPortfolioId(req.portfolioId());
        condition.setStressFindingId(req.stressFindingId());
        condition.setOriginatingFindingId(req.stressFindingId());
        condition.setConditionStatus("ACTIVE");

        // Verification of metric compatibility
        if (!metricConfigService.isSupported(fieldCode) && !authoritativeRegistry.isRegistered(fieldCode)) {
            condition.setEvaluationState("REQUIRES_REVIEW");
            condition.setEvidenceState("METHODOLOGY_INCOMPATIBLE");
        } else {
            condition.setEvaluationState("NOT_EVALUATED");
            condition.setEvidenceState("AVAILABLE");
        }

        if (req.schemeOptionId() != null) {
            schemeOptionRepository.findById(req.schemeOptionId()).ifPresent(opt -> {
                condition.setSchemeOption(opt);
                if (opt.getPlan() != null && opt.getPlan().getScheme() != null) {
                    condition.setScheme(opt.getPlan().getScheme());
                }
            });
        } else if (req.schemeId() != null) {
            schemeRepository.findById(req.schemeId()).ifPresent(condition::setScheme);
        }

        InvestorMonitoringCondition saved = conditionRepository.save(condition);
        return mapToConditionDto(saved);
    }

    /**
     * Updates an existing monitoring condition with unit normalization.
     */
    public MonitoringConditionDto updateCondition(Long id, MonitoringConditionRequest req, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorMonitoringCondition condition = conditionRepository.findByIdAndInvestor(id, investor)
            .orElseThrow(() -> new IllegalArgumentException("Monitoring condition not found for ID: " + id));

        if (req.conditionName() != null) condition.setConditionName(req.conditionName().trim());
        if (req.operator() != null) condition.setOperator(req.operator().trim().toUpperCase());
        if (req.thresholdValue() != null) condition.setThresholdValue(req.thresholdValue());

        String fieldCode = condition.getEvidenceField();
        String unit = req.thresholdUnit() != null ? req.thresholdUnit() : condition.getThresholdUnit();
        condition.setThresholdUnit(unit);

        if (req.thresholdNumeric() != null) {
            condition.setThresholdNumeric(metricConfigService.normalizeThreshold(fieldCode, req.thresholdNumeric(), unit));
        }
        if (req.thresholdClassification() != null) condition.setThresholdClassification(req.thresholdClassification());
        if (req.timeWindow() != null) condition.setTimeWindow(req.timeWindow());
        if (req.thesisReconsiderationGuidance() != null) condition.setThesisReconsiderationGuidance(req.thesisReconsiderationGuidance());
        if (req.isEnabled() != null) condition.setIsEnabled(req.isEnabled());
        if (req.portfolioId() != null) condition.setPortfolioId(req.portfolioId());
        if (req.stressFindingId() != null) condition.setStressFindingId(req.stressFindingId());

        InvestorMonitoringCondition saved = conditionRepository.save(condition);
        return mapToConditionDto(saved);
    }

    /**
     * Direct Finding-to-Monitoring Handoff: Seamless Thesis Tracking (Task 89).
     * Converts an active stress finding into a tracked condition with one click.
     */
    @Transactional
    public MonitoringConditionDto createConditionFromFinding(CreateConditionFromFindingRequest req, String auth0Subject) {
        if (req == null || req.findingId() == null || req.findingId().isBlank()) {
            throw new IllegalArgumentException("Finding ID is required to create a monitoring condition from a stress finding.");
        }
        String findingId = req.findingId().trim();
        String thesisKey = "finding:" + findingId;
        String name = req.conditionName() != null && !req.conditionName().isBlank()
            ? req.conditionName().trim()
            : "Stress Finding Guard: " + findingId;
        String field = req.evidenceField() != null && !req.evidenceField().isBlank()
            ? req.evidenceField().trim().toUpperCase()
            : "STR-02"; // default Historical Stress Max Drawdown
        String op = req.operator() != null && !req.operator().isBlank()
            ? req.operator().trim().toUpperCase()
            : "GT";

        MonitoringConditionRequest conditionReq = new MonitoringConditionRequest(
            null, // schemeId
            req.schemeOptionId(), // schemeOptionId
            req.schemeOptionId() != null ? "SCHEME_OPTION" : "PORTFOLIO",
            name,
            field,
            op,
            req.thresholdValue() != null ? req.thresholdValue() : "-15.00%",
            req.thresholdNumeric() != null ? req.thresholdNumeric() : new BigDecimal("-15.0"),
            req.thresholdUnit() != null ? req.thresholdUnit() : "PERCENTAGE",
            "FINDING_CONVERTED_THRESHOLD",
            req.timeWindow() != null ? req.timeWindow() : "3Y",
            thesisKey,
            req.thesisReconsiderationGuidance() != null
                ? req.thesisReconsiderationGuidance()
                : "Active hypothesis from stress finding " + findingId + ". Re-evaluate portfolio risk if triggered.",
            true,
            req.portfolioId(),
            findingId
        );
        return createCondition(conditionReq, auth0Subject);
    }

    /**
     * Deletes a monitoring condition.
     */
    public void deleteCondition(Long id, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorMonitoringCondition condition = conditionRepository.findByIdAndInvestor(id, investor)
            .orElseThrow(() -> new IllegalArgumentException("Monitoring condition not found for ID: " + id));
        conditionRepository.delete(condition);
    }

    /**
     * Updates condition lifecycle status (ACTIVE, PAUSED, RESOLVED, DISMISSED) (Task 151).
     */
    @Transactional
    public MonitoringConditionDto updateConditionStatus(Long conditionId, String newStatus, String notes, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorMonitoringCondition c = conditionRepository.findByIdAndInvestor(conditionId, investor)
            .orElseThrow(() -> new IllegalArgumentException("Monitoring condition not found for ID: " + conditionId));

        String status = (newStatus != null && !newStatus.isBlank()) ? newStatus.toUpperCase().trim() : "ACTIVE";
        c.setConditionStatus(status);
        if ("PAUSED".equals(status)) {
            c.setIsEnabled(false);
            c.setPausedAt(ZonedDateTime.now());
            c.setPauseReason(notes != null ? notes : "Investor paused condition");
        } else if ("ACTIVE".equals(status)) {
            c.setIsEnabled(true);
            c.setResumeAt(ZonedDateTime.now());
            c.setPausedAt(null);
        } else if ("RESOLVED".equals(status) || "DISMISSED".equals(status)) {
            c.setIsEnabled(false);
            c.setEvaluationState(status);
        }
        if (notes != null && !notes.isBlank()) {
            String existingGuidance = c.getThesisReconsiderationGuidance() != null ? c.getThesisReconsiderationGuidance() + "\n" : "";
            c.setThesisReconsiderationGuidance(existingGuidance + "[" + status + "]: " + notes.trim());
        }
        InvestorMonitoringCondition saved = conditionRepository.save(c);
        return mapToConditionDto(saved);
    }

    /**
     * Toggles a condition enabled/disabled state.
     */
    public MonitoringConditionDto toggleCondition(Long id, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorMonitoringCondition condition = conditionRepository.findByIdAndInvestor(id, investor)
            .orElseThrow(() -> new IllegalArgumentException("Monitoring condition not found for ID: " + id));
        condition.setIsEnabled(!condition.getIsEnabled());
        InvestorMonitoringCondition saved = conditionRepository.save(condition);
        return mapToConditionDto(saved);
    }

    /**
     * On-demand investor-initiated evaluation entry point.
     */
    public MonitoringEvaluationResponseDto evaluateMonitoring(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorMonitoringExecution execution = executeMonitoringSweep(investor, "INVESTOR_INITIATED", null);

        List<String> notes = new ArrayList<>();
        notes.add(String.format("Evaluated %d watched funds and %d portfolio positions.",
            execution.getFundsEvaluatedCount(), execution.getPortfolioScopesEvaluatedCount()));
        notes.add(String.format("Evaluated %d active investor-defined conditions.", execution.getConditionsEvaluatedCount()));
        notes.add(String.format("%d new events generated (%d deduplicated, %d unavailable).",
            execution.getEventsGeneratedCount(), execution.getEventsDeduplicatedCount(), execution.getUnavailableEvidenceCount()));

        long activeCount = eventRepository.countByInvestorAndLifecycleStatusIn(
            investor, List.of("NEW", "ACKNOWLEDGED", "INVESTIGATING")
        );

        return new MonitoringEvaluationResponseDto(
            execution.getFundsEvaluatedCount(),
            execution.getPortfolioScopesEvaluatedCount(),
            execution.getConditionsEvaluatedCount(),
            execution.getEventsGeneratedCount(),
            (int) activeCount,
            execution.getCompletedAt() != null ? execution.getCompletedAt() : ZonedDateTime.now(),
            notes
        );
    }

    /**
     * Core deterministic monitoring execution engine with execution telemetry and concurrency safety.
     * Evaluates watched mutual funds, portfolio construction, data freshness, and research thesis conditions.
     */
    public InvestorMonitoringExecution executeMonitoringSweep(
        Investor investor,
        String triggerType,
        ZonedDateTime knowledgeCutoff
    ) {
        // Concurrency overlap prevention per investor
        if (activeInvestorLocks.putIfAbsent(investor.getId(), Boolean.TRUE) != null) {
            log.warn("Monitoring sweep skipped for investor ID {}: another execution is currently running.", investor.getId());
            InvestorMonitoringExecution skipped = new InvestorMonitoringExecution();
            skipped.setInvestor(investor);
            skipped.setExecutionTrigger(triggerType);
            skipped.setExecutionStatus("SKIPPED");
            skipped.setStartedAt(ZonedDateTime.now());
            skipped.setCompletedAt(ZonedDateTime.now());
            skipped.setErrorMessage("Skipped due to concurrent active execution.");
            return executionRepository.save(skipped);
        }

        InvestorMonitoringExecution execution = new InvestorMonitoringExecution();
        execution.setInvestor(investor);
        execution.setExecutionTrigger(triggerType != null ? triggerType : "SCHEDULED");
        execution.setExecutionStatus("RUNNING");
        execution.setStartedAt(ZonedDateTime.now());
        execution.setKnowledgeCutoff(knowledgeCutoff != null ? knowledgeCutoff : ZonedDateTime.now());
        execution = executionRepository.save(execution);

        try {
            // 1. Gather monitored universe
            List<InvestorWatchlist> watchlist = watchlistRepository.findByInvestorId(investor.getId());
            List<InvestorPortfolioHolding> holdings = portfolioHoldingRepository.findByInvestorId(investor.getId());

            Set<SchemeOption> monitoredOptions = new LinkedHashSet<>();
            for (InvestorWatchlist w : watchlist) {
                if (w.getSchemeOption() != null) {
                    monitoredOptions.add(w.getSchemeOption());
                }
            }
            for (InvestorPortfolioHolding h : holdings) {
                if (h.getSchemeOption() != null) {
                    monitoredOptions.add(h.getSchemeOption());
                }
            }

            execution.setFundsEvaluatedCount(monitoredOptions.size());
            execution.setPortfolioScopesEvaluatedCount(holdings.isEmpty() ? 0 : 1);

            int generatedEvents = 0;

            // 2. Evaluate each fund share class
            for (SchemeOption option : monitoredOptions) {
                generatedEvents += evaluateFundOptionEvents(investor, option, execution);
            }

            // 3. Evaluate portfolio-level evidence drift
            if (!holdings.isEmpty()) {
                generatedEvents += evaluatePortfolioLevelEvents(investor, execution);
            }

            // 4. Evaluate investor-defined conditions
            int evaluatedConditions = evaluateInvestorConditions(investor, execution);
            execution.setConditionsEvaluatedCount(evaluatedConditions);
            execution.setEventsGeneratedCount(generatedEvents);

            // Determine final completion status
            if (execution.getUnavailableEvidenceCount() > 0 || execution.getStaleSourceCount() > 0) {
                execution.setExecutionStatus("PARTIALLY_COMPLETED");
            } else {
                execution.setExecutionStatus("COMPLETED");
            }

            execution.setCompletedAt(ZonedDateTime.now());
            execution.setSummaryNotes(String.format(
                "Evaluated %d funds, %d conditions. Generated %d events (%d deduplicated, %d unavailable evidence, %d stale sources).",
                monitoredOptions.size(), evaluatedConditions, generatedEvents,
                execution.getEventsDeduplicatedCount(), execution.getUnavailableEvidenceCount(), execution.getStaleSourceCount()
            ));

            InvestorMonitoringExecution savedExecution = executionRepository.save(execution);

            if (notificationDeliveryService != null) {
                try {
                    notificationDeliveryService.processPendingOutboxDeliveries();
                } catch (Exception ne) {
                    log.warn("Error processing pending notification deliveries: {}", ne.getMessage());
                }
            }

            return savedExecution;

        } catch (Exception e) {
            log.error("Continuous monitoring execution failed for investor ID {}: {}", investor.getId(), e.getMessage(), e);
            execution.setExecutionStatus("FAILED");
            execution.setCompletedAt(ZonedDateTime.now());
            execution.setErrorMessage(e.getMessage());
            InvestorMonitoringExecution savedExecution = executionRepository.save(execution);

            if (notificationDeliveryService != null) {
                try {
                    notificationDeliveryService.processPendingOutboxDeliveries();
                } catch (Exception ne) {
                    log.warn("Error processing pending notification deliveries: {}", ne.getMessage());
                }
            }

            return savedExecution;
        } finally {
            activeInvestorLocks.remove(investor.getId());
        }
    }

    /**
     * Evaluates fund-specific evidence: performance, holdings changes, and data freshness.
     */
    private int evaluateFundOptionEvents(Investor investor, SchemeOption option, InvestorMonitoringExecution execution) {
        int generated = 0;
        Scheme scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;

        // --- Domain D: Data Freshness & Quality ---
        try {
            DataQualityFreshnessDto freshness = freshnessService.evaluateQualityAndFreshness(option.getId(), null, null);
            if (freshness != null) {
                // Check NAV Stale
                if (freshness.navEvidence() != null && "STALE".equalsIgnoreCase(freshness.navEvidence().freshnessState())) {
                    long age = freshness.navEvidence().observationAgeDays() != null ? freshness.navEvidence().observationAgeDays() : 0L;
                    execution.setStaleSourceCount(execution.getStaleSourceCount() + 1);

                    String dedup = computeDedupHash(investor.getId(), "FUND", option.getId(), "NAV_FRESHNESS", freshness.evaluationDate());
                    boolean created = recordOrUpdateEvent(
                        investor, scheme, option, null,
                        "DATA_FRESHNESS_CHANGE",
                        age > 7 ? "HIGH" : "MEDIUM",
                        "NAV_FRESHNESS",
                        "NAV Observation Freshness",
                        "CURRENT (Daily Business Day)",
                        String.format("STALE (%d days since latest NAV %s)", age, freshness.navEvidence().latestNavDate()),
                        BigDecimal.valueOf(age),
                        "DAYS",
                        "SYSTEM_CANDIDATE_THRESHOLD",
                        "STALE",
                        freshness.navEvidence().latestNavDate(),
                        freshness.knowledgeCutoff() != null ? freshness.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                        ZonedDateTime.now(),
                        freshness.navEvidence().sha256Hash(),
                        freshness.navEvidence().latestNavDate(),
                        freshness.evaluationDate(),
                        freshness.knowledgeCutoff() != null ? freshness.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                        freshness.navEvidence().sha256Hash(),
                        null,
                        execution,
                        "V1",
                        "PHASE_2H_FROZEN",
                        "Latest NAV observation date: " + freshness.navEvidence().latestNavDate(),
                        String.format("Latest NAV observation for %s is %d days old, exceeding the expected daily business day reporting cadence.",
                            scheme != null ? scheme.getName() : "fund", age),
                        "Cadence policy: " + freshness.navEvidence().cadencePolicy(),
                        "NAV freshness is evaluated strictly independent of holdings freshness.",
                        true,
                        "This indicates a delay in published NAV data from AMFI or the AMC. This is a source disclosure/pipeline issue, NOT an investment loss or fund value decline.",
                        dedup,
                        null, null, null
                    );
                    if (created) generated++;
                    else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                }

                // Check Holdings Stale
                if (freshness.holdingsEvidence() != null && "STALE".equalsIgnoreCase(freshness.holdingsEvidence().freshnessState())) {
                    long age = freshness.holdingsEvidence().reportingAgeDays() != null ? freshness.holdingsEvidence().reportingAgeDays() : 0L;
                    execution.setStaleSourceCount(execution.getStaleSourceCount() + 1);

                    String dedup = computeDedupHash(investor.getId(), "FUND", option.getId(), "HOLDINGS_FRESHNESS", freshness.evaluationDate());
                    boolean created = recordOrUpdateEvent(
                        investor, scheme, option, null,
                        "DATA_FRESHNESS_CHANGE",
                        "MEDIUM",
                        "HOLDINGS_FRESHNESS",
                        "Monthly Holdings Disclosure Freshness",
                        "CURRENT (Monthly statutory cadence)",
                        String.format("STALE (%d days since reporting date %s)", age, freshness.holdingsEvidence().latestReportingDate()),
                        BigDecimal.valueOf(age),
                        "DAYS",
                        "SYSTEM_CANDIDATE_THRESHOLD",
                        "STALE",
                        freshness.holdingsEvidence().latestReportingDate(),
                        freshness.knowledgeCutoff() != null ? freshness.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                        ZonedDateTime.now(),
                        freshness.holdingsEvidence().sha256Hash(),
                        freshness.holdingsEvidence().latestReportingDate(),
                        freshness.evaluationDate(),
                        freshness.knowledgeCutoff() != null ? freshness.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                        freshness.holdingsEvidence().sha256Hash(),
                        null,
                        execution,
                        "V1",
                        "PHASE_2H_FROZEN",
                        "Holdings reporting date: " + freshness.holdingsEvidence().latestReportingDate(),
                        String.format("Disclosed portfolio holdings have not been updated for the latest statutory monthly reporting cycle (%d days since %s).",
                            age, freshness.holdingsEvidence().latestReportingDate()),
                        "SEBI Master Circular requires monthly portfolio disclosure within 10 calendar days of month close.",
                        "Holdings reporting date is strictly separated from availability cutoff.",
                        true,
                        "Disclosed holdings have not yet been refreshed by the AMC. Portfolio-construction metrics reflect the prior statutory filing.",
                        dedup,
                        null, null, null
                    );
                    if (created) generated++;
                    else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                }

                // Check Unclassified Exposure
                if (freshness.holdingsEvidence() != null && freshness.holdingsEvidence().unclassifiedExposurePercentage() != null
                    && freshness.holdingsEvidence().unclassifiedExposurePercentage() > 5.0) {
                    double unclassified = freshness.holdingsEvidence().unclassifiedExposurePercentage();
                    String dedup = computeDedupHash(investor.getId(), "FUND", option.getId(), "UNCLASSIFIED_EXPOSURE", freshness.evaluationDate());
                    boolean created = recordOrUpdateEvent(
                        investor, scheme, option, null,
                        "COVERAGE_CHANGE",
                        unclassified > 10.0 ? "MEDIUM" : "LOW",
                        "UNCLASSIFIED_EXPOSURE",
                        "Unclassified Holdings Exposure",
                        "<= 5.00%",
                        String.format("%.2f%% unclassified", unclassified),
                        BigDecimal.valueOf(unclassified),
                        "%",
                        "SYSTEM_CANDIDATE_THRESHOLD",
                        "DATA_QUALITY_LIMITED",
                        freshness.evaluationDate(),
                        freshness.knowledgeCutoff() != null ? freshness.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                        ZonedDateTime.now(),
                        null,
                        null,
                        freshness.evaluationDate(),
                        freshness.knowledgeCutoff() != null ? freshness.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                        null, null,
                        execution,
                        "V1", "PHASE_2H_FROZEN",
                        String.format("Disclosed positions count: %d", freshness.holdingsEvidence().positionCount()),
                        String.format("%.2f%% of disclosed fund assets are unclassified or lack verified security identifiers.", unclassified),
                        "Unclassified exposure exceeds 5.0% candidate due-diligence threshold.",
                        "Unclassified holdings are explicitly isolated; they are never imputed or guessed.",
                        true,
                        "Data coverage limitation: some underlying securities in the AMC monthly filing lack mapped master identifiers.",
                        dedup, null, null, null
                    );
                    if (created) generated++;
                    else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                }
            }
        } catch (Exception e) {
            log.warn("Monitoring freshness evaluation error for option ID {}: {}", option.getId(), e.getMessage());
            execution.setUnavailableEvidenceCount(execution.getUnavailableEvidenceCount() + 1);
        }

        // --- Domain B: Holdings & Portfolio-Construction Changes ---
        if (scheme != null && snapshotRepository != null && holdingRepository != null) {
            try {
                List<PortfolioSnapshot> snapshots = snapshotRepository.findBySchemeIdOrderByReportingDateDesc(scheme.getId());
                if (snapshots.size() >= 2) {
                    PortfolioSnapshot latestSnap = snapshots.get(0);
                    PortfolioSnapshot priorSnap = snapshots.get(1);

                    List<PortfolioHolding> latestHoldings = holdingRepository.findBySnapshotIdOrderByWeightPercentageDesc(latestSnap.getId());
                    List<PortfolioHolding> priorHoldings = holdingRepository.findBySnapshotIdOrderByWeightPercentageDesc(priorSnap.getId());

                    BigDecimal latestTop10 = latestHoldings.stream().limit(10)
                        .map(h -> h.getWeightPercentage() != null ? h.getWeightPercentage() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal priorTop10 = priorHoldings.stream().limit(10)
                        .map(h -> h.getWeightPercentage() != null ? h.getWeightPercentage() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal top10Diff = latestTop10.subtract(priorTop10).abs();
                    if (top10Diff.compareTo(BigDecimal.valueOf(3.0)) >= 0) {
                        String dedup = computeDedupHash(investor.getId(), "FUND", option.getId(), "PRT-01", latestSnap.getReportingDate());
                        boolean created = recordOrUpdateEvent(
                            investor, scheme, option, null,
                            "HOLDINGS_DISCLOSURE_CHANGE",
                            top10Diff.compareTo(BigDecimal.valueOf(5.0)) >= 0 ? "MEDIUM" : "LOW",
                            "PRT-01",
                            "Top 10 Equity Holdings Concentration (PRT-01)",
                            String.format("%.2f%%", priorTop10.doubleValue()),
                            String.format("%.2f%%", latestTop10.doubleValue()),
                            latestTop10.subtract(priorTop10),
                            "%",
                            "SYSTEM_CANDIDATE_THRESHOLD",
                            "AVAILABLE",
                            latestSnap.getReportingDate(),
                            latestSnap.getAvailabilityTime() != null ? latestSnap.getAvailabilityTime().toZonedDateTime() : ZonedDateTime.now(),
                            latestSnap.getCreatedAt() != null ? latestSnap.getCreatedAt().toZonedDateTime() : ZonedDateTime.now(),
                            latestSnap.getSourceArtifact() != null ? latestSnap.getSourceArtifact().getSha256() : null,
                            priorSnap.getReportingDate(),
                            latestSnap.getReportingDate(),
                            latestSnap.getAvailabilityTime() != null ? latestSnap.getAvailabilityTime().toZonedDateTime() : ZonedDateTime.now(),
                            latestSnap.getSourceArtifact() != null ? latestSnap.getSourceArtifact().getSha256() : null,
                            null,
                            execution,
                            "V1", "PHASE_2H_FROZEN",
                            String.format("Latest snapshot: %s (%d positions), Prior: %s (%d positions)",
                                latestSnap.getReportingDate(), latestHoldings.size(), priorSnap.getReportingDate(), priorHoldings.size()),
                            String.format("Top-10 holdings concentration changed by %s (from %.2f%% to %.2f%%) between disclosures.",
                                (latestTop10.compareTo(priorTop10) >= 0 ? "+" : "") + latestTop10.subtract(priorTop10).setScale(2, RoundingMode.HALF_UP) + "%",
                                priorTop10.doubleValue(), latestTop10.doubleValue()),
                            "Candidate materiality trigger: Top-10 concentration changed by >= 3.0% month-over-month.",
                            "Candidate threshold — not validated as an automated risk rating.",
                            false,
                            null,
                            dedup, null, null, null
                        );
                        if (created) generated++;
                        else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                    }
                }
            } catch (Exception e) {
                log.warn("Monitoring holdings change detection error for scheme {}: {}", scheme.getId(), e.getMessage());
            }
        }

        // --- Domain A: Quantitative Performance Changes (Authoritative Registry Aligned) ---
        if (calculationRunRepository != null && metricResultRepository != null) {
            try {
                List<CalculationRun> runs = calculationRunRepository.findBySchemeOptionIdOrderByAsOfDateDesc(option.getId());
                if (!runs.isEmpty()) {
                    CalculationRun latestRun = runs.get(0);
                    List<MetricResult> results = metricResultRepository.findByCalculationRunId(latestRun.getId());

                    for (MetricResult mr : results) {
                        String mCode = mr.getMetricCode();
                        BigDecimal numVal = mr.getNumericValue();
                        if (numVal == null) continue;

                        // 1. Annualized Volatility: RSK-01 (Stored as decimal, e.g. 0.20 = 20%)
                        if ("RSK-01".equalsIgnoreCase(mCode) && numVal.compareTo(BigDecimal.valueOf(0.20)) > 0) {
                            String dedup = computeDedupHash(investor.getId(), "FUND", option.getId(), "RSK-01_ELEVATED", latestRun.getAsOfDate());
                            boolean created = recordOrUpdateEvent(
                                investor, scheme, option, null,
                                "PERFORMANCE_CHANGE",
                                numVal.compareTo(BigDecimal.valueOf(0.25)) > 0 ? "HIGH" : "MEDIUM",
                                "RSK-01",
                                "Annualized Volatility (RSK-01)",
                                "<= 20.00%",
                                String.format("%.2f%%", numVal.doubleValue() * 100),
                                numVal.multiply(BigDecimal.valueOf(100)),
                                "%",
                                "SYSTEM_CANDIDATE_THRESHOLD",
                                "AVAILABLE",
                                latestRun.getAsOfDate(),
                                latestRun.getKnowledgeCutoffTime() != null ? latestRun.getKnowledgeCutoffTime().toZonedDateTime() : ZonedDateTime.now(),
                                latestRun.getCreatedAt() != null ? latestRun.getCreatedAt().toZonedDateTime() : ZonedDateTime.now(),
                                null,
                                null,
                                latestRun.getAsOfDate(),
                                latestRun.getKnowledgeCutoffTime() != null ? latestRun.getKnowledgeCutoffTime().toZonedDateTime() : ZonedDateTime.now(),
                                null,
                                latestRun.getId(),
                                execution,
                                "V1",
                                latestRun.getMethodologyVersion() != null ? latestRun.getMethodologyVersion().getVersionTag() : "PHASE_2H_FROZEN",
                                "Calculation run #" + latestRun.getId() + " as of " + latestRun.getAsOfDate(),
                                String.format("Annualized volatility (RSK-01) is elevated at %.2f%% as of %s.",
                                    numVal.doubleValue() * 100, latestRun.getAsOfDate()),
                                "Candidate materiality threshold: Annualized volatility exceeds 20.0% benchmark threshold.",
                                "Evaluated under deterministic mathematical kernel. High volatility describes historical dispersion, NOT future downside certainty.",
                                false,
                                null,
                                dedup, null, null, null
                            );
                            if (created) generated++;
                            else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                        }

                        // 2. Downside Semideviation: RSK-02 (Stored as decimal, e.g. 0.15 = 15%)
                        if ("RSK-02".equalsIgnoreCase(mCode) && numVal.compareTo(BigDecimal.valueOf(0.15)) > 0) {
                            String dedup = computeDedupHash(investor.getId(), "FUND", option.getId(), "RSK-02_ELEVATED", latestRun.getAsOfDate());
                            boolean created = recordOrUpdateEvent(
                                investor, scheme, option, null,
                                "PERFORMANCE_CHANGE",
                                numVal.compareTo(BigDecimal.valueOf(0.20)) > 0 ? "HIGH" : "MEDIUM",
                                "RSK-02",
                                "Downside Semideviation (RSK-02)",
                                "<= 15.00%",
                                String.format("%.2f%%", numVal.doubleValue() * 100),
                                numVal.multiply(BigDecimal.valueOf(100)),
                                "%",
                                "SYSTEM_CANDIDATE_THRESHOLD",
                                "AVAILABLE",
                                latestRun.getAsOfDate(),
                                latestRun.getKnowledgeCutoffTime() != null ? latestRun.getKnowledgeCutoffTime().toZonedDateTime() : ZonedDateTime.now(),
                                latestRun.getCreatedAt() != null ? latestRun.getCreatedAt().toZonedDateTime() : ZonedDateTime.now(),
                                null,
                                null,
                                latestRun.getAsOfDate(),
                                latestRun.getKnowledgeCutoffTime() != null ? latestRun.getKnowledgeCutoffTime().toZonedDateTime() : ZonedDateTime.now(),
                                null,
                                latestRun.getId(),
                                execution,
                                "V1",
                                latestRun.getMethodologyVersion() != null ? latestRun.getMethodologyVersion().getVersionTag() : "PHASE_2H_FROZEN",
                                "Calculation run #" + latestRun.getId() + " as of " + latestRun.getAsOfDate(),
                                String.format("Downside semideviation (RSK-02) is elevated at %.2f%% as of %s.",
                                    numVal.doubleValue() * 100, latestRun.getAsOfDate()),
                                "Candidate materiality threshold: Downside semideviation exceeds 15.0% risk hurdle.",
                                "Measures dispersion of negative returns strictly below MAR (0.0%). Candidate specification.",
                                false,
                                null,
                                dedup, null, null, null
                            );
                            if (created) generated++;
                            else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                        }

                        // 3. Maximum Drawdown: RSK-03 (Authoritative Code! Strictly negative decimal convention: loss is <= -0.15)
                        if ("RSK-03".equalsIgnoreCase(mCode) && numVal.compareTo(BigDecimal.valueOf(-0.15)) < 0) {
                            String dedup = computeDedupHash(investor.getId(), "FUND", option.getId(), "RSK-03_DRAWDOWN", latestRun.getAsOfDate());
                            boolean created = recordOrUpdateEvent(
                                investor, scheme, option, null,
                                "PERFORMANCE_CHANGE",
                                numVal.compareTo(BigDecimal.valueOf(-0.25)) < 0 ? "HIGH" : "MEDIUM",
                                "RSK-03",
                                "Maximum Drawdown (RSK-03)",
                                ">= -15.00%",
                                String.format("%.2f%%", numVal.doubleValue() * 100),
                                numVal.multiply(BigDecimal.valueOf(100)),
                                "%",
                                "SYSTEM_CANDIDATE_THRESHOLD",
                                "AVAILABLE",
                                latestRun.getAsOfDate(),
                                latestRun.getKnowledgeCutoffTime() != null ? latestRun.getKnowledgeCutoffTime().toZonedDateTime() : ZonedDateTime.now(),
                                latestRun.getCreatedAt() != null ? latestRun.getCreatedAt().toZonedDateTime() : ZonedDateTime.now(),
                                null,
                                null,
                                latestRun.getAsOfDate(),
                                latestRun.getKnowledgeCutoffTime() != null ? latestRun.getKnowledgeCutoffTime().toZonedDateTime() : ZonedDateTime.now(),
                                null,
                                latestRun.getId(),
                                execution,
                                "V1",
                                latestRun.getMethodologyVersion() != null ? latestRun.getMethodologyVersion().getVersionTag() : "PHASE_2H_FROZEN",
                                "Calculation run #" + latestRun.getId() + " as of " + latestRun.getAsOfDate(),
                                String.format("Historical peak-to-trough drawdown (RSK-03) is %.2f%% as of %s.",
                                    numVal.doubleValue() * 100, latestRun.getAsOfDate()),
                                "Candidate materiality threshold: Drawdown deepened beyond -15.0% peak-to-trough.",
                                "Drawdown is point-in-time empirical fact over the analyzed window. RSK-03 is candidate methodology.",
                                false,
                                null,
                                dedup, null, null, null
                            );
                            if (created) generated++;
                            else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                        }
                    }
                } else {
                    execution.setUnavailableEvidenceCount(execution.getUnavailableEvidenceCount() + 1);
                }
            } catch (Exception e) {
                log.warn("Monitoring performance evaluation error for option ID {}: {}", option.getId(), e.getMessage());
                execution.setUnavailableEvidenceCount(execution.getUnavailableEvidenceCount() + 1);
            }
        }

        return generated;
    }

    /**
     * Evaluates investor-level consolidated portfolio intelligence changes.
     */
    private int evaluatePortfolioLevelEvents(Investor investor, InvestorMonitoringExecution execution) {
        int generated = 0;
        if (portfolioIntelligenceService == null) return 0;

        try {
            InvestorPortfolioIntelligenceResponseDto intel = portfolioIntelligenceService.calculatePortfolioIntelligence(
                investor.getAuth0Subject(), null
            );

            if (intel != null) {
                // Check Unvalued Positions
                if (intel.valuationBasis() != null && intel.valuationBasis().unvaluedPositionsCount() > 0) {
                    int unvalued = intel.valuationBasis().unvaluedPositionsCount();
                    execution.setUnavailableEvidenceCount(execution.getUnavailableEvidenceCount() + unvalued);

                    String dedup = computeDedupHash(investor.getId(), "PORTFOLIO", 0L, "PORTFOLIO_UNVALUED_POSITIONS", intel.asOfDate());
                    boolean created = recordOrUpdateEvent(
                        investor, null, null, "CONSOLIDATED",
                        "DATA_QUALITY_CHANGE",
                        "MEDIUM",
                        "PORTFOLIO_UNVALUED_POSITIONS",
                        "Unvalued Portfolio Positions",
                        "0 unvalued positions",
                        String.format("%d unvalued positions", unvalued),
                        BigDecimal.valueOf(unvalued),
                        "COUNT",
                        "SYSTEM_CANDIDATE_THRESHOLD",
                        "DATA_QUALITY_LIMITED",
                        intel.asOfDate(),
                        intel.knowledgeCutoff() != null ? intel.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                        ZonedDateTime.now(),
                        null,
                        null,
                        intel.asOfDate(),
                        intel.knowledgeCutoff() != null ? intel.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                        null, null,
                        execution,
                        "V1", "PHASE_2H_FROZEN",
                        String.format("Valuation coverage: %.1f%% (%d of %d positions valued)",
                            intel.valuationBasis().valuationCoveragePercentage(),
                            intel.valuationBasis().knownValuedPositionsCount(),
                            intel.valuationBasis().totalPositionsCount()),
                        String.format("%d portfolio holdings lack an authoritative NAV observation as of %s. Valuation totals isolate these positions; they are NOT assumed to be zero.",
                            unvalued, intel.asOfDate()),
                        "Portfolio valuation completeness is partial when one or more holdings lack an effective NAV observation.",
                        "Valuation basis explicitly distinguishes between complete and partial coverage.",
                        true,
                        "Valuation gap reflects missing market data, NOT zero portfolio value.",
                        dedup, null, null, null
                    );
                    if (created) generated++;
                    else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                }

                // Check Single Stock Concentration (> 10%)
                if (intel.consolidatedSecurities() != null && !intel.consolidatedSecurities().isEmpty()) {
                    ConsolidatedPortfolioSecurityDto topSecurity = intel.consolidatedSecurities().get(0);
                    if (topSecurity.portfolioWeightPercentage() != null && topSecurity.portfolioWeightPercentage() > 10.0) {
                        String dedup = computeDedupHash(investor.getId(), "PORTFOLIO", 0L, "CONSOLIDATED_STOCK_CONCENTRATION_" + topSecurity.securityId(), intel.asOfDate());
                        boolean created = recordOrUpdateEvent(
                            investor, null, null, "CONSOLIDATED",
                            "PORTFOLIO_EXPOSURE_CHANGE",
                            topSecurity.portfolioWeightPercentage() > 15.0 ? "HIGH" : "MEDIUM",
                            "CONSOLIDATED_STOCK_CONCENTRATION",
                            "Consolidated Security Exposure: " + topSecurity.securityName(),
                            "<= 10.00%",
                            String.format("%.2f%%", topSecurity.portfolioWeightPercentage()),
                            BigDecimal.valueOf(topSecurity.portfolioWeightPercentage()),
                            "%",
                            "SYSTEM_CANDIDATE_THRESHOLD",
                            "AVAILABLE",
                            intel.asOfDate(),
                            intel.knowledgeCutoff() != null ? intel.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                            ZonedDateTime.now(),
                            null,
                            null,
                            intel.asOfDate(),
                            intel.knowledgeCutoff() != null ? intel.knowledgeCutoff().toZonedDateTime() : ZonedDateTime.now(),
                            null, null,
                            execution,
                            "V1", "PHASE_2H_FROZEN",
                            String.format("Held across %d portfolio funds. Direct stock weight: %.2f%%",
                                topSecurity.fundAppearancesCount(), topSecurity.portfolioWeightPercentage()),
                            String.format("Consolidated look-through exposure to %s is %.2f%% across %d underlying funds in your portfolio.",
                                topSecurity.securityName(), topSecurity.portfolioWeightPercentage(), topSecurity.fundAppearancesCount()),
                            "Candidate threshold: Consolidated exposure to a single company exceeds 10.0% of portfolio capital.",
                            "Calculated from disclosed mutual fund holdings weighted by actual investor capital.",
                            false,
                            null,
                            dedup, null, null, null
                        );
                        if (created) generated++;
                        else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                    }
                }
            }

            // Check Portfolio Drift vs Target Allocation
            if (investorPortfolioService != null) {
                PortfolioDriftAnalysisDto drift = investorPortfolioService.evaluatePortfolioDrift(investor.getAuth0Subject());
                if (drift != null && drift.isTargetConfigured() && drift.maxDriftPercentage() != null
                    && drift.maxDriftPercentage().compareTo(BigDecimal.valueOf(5.0)) >= 0) {
                    String dedup = computeDedupHash(investor.getId(), "PORTFOLIO", 0L, "PORTFOLIO_DRIFT", LocalDate.now());
                    boolean created = recordOrUpdateEvent(
                        investor, null, null, "PORTFOLIO",
                        "PORTFOLIO_EXPOSURE_CHANGE",
                        drift.maxDriftPercentage().compareTo(BigDecimal.valueOf(10.0)) >= 0 ? "HIGH" : "MEDIUM",
                        "PORTFOLIO_DRIFT",
                        "Portfolio Target Allocation Drift",
                        "<= 5.00%",
                        String.format("%.2f%% (Max drift in %s)", drift.maxDriftPercentage().doubleValue(), drift.maxDriftCategoryName()),
                        drift.maxDriftPercentage(),
                        "%",
                        "SYSTEM_CANDIDATE_THRESHOLD",
                        "AVAILABLE",
                        LocalDate.now(),
                        ZonedDateTime.now(),
                        ZonedDateTime.now(),
                        null,
                        null,
                        LocalDate.now(),
                        ZonedDateTime.now(),
                        null, null,
                        execution,
                        "V1", "PHASE_2H_FROZEN",
                        String.format("Total drift magnitude: %.2f%% across categories.", drift.totalDriftMagnitude().doubleValue()),
                        String.format("Actual portfolio allocation has drifted by %.2f%% from target weights (largest divergence in %s).",
                            drift.maxDriftPercentage().doubleValue(), drift.maxDriftCategoryName()),
                        "Target allocation drift exceeds 5.0% candidate re-examination band.",
                        "Drift analysis is informative; YUKIRA never generates automated rebalancing orders or trading advice.",
                        false,
                        null,
                        dedup, null, null, null
                    );
                    if (created) generated++;
                    else execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                }
            }
        } catch (Exception e) {
            log.warn("Monitoring portfolio-level evaluation error: {}", e.getMessage());
        }

        return generated;
    }

    /**
     * Evaluates investor-defined conditions and generates THESIS_CONDITION_TRIGGERED events.
     * Enforces metric unit normalization and explicit evidence states (REQUIRES_REVIEW, DATA_UNAVAILABLE, CLEAR, TRIGGERED).
     */
    private int evaluateInvestorConditions(Investor investor, InvestorMonitoringExecution execution) {
        List<InvestorMonitoringCondition> conditions = conditionRepository.findByInvestorAndIsEnabledTrue(investor);
        int evaluated = 0;
        ZonedDateTime now = ZonedDateTime.now();

        for (InvestorMonitoringCondition cond : conditions) {
            // Task 161: Due-Condition Selection
            if (cond.getNextEvaluationDueAt() != null && cond.getNextEvaluationDueAt().isAfter(now)) {
                continue;
            }

            evaluated++;
            String outcome = "NOT_EVALUATED";
            String triggerStatus = "CLEAR";
            String withholdingReason = null;

            // Calculate next evaluation due timestamp based on cadence (Task 161)
            String cadence = cond.getEvaluationCadence() != null ? cond.getEvaluationCadence().trim().toUpperCase() : "DAILY";
            ZonedDateTime nextDue = switch (cadence) {
                case "HOURLY" -> now.plusHours(1);
                case "WEEKLY" -> now.plusWeeks(1);
                case "MONTHLY" -> now.plusMonths(1);
                default -> now.plusDays(1);
            };
            cond.setNextEvaluationDueAt(nextDue);

            try {
                String fieldCode = cond.getEvidenceField() != null ? cond.getEvidenceField().trim().toUpperCase() : "";

                // Check compatibility
                if (!metricConfigService.isSupported(fieldCode) && !authoritativeRegistry.isRegistered(fieldCode)) {
                    cond.setEvaluationState("REQUIRES_REVIEW");
                    cond.setEvidenceState("METHODOLOGY_INCOMPATIBLE");
                    cond.setLastEvaluatedAt(now);
                    cond.setLastOutcome("INSUFFICIENT_EVIDENCE");
                    cond.setLastWithholdingReason("METHODOLOGY_INCOMPATIBLE");
                    cond.setLastFailedEvaluatedAt(now);
                    conditionRepository.save(cond);
                    execution.setUnavailableEvidenceCount(execution.getUnavailableEvidenceCount() + 1);

                    if (conditionHistoryRepository != null) {
                        InvestorMonitoringConditionHistory history = new InvestorMonitoringConditionHistory();
                        history.setCondition(cond);
                        history.setExecution(execution);
                        history.setInvestor(investor);
                        history.setEvaluatedAt(now);
                        history.setObservationCutoff(LocalDate.now());
                        history.setKnowledgeCutoff(now);
                        history.setEvaluationOutcome("INSUFFICIENT_EVIDENCE");
                        history.setTriggerStatus("WITHHELD");
                        history.setWithholdingReason("METHODOLOGY_INCOMPATIBLE");
                        history.setEvaluatedValue("Not available");
                        history.setThresholdValue(cond.getThresholdValue());
                        history.setSourceContext(cond.getTargetScope());
                        conditionHistoryRepository.save(history);
                    }
                    continue;
                }

                boolean triggered = false;
                String currentValStr = "Not available";
                BigDecimal currentValNum = null;
                String evidenceState = "MISSING";

                SchemeOption option = cond.getSchemeOption();
                if (option != null && calculationRunRepository != null && metricResultRepository != null) {
                    List<CalculationRun> runs = calculationRunRepository.findBySchemeOptionIdOrderByAsOfDateDesc(option.getId());
                    if (!runs.isEmpty()) {
                        CalculationRun latestRun = runs.get(0);
                        List<MetricResult> results = metricResultRepository.findByCalculationRunId(latestRun.getId());

                        String canonicalField = authoritativeRegistry.resolveCanonicalCode(fieldCode);
                        for (MetricResult mr : results) {
                            String mrCanonical = authoritativeRegistry.resolveCanonicalCode(mr.getMetricCode());
                            if (mrCanonical != null && mrCanonical.equalsIgnoreCase(canonicalField)) {
                                currentValNum = mr.getNumericValue();
                                evidenceState = "AVAILABLE";
                                currentValStr = metricConfigService.formatDisplayValue(canonicalField, currentValNum);
                                break;
                            }
                        }
                    }
                }

                // Operator evaluation
                String op = cond.getOperator();
                BigDecimal threshold = cond.getThresholdNumeric();

                if ("DATA_UNAVAILABLE".equalsIgnoreCase(op)) {
                    triggered = (currentValNum == null);
                    evidenceState = triggered ? "MISSING" : "AVAILABLE";
                } else if ("EVIDENCE_STALE".equalsIgnoreCase(op)) {
                    if (option != null) {
                        DataQualityFreshnessDto f = freshnessService.evaluateQualityAndFreshness(option.getId(), null, null);
                        triggered = f != null && f.navEvidence() != null && "STALE".equalsIgnoreCase(f.navEvidence().freshnessState());
                        evidenceState = triggered ? "STALE" : "AVAILABLE";
                        currentValStr = triggered ? "STALE" : "CURRENT";
                    }
                } else if (currentValNum != null && threshold != null) {
                    int cmp = currentValNum.compareTo(threshold);
                    switch (op) {
                        case "GT" -> triggered = (cmp > 0);
                        case "GTE" -> triggered = (cmp >= 0);
                        case "LT" -> triggered = (cmp < 0);
                        case "LTE" -> triggered = (cmp <= 0);
                        case "ABS_CHANGE_GT" -> triggered = (currentValNum.abs().compareTo(threshold) > 0);
                        default -> {}
                    }
                } else if (currentValNum == null) {
                    execution.setUnavailableEvidenceCount(execution.getUnavailableEvidenceCount() + 1);
                }

                cond.setEvidenceState(evidenceState);
                cond.setLastEvaluatedAt(now);
                cond.setLastEvaluatedValue(currentValStr);
                cond.setLastObservationDate(LocalDate.now());

                if (triggered) {
                    outcome = "TRIGGERED";
                    triggerStatus = "TRIGGERED";
                    cond.setEvaluationState("TRIGGERED");
                    cond.setTriggerCount(cond.getTriggerCount() != null ? cond.getTriggerCount() + 1 : 1);
                    cond.setLastTriggeredAt(now);
                    cond.setLastSuccessfulEvaluatedAt(now);

                    String dedup = computeDedupHash(investor.getId(), "THESIS_CONDITION", cond.getId(), fieldCode, LocalDate.now());
                    boolean created = recordOrUpdateEvent(
                        investor, cond.getScheme(), cond.getSchemeOption(), cond.getTargetScope(),
                        "THESIS_CONDITION_TRIGGERED",
                        "HIGH",
                        "THESIS_CONDITION_" + cond.getId(),
                        "Thesis Invalidation Rule: " + cond.getConditionName(),
                        cond.getOperator() + " " + cond.getThresholdValue(),
                        currentValStr,
                        currentValNum,
                        cond.getThresholdUnit() != null ? cond.getThresholdUnit() : "VALUE",
                        "INVESTOR_DEFINED_THRESHOLD",
                        evidenceState,
                        LocalDate.now(),
                        now,
                        now,
                        null,
                        null,
                        LocalDate.now(),
                        now,
                        null, null,
                        execution,
                        "V1", "PHASE_2H_FROZEN",
                        "Associated Research Thesis: " + (cond.getAssociatedThesisKey() != null ? cond.getAssociatedThesisKey() : "Not linked"),
                        String.format("Investor-defined reconsideration condition '%s' triggered. Current value is %s (Threshold: %s %s). %s",
                            cond.getConditionName(), currentValStr, cond.getOperator(), cond.getThresholdValue(),
                            cond.getThesisReconsiderationGuidance() != null ? cond.getThesisReconsiderationGuidance() : ""),
                        "Triggered condition indicates you should reassess your working thesis; it does not automatically invalidate your thesis.",
                        "User-defined rule executed deterministically against stored empirical observation.",
                        false,
                        null,
                        dedup,
                        cond.getAssociatedThesisKey(),
                        cond,
                        null
                    );
                    if (!created) {
                        execution.setEventsDeduplicatedCount(execution.getEventsDeduplicatedCount() + 1);
                    }
                } else if (currentValNum != null) {
                    outcome = "NOT_TRIGGERED";
                    triggerStatus = "CLEAR";
                    cond.setEvaluationState("CLEAR");
                    cond.setLastSuccessfulEvaluatedAt(now);
                } else if ("STALE".equalsIgnoreCase(evidenceState)) {
                    outcome = "STALE_SOURCE_DATA";
                    triggerStatus = "WITHHELD";
                    withholdingReason = "NAV_OBSERVATION_STALE";
                    cond.setEvaluationState("DATA_UNAVAILABLE");
                    cond.setLastFailedEvaluatedAt(now);
                } else {
                    outcome = "MISSING_SOURCE_OBSERVATIONS";
                    triggerStatus = "WITHHELD";
                    withholdingReason = "METRIC_OBSERVATIONS_MISSING";
                    cond.setEvaluationState("DATA_UNAVAILABLE");
                    cond.setLastFailedEvaluatedAt(now);
                }

                cond.setLastOutcome(outcome);
                cond.setLastWithholdingReason(withholdingReason);
                conditionRepository.save(cond);

                if (conditionHistoryRepository != null) {
                    InvestorMonitoringConditionHistory history = new InvestorMonitoringConditionHistory();
                    history.setCondition(cond);
                    history.setExecution(execution);
                    history.setInvestor(investor);
                    history.setEvaluatedAt(now);
                    history.setObservationCutoff(LocalDate.now());
                    history.setKnowledgeCutoff(now);
                    history.setEvaluationOutcome(outcome);
                    history.setTriggerStatus(triggerStatus);
                    history.setWithholdingReason(withholdingReason);
                    history.setEvaluatedValue(currentValStr);
                    history.setThresholdValue(cond.getThresholdValue());
                    history.setSourceContext(option != null ? option.getIsin() : cond.getTargetScope());
                    conditionHistoryRepository.save(history);
                }
            } catch (Exception e) {
                log.warn("Error evaluating condition ID {}: {}", cond.getId(), e.getMessage());
                cond.setLastOutcome("EVALUATION_FAILURE");
                cond.setLastWithholdingReason("EXECUTION_EXCEPTION");
                cond.setLastFailedEvaluatedAt(now);
                conditionRepository.save(cond);
                execution.setUnavailableEvidenceCount(execution.getUnavailableEvidenceCount() + 1);
            }
        }

        return evaluated;
    }

    /**
     * Records a new monitoring event or updates an existing active event if dedup matches.
     */
    private boolean recordOrUpdateEvent(
        Investor investor,
        Scheme scheme,
        SchemeOption schemeOption,
        String portfolioScope,
        String classification,
        String severity,
        String metricOrEvidenceId,
        String metricName,
        String previousValue,
        String currentValue,
        BigDecimal changeMagnitude,
        String unitOrFormat,
        String thresholdClassification,
        String evidenceState,
        LocalDate effectiveDate,
        ZonedDateTime availabilityTime,
        ZonedDateTime ingestionTimestamp,
        String sourceRevision,
        LocalDate previousObsDate,
        LocalDate currentObsDate,
        ZonedDateTime knowledgeCutoff,
        String sourceReference,
        Long calculationRunId,
        InvestorMonitoringExecution execution,
        String metricRegistryVersion,
        String methodologyVersion,
        String evidenceCoverageInfo,
        String explanation,
        String triggerRationale,
        String knownLimitations,
        Boolean isDataArtifact,
        String artifactExplanation,
        String dedupHash,
        String associatedThesisKey,
        InvestorMonitoringCondition associatedCondition,
        String associatedFollowUpId
    ) {
        Optional<InvestorMonitoringEvent> existingOpt = eventRepository.findByInvestorAndDedupHash(investor, dedupHash);
        if (existingOpt.isPresent()) {
            InvestorMonitoringEvent existing = existingOpt.get();
            // If already resolved or dismissed, do not overwrite unless observation date is genuinely newer
            if ("RESOLVED".equals(existing.getLifecycleStatus()) || "DISMISSED".equals(existing.getLifecycleStatus())) {
                if (currentObsDate != null && existing.getCurrentObservationDate() != null
                    && currentObsDate.isAfter(existing.getCurrentObservationDate())) {
                    // Subsequent cycle breach after resolution: re-open as NEW with updated values
                    existing.setLifecycleStatus("NEW");
                    existing.setCurrentValue(currentValue);
                    existing.setChangeMagnitude(changeMagnitude);
                    existing.setCurrentObservationDate(currentObsDate);
                    existing.setKnowledgeCutoff(knowledgeCutoff);
                    existing.setExecution(execution);
                    existing.setUpdatedAt(ZonedDateTime.now());
                    eventRepository.save(existing);
                    return true;
                }
                return false;
            }
            // Update active event
            existing.setCurrentValue(currentValue);
            existing.setChangeMagnitude(changeMagnitude);
            existing.setKnowledgeCutoff(knowledgeCutoff);
            existing.setExecution(execution);
            existing.setEvidenceState(evidenceState);
            existing.setUpdatedAt(ZonedDateTime.now());
            eventRepository.save(existing);
            return false;
        }

        InvestorMonitoringEvent event = new InvestorMonitoringEvent();
        event.setInvestor(investor);
        event.setScheme(scheme);
        event.setSchemeOption(schemeOption);
        event.setPortfolioScope(portfolioScope);
        event.setEventClassification(classification);
        event.setSeverity(severity);
        event.setLifecycleStatus("NEW");
        event.setMetricOrEvidenceId(metricOrEvidenceId);
        event.setMetricName(metricName);
        event.setPreviousValue(previousValue);
        event.setCurrentValue(currentValue);
        event.setChangeMagnitude(changeMagnitude);
        event.setUnitOrFormat(unitOrFormat);
        event.setThresholdClassification(thresholdClassification != null ? thresholdClassification : "SYSTEM_CANDIDATE_THRESHOLD");
        event.setEvidenceState(evidenceState != null ? evidenceState : "AVAILABLE");
        event.setEffectiveDate(effectiveDate);
        event.setAvailabilityTime(availabilityTime);
        event.setIngestionTimestamp(ingestionTimestamp);
        event.setSourceRevision(sourceRevision);
        event.setPreviousObservationDate(previousObsDate);
        event.setCurrentObservationDate(currentObsDate);
        event.setKnowledgeCutoff(knowledgeCutoff);
        event.setSourceReference(sourceReference);

        if (calculationRunId != null && calculationRunRepository != null) {
            calculationRunRepository.findById(calculationRunId).ifPresent(event::setCalculationRun);
        }
        event.setExecution(execution);

        event.setMetricRegistryVersion(metricRegistryVersion);
        event.setMethodologyVersion(methodologyVersion);
        event.setEvidenceCoverageInfo(evidenceCoverageInfo);
        event.setExplanation(explanation);
        event.setTriggerRationale(triggerRationale);
        event.setKnownLimitations(knownLimitations);
        event.setIsDataArtifact(isDataArtifact != null ? isDataArtifact : false);
        event.setArtifactExplanation(artifactExplanation);
        event.setDedupHash(dedupHash);
        event.setAssociatedThesisKey(associatedThesisKey);
        event.setAssociatedCondition(associatedCondition);
        event.setAssociatedFollowUpId(associatedFollowUpId);

        InvestorMonitoringEvent savedEvent = eventRepository.save(event);
        if (notificationDeliveryService != null) {
            try {
                notificationDeliveryService.enqueueNotificationsForEvent(savedEvent);
            } catch (Exception ne) {
                log.warn("Failed to enqueue notifications for event ID {}: {}", savedEvent.getId(), ne.getMessage());
            }
        }
        return true;
    }

    /**
     * Generates a deterministic or AI-grounded explanation for an event.
     */
    @Transactional(readOnly = true)
    public MonitoringAiExplanationDto getEventAiExplanation(Long eventId, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorMonitoringEvent event = eventRepository.findByIdAndInvestor(eventId, investor)
            .orElseThrow(() -> new IllegalArgumentException("Monitoring event not found for ID: " + eventId));

        // Deterministic fallback synthesis (guaranteed, non-advisory, institutional)
        String plainLanguage;
        String historicalSummary;
        List<String> implications = new ArrayList<>();
        List<String> caveats = new ArrayList<>();
        String missingInfo;
        List<String> questions = new ArrayList<>();

        if (Boolean.TRUE.equals(event.getIsDataArtifact())) {
            plainLanguage = String.format("This event represents a data disclosure or pipeline timing issue (%s: %s). It does not reflect a change in the fund's actual market portfolio or trading performance.",
                event.getMetricName(), event.getCurrentValue());
            historicalSummary = "Observed status: " + event.getExplanation();
            implications.add("Analytical metrics depending on this data source may reflect prior historical periods until refreshed.");
            implications.add("Do not infer negative managerial skill or fund deterioration from a source reporting lag.");
            caveats.add("AMFI and AMC disclosure timings vary during holidays, weekends, and quarter-ends.");
            caveats.add(event.getArtifactExplanation() != null ? event.getArtifactExplanation() : "Data quality limitation.");
            missingInfo = "Awaiting subsequent statutory reporting cycle or pipeline synchronization.";
            questions.add("Has the AMC published its latest statutory portfolio report on its official investor portal?");
            questions.add("Are peers in the same AMFI category exhibiting identical disclosure freshness?");
            questions.add("Does this reporting delay impact your near-term allocation reassessment window?");
        } else if ("THESIS_CONDITION_TRIGGERED".equals(event.getEventClassification())) {
            plainLanguage = String.format("An investor-authored thesis invalidation condition has triggered: %s. Current evidence is %s, exceeding your threshold of %s.",
                event.getMetricName(), event.getCurrentValue(), event.getPreviousValue());
            historicalSummary = "Trigger rationale: " + event.getTriggerRationale();
            implications.add("Your active working hypothesis for this fund requires systematic review against the new evidence.");
            implications.add("A triggered condition indicates reconsideration; it does not automatically invalidate your thesis.");
            caveats.add("Verify whether the change was driven by market-wide regime shift or idiosyncratic fund management.");
            caveats.add("Check whether holdings concentration or style drift accompanied the metric change.");
            missingInfo = event.getKnownLimitations() != null ? event.getKnownLimitations() : "No methodology version discrepancies noted.";
            questions.add("Does the underlying economic rationale for your original thesis still hold despite this metric breach?");
            questions.add("Is this drift temporary or does it reflect structural changes in portfolio management?");
            questions.add("Should you log an investigation follow-up issue in your Research Workspace?");
        } else {
            plainLanguage = String.format("A material change was detected in %s: shifted from %s to %s.",
                event.getMetricName(),
                event.getPreviousValue() != null ? event.getPreviousValue() : "Baseline",
                event.getCurrentValue());
            historicalSummary = "Observation details: " + event.getExplanation();
            implications.add("The observed metric change exceeds the empirical candidate materiality threshold.");
            implications.add("Review underlying security disclosures to verify what drove the change.");
            caveats.add("Candidate thresholds are established for evidence-tracking, not validated investment rules.");
            caveats.add("Past returns and historical volatility do not predict future fund performance.");
            missingInfo = "Evaluated under authoritative metric registry " + event.getMetricRegistryVersion() + ".";
            questions.add("Did the fund manager alter top sector allocations or cash drag during this period?");
            questions.add("How does this performance or risk shift compare to the fund's assigned category benchmark?");
            questions.add("Does this change affect your existing portfolio asset-allocation targets?");
        }

        return new MonitoringAiExplanationDto(
            event.getId(),
            event.getEventClassification(),
            event.getMetricName(),
            plainLanguage,
            historicalSummary,
            implications,
            caveats,
            missingInfo,
            questions,
            false, // Deterministic institutional synthesis engine
            "YUKIRA-Deterministic-Explanation-Engine-V1",
            ZonedDateTime.now(),
            "Non-advisory due-diligence analysis. Calculations are deterministic and backend-authoritative. YUKIRA does not provide financial advice or investment recommendations."
        );
    }

    /**
     * Retrieves the latest monitoring execution record for an investor.
     */
    @Transactional(readOnly = true)
    public Optional<MonitoringExecutionDto> getLatestExecution(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return executionRepository.findTopByInvestorOrderByStartedAtDesc(investor).map(this::mapToExecutionDto);
    }

    /**
     * Retrieves paginated execution history for an investor.
     */
    @Transactional(readOnly = true)
    public Page<MonitoringExecutionDto> getExecutionHistory(String auth0Subject, int page, int size) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)));
        return executionRepository.findByInvestorOrderByStartedAtDesc(investor, pageable).map(this::mapToExecutionDto);
    }

    /**
     * Retrieves all centralized metric configs and supported operators for monitoring.
     */
    public List<MonitoringMetricConfigDto> getSupportedMetrics() {
        return metricConfigService.getAllConfigs();
    }

    /**
     * Retrieves all events for a specific fund scheme.
     */
    @Transactional(readOnly = true)
    public List<MonitoringEventDto> getFundEvents(Long schemeId, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return eventRepository.findByInvestorAndSchemeIdOrderByCreatedAtDesc(investor, schemeId).stream()
            .map(this::mapToEventDto)
            .collect(Collectors.toList());
    }

    /**
     * Retrieves all portfolio-level monitoring events.
     */
    @Transactional(readOnly = true)
    public List<MonitoringEventDto> getPortfolioEvents(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return eventRepository.findByInvestorAndPortfolioScopeIsNotNullOrderByCreatedAtDesc(investor).stream()
            .map(this::mapToEventDto)
            .collect(Collectors.toList());
    }

    private MonitoringExecutionDto mapToExecutionDto(InvestorMonitoringExecution exec) {
        return new MonitoringExecutionDto(
            exec.getId(),
            exec.getInvestor() != null ? exec.getInvestor().getId() : null,
            exec.getExecutionTrigger(),
            exec.getExecutionStatus(),
            exec.getStartedAt(),
            exec.getCompletedAt(),
            exec.getKnowledgeCutoff(),
            exec.getFundsEvaluatedCount(),
            exec.getPortfolioScopesEvaluatedCount(),
            exec.getConditionsEvaluatedCount(),
            exec.getEventsGeneratedCount(),
            exec.getEventsDeduplicatedCount(),
            exec.getUnavailableEvidenceCount(),
            exec.getStaleSourceCount(),
            exec.getErrorMessage(),
            exec.getSummaryNotes(),
            exec.getCreatedAt()
        );
    }

    private MonitoringEventDto mapToEventDto(InvestorMonitoringEvent e) {
        Scheme s = e.getScheme();
        SchemeOption opt = e.getSchemeOption();

        String code = opt != null ? opt.getAmfiCode() : (s != null ? s.getCode() : "N/A");
        String name = opt != null && opt.getPlan() != null && opt.getPlan().getScheme() != null
            ? opt.getPlan().getScheme().getName()
            : (s != null ? s.getName() : (e.getPortfolioScope() != null ? "Consolidated Portfolio" : "Fund"));
        String planType = opt != null && opt.getPlan() != null ? opt.getPlan().getPlanType() : null;
        String optionType = opt != null ? opt.getOptionType() : null;

        List<MonitoringEventNoteDto> notes = e.getNotes() != null
            ? e.getNotes().stream()
                .map(n -> new MonitoringEventNoteDto(n.getId(), e.getId(), n.getNoteText(), n.getAuthor(), n.getCreatedAt()))
                .collect(Collectors.toList())
            : Collections.emptyList();

        return new MonitoringEventDto(
            e.getId(),
            e.getInvestor() != null ? e.getInvestor().getId() : null,
            s != null ? s.getId() : null,
            opt != null ? opt.getId() : null,
            code,
            name,
            planType,
            optionType,
            e.getPortfolioScope(),
            e.getEventClassification(),
            e.getSeverity(),
            e.getLifecycleStatus(),
            e.getMetricOrEvidenceId(),
            e.getMetricName(),
            e.getPreviousValue(),
            e.getCurrentValue(),
            e.getChangeMagnitude(),
            e.getUnitOrFormat(),
            e.getThresholdClassification(),
            e.getEvidenceState(),
            e.getEffectiveDate(),
            e.getAvailabilityTime(),
            e.getIngestionTimestamp(),
            e.getSourceRevision(),
            e.getPreviousObservationDate(),
            e.getCurrentObservationDate(),
            e.getKnowledgeCutoff(),
            e.getSourceReference(),
            e.getCalculationRun() != null ? e.getCalculationRun().getId() : null,
            e.getExecution() != null ? e.getExecution().getId() : null,
            e.getMetricRegistryVersion(),
            e.getMethodologyVersion(),
            e.getEvidenceCoverageInfo(),
            e.getExplanation(),
            e.getTriggerRationale(),
            e.getKnownLimitations(),
            e.getIsDataArtifact(),
            e.getArtifactExplanation(),
            e.getDedupHash(),
            e.getAssociatedThesisKey(),
            e.getAssociatedCondition() != null ? e.getAssociatedCondition().getId() : null,
            e.getAssociatedFollowUpId(),
            e.getInvestigationNotes(),
            e.getAcknowledgedAt(),
            e.getInvestigatingAt(),
            e.getResolvedAt(),
            e.getDismissedAt(),
            e.getResolutionNote(),
            e.getCreatedAt(),
            e.getUpdatedAt(),
            notes
        );
    }

    private MonitoringConditionDto mapToConditionDto(InvestorMonitoringCondition c) {
        Scheme s = c.getScheme();
        SchemeOption opt = c.getSchemeOption();
        String code = opt != null ? opt.getAmfiCode() : (s != null ? s.getCode() : "PORTFOLIO");
        String name = opt != null && opt.getPlan() != null && opt.getPlan().getScheme() != null
            ? opt.getPlan().getScheme().getName()
            : (s != null ? s.getName() : "Consolidated Portfolio");

        return new MonitoringConditionDto(
            c.getId(),
            c.getInvestor() != null ? c.getInvestor().getId() : null,
            s != null ? s.getId() : null,
            opt != null ? opt.getId() : null,
            code,
            name,
            c.getTargetScope(),
            c.getConditionName(),
            c.getEvidenceField(),
            c.getOperator(),
            c.getThresholdValue(),
            c.getThresholdNumeric(),
            c.getThresholdUnit(),
            c.getThresholdClassification(),
            c.getTimeWindow(),
            c.getAssociatedThesisKey(),
            c.getThesisReconsiderationGuidance(),
            c.getIsEnabled(),
            c.getEvaluationState(),
            c.getEvidenceState(),
            c.getLastEvaluatedValue(),
            c.getLastEvaluatedAt(),
            c.getLastObservationDate(),
            c.getTriggerCount(),
            c.getLastTriggeredAt(),
            c.getCreatedAt(),
            c.getUpdatedAt(),
            c.getPortfolioId(),
            c.getStressFindingId(),
            c.getConditionStatus() != null ? c.getConditionStatus() : "ACTIVE",
            c.getOriginatingFindingId() != null ? c.getOriginatingFindingId() : c.getStressFindingId()
        );
    }

    private String computeDedupHash(Long investorId, String scope, Long targetId, String metricId, LocalDate obsDate) {
        try {
            String raw = String.format("%d:%s:%d:%s:%s",
                investorId != null ? investorId : 0L,
                scope != null ? scope : "FUND",
                targetId != null ? targetId : 0L,
                metricId != null ? metricId : "METRIC",
                obsDate != null ? obsDate.toString() : "DATE"
            );
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 32);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        }
    }

    @Transactional(readOnly = true)
    public List<MonitoringConditionHistoryDto> getConditionHistory(Long conditionId, String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return conditionRepository.findByIdAndInvestor(conditionId, investor)
            .map(cond -> {
                if (conditionHistoryRepository == null) return List.<MonitoringConditionHistoryDto>of();
                return conditionHistoryRepository.findByConditionOrderByEvaluatedAtDesc(cond).stream()
                    .map(h -> new MonitoringConditionHistoryDto(
                        h.getId(),
                        h.getCondition() != null ? h.getCondition().getId() : null,
                        h.getExecution() != null ? h.getExecution().getId() : null,
                        h.getEvaluatedAt(),
                        h.getObservationCutoff(),
                        h.getKnowledgeCutoff(),
                        h.getEvaluationOutcome(),
                        h.getTriggerStatus(),
                        h.getWithholdingReason(),
                        h.getEvaluatedValue(),
                        h.getThresholdValue(),
                        h.getSourceContext(),
                        h.getEvent() != null ? h.getEvent().getId() : null
                    )).collect(Collectors.toList());
            }).orElseGet(List::of);
    }
}
