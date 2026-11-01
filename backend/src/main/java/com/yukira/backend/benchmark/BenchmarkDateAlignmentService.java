package com.yukira.backend.benchmark;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
import com.yukira.backend.service.BenchmarkResolutionService.BenchmarkResolutionResult;
import com.yukira.backend.universe.EquityUniverseCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for date-aligned multi-asset calendar synchronization and data-quality diagnostics
 * between mutual fund NAV observations and canonical benchmark series (TSK-M1-04).
 *
 * Epistemic & Methodological Invariants:
 * 1. Zero Synthetic Observations: Mismatched dates or missing observations are NEVER forward-filled,
 *    interpolated, or synthetically manufactured.
 * 2. Return Variant Invariant: Benchmark-relative metrics strictly require Total Return Indices (TRI).
 *    Price Return Indices (PRI) are rejected with NON_TRI_INCOMPATIBLE status.
 * 3. Point-in-Time Integrity: Evaluated observations are strictly bounded by asOfDate and knowledgeCutoffTime.
 *    Future benchmark revisions or observations published after the cutoff are never used.
 * 4. Explicit Metric Prerequisites: Missing benchmark data prevents benchmark-dependent metrics
 *    from being marked ready, but does NOT prevent absolute return metrics (e.g. RET-02, RET-03) from calculating.
 */
