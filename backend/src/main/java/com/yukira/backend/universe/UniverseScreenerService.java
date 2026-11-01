package com.yukira.backend.universe;

import com.yukira.backend.benchmark.CategoryBenchmarkMapping;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.NavObservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Investor Comparative Screener Service (TSK-M1-08).
 *
 * Epistemic & Governance Constraints:
 * 1. Derives screener rows directly from authoritative reference-universe options and database NAV observations.
 * 2. Side-by-side metric comparison compares only semantically consistent metrics calculated over comparable 3Y windows.
 * 3. Distinguishes absolute-return metrics (RET-02, RET-03, RSK-01, RSK-03) from candidate-benchmark-relative
 *    and risk-adjusted metrics (MKT-01, MKT-02, RAT-04, REL-01).
 * 4. Preserves CANDIDATE_METHODOLOGY_MAPPING governance status for all category benchmarks.
 * 5. Explains explicit exclusion reasons for funds with partial or insufficient NAV history (< 700 dates).
 * 6. Zero composite scores, star ratings, or buy/hold recommendations.
 */
@Service
public class UniverseScreenerService {

    public static final int DEFAULT_SUFFICIENCY_THRESHOLD = 700;

    public static final List<String> CANONICAL_SCREENER_METRICS = List.of(
        "RET-02", "RET-03", "RSK-01", "RSK-03", "MKT-01", "MKT-02", "RAT-04", "REL-01"
    );

    public static List<UniverseScreenerDto.MetricDefinitionDto> getMetricDefinitionsForHorizon(AnalyticalHorizon horizon) {
        String hCode = (horizon != null) ? horizon.getCode() : "3Y";
        int threshold = (horizon != null) ? horizon.getDefaultThreshold() : DEFAULT_SUFFICIENCY_THRESHOLD;
        int years = (horizon != null) ? horizon.getYears() : 3;
        String windowDesc = years + " Years (>= " + threshold + " aligned daily observations)";
        String irStatus = (horizon != null && horizon != AnalyticalHorizon.THREE_YEAR)
            ? "RESTRICTED_CANDIDATE_METHODOLOGY"
            : "CANDIDATE_METHODOLOGY";
        String irDesc = (horizon != null && horizon != AnalyticalHorizon.THREE_YEAR)
            ? "Information Ratio (" + hCode + "). Note: Phase B1/B2 governance restriction applies on multi-cycle horizons pending formal benchmark calibration."
            : "Information Ratio (annualized active return per unit tracking error). Higher percentile indicates superior active benchmark efficiency.";

        return List.of(
            new UniverseScreenerDto.MetricDefinitionDto(
                "RET-02", "Simple Period Return (" + hCode + ")", "RETURN", "PERCENTAGE",
                windowDesc, false,
                "R = (NAV_end - NAV_start) / NAV_start. Exact point-in-time discrete cumulative holding-period return across boundary dates (unannualized).",
                (horizon == null || horizon == AnalyticalHorizon.THREE_YEAR) ? "OPERATIONAL_BASELINE" : "CANDIDATE_METHODOLOGY",
                "HIGHER_IS_BETTER",
                "Higher percentile indicates higher total holding-period return relative to category peers."
            ),
            new UniverseScreenerDto.MetricDefinitionDto(
                "RET-03", "CAGR (" + hCode + ")", "RETURN", "PERCENTAGE",
                windowDesc, false,
                "CAGR = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1. Annualized geometric compound return across leap-adjusted Julian trading years.",
                "CANDIDATE_METHODOLOGY",
                "HIGHER_IS_BETTER",
                "Higher percentile indicates higher annualized compound return (CAGR) relative to category peers."
            ),
            new UniverseScreenerDto.MetricDefinitionDto(
                "RSK-01", "Annualized Volatility (" + hCode + ")", "RISK", "PERCENTAGE",
                windowDesc, false,
                "sigma = std_dev(daily_log_returns) * sqrt(252). Annualized standard deviation of daily log returns.",
                "CANDIDATE_METHODOLOGY",
                "LOWER_IS_BETTER",
                "Higher percentile indicates higher historical volatility (greater dispersion/risk); lower percentile indicates lower volatility (defensive)."
            ),
            new UniverseScreenerDto.MetricDefinitionDto(
                "RSK-03", "Maximum Drawdown (" + hCode + ")", "RISK", "PERCENTAGE",
                windowDesc, false,
                "MDD = min_t ((NAV_t - Peak_t) / Peak_t). Maximum peak-to-trough percentage decline over " + hCode + " window.",
                "CANDIDATE_METHODOLOGY",
                "HIGHER_IS_BETTER",
                "Higher percentile indicates shallower maximum drawdown (better capital preservation); lower percentile indicates deeper peak-to-trough decline."
            ),
            new UniverseScreenerDto.MetricDefinitionDto(
                "MKT-01", "Systematic Beta (" + hCode + ")", "MARKET_SENSITIVITY", "RATIO",
                windowDesc, true,
                "Beta = Cov(R_fund, R_bench) / Var(R_bench). Sensitivity of fund excess return to category benchmark TRI excess return.",
                "CANDIDATE_METHODOLOGY",
                "DESCRIPTIVE",
                "Systematic Beta relative to category benchmark (target = 1.0). Higher percentile indicates higher market sensitivity."
            ),
            new UniverseScreenerDto.MetricDefinitionDto(
                "MKT-02", "Downside Beta (" + hCode + ")", "MARKET_SENSITIVITY", "RATIO",
                years + " Years (>= " + threshold + " aligned daily observations, >= 100 benchmark down-days)", true,
                "Beta_down = Cov(R_fund, R_bench | R_bench < 0) / Var(R_bench | R_bench < 0). Sensitivity conditioned on negative benchmark return days.",
                "CANDIDATE_METHODOLOGY",
                "LOWER_IS_BETTER",
                "Downside Beta during negative benchmark days. Lower percentile indicates stronger downside protection; higher percentile indicates greater downside participation."
            ),
            new UniverseScreenerDto.MetricDefinitionDto(
                "RAT-04", "Information Ratio (" + hCode + ")", "RISK_ADJUSTED", "RATIO",
                windowDesc, true,
                "IR = (Annualized Active Return) / (Tracking Error). Active return generated per unit of relative benchmark risk.",
                irStatus,
                "HIGHER_IS_BETTER",
                irDesc
            ),
            new UniverseScreenerDto.MetricDefinitionDto(
                "REL-01", "Tracking Error (" + hCode + " Annualized)", "BENCHMARK_ALPHA", "PERCENTAGE",
                windowDesc, true,
                "TE = std_dev(daily_excess_returns) * sqrt(252). Annualized standard deviation of active daily return differences against category TRI benchmark.",
                "CANDIDATE_METHODOLOGY",
                "LOWER_IS_BETTER",
                "Tracking Error against category benchmark. Lower percentile indicates closer index replication; higher percentile indicates higher active divergence."
            )
        );
    }

