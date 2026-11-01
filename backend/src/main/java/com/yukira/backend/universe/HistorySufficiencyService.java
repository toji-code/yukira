package com.yukira.backend.universe;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service evaluating historical NAV data sufficiency, point-in-time observation completeness,
 * and multi-year analytical eligibility for reference-universe candidates (TSK-M1-03).
 *
 * Epistemic & Quantitative Rules:
 * 1. Sufficiency is derived from distinct usable trading dates, never inflated by duplicate revisions.
 * 2. Strict separation of concerns: Reference universe membership != History sufficiency != Metric readiness.
 * 3. Operational 700-day threshold is an essential prerequisite for 3Y metrics (RSK-01, RET-03).
 * 4. Point-in-Time (PIT) evaluation enforces knowledge cutoffs and prevents look-ahead bias.
 * 5. Observations with data quality defects or missing cryptographic source artifacts are classified
 *    as DATA_QUALITY_LIMITED, preventing unsafe quantitative calculations.
 */
@Service
public class HistorySufficiencyService {

    private static final Logger log = LoggerFactory.getLogger(HistorySufficiencyService.class);

    public static final int DEFAULT_THRESHOLD = 700;
    public static final String RULE_VERSION = "YUKIRA_V1_M1_700_DAY_THRESHOLD";
    public static final String COUNTING_UNIT = "DISTINCT_TRADING_DAY";

    private final NavObservationRepository navObservationRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final ReferenceUniverseService referenceUniverseService;

    public HistorySufficiencyService(
        NavObservationRepository navObservationRepository,
        SchemeOptionRepository schemeOptionRepository,
        ReferenceUniverseService referenceUniverseService
    ) {
        this.navObservationRepository = navObservationRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.referenceUniverseService = referenceUniverseService;
    }

    /**
     * Evaluates historical NAV sufficiency for a scheme option entity against current persisted state.
     */
    @Transactional(readOnly = true)
    public HistorySufficiencyResult evaluateOption(SchemeOption option) {
        if (option == null) {
            return new HistorySufficiencyResult(
                null, null, null, null, null, null, false,
                HistorySufficiencyStatus.NOT_EVALUATED, 0L, DEFAULT_THRESHOLD,
                null, null, RULE_VERSION, OffsetDateTime.now(),
                "SchemeOption is null.", HistorySufficiencyResult.STANDARD_METRIC_LIMITATIONS
            );
        }

        Long optId = option.getId();
        String amfiCode = option.getAmfiCode();
        String isin = option.getIsin();
        String schemeName = (option.getPlan() != null && option.getPlan().getScheme() != null)
            ? option.getPlan().getScheme().getName() : "Unknown Scheme";
        String amcName = (option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null)
            ? option.getPlan().getScheme().getAmc().getLegalName() : "Unknown AMC";

        UniverseEvaluationResult universeResult = referenceUniverseService.evaluateOption(option);
        boolean inRefUniverse = universeResult.included();
        EquityUniverseCategory category = universeResult.category();

        List<Object[]> rows = navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(optId);

        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return HistorySufficiencyResult.of(
                optId, amfiCode, isin, schemeName, amcName, category, inRefUniverse,
                HistorySufficiencyStatus.NO_HISTORY, 0L, DEFAULT_THRESHOLD,
                null, null,
                "Zero NAV observations recorded in database."
            );
        }

        Object[] summary = rows.get(0);
        LocalDate earliest = summary[0] != null ? (LocalDate) summary[0] : null;
        LocalDate latest = summary[1] != null ? (LocalDate) summary[1] : null;
        long distinctDates = summary[2] != null ? ((Number) summary[2]).longValue() : 0L;
        long invalidCount = summary[3] != null ? ((Number) summary[3]).longValue() : 0L;
        long missingArtifactCount = summary[4] != null ? ((Number) summary[4]).longValue() : 0L;

