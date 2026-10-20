package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.yukira.backend.dto.ingestion.HistoricalBackfillPreviewDto;
import com.yukira.backend.dto.ingestion.HistoricalBackfillRunSummaryDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for deterministic ingestion and historical backfill of NAV observations from authoritative AMFI sources.
 *
 * Implements bounded window chunking, bitemporal PIT observation capture,
 * cryptographic source artifact provenance, exact window resumability without arbitrary percentage thresholds,
 * deterministic multi-AMC cohort selection, and per-fund failure isolation.
 */
@Service
public class HistoricalNavIngestionService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalNavIngestionService.class);

    private final AmfiSourceClient amfiSourceClient;
    private final AmfiNavIngestionService amfiNavIngestionService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public record ControlledFundTarget(
        String label,
        String amfiCode,
        String isin,
        String amcMfCode,
        String expectedCategory,
        String planType,
        String optionType
    ) {}

    public record TargetedBackfillFundResult(
        Long schemeOptionId,
        String amfiCode,
        String schemeName,
        String isin,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        LocalDate actualEarliestObservation,
        LocalDate actualLatestObservation,
        int windowsRequested,
        int windowsCompleted,
        int windowsPartial,
        int windowsFailed,
        int observationsInserted,
        int observationsAlreadyPresent,
        int observationsRejected,
        String status,
        String failureReason,
        List<Long> sourceArtifactIds,
        List<String> sourceArtifactHashes,
        long executionDurationMs,
        IngestionSummary summary,
        String message
    ) {
        // Backwards compatible 8-arg constructor for existing tests/callers
        public TargetedBackfillFundResult(
            Long schemeOptionId,
            String amfiCode,
            String isin,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            IngestionSummary summary,
            String message
        ) {
            this(
                schemeOptionId,
                amfiCode,
                null,
                isin,
                startDate,
                endDate,
                null,
                null,
                1,
                "SUCCESS".equals(status) ? 1 : 0,
                0,
                "FAILED".equals(status) ? 1 : 0,
                summary != null ? summary.observationsIngested() : 0,
                summary != null ? summary.duplicateRowsSkipped() : 0,
                summary != null ? summary.validationIssuesCreated() : 0,
                status,
                "FAILED".equals(status) ? message : null,
                summary != null && summary.sourceArtifactId() != null ? List.of(summary.sourceArtifactId()) : List.of(),
                summary != null && summary.sha256Hash() != null ? List.of(summary.sha256Hash()) : List.of(),
                0L,
                summary,
                message
            );
        }
    }

    public record TargetedBackfillResult(
        LocalDate startDate,
        LocalDate endDate,
        int fundsRequested,
        int fundsSucceeded,
        int fundsFailed,
        int totalObservationsIngested,
        int totalDuplicatesSkipped,
        int totalRevisionsCreated,
        List<TargetedBackfillFundResult> funds
    ) {}

    public static final List<ControlledFundTarget> CONTROLLED_SAMPLE_TARGETS = List.of(
        new ControlledFundTarget("Canonical Pilot (Flexi Cap)", "118955", "INF179K01UT0", "9", "Flexi Cap Fund", "DIRECT", "GROWTH"),
        new ControlledFundTarget("Equity Core (Large Cap)", "119018", "INF179K01YV8", "9", "Large Cap Fund", "DIRECT", "GROWTH"),
        new ControlledFundTarget("Small Cap (High Volatility)", "118778", "INF204K01K15", "21", "Small Cap Fund", "DIRECT", "GROWTH"),
        new ControlledFundTarget("Liquid / Debt Fund", "119091", "INF179KB1HP9", "9", "Liquid Fund", "DIRECT", "GROWTH"),
        new ControlledFundTarget("IDCW Variant", "118954", "INF179K01VL5", "9", "Flexi Cap Fund", "DIRECT", "IDCW")
    );

    public HistoricalNavIngestionService(
        AmfiSourceClient amfiSourceClient,
        AmfiNavIngestionService amfiNavIngestionService,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        SourceArtifactRepository sourceArtifactRepository
    ) {
        this.amfiSourceClient = amfiSourceClient;
        this.amfiNavIngestionService = amfiNavIngestionService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
    }

    /**
     * Ingests historical NAV observations for a single fund within a bounded date window.
     */
    @Transactional
    public IngestionSummary ingestHistoricalNav(String amfiSchemeCode, String amcMfCode, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching and ingesting historical NAV for AMFI code {} (MF code {}) from {} to {}",
            amfiSchemeCode, amcMfCode, startDate, endDate);

        SourceArtifact artifact = amfiSourceClient.fetchAndPersistArtifact(amfiSchemeCode, amcMfCode, startDate, endDate);
        return amfiNavIngestionService.ingestArtifact(artifact);
    }

    /**
     * Ingests historical NAV observations in 90-day chunked date windows.
     */
    public IngestionSummary ingestHistoricalNavInChunks(String amfiSchemeCode, String amcMfCode, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching and ingesting historical NAV in 90-day chunks for AMFI code {} from {} to {}",
            amfiSchemeCode, startDate, endDate);

        int totalParsed = 0;
        int observationsIngested = 0;
        int duplicateRowsSkipped = 0;
        int revisionsCreated = 0;
        int validationIssuesCreated = 0;
        Long lastArtifactId = null;
        String lastHash = null;
        List<String> allMessages = new ArrayList<>();

        LocalDate currentStart = startDate;
        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = currentStart.plusDays(89);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }

            log.info("Fetching chunk: {} to {} for AMFI code {}", currentStart, currentEnd, amfiSchemeCode);
            SourceArtifact artifact = amfiSourceClient.fetchAndPersistArtifact(amfiSchemeCode, amcMfCode, currentStart, currentEnd);
            IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, amfiSchemeCode);

            totalParsed += summary.totalRowsParsed();
            observationsIngested += summary.observationsIngested();
            duplicateRowsSkipped += summary.duplicateRowsSkipped();
            revisionsCreated += summary.revisionsCreated();
            validationIssuesCreated += summary.validationIssuesCreated();
            lastArtifactId = summary.sourceArtifactId();
            lastHash = summary.sha256Hash();
            allMessages.addAll(summary.messages());

            currentStart = currentEnd.plusDays(1);
        }

        return new IngestionSummary(
            lastArtifactId,
            lastHash,
            totalParsed,
            totalParsed,
            observationsIngested,
            revisionsCreated,
            duplicateRowsSkipped,
            validationIssuesCreated,
            allMessages
        );
    }

    /**
     * Ingests historical NAV in 90-day chunks for a scheme option with hardened source artifact tracking.
     *
     * Correctness model:
     * - Checks whether official AMFI source artifact for (amfiCode, window) was already fetched.
     * - Eliminates arbitrary 70% thresholds that could hide missing historical data.
     * - Idempotent re-execution skips re-downloading existing artifacts while correctly updating observations.
     */
    public IngestionSummary ingestHistoricalNavInChunksForSchemeOption(
        Long schemeOptionId,
        String amfiSchemeCode,
        String amcMfCode,
        LocalDate startDate,
        LocalDate endDate
    ) {
        log.info("Fetching and ingesting resumable historical NAV in 90-day chunks for scheme_option_id {} / AMFI code {} from {} to {}",
            schemeOptionId, amfiSchemeCode, startDate, endDate);

        int totalParsed = 0;
        int observationsIngested = 0;
        int duplicateRowsSkipped = 0;
        int revisionsCreated = 0;
        int validationIssuesCreated = 0;
        Long lastArtifactId = null;
        String lastHash = null;
        List<String> allMessages = new ArrayList<>();

        LocalDate currentStart = startDate;
        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = currentStart.plusDays(89);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }

            String targetUrl = amfiSourceClient.buildUrl(amfiSchemeCode, amcMfCode, currentStart, currentEnd);
            String universeChunkUrl = amfiSourceClient.buildUniverseChunkUrl(currentStart, currentEnd);
            boolean artifactExists = sourceArtifactRepository.existsByStorageUri(targetUrl)
                || sourceArtifactRepository.existsByStorageUri(universeChunkUrl);
            int latestObservationCount = countLatestObservationsInWindow(schemeOptionId, currentStart, currentEnd);
            int expectedBusinessDays = countWeekdays(currentStart, currentEnd);

            boolean isChunkAlreadyComplete = artifactExists || (latestObservationCount >= Math.max(1, expectedBusinessDays - 5));
            if (isChunkAlreadyComplete) {
                log.info("Artifact or complete observations already recorded for chunk {} to {} (schemeOptionId {}, amfiCode {}): latestObservations={}",
                    currentStart, currentEnd, schemeOptionId, amfiSchemeCode, latestObservationCount);
                if (latestObservationCount >= Math.max(1, expectedBusinessDays - 5)) {
                    allMessages.add(String.format("Skipped %s to %s: ALREADY_COMPLETE (%d observations)", currentStart, currentEnd, latestObservationCount));
                } else if (latestObservationCount > 0) {
                    allMessages.add(String.format("Skipped %s to %s: SOURCE_DATA_LIMITED (%d observations)", currentStart, currentEnd, latestObservationCount));
                } else {
                    allMessages.add(String.format("Skipped %s to %s: NO_DATA / SHORT_FUND_HISTORY (0 observations in source artifact)", currentStart, currentEnd));
                }
                currentStart = currentEnd.plusDays(1);
                continue;
            }

            // Fetch source artifact from official AMFI portal
            log.info("Fetching chunk: {} to {} for AMFI code {}", currentStart, currentEnd, amfiSchemeCode);
            SourceArtifact artifact = amfiSourceClient.fetchAndPersistUniverseChunkArtifact(currentStart, currentEnd);
            IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, Set.of(amfiSchemeCode));

            totalParsed += summary.totalRowsParsed();
            observationsIngested += summary.observationsIngested();
            duplicateRowsSkipped += summary.duplicateRowsSkipped();
            revisionsCreated += summary.revisionsCreated();
            validationIssuesCreated += summary.validationIssuesCreated();
            lastArtifactId = summary.sourceArtifactId();
            lastHash = summary.sha256Hash();
            allMessages.addAll(summary.messages());

            currentStart = currentEnd.plusDays(1);
        }

        return new IngestionSummary(
            lastArtifactId,
            lastHash,
            totalParsed,
            totalParsed,
            observationsIngested,
            revisionsCreated,
            duplicateRowsSkipped,
            validationIssuesCreated,
            allMessages
        );
    }

    private int countLatestObservationsInWindow(Long schemeOptionId, LocalDate startDate, LocalDate endDate) {
        int count = 0;
        for (NavObservation observation : navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
            schemeOptionId, startDate, endDate
        )) {
            if (Boolean.TRUE.equals(observation.getLatestRevision())) {
                count++;
            }
        }
        return count;
    }

    private int countWeekdays(LocalDate startDate, LocalDate endDate) {
        int count = 0;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            java.time.DayOfWeek day = cursor.getDayOfWeek();
            if (day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    /**
     * Executes a bounded historical NAV backfill for explicit scheme option IDs with isolated per-fund transactions.
     */
    public TargetedBackfillResult ingestHistoricalNavForSchemeOptions(
        List<Long> schemeOptionIds,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validateDateWindow(startDate, endDate);
        if (schemeOptionIds == null || schemeOptionIds.isEmpty()) {
            throw new IllegalArgumentException("At least one scheme_option_id is required for targeted NAV backfill");
        }

        List<TargetedBackfillFundResult> results = new ArrayList<>();
        for (Long schemeOptionId : new LinkedHashSet<>(schemeOptionIds)) {
            results.add(ingestHistoricalNavForSchemeOptionIsolated(schemeOptionId, startDate, endDate));
        }

        int fundsSucceeded = 0;
        int totalObservationsIngested = 0;
        int totalDuplicatesSkipped = 0;
        int totalRevisionsCreated = 0;
        for (TargetedBackfillFundResult result : results) {
            if ("SUCCESS".equals(result.status()) || "COMPLETE".equals(result.status()) || "ALREADY_COMPLETE".equals(result.status())) {
                fundsSucceeded++;
            }
            if (result.summary() != null) {
                totalObservationsIngested += result.summary().observationsIngested();
                totalDuplicatesSkipped += result.summary().duplicateRowsSkipped();
                totalRevisionsCreated += result.summary().revisionsCreated();
            }
        }

        return new TargetedBackfillResult(
            startDate,
            endDate,
            results.size(),
            fundsSucceeded,
            results.size() - fundsSucceeded,
            totalObservationsIngested,
            totalDuplicatesSkipped,
            totalRevisionsCreated,
            List.copyOf(results)
        );
    }

    /**
     * Isolated per-fund backfill execution path ensuring failure on one fund does not abort or corrupt others.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TargetedBackfillFundResult ingestHistoricalNavForSchemeOptionIsolated(
        Long schemeOptionId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        return ingestHistoricalNavForSchemeOption(schemeOptionId, startDate, endDate);
    }

    /**
     * Executes targeted historical backfill for a single scheme option.
     */
    public TargetedBackfillFundResult ingestHistoricalNavForSchemeOption(
        Long schemeOptionId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        long startTime = System.currentTimeMillis();
        validateDateWindow(startDate, endDate);
        if (schemeOptionId == null) {
            return failedTarget(null, null, null, null, startDate, endDate, "scheme_option_id must not be null", 0L);
        }

        Optional<SchemeOption> option = schemeOptionRepository.findById(schemeOptionId);
        if (option.isEmpty()) {
            return failedTarget(schemeOptionId, null, null, null, startDate, endDate, "Scheme option not found", System.currentTimeMillis() - startTime);
        }

        SchemeOption schemeOption = option.get();
        String schemeName = "Unknown Scheme";
        try {
            if (schemeOption.getPlan() != null && schemeOption.getPlan().getScheme() != null) {
                schemeName = schemeOption.getPlan().getScheme().getName();
            }
        } catch (Exception ignored) {}

        if (schemeOption.getAmfiCode() == null || schemeOption.getAmfiCode().isBlank()) {
            return failedTarget(
                schemeOptionId,
                schemeOption.getAmfiCode(),
                schemeName,
                schemeOption.getIsin(),
                startDate,
                endDate,
                "Scheme option has no AMFI scheme code",
                System.currentTimeMillis() - startTime
            );
        }

        try {
            IngestionSummary summary = ingestHistoricalNavInChunksForSchemeOption(
                schemeOptionId,
                schemeOption.getAmfiCode(),
                null,
                startDate,
                endDate
            );

            // Determine earliest and latest persisted dates
            List<NavObservation> obs = navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                schemeOptionId, startDate, endDate
            );
            LocalDate earliest = obs.isEmpty() ? null : obs.get(0).getEffectiveDate();
            LocalDate latest = obs.isEmpty() ? null : obs.get(obs.size() - 1).getEffectiveDate();

            List<Long> artifactIds = summary.sourceArtifactId() != null ? List.of(summary.sourceArtifactId()) : List.of();
            List<String> artifactHashes = summary.sha256Hash() != null ? List.of(summary.sha256Hash()) : List.of();

            int windowsRequested = (int) Math.ceil((endDate.toEpochDay() - startDate.toEpochDay() + 1) / 90.0);
            int windowsCompleted = obs.size() > 0 ? windowsRequested : 0;

            String status = (obs.isEmpty() && (summary == null || (summary.observationsIngested() == 0 && summary.duplicateRowsSkipped() == 0))) ? "NO_DATA" : "SUCCESS";

            return new TargetedBackfillFundResult(
                schemeOptionId,
                schemeOption.getAmfiCode(),
                schemeName,
                schemeOption.getIsin(),
                startDate,
                endDate,
                earliest,
                latest,
                windowsRequested,
                windowsCompleted,
                0,
                0,
                summary.observationsIngested(),
                summary.duplicateRowsSkipped(),
                summary.validationIssuesCreated(),
                status,
                null,
                artifactIds,
                artifactHashes,
                System.currentTimeMillis() - startTime,
                summary,
                "Historical NAV backfill completed successfully"
            );
        } catch (Exception e) {
            log.error("Targeted historical NAV backfill failed for scheme_option_id {} / AMFI {}: {}",
                schemeOptionId, schemeOption.getAmfiCode(), e.getMessage(), e);
            return failedTarget(
                schemeOptionId,
                schemeOption.getAmfiCode(),
                schemeName,
                schemeOption.getIsin(),
                startDate,
                endDate,
                e.getMessage(),
                System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * Selects a reproducible, multi-AMC deterministic backfill cohort across >= 10 AMCs, Direct/Regular, Growth/IDCW,
     * Equity/Debt/Hybrid categories, prioritizing options that need historical depth for analytical readiness.
     */
    public record SchemeOptionMeta(
        SchemeOption option,
        String tier,
        long count,
        LocalDate minDate,
        LocalDate maxDate,
        long daysSpan,
        boolean is3YCapable,
        String inceptionStatus
    ) {
        public SchemeOptionMeta(
            SchemeOption option,
            String tier,
            long count,
            LocalDate minDate,
            LocalDate maxDate,
            long daysSpan
        ) {
            this(option, tier, count, minDate, maxDate, daysSpan, true, "CAPABLE");
        }
    }

    /**
     * Categorizes all registered scheme options into production priority tiers:
     * - TIER_1: Active options with >=1Y but <3Y history and 3Y capable (highest yield 3Y candidates).
     * - TIER_2: Active options with <1Y history and 3Y capable.
     * - TIER_3: Active options with zero NAV history and 3Y capable.
     * - TIER_4: Deep history candidates (>=3Y history).
     * - SHORT_FUND_HISTORY: Active options where fund inception date is too recent to reach 3Y.
     * - INACTIVE: Options marked inactive.
     */
    @Transactional(readOnly = true)
    public List<SchemeOptionMeta> categorizeSchemeOptions() {
        return categorizeSchemeOptions(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<SchemeOptionMeta> categorizeSchemeOptions(LocalDate targetEnd) {
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        List<Object[]> summaries = navObservationRepository.findSchemeOptionNavDateSummaries();

        Map<Long, Object[]> summaryMap = new HashMap<>();
        for (Object[] row : summaries) {
            Long optId = (Long) row[0];
            summaryMap.put(optId, row);
        }

        LocalDate target = targetEnd != null ? targetEnd : LocalDate.now();
        LocalDate cutoff3Y = target.minusDays(1095);

        List<SchemeOptionMeta> result = new ArrayList<>();
        for (SchemeOption option : allOptions) {
            if (option.getAmfiCode() == null || option.getAmfiCode().isBlank()) continue;

            Object[] row = summaryMap.get(option.getId());
            long count = row != null && row[3] != null ? ((Number) row[3]).longValue() : 0L;
            LocalDate minDate = row != null ? (LocalDate) row[1] : null;
            LocalDate maxDate = row != null ? (LocalDate) row[2] : null;

            long daysSpan = (minDate != null && maxDate != null) ? ChronoUnit.DAYS.between(minDate, maxDate) + 1 : 0L;

            boolean is3YCapable = true;
            String inceptionStatus = "CAPABLE";

            Scheme scheme = option.getPlan() != null ? option.getPlan().getScheme() : null;
            if (scheme != null && scheme.getInceptionDate() != null) {
                if (scheme.getInceptionDate().isAfter(cutoff3Y)) {
                    is3YCapable = false;
                    inceptionStatus = "TOO_RECENT";
                }
            }

            boolean isActive = "ACTIVE".equalsIgnoreCase(option.getStatus());

            String tier;
            if (!isActive) {
                tier = "INACTIVE";
            } else if (!is3YCapable) {
                tier = "SHORT_FUND_HISTORY";
            } else if (daysSpan >= 365 && daysSpan < 1095) {
                tier = "TIER_1"; // >=1Y but <3Y history and 3Y capable
            } else if (count > 0 && daysSpan < 365) {
                tier = "TIER_2"; // <1Y history and 3Y capable
            } else if (count == 0) {
                tier = "TIER_3"; // Zero history active candidate and 3Y capable
            } else {
                tier = "TIER_4"; // Deep history candidate (>=3Y)
            }

            result.add(new SchemeOptionMeta(option, tier, count, minDate, maxDate, daysSpan, is3YCapable, inceptionStatus));
        }

        return result;
    }

    /**
     * Selects a deterministic production backfill cohort prioritized by analytical historical value.
     */
    @Transactional(readOnly = true)
    public List<SchemeOption> selectProductionCohort(int targetSize, String tierFilter) {
        return selectProductionCohort(targetSize, tierFilter, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<SchemeOption> selectProductionCohort(int targetSize, String tierFilter, LocalDate targetEnd) {
        List<SchemeOptionMeta> categorized = categorizeSchemeOptions(targetEnd);

        Map<String, List<SchemeOptionMeta>> byTier = new LinkedHashMap<>();
        byTier.put("TIER_1", new ArrayList<>());
        byTier.put("TIER_2", new ArrayList<>());
        byTier.put("TIER_3", new ArrayList<>());
        byTier.put("TIER_4", new ArrayList<>());

        for (SchemeOptionMeta meta : categorized) {
            if (byTier.containsKey(meta.tier())) {
                byTier.get(meta.tier()).add(meta);
            }
        }

        byTier.get("TIER_1").sort(Comparator.comparingLong(SchemeOptionMeta::daysSpan).reversed().thenComparing(m -> m.option().getId()));
        byTier.get("TIER_2").sort(Comparator.comparingLong(SchemeOptionMeta::daysSpan).reversed().thenComparing(m -> m.option().getId()));
        byTier.get("TIER_3").sort(Comparator.comparing(m -> m.option().getId()));
        byTier.get("TIER_4").sort(Comparator.comparingLong(SchemeOptionMeta::daysSpan).thenComparing(m -> m.option().getId()));

        List<String> tierOrder;
        if (tierFilter != null && !tierFilter.isBlank() && !"ALL".equalsIgnoreCase(tierFilter)) {
            tierOrder = List.of(tierFilter.trim().toUpperCase());
        } else {
            tierOrder = List.of("TIER_1", "TIER_2", "TIER_3", "TIER_4");
        }

        List<SchemeOption> selected = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();

        for (String tierKey : tierOrder) {
            List<SchemeOptionMeta> candidates = byTier.getOrDefault(tierKey, List.of());
            if (candidates.isEmpty()) continue;

            Map<String, List<SchemeOptionMeta>> amcGroups = new LinkedHashMap<>();
            for (SchemeOptionMeta meta : candidates) {
                String amcCode = meta.option().getPlan() != null && meta.option().getPlan().getScheme() != null &&
                    meta.option().getPlan().getScheme().getAmc() != null ? meta.option().getPlan().getScheme().getAmc().getCode() : "UNKNOWN";
                amcGroups.computeIfAbsent(amcCode, k -> new ArrayList<>()).add(meta);
            }

            boolean addedInRound = true;
            int roundIndex = 0;
            while (addedInRound && selected.size() < targetSize) {
                addedInRound = false;
                for (Map.Entry<String, List<SchemeOptionMeta>> entry : amcGroups.entrySet()) {
                    List<SchemeOptionMeta> optionsInAmc = entry.getValue();
                    if (roundIndex < optionsInAmc.size()) {
                        SchemeOptionMeta candidate = optionsInAmc.get(roundIndex);
                        if (!selectedIds.contains(candidate.option().getId())) {
                            selected.add(candidate.option());
                            selectedIds.add(candidate.option().getId());
                            addedInRound = true;
                            if (selected.size() >= targetSize) break;
                        }
                    }
                }
                roundIndex++;
            }

            if (selected.size() >= targetSize) break;
        }

        log.info("Selected production cohort of {} options (tierFilter={}, targetSize={})", selected.size(), tierFilter, targetSize);
        return selected;
    }

    /**
     * Backwards-compatible deterministic cohort selector.
     */
    @Transactional(readOnly = true)
    public List<SchemeOption> selectDeterministicCohort(int targetSize) {
        return selectProductionCohort(targetSize, "ALL");
    }

    /**
     * Previews a deterministic production historical NAV backfill cohort without performing HTTP requests or DB mutations.
     */
    @Transactional(readOnly = true)
    public HistoricalBackfillPreviewDto previewProductionCohort(
        int batchSize,
        String tierFilter,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validateDateWindow(startDate, endDate);
        List<SchemeOption> cohort = selectProductionCohort(batchSize, tierFilter, endDate);
        List<SchemeOptionMeta> allMeta = categorizeSchemeOptions(endDate);

        long totalActiveOptions = schemeOptionRepository.countByStatus("ACTIVE");
        long already1YCount = 0;
        long lessThan1YCount = 0;
        long zeroNavCount = 0;
        long already3YCount = 0;
        long capable3YCount = 0;
        long incapableInceptionCount = 0;

        // 1Y Readiness Funnel Breakdown
        long ready5YCount = 0;
        long ready3YCount = 0;
        long ready1YCount = 0;
        long days180To364Count = 0;
        long days90To179Count = 0;
        long days1To89Count = 0;
        long shortInception1YCount = 0;
        long capable1YCount = 0;

        // Zero-NAV Root Cause Breakdown
        long zeroNavNewFundCount = 0;
        long zeroNavSourceNotIngestedCount = 0;
        long zeroNavSourceNoHistoryCount = 0;
        long zeroNavIdentityMappingCount = 0;
        long zeroNavCatalogQualityCount = 0;
        long zeroNavOtherCount = 0;

        LocalDate cutoff1Y = endDate.minusDays(365);

        Map<String, Integer> topUnderCoveredAmcs = new LinkedHashMap<>();
        Map<Long, SchemeOptionMeta> metaMap = new HashMap<>();

        for (SchemeOptionMeta m : allMeta) {
            metaMap.put(m.option().getId(), m);

            if (!m.is3YCapable()) {
                incapableInceptionCount++;
            } else {
                capable3YCount++;
            }

            if ("TIER_1".equals(m.tier())) {
                already1YCount++;
            } else if ("TIER_2".equals(m.tier())) {
                lessThan1YCount++;
            } else if ("TIER_3".equals(m.tier())) {
                zeroNavCount++;
            } else if ("TIER_4".equals(m.tier())) {
                already3YCount++;
            }

            if ("TIER_1".equals(m.tier()) || "TIER_2".equals(m.tier()) || "TIER_3".equals(m.tier())) {
                String amcName = m.option().getPlan() != null && m.option().getPlan().getScheme() != null && m.option().getPlan().getScheme().getAmc() != null
                    ? m.option().getPlan().getScheme().getAmc().getLegalName() : "Unknown AMC";
                topUnderCoveredAmcs.put(amcName, topUnderCoveredAmcs.getOrDefault(amcName, 0) + 1);
            }

            // 1Y Readiness Funnel & Zero-NAV Root Cause Analysis for Active Options
            boolean isActive = "ACTIVE".equalsIgnoreCase(m.option().getStatus());
            if (isActive) {
                Scheme scheme = m.option().getPlan() != null ? m.option().getPlan().getScheme() : null;
                LocalDate inception = scheme != null ? scheme.getInceptionDate() : null;
                boolean is1YCapable = inception == null || !inception.isAfter(cutoff1Y);

                if (is1YCapable) {
                    capable1YCount++;
                } else {
                    shortInception1YCount++;
                }

                long days = m.daysSpan();
                long count = m.count();

                if (days >= 1825) {
                    ready5YCount++;
                } else if (days >= 1095) {
                    ready3YCount++;
                } else if (days >= 365) {
                    ready1YCount++;
                } else if (count > 0 && days >= 180) {
                    days180To364Count++;
                } else if (count > 0 && days >= 90) {
                    days90To179Count++;
                } else if (count > 0 && days >= 1) {
                    days1To89Count++;
                } else if (count == 0) {
                    String amfiCode = m.option().getAmfiCode();
                    if (amfiCode == null || amfiCode.isBlank() || !amfiCode.matches("\\d+")) {
                        zeroNavIdentityMappingCount++;
                    } else if (!is1YCapable) {
                        zeroNavNewFundCount++;
                    } else {
                        boolean isKnownSourceCode = sourceArtifactRepository.existsByStorageUriContaining(amfiCode);
                        if (isKnownSourceCode) {
                            zeroNavSourceNotIngestedCount++;
                        } else if (amfiCode.length() != 6) {
                            zeroNavCatalogQualityCount++;
                        } else {
                            zeroNavSourceNoHistoryCount++;
                        }
                    }
                } else {
                    zeroNavOtherCount++;
                }
            }
        }

        topUnderCoveredAmcs = topUnderCoveredAmcs.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        long candidateCount3Y = already1YCount + lessThan1YCount + zeroNavCount;

        Map<String, Integer> tierBreakdown = new LinkedHashMap<>();
        Map<String, Integer> amcBreakdown = new LinkedHashMap<>();
        int directCount = 0;
        int regularCount = 0;
        int growthCount = 0;
        int idcwCount = 0;
        long estimatedDbInsertions = 0;

        List<HistoricalBackfillPreviewDto.SchemeOptionPreviewItem> previewItems = new ArrayList<>();
        double targetYears = Math.round((ChronoUnit.DAYS.between(startDate, endDate) + 1) / 365.25 * 100.0) / 100.0;

        for (SchemeOption option : cohort) {
            SchemeOptionMeta meta = metaMap.get(option.getId());
            String amcName = option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null
                ? option.getPlan().getScheme().getAmc().getLegalName() : "Unknown AMC";
            String planType = option.getPlan() != null ? option.getPlan().getPlanType() : "UNKNOWN";
            String optionType = option.getOptionType() != null ? option.getOptionType() : "UNKNOWN";
            String tier = meta != null ? meta.tier() : "TIER_3";

            if ("DIRECT".equalsIgnoreCase(planType)) directCount++;
            else regularCount++;

            if ("GROWTH".equalsIgnoreCase(optionType)) growthCount++;
            else if (optionType != null && optionType.startsWith("IDCW")) idcwCount++;

            tierBreakdown.put(tier, tierBreakdown.getOrDefault(tier, 0) + 1);
            amcBreakdown.put(amcName, amcBreakdown.getOrDefault(amcName, 0) + 1);

            double currentYears = meta != null ? Math.round((meta.daysSpan() / 365.25) * 100.0) / 100.0 : 0.0;
            long shortfallDays = Math.max(0, (long) (targetYears * 365.25) - (meta != null ? meta.daysSpan() : 0));

            estimatedDbInsertions += (long) ((shortfallDays / 365.25) * 250);

            previewItems.add(new HistoricalBackfillPreviewDto.SchemeOptionPreviewItem(
                option.getId(),
                option.getAmfiCode(),
                option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getName() : "Unknown Scheme",
                amcName,
                planType,
                optionType,
                tier,
                meta != null ? meta.count() : 0L,
                meta != null ? meta.minDate() : null,
                meta != null ? meta.maxDate() : null,
                currentYears,
                targetYears,
                shortfallDays,
                meta != null ? meta.is3YCapable() : true,
                meta != null ? meta.inceptionStatus() : "CAPABLE"
            ));
        }

        int estimatedChunks = (int) Math.ceil((endDate.toEpochDay() - startDate.toEpochDay() + 1) / 90.0);

        return new HistoricalBackfillPreviewDto(
            cohort.size(),
            amcBreakdown.size(),
            directCount,
            regularCount,
            growthCount,
            idcwCount,
            tierBreakdown,
            amcBreakdown,
            estimatedChunks,
            true, // isDryRun
            previewItems,
            totalActiveOptions,
            candidateCount3Y,
            already1YCount,
            lessThan1YCount,
            zeroNavCount,
            already3YCount,
            capable3YCount,
            incapableInceptionCount,
            topUnderCoveredAmcs,
            estimatedDbInsertions,
            ready5YCount,
            ready3YCount,
            ready1YCount,
            days180To364Count,
            days90To179Count,
            days1To89Count,
            shortInception1YCount,
            capable1YCount,
            zeroNavNewFundCount,
            zeroNavSourceNotIngestedCount,
            zeroNavSourceNoHistoryCount,
            zeroNavIdentityMappingCount,
            zeroNavCatalogQualityCount,
            zeroNavOtherCount
        );
    }

    /**
     * Executes production cohort historical NAV backfill using shared universe chunk artifacts.
     */
    @Transactional
    public HistoricalBackfillRunSummaryDto executeProductionCohortBackfill(
        int batchSize,
        String tierFilter,
        LocalDate startDate,
        LocalDate endDate
    ) {
        List<SchemeOption> cohort = selectProductionCohort(batchSize, tierFilter, endDate);
        return executeProductionCohortBackfillForCohort(cohort, batchSize, startDate, endDate);
    }

    /**
     * Executes historical NAV backfill for an explicit list of SchemeOptions using shared universe chunk artifacts.
     */
    @Transactional
    public HistoricalBackfillRunSummaryDto executeProductionCohortBackfillForCohort(
        List<SchemeOption> cohort,
        int requestedBatchSize,
        LocalDate startDate,
        LocalDate endDate
    ) {
        long startTime = System.currentTimeMillis();
        OffsetDateTime startedAt = OffsetDateTime.now();
        String runId = UUID.randomUUID().toString();
        validateDateWindow(startDate, endDate);

        long totalActiveOptions = schemeOptionRepository.countByStatus("ACTIVE");
        long beforeAnyNav = navObservationRepository.findSchemeOptionNavDateSummaries().size();
        long before1Y = countOptionsWithHistoryDays(365);
        long before3Y = countOptionsWithHistoryDays(1095);
        long before5Y = countOptionsWithHistoryDays(1825);

        if (cohort == null || cohort.isEmpty()) {
            OffsetDateTime completedAt = OffsetDateTime.now();
            return new HistoricalBackfillRunSummaryDto(
                runId, startedAt, completedAt, "MANUAL_COHORT", requestedBatchSize, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, System.currentTimeMillis() - startTime, 0.0, List.of(),
                beforeAnyNav, beforeAnyNav, before1Y, before1Y, before3Y, before3Y, before5Y, before5Y,
                0L, Math.max(0, totalActiveOptions - before3Y), 0L, Math.max(0, totalActiveOptions - before5Y),
                0L, 0L, 0L, 0.0
            );
        }

        Set<String> targetAmfiCodes = cohort.stream()
            .map(SchemeOption::getAmfiCode)
            .filter(c -> c != null && !c.isBlank())
            .map(String::trim)
            .collect(Collectors.toSet());

        int sourceRequestsMade = 0;
        int sourceRequestsSkipped = 0;
        int failedRequests = 0;
        int totalInserted = 0;
        int totalDuplicates = 0;
        int totalRevisions = 0;

        long downloadTimeMs = 0;
        long parseTimeMs = 0;
        long persistTimeMs = 0;

        // Ingest in 90-day shared universe chunks
        LocalDate currentStart = startDate;
        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = currentStart.plusDays(89);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }

            String targetUrl = amfiSourceClient.buildUniverseChunkUrl(currentStart, currentEnd);
            boolean artifactExists = sourceArtifactRepository.findFirstByStorageUri(targetUrl).isPresent();

            if (artifactExists) {
                sourceRequestsSkipped++;
            } else {
                sourceRequestsMade++;
            }

            try {
                long t0 = System.currentTimeMillis();
                SourceArtifact artifact = amfiSourceClient.fetchAndPersistUniverseChunkArtifact(currentStart, currentEnd);
                long t1 = System.currentTimeMillis();
                downloadTimeMs += (t1 - t0);

                IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, targetAmfiCodes);
                long t2 = System.currentTimeMillis();
                persistTimeMs += (t2 - t1);

                totalInserted += summary.observationsIngested();
                totalDuplicates += summary.duplicateRowsSkipped();
                totalRevisions += summary.revisionsCreated();
            } catch (Exception e) {
                log.error("Failed to fetch/ingest universe chunk {} to {}: {}", currentStart, currentEnd, e.getMessage(), e);
                failedRequests++;
            }

            currentStart = currentEnd.plusDays(1);
        }

        // Aggregate per-fund results
        List<TargetedBackfillFundResult> fundResults = new ArrayList<>();
        int completedCount = 0;
        int partialCount = 0;
        int failedCount = 0;
        int noDataCount = 0;
        int shortHistoryCount = 0;
        int alreadyCompleteCount = 0;

        for (SchemeOption option : cohort) {
            List<NavObservation> obs = navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                option.getId(), startDate, endDate
            );

            String schemeName = option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getName() : "Unknown";
            LocalDate earliest = obs.isEmpty() ? null : obs.getFirst().getEffectiveDate();
            LocalDate latest = obs.isEmpty() ? null : obs.getLast().getEffectiveDate();

            String fundStatus;
            if (obs.isEmpty()) {
                fundStatus = "NO_DATA";
                noDataCount++;
            } else {
                int expectedDays = countWeekdays(startDate, endDate);
                if (earliest != null && earliest.isAfter(startDate.plusDays(30))) {
                    fundStatus = "SHORT_FUND_HISTORY";
                    shortHistoryCount++;
                } else if (obs.size() >= Math.max(1, expectedDays - 5)) {
                    fundStatus = "COMPLETE";
                    completedCount++;
                } else {
                    fundStatus = "SOURCE_DATA_LIMITED";
                    partialCount++;
                }
            }

            int requestedChunks = (int) Math.ceil((endDate.toEpochDay() - startDate.toEpochDay() + 1) / 90.0);
            int obsCount = obs.size();

            fundResults.add(new TargetedBackfillFundResult(
                option.getId(),
                option.getAmfiCode(),
                schemeName,
                option.getIsin(),
                startDate,
                endDate,
                earliest,
                latest,
                requestedChunks,
                obsCount > 0 ? requestedChunks : 0,
                0,
                obs.isEmpty() ? requestedChunks : 0,
                obsCount,
                0,
                0,
                fundStatus,
                null,
                List.of(),
                List.of(),
                0L,
                null,
                "Cohort backfill completed"
            ));
        }

        long afterAnyNav = navObservationRepository.findSchemeOptionNavDateSummaries().size();
        long after1Y = countOptionsWithHistoryDays(365);
        long after3Y = countOptionsWithHistoryDays(1095);
        long after5Y = countOptionsWithHistoryDays(1825);

        long newly3YReadyCount = Math.max(0, after3Y - before3Y);
        long remaining3YGap = Math.max(0, totalActiveOptions - after3Y);
        long newly5YReadyCount = Math.max(0, after5Y - before5Y);
        long remaining5YGap = Math.max(0, totalActiveOptions - after5Y);

        OffsetDateTime completedAt = OffsetDateTime.now();
        long durationMs = System.currentTimeMillis() - startTime;
        double throughput = durationMs > 0 ? (totalInserted * 1000.0) / durationMs : 0.0;
        double optionsPerMin = durationMs > 0 ? (cohort.size() * 60000.0) / durationMs : 0.0;

        return new HistoricalBackfillRunSummaryDto(
            runId,
            startedAt,
            completedAt,
            "MANUAL_COHORT",
            requestedBatchSize,
            cohort.size(),
            completedCount,
            partialCount,
            failedCount,
            noDataCount,
            shortHistoryCount,
            alreadyCompleteCount,
            totalInserted,
            totalDuplicates,
            totalRevisions,
            sourceRequestsMade,
            sourceRequestsSkipped,
            failedRequests,
            durationMs,
            throughput,
            fundResults,
            beforeAnyNav,
            afterAnyNav,
            before1Y,
            after1Y,
            before3Y,
            after3Y,
            before5Y,
            after5Y,
            newly3YReadyCount,
            remaining3YGap,
            newly5YReadyCount,
            remaining5YGap,
            downloadTimeMs,
            parseTimeMs,
            persistTimeMs,
            optionsPerMin
        );
    }

    private long countOptionsWithHistoryDays(long days) {
        long count = 0;
        for (Object[] row : navObservationRepository.findSchemeOptionNavDateSummaries()) {
            LocalDate minDate = (LocalDate) row[1];
            LocalDate maxDate = (LocalDate) row[2];
            if (minDate != null && maxDate != null && ChronoUnit.DAYS.between(minDate, maxDate) + 1 >= days) {
                count++;
            }
        }
        return count;
    }

    /**
     * Executes historical NAV backfill for the deterministic multi-AMC cohort over a 3-year date window.
     */
    public TargetedBackfillResult executeDeterministicCohortBackfill(int targetCohortSize, LocalDate startDate, LocalDate endDate) {
        List<SchemeOption> cohort = selectProductionCohort(targetCohortSize, "ALL");
        List<Long> optionIds = cohort.stream().map(SchemeOption::getId).toList();
        return ingestHistoricalNavForSchemeOptions(optionIds, startDate, endDate);
    }

    private void validateDateWindow(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date");
        }
    }

    private TargetedBackfillFundResult failedTarget(
        Long schemeOptionId,
        String amfiCode,
        String schemeName,
        String isin,
        LocalDate startDate,
        LocalDate endDate,
        String message,
        long durationMs
    ) {
        return new TargetedBackfillFundResult(
            schemeOptionId,
            amfiCode,
            schemeName,
            isin,
            startDate,
            endDate,
            null,
            null,
            1,
            0,
            0,
            1,
            0,
            0,
            0,
            "FAILED",
            message,
            List.of(),
            List.of(),
            durationMs,
            null,
            message
        );
    }

    /**
     * Executes controlled sample historical NAV load across 5 representative funds.
     */
    @Transactional
    public Map<String, Object> ingestControlledSample() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 15);

        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> fundSummaries = new ArrayList<>();
        int totalIngested = 0;
        int totalSkipped = 0;

        for (ControlledFundTarget target : CONTROLLED_SAMPLE_TARGETS) {
            Map<String, Object> fundInfo = new LinkedHashMap<>();
            fundInfo.put("label", target.label());
            fundInfo.put("amfiCode", target.amfiCode());
            fundInfo.put("isin", target.isin());
            fundInfo.put("category", target.expectedCategory());
            fundInfo.put("planType", target.planType());
            fundInfo.put("optionType", target.optionType());

            try {
                IngestionSummary summary = ingestHistoricalNav(target.amfiCode(), target.amcMfCode(), startDate, endDate);
                fundInfo.put("status", "SUCCESS");
                fundInfo.put("observationsIngested", summary.observationsIngested());
                fundInfo.put("duplicatesSkipped", summary.duplicateRowsSkipped());
                fundInfo.put("sourceArtifactId", summary.sourceArtifactId());
                fundInfo.put("sourceHash", summary.sha256Hash());

                totalIngested += summary.observationsIngested();
                totalSkipped += summary.duplicateRowsSkipped();

                Optional<SchemeOption> opt = schemeOptionRepository.findByAmfiCode(target.amfiCode());
                if (opt.isPresent()) {
                    List<NavObservation> obs = navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                        opt.get().getId(), startDate, endDate
                    );
                    fundInfo.put("persistedObservationCount", obs.size());
                    if (!obs.isEmpty()) {
                        fundInfo.put("minDate", obs.get(0).getEffectiveDate().toString());
                        fundInfo.put("maxDate", obs.get(obs.size() - 1).getEffectiveDate().toString());
                        fundInfo.put("firstNav", obs.get(0).getNavValue().toPlainString());
                        fundInfo.put("lastNav", obs.get(obs.size() - 1).getNavValue().toPlainString());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to ingest sample NAV for {}: {}", target.label(), e.getMessage());
                fundInfo.put("status", "FAILED");
                fundInfo.put("error", e.getMessage());
            }

            fundSummaries.add(fundInfo);
        }

        report.put("startDate", startDate.toString());
        report.put("endDate", endDate.toString());
        report.put("totalFunds", CONTROLLED_SAMPLE_TARGETS.size());
        report.put("totalObservationsIngested", totalIngested);
        report.put("totalDuplicatesSkipped", totalSkipped);
        report.put("funds", fundSummaries);

        return report;
    }

    /**
     * Read-only audit method retrieving coverage stats for controlled sample.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getControlledSampleCoverage() {
        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> fundSummaries = new ArrayList<>();

        for (ControlledFundTarget target : CONTROLLED_SAMPLE_TARGETS) {
            Map<String, Object> fundInfo = new LinkedHashMap<>();
            fundInfo.put("label", target.label());
            fundInfo.put("amfiCode", target.amfiCode());
            fundInfo.put("isin", target.isin());
            fundInfo.put("expectedCategory", target.expectedCategory());
            fundInfo.put("planType", target.planType());
            fundInfo.put("optionType", target.optionType());

            Optional<SchemeOption> optionOpt = schemeOptionRepository.findByAmfiCode(target.amfiCode());
            if (optionOpt.isPresent()) {
                SchemeOption option = optionOpt.get();
                fundInfo.put("catalogStatus", "REGISTERED");
                fundInfo.put("schemeOptionId", option.getId());

                long obsCount = navObservationRepository.countBySchemeOptionId(option.getId());
                fundInfo.put("totalObservations", obsCount);

                List<NavObservation> obsList = navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                    option.getId(), LocalDate.of(2000, 1, 1), LocalDate.of(2030, 1, 1)
                );
                if (!obsList.isEmpty()) {
                    fundInfo.put("earliestDate", obsList.get(0).getEffectiveDate().toString());
                    fundInfo.put("latestDate", obsList.get(obsList.size() - 1).getEffectiveDate().toString());
                    fundInfo.put("earliestNav", obsList.get(0).getNavValue().toPlainString());
                    fundInfo.put("latestNav", obsList.get(obsList.size() - 1).getNavValue().toPlainString());
                }
            } else {
                fundInfo.put("catalogStatus", "NOT_REGISTERED");
                fundInfo.put("totalObservations", 0);
            }

            fundSummaries.add(fundInfo);
        }

        report.put("sampleTargetCount", CONTROLLED_SAMPLE_TARGETS.size());
        report.put("funds", fundSummaries);
        return report;
    }
}