    public static final List<UniverseScreenerDto.MetricDefinitionDto> METRIC_DEFINITIONS = getMetricDefinitionsForHorizon(AnalyticalHorizon.THREE_YEAR);

    public static final List<String> GOVERNANCE_DISCLAIMERS = List.of(
        "Category benchmark assignments represent candidate methodology conventions under Phase 2H.",
        "Candidate methodology mappings have not been formally approved by the review committee.",
        "Relative metrics (MKT-01, MKT-02, RAT-04, REL-01) depend on candidate category benchmark TRIs.",
        "Category percentiles represent non-parametric empirical distributions within each reference category cohort (DEC-V1-02). They describe historical relative position and do NOT constitute predictions, scores, or investment recommendations.",
        "Calculations are deterministic financial algorithms executed by the Python quant kernel; no LLM approximations.",
        "Zero synthetic NAVs or interpolated returns are injected into production ledgers.",
        "This comparative screener provides empirical, mathematical verification and does NOT constitute investment advice."
    );

    private final ReferenceUniverseService referenceUniverseService;

    private final NavObservationRepository navObservationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;

    @Autowired
    public UniverseScreenerService(
        ReferenceUniverseService referenceUniverseService,
        NavObservationRepository navObservationRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository
    ) {
        this.referenceUniverseService = referenceUniverseService;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
    }

    /**
     * Generates comprehensive, server-derived comparative screener data across the 193 reference-universe options (default 3Y horizon).
     */
    @Transactional(readOnly = true)
    public UniverseScreenerDto.ScreenerResponse getScreenerData(
        EquityUniverseCategory categoryFilter,
        Boolean eligibleOnly,
        LocalDate asOfDate
    ) {
        return getScreenerData(categoryFilter, eligibleOnly, asOfDate, AnalyticalHorizon.THREE_YEAR);
    }