@Service
public class BenchmarkDateAlignmentService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkDateAlignmentService.class);
    private static final String FBIL_TBILL_CODE = "FBIL_91D_TBILL";
    public static final String GOVERNANCE_DISCLAIMER =
        "Benchmark alignment diagnostics evaluate observational calendar synchronization and mathematical calculation prerequisites. " +
        "Date alignment does NOT constitute methodology validation or investment recommendation (Phase 2H governance rule).";

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final BenchmarkResolutionService benchmarkResolutionService;

    public BenchmarkDateAlignmentService(
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        RiskFreeObservationRepository riskFreeObservationRepository,
        BenchmarkResolutionService benchmarkResolutionService
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.benchmarkResolutionService = benchmarkResolutionService;
    }

    /**
     * Resolves option by identifier (numeric ID, AMFI code, or ISIN) and evaluates date alignment.
     */
    @Transactional(readOnly = true)
    public BenchmarkDateAlignmentResultDto evaluateAlignmentByIdentifier(
        String identifier,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime
    ) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Option identifier must not be null or blank");
        }
        SchemeOption option = null;
        try {
            Long optId = Long.parseLong(identifier.trim());
            option = schemeOptionRepository.findById(optId).orElse(null);
        } catch (NumberFormatException ignored) {}

        if (option == null) {
            option = schemeOptionRepository.findByAmfiCode(identifier.trim()).orElse(null);
        }
        if (option == null) {
            option = schemeOptionRepository.findByIsin(identifier.trim()).orElse(null);
        }
        if (option == null) {
            throw new IllegalArgumentException("SchemeOption not found for identifier: " + identifier);
        }

        return evaluateAlignment(option, requestedStartDate, requestedEndDate, asOfDate, knowledgeCutoffTime);
    }

    /**
     * Evaluates calendar date alignment, missing date diagnostics, and metric-specific readiness.
     */
    @Transactional(readOnly = true)
    public BenchmarkDateAlignmentResultDto evaluateAlignment(
        SchemeOption option,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime
    ) {
        if (option == null) {
            throw new IllegalArgumentException("option must not be null");
        }

        // 1. Establish temporal boundaries
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }
        if (knowledgeCutoffTime == null) {
            knowledgeCutoffTime = OffsetDateTime.now();
        }
        if (requestedEndDate == null) {
            requestedEndDate = asOfDate;
        }
        if (requestedStartDate == null) {
            requestedStartDate = requestedEndDate.minusYears(3);
        }
        if (requestedStartDate.isAfter(requestedEndDate)) {
            throw new IllegalArgumentException("requestedStartDate (" + requestedStartDate + ") must not be after requestedEndDate (" + requestedEndDate + ")");
        }

        SchemePlan plan = option.getPlan();
        Scheme scheme = plan != null ? plan.getScheme() : null;
        String schemeCategory = scheme != null ? scheme.getCategory() : null;
        String schemeSubcategory = scheme != null ? scheme.getSubcategory() : null;
        String schemeName = scheme != null ? scheme.getName() : null;

        EquityUniverseCategory eqCategory = scheme != null
            ? EquityUniverseCategory.categorize(schemeCategory, schemeSubcategory, schemeName)
            : null;

        // 2. Resolve primary benchmark
        BenchmarkResolutionResult bmResolution = benchmarkResolutionService.resolveBenchmarkForOption(option, asOfDate);
        Benchmark benchmark = bmResolution.benchmark();
        boolean isMapped = bmResolution.isMapped();
        boolean isTri = bmResolution.isTri();

        // 3. Fetch PIT fund observations
        List<NavObservation> pitFundObs = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(option.getId(), asOfDate, knowledgeCutoffTime);

        int duplicateOrInvalidExcluded = 0;
        Map<LocalDate, BigDecimal> fundNavMap = new TreeMap<>();
        int fundObsInWindow = 0;

        for (NavObservation obs : pitFundObs) {
            LocalDate d = obs.getEffectiveDate();
            if (!d.isBefore(requestedStartDate) && !d.isAfter(requestedEndDate)) {
                fundObsInWindow++;
                if ("INVALID".equalsIgnoreCase(obs.getQualityAssessment()) || obs.getNavValue() == null || obs.getNavValue().compareTo(BigDecimal.ZERO) <= 0) {
                    duplicateOrInvalidExcluded++;
                    continue;
                }
                fundNavMap.putIfAbsent(d, obs.getNavValue());
            }
        }
        int fundDistinctValidDates = fundNavMap.size();

        // 4. Fetch PIT benchmark observations
        Map<LocalDate, BigDecimal> benchNavMap = new TreeMap<>();
        int bmObsInWindow = 0;

        if (benchmark != null) {
            List<BenchmarkObservation> pitBmObs = benchmarkObservationRepository
                .findAuthoritativeObservationsAsOfCutoff(benchmark.getId(), asOfDate, knowledgeCutoffTime);

            for (BenchmarkObservation obs : pitBmObs) {
                LocalDate d = obs.getEffectiveDate();
                if (!d.isBefore(requestedStartDate) && !d.isAfter(requestedEndDate)) {
                    bmObsInWindow++;
                    if (obs.getIndexLevel() == null || obs.getIndexLevel().compareTo(BigDecimal.ZERO) <= 0) {
                        duplicateOrInvalidExcluded++;
                        continue;
                    }
                    benchNavMap.putIfAbsent(d, obs.getIndexLevel());
                }
            }
        }
        int bmDistinctValidDates = benchNavMap.size();

        // 5. Synchronous date alignment
        Set<LocalDate> commonDateSet = new TreeSet<>(fundNavMap.keySet());
        commonDateSet.retainAll(benchNavMap.keySet());
        List<LocalDate> alignedDates = new ArrayList<>(commonDateSet);
        int alignedTradingDays = alignedDates.size();

        LocalDate earliestAligned = alignedDates.isEmpty() ? null : alignedDates.get(0);
        LocalDate latestAligned = alignedDates.isEmpty() ? null : alignedDates.get(alignedDates.size() - 1);

        Set<LocalDate> fundDatesWithoutBm = new TreeSet<>(fundNavMap.keySet());
        fundDatesWithoutBm.removeAll(commonDateSet);

        Set<LocalDate> bmDatesWithoutFund = new TreeSet<>(benchNavMap.keySet());
        bmDatesWithoutFund.removeAll(commonDateSet);

        List<LocalDate> sampleFundWithoutBm = fundDatesWithoutBm.stream().limit(10).toList();
        List<LocalDate> sampleBmWithoutFund = bmDatesWithoutFund.stream().limit(10).toList();

        BigDecimal overlapRatio = fundDistinctValidDates > 0
            ? BigDecimal.valueOf((double) alignedTradingDays / fundDistinctValidDates).setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // 6. Compute down-market benchmark days in aligned window for MKT-02
        int downMarketBenchmarkDays = 0;
        for (int i = 1; i < alignedDates.size(); i++) {
            BigDecimal prevBm = benchNavMap.get(alignedDates.get(i - 1));
            BigDecimal currBm = benchNavMap.get(alignedDates.get(i));
            if (prevBm != null && currBm != null && prevBm.compareTo(BigDecimal.ZERO) > 0) {
                if (currBm.compareTo(prevBm) < 0) {
                    downMarketBenchmarkDays++;
                }
            }
        }

        // 7. Check risk-free rate series availability
        long rfObsCount = riskFreeObservationRepository.countByBenchmarkCodeAndDateRange(
            FBIL_TBILL_CODE, requestedStartDate, requestedEndDate
        );
        boolean riskFreeAvailable = rfObsCount >= 700 || (rfObsCount >= 60 && !alignedDates.isEmpty());

        // 8. Overall Alignment Status determination
        String alignmentStatus;
        if (!isMapped) {
            alignmentStatus = "BENCHMARK_UNMAPPED";
        } else if (benchmark != null && !isTri) {
            alignmentStatus = "NON_TRI_INCOMPATIBLE";
        } else if (benchNavMap.isEmpty()) {
            alignmentStatus = "BENCHMARK_DATA_MISSING";
        } else if (alignedTradingDays >= 700) {
            alignmentStatus = "ALIGNED_3Y_SUFFICIENT";
        } else if (alignedTradingDays >= 60) {
            alignmentStatus = "ALIGNED_MINIMAL_SAMPLE_SUFFICIENT";
        } else {
            alignmentStatus = "INSUFFICIENT_OVERLAP";
        }

        // 9. Source Provenance Limitations
        List<String> limitations = new ArrayList<>();
        if (!isMapped) {
            limitations.add("Scheme is outside the five reference equity categories and has no explicit benchmark mapping.");
        } else if (!isTri) {
            limitations.add(String.format("Assigned benchmark '%s' is not a Total Returns Index (variant: %s). Relative metrics require TRI.",
                benchmark != null ? benchmark.getCode() : "N/A", benchmark != null ? benchmark.getReturnVariant() : "UNKNOWN"));
        } else if (benchNavMap.isEmpty()) {
            limitations.add(String.format("No benchmark observations found for '%s' in requested window [%s to %s].",
                benchmark != null ? benchmark.getCode() : "N/A", requestedStartDate, requestedEndDate));
        } else {
            limitations.add("NSE Indices publishes EOD TRI index levels with default 18:00 IST availability time.");
            if (fundDatesWithoutBm.size() > 0) {
                limitations.add(String.format("Found %d fund dates without corresponding benchmark observation (e.g. fund reporting on market holiday).",
                    fundDatesWithoutBm.size()));
            }
            if (bmDatesWithoutFund.size() > 0) {
                limitations.add(String.format("Found %d benchmark trading dates without fund NAV observation (e.g. scheme inception delay or missing report).",
                    bmDatesWithoutFund.size()));
            }
        }

        if (duplicateOrInvalidExcluded > 0) {
            limitations.add(String.format("Excluded %d duplicate or invalid observations from calendar alignment.", duplicateOrInvalidExcluded));
        }

        // 10. Metric-specific readiness evaluation
        Map<String, MetricReadinessStatusDto> metricReadiness = buildMetricReadinessMap(
            fundDistinctValidDates,
            alignedTradingDays,
            downMarketBenchmarkDays,
            isMapped,
            isTri,
            benchNavMap.isEmpty(),
            riskFreeAvailable,
            rfObsCount
        );

        return new BenchmarkDateAlignmentResultDto(
            option.getId(),
            option.getAmfiCode(),
            option.getIsin(),
            schemeName,
            schemeCategory,
            schemeSubcategory,
            eqCategory,
            benchmark != null ? benchmark.getId() : null,
            benchmark != null ? benchmark.getCode() : null,
            benchmark != null ? benchmark.getName() : null,
            benchmark != null ? benchmark.getProvider() : null,
            benchmark != null ? benchmark.getReturnVariant() : null,
            isTri,
            bmResolution.resolutionMode(),
            bmResolution.methodologyStatus(),
            bmResolution.message(),
            requestedStartDate,
            requestedEndDate,
            asOfDate,
            knowledgeCutoffTime,
            earliestAligned,
            latestAligned,
            fundObsInWindow,
            fundDistinctValidDates,
            bmObsInWindow,
            bmDistinctValidDates,
            alignedTradingDays,
            fundDatesWithoutBm.size(),
            bmDatesWithoutFund.size(),
            downMarketBenchmarkDays,
            duplicateOrInvalidExcluded,
            overlapRatio,
            alignmentStatus,
            sampleFundWithoutBm,
            sampleBmWithoutFund,
            limitations,
            metricReadiness,
            GOVERNANCE_DISCLAIMER
        );
    }

    private Map<String, MetricReadinessStatusDto> buildMetricReadinessMap(
        int fundDays,
        int alignedDays,
        int downMarketDays,
        boolean isMapped,
        boolean isTri,
        boolean bmDataMissing,
        boolean riskFreeAvailable,
        long rfObsCount
    ) {
        Map<String, MetricReadinessStatusDto> map = new LinkedHashMap<>();

        // 1. RET-02: Simple Period Return (Operational Vertical Slice)
        if (fundDays >= 2) {
            map.put("RET-02", MetricReadinessStatusDto.ready(
                "RET-02", "Simple Period Return", "ABSOLUTE_RETURN",
                false, false, 2, null, fundDays, "VALIDATED_OPERATIONAL_SLICE"
            ));
        } else {
            map.put("RET-02", MetricReadinessStatusDto.disqualified(
                "RET-02", "Simple Period Return", "ABSOLUTE_RETURN",
                false, false, 2, null, fundDays,
                List.of(String.format("Requires at least 2 valid fund NAV observations (found %d).", fundDays)),
                "VALIDATED_OPERATIONAL_SLICE"
            ));
        }

        // 2. RET-03: CAGR 3Y (Candidate Slice)
        if (fundDays >= 700) {
            map.put("RET-03", MetricReadinessStatusDto.ready(
                "RET-03", "Compound Annual Growth Rate (3Y)", "ABSOLUTE_RETURN",
                false, false, 700, null, fundDays, "IMPLEMENTED_CANDIDATE_SLICE"
            ));
        } else {
            map.put("RET-03", MetricReadinessStatusDto.disqualified(
                "RET-03", "Compound Annual Growth Rate (3Y)", "ABSOLUTE_RETURN",
                false, false, 700, null, fundDays,
                List.of(String.format("Requires at least 700 distinct valid trading dates (found %d).", fundDays)),
                "IMPLEMENTED_CANDIDATE_SLICE"
            ));
        }

        // 3. RSK-01: Volatility 3Y (Candidate Slice)
        if (fundDays >= 700) {
            map.put("RSK-01", MetricReadinessStatusDto.ready(
                "RSK-01", "Annualized Volatility (3Y)", "ABSOLUTE_RISK",
                false, false, 700, null, fundDays, "IMPLEMENTED_CANDIDATE_SLICE"
            ));
        } else {
            map.put("RSK-01", MetricReadinessStatusDto.disqualified(
                "RSK-01", "Annualized Volatility (3Y)", "ABSOLUTE_RISK",
                false, false, 700, null, fundDays,
                List.of(String.format("Requires at least 700 distinct valid trading dates (found %d).", fundDays)),
                "IMPLEMENTED_CANDIDATE_SLICE"
            ));
        }

        // 4. RSK-03: Maximum Drawdown (Candidate Specification)
        if (fundDays >= 2) {
            map.put("RSK-03", MetricReadinessStatusDto.ready(
                "RSK-03", "Maximum Drawdown", "ABSOLUTE_RISK",
                false, false, 2, null, fundDays, "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("RSK-03", MetricReadinessStatusDto.disqualified(
                "RSK-03", "Maximum Drawdown", "ABSOLUTE_RISK",
                false, false, 2, null, fundDays,
                List.of(String.format("Requires at least 2 valid fund NAV observations (found %d).", fundDays)),
                "CANDIDATE_SPECIFICATION"
            ));
        }

        // Helper for benchmark-relative validation
        List<String> bmDisqualifications = new ArrayList<>();
        if (!isMapped) {
            bmDisqualifications.add("No benchmark assigned to this scheme or category.");
        } else if (!isTri) {
            bmDisqualifications.add("Assigned benchmark is not a Total Returns Index (TRI). Price Return Indices are non-compliant.");
        } else if (bmDataMissing) {
            bmDisqualifications.add("Assigned benchmark has zero observations in the requested evaluation window.");
        }

        // 5. MKT-01: Beta 3Y (Candidate Specification)
        if (!bmDisqualifications.isEmpty()) {
            map.put("MKT-01", MetricReadinessStatusDto.disqualified(
                "MKT-01", "Systematic Beta (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, 60, alignedDays, bmDisqualifications, "CANDIDATE_SPECIFICATION"
            ));
        } else if (alignedDays >= 700) {
            map.put("MKT-01", MetricReadinessStatusDto.ready(
                "MKT-01", "Systematic Beta (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, 60, alignedDays, "CANDIDATE_SPECIFICATION"
            ));
        } else if (alignedDays >= 60) {
            map.put("MKT-01", MetricReadinessStatusDto.candidateOnly(
                "MKT-01", "Systematic Beta (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, 60, alignedDays,
                String.format("Aligned sample (%d days) satisfies minimal regression threshold (>= 60) for candidate exploration, but does not meet standard 3Y threshold (>= 700).", alignedDays),
                "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("MKT-01", MetricReadinessStatusDto.disqualified(
                "MKT-01", "Systematic Beta (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, 60, alignedDays,
                List.of(String.format("Requires at least 700 aligned common trading dates (found %d, minimal threshold 60).", alignedDays)),
                "CANDIDATE_SPECIFICATION"
            ));
        }

        // 6. MKT-02: Downside Beta 3Y (Candidate Specification)
        List<String> mkt02Disqualifications = new ArrayList<>(bmDisqualifications);
        if (bmDisqualifications.isEmpty()) {
            if (alignedDays < 60) {
                mkt02Disqualifications.add(String.format("Requires at least 700 aligned dates (found %d, minimal threshold 60).", alignedDays));
            } else if (downMarketDays < 100) {
                mkt02Disqualifications.add(String.format("Requires at least 100 down-market benchmark trading days in aligned window (found %d).", downMarketDays));
            }
        }
        if (!mkt02Disqualifications.isEmpty()) {
            map.put("MKT-02", MetricReadinessStatusDto.disqualified(
                "MKT-02", "Downside Beta (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, 60, alignedDays, mkt02Disqualifications, "CANDIDATE_SPECIFICATION"
            ));
        } else if (alignedDays >= 700) {
            map.put("MKT-02", MetricReadinessStatusDto.ready(
                "MKT-02", "Downside Beta (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, 60, alignedDays, "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("MKT-02", MetricReadinessStatusDto.candidateOnly(
                "MKT-02", "Downside Beta (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, 60, alignedDays,
                String.format("Candidate exploratory downside regression ready (%d aligned days >= 60, %d down days >= 100). Standard 3Y requires >= 700.", alignedDays, downMarketDays),
                "CANDIDATE_SPECIFICATION"
            ));
        }

        // 7. RSK-02: Downside Semideviation 3Y (Candidate Specification)
        if (fundDays >= 700) {
            map.put("RSK-02", MetricReadinessStatusDto.ready(
                "RSK-02", "Downside Semideviation (3Y)", "ABSOLUTE_RISK",
                false, false, 700, null, fundDays, "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("RSK-02", MetricReadinessStatusDto.disqualified(
                "RSK-02", "Downside Semideviation (3Y)", "ABSOLUTE_RISK",
                false, false, 700, null, fundDays,
                List.of(String.format("Requires at least 700 distinct valid trading dates (found %d).", fundDays)),
                "CANDIDATE_SPECIFICATION"
            ));
        }

        // 8. REL-01: Tracking Error 3Y (Candidate Specification)
        if (!bmDisqualifications.isEmpty()) {
            map.put("REL-01", MetricReadinessStatusDto.disqualified(
                "REL-01", "Tracking Error (3Y Annualized)", "BENCHMARK_RELATIVE",
                true, false, 700, null, alignedDays, bmDisqualifications, "CANDIDATE_SPECIFICATION"
            ));
        } else if (alignedDays >= 700) {
            map.put("REL-01", MetricReadinessStatusDto.ready(
                "REL-01", "Tracking Error (3Y Annualized)", "BENCHMARK_RELATIVE",
                true, false, 700, null, alignedDays, "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("REL-01", MetricReadinessStatusDto.disqualified(
                "REL-01", "Tracking Error (3Y Annualized)", "BENCHMARK_RELATIVE",
                true, false, 700, null, alignedDays,
                List.of(String.format("Requires at least 700 aligned common trading dates (found %d).", alignedDays)),
                "CANDIDATE_SPECIFICATION"
            ));
        }

        // 9. RAT-04: Information Ratio 3Y (Candidate Specification)
        if (!bmDisqualifications.isEmpty()) {
            map.put("RAT-04", MetricReadinessStatusDto.disqualified(
                "RAT-04", "Information Ratio (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, null, alignedDays, bmDisqualifications, "CANDIDATE_SPECIFICATION"
            ));
        } else if (alignedDays >= 700) {
            map.put("RAT-04", MetricReadinessStatusDto.ready(
                "RAT-04", "Information Ratio (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, null, alignedDays, "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("RAT-04", MetricReadinessStatusDto.disqualified(
                "RAT-04", "Information Ratio (3Y)", "BENCHMARK_RELATIVE",
                true, false, 700, null, alignedDays,
                List.of(String.format("Requires at least 700 aligned common trading dates (found %d).", alignedDays)),
                "CANDIDATE_SPECIFICATION"
            ));
        }

        // 10. REL-02: Jensen's Alpha 3Y (Candidate Specification)
        List<String> rel02Disqualifications = new ArrayList<>(bmDisqualifications);
        if (bmDisqualifications.isEmpty() && alignedDays < 700) {
            rel02Disqualifications.add(String.format("Requires at least 700 aligned common trading dates (found %d).", alignedDays));
        }
        if (!riskFreeAvailable) {
            rel02Disqualifications.add(String.format("Requires FBIL 91-Day T-Bill risk-free series observations (found %d).", rfObsCount));
        }
        if (!rel02Disqualifications.isEmpty()) {
            map.put("REL-02", MetricReadinessStatusDto.disqualified(
                "REL-02", "Jensen's Alpha (3Y)", "BENCHMARK_RELATIVE",
                true, true, 700, null, alignedDays, rel02Disqualifications, "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("REL-02", MetricReadinessStatusDto.ready(
                "REL-02", "Jensen's Alpha (3Y)", "BENCHMARK_RELATIVE",
                true, true, 700, null, alignedDays, "CANDIDATE_SPECIFICATION"
            ));
        }

        // 11. RAT-01: Sharpe Ratio 3Y (Candidate Specification)
        List<String> rat01Disqualifications = new ArrayList<>();
        if (fundDays < 700) {
            rat01Disqualifications.add(String.format("Requires at least 700 distinct valid trading dates (found %d).", fundDays));
        }
        if (!riskFreeAvailable) {
            rat01Disqualifications.add(String.format("Requires FBIL 91-Day T-Bill risk-free series observations (found %d).", rfObsCount));
        }
        if (!rat01Disqualifications.isEmpty()) {
            map.put("RAT-01", MetricReadinessStatusDto.disqualified(
                "RAT-01", "Sharpe Ratio (3Y)", "RISK_FREE_ADJUSTED",
                false, true, 700, null, fundDays, rat01Disqualifications, "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("RAT-01", MetricReadinessStatusDto.ready(
                "RAT-01", "Sharpe Ratio (3Y)", "RISK_FREE_ADJUSTED",
                false, true, 700, null, fundDays, "CANDIDATE_SPECIFICATION"
            ));
        }

        // 12. RSK-04: Maximum Drawdown Duration (Candidate Specification)
        if (fundDays >= 2) {
            map.put("RSK-04", MetricReadinessStatusDto.ready(
                "RSK-04", "Maximum Drawdown Duration", "ABSOLUTE_RISK",
                false, false, 2, null, fundDays, "CANDIDATE_SPECIFICATION"
            ));
        } else {
            map.put("RSK-04", MetricReadinessStatusDto.disqualified(
                "RSK-04", "Maximum Drawdown Duration", "ABSOLUTE_RISK",
                false, false, 2, null, fundDays,
                List.of(String.format("Requires at least 2 valid fund NAV observations (found %d).", fundDays)),
                "CANDIDATE_SPECIFICATION"
            ));
        }

        return Collections.unmodifiableMap(map);
    }
}
