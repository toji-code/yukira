package com.yukira.backend.universe;

import com.yukira.backend.benchmark.CategoryBenchmarkMapping;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.ingestion.amfi.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
import com.yukira.backend.service.CalculationOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Institutional Reference-Universe Historical NAV Recovery Service (Phase A1).
 *
 * Implements bounded, reusable recovery capabilities across the 193 Direct-Growth reference options:
 * 1. Stored-Artifact Recovery:
 *    Recovers options (starting with the 66 candidates at 556 observations) whose missing 2026 data
 *    is covered by stored authentic AMFI universe artifacts #5378–#5381.
 *    Reuses artifact caching and targeted multi-scheme AMFI parsing to avoid loading and parsing 70MB payloads repeatedly.
 * 2. Targeted Interval Recovery:
 *    Derives missing historical intervals from empirical observations and retrieves only missing date ranges
 *    for options with all-time history but 3Y gaps (37 candidates) and older historical discontinuities (12 candidates).
 * 3. Preserves authentic source lineage, point-in-time metadata, and the frozen 700-day sufficiency threshold.
 * 4. Zero synthetic, fabricated, interpolated, or forward-filled observations.
 */
@Service
public class ReferenceUniverseNavRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceUniverseNavRecoveryService.class);

    public static final LocalDate DEFAULT_WINDOW_START = LocalDate.of(2023, 10, 1);
    public static final LocalDate DEFAULT_WINDOW_END = LocalDate.of(2026, 10, 1);
    public static final int SUFFICIENCY_THRESHOLD = 700;
    private static final DateTimeFormatter AMFI_URL_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    public static final List<String> CANONICAL_METRICS = List.of(
        "RET-02", "RET-03", "RSK-01", "RSK-03", "MKT-01", "MKT-02", "RAT-04", "REL-01"
    );
    public static final String CANONICAL_METHODOLOGY_TAG = "APPROVED_M2N";

    // 2026 Stored Universe Chunk Artifact Fragments (covered by artifacts #5378–#5381)
    private static final List<String> STORED_2026_CHUNK_FRAGMENTS = List.of(
        "frmdt=19-Dec-2025&todt=18-Mar-2026",
        "frmdt=19-Mar-2026&todt=16-Jun-2026",
        "frmdt=17-Jun-2026&todt=14-Sep-2026",
        "frmdt=15-Sep-2026&todt=01-Oct-2026"
    );

    // In-memory cache for stored universe chunk artifacts to avoid repeated bytea loading from DB
    private final Map<String, SourceArtifact> storedChunkCache = new ConcurrentHashMap<>();

    private final ReferenceUniverseService referenceUniverseService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final AmfiNavIngestionService amfiNavIngestionService;
    private final AmfiSourceClient amfiSourceClient;
    private final HistorySufficiencyService historySufficiencyService;
    private final BenchmarkResolutionService benchmarkResolutionService;
    private final CalculationOrchestratorService calculationOrchestratorService;

    @Autowired
    public ReferenceUniverseNavRecoveryService(
        ReferenceUniverseService referenceUniverseService,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        SourceArtifactRepository sourceArtifactRepository,
        AmfiNavIngestionService amfiNavIngestionService,
        AmfiSourceClient amfiSourceClient,
        HistorySufficiencyService historySufficiencyService,
        BenchmarkResolutionService benchmarkResolutionService,
        CalculationOrchestratorService calculationOrchestratorService
    ) {
        this.referenceUniverseService = referenceUniverseService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.amfiNavIngestionService = amfiNavIngestionService;
        this.amfiSourceClient = amfiSourceClient;
        this.historySufficiencyService = historySufficiencyService;
        this.benchmarkResolutionService = benchmarkResolutionService;
        this.calculationOrchestratorService = calculationOrchestratorService;
    }

    /**
     * Inspects and classifies all reference-universe scheme options against the strict 3Y evaluation window.
     */
    @Transactional(readOnly = true)
    public ReferenceUniverseRecoveryInventoryDto getInventory(LocalDate windowStart, LocalDate windowEnd) {
        final LocalDate effectiveStart = windowStart != null ? windowStart : DEFAULT_WINDOW_START;
        final LocalDate effectiveEnd = windowEnd != null ? windowEnd : DEFAULT_WINDOW_END;

        List<SchemeOption> refOptions = referenceUniverseService.getEligibleReferenceUniverseOptions();

        // 1. Query distinct 3Y valid observation counts
        List<Object[]> window3yRows = navObservationRepository.findDistinctValidNavSummariesInDateRange(effectiveStart, effectiveEnd);
        Map<Long, Long> window3yCountMap = new HashMap<>();
        for (Object[] row : window3yRows) {
            if (row != null && row[0] != null && row[3] != null) {
                window3yCountMap.put(((Number) row[0]).longValue(), ((Number) row[3]).longValue());
            }
        }

        // 2. Query all-time observation summaries
        List<Object[]> allTimeRows = navObservationRepository.findDistinctValidNavSummaries();
        Map<Long, Object[]> allTimeMap = new HashMap<>();
        for (Object[] row : allTimeRows) {
            if (row != null && row[0] != null) {
                allTimeMap.put(((Number) row[0]).longValue(), row);
            }
        }

        int sufficientCount = 0;
        int partialCount = 0;
        int storedArtifactReadyCount = 0;
        int inceptionBoundedCount = 0;
        int historicalGap3YCount = 0;
        int olderDiscontinuityCount = 0;

        Map<String, Integer> categoryBreakdown = new LinkedHashMap<>();
        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            categoryBreakdown.put(cat.name(), 0);
        }

        List<NavRecoveryCandidateDto> candidateDtos = new ArrayList<>();

        for (SchemeOption option : refOptions) {
            Long optId = option.getId();
            String amfiCode = option.getAmfiCode();
            String isin = option.getIsin();
            Scheme scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;
            String schemeName = scheme != null ? scheme.getName() : "Unknown Scheme";
            String amcName = (scheme != null && scheme.getAmc() != null) ? scheme.getAmc().getLegalName() : "Unknown AMC";
            EquityUniverseCategory category = referenceUniverseService.evaluateOption(option).category();
            LocalDate inceptionDate = scheme != null ? scheme.getInceptionDate() : null;

            long count3Y = window3yCountMap.getOrDefault(optId, 0L);

            Object[] allTimeRow = allTimeMap.get(optId);
            long countAll = (allTimeRow != null && allTimeRow[3] != null) ? ((Number) allTimeRow[3]).longValue() : 0L;
            LocalDate earliestDate = (allTimeRow != null && allTimeRow[1] != null) ? (LocalDate) allTimeRow[1] : null;
            LocalDate latestDate = (allTimeRow != null && allTimeRow[2] != null) ? (LocalDate) allTimeRow[2] : null;

            HistoricalNavRecoveryCategory classification;

            if (count3Y >= SUFFICIENCY_THRESHOLD) {
                classification = HistoricalNavRecoveryCategory.SUFFICIENT_3Y;
                sufficientCount++;
            } else {
                partialCount++;
                if (count3Y == 556) {
                    classification = HistoricalNavRecoveryCategory.STORED_ARTIFACT_READY;
                    storedArtifactReadyCount++;
                } else if (earliestDate != null && earliestDate.isAfter(effectiveStart)) {
                    classification = HistoricalNavRecoveryCategory.INCEPTION_BOUNDED;
                    inceptionBoundedCount++;
                } else if (countAll >= SUFFICIENCY_THRESHOLD) {
                    classification = HistoricalNavRecoveryCategory.HISTORICAL_GAP_3Y;
                    historicalGap3YCount++;
                } else {
                    classification = HistoricalNavRecoveryCategory.OLDER_DISCONTINUITY;
                    olderDiscontinuityCount++;
                }
            }

            if (category != null) {
                categoryBreakdown.put(category.name(), categoryBreakdown.getOrDefault(category.name(), 0) + 1);
            }

            List<HistoricalNavGapInterval> missingIntervals = deriveMissingIntervals(
                optId, effectiveStart, effectiveEnd, earliestDate
            );

            candidateDtos.add(new NavRecoveryCandidateDto(
                optId,
                amfiCode,
                isin,
                schemeName,
                amcName,
                category,
                count3Y,
                countAll,
                earliestDate,
                latestDate,
                inceptionDate,
                classification,
                missingIntervals
            ));
        }

        return new ReferenceUniverseRecoveryInventoryDto(
            effectiveStart,
            effectiveEnd,
            SUFFICIENCY_THRESHOLD,
            refOptions.size(),
            sufficientCount,
            partialCount,
            storedArtifactReadyCount,
            inceptionBoundedCount,
            historicalGap3YCount,
            olderDiscontinuityCount,
            categoryBreakdown,
            candidateDtos
        );
    }

    /**
     * Derives exact missing date intervals for an option within an evaluation window.
     * Respects inception boundaries: funds whose first market appearance is after windowStart
     * are not marked with artificial gaps prior to their first observation.
     */
    public List<HistoricalNavGapInterval> deriveMissingIntervals(
        Long schemeOptionId,
        LocalDate windowStart,
        LocalDate windowEnd,
        LocalDate earliestKnownObservation
    ) {
        if (schemeOptionId == null) {
            return List.of();
        }

        LocalDate effectiveStart = windowStart;
        if (earliestKnownObservation != null && earliestKnownObservation.isAfter(windowStart)) {
            effectiveStart = earliestKnownObservation;
        }

        List<NavObservation> obsList = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(schemeOptionId, effectiveStart, windowEnd)
            .stream()
            .filter(o -> Boolean.TRUE.equals(o.getLatestRevision()))
            .toList();

        if (obsList.isEmpty()) {
            return List.of(HistoricalNavGapInterval.of(effectiveStart, windowEnd, "FULL_WINDOW_MISSING"));
        }

        List<HistoricalNavGapInterval> gaps = new ArrayList<>();

        // 1. Leading gap before first observation
        LocalDate firstObsDate = obsList.getFirst().getEffectiveDate();
        if (ChronoUnit.DAYS.between(effectiveStart, firstObsDate) > 4) {
            gaps.add(HistoricalNavGapInterval.of(effectiveStart, firstObsDate.minusDays(1), "LEADING_GAP"));
        }

        // 2. Intermediate gaps between observations
        for (int i = 0; i < obsList.size() - 1; i++) {
            LocalDate curr = obsList.get(i).getEffectiveDate();
            LocalDate next = obsList.get(i + 1).getEffectiveDate();
            long dayDiff = ChronoUnit.DAYS.between(curr, next);

            // Gaps of 5 or more calendar days contain missing business days (Friday to Monday is 3 days)
            if (dayDiff > 4) {
                gaps.add(HistoricalNavGapInterval.of(curr.plusDays(1), next.minusDays(1), "INTERMEDIATE_GAP"));
            }
        }

        // 3. Trailing gap after last observation
        LocalDate lastObsDate = obsList.getLast().getEffectiveDate();
        if (ChronoUnit.DAYS.between(lastObsDate, windowEnd) > 3) {
            gaps.add(HistoricalNavGapInterval.of(lastObsDate.plusDays(1), windowEnd, "TRAILING_GAP"));
        }

        return gaps;
    }

    /**
     * Executes bounded stored-artifact recovery for eligible reference universe options.
     *
     * Invariants:
     * 1. Reuses stored authentic AMFI universe artifacts #5378–#5381.
     * 2. Ingests each artifact in a single streaming pass for all targeted AMFI codes in the batch,
     *    avoiding loading and parsing 70MB payloads repeatedly.
     * 3. Idempotently skips already-persisted identical observations.
     * 4. Excludes options already at >= 700 threshold.
     * 5. Connects to calculation orchestrator for newly sufficient options if requested.
     */
    @Transactional
    public NavRecoveryBatchExecutionResultDto executeStoredArtifactRecovery(StoredArtifactRecoveryRequest request) {
        long startTime = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        String runId = "STORED-REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        final StoredArtifactRecoveryRequest effectiveReq = (request != null)
            ? request : StoredArtifactRecoveryRequest.ofDefault();

        LocalDate asOfDate = effectiveReq.effectiveAsOfDate();
        LocalDate windowStart = asOfDate.minusYears(3);
        OffsetDateTime knowledgeCutoff = asOfDate.atTime(23, 59, 59, 999000000).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

        // 1. Candidate Selection
        List<SchemeOption> candidateOptions;
        if (effectiveReq.candidateOptionIds() != null && !effectiveReq.candidateOptionIds().isEmpty()) {
            candidateOptions = schemeOptionRepository.findAllById(effectiveReq.candidateOptionIds()).stream()
                .filter(referenceUniverseService::isOptionInReferenceUniverse)
                .toList();
        } else {
            // Select candidates from inventory with STORED_ARTIFACT_READY classification (< 700 observations)
            ReferenceUniverseRecoveryInventoryDto inventory = getInventory(windowStart, asOfDate);
            final EquityUniverseCategory categoryFilter = effectiveReq.category();
            List<Long> targetIds = inventory.candidates().stream()
                .filter(c -> c.classification() == HistoricalNavRecoveryCategory.STORED_ARTIFACT_READY)
                .filter(c -> c.count3Y() < SUFFICIENCY_THRESHOLD)
                .filter(c -> categoryFilter == null || c.category() == categoryFilter)
                .map(NavRecoveryCandidateDto::schemeOptionId)
                .limit(effectiveReq.effectiveBatchSize())
                .toList();

            candidateOptions = schemeOptionRepository.findAllById(targetIds);
        }

        if (candidateOptions.isEmpty()) {
            OffsetDateTime completedAt = OffsetDateTime.now();
            return new NavRecoveryBatchExecutionResultDto(
                runId, "STORED_ARTIFACT_RECOVERY", startedAt, completedAt,
                effectiveReq.effectiveBatchSize(), 0, 0, 0, 0,
                System.currentTimeMillis() - startTime, List.of()
            );
        }

        log.info("Starting bounded stored-artifact recovery run {} for {} options (effectiveBatchSize={})",
            runId, candidateOptions.size(), effectiveReq.effectiveBatchSize());

        // Capture pre-recovery observation counts
        Map<Long, Long> preCounts = new HashMap<>();
        for (SchemeOption opt : candidateOptions) {
            long preCount = countObservationsInWindow(opt.getId(), windowStart, asOfDate);
            preCounts.put(opt.getId(), preCount);
        }

        Set<String> targetAmfiCodes = candidateOptions.stream()
            .map(SchemeOption::getAmfiCode)
            .filter(c -> c != null && !c.isBlank())
            .map(String::trim)
            .collect(Collectors.toSet());

        // 2. Ingest stored 2026 artifacts (#5378–#5381) in single passes
        List<SourceArtifact> artifactsToProcess = resolveStored2026Artifacts();
        List<Long> usedArtifactIds = new ArrayList<>();
        int totalInserted = 0;
        int totalDuplicates = 0;

        for (SourceArtifact artifact : artifactsToProcess) {
            usedArtifactIds.add(artifact.getId());
            log.info("Processing stored artifact #{} ({} bytes, URI: {}) for {} target AMFI schemes...",
                artifact.getId(), artifact.getByteSize(), artifact.getStorageUri(), targetAmfiCodes.size());

            IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, targetAmfiCodes);
            totalInserted += summary.observationsIngested();
            totalDuplicates += summary.duplicateRowsSkipped();
            log.info("Artifact #{} result: inserted={}, skipped={}, revisions={}",
                artifact.getId(), summary.observationsIngested(), summary.duplicateRowsSkipped(), summary.revisionsCreated());
        }

        // 3. Post-recovery per-fund evaluation and calculation orchestration
        List<NavRecoveryFundOutcomeDto> fundOutcomes = new ArrayList<>();
        int newlySufficientCount = 0;

        for (SchemeOption option : candidateOptions) {
            Long optId = option.getId();
            long preCount = preCounts.getOrDefault(optId, 0L);
            long postCount = countObservationsInWindow(optId, windowStart, asOfDate);
            int fundInserted = (int) Math.max(0, postCount - preCount);

            String schemeName = (option.getPlan() != null && option.getPlan().getScheme() != null)
                ? option.getPlan().getScheme().getName() : "Unknown Scheme";
            EquityUniverseCategory category = referenceUniverseService.evaluateOption(option).category();

            String outcomeStatus;
            Long calcRunId = null;
            String calcStatus = "NOT_EXECUTED";
            String message;

            if (postCount >= SUFFICIENCY_THRESHOLD) {
                newlySufficientCount++;
                outcomeStatus = "RECOVERED_SUFFICIENT";
                message = String.format("Successfully recovered: 3Y observations increased from %d to %d (threshold: %d).",
                    preCount, postCount, SUFFICIENCY_THRESHOLD);

                // Recalculate analytical profile if requested
                if (effectiveReq.shouldRecalculate()) {
                    try {
                        var bmRes = benchmarkResolutionService.resolveBenchmarkForOption(option, asOfDate);
                        if (bmRes != null && bmRes.benchmark() != null) {
                            Map<String, Object> calcParams = Map.of(
                                "start_date", windowStart.toString(),
                                "lookback_years", 3,
                                "periods_per_year", 252.0,
                                "min_downside_observations", 100
                            );
                            CalculationRun run = calculationOrchestratorService.executeCalculationRun(
                                optId,
                                bmRes.benchmark().getId(),
                                asOfDate,
                                knowledgeCutoff,
                                CANONICAL_METRICS,
                                CANONICAL_METHODOLOGY_TAG,
                                calcParams
                            );
                            calcRunId = run.getId();
                            calcStatus = run.getRunStatus();
                        } else {
                            calcStatus = "BENCHMARK_UNMAPPED";
                        }
                    } catch (Exception e) {
                        log.warn("Calculation orchestration failed for option {} ({}): {}", optId, amfiCodeOrNull(option), e.getMessage());
                        calcStatus = "FAILED: " + e.getMessage();
                    }
                }
            } else if (fundInserted > 0) {
                outcomeStatus = "PARTIAL_PROGRESS";
                message = String.format("Partial progress: observations increased from %d to %d, but still below 700 threshold.",
                    preCount, postCount);
            } else {
                outcomeStatus = "NO_DATA_AVAILABLE";
                message = "No matching observations found in stored artifacts #5378–#5381.";
            }

            fundOutcomes.add(new NavRecoveryFundOutcomeDto(
                optId,
                option.getAmfiCode(),
                option.getIsin(),
                schemeName,
                category,
                outcomeStatus,
                preCount,
                postCount,
                fundInserted,
                0,
                usedArtifactIds,
                calcRunId,
                calcStatus,
                message
            ));
        }

        OffsetDateTime completedAt = OffsetDateTime.now();
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Completed stored-artifact recovery run {}: {}/{} options became SUFFICIENT in {} ms (totalInserted={})",
            runId, newlySufficientCount, candidateOptions.size(), durationMs, totalInserted);

        return new NavRecoveryBatchExecutionResultDto(
            runId,
            "STORED_ARTIFACT_RECOVERY",
            startedAt,
            completedAt,
            effectiveReq.effectiveBatchSize(),
            candidateOptions.size(),
            newlySufficientCount,
            totalInserted,
            totalDuplicates,
            durationMs,
            fundOutcomes
        );
    }

    /**
     * Executes bounded targeted interval recovery for options with historical gaps inside the 3Y window.
     * Supports both the 37 gap candidates and 12 older discontinuity candidates.
     */
    @Transactional
    public NavRecoveryBatchExecutionResultDto executeTargetedIntervalRecovery(TargetedIntervalRecoveryRequest request) {
        long startTime = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        String runId = "GAP-REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        final TargetedIntervalRecoveryRequest effectiveReq = (request != null)
            ? request : TargetedIntervalRecoveryRequest.ofDefault();

        LocalDate asOfDate = effectiveReq.effectiveAsOfDate();
        LocalDate windowStart = asOfDate.minusYears(3);
        OffsetDateTime knowledgeCutoff = asOfDate.atTime(23, 59, 59, 999000000).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

        // 1. Candidate Selection
        List<SchemeOption> candidateOptions;
        if (effectiveReq.candidateOptionIds() != null && !effectiveReq.candidateOptionIds().isEmpty()) {
            candidateOptions = schemeOptionRepository.findAllById(effectiveReq.candidateOptionIds()).stream()
                .filter(referenceUniverseService::isOptionInReferenceUniverse)
                .toList();
        } else {
            // Select candidates from inventory with HISTORICAL_GAP_3Y or OLDER_DISCONTINUITY classification
            ReferenceUniverseRecoveryInventoryDto inventory = getInventory(windowStart, asOfDate);
            final EquityUniverseCategory categoryFilter = effectiveReq.category();
            List<Long> targetIds = inventory.candidates().stream()
                .filter(c -> c.classification() == HistoricalNavRecoveryCategory.HISTORICAL_GAP_3Y
                          || c.classification() == HistoricalNavRecoveryCategory.OLDER_DISCONTINUITY)
                .filter(c -> c.count3Y() < SUFFICIENCY_THRESHOLD)
                .filter(c -> categoryFilter == null || c.category() == categoryFilter)
                .map(NavRecoveryCandidateDto::schemeOptionId)
                .limit(effectiveReq.effectiveBatchSize())
                .toList();

            candidateOptions = schemeOptionRepository.findAllById(targetIds);
        }

        if (candidateOptions.isEmpty()) {
            OffsetDateTime completedAt = OffsetDateTime.now();
            return new NavRecoveryBatchExecutionResultDto(
                runId, "TARGETED_INTERVAL_RECOVERY", startedAt, completedAt,
                effectiveReq.effectiveBatchSize(), 0, 0, 0, 0,
                System.currentTimeMillis() - startTime, List.of()
            );
        }

        log.info("Starting targeted interval recovery run {} for {} options (effectiveBatchSize={})",
            runId, candidateOptions.size(), effectiveReq.effectiveBatchSize());

        List<NavRecoveryFundOutcomeDto> fundOutcomes = new ArrayList<>();
        int newlySufficientCount = 0;
        int totalInserted = 0;
        int totalDuplicates = 0;

        for (SchemeOption option : candidateOptions) {
            Long optId = option.getId();
            String amfiCode = option.getAmfiCode();
            Scheme scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;
            String schemeName = scheme != null ? scheme.getName() : "Unknown Scheme";
            String amcCode = (scheme != null && scheme.getAmc() != null) ? scheme.getAmc().getCode() : null;
            String amcMfCode = PeerHistoricalNavIngestionService.AMC_MF_MAP.getOrDefault(amcCode, "");
            EquityUniverseCategory category = referenceUniverseService.evaluateOption(option).category();

            long preCount = countObservationsInWindow(optId, windowStart, asOfDate);

            // Derive missing intervals
            List<HistoricalNavGapInterval> gaps = deriveMissingIntervals(optId, windowStart, asOfDate, null);
            List<Long> usedArtifactIds = new ArrayList<>();
            int fundInserted = 0;
            int fundDuplicates = 0;

            int processedGaps = 0;
            for (HistoricalNavGapInterval gap : gaps) {
                if (processedGaps >= effectiveReq.effectiveMaxIntervals()) break;

                // Chunk gap into <= 90-day segments
                LocalDate chunkStart = gap.startDate();
                while (!chunkStart.isAfter(gap.endDate())) {
                    LocalDate chunkEnd = chunkStart.plusDays(89);
                    if (chunkEnd.isAfter(gap.endDate())) {
                        chunkEnd = gap.endDate();
                    }

                    try {
                        SourceArtifact artifact = resolveOrFetchArtifact(amfiCode, amcMfCode, chunkStart, chunkEnd);
                        if (artifact != null) {
                            usedArtifactIds.add(artifact.getId());
                            IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, Set.of(amfiCode));
                            fundInserted += summary.observationsIngested();
                            fundDuplicates += summary.duplicateRowsSkipped();
                        }
                    } catch (Exception e) {
                        log.warn("Failed interval retrieval for option {} / AMFI {} [{} to {}]: {}",
                            optId, amfiCode, chunkStart, chunkEnd, e.getMessage());
                    }

                    chunkStart = chunkEnd.plusDays(1);
                }
                processedGaps++;
            }

            totalInserted += fundInserted;
            totalDuplicates += fundDuplicates;

            long postCount = countObservationsInWindow(optId, windowStart, asOfDate);

            String outcomeStatus;
            Long calcRunId = null;
            String calcStatus = "NOT_EXECUTED";
            String message;

            if (postCount >= SUFFICIENCY_THRESHOLD) {
                newlySufficientCount++;
                outcomeStatus = "RECOVERED_SUFFICIENT";
                message = String.format("Targeted intervals recovered: 3Y observations increased from %d to %d (threshold: %d).",
                    preCount, postCount, SUFFICIENCY_THRESHOLD);

                if (effectiveReq.shouldRecalculate()) {
                    try {
                        var bmRes = benchmarkResolutionService.resolveBenchmarkForOption(option, asOfDate);
                        if (bmRes != null && bmRes.benchmark() != null) {
                            Map<String, Object> calcParams = Map.of(
                                "start_date", windowStart.toString(),
                                "lookback_years", 3,
                                "periods_per_year", 252.0,
                                "min_downside_observations", 100
                            );
                            CalculationRun run = calculationOrchestratorService.executeCalculationRun(
                                optId,
                                bmRes.benchmark().getId(),
                                asOfDate,
                                knowledgeCutoff,
                                CANONICAL_METRICS,
                                CANONICAL_METHODOLOGY_TAG,
                                calcParams
                            );
                            calcRunId = run.getId();
                            calcStatus = run.getRunStatus();
                        } else {
                            calcStatus = "BENCHMARK_UNMAPPED";
                        }
                    } catch (Exception e) {
                        log.warn("Calculation orchestration failed for option {} ({}): {}", optId, amfiCode, e.getMessage());
                        calcStatus = "FAILED: " + e.getMessage();
                    }
                }
            } else if (fundInserted > 0) {
                outcomeStatus = "PARTIAL_PROGRESS";
                message = String.format("Targeted recovery inserted %d observations (count: %d -> %d), still below %d threshold.",
                    fundInserted, preCount, postCount, SUFFICIENCY_THRESHOLD);
            } else {
                outcomeStatus = "NO_DATA_AVAILABLE";
                message = "AMFI source returned zero records for requested missing intervals.";
            }

            fundOutcomes.add(new NavRecoveryFundOutcomeDto(
                optId,
                amfiCode,
                option.getIsin(),
                schemeName,
                category,
                outcomeStatus,
                preCount,
                postCount,
                fundInserted,
                fundDuplicates,
                usedArtifactIds,
                calcRunId,
                calcStatus,
                message
            ));
        }

        OffsetDateTime completedAt = OffsetDateTime.now();
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Completed targeted interval recovery run {}: {}/{} options became SUFFICIENT in {} ms (totalInserted={})",
            runId, newlySufficientCount, candidateOptions.size(), durationMs, totalInserted);

        return new NavRecoveryBatchExecutionResultDto(
            runId,
            "TARGETED_INTERVAL_RECOVERY",
            startedAt,
            completedAt,
            effectiveReq.effectiveBatchSize(),
            candidateOptions.size(),
            newlySufficientCount,
            totalInserted,
            totalDuplicates,
            durationMs,
            fundOutcomes
        );
    }

    /**
     * Operationally recovers missing historical NAV intervals for a specific scheme option.
     * If explicitIntervals is null or empty, automatically derives missing intervals from the database state.
     * Enforces <= 90-day chunking, authentic AMFI retrieval, artifact reuse, and optional calculation trigger.
     */
    public NavRecoveryFundOutcomeDto recoverOptionIntervals(
        Long schemeOptionId,
        LocalDate windowStart,
        LocalDate windowEnd,
        List<HistoricalNavGapInterval> explicitIntervals,
        int maxIntervals,
        boolean triggerCalculation
    ) {
        if (schemeOptionId == null) {
            throw new IllegalArgumentException("schemeOptionId must not be null");
        }
        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption not found: " + schemeOptionId));

        LocalDate start = windowStart != null ? windowStart : DEFAULT_WINDOW_START;
        LocalDate end = windowEnd != null ? windowEnd : DEFAULT_WINDOW_END;
        OffsetDateTime knowledgeCutoff = end.atTime(23, 59, 59, 999000000).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

        String amfiCode = option.getAmfiCode();
        if (amfiCode == null || amfiCode.isBlank()) {
            return new NavRecoveryFundOutcomeDto(
                schemeOptionId, null, option.getIsin(),
                option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getName() : "Unknown",
                referenceUniverseService.evaluateOption(option).category(),
                "NO_AMFI_CODE", 0, 0, 0, 0, List.of(), null, "NOT_EXECUTED",
                "Option does not have an AMFI scheme code registered."
            );
        }

        Scheme scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;
        String schemeName = scheme != null ? scheme.getName() : "Unknown Scheme";
        String amcCode = (scheme != null && scheme.getAmc() != null) ? scheme.getAmc().getCode() : null;
        String amcMfCode = PeerHistoricalNavIngestionService.AMC_MF_MAP.getOrDefault(amcCode, "");
        EquityUniverseCategory category = referenceUniverseService.evaluateOption(option).category();

        long preCount = countObservationsInWindow(schemeOptionId, start, end);

        List<HistoricalNavGapInterval> gaps = (explicitIntervals != null && !explicitIntervals.isEmpty())
            ? explicitIntervals
            : deriveMissingIntervals(schemeOptionId, start, end, null);

        List<Long> usedArtifactIds = new ArrayList<>();
        int fundInserted = 0;
        int fundDuplicates = 0;

        int limit = maxIntervals > 0 ? maxIntervals : 10;
        int processed = 0;

        for (HistoricalNavGapInterval gap : gaps) {
            if (processed >= limit) break;

            LocalDate chunkStart = gap.startDate();
            while (!chunkStart.isAfter(gap.endDate())) {
                LocalDate chunkEnd = chunkStart.plusDays(89);
                if (chunkEnd.isAfter(gap.endDate())) {
                    chunkEnd = gap.endDate();
                }

                try {
                    SourceArtifact artifact = resolveOrFetchArtifact(amfiCode, amcMfCode, chunkStart, chunkEnd);
                    if (artifact != null) {
                        usedArtifactIds.add(artifact.getId());
                        IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, Set.of(amfiCode));
                        fundInserted += summary.observationsIngested();
                        fundDuplicates += summary.duplicateRowsSkipped();
                    }
                } catch (Exception e) {
                    log.warn("Failed interval retrieval for option {} / AMFI {} [{} to {}]: {}",
                        schemeOptionId, amfiCode, chunkStart, chunkEnd, e.getMessage());
                }

                chunkStart = chunkEnd.plusDays(1);
            }
            processed++;
        }

        long postCount = countObservationsInWindow(schemeOptionId, start, end);
        String outcomeStatus;
        Long calcRunId = null;
        String calcStatus = "NOT_EXECUTED";
        String message;

        if (postCount >= SUFFICIENCY_THRESHOLD) {
            outcomeStatus = "RECOVERED_SUFFICIENT";
            message = String.format("Targeted intervals recovered: 3Y observations increased from %d to %d (threshold: %d).",
                preCount, postCount, SUFFICIENCY_THRESHOLD);

            if (triggerCalculation) {
                try {
                    var bmRes = benchmarkResolutionService.resolveBenchmarkForOption(option, end);
                    if (bmRes != null && bmRes.benchmark() != null) {
                        Map<String, Object> calcParams = Map.of(
                            "start_date", start.toString(),
                            "lookback_years", 3,
                            "periods_per_year", 252.0,
                            "min_downside_observations", 100
                        );
                        CalculationRun run = calculationOrchestratorService.executeCalculationRun(
                            schemeOptionId,
                            bmRes.benchmark().getId(),
                            end,
                            knowledgeCutoff,
                            CANONICAL_METRICS,
                            CANONICAL_METHODOLOGY_TAG,
                            calcParams
                        );
                        calcRunId = run.getId();
                        calcStatus = run.getRunStatus();
                    } else {
                        calcStatus = "BENCHMARK_UNMAPPED";
                    }
                } catch (Exception e) {
                    calcStatus = "FAILED: " + e.getMessage();
                }
            }
        } else if (fundInserted > 0) {
            outcomeStatus = "PARTIAL_PROGRESS";
            message = String.format("Targeted recovery inserted %d observations (count: %d -> %d), still below %d threshold.",
                fundInserted, preCount, postCount, SUFFICIENCY_THRESHOLD);
        } else {
            outcomeStatus = "NO_DATA_AVAILABLE";
            message = "AMFI source returned zero observations for requested missing intervals.";
        }

        return new NavRecoveryFundOutcomeDto(
            schemeOptionId,
            amfiCode,
            option.getIsin(),
            schemeName,
            category,
            outcomeStatus,
            preCount,
            postCount,
            fundInserted,
            fundDuplicates,
            usedArtifactIds,
            calcRunId,
            calcStatus,
            message
        );
    }

    /**
     * Operationally recovers missing intervals by AMFI scheme code.
     */
    public NavRecoveryFundOutcomeDto recoverOptionByAmfiCode(
        String amfiCode,
        LocalDate windowStart,
        LocalDate windowEnd,
        boolean triggerCalculation
    ) {
        if (amfiCode == null || amfiCode.isBlank()) {
            throw new IllegalArgumentException("amfiCode must not be null or blank");
        }
        Optional<SchemeOption> opt = schemeOptionRepository.findByAmfiCode(amfiCode.trim());
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("No SchemeOption registered for AMFI code: " + amfiCode);
        }
        return recoverOptionIntervals(opt.get().getId(), windowStart, windowEnd, null, 10, triggerCalculation);
    }

    /**
     * Resolves stored universe artifacts #5378–#5381 from cache or repository.
     */
    public List<SourceArtifact> resolveStored2026Artifacts() {
        List<SourceArtifact> artifacts = new ArrayList<>();
        for (String fragment : STORED_2026_CHUNK_FRAGMENTS) {
            SourceArtifact cached = storedChunkCache.get(fragment);
            if (cached != null) {
                artifacts.add(cached);
                continue;
            }

            Optional<SourceArtifact> found = sourceArtifactRepository.findTopByStorageUriContaining(fragment);
            if (found.isPresent() && found.get().getPayloadBlob() != null && found.get().getPayloadBlob().length > 0) {
                SourceArtifact art = found.get();
                storedChunkCache.put(fragment, art);
                artifacts.add(art);
                log.info("Resolved authentic stored artifact #{} ({} bytes) for date fragment '{}'",
                    art.getId(), art.getByteSize(), fragment);
            } else {
                log.warn("Stored artifact covering date fragment '{}' not found in source_artifact table", fragment);
            }
        }
        return artifacts;
    }

    /**
     * Checks repository for an authentic source artifact covering the given date chunk before falling back to network fetch.
     */
    private SourceArtifact resolveOrFetchArtifact(String amfiCode, String amcMfCode, LocalDate startDate, LocalDate endDate) {
        String fromStr = startDate.format(AMFI_URL_DATE);
        String toStr = endDate.format(AMFI_URL_DATE);
        String dateFragment = String.format("frmdt=%s&todt=%s", fromStr, toStr);

        SourceArtifact cached = storedChunkCache.get(dateFragment);
        if (cached != null) {
            return cached;
        }

        // 1. Check if universe chunk artifact exists for this date window
        Optional<SourceArtifact> existingUniverseChunk = sourceArtifactRepository.findTopByStorageUriContaining(dateFragment);
        if (existingUniverseChunk.isPresent() && existingUniverseChunk.get().getPayloadBlob() != null) {
            storedChunkCache.put(dateFragment, existingUniverseChunk.get());
            return existingUniverseChunk.get();
        }

        // 2. Check if fund-specific artifact exists
        String fundUrl = amfiSourceClient.buildUrl(amfiCode, amcMfCode, startDate, endDate);
        Optional<SourceArtifact> existingFund = sourceArtifactRepository.findFirstByStorageUri(fundUrl);
        if (existingFund.isPresent() && existingFund.get().getPayloadBlob() != null) {
            return existingFund.get();
        }

        // 3. Fallback to network fetch via AmfiSourceClient
        log.info("Fetching missing historical interval from AMFI: scheme={}, dates={} to {}", amfiCode, startDate, endDate);
        return amfiSourceClient.fetchAndPersistArtifact(amfiCode, amcMfCode, startDate, endDate);
    }

    private long countObservationsInWindow(Long schemeOptionId, LocalDate start, LocalDate end) {
        return navObservationRepository
            .findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(schemeOptionId, start, end)
            .stream()
            .filter(o -> Boolean.TRUE.equals(o.getLatestRevision()))
            .map(NavObservation::getEffectiveDate)
            .distinct()
            .count();
    }

    private String amfiCodeOrNull(SchemeOption option) {
        return option != null ? option.getAmfiCode() : "NULL";
    }

    /**
     * Retrieves universe-wide horizon coverage report (Phase A3).
     */
    public UniverseHorizonCoverageReportDto getHorizonInventory(AnalyticalHorizon horizon, LocalDate asOfDate) {
        return historySufficiencyService.generateUniverseHorizonReport(horizon, asOfDate);
    }

    /**
     * Evaluates multi-horizon coverage across 3Y, 5Y, and 10Y for a specific scheme option (Phase A3).
     */
    public OptionMultiHorizonCoverageDto evaluateOptionMultiHorizon(Long schemeOptionId, LocalDate asOfDate) {
        if (schemeOptionId == null) {
            return null;
        }
        SchemeOption option = schemeOptionRepository.findById(schemeOptionId).orElse(null);
        if (option == null) {
            return null;
        }
        return historySufficiencyService.evaluateOptionMultiHorizon(option, asOfDate);
    }

    /**
     * Operationally recovers missing intervals for an option under a specific analytical horizon (Phase A3).
     */
    public NavRecoveryFundOutcomeDto recoverOptionHorizon(
        Long schemeOptionId,
        AnalyticalHorizon horizon,
        LocalDate asOfDate,
        Integer maxIntervals,
        boolean triggerCalculation
    ) {
        if (schemeOptionId == null) {
            throw new IllegalArgumentException("schemeOptionId must not be null");
        }
        final AnalyticalHorizon effHorizon = horizon != null ? horizon : AnalyticalHorizon.FIVE_YEAR;
        final LocalDate end = asOfDate != null ? asOfDate : DEFAULT_WINDOW_END;
        final LocalDate fullWindowStart = effHorizon.calculateStartDate(end);
        final OffsetDateTime knowledgeCutoff = end.atTime(23, 59, 59, 999000000).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId).orElse(null);
        if (option == null) {
            return new NavRecoveryFundOutcomeDto(
                schemeOptionId, "UNKNOWN", "UNKNOWN", "Unknown Scheme", null,
                "NOT_FOUND", 0, 0, 0, 0, List.of(), null, "NOT_EXECUTED",
                "SchemeOption #" + schemeOptionId + " not found."
            );
        }

        String amfiCode = option.getAmfiCode();
        if (amfiCode == null || amfiCode.isBlank()) {
            return new NavRecoveryFundOutcomeDto(
                schemeOptionId, "UNKNOWN", option.getIsin(), "Unknown Scheme", null,
                "NO_AMFI_CODE", 0, 0, 0, 0, List.of(), null, "NOT_EXECUTED",
                "Option does not have an AMFI scheme code registered."
            );
        }

        Scheme scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;
        String schemeName = scheme != null ? scheme.getName() : "Unknown Scheme";
        String amcCode = (scheme != null && scheme.getAmc() != null) ? scheme.getAmc().getCode() : null;
        String amcMfCode = PeerHistoricalNavIngestionService.AMC_MF_MAP.getOrDefault(amcCode, "");
        EquityUniverseCategory category = referenceUniverseService.evaluateOption(option).category();
        LocalDate inceptionDate = scheme != null ? scheme.getInceptionDate() : null;

        boolean isInceptionBounded = inceptionDate != null && inceptionDate.isAfter(fullWindowStart);
        LocalDate effectiveStart = isInceptionBounded ? inceptionDate : fullWindowStart;

        long preCount = countObservationsInWindow(schemeOptionId, effectiveStart, end);

        // Derive missing intervals respecting inception boundary
        List<HistoricalNavGapInterval> gaps = historySufficiencyService.deriveGapsInWindow(
            schemeOptionId, effectiveStart, end
        );

        List<Long> usedArtifactIds = new ArrayList<>();
        int fundInserted = 0;
        int fundDuplicates = 0;

        int limit = (maxIntervals != null && maxIntervals > 0) ? maxIntervals : 10;
        int processed = 0;

        for (HistoricalNavGapInterval gap : gaps) {
            if (processed >= limit) break;

            LocalDate chunkStart = gap.startDate();
            while (!chunkStart.isAfter(gap.endDate())) {
                LocalDate chunkEnd = chunkStart.plusDays(89);
                if (chunkEnd.isAfter(gap.endDate())) {
                    chunkEnd = gap.endDate();
                }

                try {
                    SourceArtifact artifact = resolveOrFetchArtifact(amfiCode, amcMfCode, chunkStart, chunkEnd);
                    if (artifact != null) {
                        usedArtifactIds.add(artifact.getId());
                        IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, Set.of(amfiCode));
                        fundInserted += summary.observationsIngested();
                        fundDuplicates += summary.duplicateRowsSkipped();
                    }
                } catch (Exception e) {
                    log.warn("Failed interval retrieval for option {} / AMFI {} [{} to {}] ({}): {}",
                        schemeOptionId, amfiCode, chunkStart, chunkEnd, effHorizon.getCode(), e.getMessage());
                }

                chunkStart = chunkEnd.plusDays(1);
            }
            processed++;
        }

        long postCount = countObservationsInWindow(schemeOptionId, effectiveStart, end);
        int threshold = effHorizon.getDefaultThreshold();
        boolean newlySufficient = postCount >= threshold;

        String outcomeStatus;
        Long calcRunId = null;
        String calcStatus = "NOT_EXECUTED";
        String message;

        if (newlySufficient) {
            outcomeStatus = "RECOVERED_SUFFICIENT";
            message = String.format("Targeted %s horizon intervals recovered: observations increased from %d to %d (threshold: %d).",
                effHorizon.getCode(), preCount, postCount, threshold);

            if (triggerCalculation) {
                try {
                    var bmRes = benchmarkResolutionService.resolveBenchmarkForOption(option, end);
                    if (bmRes != null && bmRes.benchmark() != null) {
                        List<String> targetMetrics;
                        Map<String, Object> calcParams;
                        String methodologyTag;

                        if (effHorizon == AnalyticalHorizon.THREE_YEAR) {
                            targetMetrics = CANONICAL_METRICS;
                            methodologyTag = CANONICAL_METHODOLOGY_TAG;
                            calcParams = Map.of(
                                "start_date", effectiveStart.toString(),
                                "lookback_years", 3,
                                "periods_per_year", 252.0,
                                "min_downside_observations", 100
                            );
                        } else if (effHorizon == AnalyticalHorizon.FIVE_YEAR) {
                            targetMetrics = List.of("RET-02", "RET-04", "RET-05");
                            methodologyTag = "CANDIDATE_M2N_HORIZON";
                            calcParams = Map.of(
                                "start_date", effectiveStart.toString(),
                                "lookback_years", 5,
                                "window_years", 5,
                                "periods_per_year", 252.0
                            );
                        } else {
                            targetMetrics = List.of("RET-02", "RET-05");
                            methodologyTag = "CANDIDATE_M2N_HORIZON";
                            calcParams = Map.of(
                                "start_date", effectiveStart.toString(),
                                "lookback_years", 10,
                                "window_years", 10,
                                "periods_per_year", 252.0
                            );
                        }

                        CalculationRun run = calculationOrchestratorService.executeCalculationRun(
                            schemeOptionId,
                            bmRes.benchmark().getId(),
                            end,
                            knowledgeCutoff,
                            targetMetrics,
                            methodologyTag,
                            calcParams
                        );
                        calcRunId = run.getId();
                        calcStatus = run.getRunStatus();
                    } else {
                        calcStatus = "BENCHMARK_UNMAPPED";
                    }
                } catch (Exception e) {
                    calcStatus = "FAILED: " + e.getMessage();
                }
            }
        } else if (isInceptionBounded) {
            outcomeStatus = "INCEPTION_BOUNDED";
            message = String.format("Scheme inception (%s) limits history. Recovered %d observations (count: %d -> %d), below %d threshold.",
                inceptionDate, fundInserted, preCount, postCount, threshold);
        } else if (fundInserted > 0) {
            outcomeStatus = "PARTIAL_PROGRESS";
            message = String.format("Targeted %s recovery inserted %d observations (count: %d -> %d), still below %d threshold.",
                effHorizon.getCode(), fundInserted, preCount, postCount, threshold);
        } else {
            outcomeStatus = "NO_DATA_AVAILABLE";
            message = String.format("AMFI source returned zero observations for %s horizon requested intervals.", effHorizon.getCode());
        }

        return new NavRecoveryFundOutcomeDto(
            schemeOptionId,
            amfiCode,
            option.getIsin(),
            schemeName,
            category,
            outcomeStatus,
            preCount,
            postCount,
            fundInserted,
            fundDuplicates,
            usedArtifactIds,
            calcRunId,
            calcStatus,
            message
        );
    }

    /**
     * Executes bounded multi-horizon recovery batch across eligible options (Phase A3).
     */
    public NavRecoveryBatchExecutionResultDto executeHorizonRecovery(HorizonRecoveryRequest request) {
        long startTime = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        final HorizonRecoveryRequest effectiveReq = request != null
            ? request : HorizonRecoveryRequest.ofDefault(AnalyticalHorizon.FIVE_YEAR);

        final AnalyticalHorizon horizon = effectiveReq.effectiveHorizon();
        String runId = "HORIZON-REC-" + horizon.getCode() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDate asOfDate = effectiveReq.effectiveAsOfDate();

        List<SchemeOption> candidateOptions;
        if (effectiveReq.candidateOptionIds() != null && !effectiveReq.candidateOptionIds().isEmpty()) {
            candidateOptions = schemeOptionRepository.findAllById(effectiveReq.candidateOptionIds()).stream()
                .filter(referenceUniverseService::isOptionInReferenceUniverse)
                .toList();
        } else {
            UniverseHorizonCoverageReportDto report = historySufficiencyService.generateUniverseHorizonReport(horizon, asOfDate);
            final EquityUniverseCategory categoryFilter = effectiveReq.category();
            List<Long> targetIds = report.candidates().stream()
                .filter(c -> !c.isInceptionBounded())
                .filter(c -> !c.isSufficient())
                .filter(c -> categoryFilter == null || c.category() == categoryFilter)
                .map(OptionHorizonCoverageDto::schemeOptionId)
                .limit(effectiveReq.effectiveBatchSize())
                .toList();

            candidateOptions = schemeOptionRepository.findAllById(targetIds);
        }

        List<NavRecoveryFundOutcomeDto> fundOutcomes = new ArrayList<>();
        int newlySufficientCount = 0;
        int totalInserted = 0;
        int totalDuplicates = 0;

        for (SchemeOption option : candidateOptions) {
            NavRecoveryFundOutcomeDto outcome = recoverOptionHorizon(
                option.getId(),
                horizon,
                asOfDate,
                effectiveReq.effectiveMaxIntervals(),
                effectiveReq.shouldTriggerCalculation()
            );
            fundOutcomes.add(outcome);
            totalInserted += outcome.observationsInserted();
            totalDuplicates += outcome.duplicatesSkipped();
            if ("RECOVERED_SUFFICIENT".equals(outcome.outcomeStatus())) {
                newlySufficientCount++;
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        OffsetDateTime completedAt = OffsetDateTime.now();

        return new NavRecoveryBatchExecutionResultDto(
            runId,
            "HORIZON_RECOVERY_" + horizon.getCode(),
            startedAt,
            completedAt,
            effectiveReq.effectiveBatchSize(),
            candidateOptions.size(),
            newlySufficientCount,
            totalInserted,
            totalDuplicates,
            durationMs,
            fundOutcomes
        );
    }
}