    /**
     * Generates comprehensive, server-derived comparative screener data across the 193 reference-universe options
     * for an explicit institutional horizon (3Y, 5Y, 10Y).
     */
    @Transactional(readOnly = true)
    public UniverseScreenerDto.ScreenerResponse getScreenerData(
        EquityUniverseCategory categoryFilter,
        Boolean eligibleOnly,
        LocalDate asOfDate,
        AnalyticalHorizon horizon
    ) {
        final AnalyticalHorizon effectiveHorizon = (horizon != null) ? horizon : AnalyticalHorizon.THREE_YEAR;
        final LocalDate effectiveAsOfDate = (asOfDate != null) ? asOfDate : LocalDate.of(2026, 10, 1);
        final LocalDate windowStartDate = effectiveHorizon.calculateStartDate(effectiveAsOfDate);
        final int sufficiencyThreshold = effectiveHorizon.getDefaultThreshold();

        List<SchemeOption> refOptions = referenceUniverseService.getEligibleReferenceUniverseOptions();

        // 1. Query distinct valid date counts and date summaries from database
        // All-time historical coverage
        List<Object[]> allTimeSummaries = navObservationRepository.findDistinctValidNavSummaries();
        Map<Long, Object[]> allTimeMap = new HashMap<>();
        for (Object[] row : allTimeSummaries) {
            if (row != null && row[0] != null) {
                allTimeMap.put(((Number) row[0]).longValue(), row);
            }
        }

        // Strict evaluation window coverage [windowStartDate, effectiveAsOfDate]
        List<Object[]> windowSummaries = navObservationRepository.findDistinctValidNavSummariesInDateRange(windowStartDate, effectiveAsOfDate);
        Map<Long, Long> windowCountMap = new HashMap<>();
        for (Object[] row : windowSummaries) {
            if (row != null && row[0] != null && row[3] != null) {
                windowCountMap.put(((Number) row[0]).longValue(), ((Number) row[3]).longValue());
            }
        }

        // 2. Cache category benchmarks and observation counts
        Map<EquityUniverseCategory, Integer> bmCountMap = new EnumMap<>(EquityUniverseCategory.class);
        Map<EquityUniverseCategory, String> bmStatusMap = new EnumMap<>(EquityUniverseCategory.class);
        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            var descOpt = CategoryBenchmarkMapping.getDescriptorForCategory(cat);
            if (descOpt.isPresent()) {
                String bmCode = descOpt.get().benchmarkCode();
                Optional<Benchmark> bm = benchmarkRepository.findByCode(bmCode);
                int count = bm.map(b -> benchmarkObservationRepository.findByBenchmarkId(b.getId()).size()).orElse(0);
                bmCountMap.put(cat, count);
                bmStatusMap.put(cat, count >= sufficiencyThreshold
                    ? "SUFFICIENT_" + effectiveHorizon.getCode() + "_COVERAGE"
                    : (count > 0 ? "PARTIAL_COVERAGE" : "NO_OBSERVATIONS"));
            }
        }

        // 3. Fetch all calculation runs for the options as of date
        List<Long> allOptionIds = refOptions.stream().map(SchemeOption::getId).toList();
        List<CalculationRun> comparisonRuns = calculationRunRepository.findComparisonRunsBySchemeOptionIdsAndAsOfDate(allOptionIds, effectiveAsOfDate);

        // Find option IDs that have no runs on the active asOfDate, and retrieve their latest historical runs
        Set<Long> optionsWithActiveRuns = comparisonRuns.stream()
            .map(r -> r.getSchemeOption().getId())
            .collect(Collectors.toSet());
        List<Long> missingOptionIds = allOptionIds.stream()
            .filter(id -> !optionsWithActiveRuns.contains(id))
            .toList();

        List<CalculationRun> historicalRuns = Collections.emptyList();
        if (!missingOptionIds.isEmpty()) {
            historicalRuns = calculationRunRepository.findComparisonRunsBySchemeOptionIds(missingOptionIds);
        }

        // Combine runs for grouping (active runs first, then historical)
        List<CalculationRun> allRuns = new ArrayList<>(comparisonRuns);
        allRuns.addAll(historicalRuns);

        // Group runs by option ID
        Map<Long, List<CalculationRun>> runsByOption = new HashMap<>();
        for (CalculationRun run : allRuns) {
            runsByOption.computeIfAbsent(run.getSchemeOption().getId(), k -> new ArrayList<>()).add(run);
        }