        return buildResult(
            optId, amfiCode, isin, schemeName, amcName, category, inRefUniverse,
            distinctDates, earliest, latest, invalidCount, missingArtifactCount
        );
    }

    /**
     * Evaluates historical NAV sufficiency for a scheme option entity as of a specific effective date.
     * Applies standard EOD 23:59:59.999+05:30 knowledge cutoff for the analysis date.
     */
    @Transactional(readOnly = true)
    public HistorySufficiencyResult evaluateOptionAsOf(SchemeOption option, LocalDate asOfDate) {
        if (option == null) {
            return evaluateOption(null);
        }
        OffsetDateTime knowledgeCutoff = asOfDate != null
            ? asOfDate.atTime(23, 59, 59).atOffset(java.time.ZoneOffset.ofHoursMinutes(5, 30))
            : OffsetDateTime.now();
        return evaluateOptionAsOf(option.getId(), asOfDate, knowledgeCutoff);
    }

    /**
     * Evaluates historical NAV sufficiency as of a specific Point-in-Time (PIT) analysis and knowledge cutoff.
     */
    @Transactional(readOnly = true)
    public HistorySufficiencyResult evaluateOptionAsOf(
        Long schemeOptionId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime
    ) {
        if (schemeOptionId == null) {
            return new HistorySufficiencyResult(
                null, null, null, null, null, null, false,
                HistorySufficiencyStatus.NOT_EVALUATED, 0L, DEFAULT_THRESHOLD,
                null, null, RULE_VERSION, OffsetDateTime.now(),
                "schemeOptionId is null.", HistorySufficiencyResult.STANDARD_METRIC_LIMITATIONS
            );
        }

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId).orElse(null);
        if (option == null) {
            return new HistorySufficiencyResult(
                schemeOptionId, null, null, null, null, null, false,
                HistorySufficiencyStatus.NOT_EVALUATED, 0L, DEFAULT_THRESHOLD,
                null, null, RULE_VERSION, OffsetDateTime.now(),
                "SchemeOption #" + schemeOptionId + " not found.", HistorySufficiencyResult.STANDARD_METRIC_LIMITATIONS
            );
        }

        LocalDate effectiveCutoff = asOfDate != null ? asOfDate : LocalDate.now();
        OffsetDateTime pitCutoff = knowledgeCutoffTime != null ? knowledgeCutoffTime : OffsetDateTime.now();

        List<NavObservation> pitObservations = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            schemeOptionId, effectiveCutoff, pitCutoff
        );

        String amfiCode = option.getAmfiCode();
        String isin = option.getIsin();
        String schemeName = (option.getPlan() != null && option.getPlan().getScheme() != null)
            ? option.getPlan().getScheme().getName() : "Unknown Scheme";
        String amcName = (option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null)
            ? option.getPlan().getScheme().getAmc().getLegalName() : "Unknown AMC";

        UniverseEvaluationResult universeResult = referenceUniverseService.evaluateOption(option);
        boolean inRefUniverse = universeResult.included();
        EquityUniverseCategory category = universeResult.category();

        if (pitObservations.isEmpty()) {
            return HistorySufficiencyResult.of(
                schemeOptionId, amfiCode, isin, schemeName, amcName, category, inRefUniverse,
                HistorySufficiencyStatus.NO_HISTORY, 0L, DEFAULT_THRESHOLD,
                null, null,
                String.format("Zero PIT-eligible observations known as of %s (knowledge cutoff: %s).", effectiveCutoff, pitCutoff)
            );
        }

        Set<LocalDate> distinctDatesSet = new TreeSet<>();
        long invalidCount = 0;
        long missingArtifactCount = 0;

        for (NavObservation obs : pitObservations) {
            distinctDatesSet.add(obs.getEffectiveDate());
            if ("INVALID".equalsIgnoreCase(obs.getQualityAssessment())) {
                invalidCount++;
            }
            if (obs.getSourceArtifact() == null) {
                missingArtifactCount++;
            }
        }

        long distinctDates = distinctDatesSet.size();
        LocalDate earliest = ((TreeSet<LocalDate>) distinctDatesSet).first();
        LocalDate latest = ((TreeSet<LocalDate>) distinctDatesSet).last();

        return buildResult(
            schemeOptionId, amfiCode, isin, schemeName, amcName, category, inRefUniverse,
            distinctDates, earliest, latest, invalidCount, missingArtifactCount
        );
    }

    private HistorySufficiencyResult buildResult(
        Long optId,
        String amfiCode,
        String isin,
        String schemeName,
        String amcName,
        EquityUniverseCategory category,
        boolean inRefUniverse,
        long distinctDates,
        LocalDate earliest,
        LocalDate latest,
        long invalidCount,
        long missingArtifactCount
    ) {
        HistorySufficiencyStatus status;
        String explanation;

        if (invalidCount > 0 || missingArtifactCount > 0) {
            status = HistorySufficiencyStatus.DATA_QUALITY_LIMITED;
            explanation = String.format(
                "Observations present (%d distinct dates), but quality/provenance compromised: %d invalid observations, %d missing source artifacts.",
                distinctDates, invalidCount, missingArtifactCount
            );
        } else if (distinctDates >= DEFAULT_THRESHOLD) {
            status = HistorySufficiencyStatus.ELIGIBLE;
            explanation = String.format(
                "Satisfies operational minimum history threshold (%d distinct trading days recorded between %s and %s; threshold: %d).",
                distinctDates, earliest, latest, DEFAULT_THRESHOLD
            );
        } else if (distinctDates > 0) {
            status = HistorySufficiencyStatus.INSUFFICIENT_HISTORY;
            explanation = String.format(
                "Insufficient history (%d distinct trading days between %s and %s; %d required for multi-year metrics).",
                distinctDates, earliest, latest, DEFAULT_THRESHOLD
            );
        } else {
            status = HistorySufficiencyStatus.NO_HISTORY;
            explanation = "Zero valid NAV observations recorded in database.";
        }

        return HistorySufficiencyResult.of(
            optId, amfiCode, isin, schemeName, amcName, category, inRefUniverse,
            status, distinctDates, DEFAULT_THRESHOLD, earliest, latest, explanation
        );
    }

    /**
     * Enriches a SchemeOption's transient fields with history sufficiency indicators.
     */
    public void enrichOption(SchemeOption option) {
        if (option == null) return;
        HistorySufficiencyResult result = evaluateOption(option);
        option.setHistorySufficiencyStatus(result.status().name());
        option.setDistinctObservationCount(result.distinctObservationDates());
        option.setEarliestNavDate(result.earliestObservationDate() != null ? result.earliestObservationDate().toString() : null);
        if (result.latestObservationDate() != null) {
            option.setLatestNavDate(result.latestObservationDate().toString());
        }
    }

    /**
     * Generates a universe-wide historical data sufficiency and analytical readiness report.
     */
    @Transactional(readOnly = true)
    public UniverseHistorySufficiencyReportDto generateUniverseSufficiencyReport() {
        List<SchemeOption> referenceOptions = referenceUniverseService.getEligibleReferenceUniverseOptions();
        long totalEvaluated = referenceOptions.size();

        // Query distinct valid summaries in batch
        List<Object[]> summaries = navObservationRepository.findDistinctValidNavSummaries();
        Map<Long, Object[]> summaryMap = new HashMap<>();
        for (Object[] row : summaries) {
            if (row != null && row[0] != null) {
                summaryMap.put(((Number) row[0]).longValue(), row);
            }
        }

        long eligibleCount = 0;
        long insufficientHistoryCount = 0;
        long noHistoryCount = 0;
        long dataQualityLimitedCount = 0;

        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (HistorySufficiencyStatus s : HistorySufficiencyStatus.values()) {
            countByStatus.put(s.name(), 0L);
        }

        Map<String, Long> eligibleByCategory = new LinkedHashMap<>();
        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            eligibleByCategory.put(cat.name(), 0L);
        }

        for (SchemeOption opt : referenceOptions) {
            Object[] row = summaryMap.get(opt.getId());
            HistorySufficiencyStatus status;

            if (row == null) {
                status = HistorySufficiencyStatus.NO_HISTORY;
            } else {
                long distinctDates = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                long invalidCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;
                long missingArtifactCount = row[5] != null ? ((Number) row[5]).longValue() : 0L;

                if (invalidCount > 0 || missingArtifactCount > 0) {
                    status = HistorySufficiencyStatus.DATA_QUALITY_LIMITED;
                } else if (distinctDates >= DEFAULT_THRESHOLD) {
                    status = HistorySufficiencyStatus.ELIGIBLE;
                } else if (distinctDates > 0) {
                    status = HistorySufficiencyStatus.INSUFFICIENT_HISTORY;
                } else {
                    status = HistorySufficiencyStatus.NO_HISTORY;
                }
            }

            countByStatus.put(status.name(), countByStatus.getOrDefault(status.name(), 0L) + 1L);

            switch (status) {
                case ELIGIBLE -> {
                    eligibleCount++;
                    EquityUniverseCategory cat = referenceUniverseService.evaluateOption(opt).category();
                    if (cat != null) {
                        eligibleByCategory.put(cat.name(), eligibleByCategory.getOrDefault(cat.name(), 0L) + 1L);
                    }
                }
                case INSUFFICIENT_HISTORY -> insufficientHistoryCount++;
                case NO_HISTORY -> noHistoryCount++;
                case DATA_QUALITY_LIMITED -> dataQualityLimitedCount++;
                case NOT_EVALUATED -> {}
            }
        }

        List<String> governanceNotes = List.of(
            "Rule Version: " + RULE_VERSION,
            "Counting Unit: " + COUNTING_UNIT + " (distinct effective dates on latest verified observation revisions).",
            "Threshold: " + DEFAULT_THRESHOLD + " distinct trading days required for 3-year multi-year metrics.",
            "Historical availability convention: Analytical EOD convention (23:59:59.999+05:30) applied where source timestamp is omitted by AMFI.",
            "Separation of Concerns: ELIGIBLE history status indicates observation quantity/integrity, not scoring approval or metric validation."
        );

        return new UniverseHistorySufficiencyReportDto(
            RULE_VERSION,
            COUNTING_UNIT,
            DEFAULT_THRESHOLD,
            totalEvaluated,
            eligibleCount,
            insufficientHistoryCount,
            noHistoryCount,
            dataQualityLimitedCount,
            countByStatus,
            eligibleByCategory,
            OffsetDateTime.now(),
            HistorySufficiencyResult.STANDARD_METRIC_LIMITATIONS,
            governanceNotes
        );
    }

    /**
     * Derives missing date intervals within a specified window for an option.
     */
    public List<HistoricalNavGapInterval> deriveGapsInWindow(
        Long schemeOptionId,
        LocalDate windowStart,
        LocalDate windowEnd
    ) {
        if (schemeOptionId == null || windowStart == null || windowEnd == null || windowStart.isAfter(windowEnd)) {
            return List.of();
        }
        List<NavObservation> obsList = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(schemeOptionId, windowStart, windowEnd)
            .stream()
            .filter(o -> Boolean.TRUE.equals(o.getLatestRevision()))
            .toList();

        if (obsList.isEmpty()) {
            return List.of(HistoricalNavGapInterval.of(windowStart, windowEnd, "FULL_WINDOW_MISSING"));
        }

        List<HistoricalNavGapInterval> gaps = new ArrayList<>();
        LocalDate firstObsDate = obsList.getFirst().getEffectiveDate();
        if (java.time.temporal.ChronoUnit.DAYS.between(windowStart, firstObsDate) > 4) {
            gaps.add(HistoricalNavGapInterval.of(windowStart, firstObsDate.minusDays(1), "LEADING_GAP"));
        }

        for (int i = 0; i < obsList.size() - 1; i++) {
            LocalDate curr = obsList.get(i).getEffectiveDate();
            LocalDate next = obsList.get(i + 1).getEffectiveDate();
            long dayDiff = java.time.temporal.ChronoUnit.DAYS.between(curr, next);
            if (dayDiff > 4) {
                gaps.add(HistoricalNavGapInterval.of(curr.plusDays(1), next.minusDays(1), "INTERMEDIATE_GAP"));
            }
        }

        LocalDate lastObsDate = obsList.getLast().getEffectiveDate();
        if (java.time.temporal.ChronoUnit.DAYS.between(lastObsDate, windowEnd) > 3) {
            gaps.add(HistoricalNavGapInterval.of(lastObsDate.plusDays(1), windowEnd, "TRAILING_GAP"));
        }

        return gaps;
    }

    /**
     * Evaluates factual coverage and sufficiency for an option under a specific analytical horizon (Phase A3).
     */
    @Transactional(readOnly = true)
    public OptionHorizonCoverageDto evaluateOptionHorizon(
        SchemeOption option,
        AnalyticalHorizon horizon,
        LocalDate asOfDate
    ) {
        if (option == null) {
            return null;
        }

        final AnalyticalHorizon effHorizon = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        final LocalDate effEnd = asOfDate != null ? asOfDate : LocalDate.of(2026, 10, 1);
        final LocalDate effStart = effHorizon.calculateStartDate(effEnd);

        Long optId = option.getId();
        String amfiCode = option.getAmfiCode();
        String isin = option.getIsin();
        String schemeName = (option.getPlan() != null && option.getPlan().getScheme() != null)
            ? option.getPlan().getScheme().getName() : "Unknown Scheme";
        String amcName = (option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null)
            ? option.getPlan().getScheme().getAmc().getLegalName() : "Unknown AMC";
        LocalDate inceptionDate = (option.getPlan() != null && option.getPlan().getScheme() != null)
            ? option.getPlan().getScheme().getInceptionDate() : null;

        EquityUniverseCategory category = referenceUniverseService.evaluateOption(option).category();

        boolean isInceptionBounded = inceptionDate != null && inceptionDate.isAfter(effStart);
        LocalDate boundedIntervalStart = isInceptionBounded ? inceptionDate : effStart;

        // Query observations within [effStart, effEnd]
        List<Object[]> horizonRows = navObservationRepository
            .findDistinctValidNavSummaryBySchemeOptionIdInDateRange(optId, effStart, effEnd);

        long distinctDates = 0;
        long invalidCount = 0;
        long missingArtifactCount = 0;
        LocalDate earliestObs = null;
        LocalDate latestObs = null;

        if (horizonRows != null && !horizonRows.isEmpty() && horizonRows.get(0) != null) {
            Object[] row = horizonRows.get(0);
            earliestObs = row[0] != null ? (LocalDate) row[0] : null;
            latestObs = row[1] != null ? (LocalDate) row[1] : null;
            distinctDates = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            invalidCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            missingArtifactCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;
        }

        // Query all-time observations
        List<Object[]> allTimeRows = navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(optId);
        Long allTimeDates = 0L;
        if (allTimeRows != null && !allTimeRows.isEmpty() && allTimeRows.get(0) != null) {
            allTimeDates = allTimeRows.get(0)[2] != null ? ((Number) allTimeRows.get(0)[2]).longValue() : 0L;
        }

        List<HistoricalNavGapInterval> gaps = deriveGapsInWindow(optId, boundedIntervalStart, effEnd);

        int threshold = effHorizon.getDefaultThreshold();
        boolean isSufficient = distinctDates >= threshold && invalidCount == 0 && missingArtifactCount == 0;

        String coverageStatus;
        String explanation;

        if (distinctDates == 0) {
            coverageStatus = "NO_HISTORY";
            explanation = String.format("Zero NAV observations recorded in database for %s horizon window (%s to %s).",
                effHorizon.getCode(), effStart, effEnd);
        } else if (invalidCount > 0 || missingArtifactCount > 0) {
            coverageStatus = "DATA_QUALITY_LIMITED";
            explanation = String.format("Observations present (%d dates), but compromised: %d invalid, %d missing artifacts.",
                distinctDates, invalidCount, missingArtifactCount);
        } else if (isSufficient) {
            coverageStatus = "SUFFICIENT";
            explanation = String.format("Satisfies %s horizon threshold (%d distinct trading days recorded between %s and %s; threshold: %d; rule: %s).",
                effHorizon.getCode(), distinctDates, earliestObs, latestObs, threshold, effHorizon.getRuleVersion());
        } else if (isInceptionBounded) {
            coverageStatus = "INCEPTION_BOUNDED";
            explanation = String.format("Scheme inception (%s) post-dates %s horizon start (%s). Available history (%d dates) cannot satisfy full %s window.",
                inceptionDate, effHorizon.getCode(), effStart, distinctDates, effHorizon.getCode());
        } else if (!gaps.isEmpty()) {
            coverageStatus = "PARTIAL_WITH_GAPS";
            explanation = String.format("Partial history for %s horizon: %d distinct dates recorded (threshold: %d), %d missing intervals remain to be recovered.",
                effHorizon.getCode(), distinctDates, threshold, gaps.size());
        } else {
            coverageStatus = "INSUFFICIENT_HISTORY";
            explanation = String.format("Insufficient observations for %s horizon: %d distinct dates recorded (threshold: %d).",
                effHorizon.getCode(), distinctDates, threshold);
        }

        return new OptionHorizonCoverageDto(
            optId,
            amfiCode,
            isin,
            schemeName,
            amcName,
            category,
            effHorizon,
            effStart,
            effEnd,
            inceptionDate,
            isInceptionBounded,
            distinctDates,
            threshold,
            isSufficient,
            coverageStatus,
            effHorizon.getMethodologyStatus(),
            earliestObs,
            latestObs,
            allTimeDates,
            gaps.size(),
            gaps,
            explanation
        );
    }

    /**
     * Evaluates multi-horizon coverage across 3Y, 5Y, and 10Y horizons for an option (Phase A3).
     */
    @Transactional(readOnly = true)
    public OptionMultiHorizonCoverageDto evaluateOptionMultiHorizon(
        SchemeOption option,
        LocalDate asOfDate
    ) {
        if (option == null) {
            return null;
        }
        final LocalDate effAsOf = asOfDate != null ? asOfDate : LocalDate.of(2026, 10, 1);

        OptionHorizonCoverageDto cov3Y = evaluateOptionHorizon(option, AnalyticalHorizon.THREE_YEAR, effAsOf);
        OptionHorizonCoverageDto cov5Y = evaluateOptionHorizon(option, AnalyticalHorizon.FIVE_YEAR, effAsOf);
        OptionHorizonCoverageDto cov10Y = evaluateOptionHorizon(option, AnalyticalHorizon.TEN_YEAR, effAsOf);

        Scheme scheme = (option.getPlan() != null) ? option.getPlan().getScheme() : null;
        String schemeName = scheme != null ? scheme.getName() : "Unknown Scheme";
        String amcName = (scheme != null && scheme.getAmc() != null) ? scheme.getAmc().getLegalName() : "Unknown AMC";
        LocalDate inceptionDate = scheme != null ? scheme.getInceptionDate() : null;
        EquityUniverseCategory category = referenceUniverseService.evaluateOption(option).category();

        List<Object[]> allTimeRows = navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(option.getId());
        Long allTimeDates = 0L;
        LocalDate earliestAll = null;
        LocalDate latestAll = null;
        if (allTimeRows != null && !allTimeRows.isEmpty() && allTimeRows.get(0) != null) {
            earliestAll = allTimeRows.get(0)[0] != null ? (LocalDate) allTimeRows.get(0)[0] : null;
            latestAll = allTimeRows.get(0)[1] != null ? (LocalDate) allTimeRows.get(0)[1] : null;
            allTimeDates = allTimeRows.get(0)[2] != null ? ((Number) allTimeRows.get(0)[2]).longValue() : 0L;
        }

        return new OptionMultiHorizonCoverageDto(
            option.getId(),
            option.getAmfiCode(),
            option.getIsin(),
            schemeName,
            amcName,
            category,
            effAsOf,
            inceptionDate,
            allTimeDates,
            earliestAll,
            latestAll,
            cov3Y,
            cov5Y,
            cov10Y,
            OffsetDateTime.now()
        );
    }

    /**
     * Generates a universe-wide coverage report for a specific analytical horizon (Phase A3).
     */
    @Transactional(readOnly = true)
    public UniverseHorizonCoverageReportDto generateUniverseHorizonReport(
        AnalyticalHorizon horizon,
        LocalDate asOfDate
    ) {
        final AnalyticalHorizon effHorizon = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        final LocalDate effEnd = asOfDate != null ? asOfDate : LocalDate.of(2026, 10, 1);
        final LocalDate effStart = effHorizon.calculateStartDate(effEnd);

        List<SchemeOption> refOptions = referenceUniverseService.getEligibleReferenceUniverseOptions();
        List<OptionHorizonCoverageDto> candidates = new ArrayList<>();

        int sufficientCount = 0;
        int inceptionBoundedCount = 0;
        int partialWithGapsCount = 0;
        int insufficientHistoryCount = 0;
        int noHistoryCount = 0;

        Map<String, Integer> categoryBreakdown = new LinkedHashMap<>();
        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            categoryBreakdown.put(cat.name(), 0);
        }

        for (SchemeOption opt : refOptions) {
            OptionHorizonCoverageDto cov = evaluateOptionHorizon(opt, effHorizon, effEnd);
            candidates.add(cov);

            if (cov.category() != null) {
                categoryBreakdown.put(cov.category().name(), categoryBreakdown.getOrDefault(cov.category().name(), 0) + 1);
            }

            switch (cov.coverageStatus()) {
                case "SUFFICIENT" -> sufficientCount++;
                case "INCEPTION_BOUNDED" -> inceptionBoundedCount++;
                case "PARTIAL_WITH_GAPS" -> partialWithGapsCount++;
                case "INSUFFICIENT_HISTORY" -> insufficientHistoryCount++;
                case "NO_HISTORY", "DATA_QUALITY_LIMITED" -> noHistoryCount++;
                default -> {}
            }
        }

        return new UniverseHorizonCoverageReportDto(
            effHorizon,
            effEnd,
            effStart,
            effEnd,
            effHorizon.getDefaultThreshold(),
            effHorizon.getMethodologyStatus(),
            effHorizon.getRuleVersion(),
            refOptions.size(),
            sufficientCount,
            inceptionBoundedCount,
            partialWithGapsCount,
            insufficientHistoryCount,
            noHistoryCount,
            categoryBreakdown,
            candidates,
            OffsetDateTime.now()
        );
    }
}