        // 4. Fetch metrics for all successful runs
        List<Long> successfulRunIds = allRuns.stream()
            .filter(r -> "SUCCESS".equalsIgnoreCase(r.getRunStatus())
                || "COMPLETED".equalsIgnoreCase(r.getRunStatus())
                || "INSUFFICIENT_HISTORY".equalsIgnoreCase(r.getRunStatus())
                || "BENCHMARK_UNAVAILABLE".equalsIgnoreCase(r.getRunStatus())
                || "UNMAPPED".equalsIgnoreCase(r.getRunStatus()))
            .map(CalculationRun::getId)
            .toList();

        Map<Long, Map<String, UniverseScreenerDto.MetricValueDto>> metricsByRunId = new HashMap<>();
        if (!successfulRunIds.isEmpty()) {
            List<MetricResult> results = metricResultRepository.findCanonicalByCalculationRunIdInAndMetricCodeIn(
                successfulRunIds, CANONICAL_SCREENER_METRICS
            );
            for (MetricResult mr : results) {
                Long runId = mr.getCalculationRun().getId();
                metricsByRunId.computeIfAbsent(runId, k -> new LinkedHashMap<>())
                    .put(mr.getMetricCode(), formatMetricValue(mr));
            }
        }

        // Map optionId -> chosen CalculationRun (prioritize active runs with complete canonical metrics, then partial, then historical)
        Map<Long, CalculationRun> latestRunByOption = new HashMap<>();
        for (Map.Entry<Long, List<CalculationRun>> entry : runsByOption.entrySet()) {
            Long optId = entry.getKey();
            List<CalculationRun> optionRuns = entry.getValue();
            CalculationRun chosenRun = null;

            // Prefer active runs matching the requested asOfDate
            List<CalculationRun> activeRuns = optionRuns.stream()
                .filter(r -> effectiveAsOfDate.equals(r.getAsOfDate()))
                .toList();

            List<CalculationRun> candidatePool = !activeRuns.isEmpty() ? activeRuns : optionRuns;

            // 1. Prefer latest run with all canonical metrics having non-null numeric values
            for (CalculationRun r : candidatePool) {
                Map<String, UniverseScreenerDto.MetricValueDto> m = metricsByRunId.get(r.getId());
                if (m != null) {
                    long validCount = m.values().stream().filter(v -> v != null && v.numericValue() != null).count();
                    long horizonMatchCount = m.values().stream().filter(v -> v != null && effectiveHorizon.getCode().equalsIgnoreCase(v.periodType())).count();
                    if (validCount >= CANONICAL_SCREENER_METRICS.size() && (horizonMatchCount > 0 || effectiveHorizon == AnalyticalHorizon.THREE_YEAR)) {
                        chosenRun = r;
                        break;
                    }
                }
            }
            // 2. Fallback to latest run with any canonical metrics having non-null numeric values
            if (chosenRun == null) {
                for (CalculationRun r : candidatePool) {
                    Map<String, UniverseScreenerDto.MetricValueDto> m = metricsByRunId.get(r.getId());
                    if (m != null) {
                        long validCount = m.values().stream().filter(v -> v != null && v.numericValue() != null).count();
                        if (validCount > 0) {
                            chosenRun = r;
                            break;
                        }
                    }
                }
            }
            // 3. Fallback to latest valid/recorded run
            if (chosenRun == null) {
                for (CalculationRun r : candidatePool) {
                    if ("SUCCESS".equalsIgnoreCase(r.getRunStatus())
                        || "COMPLETED".equalsIgnoreCase(r.getRunStatus())
                        || "INSUFFICIENT_HISTORY".equalsIgnoreCase(r.getRunStatus())
                        || "BENCHMARK_UNAVAILABLE".equalsIgnoreCase(r.getRunStatus())
                        || "UNMAPPED".equalsIgnoreCase(r.getRunStatus())) {
                        chosenRun = r;
                        break;
                    }
                }
            }
            // 4. Fallback to latest run overall (e.g. FAILED)
            if (chosenRun == null && !candidatePool.isEmpty()) {
                chosenRun = candidatePool.get(0);
            }

            if (chosenRun != null) {
                latestRunByOption.put(optId, chosenRun);
            }
        }

        // 5. Build items and category breakdown
        List<UniverseScreenerDto.ScreenerItem> allItems = new ArrayList<>();
        Map<String, UniverseScreenerDto.CategorySummary> categorySummaries = new LinkedHashMap<>();

        int totalRef = refOptions.size();
        int totalEligible = 0;
        int totalPartial = 0;
        int totalZero = 0;
        int totalCalculated = 0;
        int allTimeSufficientCount = 0;
        int allTimePartialCount = 0;

        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            categorySummaries.put(cat.name(), new UniverseScreenerDto.CategorySummary(
                cat, cat.getDisplayName(),
                CategoryBenchmarkMapping.getDescriptorForCategory(cat).map(CategoryBenchmarkMapping.BenchmarkDescriptor::benchmarkCode).orElse("UNMAPPED"),
                0, 0, 0, 0
            ));
        }

        for (SchemeOption option : refOptions) {
            Long optId = option.getId();
            EquityUniverseCategory cat = referenceUniverseService.evaluateOption(option).category();

            Object[] allTimeRow = allTimeMap.get(optId);
            long allTimeDates = (allTimeRow != null && allTimeRow[3] != null) ? ((Number) allTimeRow[3]).longValue() : 0L;
            LocalDate earliestNavDate = (allTimeRow != null && allTimeRow[1] != null) ? (LocalDate) allTimeRow[1] : null;
            LocalDate latestNavDate = (allTimeRow != null && allTimeRow[2] != null) ? (LocalDate) allTimeRow[2] : null;

            if (allTimeDates >= sufficiencyThreshold) {
                allTimeSufficientCount++;
            } else {
                allTimePartialCount++;
            }

            long windowDates = windowCountMap.getOrDefault(optId, 0L);
            boolean isEligible = windowDates >= sufficiencyThreshold;
            String suffStatus = isEligible ? "ELIGIBLE" : (windowDates > 0 ? "PARTIAL_HISTORY" : "INSUFFICIENT_HISTORY");
            String exclusionReason = isEligible ? null : String.format(
                "Only %d distinct trading days available in %s window (%s to %s); %d required for %s statistical metrics.",
                windowDates, effectiveHorizon.getCode(), windowStartDate, effectiveAsOfDate, sufficiencyThreshold, effectiveHorizon.getCode()
            );

            if (isEligible) totalEligible++;
            else if (windowDates > 0) totalPartial++;
            else totalZero++;

            String dataFreshnessStatus;
            if (latestNavDate == null) {
                dataFreshnessStatus = "NO_DATA";
            } else if (windowDates < sufficiencyThreshold && !latestNavDate.isBefore(effectiveAsOfDate)) {
                // Option has a snapshot on effectiveAsOfDate (e.g. isolated 2026-10-01 point) but is missing trading days within the window
                dataFreshnessStatus = "STALE_GAP (" + windowDates + "/" + sufficiencyThreshold + "d)";
            } else if (!latestNavDate.isBefore(effectiveAsOfDate)) {
                dataFreshnessStatus = "CURRENT";
            } else {
                long lagDays = java.time.temporal.ChronoUnit.DAYS.between(latestNavDate, effectiveAsOfDate);
                dataFreshnessStatus = lagDays > 30 ? "STALE (" + lagDays + "d lag)" : "SLIGHT_LAG (" + lagDays + "d lag)";
            }

            var descOpt = CategoryBenchmarkMapping.getDescriptorForCategory(cat);
            String bmCode = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::benchmarkCode).orElse("UNMAPPED");
            String bmName = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::benchmarkName).orElse("Unmapped Benchmark");
            String bmGov = descOpt.map(CategoryBenchmarkMapping.BenchmarkDescriptor::methodologyStatus).orElse("UNMAPPED");
            int bmCount = bmCountMap.getOrDefault(cat, 0);
            String bmStatus = bmStatusMap.getOrDefault(cat, "NOT_REGISTERED");

            CalculationRun run = latestRunByOption.get(optId);
            Long runId = run != null ? run.getId() : null;
            Map<String, UniverseScreenerDto.MetricValueDto> optionMetrics = runId != null
                ? metricsByRunId.getOrDefault(runId, Map.of()) : Map.of();

            int validMetricCount = 0;
            String calcStatus;
            if (run != null) {
                if (!optionMetrics.isEmpty()) {
                    validMetricCount = (int) optionMetrics.values().stream()
                        .filter(v -> v != null && v.numericValue() != null && "CALCULATED".equalsIgnoreCase(v.calculationStatus()))
                        .count();
                    if (validMetricCount >= CANONICAL_SCREENER_METRICS.size()) {
                        calcStatus = "CALCULATED";
                    } else if (validMetricCount > 0) {
                        calcStatus = "PARTIALLY_CALCULATED";
                    } else {
                        calcStatus = "INSUFFICIENT_DATA";
                    }
                } else {
                    calcStatus = run.getRunStatus();
                }
            } else {
                calcStatus = isEligible ? "NOT_CALCULATED" : "INSUFFICIENT_DATA";
            }

            if (validMetricCount > 0) {
                totalCalculated++;
            }

            // Update category summary
            UniverseScreenerDto.CategorySummary existingCatSum = categorySummaries.get(cat.name());
            categorySummaries.put(cat.name(), new UniverseScreenerDto.CategorySummary(
                cat, cat.getDisplayName(), bmCode,
                existingCatSum.totalOptions() + 1,
                existingCatSum.eligibleCount() + (isEligible ? 1 : 0),
                existingCatSum.partialCount() + (isEligible ? 0 : 1),
                existingCatSum.calculatedCount() + (validMetricCount > 0 ? 1 : 0)
            ));

            String schemeName = (option.getPlan() != null && option.getPlan().getScheme() != null)
                ? option.getPlan().getScheme().getName() : "Unknown Scheme";
            String schemeCode = (option.getPlan() != null && option.getPlan().getScheme() != null)
                ? option.getPlan().getScheme().getCode() : null;
            Long schemeId = (option.getPlan() != null && option.getPlan().getScheme() != null)
                ? option.getPlan().getScheme().getId() : null;
            Long amcId = (option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null)
                ? option.getPlan().getScheme().getAmc().getId() : null;
            String amcName = (option.getPlan() != null && option.getPlan().getScheme() != null && option.getPlan().getScheme().getAmc() != null)
                ? option.getPlan().getScheme().getAmc().getName() : "Unknown AMC";

            String planType = (option.getPlan() != null && option.getPlan().getPlanType() != null)
                ? option.getPlan().getPlanType() : "DIRECT";
            String optionType = (option.getOptionType() != null)
                ? option.getOptionType() : "GROWTH";

            LocalDate itemAsOfDate = (run != null && run.getAsOfDate() != null) ? run.getAsOfDate() : effectiveAsOfDate;
            OffsetDateTime knowledgeCutoff = run != null ? run.getKnowledgeCutoffTime() : null;

            allItems.add(new UniverseScreenerDto.ScreenerItem(
                optId, schemeId, schemeCode, schemeName,
                amcId, amcName, option.getAmfiCode(), option.getIsin(),
                cat, cat.getDisplayName(), windowDates, suffStatus, isEligible, exclusionReason,
                bmCode, bmName, bmGov, bmCount, bmStatus,
                runId, itemAsOfDate, knowledgeCutoff, calcStatus, validMetricCount, optionMetrics,
                allTimeDates, earliestNavDate, latestNavDate, dataFreshnessStatus,
                planType, optionType, effectiveHorizon.getCode(), isEligible
            ));
        }

        // Enrich items with empirical category percentiles BEFORE applying user query filters (DEC-V1-02 / TSK-M4-01)
        List<UniverseScreenerDto.ScreenerItem> enrichedAllItems = enrichWithCategoryPercentiles(allItems, effectiveAsOfDate);

        // Apply filters if provided
        List<UniverseScreenerDto.ScreenerItem> filteredItems = enrichedAllItems.stream()
            .filter(item -> categoryFilter == null || item.category() == categoryFilter)
            .filter(item -> eligibleOnly == null || !eligibleOnly || (item.isEligibleForHorizon() != null ? item.isEligibleForHorizon() : item.isEligibleFor3Y()))
            .toList();

        List<UniverseScreenerDto.MetricDefinitionDto> activeMetricDefs = getMetricDefinitionsForHorizon(effectiveHorizon);

        return new UniverseScreenerDto.ScreenerResponse(
            effectiveAsOfDate, totalRef, totalEligible, totalPartial, totalZero, totalCalculated,
            categorySummaries, activeMetricDefs, filteredItems,
            GOVERNANCE_DISCLAIMERS, OffsetDateTime.now(),
            allTimeSufficientCount, allTimePartialCount, totalEligible,
            effectiveHorizon.getCode(),
            effectiveHorizon.getYears() + " Years (>= " + sufficiencyThreshold + " aligned daily observations)",
            windowStartDate,
            sufficiencyThreshold
        );
    }

    /**
     * Computes empirical category-relative percentile ranks across all reference-universe items
     * using the authorized non-parametric ECDF midpoint formula (DEC-V1-02 / TSK-M4-01).
     *
     * Invariants:
     * 1. Category Peer Isolation: Funds are strictly ranked against peers within the same SEBI equity category.
     * 2. Evaluation Window & Date Alignment: Uses the identical 3Y window and asOfDate cutoff.
     * 3. Non-parametric ECDF Midpoint Formula:
     *    PR = (count(y < x) + 0.5 * count(y == x)) / N * 100.0
     *    Guarantees exact tie-handling and symmetry across [0, 100].
     * 4. Missing-Data Exclusion: Unavailable or invalid metrics are excluded from the peer denominator N.
     * 5. Minimum Peer & Confidence Rules:
     *    - N < 3: INSUFFICIENT_PEERS (percentile is not exposed).
     *    - 3 <= N < 15: CONFIDENCE_LIMITED_SMALL_COHORT (percentile is exposed with explicit small cohort disclosure).
     *    - N >= 15: PRODUCTION_CALIBRATED.
     */
    private List<UniverseScreenerDto.ScreenerItem> enrichWithCategoryPercentiles(
        List<UniverseScreenerDto.ScreenerItem> rawItems,
        LocalDate asOfDate
    ) {
        Map<String, String> metricDirectionMap = Map.of(
            "RET-02", "HIGHER_IS_BETTER",
            "RET-03", "HIGHER_IS_BETTER",
            "RSK-01", "LOWER_IS_BETTER",
            "RSK-03", "HIGHER_IS_BETTER",
            "MKT-01", "DESCRIPTIVE",
            "MKT-02", "LOWER_IS_BETTER",
            "RAT-04", "HIGHER_IS_BETTER",
            "REL-01", "LOWER_IS_BETTER"
        );

        Map<EquityUniverseCategory, List<UniverseScreenerDto.ScreenerItem>> byCategory = rawItems.stream()
            .filter(item -> item.category() != null)
            .collect(Collectors.groupingBy(UniverseScreenerDto.ScreenerItem::category));

        Map<Long, Map<String, UniverseScreenerDto.MetricValueDto>> enrichedMetricsByOptionId = new HashMap<>();

        for (Map.Entry<EquityUniverseCategory, List<UniverseScreenerDto.ScreenerItem>> catEntry : byCategory.entrySet()) {
            List<UniverseScreenerDto.ScreenerItem> catItems = catEntry.getValue();

            for (String metricCode : CANONICAL_SCREENER_METRICS) {
                String direction = metricDirectionMap.getOrDefault(metricCode, "DESCRIPTIVE");

                // Extract all valid numeric values in this category for this metric (strictly CALCULATED with non-null value)
                // Invariant: only include peer items whose calculation asOfDate matches the active screener asOfDate
                List<BigDecimal> validValues = new ArrayList<>();
                for (UniverseScreenerDto.ScreenerItem item : catItems) {
                    if (!asOfDate.equals(item.asOfDate())) {
                        continue;
                    }
                    UniverseScreenerDto.MetricValueDto mv = item.metrics().get(metricCode);
                    if (mv != null && "CALCULATED".equalsIgnoreCase(mv.calculationStatus()) && mv.numericValue() != null) {
                        validValues.add(mv.numericValue());
                    }
                }

                int N = validValues.size();

                for (UniverseScreenerDto.ScreenerItem item : catItems) {
                    UniverseScreenerDto.MetricValueDto mv = item.metrics().get(metricCode);
                    if (mv == null) {
                        continue;
                    }

                    // If item is from a historical run (asOfDate != screener asOfDate), do not rank against active cohort
                    if (!asOfDate.equals(item.asOfDate())) {
                        String histReason = "Historical calculation run as of " + item.asOfDate()
                            + " cannot be ranked against the active " + asOfDate + " cohort.";
                        enrichedMetricsByOptionId.computeIfAbsent(item.schemeOptionId(), k -> new LinkedHashMap<>())
                            .put(metricCode, mv.withPercentile(null, null, N, direction, "HISTORICAL_RUN_EXCLUDED", histReason));
                        continue;
                    }

                    if (mv.numericValue() == null || !"CALCULATED".equalsIgnoreCase(mv.calculationStatus())) {
                        String unavailableReason;
                        if (mv.errorMessage() != null && !mv.errorMessage().isBlank()) {
                            unavailableReason = mv.errorMessage();
                        } else if (mv.calculationStatus() != null && !mv.calculationStatus().isBlank()) {
                            unavailableReason = "Metric calculation status: " + mv.calculationStatus();
                        } else {
                            unavailableReason = "Metric value unavailable";
                        }
                        enrichedMetricsByOptionId.computeIfAbsent(item.schemeOptionId(), k -> new LinkedHashMap<>())
                            .put(metricCode, mv.withPercentile(null, null, N, direction, "INSUFFICIENT_DATA", unavailableReason));
                        continue;
                    }

                    if (N < 3) {
                        enrichedMetricsByOptionId.computeIfAbsent(item.schemeOptionId(), k -> new LinkedHashMap<>())
                            .put(metricCode, mv.withPercentile(null, null, N, direction, "INSUFFICIENT_PEERS",
                                "Insufficient peer coverage (minimum 3 valid peers in category required; found " + N + ")"));
                    } else {
                        BigDecimal x = mv.numericValue();
                        long strictlyLower = validValues.stream().filter(v -> v.compareTo(x) < 0).count();
                        long equalCount = validValues.stream().filter(v -> v.compareTo(x) == 0).count();

                        double pr = ((strictlyLower + 0.5 * equalCount) / (double) N) * 100.0;
                        double prRounded = BigDecimal.valueOf(pr).setScale(2, RoundingMode.HALF_UP).doubleValue();
                        int pInt = Math.min(99, Math.max(1, (int) Math.round(prRounded)));
                        String formattedPR = String.format("P%02d", pInt);

                        String cohortStatus = N < 15 ? "CONFIDENCE_LIMITED_SMALL_COHORT" : "PRODUCTION_CALIBRATED";
                        String reason = N < 15
                            ? "Confidence Limited: Small Peer Cohort (N=" + N + " < 15 required for cohort sample-size adequacy)"
                            : null;

                        enrichedMetricsByOptionId.computeIfAbsent(item.schemeOptionId(), k -> new LinkedHashMap<>())
                            .put(metricCode, mv.withPercentile(prRounded, formattedPR, N, direction, cohortStatus, reason));
                    }
                }
            }
        }

        List<UniverseScreenerDto.ScreenerItem> enrichedItems = new ArrayList<>(rawItems.size());
        for (UniverseScreenerDto.ScreenerItem item : rawItems) {
            Map<String, UniverseScreenerDto.MetricValueDto> enriched = enrichedMetricsByOptionId.getOrDefault(item.schemeOptionId(), item.metrics());
            enrichedItems.add(new UniverseScreenerDto.ScreenerItem(
                item.schemeOptionId(),
                item.schemeId(),
                item.schemeCode(),
                item.schemeName(),
                item.amcId(),
                item.amcName(),
                item.amfiCode(),
                item.isin(),
                item.category(),
                item.categoryDisplayName(),
                item.distinctNavDates(),
                item.sufficiencyStatus(),
                item.isEligibleFor3Y(),
                item.exclusionReason(),
                item.candidateBenchmarkCode(),
                item.candidateBenchmarkName(),
                item.benchmarkGovernanceStatus(),
                item.benchmarkObservationCount(),
                item.benchmarkCoverageStatus(),
                item.latestCalculationRunId(),
                item.asOfDate(),
                item.knowledgeCutoffTime(),
                item.calculationStatus(),
                item.validMetricCount(),
                enriched,
                item.allTimeNavDates(),
                item.earliestNavDate(),
                item.latestNavDate(),
                item.dataFreshnessStatus(),
                item.planType(),
                item.optionType(),
                item.horizon(),
                item.isEligibleForHorizon()
            ));
        }

        return enrichedItems;
    }

    private UniverseScreenerDto.MetricValueDto formatMetricValue(MetricResult mr) {
        BigDecimal val = mr.getNumericValue();
        String formatted;
        if (val == null) {
            formatted = "N/A";
        } else if ("PERCENTAGE".equalsIgnoreCase(mr.getUnits())) {
            BigDecimal pct = val.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            formatted = (pct.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + pct.toPlainString() + "%";
        } else {
            formatted = val.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return new UniverseScreenerDto.MetricValueDto(
            mr.getMetricCode(), val, formatted, mr.getPeriodType(), mr.getUnits(), mr.getCalculationStatus(), mr.getErrorMessage()
        );
    }
}

