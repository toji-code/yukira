package com.yukira.backend.service.portfolio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorPortfolioAllocationSnapshot;
import com.yukira.backend.domain.entity.InvestorPortfolioAllocationSnapshotItem;
import com.yukira.backend.domain.entity.InvestorStressCalculationManifest;
import com.yukira.backend.domain.entity.InvestorStressDataQualitySignal;
import com.yukira.backend.domain.entity.InvestorStressFinding;
import com.yukira.backend.domain.entity.InvestorStressFindingTransition;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.risk.RiskInvestigationFindingDto;
import com.yukira.backend.dto.portfolio.risk.UnvaluedPositionDto;
import com.yukira.backend.dto.portfolio.risk.stress.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service orchestrating Historical Portfolio Stress-Period Analysis (Tasks 25–44).
 *
 * Epistemic & Methodological Guardrails (AGENTS.md & Tasks 35–44 Specs):
 * 1. Model: Static Current-Allocation Constant-Weight Daily Rebalanced Historical Stress Proxy
 *    (R_{p,t} = \sum w_i R_{i,t}).
 *    - Hypothetical replay projecting current known capital allocation weights consistently across
 *      historical market-stress episodes with daily rebalancing back to those weights.
 *    - Mathematical proxy, NOT a reconstruction of the investor's actual past trades, cash flows,
 *      taxes, or realized account performance.
 * 2. Multi-Period Constituent Return Attribution (Task 25, 40 & 42):
 *    - Multi-period compound portfolio return R_{p,cum} = \prod (1 + R_{p,t}) - 1 is non-linear.
 *    - Numerically stable Carino Logarithmic Linking ensures exact additive decomposition
 *      (\sum A_i = R_{p,cum}) within 0.0001% tolerance (10^-6 decimal).
 *    - Observation-specific linking factor k_t = ln(1+R_{p,t})/R_{p,t} across trading days t
 *      and overall linking factor K = ln(1+R_{p,cum})/R_{p,cum}. Factor is NOT constituent-specific.
 * 3. Lifecycle & Boundary Integrity (Task 36):
 *    - 10.0% benchmark drawdown qualification threshold with non-overlapping peak-to-recovery deduplication.
 *    - Captures benchmarkPeakDate, firstDeclineDate, benchmarkTroughDate, benchmarkRecoveryDate, analysisCutoffDate.
 *    - Distinct tracking of benchmark recovery vs portfolio proxy recovery against their respective base peaks.
 * 4. Constituent Observation Completeness & Date-Level Eligibility (Task 35):
 *    - Zero synthetic NAV fabrication, category substitutions, or silent weight redistribution (§4 AGENTS.md).
 *    - Holdings launched after episode peak are isolated as INSUFFICIENT_EVIDENCE / PRE_INCEPTION.
 *    - Missing constituent history prevents reporting incomplete portfolio-level proxy return.
 *    - Exposes expected vs usable observation counts, excluded counts, pre-inception exclusions,
 *      missing NAV exclusions, invalid NAV exclusions, and exact calculation window.
 * 5. Benchmark Identity & Cross-Episode Comparability (Task 37):
 *    - Primary: NIFTY_500_TRI; Fallback: NIFTY_50_TRI. Total Returns Index mandatory; no price indices.
 *    - Discloses fallback reason, coverage ratio, and comparability classifications.
 * 6. NAV Gaps, Flat Streaks & Data-Quality Signals (Task 38):
 *    - Identifies calendar gaps (> 7 days) and repeated flat streaks (>= 3 identical NAVs).
 *    - Distinguishes potential anomalies from confirmed data defects with descriptive severity.
 * 7. Stress Path, Drawdown & Duration Metrics (Task 39):
 *    - Preserves pre-decline base level V_{p,0} = 1.0; separates peak-to-trough loss, max drawdown,
 *      time to trough, and time to recovery.
 * 8. Attribution Role Governance (Task 40):
 *    - Separates mathematical contribution sign, magnitude, standalone return, relative outperformance,
 *      descriptive role label, and role interpretation.
 *    - RESILIENT_LOWER_LOSS_CONTRIBUTOR strictly requires R_{i,cum} > R_{p,cum} and A_i >= -1.0% pts.
 * 9. Multi-Episode Comparability & Summary Integrity (Task 41):
 *    - Summary metrics define aggregation conventions (arithmetic mean across computable episodes).
 *    - Explicit denominator: COMPUTABLE_EPISODES_ONLY (n=X of Y eligible).
 * 10. Provenance & Research Workspace Integration (Task 42 & 44):
 *     - Preserves calculationRunId, knowledgeCutoff, observation windows, and metric registry mappings.
 *     - Grounded research findings with one-click workspace logging.
 */
@Service
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class PortfolioHistoricalStressService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioHistoricalStressService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static final String METHODOLOGY_VERSION = "PORTFOLIO_STRESS_METHODOLOGY_V2";
    public static final String IDENTIFICATION_POLICY_VERSION = "PORTFOLIO_STRESS_IDENTIFICATION_POLICY_V2";
    public static final String EVIDENCE_POLICY_VERSION = "PORTFOLIO_STRESS_EVIDENCE_POLICY_V2";
    public static final String CONSTRUCTION_CONVENTION = "CONSTANT_WEIGHT_DAILY_REBALANCED_PROXY";
    public static final String ATTRIBUTION_METHODOLOGY = "CARINO_DAILY_REBALANCED_LOGARITHMIC_LINKING";
    public static final double ATTRIBUTION_RECONCILIATION_TOLERANCE_PCT = 0.0001; // 0.0001% tolerance (10^-6 decimal)

    public static final String DEFAULT_BENCHMARK_CODE = "NIFTY_500_TRI";
    public static final String FALLBACK_BENCHMARK_CODE = "NIFTY_50_TRI";
    public static final double MIN_DRAWDOWN_THRESHOLD = 0.10; // 10.0% peak-to-trough drawdown threshold
    public static final int ANNUALIZATION_FACTOR = 252;
    public static final double SQRT_ANNUALIZATION = Math.sqrt(ANNUALIZATION_FACTOR);

    // Evidence Thresholds for Episode Analysis (Task 27 & 35)
    public static final int MIN_EPISODE_OBS_SUFFICIENT = 15;
    public static final int MIN_EPISODE_OBS_LIMITED = 5;
    public static final double MIN_COVERAGE_RATIO_SUFFICIENT = 0.90; // >= 90% benchmark trading days aligned
    public static final double MIN_KNOWN_VALUE_COVERAGE_SUFFICIENT = 95.0; // >= 95% portfolio value coverage

    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final NavObservationRepository navObservationRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final InvestorStressFindingRepository stressFindingRepository;
    private final InvestorStressDataQualitySignalRepository stressSignalRepository;
    private final InvestorStressCalculationManifestRepository stressManifestRepository;
    private final InvestorRepository investorRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private InvestorStressFindingTransitionRepository stressFindingTransitionRepository;

    @Autowired(required = false)
    private InvestorPortfolioAllocationSnapshotRepository allocationSnapshotRepository;

    public PortfolioHistoricalStressService(
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        NavObservationRepository navObservationRepository,
        SchemeOptionRepository schemeOptionRepository
    ) {
        this(benchmarkRepository, benchmarkObservationRepository, navObservationRepository, schemeOptionRepository, null, null, null, null, null);
    }

    @Autowired
    public PortfolioHistoricalStressService(
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        NavObservationRepository navObservationRepository,
        SchemeOptionRepository schemeOptionRepository,
        @Autowired(required = false) InvestorStressFindingRepository stressFindingRepository,
        @Autowired(required = false) InvestorStressDataQualitySignalRepository stressSignalRepository,
        @Autowired(required = false) InvestorStressCalculationManifestRepository stressManifestRepository,
        @Autowired(required = false) InvestorRepository investorRepository,
        @Autowired(required = false) CalculationRunRepository calculationRunRepository
    ) {
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.navObservationRepository = navObservationRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.stressFindingRepository = stressFindingRepository;
        this.stressSignalRepository = stressSignalRepository;
        this.stressManifestRepository = stressManifestRepository;
        this.investorRepository = investorRepository;
        this.calculationRunRepository = calculationRunRepository;
    }

    /**
     * Computes a content-addressed, deterministic SHA-256 fingerprint for stress calculation inputs (Task 61).
     * Distinguishes immutable mathematical input identity from ephemeral execution run IDs.
     */
    public static String computeCalculationInputFingerprint(
        List<Long> fundIds,
        double[] weights,
        String snapshotId,
        String benchmarkCode,
        LocalDate analysisCutoff,
        OffsetDateTime knowledgeCutoff,
        String methodologyVersion
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append("BENCHMARK:").append(benchmarkCode != null ? benchmarkCode : "").append(";");
            sb.append("SNAPSHOT:").append(snapshotId != null ? snapshotId : "").append(";");
            sb.append("ANALYSIS_CUTOFF:").append(analysisCutoff != null ? analysisCutoff.toString() : "").append(";");
            sb.append("KNOWLEDGE_CUTOFF:").append(knowledgeCutoff != null ? knowledgeCutoff.toString() : "").append(";");
            sb.append("METHODOLOGY:").append(methodologyVersion != null ? methodologyVersion : "").append(";");
            sb.append("CONSTITUENTS:[");
            if (fundIds != null && weights != null) {
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < fundIds.size(); i++) indices.add(i);
                indices.sort(Comparator.comparing(i -> fundIds.get(i)));
                for (int idx : indices) {
                    sb.append(fundIds.get(idx)).append("=").append(String.format(Locale.US, "%.6f", weights[idx])).append(",");
                }
            }
            sb.append("]");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "FP-FALLBACK-" + (snapshotId != null ? snapshotId : UUID.randomUUID().toString().substring(0, 8));
        }
    }

    /**
     * Internal record capturing a deterministically identified benchmark drawdown episode (Tasks 26, 36, 47 & 54).
     */
    public record IdentifiedBenchmarkEpisode(
        String episodeId,
        String legacyEpisodeId,
        String episodeName,
        String regimeCategory,
        LocalDate peakDate,
        LocalDate firstDeclineDate,
        LocalDate troughDate,
        LocalDate recoveryDate,
        boolean hasRecovered,
        BigDecimal maxDrawdownPct,
        BigDecimal peakLevel,
        BigDecimal troughLevel,
        BigDecimal recoveryLevel,
        String lifecycleState,
        String lifecycleTransitionContext,
        int tradingDaysToTrough,
        Integer tradingDaysToRecovery,
        int calendarDaysToTrough,
        Integer calendarDaysToRecovery
    ) {
        /**
         * Backward-compatible 12-parameter constructor (Task 36 compatibility).
         */
        public IdentifiedBenchmarkEpisode(
            String episodeId,
            String episodeName,
            String regimeCategory,
            LocalDate peakDate,
            LocalDate firstDeclineDate,
            LocalDate troughDate,
            LocalDate recoveryDate,
            boolean hasRecovered,
            BigDecimal maxDrawdownPct,
            BigDecimal peakLevel,
            BigDecimal troughLevel,
            BigDecimal recoveryLevel
        ) {
            this(
                episodeId,
                episodeId,
                episodeName,
                regimeCategory,
                peakDate,
                firstDeclineDate,
                troughDate,
                recoveryDate,
                hasRecovered,
                maxDrawdownPct,
                peakLevel,
                troughLevel,
                recoveryLevel,
                hasRecovered ? "RECOVERED" : "UNRECOVERED_CUTOFF_REACHED",
                hasRecovered ? "Benchmark achieved recovery to peak." : "Benchmark remained below peak at cutoff.",
                0,
                null,
                (int) Math.max(0, ChronoUnit.DAYS.between(peakDate, troughDate)),
                recoveryDate != null ? (int) Math.max(0, ChronoUnit.DAYS.between(troughDate, recoveryDate)) : null
            );
        }
    }

    /**
     * Known historical market regimes for contextual institutional naming.
     */
    private record MarketRegimeDescriptor(
        LocalDate approximateStart,
        LocalDate approximateEnd,
        String institutionalTitle,
        String regimeCategory
    ) {}

    private static final List<MarketRegimeDescriptor> KNOWN_REGIMES = List.of(
        new MarketRegimeDescriptor(
            LocalDate.of(2021, 10, 1), LocalDate.of(2022, 7, 1),
            "2021–2022 Global Rate-Hike & Inflation Drawdown", "GLOBAL_MONETARY_TIGHTENING"
        ),
        new MarketRegimeDescriptor(
            LocalDate.of(2022, 11, 15), LocalDate.of(2023, 4, 15),
            "Winter 2022–2023 Rate Peak & US Banking Shock", "BANKING_LIQUIDITY_SHOCK"
        ),
        new MarketRegimeDescriptor(
            LocalDate.of(2023, 9, 1), LocalDate.of(2023, 11, 15),
            "Autumn 2023 US 10Y Yield Spike & Geopolitical Tension", "GEOPOLITICAL_YIELD_SPIKE"
        ),
        new MarketRegimeDescriptor(
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 31),
            "Feb–March 2024 Small/Mid-Cap Regulatory Liquidity Shakeout", "REGULATORY_LIQUIDITY_STRESS"
        ),
        new MarketRegimeDescriptor(
            LocalDate.of(2024, 5, 20), LocalDate.of(2024, 6, 15),
            "June 2024 Indian General Election Shock & Rebound", "POLITICAL_VOLATILITY"
        ),
        new MarketRegimeDescriptor(
            LocalDate.of(2020, 2, 1), LocalDate.of(2020, 4, 1),
            "February–March 2020 COVID-19 Liquidity Shock", "SYSTEMIC_PANDEMIC_CRISIS"
        )
    );

    /**
     * Evaluates deterministic historical stress-period analysis for an investor portfolio.
     */
    public PortfolioStressAnalysisResponseDto evaluateHistoricalStress(
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        String requestedBenchmarkCode,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        Map<Long, BigDecimal> fundMarketValues,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        BigDecimal knownValueCoveragePct,
        List<UnvaluedPositionDto> unvaluedPositions,
        List<RiskInvestigationFindingDto> findings
    ) {
        return evaluateHistoricalStress(
            asOfDate, knowledgeCutoff, requestedBenchmarkCode, fundIds, valuedSchemes, fundMarketValues,
            weights, weightPcts, fundNavHistory, knownValueCoveragePct, unvaluedPositions, findings,
            UUID.randomUUID().toString(), null
        );
    }

    /**
     * Overloaded method including calculationRunId for end-to-end execution provenance (Task 29.3 & 42).
     */
    public PortfolioStressAnalysisResponseDto evaluateHistoricalStress(
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        String requestedBenchmarkCode,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        Map<Long, BigDecimal> fundMarketValues,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        BigDecimal knownValueCoveragePct,
        List<UnvaluedPositionDto> unvaluedPositions,
        List<RiskInvestigationFindingDto> findings,
        String calculationRunId
    ) {
        return evaluateHistoricalStress(
            asOfDate, knowledgeCutoff, requestedBenchmarkCode, fundIds, valuedSchemes, fundMarketValues,
            weights, weightPcts, fundNavHistory, knownValueCoveragePct, unvaluedPositions, findings,
            calculationRunId, null
        );
    }

    /**
     * Canonical execution method accepting explicit PortfolioAllocationSnapshotDto for institutional provenance (Tasks 48 & 49).
     */
    public PortfolioStressAnalysisResponseDto evaluateHistoricalStress(
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        String requestedBenchmarkCode,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        Map<Long, BigDecimal> fundMarketValues,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        BigDecimal knownValueCoveragePct,
        List<UnvaluedPositionDto> unvaluedPositions,
        List<RiskInvestigationFindingDto> findings,
        String calculationRunId,
        PortfolioAllocationSnapshotDto allocationSnapshot
    ) {
        return evaluateHistoricalStress(
            null, null, asOfDate, knowledgeCutoff, requestedBenchmarkCode, fundIds, valuedSchemes,
            fundMarketValues, weights, weightPcts, fundNavHistory, knownValueCoveragePct,
            unvaluedPositions, findings, calculationRunId, allocationSnapshot
        );
    }

    /**
     * Canonical execution method accepting investor and portfolio IDs for durable evidence persistence and lifecycle tracking (Tasks 80–85).
     */
    public PortfolioStressAnalysisResponseDto evaluateHistoricalStress(
        Long investorId,
        Long portfolioId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        String requestedBenchmarkCode,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        Map<Long, BigDecimal> fundMarketValues,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        BigDecimal knownValueCoveragePct,
        List<UnvaluedPositionDto> unvaluedPositions,
        List<RiskInvestigationFindingDto> findings,
        String calculationRunId,
        PortfolioAllocationSnapshotDto allocationSnapshot
    ) {
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        OffsetDateTime effectiveCutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        String runId = calculationRunId != null ? calculationRunId : UUID.randomUUID().toString();

        // Task 48: Ensure allocation snapshot provenance is deterministic and explicit
        PortfolioAllocationSnapshotDto snapshot = allocationSnapshot != null
            ? allocationSnapshot
            : new PortfolioAllocationSnapshotDto(
                "ALLOC-SNAP-" + effectiveAsOf + "-" + (runId.length() >= 8 ? runId.substring(0, 8) : runId),
                effectiveAsOf,
                effectiveCutoff,
                "PORTFOLIO_MARKET_VALUE_WEIGHTED",
                fundIds != null ? fundIds.size() : 0,
                unvaluedPositions != null ? unvaluedPositions.size() : 0,
                knownValueCoveragePct != null ? knownValueCoveragePct : BigDecimal.ZERO,
                unvaluedPositions == null || unvaluedPositions.isEmpty(),
                "Static Current-Allocation Historical Stress Proxy (R_{p,t} = \\sum w_i R_{i,t})",
                "Capital weights derived from current investor portfolio valuation as of " + effectiveAsOf + " and held constant across historical stress episodes with daily rebalancing.",
                true
            );

        // Tasks 61 & 64: Content-addressed calculation input fingerprint and source revision lineage
        String inputFingerprint = computeCalculationInputFingerprint(
            fundIds, weights, snapshot.snapshotId(),
            requestedBenchmarkCode != null ? requestedBenchmarkCode : DEFAULT_BENCHMARK_CODE,
            effectiveAsOf, effectiveCutoff, METHODOLOGY_VERSION
        );

        List<String> disclaimers = buildStressDisclaimers();
        Map<String, String> methodologyNotes = buildStressMethodologyNotes();

        // 1. Resolve Benchmark (Task 37)
        Benchmark benchmark = resolveStressBenchmark(requestedBenchmarkCode);
        if (benchmark == null) {
            StressBenchmarkReferenceDto unavailableRef = new StressBenchmarkReferenceDto(
                requestedBenchmarkCode != null ? requestedBenchmarkCode : DEFAULT_BENCHMARK_CODE,
                "Reference Benchmark Not Found",
                "NSE Indices Limited",
                null, null, null, 0, "UNAVAILABLE",
                false, null,
                "TOTAL_RETURNS_INDEX",
                "No benchmark registered or available at knowledge cutoff.",
                "BENCHMARK_METHODOLOGY_V1",
                BigDecimal.ZERO,
                "None"
            );
            StressCalculationContextDto fallbackContext = new StressCalculationContextDto(
                runId, inputFingerprint, snapshot.snapshotId(),
                requestedBenchmarkCode != null ? requestedBenchmarkCode : DEFAULT_BENCHMARK_CODE,
                effectiveAsOf, effectiveCutoff, METHODOLOGY_VERSION, IDENTIFICATION_POLICY_VERSION,
                EVIDENCE_POLICY_VERSION, CONSTRUCTION_CONVENTION, ATTRIBUTION_METHODOLOGY, true,
                "Benchmark resolution failed.", "BM-REV-UNAVAILABLE", Collections.emptyMap(),
                "SOURCE_REVISION_UNAVAILABLE", "No benchmark registered or available."
            );
            return new PortfolioStressAnalysisResponseDto(
                "UNAVAILABLE", unavailableRef, null, Collections.emptyList(),
                EVIDENCE_POLICY_VERSION, IDENTIFICATION_POLICY_VERSION,
                METHODOLOGY_VERSION, CONSTRUCTION_CONVENTION, ATTRIBUTION_METHODOLOGY,
                disclaimers, methodologyNotes, knownValueCoveragePct, unvaluedPositions,
                runId, effectiveAsOf, effectiveCutoff, BigDecimal.valueOf(ATTRIBUTION_RECONCILIATION_TOLERANCE_PCT),
                "UNAVAILABLE", snapshot, fallbackContext
            );
        }

        boolean isFallbackBenchmark = !DEFAULT_BENCHMARK_CODE.equalsIgnoreCase(benchmark.getCode());
        String fallbackReason = isFallbackBenchmark
            ? String.format(Locale.US,
                "Preferred broad-market benchmark %s was unavailable or had insufficient observations. Fallback benchmark %s was selected under %s.",
                DEFAULT_BENCHMARK_CODE, benchmark.getCode(), IDENTIFICATION_POLICY_VERSION)
            : null;

        String comparabilityNotice = isFallbackBenchmark
            ? String.format(Locale.US,
                "Benchmark '%s' was used as the market-stress identification reference instead of preferred '%s'. '%s' represents fewer constituent securities and may exhibit different drawdown dynamics and regime dates than the broader market.",
                benchmark.getCode(), DEFAULT_BENCHMARK_CODE, benchmark.getCode())
            : null;

        // 2. Fetch Authoritative Point-in-Time Benchmark Observations
        List<BenchmarkObservation> bmObs = benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            benchmark.getId(), effectiveAsOf, effectiveCutoff
        );

        if (bmObs == null || bmObs.isEmpty()) {
            StressBenchmarkReferenceDto noObsRef = new StressBenchmarkReferenceDto(
                benchmark.getCode(), benchmark.getName(), benchmark.getProvider(),
                benchmark.getId(), null, null, 0, "NO_OBSERVATIONS",
                isFallbackBenchmark, comparabilityNotice,
                "TOTAL_RETURNS_INDEX",
                fallbackReason,
                "BENCHMARK_METHODOLOGY_V1",
                BigDecimal.ZERO,
                "None"
            );
            StressCalculationContextDto fallbackContext = new StressCalculationContextDto(
                runId, inputFingerprint, snapshot.snapshotId(),
                benchmark.getCode(), effectiveAsOf, effectiveCutoff, METHODOLOGY_VERSION,
                IDENTIFICATION_POLICY_VERSION, EVIDENCE_POLICY_VERSION, CONSTRUCTION_CONVENTION,
                ATTRIBUTION_METHODOLOGY, true, "Benchmark observations unavailable at knowledge cutoff.",
                "BM-REV-UNAVAILABLE", Collections.emptyMap(),
                "SOURCE_REVISION_UNAVAILABLE", "No benchmark observations found."
            );
            return new PortfolioStressAnalysisResponseDto(
                "UNAVAILABLE", noObsRef, null, Collections.emptyList(),
                EVIDENCE_POLICY_VERSION, IDENTIFICATION_POLICY_VERSION,
                METHODOLOGY_VERSION, CONSTRUCTION_CONVENTION, ATTRIBUTION_METHODOLOGY,
                disclaimers, methodologyNotes, knownValueCoveragePct, unvaluedPositions,
                runId, effectiveAsOf, effectiveCutoff, BigDecimal.valueOf(ATTRIBUTION_RECONCILIATION_TOLERANCE_PCT),
                "UNAVAILABLE", snapshot, fallbackContext
            );
        }

        // Task 83: Lineage Verification Contract: Authoritative Source Artifact Linkage and Gap Tracking
        int totalLineageEntities = 0;
        int linkedLineageEntities = 0;

        String benchmarkSourceRevisionId = "BM-REV-UNAVAILABLE";
        if (!bmObs.isEmpty()) {
            totalLineageEntities++;
            BenchmarkObservation latestBm = bmObs.get(bmObs.size() - 1);
            Long bmArtId = (latestBm.getSourceArtifact() != null) ? latestBm.getSourceArtifact().getId() : null;
            Integer bmRev = (latestBm.getRevisionSeq() != null) ? latestBm.getRevisionSeq() : 1;
            if (bmArtId != null) {
                linkedLineageEntities++;
                benchmarkSourceRevisionId = "BM-ART-" + bmArtId + "-REV-" + bmRev;
            } else {
                benchmarkSourceRevisionId = "BM-OBS-REV-" + bmRev;
            }
        }

        Map<String, String> constituentSourceRevisions = new LinkedHashMap<>();
        if (fundIds != null) {
            for (Long fid : fundIds) {
                totalLineageEntities++;
                Optional<NavObservation> navOpt = navObservationRepository.findLatestAuthoritativeObservationAsOfCutoff(fid, effectiveAsOf, effectiveCutoff);
                if (navOpt.isPresent()) {
                    NavObservation nav = navOpt.get();
                    Long artId = (nav.getSourceArtifact() != null) ? nav.getSourceArtifact().getId() : null;
                    Integer revSeq = (nav.getRevisionSeq() != null) ? nav.getRevisionSeq() : 1;
                    if (artId != null) {
                        linkedLineageEntities++;
                        constituentSourceRevisions.put(String.valueOf(fid), "NAV-ART-" + artId + "-REV-" + revSeq);
                    } else {
                        constituentSourceRevisions.put(String.valueOf(fid), "NAV-OBS-REV-" + revSeq);
                    }
                } else {
                    constituentSourceRevisions.put(String.valueOf(fid), "NAV-UNAVAILABLE");
                }
            }
        }

        String sourceLineageState;
        if (totalLineageEntities > 0 && linkedLineageEntities == totalLineageEntities) {
            sourceLineageState = "COMPLETE_SOURCE_LINEAGE";
        } else if (linkedLineageEntities > 0) {
            sourceLineageState = "PARTIAL_SOURCE_LINEAGE";
        } else {
            sourceLineageState = "SOURCE_REVISION_UNAVAILABLE";
        }
        String lineageDescription = String.format(Locale.US,
            "Point-in-time source lineage: %d of %d series verified against authoritative cryptographic source artifacts.",
            linkedLineageEntities, totalLineageEntities);

        // Task 49, 61, 64, 83: Reproducible calculation execution context with verified lineage
        StressCalculationContextDto calculationContext = new StressCalculationContextDto(
            runId,
            inputFingerprint,
            snapshot.snapshotId(),
            requestedBenchmarkCode != null ? requestedBenchmarkCode : DEFAULT_BENCHMARK_CODE,
            effectiveAsOf,
            effectiveCutoff,
            METHODOLOGY_VERSION,
            IDENTIFICATION_POLICY_VERSION,
            EVIDENCE_POLICY_VERSION,
            CONSTRUCTION_CONVENTION,
            ATTRIBUTION_METHODOLOGY,
            true,
            String.format(Locale.US, "Deterministic execution under %s on snapshot %s", METHODOLOGY_VERSION, snapshot.snapshotId()),
            benchmarkSourceRevisionId,
            constituentSourceRevisions,
            sourceLineageState,
            lineageDescription
        );

        LocalDate minBmDate = bmObs.get(0).getEffectiveDate();
        LocalDate maxBmDate = bmObs.get(bmObs.size() - 1).getEffectiveDate();
        String effectiveWindowStr = minBmDate + " to " + maxBmDate;
        StressBenchmarkReferenceDto bmRef = new StressBenchmarkReferenceDto(
            benchmark.getCode(), benchmark.getName(), benchmark.getProvider(),
            benchmark.getId(), minBmDate, maxBmDate, bmObs.size(), "AVAILABLE",
            isFallbackBenchmark, comparabilityNotice,
            "TOTAL_RETURNS_INDEX",
            fallbackReason,
            "BENCHMARK_METHODOLOGY_V1",
            BigDecimal.valueOf(100.0),
            effectiveWindowStr
        );

        // 3. Deterministically Identify Benchmark Drawdown Episodes (>= 10% peak-to-trough) (Task 36)
        List<IdentifiedBenchmarkEpisode> identifiedEpisodes = identifyBenchmarkDrawdownEpisodes(bmObs, MIN_DRAWDOWN_THRESHOLD, benchmark.getCode());

        if (identifiedEpisodes.isEmpty()) {
            PortfolioStressSummaryDto emptySummary = new PortfolioStressSummaryDto(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                "COMPUTABLE_EPISODES_ONLY (n=0)",
                null, null, null, null,
                null, "None", "None",
                String.format(Locale.US, "Benchmark '%s' observation window (%s to %s, %d trading days) contains no market drawdown reaching the 10.0%% threshold under %s.",
                    benchmark.getCode(), minBmDate, maxBmDate, bmObs.size(), IDENTIFICATION_POLICY_VERSION),
                "ARITHMETIC_EPISODE_MEAN_ACROSS_COMPUTABLE_EPISODES", null,
                Collections.emptyList(),
                snapshot.snapshotId(), runId,
                0, 0, 0, 0, null, null, null,
                "No market drawdown qualified under the 10.0% threshold.",
                inputFingerprint
            );
            return new PortfolioStressAnalysisResponseDto(
                "NO_ELIGIBLE_EPISODES", bmRef, emptySummary, Collections.emptyList(),
                EVIDENCE_POLICY_VERSION, IDENTIFICATION_POLICY_VERSION,
                METHODOLOGY_VERSION, CONSTRUCTION_CONVENTION, ATTRIBUTION_METHODOLOGY,
                disclaimers, methodologyNotes, knownValueCoveragePct, unvaluedPositions,
                runId, effectiveAsOf, effectiveCutoff, BigDecimal.valueOf(ATTRIBUTION_RECONCILIATION_TOLERANCE_PCT),
                "DIRECTLY_COMPARABLE", snapshot, calculationContext,
                inputFingerprint, "AUTHORITATIVE_SOURCE_REVISION_TRACKED",
                effectiveAsOf.isBefore(LocalDate.now().minusDays(7)) ? "HISTORICAL_AUDIT_CONTEXT" : "LATEST_POINT_IN_TIME"
            );
        }

        // Map benchmark index levels by date for quick O(1) lookups
        Map<LocalDate, BigDecimal> bmLevelsByDate = new TreeMap<>();
        for (BenchmarkObservation o : bmObs) {
            bmLevelsByDate.put(o.getEffectiveDate(), o.getIndexLevel());
        }

        // 4. Calculate Portfolio Stress Proxy Behavior for Each Episode (Tasks 35, 38, 39, 40, 45)
        List<PortfolioStressEpisodeDto> episodeDtos = new ArrayList<>();
        int sufficientCount = 0;
        int limitedCount = 0;
        int insufficientCount = 0;
        int completeWindowCount = 0;
        int commonSubsetCount = 0;
        int withheldCount = 0;

        int directlyComparableCount = 0;
        int fallbackBmCount = 0;
        int commonDateCount = 0;
        int withheldGroupCount = 0;

        BigDecimal sumDirectlyComparableReturns = BigDecimal.ZERO;
        int directlyComparableComputableCount = 0;
        BigDecimal sumFallbackReturns = BigDecimal.ZERO;
        int fallbackComputableCount = 0;
        BigDecimal sumCommonDateReturns = BigDecimal.ZERO;
        int commonDateComputableCount = 0;

        BigDecimal worstPortDD = null;
        String worstDDEpisodeId = null;
        BigDecimal worstPortCumReturn = null;
        String worstReturnEpisodeId = null;
        BigDecimal sumCumReturns = BigDecimal.ZERO;
        int computableEpisodeCount = 0;
        int unavailableCount = 0;
        int episodesWithFallbackCount = 0;

        Map<Long, Double> fundTotalCumReturns = new HashMap<>();
        Map<Long, Integer> fundEpisodeCounts = new HashMap<>();
        for (Long id : fundIds) {
            fundTotalCumReturns.put(id, 0.0);
            fundEpisodeCounts.put(id, 0);
        }

        for (IdentifiedBenchmarkEpisode ep : identifiedEpisodes) {
            PortfolioStressEpisodeDto episodeDto = calculateEpisodePortfolioProxy(
                ep, bmLevelsByDate, fundIds, valuedSchemes, weights, weightPcts, fundNavHistory,
                benchmark.getCode(), isFallbackBenchmark, comparabilityNotice, effectiveAsOf, effectiveCutoff,
                knownValueCoveragePct, runId, snapshot.snapshotId()
            );
            episodeDtos.add(episodeDto);

            if (episodeDto.isFallbackBenchmark()) {
                episodesWithFallbackCount++;
            }

            boolean isEpisodeFallback = episodeDto.isFallbackBenchmark();
            boolean isCompleteWindow = "ELIGIBLE_COMPLETE_WINDOW".equals(episodeDto.calculationEligibilityOutcome());
            boolean isCommonSubset = "ELIGIBLE_COMMON_DATE_SUBSET".equals(episodeDto.calculationEligibilityOutcome());
            boolean isWithheld = episodeDto.calculationEligibilityOutcome() != null && episodeDto.calculationEligibilityOutcome().startsWith("WITHHELD");

            if (isEpisodeFallback) {
                fallbackBmCount++;
                if (episodeDto.portfolioCumulativeReturnPct() != null) {
                    sumFallbackReturns = sumFallbackReturns.add(episodeDto.portfolioCumulativeReturnPct());
                    fallbackComputableCount++;
                }
            } else if (isCompleteWindow) {
                directlyComparableCount++;
                if (episodeDto.portfolioCumulativeReturnPct() != null) {
                    sumDirectlyComparableReturns = sumDirectlyComparableReturns.add(episodeDto.portfolioCumulativeReturnPct());
                    directlyComparableComputableCount++;
                }
            } else if (isCommonSubset) {
                commonDateCount++;
                if (episodeDto.portfolioCumulativeReturnPct() != null) {
                    sumCommonDateReturns = sumCommonDateReturns.add(episodeDto.portfolioCumulativeReturnPct());
                    commonDateComputableCount++;
                }
            }

            if (isWithheld) {
                withheldGroupCount++;
            }

            if (isCompleteWindow) {
                completeWindowCount++;
            } else if (isCommonSubset) {
                commonSubsetCount++;
            } else if (isWithheld) {
                withheldCount++;
            }

            if ("SUFFICIENT_EVIDENCE".equals(episodeDto.evidenceState())) {
                sufficientCount++;
            } else if ("LIMITED_EVIDENCE".equals(episodeDto.evidenceState())) {
                limitedCount++;
            } else {
                insufficientCount++;
            }

            // Aggregate only computable episodes (Task 28.1 & Task 41)
            if (episodeDto.portfolioMaxDrawdownPct() != null) {
                if (worstPortDD == null || episodeDto.portfolioMaxDrawdownPct().compareTo(worstPortDD) < 0) {
                    worstPortDD = episodeDto.portfolioMaxDrawdownPct();
                    worstDDEpisodeId = episodeDto.episodeId();
                }
            }

            if (episodeDto.portfolioCumulativeReturnPct() != null) {
                if (worstPortCumReturn == null || episodeDto.portfolioCumulativeReturnPct().compareTo(worstPortCumReturn) < 0) {
                    worstPortCumReturn = episodeDto.portfolioCumulativeReturnPct();
                    worstReturnEpisodeId = episodeDto.episodeId();
                }
                sumCumReturns = sumCumReturns.add(episodeDto.portfolioCumulativeReturnPct());
                computableEpisodeCount++;
            } else {
                unavailableCount++;
            }

            // Track fund performance across computable episodes
            if (episodeDto.fundContributions() != null) {
                for (FundStressContributionDto fc : episodeDto.fundContributions()) {
                    if (fc.fundCumulativeReturnPct() != null) {
                        fundTotalCumReturns.put(fc.schemeOptionId(), fundTotalCumReturns.get(fc.schemeOptionId()) + fc.fundCumulativeReturnPct().doubleValue());
                        fundEpisodeCounts.put(fc.schemeOptionId(), fundEpisodeCounts.get(fc.schemeOptionId()) + 1);
                    }
                }
            }
        }

        int eligibleEpisodeCount = identifiedEpisodes.size();
        int excludedCount = unavailableCount;
        String aggregationDenominator = String.format(Locale.US, "COMPUTABLE_EPISODES_ONLY (n=%d of %d eligible)", computableEpisodeCount, eligibleEpisodeCount);

        // 5. Derive Consolidated Stress Summary Metrics (Task 41 & 45)
        BigDecimal avgCumReturn = computableEpisodeCount > 0
            ? sumCumReturns.divide(BigDecimal.valueOf(computableEpisodeCount), 2, RoundingMode.HALF_UP)
            : null;

        String mostResilientFund = "None";
        String greatestDetractorFundName = "None";
        double highestAvgFundReturn = -Double.MAX_VALUE;
        double lowestAvgFundReturn = Double.MAX_VALUE;

        for (Long id : fundIds) {
            int count = fundEpisodeCounts.get(id);
            if (count > 0) {
                double avg = fundTotalCumReturns.get(id) / count;
                SchemeOption opt = valuedSchemes.get(id);
                String name = getFundDisplayName(opt);
                if (avg > highestAvgFundReturn) {
                    highestAvgFundReturn = avg;
                    mostResilientFund = name;
                }
                if (avg < lowestAvgFundReturn) {
                    lowestAvgFundReturn = avg;
                    greatestDetractorFundName = name;
                }
            }
        }

        String overallAssessment = String.format(Locale.US,
            "Evaluated %d historical market stress episodes (>= 10%% benchmark drawdown). Computable episodes: %d (Complete window: %d, Common-date subset: %d, Withheld/Insufficient: %d). Worst portfolio proxy drawdown was %s (%s); average cumulative return was %s across computable episodes (%s). Most resilient constituent was '%s'; primary loss detractor was '%s'. Static current-allocation constant-weight daily rebalanced proxy with Carino exact additive attribution.",
            episodeDtos.size(), computableEpisodeCount, completeWindowCount, commonSubsetCount, withheldCount,
            worstPortDD != null ? String.format(Locale.US, "%.2f%%", worstPortDD.doubleValue()) : "N/A",
            worstDDEpisodeId != null ? worstDDEpisodeId : "N/A",
            avgCumReturn != null ? String.format(Locale.US, "%+.2f%%", avgCumReturn.doubleValue()) : "N/A",
            aggregationDenominator,
            mostResilientFund, greatestDetractorFundName
        );

        List<String> summaryLimitations = new ArrayList<>();
        summaryLimitations.add("Arithmetic episode mean across computable historical episodes only; does NOT represent expected future return, average annual return, or representative portfolio drawdown.");
        if (isFallbackBenchmark) {
            summaryLimitations.add(String.format(Locale.US,
                "Summary aggregates episodes evaluated against fallback benchmark %s instead of preferred broad-market %s.",
                benchmark.getCode(), DEFAULT_BENCHMARK_CODE));
        }
        if (insufficientCount > 0 || limitedCount > 0) {
            summaryLimitations.add(String.format(Locale.US,
                "Summary spans episodes with varying historical constituent coverage (Sufficient: %d, Limited: %d, Insufficient: %d). Episodes with truncated constituent history withhold portfolio proxy calculations without synthetic fabrication.",
                sufficientCount, limitedCount, insufficientCount));
        }

        String summaryComparabilityNotice = null;
        if (!summaryLimitations.isEmpty()) {
            summaryComparabilityNotice = String.join(" ", summaryLimitations);
        }

        BigDecimal directlyComparableAverageReturnPct = directlyComparableComputableCount > 0
            ? sumDirectlyComparableReturns.divide(BigDecimal.valueOf(directlyComparableComputableCount), 2, RoundingMode.HALF_UP)
            : null;
        BigDecimal fallbackBenchmarkAverageReturnPct = fallbackComputableCount > 0
            ? sumFallbackReturns.divide(BigDecimal.valueOf(fallbackComputableCount), 2, RoundingMode.HALF_UP)
            : null;
        BigDecimal commonDateSubsetAverageReturnPct = commonDateComputableCount > 0
            ? sumCommonDateReturns.divide(BigDecimal.valueOf(commonDateComputableCount), 2, RoundingMode.HALF_UP)
            : null;

        PortfolioStressSummaryDto summaryDto = new PortfolioStressSummaryDto(
            episodeDtos.size(),
            eligibleEpisodeCount,
            computableEpisodeCount,
            completeWindowCount,
            commonSubsetCount,
            withheldCount,
            sufficientCount,
            limitedCount,
            insufficientCount,
            unavailableCount,
            excludedCount,
            episodesWithFallbackCount,
            aggregationDenominator,
            worstPortDD, // Task 60: null instead of BigDecimal.ZERO
            worstDDEpisodeId,
            worstPortCumReturn, // Task 60: null instead of BigDecimal.ZERO
            worstReturnEpisodeId,
            avgCumReturn,
            mostResilientFund,
            greatestDetractorFundName,
            overallAssessment,
            "ARITHMETIC_EPISODE_MEAN_ACROSS_COMPUTABLE_EPISODES",
            summaryComparabilityNotice,
            summaryLimitations,
            snapshot.snapshotId(),
            runId,
            directlyComparableCount,
            fallbackBmCount,
            commonDateCount,
            withheldGroupCount,
            directlyComparableAverageReturnPct,
            fallbackBenchmarkAverageReturnPct,
            commonDateSubsetAverageReturnPct,
            "Episodes grouped by benchmark comparability (direct primary vs fallback) and observation window completeness (complete vs common-date subset).",
            inputFingerprint
        );

        // Overall comparability state (Task 37 & 41)
        String overallComparabilityState = isFallbackBenchmark
            ? "CONDITIONALLY_COMPARABLE_FALLBACK_BENCHMARK"
            : (insufficientCount > 0 ? "CONDITIONALLY_COMPARABLE_PARTIAL_HISTORY" : "DIRECTLY_COMPARABLE");

        // 6. Generate Deterministic Stress Investigation Findings and Persist Evidence (Tasks 44, 50, 65, 71, 80, 81)
        generateStressInvestigationFindings(
            investorId, portfolioId, episodeDtos, benchmark.getCode(), isFallbackBenchmark, runId,
            snapshot.snapshotId(), inputFingerprint, findings, effectiveAsOf, effectiveCutoff
        );

        // 7. Persist Calculation Manifest (Task 85, 104)
        persistCalculationManifest(
            investorId, portfolioId, runId, snapshot, inputFingerprint, effectiveAsOf, effectiveCutoff,
            calculationContext, summaryDto, episodeDtos
        );

        // 8. Persist Immutable Portfolio Allocation Snapshot (Task 105, 106)
        Map<Long, Double> allocationWeightMap = new HashMap<>();
        if (fundIds != null && weights != null) {
            for (int i = 0; i < fundIds.size() && i < weights.length; i++) {
                allocationWeightMap.put(fundIds.get(i), weights[i]);
            }
        }
        persistAllocationSnapshotIfAbsent(
            investorId, portfolioId, effectiveAsOf, effectiveCutoff, snapshot,
            fundIds, allocationWeightMap, fundMarketValues, valuedSchemes
        );

        // 9. Persist Data Quality Signals with Source-Revision Lineage (Task 82, 103)
        persistDataQualitySignals(
            investorId, portfolioId, runId, benchmark.getCode(), effectiveAsOf, episodeDtos
        );

        return new PortfolioStressAnalysisResponseDto(
            "SUCCESS",
            bmRef,
            summaryDto,
            episodeDtos,
            EVIDENCE_POLICY_VERSION,
            IDENTIFICATION_POLICY_VERSION,
            METHODOLOGY_VERSION,
            CONSTRUCTION_CONVENTION,
            ATTRIBUTION_METHODOLOGY,
            disclaimers,
            methodologyNotes,
            knownValueCoveragePct,
            unvaluedPositions,
            runId,
            effectiveAsOf,
            effectiveCutoff,
            BigDecimal.valueOf(ATTRIBUTION_RECONCILIATION_TOLERANCE_PCT),
            overallComparabilityState,
            snapshot,
            calculationContext,
            inputFingerprint,
            sourceLineageState,
            effectiveAsOf.isBefore(LocalDate.now().minusDays(7)) ? "HISTORICAL_AUDIT_CONTEXT" : "LATEST_POINT_IN_TIME"
        );
    }

    /**
     * Resolves the market-stress reference benchmark from the repository (Task 37).
     * Hierarchy: NIFTY_500_TRI -> NIFTY_50_TRI -> Any TRI.
     */
    private Benchmark resolveStressBenchmark(String requestedBenchmarkCode) {
        if (requestedBenchmarkCode != null && !requestedBenchmarkCode.isBlank()) {
            Optional<Benchmark> requested = benchmarkRepository.findByCode(requestedBenchmarkCode.trim());
            if (requested.isPresent()) {
                return requested.get();
            }
        }

        // Try primary broad Indian equity TRI benchmark
        Optional<Benchmark> primary = benchmarkRepository.findByCode(DEFAULT_BENCHMARK_CODE);
        if (primary.isPresent()) {
            return primary.get();
        }

        // Fallback to NIFTY_50_TRI
        Optional<Benchmark> fallback = benchmarkRepository.findByCode(FALLBACK_BENCHMARK_CODE);
        if (fallback.isPresent()) {
            return fallback.get();
        }

        // Fallback to any registered benchmark
        List<Benchmark> all = benchmarkRepository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Deterministically identifies all distinct, non-overlapping drawdown episodes where
     * benchmark peak-to-trough decline reaches or exceeds the specified threshold (e.g. 10.0%) (Task 36).
     */
    public List<IdentifiedBenchmarkEpisode> identifyBenchmarkDrawdownEpisodes(
        List<BenchmarkObservation> bmObs,
        double minDrawdownThreshold
    ) {
        return identifyBenchmarkDrawdownEpisodes(bmObs, minDrawdownThreshold, DEFAULT_BENCHMARK_CODE);
    }

    /**
     * Overloaded deterministic episode identifier with benchmark identity (Task 36 & 37).
     */
    public List<IdentifiedBenchmarkEpisode> identifyBenchmarkDrawdownEpisodes(
        List<BenchmarkObservation> bmObs,
        double minDrawdownThreshold,
        String benchmarkCode
    ) {
        if (bmObs == null || bmObs.size() < 2) {
            return Collections.emptyList();
        }

        List<IdentifiedBenchmarkEpisode> episodes = new ArrayList<>();

        int n = bmObs.size();
        BigDecimal runningPeak = bmObs.get(0).getIndexLevel();
        int peakIdx = 0;

        int currentEpisodeStartIdx = -1;
        int currentFirstDeclineIdx = -1;
        int currentTroughIdx = -1;
        BigDecimal currentMaxDD = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            BigDecimal currentLevel = bmObs.get(i).getIndexLevel();

            if (currentLevel.compareTo(runningPeak) >= 0) {
                // Peak is reached or surpassed
                if (currentEpisodeStartIdx >= 0 && currentMaxDD.doubleValue() <= -minDrawdownThreshold) {
                    // Previous episode has achieved full recovery on day i!
                    episodes.add(buildIdentifiedEpisode(bmObs, currentEpisodeStartIdx, currentFirstDeclineIdx, currentTroughIdx, i, currentMaxDD, benchmarkCode));
                    currentEpisodeStartIdx = -1;
                    currentFirstDeclineIdx = -1;
                    currentTroughIdx = -1;
                    currentMaxDD = BigDecimal.ZERO;
                }
                runningPeak = currentLevel;
                peakIdx = i;
            } else {
                // Calculate current drawdown from running peak
                BigDecimal dd = currentLevel.subtract(runningPeak, MC).divide(runningPeak, MC);

                if (currentEpisodeStartIdx < 0) {
                    if (dd.doubleValue() <= -minDrawdownThreshold) {
                        currentEpisodeStartIdx = peakIdx;
                        // Identify the first trading date strictly dropping below peak level
                        int firstDec = peakIdx + 1;
                        for (int k = peakIdx + 1; k <= i; k++) {
                            if (bmObs.get(k).getIndexLevel().compareTo(bmObs.get(peakIdx).getIndexLevel()) < 0) {
                                firstDec = k;
                                break;
                            }
                        }
                        currentFirstDeclineIdx = firstDec;
                        currentTroughIdx = i;
                        currentMaxDD = dd;
                    }
                } else {
                    if (dd.compareTo(currentMaxDD) < 0) {
                        currentMaxDD = dd;
                        currentTroughIdx = i;
                    }
                }
            }
        }

        // Check if there is an unrecovered ongoing drawdown reaching the threshold as of historical cutoff
        if (currentEpisodeStartIdx >= 0 && currentMaxDD.doubleValue() <= -minDrawdownThreshold) {
            episodes.add(buildIdentifiedEpisode(bmObs, currentEpisodeStartIdx, currentFirstDeclineIdx, currentTroughIdx, -1, currentMaxDD, benchmarkCode));
        }

        // Sort descending by start date (most recent episodes first for institutional review)
        episodes.sort((a, b) -> b.peakDate().compareTo(a.peakDate()));

        return episodes;
    }

    private IdentifiedBenchmarkEpisode buildIdentifiedEpisode(
        List<BenchmarkObservation> bmObs,
        int peakIdx,
        int firstDeclineIdx,
        int troughIdx,
        int recoveryIdx,
        BigDecimal maxDrawdown,
        String benchmarkCode
    ) {
        LocalDate peakDate = bmObs.get(peakIdx).getEffectiveDate();
        LocalDate firstDeclineDate = (firstDeclineIdx >= 0 && firstDeclineIdx < bmObs.size())
            ? bmObs.get(firstDeclineIdx).getEffectiveDate()
            : (peakIdx + 1 < bmObs.size() ? bmObs.get(peakIdx + 1).getEffectiveDate() : peakDate);
        LocalDate troughDate = bmObs.get(troughIdx).getEffectiveDate();
        LocalDate recoveryDate = (recoveryIdx >= 0 && recoveryIdx < bmObs.size())
            ? bmObs.get(recoveryIdx).getEffectiveDate() : null;
        boolean hasRecovered = recoveryIdx >= 0;

        BigDecimal peakLevel = bmObs.get(peakIdx).getIndexLevel();
        BigDecimal troughLevel = bmObs.get(troughIdx).getIndexLevel();
        BigDecimal recoveryLevel = hasRecovered ? bmObs.get(recoveryIdx).getIndexLevel() : null;

        BigDecimal maxDDPct = maxDrawdown.multiply(HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);

        // Assign contextual regime descriptor or default date-based name
        String bmCode = benchmarkCode != null ? benchmarkCode : DEFAULT_BENCHMARK_CODE;
        // Task 47: Stable episode identity (STR-EP-{bm}-{peakDate}-{firstDeclineDate}-V2) and legacy mapping
        String episodeId = String.format("STR-EP-%s-%s-%s-V2", bmCode, peakDate, firstDeclineDate);
        String legacyEpisodeId = String.format("STRESS-%s-%s-TO-%s", bmCode, peakDate, troughDate);
        String episodeName = null;
        String regimeCategory = "BROAD_MARKET_CORRECTION";

        for (MarketRegimeDescriptor reg : KNOWN_REGIMES) {
            if (!peakDate.isBefore(reg.approximateStart()) && !troughDate.isAfter(reg.approximateEnd())) {
                episodeName = reg.institutionalTitle();
                regimeCategory = reg.regimeCategory();
                break;
            }
        }

        if (episodeName == null) {
            episodeName = String.format(Locale.US, "%s %d Market Correction (%.1f%%)",
                peakDate.getMonth().name().substring(0, 1) + peakDate.getMonth().name().substring(1).toLowerCase(),
                peakDate.getYear(), Math.abs(maxDDPct.doubleValue())
            );
        }

        int tradingDaysToTrough = Math.max(0, troughIdx - peakIdx);
        Integer tradingDaysToRecovery = hasRecovered ? Math.max(0, recoveryIdx - troughIdx) : null;
        int calendarDaysToTrough = (int) Math.max(0, ChronoUnit.DAYS.between(peakDate, troughDate));
        Integer calendarDaysToRecovery = hasRecovered && recoveryDate != null ? (int) Math.max(0, ChronoUnit.DAYS.between(troughDate, recoveryDate)) : null;

        // Task 47: Explicit lifecycle transition determination
        String lifecycleState;
        String lifecycleTransitionContext;
        if (hasRecovered) {
            lifecycleState = "RECOVERED";
            lifecycleTransitionContext = String.format(Locale.US,
                "Benchmark achieved recovery to pre-decline peak (%s) on %s (%d trading sessions / %d calendar days after trough).",
                peakLevel, recoveryDate, tradingDaysToRecovery, calendarDaysToRecovery);
        } else if (troughIdx == bmObs.size() - 1) {
            lifecycleState = "ACTIVE_DECLINE";
            lifecycleTransitionContext = String.format(Locale.US,
                "Benchmark is in active decline with latest observation (%s) at current trough.",
                troughDate);
        } else {
            boolean hasDataGap = false;
            for (int k = troughIdx + 1; k < bmObs.size(); k++) {
                if (ChronoUnit.DAYS.between(bmObs.get(k - 1).getEffectiveDate(), bmObs.get(k).getEffectiveDate()) > 7) {
                    hasDataGap = true;
                    break;
                }
            }
            if (hasDataGap) {
                lifecycleState = "RECOVERY_UNVERIFIABLE_DATA_GAP";
                lifecycleTransitionContext = "Recovery status cannot be verified due to calendar observation gap (> 7 days) in benchmark series post-trough.";
            } else {
                BigDecimal latestLevel = bmObs.get(bmObs.size() - 1).getIndexLevel();
                if (latestLevel.compareTo(troughLevel) > 0) {
                    lifecycleState = "ONGOING_RECOVERY";
                    lifecycleTransitionContext = String.format(Locale.US,
                        "Benchmark has rebounded from trough (%s) to %s but has not yet recovered to pre-stress peak (%s) as of analysis cutoff (%s).",
                        troughLevel, latestLevel, peakLevel, bmObs.get(bmObs.size() - 1).getEffectiveDate());
                } else {
                    lifecycleState = "UNRECOVERED_CUTOFF_REACHED";
                    lifecycleTransitionContext = String.format(Locale.US,
                        "Decline remained unrecovered at historical analysis cutoff (%s).",
                        bmObs.get(bmObs.size() - 1).getEffectiveDate());
                }
            }
        }

        return new IdentifiedBenchmarkEpisode(
            episodeId,
            legacyEpisodeId,
            episodeName,
            regimeCategory,
            peakDate,
            firstDeclineDate,
            troughDate,
            recoveryDate,
            hasRecovered,
            maxDDPct,
            peakLevel,
            troughLevel,
            recoveryLevel,
            lifecycleState,
            lifecycleTransitionContext,
            tradingDaysToTrough,
            tradingDaysToRecovery,
            calendarDaysToTrough,
            calendarDaysToRecovery
        );
    }

    /**
     * Evaluates the Carino logarithmic factor ln(1 + r) / r in a numerically stable manner (Task 25.1 & 42).
     * - For r <= -1.0 or non-finite: returns Double.NaN (outside domain / wealth wiped out).
     * - For |r| < 1e-12: uses 2nd-order Taylor expansion 1.0 - 0.5 * r + (r * r) / 3.0.
     * - For valid finite r > -1.0: uses Math.log1p(r) / r to prevent catastrophic cancellation.
     */
    public static double calculateStableCarinoFactor(double r) {
        if (Double.isNaN(r) || Double.isInfinite(r) || r <= -1.0) {
            return Double.NaN;
        }
        double absR = Math.abs(r);
        if (absR < 1e-12) {
            return 1.0 - 0.5 * r + (r * r) / 3.0;
        }
        return Math.log1p(r) / r;
    }

    /**
     * Backward-compatible overload for calculateEpisodePortfolioProxy.
     */
    public PortfolioStressEpisodeDto calculateEpisodePortfolioProxy(
        IdentifiedBenchmarkEpisode ep,
        Map<LocalDate, BigDecimal> bmLevelsByDate,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String benchmarkComparabilityNotice,
        LocalDate asOfDate,
        BigDecimal knownValueCoveragePct
    ) {
        return calculateEpisodePortfolioProxy(
            ep, bmLevelsByDate, fundIds, valuedSchemes, weights, weightPcts, fundNavHistory,
            benchmarkCode, isFallbackBenchmark, benchmarkComparabilityNotice, asOfDate, OffsetDateTime.now(),
            knownValueCoveragePct, UUID.randomUUID().toString()
        );
    }

    /**
     * Backward-compatible overload with explicit knowledge cutoff.
     */
    public PortfolioStressEpisodeDto calculateEpisodePortfolioProxy(
        IdentifiedBenchmarkEpisode ep,
        Map<LocalDate, BigDecimal> bmLevelsByDate,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String benchmarkComparabilityNotice,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        BigDecimal knownValueCoveragePct
    ) {
        return calculateEpisodePortfolioProxy(
            ep, bmLevelsByDate, fundIds, valuedSchemes, weights, weightPcts, fundNavHistory,
            benchmarkCode, isFallbackBenchmark, benchmarkComparabilityNotice, asOfDate, knowledgeCutoff,
            knownValueCoveragePct, UUID.randomUUID().toString()
        );
    }

    /**
     * Backward-compatible 14-parameter calculateEpisodePortfolioProxy.
     */
    public PortfolioStressEpisodeDto calculateEpisodePortfolioProxy(
        IdentifiedBenchmarkEpisode ep,
        Map<LocalDate, BigDecimal> bmLevelsByDate,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String benchmarkComparabilityNotice,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        BigDecimal knownValueCoveragePct,
        String calculationRunId
    ) {
        String defaultSnapshotId = "ALLOC-SNAP-" + asOfDate + "-" + (calculationRunId != null && calculationRunId.length() >= 8 ? calculationRunId.substring(0, 8) : "DEFAULT");
        return calculateEpisodePortfolioProxy(
            ep, bmLevelsByDate, fundIds, valuedSchemes, weights, weightPcts, fundNavHistory,
            benchmarkCode, isFallbackBenchmark, benchmarkComparabilityNotice, asOfDate, knowledgeCutoff,
            knownValueCoveragePct, calculationRunId, defaultSnapshotId
        );
    }

    /**
     * Evaluates portfolio behavior during a single identified benchmark stress episode.
     * Implements Tasks 35, 36, 37, 38, 39, 40, 45, 46, 47, 48, 51, 52, 53, 54.
     */
    public PortfolioStressEpisodeDto calculateEpisodePortfolioProxy(
        IdentifiedBenchmarkEpisode ep,
        Map<LocalDate, BigDecimal> bmLevelsByDate,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String benchmarkComparabilityNotice,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        BigDecimal knownValueCoveragePct,
        String calculationRunId,
        String portfolioAllocationSnapshotId
    ) {
        LocalDate startDate = ep.peakDate();
        LocalDate endDate = ep.hasRecovered() && ep.recoveryDate() != null ? ep.recoveryDate() : ep.troughDate();
        OffsetDateTime effectiveCutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        // 1. Gather all benchmark trading dates in the episode window (Task 35 & 52)
        List<LocalDate> bmDatesInWindow = new ArrayList<>();
        for (LocalDate d : bmLevelsByDate.keySet()) {
            if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                bmDatesInWindow.add(d);
            }
        }
        int totalBmDays = bmDatesInWindow.size();

        // 2. Date-Level Observation Accounting across all constituents (Task 35 & 52)
        Map<Long, Integer> fundObservationCounts = new HashMap<>();
        Map<Long, LocalDate> fundEarliestNavDates = new HashMap<>();
        Map<Long, Integer> fundPreInceptionCounts = new HashMap<>();
        Map<Long, Integer> fundMissingNavCounts = new HashMap<>();
        Map<Long, Integer> fundInvalidNavCounts = new HashMap<>();

        List<String> includedConstituents = new ArrayList<>();
        List<String> excludedOrUnavailableConstituents = new ArrayList<>();
        double missingWeightSum = 0.0;
        int missingConstituentCount = 0;
        int partialConstituentCount = 0;

        for (int i = 0; i < fundIds.size(); i++) {
            Long id = fundIds.get(i);
            SchemeOption opt = valuedSchemes.get(id);
            String fundName = getFundDisplayName(opt);
            Map<LocalDate, BigDecimal> navs = fundNavHistory.get(id);

            int countInWindow = 0;
            int preInceptionCount = 0;
            int missingNavCount = 0;
            int invalidNavCount = 0;
            LocalDate earliest = null;

            if (navs != null && !navs.isEmpty()) {
                for (LocalDate d : navs.keySet()) {
                    if (earliest == null || d.isBefore(earliest)) {
                        earliest = d;
                    }
                }
                for (LocalDate d : bmDatesInWindow) {
                    if (earliest != null && d.isBefore(earliest)) {
                        preInceptionCount++;
                    } else if (!navs.containsKey(d) || navs.get(d) == null) {
                        missingNavCount++;
                    } else if (navs.get(d).compareTo(BigDecimal.ZERO) <= 0) {
                        invalidNavCount++;
                    } else {
                        countInWindow++;
                    }
                }
            } else {
                missingNavCount = totalBmDays;
            }

            fundObservationCounts.put(id, countInWindow);
            fundEarliestNavDates.put(id, earliest);
            fundPreInceptionCounts.put(id, preInceptionCount);
            fundMissingNavCounts.put(id, missingNavCount);
            fundInvalidNavCounts.put(id, invalidNavCount);

            if (countInWindow == 0) {
                missingConstituentCount++;
                missingWeightSum += weights[i];
                excludedOrUnavailableConstituents.add(fundName);
            } else {
                includedConstituents.add(fundName);
                if (countInWindow < totalBmDays) {
                    partialConstituentCount++;
                }
            }
        }

        // Identify common dates where EVERY included constituent has a valid return observation (Task 35 & 45)
        Set<LocalDate> commonDates = new HashSet<>();
        List<String> excludedDateReasons = new ArrayList<>();
        int totalPreInceptionExclusions = 0;
        int totalMissingNavExclusions = 0;
        int totalInvalidNavExclusions = 0;

        for (LocalDate d : bmDatesInWindow) {
            boolean allConstituentsAvailable = true;
            for (Long id : fundIds) {
                Map<LocalDate, BigDecimal> navs = fundNavHistory.get(id);
                LocalDate earliest = fundEarliestNavDates.get(id);
                if (earliest != null && d.isBefore(earliest)) {
                    allConstituentsAvailable = false;
                    totalPreInceptionExclusions++;
                    break;
                }
                if (navs == null || !navs.containsKey(d) || navs.get(d) == null) {
                    allConstituentsAvailable = false;
                    totalMissingNavExclusions++;
                    break;
                }
                if (navs.get(d).compareTo(BigDecimal.ZERO) <= 0) {
                    allConstituentsAvailable = false;
                    totalInvalidNavExclusions++;
                    break;
                }
            }

            if (allConstituentsAvailable) {
                commonDates.add(d);
            }
        }

        List<LocalDate> sortedAlignedDates = new ArrayList<>(commonDates);
        Collections.sort(sortedAlignedDates);
        int alignedCount = sortedAlignedDates.size();
        int excludedBmDaysCount = Math.max(0, totalBmDays - alignedCount);

        LocalDate commonSubsetStart = !sortedAlignedDates.isEmpty() ? sortedAlignedDates.get(0) : startDate;
        LocalDate commonSubsetEnd = !sortedAlignedDates.isEmpty() ? sortedAlignedDates.get(sortedAlignedDates.size() - 1) : endDate;

        if (totalPreInceptionExclusions > 0) {
            excludedDateReasons.add(String.format(Locale.US, "%d date instance(s) excluded due to constituent pre-inception (fund launched post episode start).", totalPreInceptionExclusions));
        }
        if (totalMissingNavExclusions > 0) {
            excludedDateReasons.add(String.format(Locale.US, "%d date instance(s) excluded due to missing constituent NAV observations.", totalMissingNavExclusions));
        }
        if (totalInvalidNavExclusions > 0) {
            excludedDateReasons.add(String.format(Locale.US, "%d date instance(s) excluded due to non-positive or invalid NAV observations.", totalInvalidNavExclusions));
        }

        BigDecimal missingAllocationWeightPct = BigDecimal.valueOf(missingWeightSum * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal portfolioCoverageRatioPct = totalBmDays > 0
            ? BigDecimal.valueOf((alignedCount * 100.0) / totalBmDays).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // 3. Centralized Eligibility Evaluation (Task 45)
        String benchmarkObservationAvailability = totalBmDays > 0 ? "AVAILABLE" : "NO_OBSERVATIONS";
        String constituentObservationAvailability = (missingConstituentCount == 0 && partialConstituentCount == 0)
            ? "ALL_CONSTITUENTS_AVAILABLE"
            : (missingConstituentCount == 0 ? "PARTIAL_CONSTITUENTS_AVAILABLE" : (missingConstituentCount < fundIds.size() ? "INSUFFICIENT_CONSTITUENT_HISTORY" : "UNAVAILABLE_SOURCE_DATA"));
        String commonDateSynchronization = (alignedCount == totalBmDays)
            ? "SYNCHRONIZED_COMPLETE"
            : (alignedCount >= MIN_EPISODE_OBS_LIMITED ? "SYNCHRONIZED_COMMON_SUBSET" : "UNSYNCHRONIZED_INSUFFICIENT_OVERLAP");
        String constituentUniverseCompleteness = (missingConstituentCount == 0)
            ? "COMPLETE_PORTFOLIO_UNIVERSE" : "INCOMPLETE_PORTFOLIO_UNIVERSE";
        String constituentWindowCoverage = (alignedCount == totalBmDays)
            ? "COMPLETE_WINDOW" : (alignedCount > 0 ? "COMMON_DATE_SUBSET" : "NO_COVERAGE");

        String evidenceState;
        String limitationReason = null;
        String partialPortfolioNotice = null;

        if (missingConstituentCount > 0) {
            // Missing constituent history rule (Tasks 27.2, 33, 35, 45):
            // Cannot evaluate complete constant-weight portfolio proxy without synthetic fabrication!
            evidenceState = "INSUFFICIENT_EVIDENCE";
            limitationReason = String.format(Locale.US,
                "Constituent fund(s) (%s, representing %.2f%% of capital allocation) lack historical observations during this episode window (fund inception post-dates episode). Portfolio return proxy is withheld without synthetic data extrapolation under §4 AGENTS.md.",
                String.join(", ", excludedOrUnavailableConstituents), missingAllocationWeightPct.doubleValue()
            );
            partialPortfolioNotice = "Incomplete constituent historical coverage precludes calculation of a synchronous constant-weight daily rebalanced portfolio proxy. Standalone constituent metrics are preserved for available funds.";

            return buildMissingConstituentEpisodeResponse(
                ep, startDate, endDate, alignedCount, totalBmDays, evidenceState, limitationReason, partialPortfolioNotice,
                fundIds, valuedSchemes, weights, weightPcts, fundNavHistory, bmDatesInWindow,
                fundObservationCounts, fundEarliestNavDates,
                fundPreInceptionCounts, fundMissingNavCounts, fundInvalidNavCounts,
                benchmarkCode, isFallbackBenchmark, benchmarkComparabilityNotice, knownValueCoveragePct,
                missingConstituentCount, partialConstituentCount, includedConstituents, excludedOrUnavailableConstituents,
                missingAllocationWeightPct, portfolioCoverageRatioPct, asOfDate, effectiveCutoff,
                commonSubsetStart, commonSubsetEnd, excludedBmDaysCount, excludedDateReasons,
                totalPreInceptionExclusions, totalMissingNavExclusions, totalInvalidNavExclusions,
                calculationRunId, portfolioAllocationSnapshotId
            );
        } else if (alignedCount < MIN_EPISODE_OBS_LIMITED) {
            evidenceState = "INSUFFICIENT_EVIDENCE";
            limitationReason = String.format(Locale.US,
                "Aligned trading days (%d) are below the minimum threshold (%d days) required for statistical reliability under %s.",
                alignedCount, MIN_EPISODE_OBS_LIMITED, EVIDENCE_POLICY_VERSION
            );
            return buildLimitedEpisodeResponse(
                ep, startDate, endDate, alignedCount, totalBmDays, evidenceState, limitationReason,
                fundIds, valuedSchemes, weightPcts, benchmarkCode, isFallbackBenchmark, benchmarkComparabilityNotice,
                knownValueCoveragePct, asOfDate, effectiveCutoff,
                commonSubsetStart, commonSubsetEnd, excludedBmDaysCount, excludedDateReasons,
                calculationRunId, portfolioAllocationSnapshotId
            );
        } else if (alignedCount >= MIN_EPISODE_OBS_SUFFICIENT && totalBmDays > 0
            && ((double) alignedCount / totalBmDays) >= MIN_COVERAGE_RATIO_SUFFICIENT
            && knownValueCoveragePct.doubleValue() >= MIN_KNOWN_VALUE_COVERAGE_SUFFICIENT) {
            evidenceState = "SUFFICIENT_EVIDENCE";
        } else {
            evidenceState = "LIMITED_EVIDENCE";
            limitationReason = String.format(Locale.US,
                "Aligned trading days (%d of %d benchmark days, %.1f%%) or portfolio valuation coverage (%.1f%%) provide partial historical coverage under %s.",
                alignedCount, totalBmDays, portfolioCoverageRatioPct.doubleValue(),
                knownValueCoveragePct.doubleValue(), EVIDENCE_POLICY_VERSION
            );
            partialPortfolioNotice = String.format(Locale.US,
                "Calculation evaluates an aligned common-date subset (%s to %s, %d of %d benchmark trading days). It does not represent a complete benchmark-window result.",
                commonSubsetStart, commonSubsetEnd, alignedCount, totalBmDays
            );
        }

        // 4. Check observation synchronization, calendar gaps & flat streaks with Deduplication (Tasks 38 & 51)
        Map<String, StressDataQualitySignalDto> dqMap = new LinkedHashMap<>();
        List<String> intervalNotices = new ArrayList<>();

        // Calendar gap check across aligned trading observations (Tasks 38 & 68)
        for (int k = 1; k < sortedAlignedDates.size(); k++) {
            LocalDate dPrev = sortedAlignedDates.get(k - 1);
            LocalDate dCurr = sortedAlignedDates.get(k);
            long daysBetween = ChronoUnit.DAYS.between(dPrev, dCurr);
            if (daysBetween > 7) {
                String desc = String.format(Locale.US,
                    "Calendar gap of %d days between trading observations (%s to %s) exceeds standard 7-day threshold.",
                    daysBetween, dPrev, dCurr);
                intervalNotices.add(desc);
                String sigId = StressDataQualitySignalDto.generateSignalId("OBSERVATION_GAP_DETECTED", null, dPrev, dCurr);
                dqMap.putIfAbsent(sigId, new StressDataQualitySignalDto(
                    sigId, "NEWLY_DETECTED", "OBSERVATION_GAP_DETECTED", "WARNING", null, null,
                    dPrev, dCurr, (int) daysBetween, desc, false,
                    calculationRunId, OffsetDateTime.now(),
                    "BM-REV-" + benchmarkCode, true, benchmarkCode
                ));
            }
        }

        // Benchmark observation flat streak tracking (Task 68)
        int bmMaxStreak = 1;
        int bmCurStreak = 1;
        LocalDate bmStreakStart = null;
        LocalDate bmStreakEnd = null;
        BigDecimal bmPrevLvl = null;
        for (LocalDate d : sortedAlignedDates) {
            BigDecimal bmLevel = bmLevelsByDate.get(d);
            if (bmLevel != null && bmPrevLvl != null && bmLevel.compareTo(bmPrevLvl) == 0) {
                bmCurStreak++;
                if (bmCurStreak > bmMaxStreak) {
                    bmMaxStreak = bmCurStreak;
                    bmStreakEnd = d;
                }
            } else {
                bmCurStreak = 1;
                bmStreakStart = d;
            }
            bmPrevLvl = bmLevel;
        }
        if (bmMaxStreak >= 3) {
            String desc = String.format(Locale.US,
                "Benchmark '%s' exhibited %d consecutive identical index levels during episode, indicating potential holiday schedule or reporting delay.",
                benchmarkCode, bmMaxStreak);
            intervalNotices.add(desc);
            String sigId = StressDataQualitySignalDto.generateSignalId("POTENTIAL_STALE_NAV", null, bmStreakStart, bmStreakEnd);
            dqMap.putIfAbsent(sigId, new StressDataQualitySignalDto(
                sigId, "NEWLY_DETECTED", "POTENTIAL_STALE_NAV", "WARNING", null, null,
                bmStreakStart, bmStreakEnd, bmMaxStreak, desc, false,
                calculationRunId, OffsetDateTime.now(),
                "BM-REV-" + benchmarkCode, true, benchmarkCode
            ));
        }

        for (Long id : fundIds) {
            Map<LocalDate, BigDecimal> navs = fundNavHistory.get(id);
            int maxFlatStreak = 1;
            int currentStreak = 1;
            LocalDate streakStart = null;
            LocalDate streakEnd = null;
            BigDecimal prevNav = null;

            for (LocalDate d : sortedAlignedDates) {
                BigDecimal nav = navs != null ? navs.get(d) : null;
                if (nav != null && prevNav != null && nav.compareTo(prevNav) == 0) {
                    currentStreak++;
                    if (currentStreak > maxFlatStreak) {
                        maxFlatStreak = currentStreak;
                        streakEnd = d;
                    }
                } else {
                    currentStreak = 1;
                    streakStart = d;
                }
                prevNav = nav;
            }

            if (maxFlatStreak >= 3) {
                SchemeOption opt = valuedSchemes.get(id);
                String fName = getFundDisplayName(opt);
                String desc = String.format(Locale.US,
                    "Constituent '%s' exhibited %d consecutive identical NAV observations during episode, indicating potential non-trading or stale reporting.",
                    fName, maxFlatStreak);
                intervalNotices.add(desc);
                String sigId = StressDataQualitySignalDto.generateSignalId("POTENTIAL_STALE_NAV", id, streakStart, streakEnd);
                dqMap.putIfAbsent(sigId, new StressDataQualitySignalDto(
                    sigId, "NEWLY_DETECTED", "POTENTIAL_STALE_NAV", "WARNING", id, fName,
                    streakStart, streakEnd, maxFlatStreak, desc, false,
                    calculationRunId, OffsetDateTime.now(),
                    "NAV-REV-" + id, false, null
                ));
            }
        }

        if (dqMap.isEmpty()) {
            String sigId = StressDataQualitySignalDto.generateSignalId("NO_ANOMALY_DETECTED", null, startDate, endDate);
            dqMap.put(sigId, new StressDataQualitySignalDto(
                sigId, "NO_ANOMALY_DETECTED", "INFO", null, null,
                startDate, endDate, 0,
                "No calendar gaps (> 7 days) or consecutive flat NAV streaks (>= 3 observations) detected during episode window.",
                false, "INFORMATIONAL", calculationRunId, OffsetDateTime.now()
            ));
        }
        List<StressDataQualitySignalDto> dataQualitySignals = new ArrayList<>(dqMap.values());

        // 5. Calculate Daily Returns & Value Trajectories (Task 25.1 & 39)
        int nSteps = sortedAlignedDates.size();
        List<Double> portDailyReturns = new ArrayList<>();
        List<Double> bmDailyReturns = new ArrayList<>();
        Map<Long, List<Double>> fundDailyReturns = new LinkedHashMap<>();
        for (Long id : fundIds) {
            fundDailyReturns.put(id, new ArrayList<>());
        }

        for (int k = 1; k < nSteps; k++) {
            LocalDate dPrev = sortedAlignedDates.get(k - 1);
            LocalDate dCurr = sortedAlignedDates.get(k);

            // Benchmark daily return
            BigDecimal bmPrev = bmLevelsByDate.get(dPrev);
            BigDecimal bmCurr = bmLevelsByDate.get(dCurr);
            double rBm = (bmPrev != null && bmPrev.compareTo(BigDecimal.ZERO) > 0 && bmCurr != null)
                ? bmCurr.subtract(bmPrev, MC).divide(bmPrev, MC).doubleValue()
                : 0.0;
            bmDailyReturns.add(rBm);

            // Fund daily returns and portfolio daily weighted return: R_{p,t} = \sum w_i R_{i,t}
            double rPort = 0.0;
            for (int i = 0; i < fundIds.size(); i++) {
                Long id = fundIds.get(i);
                BigDecimal navPrev = fundNavHistory.get(id).get(dPrev);
                BigDecimal navCurr = fundNavHistory.get(id).get(dCurr);
                double rFund = (navPrev != null && navPrev.compareTo(BigDecimal.ZERO) > 0 && navCurr != null)
                    ? navCurr.subtract(navPrev, MC).divide(navPrev, MC).doubleValue()
                    : 0.0;
                fundDailyReturns.get(id).add(rFund);
                rPort += weights[i] * rFund;
            }
            portDailyReturns.add(rPort);
        }

        // 6. Track Value Series, Peak, Trough, Recovery, Drawdown & Duration (Task 39 & 53)
        boolean wealthPathInvalid = false;
        double portVal = 1.0;
        double portPeakVal = 1.0;
        double portMaxDD = 0.0;
        LocalDate portPeakDate = sortedAlignedDates.get(0);
        LocalDate portTroughDate = sortedAlignedDates.get(0);
        LocalDate portRecoveryDate = null;
        boolean portRecovered = false;
        int portTroughStep = 0;
        int portRecoveryStep = -1;

        double worstDaily = 0.0;
        LocalDate worstDailyDate = sortedAlignedDates.get(0);
        double bestDaily = -Double.MAX_VALUE;
        LocalDate bestDailyDate = sortedAlignedDates.get(0);

        StressWithholdingReasonCode invalidReasonCode = null;
        String invalidReasonExplanation = null;

        for (int k = 0; k < portDailyReturns.size(); k++) {
            double r = portDailyReturns.get(k);
            LocalDate date = sortedAlignedDates.get(k + 1);

            if (Double.isNaN(r) || Double.isInfinite(r)) {
                wealthPathInvalid = true;
                invalidReasonCode = StressWithholdingReasonCode.NUMERICAL_INVALIDITY_NON_FINITE_RETURN;
                invalidReasonExplanation = "Non-finite daily return encountered at step " + date + " (" + r + ").";
                break;
            }

            if (r < -1.0) {
                wealthPathInvalid = true;
                invalidReasonCode = StressWithholdingReasonCode.NUMERICAL_INVALIDITY_RETURN_BELOW_MINUS_ONE;
                invalidReasonExplanation = "Daily portfolio proxy return below -100% (" + String.format(Locale.US, "%.2f%%", r * 100.0) + ") encountered at step " + date + ".";
                break;
            }

            if (r == -1.0) {
                portVal = 0.0;
                wealthPathInvalid = true;
                invalidReasonCode = StressWithholdingReasonCode.NUMERICAL_INVALIDITY_TERMINAL_TOTAL_LOSS;
                invalidReasonExplanation = "Terminal total loss (100% capital loss) reached at step " + date + "; logarithmic linking mathematically undefined.";
                break;
            }

            portVal *= (1.0 + r);

            if (portVal < 0.0) {
                wealthPathInvalid = true;
                invalidReasonCode = StressWithholdingReasonCode.NUMERICAL_INVALIDITY_NEGATIVE_WEALTH;
                invalidReasonExplanation = "Portfolio proxy wealth dropped below zero (" + portVal + ") at step " + date + ". Negative wealth is invalid for long-only portfolios.";
                break;
            }

            if (portVal == 0.0) {
                wealthPathInvalid = true;
                invalidReasonCode = StressWithholdingReasonCode.NUMERICAL_INVALIDITY_TERMINAL_TOTAL_LOSS;
                invalidReasonExplanation = "Portfolio proxy wealth reached zero at step " + date + "; logarithmic linking mathematically undefined.";
                break;
            }

            if (Double.isNaN(portVal) || Double.isInfinite(portVal)) {
                wealthPathInvalid = true;
                invalidReasonCode = StressWithholdingReasonCode.NUMERICAL_INVALIDITY_NON_FINITE_RETURN;
                invalidReasonExplanation = "Cumulative wealth value became non-finite at step " + date + ".";
                break;
            }

            if (portVal > portPeakVal) {
                portPeakVal = portVal;
                portPeakDate = date;
            }

            double dd = (portVal - portPeakVal) / portPeakVal;
            if (dd < portMaxDD) {
                portMaxDD = dd;
                portTroughDate = date;
                portTroughStep = k + 1;
            }

            if (portMaxDD < -0.0001 && !portRecovered && portVal >= 1.0) {
                portRecoveryDate = date;
                portRecovered = true;
                portRecoveryStep = k + 1;
            }

            if (r < worstDaily) {
                worstDaily = r;
                worstDailyDate = date;
            }
            if (r > bestDaily) {
                bestDaily = r;
                bestDailyDate = date;
            }
        }

        if (bestDaily == -Double.MAX_VALUE) {
            bestDaily = 0.0;
        }

        // Benchmark cumulative return over aligned window
        double bmVal = 1.0;
        for (double r : bmDailyReturns) {
            bmVal *= (1.0 + r);
        }

        double rPortCum = portVal - 1.0;
        double rBmCum = bmVal - 1.0;

        // If wealth path is invalid or total loss occurred, return NUMERICALLY_INVALID response (Task 25.1, 35, 53, 60)
        if (wealthPathInvalid || rPortCum <= -1.0) {
            if (invalidReasonCode == null) {
                invalidReasonCode = (rPortCum <= -1.0)
                    ? StressWithholdingReasonCode.NUMERICAL_INVALIDITY_TERMINAL_TOTAL_LOSS
                    : StressWithholdingReasonCode.NUMERICAL_INVALIDITY_NEGATIVE_WEALTH;
                invalidReasonExplanation = "Cumulative portfolio proxy return (" + String.format(Locale.US, "%.2f%%", rPortCum * 100.0) + ") reached or dropped below -100% boundary.";
            }
            return buildNumericallyInvalidEpisodeResponse(
                ep, startDate, endDate, alignedCount, totalBmDays,
                invalidReasonExplanation != null ? invalidReasonExplanation : "Numerical domain invalidity occurred during portfolio wealth path evaluation.",
                fundIds, valuedSchemes, weightPcts, benchmarkCode, isFallbackBenchmark, benchmarkComparabilityNotice,
                knownValueCoveragePct, asOfDate, effectiveCutoff,
                commonSubsetStart, commonSubsetEnd, excludedBmDaysCount, excludedDateReasons,
                calculationRunId, portfolioAllocationSnapshotId
            );
        }

        BigDecimal portCumReturnPct = BigDecimal.valueOf(rPortCum * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bmCumReturnPct = BigDecimal.valueOf(rBmCum * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal activeReturnPct = portCumReturnPct.subtract(bmCumReturnPct).setScale(2, RoundingMode.HALF_UP);
        BigDecimal portMaxDDPct = BigDecimal.valueOf(portMaxDD * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal peakToTroughLossPct = portMaxDDPct; // Initial pre-decline peak base level V_{p,0} = 1.0

        int tradingDaysToTrough = portTroughStep;
        Integer tradingDaysToRecovery = portRecovered ? (portRecoveryStep - portTroughStep) : null;
        Integer unrecoveredDurationDays = !portRecovered ? (sortedAlignedDates.size() - portTroughStep) : null;
        int calendarDaysToTrough = (int) Math.max(0, ChronoUnit.DAYS.between(portPeakDate, portTroughDate));
        Integer calendarDaysToRecovery = portRecovered && portRecoveryDate != null ? (int) Math.max(0, ChronoUnit.DAYS.between(portTroughDate, portRecoveryDate)) : null;
        Integer unrecoveredTradingDaysDuration = !portRecovered ? (sortedAlignedDates.size() - portTroughStep) : null;
        Integer unrecoveredCalendarDaysDuration = !portRecovered ? (int) Math.max(0, ChronoUnit.DAYS.between(portTroughDate, commonSubsetEnd)) : null;

        // Portfolio Annualized Volatility during the episode
        BigDecimal portAnnVolPct = calculateAnnualizedVolPct(portDailyReturns);

        BigDecimal worstDailyPct = BigDecimal.valueOf(worstDaily * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bestDailyPct = BigDecimal.valueOf(bestDaily * 100.0).setScale(2, RoundingMode.HALF_UP);

        // Recovery states & comparison (Tasks 36 & 39)
        String benchmarkRecoveryState = ep.hasRecovered() ? "BENCHMARK_RECOVERED" : "BENCHMARK_UNRECOVERED";
        String portfolioRecoveryState = portRecovered ? "PORTFOLIO_RECOVERED" : "PORTFOLIO_UNRECOVERED";
        String recoveryState = portRecovered ? "RECOVERED" : "ONGOING_UNRECOVERED";
        String recoveryComparison;
        if (ep.hasRecovered() && portRecovered) {
            recoveryComparison = "BOTH_RECOVERED";
        } else if (ep.hasRecovered()) {
            recoveryComparison = "BENCHMARK_RECOVERED_PORTFOLIO_LAGGING";
        } else if (portRecovered) {
            recoveryComparison = "PORTFOLIO_RECOVERED_BENCHMARK_LAGGING";
        } else {
            recoveryComparison = "BOTH_UNRECOVERED";
        }

        // 7. Multi-Period Return Attribution via Stable Carino Logarithmic Linking (Tasks 25.2, 40, 42, 46)
        int T = portDailyReturns.size();
        double[] kFactors = new double[T];
        boolean anyFactorInvalid = false;

        for (int t = 0; t < T; t++) {
            double rPt = portDailyReturns.get(t);
            double kf = calculateStableCarinoFactor(rPt);
            if (Double.isNaN(kf) || Double.isInfinite(kf)) {
                anyFactorInvalid = true;
                break;
            }
            kFactors[t] = kf;
        }

        double kFactorCumulative = calculateStableCarinoFactor(rPortCum);
        if (Double.isNaN(kFactorCumulative) || Double.isInfinite(kFactorCumulative) || anyFactorInvalid) {
            return buildNumericallyInvalidEpisodeResponse(
                ep, startDate, endDate, alignedCount, totalBmDays,
                "Logarithmic linking factor evaluation failed due to non-finite values or non-positive cumulative returns.",
                fundIds, valuedSchemes, weightPcts, benchmarkCode, isFallbackBenchmark, benchmarkComparabilityNotice,
                knownValueCoveragePct, asOfDate, effectiveCutoff,
                commonSubsetStart, commonSubsetEnd, excludedBmDaysCount, excludedDateReasons,
                calculationRunId, portfolioAllocationSnapshotId
            );
        }

        // Pre-pass over constituents to determine minimum linked contribution (unambiguous PRIMARY_DETRACTOR)
        // and standalone return extrema (Task 46)
        double minLinkedContrib = Double.MAX_VALUE;
        Long worstLinkedFundId = null;
        double minStandaloneReturn = Double.MAX_VALUE;
        Long worstStandaloneFundId = null;
        double maxStandaloneReturn = -Double.MAX_VALUE;
        Long bestStandaloneFundId = null;

        Map<Long, Double> constituentCumReturns = new HashMap<>();
        Map<Long, Double> constituentExactContribs = new HashMap<>();
        Map<Long, Double> constituentApproxWeights = new HashMap<>();
        Map<Long, Double> constituentMaxDDs = new HashMap<>();
        Map<Long, LocalDate> constituentPeakDates = new HashMap<>();
        Map<Long, LocalDate> constituentTroughDates = new HashMap<>();

        for (int i = 0; i < fundIds.size(); i++) {
            Long id = fundIds.get(i);
            double w = weights[i];
            List<Double> returns = fundDailyReturns.get(id);
            double fundVal = 1.0;
            double fundPeak = 1.0;
            double fundMaxDD = 0.0;
            LocalDate fPeakDate = sortedAlignedDates.get(0);
            LocalDate fTroughDate = sortedAlignedDates.get(0);
            double carinoNumeratorSum = 0.0;

            for (int k = 0; k < returns.size(); k++) {
                double r = returns.get(k);
                LocalDate d = sortedAlignedDates.get(k + 1);
                fundVal *= (1.0 + r);
                if (fundVal > fundPeak) {
                    fundPeak = fundVal;
                    fPeakDate = d;
                }
                double dd = (fundVal - fundPeak) / fundPeak;
                if (dd < fundMaxDD) {
                    fundMaxDD = dd;
                    fTroughDate = d;
                }
                carinoNumeratorSum += kFactors[k] * (w * r);
            }

            double fCumReturn = fundVal - 1.0;
            double approxWeightedReturn = w * fCumReturn;
            double exactAdditiveContrib = carinoNumeratorSum / kFactorCumulative;

            constituentCumReturns.put(id, fCumReturn);
            constituentExactContribs.put(id, exactAdditiveContrib);
            constituentApproxWeights.put(id, approxWeightedReturn);
            constituentMaxDDs.put(id, fundMaxDD);
            constituentPeakDates.put(id, fPeakDate);
            constituentTroughDates.put(id, fTroughDate);

            if (exactAdditiveContrib < minLinkedContrib) {
                minLinkedContrib = exactAdditiveContrib;
                worstLinkedFundId = id;
            }
            if (fCumReturn < minStandaloneReturn) {
                minStandaloneReturn = fCumReturn;
                worstStandaloneFundId = id;
            }
            if (fCumReturn > maxStandaloneReturn) {
                maxStandaloneReturn = fCumReturn;
                bestStandaloneFundId = id;
            }
        }

        // Pre-compute unrounded Carino additive sum and verify exact reconciliation (Tasks 25.3, 42, 70)
        double sumUnroundedA = 0.0;
        for (int i = 0; i < fundIds.size(); i++) {
            sumUnroundedA += constituentExactContribs.get(fundIds.get(i));
        }

        double unroundedDiscrepancyPct = Math.abs(sumUnroundedA - rPortCum) * 100.0;
        BigDecimal discrepancyPct = BigDecimal.valueOf(unroundedDiscrepancyPct).setScale(6, RoundingMode.HALF_UP);
        boolean isReconciled = unroundedDiscrepancyPct <= ATTRIBUTION_RECONCILIATION_TOLERANCE_PCT;
        String reconciliationStatus = isReconciled
            ? "EXACTLY_RECONCILED" : "RECONCILIATION_RESIDUAL_EXCEEDED";

        List<FundStressContributionDto> fundContributions = new ArrayList<>();
        double sumExactContribPct = 0.0;
        double sumApproxWeightedPct = 0.0;

        for (int i = 0; i < fundIds.size(); i++) {
            Long id = fundIds.get(i);
            SchemeOption opt = valuedSchemes.get(id);
            String fundName = getFundDisplayName(opt);
            String amfiCode = opt != null && opt.getAmfiCode() != null ? opt.getAmfiCode() : "";
            String category = (opt != null && opt.getPlan() != null && opt.getPlan().getScheme() != null && opt.getPlan().getScheme().getCategory() != null)
                ? opt.getPlan().getScheme().getCategory() : "Unclassified";

            BigDecimal weightPct = weightPcts.get(id);
            double fundCumReturn = constituentCumReturns.get(id);
            double approxWeightedReturn = constituentApproxWeights.get(id);
            double exactAdditiveContrib = constituentExactContribs.get(id);
            double fundMaxDD = constituentMaxDDs.get(id);
            LocalDate fPeakDate = constituentPeakDates.get(id);
            LocalDate fTroughDate = constituentTroughDates.get(id);
            double diff = approxWeightedReturn - exactAdditiveContrib;

            BigDecimal fundCumReturnPct = BigDecimal.valueOf(fundCumReturn * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal approxWeightedPct = BigDecimal.valueOf(approxWeightedReturn * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal exactContribPct = BigDecimal.valueOf(exactAdditiveContrib * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal diffPct = BigDecimal.valueOf(diff * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fundMaxDDPct = BigDecimal.valueOf(fundMaxDD * 100.0).setScale(2, RoundingMode.HALF_UP);

            sumExactContribPct += exactContribPct.doubleValue();
            sumApproxWeightedPct += approxWeightedPct.doubleValue();

            // Task 46 & 70: Two-Dimensional Role Classification with Unambiguous Precedence & Reconciliation Contract
            double exactVal = exactContribPct.doubleValue();
            String linkedRole;
            String roleSign;
            boolean outperformedProxy = fundCumReturn > rPortCum;
            String roleInterpretation;

            if (exactVal > 0.0001) {
                linkedRole = "POSITIVE_CONTRIBUTOR";
                roleSign = "POSITIVE";
                roleInterpretation = "Generated positive return contribution to the portfolio proxy during this stress period.";
            } else if (Math.abs(exactVal) <= 0.0001) {
                linkedRole = "NEUTRAL_CONTRIBUTOR";
                roleSign = "ZERO";
                roleInterpretation = "Negligible return contribution near zero during this stress episode.";
            } else {
                roleSign = "NEGATIVE";
                // Unambiguous PRIMARY_DETRACTOR definition (Task 46.8): Largest negative linked contribution
                if (id.equals(worstLinkedFundId) && exactVal < -0.0001) {
                    linkedRole = "PRIMARY_DETRACTOR";
                    roleInterpretation = String.format(Locale.US,
                        "Primary loss contributor with the largest negative linked additive contribution (%+.2f%% pts) to the portfolio proxy during this episode.", exactVal);
                } else if (fundCumReturn > rPortCum && exactVal >= -1.0) {
                    linkedRole = "RESILIENT_LOWER_LOSS_CONTRIBUTOR";
                    roleInterpretation = "Exhibited lower cumulative loss than the portfolio proxy and loss drag < 1.0% pts. Mathematical lower loss drag in this specific sample; does NOT establish positive return, downside protection, or superior manager skill.";
                } else {
                    linkedRole = "MODERATE_DETRACTOR";
                    roleInterpretation = String.format(Locale.US, "Moderate loss contribution with linked attribution drag of %+.2f%% pts.", exactVal);
                }
            }

            // Task 70: When Carino discrepancy exceeds 0.0001% tolerance, linked attribution role is withheld
            String finalLinkedRole;
            BigDecimal finalExactContribPct;
            String attrReconciliationStatus;
            String withCode;
            String withExplanation;

            if (isReconciled) {
                finalLinkedRole = linkedRole;
                finalExactContribPct = exactContribPct;
                attrReconciliationStatus = "RECONCILED_WITHIN_TOLERANCE";
                withCode = StressWithholdingReasonCode.ELIGIBLE_CALCULATED.name();
                withExplanation = "Calculated via Carino multi-period logarithmic linking.";
            } else {
                finalLinkedRole = "RECONCILIATION_DISCREPANCY_WITHHELD";
                finalExactContribPct = null; // Withhold un-reconciled additive contribution (Task 70)
                attrReconciliationStatus = "RECONCILIATION_RESIDUAL_EXCEEDED";
                withCode = StressWithholdingReasonCode.RECONCILIATION_DISCREPANCY.name();
                withExplanation = String.format(Locale.US,
                    "Carino attribution discrepancy (%+.6f%%) exceeded tolerance (0.0001%% pts); additive contributions withheld.",
                    unroundedDiscrepancyPct);
            }

            // Standalone performance role evaluated strictly from R_{i,cum} (Task 46.6)
            String standaloneRole = FundStressContributionDto.determineStandaloneRole(
                fundCumReturnPct,
                portCumReturnPct,
                bmCumReturnPct,
                id.equals(worstStandaloneFundId) && fundIds.size() > 1,
                id.equals(bestStandaloneFundId) && fundIds.size() > 1
            );

            String role = finalLinkedRole; // Maintain canonical backward-compatible alias

            String fundNotes = String.format(Locale.US,
                "Carino exact additive contribution A_i = %+.2f%% pts from %.2f%% weight (approx indicator: %+.2f%% pts, difference: %+.2f%% pts). Linked role: %s; Standalone role: %s. %s",
                exactContribPct.doubleValue(), weightPct != null ? weightPct.doubleValue() : 0.0,
                approxWeightedPct.doubleValue(), diffPct.doubleValue(), finalLinkedRole, standaloneRole, roleInterpretation
            );

            int availableObs = fundObservationCounts.getOrDefault(id, alignedCount);
            LocalDate earliestNav = fundEarliestNavDates.get(id);
            boolean hasPreInception = earliestNav != null && earliestNav.isAfter(startDate);
            BigDecimal constituentCoveragePct = totalBmDays > 0
                ? BigDecimal.valueOf((availableObs * 100.0) / totalBmDays).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            String metricEvidenceState = alignedCount >= MIN_EPISODE_OBS_SUFFICIENT
                ? "DESCRIPTIVE_EVENT_SAMPLE" : "LIMITED_EVENT_SAMPLE";

            int preIncep = fundPreInceptionCounts.getOrDefault(id, 0);
            int missNav = fundMissingNavCounts.getOrDefault(id, 0);
            int invNav = fundInvalidNavCounts.getOrDefault(id, 0);
            int exclObs = preIncep + missNav + invNav;
            int calendarDaysInWindow = (int) Math.max(0, ChronoUnit.DAYS.between(startDate, endDate));

            fundContributions.add(new FundStressContributionDto(
                id, fundName, amfiCode, category, weightPct,
                fundCumReturnPct, finalExactContribPct, approxWeightedPct, finalExactContribPct,
                diffPct, diffPct, fundMaxDDPct, fPeakDate, fTroughDate, alignedCount,
                totalBmDays, availableObs, constituentCoveragePct,
                hasPreInception, earliestNav, evidenceState,
                metricEvidenceState, role, fundNotes,
                roleSign, outperformedProxy, roleInterpretation,
                availableObs, exclObs, preIncep, missNav, invNav,
                standaloneRole, finalLinkedRole, "VALID", constituentWindowCoverage,
                alignedCount, calendarDaysInWindow,
                "VALID", attrReconciliationStatus, withCode, withExplanation
            ));
        }

        double compoundingResidual = sumApproxWeightedPct - portCumReturnPct.doubleValue();
        BigDecimal compoundingResidualPct = BigDecimal.valueOf(compoundingResidual).setScale(2, RoundingMode.HALF_UP);

        // Comparability State (Task 37, 41, 45)
        String comparabilityState;
        List<String> comparabilityLimitations = new ArrayList<>();
        if (isFallbackBenchmark) {
            comparabilityState = "CONDITIONALLY_COMPARABLE_FALLBACK_BENCHMARK";
            comparabilityLimitations.add(String.format(Locale.US,
                "Identified using fallback benchmark %s instead of preferred broad-market %s.", benchmarkCode, DEFAULT_BENCHMARK_CODE));
        } else if ("SUFFICIENT_EVIDENCE".equals(evidenceState)) {
            comparabilityState = "DIRECTLY_COMPARABLE";
        } else if ("LIMITED_EVIDENCE".equals(evidenceState)) {
            comparabilityState = "CONDITIONALLY_COMPARABLE_PARTIAL_HISTORY";
            if (limitationReason != null) comparabilityLimitations.add(limitationReason);
        } else {
            comparabilityState = "NOT_DIRECTLY_COMPARABLE_INSUFFICIENT_EVIDENCE";
            if (limitationReason != null) comparabilityLimitations.add(limitationReason);
        }
        if (!intervalNotices.isEmpty()) {
            comparabilityLimitations.addAll(intervalNotices);
        }

        // Separated Evidence Dimensions (Tasks 31, 35, 45)
        String calculationAvailability = "CALCULATED";
        String dataCompleteness = (alignedCount == totalBmDays) ? "COMPLETE_WINDOW" : "PARTIAL_BENCHMARK_SYNCHRONIZED_WINDOW";
        String statisticalEvidenceAdequacy = (alignedCount >= MIN_EPISODE_OBS_SUFFICIENT)
            ? "DESCRIPTIVE_EVENT_SAMPLE" : "LIMITED_EVENT_SAMPLE";
        String volatilityEvidenceState = (alignedCount >= MIN_EPISODE_OBS_SUFFICIENT)
            ? "DESCRIPTIVE_ONLY_HIGH_VARIANCE_SAMPLE" : "INSUFFICIENT_OBSERVATIONS_FOR_VOLATILITY";
        String tailEvidenceState = (alignedCount >= MIN_EPISODE_OBS_LIMITED)
            ? "DESCRIPTIVE_EXTREME_OBSERVATION" : "NOT_APPLICABLE";

        int bmTradingDaysToTrough = ep.tradingDaysToTrough();
        Integer bmTradingDaysToRecovery = ep.tradingDaysToRecovery();
        int bmCalendarDaysToTrough = ep.calendarDaysToTrough();
        Integer bmCalendarDaysToRecovery = ep.calendarDaysToRecovery();
        String calculationEligibilityOutcome = (alignedCount == totalBmDays)
            ? "ELIGIBLE_COMPLETE_WINDOW" : "ELIGIBLE_COMMON_DATE_SUBSET";

        return new PortfolioStressEpisodeDto(
            ep.episodeId(),
            ep.legacyEpisodeId(),
            ep.episodeName(),
            ep.regimeCategory(),
            startDate,
            endDate,
            ep.peakDate(),
            ep.firstDeclineDate(),
            ep.troughDate(),
            ep.recoveryDate(),
            ep.hasRecovered(),
            benchmarkRecoveryState,
            ep.maxDrawdownPct(),
            bmCumReturnPct,
            portCumReturnPct,
            activeReturnPct,
            portMaxDDPct,
            portPeakDate,
            portTroughDate,
            portRecoveryDate,
            portRecovered,
            portfolioRecoveryState,
            recoveryComparison,
            portAnnVolPct,
            worstDailyPct,
            worstDailyDate,
            bestDailyPct,
            bestDailyDate,
            alignedCount,
            totalBmDays,
            totalBmDays,
            portfolioCoverageRatioPct,
            knownValueCoveragePct,
            missingConstituentCount,
            partialConstituentCount,
            includedConstituents,
            excludedOrUnavailableConstituents,
            missingAllocationWeightPct,
            partialPortfolioNotice,
            evidenceState,
            recoveryState,
            fundContributions,
            "Static Current-Allocation Historical Stress Proxy (R_{p,t} = \\sum w_i R_{i,t})",
            CONSTRUCTION_CONVENTION,
            ATTRIBUTION_METHODOLOGY,
            reconciliationStatus,
            discrepancyPct,
            compoundingResidualPct,
            benchmarkCode,
            isFallbackBenchmark,
            benchmarkComparabilityNotice,
            comparabilityState,
            comparabilityLimitations,
            asOfDate,
            limitationReason,
            calculationAvailability,
            dataCompleteness,
            statisticalEvidenceAdequacy,
            volatilityEvidenceState,
            tailEvidenceState,
            effectiveCutoff,
            EVIDENCE_POLICY_VERSION,
            alignedCount,
            excludedBmDaysCount,
            excludedDateReasons,
            commonSubsetStart,
            commonSubsetEnd,
            totalPreInceptionExclusions,
            totalMissingNavExclusions,
            totalInvalidNavExclusions,
            peakToTroughLossPct,
            tradingDaysToTrough,
            tradingDaysToRecovery,
            unrecoveredDurationDays,
            bmTradingDaysToTrough,
            bmTradingDaysToRecovery,
            dataQualitySignals,
            (int) dataQualitySignals.stream().filter(s -> "WARNING".equals(s.severity())).count(),
            IDENTIFICATION_POLICY_VERSION,
            METHODOLOGY_VERSION,
            calculationRunId,
            ep.lifecycleState(),
            ep.lifecycleTransitionContext(),
            calculationEligibilityOutcome,
            benchmarkObservationAvailability,
            constituentObservationAvailability,
            commonDateSynchronization,
            constituentUniverseCompleteness,
            "VALID",
            constituentWindowCoverage,
            commonSubsetStart,
            commonSubsetEnd,
            calendarDaysToTrough,
            calendarDaysToRecovery,
            unrecoveredTradingDaysDuration,
            unrecoveredCalendarDaysDuration,
            bmCalendarDaysToTrough,
            bmCalendarDaysToRecovery,
            portfolioAllocationSnapshotId,
            computeCalculationInputFingerprint(fundIds, weights, portfolioAllocationSnapshotId, benchmarkCode, asOfDate, effectiveCutoff, METHODOLOGY_VERSION),
            "VALID",
            isReconciled ? StressWithholdingReasonCode.ELIGIBLE_CALCULATED.name() : StressWithholdingReasonCode.RECONCILIATION_DISCREPANCY.name(),
            isReconciled ? "Calculated over synchronized observation window." : "Portfolio proxy calculated, but constituent attribution discrepancy exceeded tolerance."
        );
    }

    /**
     * Builds episode response when one or more constituents lack observations (Tasks 27.2, 33, 35, 45, 47, 48, 52, 53, 54).
     * Refuses to fabricate synthetic returns or silently redistribute capital weights.
     * Preserves standalone constituent calculations where aggregate portfolio calculations are withheld (Task 45.13).
     */
    private PortfolioStressEpisodeDto buildMissingConstituentEpisodeResponse(
        IdentifiedBenchmarkEpisode ep,
        LocalDate startDate,
        LocalDate endDate,
        int alignedCount,
        int totalBmDays,
        String evidenceState,
        String limitationReason,
        String partialPortfolioNotice,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory,
        List<LocalDate> bmDatesInWindow,
        Map<Long, Integer> fundObservationCounts,
        Map<Long, LocalDate> fundEarliestNavDates,
        Map<Long, Integer> fundPreInceptionCounts,
        Map<Long, Integer> fundMissingNavCounts,
        Map<Long, Integer> fundInvalidNavCounts,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String benchmarkComparabilityNotice,
        BigDecimal knownValueCoveragePct,
        int missingConstituentCount,
        int partialConstituentCount,
        List<String> includedConstituents,
        List<String> excludedOrUnavailableConstituents,
        BigDecimal missingAllocationWeightPct,
        BigDecimal portfolioCoverageRatioPct,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        LocalDate commonSubsetStart,
        LocalDate commonSubsetEnd,
        int excludedBmDaysCount,
        List<String> excludedDateReasons,
        int totalPreInceptionExclusions,
        int totalMissingNavExclusions,
        int totalInvalidNavExclusions,
        String calculationRunId,
        String portfolioAllocationSnapshotId
    ) {
        List<FundStressContributionDto> fundContributions = new ArrayList<>();
        int calendarDaysInWindow = (int) Math.max(0, ChronoUnit.DAYS.between(startDate, endDate));

        for (int i = 0; i < fundIds.size(); i++) {
            Long id = fundIds.get(i);
            SchemeOption opt = valuedSchemes.get(id);
            String fundName = getFundDisplayName(opt);
            String amfiCode = opt != null && opt.getAmfiCode() != null ? opt.getAmfiCode() : "";
            String category = (opt != null && opt.getPlan() != null && opt.getPlan().getScheme() != null && opt.getPlan().getScheme().getCategory() != null)
                ? opt.getPlan().getScheme().getCategory() : "Unclassified";
            BigDecimal weightPct = weightPcts.get(id);

            int obs = fundObservationCounts.getOrDefault(id, 0);
            int preIncep = fundPreInceptionCounts.getOrDefault(id, 0);
            int missNav = fundMissingNavCounts.getOrDefault(id, 0);
            int invNav = fundInvalidNavCounts.getOrDefault(id, 0);
            LocalDate earliestNav = fundEarliestNavDates.get(id);
            boolean hasPreInception = earliestNav != null && earliestNav.isAfter(startDate);
            BigDecimal constituentCoveragePct = totalBmDays > 0
                ? BigDecimal.valueOf((obs * 100.0) / totalBmDays).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            String fundEvidence = obs > 0 ? "LIMITED_EVIDENCE" : "INSUFFICIENT_EVIDENCE";
            String metricEvidence = obs > 0 ? "LIMITED_EVENT_SAMPLE" : "INSUFFICIENT_CONSTITUENT_HISTORY";

            // Task 45.13: Calculate standalone constituent returns and drawdown for available funds
            BigDecimal standaloneCumReturnPct = null;
            BigDecimal standaloneMaxDDPct = null;
            LocalDate fPeakDate = null;
            LocalDate fTroughDate = null;
            String standaloneRole;
            String linkedRole = "ATTRIBUTION_WITHHELD";
            String fundRole = "ATTRIBUTION_WITHHELD";

            Map<LocalDate, BigDecimal> navMap = fundNavHistory.get(id);
            if (obs >= 2 && navMap != null) {
                List<LocalDate> validFundDates = new ArrayList<>();
                for (LocalDate d : bmDatesInWindow) {
                    BigDecimal n = navMap.get(d);
                    if (n != null && n.compareTo(BigDecimal.ZERO) > 0) {
                        validFundDates.add(d);
                    }
                }
                if (validFundDates.size() >= 2) {
                    BigDecimal firstNav = navMap.get(validFundDates.get(0));
                    BigDecimal lastNav = navMap.get(validFundDates.get(validFundDates.size() - 1));
                    double rCum = lastNav.subtract(firstNav, MC).divide(firstNav, MC).doubleValue();
                    standaloneCumReturnPct = BigDecimal.valueOf(rCum * 100.0).setScale(2, RoundingMode.HALF_UP);

                    BigDecimal peak = firstNav;
                    fPeakDate = validFundDates.get(0);
                    fTroughDate = validFundDates.get(0);
                    double maxDD = 0.0;
                    for (LocalDate d : validFundDates) {
                        BigDecimal n = navMap.get(d);
                        if (n.compareTo(peak) > 0) {
                            peak = n;
                            fPeakDate = d;
                        }
                        double dd = n.subtract(peak, MC).divide(peak, MC).doubleValue();
                        if (dd < maxDD) {
                            maxDD = dd;
                            fTroughDate = d;
                        }
                    }
                    standaloneMaxDDPct = BigDecimal.valueOf(maxDD * 100.0).setScale(2, RoundingMode.HALF_UP);
                    standaloneRole = FundStressContributionDto.determineStandaloneRole(standaloneCumReturnPct, null, ep.maxDrawdownPct(), false, false);
                } else {
                    standaloneRole = "INSUFFICIENT_CONSTITUENT_HISTORY";
                }
            } else {
                standaloneRole = "INSUFFICIENT_CONSTITUENT_HISTORY";
            }

            String roleSign = standaloneCumReturnPct != null
                ? (standaloneCumReturnPct.compareTo(BigDecimal.ZERO) > 0 ? "POSITIVE" : "NEGATIVE")
                : "UNDEFINED";
            String roleInterpretation = obs > 0
                ? "Constituent has partial observations; standalone return and drawdown are preserved, but linked portfolio attribution is withheld under §4 AGENTS.md."
                : "Fund inception post-dates stress episode. Pre-inception returns cannot be evaluated under §4 AGENTS.md.";
            String notes = obs > 0
                ? String.format(Locale.US, "Constituent has %d observations; standalone return is %s. Incomplete portfolio coverage prevents synchronous proxy evaluation.",
                    obs, standaloneCumReturnPct != null ? standaloneCumReturnPct + "%" : "N/A")
                : "Fund inception post-dates stress episode. Pre-inception returns cannot be evaluated under §4 AGENTS.md.";

            fundContributions.add(new FundStressContributionDto(
                id, fundName, amfiCode, category, weightPct,
                standaloneCumReturnPct, null, null, null, null, null,
                standaloneMaxDDPct, fPeakDate, fTroughDate,
                obs, totalBmDays, obs, constituentCoveragePct,
                hasPreInception, earliestNav, fundEvidence,
                metricEvidence, fundRole, notes,
                roleSign, false, roleInterpretation,
                obs, Math.max(0, totalBmDays - obs), preIncep, missNav, invNav,
                standaloneRole, linkedRole, "VALID", "TRUNCATED_CONSTITUENT_WINDOW",
                obs, calendarDaysInWindow
            ));
        }

        List<String> limitations = List.of(limitationReason);
        String dqSigId = StressDataQualitySignalDto.generateSignalId("CONSTITUENT_HISTORY_TRUNCATED", null, startDate, endDate);
        List<StressDataQualitySignalDto> dqSignals = List.of(new StressDataQualitySignalDto(
            dqSigId, "CONSTITUENT_HISTORY_TRUNCATED", "WARNING", null, null,
            startDate, endDate, excludedBmDaysCount,
            "One or more constituent funds lack observations during this stress episode due to inception dates post-dating the episode.", true,
            "ACTIVE_WARNING", calculationRunId, OffsetDateTime.now()
        ));

        return new PortfolioStressEpisodeDto(
            ep.episodeId(),
            ep.legacyEpisodeId(),
            ep.episodeName(),
            ep.regimeCategory(),
            startDate,
            endDate,
            ep.peakDate(),
            ep.firstDeclineDate(),
            ep.troughDate(),
            ep.recoveryDate(),
            ep.hasRecovered(),
            ep.hasRecovered() ? "BENCHMARK_RECOVERED" : "BENCHMARK_UNRECOVERED",
            ep.maxDrawdownPct(),
            null, null, null, null, null, null, null,
            false,
            "RECOVERY_UNAVAILABLE",
            "RECOVERY_UNAVAILABLE",
            null, null, null, null, null,
            alignedCount,
            totalBmDays,
            totalBmDays,
            portfolioCoverageRatioPct,
            knownValueCoveragePct,
            missingConstituentCount,
            partialConstituentCount,
            includedConstituents,
            excludedOrUnavailableConstituents,
            missingAllocationWeightPct,
            partialPortfolioNotice,
            evidenceState,
            "NOT_APPLICABLE",
            fundContributions,
            "Static Current-Allocation Historical Stress Proxy (R_{p,t} = \\sum w_i R_{i,t})",
            CONSTRUCTION_CONVENTION,
            ATTRIBUTION_METHODOLOGY,
            "NOT_APPLICABLE",
            null,
            null,
            benchmarkCode,
            isFallbackBenchmark,
            benchmarkComparabilityNotice,
            "NOT_DIRECTLY_COMPARABLE_INSUFFICIENT_EVIDENCE",
            limitations,
            asOfDate,
            limitationReason,
            "WITHHELD_INSUFFICIENT_DATA",
            "TRUNCATED_CONSTITUENT_WINDOW",
            "INSUFFICIENT_SAMPLE",
            "NOT_APPLICABLE",
            "NOT_APPLICABLE",
            knowledgeCutoff,
            EVIDENCE_POLICY_VERSION,
            alignedCount,
            excludedBmDaysCount,
            excludedDateReasons,
            commonSubsetStart,
            commonSubsetEnd,
            totalPreInceptionExclusions,
            totalMissingNavExclusions,
            totalInvalidNavExclusions,
            null, 0, null, null,
            ep.tradingDaysToTrough(),
            ep.tradingDaysToRecovery(),
            dqSignals,
            1,
            IDENTIFICATION_POLICY_VERSION,
            METHODOLOGY_VERSION,
            calculationRunId,
            ep.lifecycleState(),
            ep.lifecycleTransitionContext(),
            "WITHHELD_INCOMPLETE_CONSTITUENT_HISTORY",
            totalBmDays > 0 ? "AVAILABLE" : "NO_OBSERVATIONS",
            missingConstituentCount < fundIds.size() ? "INSUFFICIENT_CONSTITUENT_HISTORY" : "UNAVAILABLE_SOURCE_DATA",
            "UNSYNCHRONIZED_INSUFFICIENT_OVERLAP",
            "INCOMPLETE_PORTFOLIO_UNIVERSE",
            "VALID",
            "TRUNCATED_CONSTITUENT_WINDOW",
            commonSubsetStart,
            commonSubsetEnd,
            ep.calendarDaysToTrough(),
            ep.calendarDaysToRecovery(),
            null, null,
            ep.calendarDaysToTrough(),
            ep.calendarDaysToRecovery(),
            portfolioAllocationSnapshotId
        );
    }

    /**
     * Builds limited response when episode has fewer than minimum required observations (Tasks 35, 39, 45, 47, 48, 52, 53, 54).
     */
    private PortfolioStressEpisodeDto buildLimitedEpisodeResponse(
        IdentifiedBenchmarkEpisode ep,
        LocalDate startDate,
        LocalDate endDate,
        int alignedCount,
        int totalBmDays,
        String evidenceState,
        String limitationReason,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        Map<Long, BigDecimal> weightPcts,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String benchmarkComparabilityNotice,
        BigDecimal knownValueCoveragePct,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        LocalDate commonSubsetStart,
        LocalDate commonSubsetEnd,
        int excludedBmDaysCount,
        List<String> excludedDateReasons,
        String calculationRunId,
        String portfolioAllocationSnapshotId
    ) {
        List<FundStressContributionDto> fundContributions = new ArrayList<>();
        List<String> included = new ArrayList<>();
        int calendarDaysInWindow = (int) Math.max(0, ChronoUnit.DAYS.between(startDate, endDate));

        for (Long id : fundIds) {
            SchemeOption opt = valuedSchemes.get(id);
            String fundName = getFundDisplayName(opt);
            included.add(fundName);
            String amfiCode = opt != null && opt.getAmfiCode() != null ? opt.getAmfiCode() : "";
            String category = (opt != null && opt.getPlan() != null && opt.getPlan().getScheme() != null && opt.getPlan().getScheme().getCategory() != null)
                ? opt.getPlan().getScheme().getCategory() : "Unclassified";
            BigDecimal weightPct = weightPcts.get(id);

            fundContributions.add(new FundStressContributionDto(
                id, fundName, amfiCode, category, weightPct,
                null, null, null, null, null, null,
                null, null, null,
                alignedCount, totalBmDays, alignedCount, BigDecimal.ZERO,
                false, null, evidenceState,
                "INSUFFICIENT_SAMPLE", "INSUFFICIENT_EVIDENCE",
                "Historical observations insufficient for stress return contribution.",
                "UNDEFINED", false, "Insufficient observations.",
                alignedCount, Math.max(0, totalBmDays - alignedCount), 0, Math.max(0, totalBmDays - alignedCount), 0,
                "INSUFFICIENT_SAMPLE", "WITHHELD_INSUFFICIENT_OBSERVATIONS", "VALID", "INCOMPLETE_INSUFFICIENT_OBSERVATIONS",
                alignedCount, calendarDaysInWindow
            ));
        }

        BigDecimal coverageRatio = totalBmDays > 0
            ? BigDecimal.valueOf((alignedCount * 100.0) / totalBmDays).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        String dqSigId = StressDataQualitySignalDto.generateSignalId("INSUFFICIENT_OBSERVATIONS", null, startDate, endDate);
        List<StressDataQualitySignalDto> dqSignals = List.of(new StressDataQualitySignalDto(
            dqSigId, "INSUFFICIENT_OBSERVATIONS", "WARNING", null, null,
            startDate, endDate, alignedCount,
            "Aligned trading observations below 5-day statistical floor.", true,
            "ACTIVE_WARNING", calculationRunId, OffsetDateTime.now()
        ));

        return new PortfolioStressEpisodeDto(
            ep.episodeId(),
            ep.legacyEpisodeId(),
            ep.episodeName(),
            ep.regimeCategory(),
            startDate,
            endDate,
            ep.peakDate(),
            ep.firstDeclineDate(),
            ep.troughDate(),
            ep.recoveryDate(),
            ep.hasRecovered(),
            ep.hasRecovered() ? "BENCHMARK_RECOVERED" : "BENCHMARK_UNRECOVERED",
            ep.maxDrawdownPct(),
            null, null, null, null, null, null, null,
            false,
            "RECOVERY_UNAVAILABLE",
            "RECOVERY_UNAVAILABLE",
            null, null, null, null, null,
            alignedCount,
            totalBmDays,
            totalBmDays,
            coverageRatio,
            knownValueCoveragePct,
            0,
            0,
            included,
            Collections.emptyList(),
            BigDecimal.ZERO,
            "Insufficient usable observations (" + alignedCount + " < 5 days). Portfolio proxy return is withheld.",
            evidenceState,
            "NOT_APPLICABLE",
            fundContributions,
            "Static Current-Allocation Historical Stress Proxy (R_{p,t} = \\sum w_i R_{i,t})",
            CONSTRUCTION_CONVENTION,
            ATTRIBUTION_METHODOLOGY,
            "NOT_APPLICABLE",
            null,
            null,
            benchmarkCode,
            isFallbackBenchmark,
            benchmarkComparabilityNotice,
            "NOT_DIRECTLY_COMPARABLE_INSUFFICIENT_EVIDENCE",
            List.of(limitationReason),
            asOfDate,
            limitationReason,
            "WITHHELD_INSUFFICIENT_DATA",
            "INCOMPLETE_INSUFFICIENT_OBSERVATIONS",
            "INSUFFICIENT_SAMPLE",
            "NOT_APPLICABLE",
            "NOT_APPLICABLE",
            knowledgeCutoff,
            EVIDENCE_POLICY_VERSION,
            alignedCount,
            excludedBmDaysCount,
            excludedDateReasons,
            commonSubsetStart,
            commonSubsetEnd,
            0, excludedBmDaysCount, 0,
            null, 0, null, null,
            ep.tradingDaysToTrough(),
            ep.tradingDaysToRecovery(),
            dqSignals,
            1,
            IDENTIFICATION_POLICY_VERSION,
            METHODOLOGY_VERSION,
            calculationRunId,
            ep.lifecycleState(),
            ep.lifecycleTransitionContext(),
            "WITHHELD_INSUFFICIENT_OBSERVATIONS",
            totalBmDays > 0 ? "AVAILABLE" : "NO_OBSERVATIONS",
            "PARTIAL_CONSTITUENTS_AVAILABLE",
            "UNSYNCHRONIZED_INSUFFICIENT_OVERLAP",
            "COMPLETE_PORTFOLIO_UNIVERSE",
            "VALID",
            "INCOMPLETE_INSUFFICIENT_OBSERVATIONS",
            commonSubsetStart,
            commonSubsetEnd,
            ep.calendarDaysToTrough(),
            ep.calendarDaysToRecovery(),
            null, null,
            ep.calendarDaysToTrough(),
            ep.calendarDaysToRecovery(),
            portfolioAllocationSnapshotId
        );
    }

    /**
     * Builds numerically invalid response when wealth path drops to <= 0 or returns are non-finite (Tasks 25.1, 35, 45, 53).
     */
    private PortfolioStressEpisodeDto buildNumericallyInvalidEpisodeResponse(
        IdentifiedBenchmarkEpisode ep,
        LocalDate startDate,
        LocalDate endDate,
        int alignedCount,
        int totalBmDays,
        String limitationReason,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        Map<Long, BigDecimal> weightPcts,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String benchmarkComparabilityNotice,
        BigDecimal knownValueCoveragePct,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        LocalDate commonSubsetStart,
        LocalDate commonSubsetEnd,
        int excludedBmDaysCount,
        List<String> excludedDateReasons,
        String calculationRunId,
        String portfolioAllocationSnapshotId
    ) {
        List<FundStressContributionDto> fundContributions = new ArrayList<>();
        List<String> included = new ArrayList<>();
        int calendarDaysInWindow = (int) Math.max(0, ChronoUnit.DAYS.between(startDate, endDate));

        for (Long id : fundIds) {
            SchemeOption opt = valuedSchemes.get(id);
            String fundName = getFundDisplayName(opt);
            included.add(fundName);
            String amfiCode = opt != null && opt.getAmfiCode() != null ? opt.getAmfiCode() : "";
            String category = (opt != null && opt.getPlan() != null && opt.getPlan().getScheme() != null && opt.getPlan().getScheme().getCategory() != null)
                ? opt.getPlan().getScheme().getCategory() : "Unclassified";
            BigDecimal weightPct = weightPcts.get(id);

            fundContributions.add(new FundStressContributionDto(
                id, fundName, amfiCode, category, weightPct,
                null, null, null, null, null, null,
                null, null, null,
                alignedCount, totalBmDays, alignedCount, BigDecimal.valueOf(100.0),
                false, null, "NUMERICALLY_INVALID",
                "NUMERICALLY_INVALID", "NUMERICALLY_INVALID",
                limitationReason,
                "UNDEFINED", false, "Numerical domain invalidity occurred.",
                alignedCount, 0, 0, 0, 0,
                "NUMERICALLY_INVALID", "WITHHELD_NUMERICALLY_INVALID", "NUMERICALLY_INVALID", "COMMON_DATE_SUBSET",
                alignedCount, calendarDaysInWindow
            ));
        }

        BigDecimal coverageRatio = totalBmDays > 0
            ? BigDecimal.valueOf((alignedCount * 100.0) / totalBmDays).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        String dqSigId = StressDataQualitySignalDto.generateSignalId("NUMERICAL_DOMAIN_INVALIDITY", null, startDate, endDate);
        List<StressDataQualitySignalDto> dqSignals = List.of(new StressDataQualitySignalDto(
            dqSigId, "NUMERICAL_DOMAIN_INVALIDITY", "WARNING", null, null,
            startDate, endDate, alignedCount,
            "Numerical domain invalidity occurred during portfolio wealth path evaluation.", true,
            "ACTIVE_WARNING", calculationRunId, OffsetDateTime.now()
        ));

        return new PortfolioStressEpisodeDto(
            ep.episodeId(),
            ep.legacyEpisodeId(),
            ep.episodeName(),
            ep.regimeCategory(),
            startDate,
            endDate,
            ep.peakDate(),
            ep.firstDeclineDate(),
            ep.troughDate(),
            ep.recoveryDate(),
            ep.hasRecovered(),
            ep.hasRecovered() ? "BENCHMARK_RECOVERED" : "BENCHMARK_UNRECOVERED",
            ep.maxDrawdownPct(),
            null, null, null, null, null, null, null,
            false,
            "RECOVERY_UNAVAILABLE",
            "RECOVERY_UNAVAILABLE",
            null, null, null, null, null,
            alignedCount,
            totalBmDays,
            totalBmDays,
            coverageRatio,
            knownValueCoveragePct,
            0,
            0,
            included,
            Collections.emptyList(),
            BigDecimal.ZERO,
            "Numerical domain invalidity occurred during portfolio wealth path evaluation.",
            "NUMERICALLY_INVALID",
            "NOT_APPLICABLE",
            fundContributions,
            "Static Current-Allocation Historical Stress Proxy (R_{p,t} = \\sum w_i R_{i,t})",
            CONSTRUCTION_CONVENTION,
            ATTRIBUTION_METHODOLOGY,
            "NUMERICALLY_INVALID",
            null,
            null,
            benchmarkCode,
            isFallbackBenchmark,
            benchmarkComparabilityNotice,
            "NOT_DIRECTLY_COMPARABLE_INSUFFICIENT_EVIDENCE",
            List.of(limitationReason),
            asOfDate,
            limitationReason,
            "WITHHELD_NUMERICALLY_INVALID",
            alignedCount == totalBmDays ? "COMPLETE_WINDOW" : "PARTIAL_BENCHMARK_SYNCHRONIZED_WINDOW",
            "NUMERICALLY_INVALID",
            "NOT_APPLICABLE",
            "NOT_APPLICABLE",
            knowledgeCutoff,
            EVIDENCE_POLICY_VERSION,
            alignedCount,
            excludedBmDaysCount,
            excludedDateReasons,
            commonSubsetStart,
            commonSubsetEnd,
            0, 0, 0,
            null, 0, null, null,
            ep.tradingDaysToTrough(),
            ep.tradingDaysToRecovery(),
            dqSignals,
            1,
            IDENTIFICATION_POLICY_VERSION,
            METHODOLOGY_VERSION,
            calculationRunId,
            ep.lifecycleState(),
            ep.lifecycleTransitionContext(),
            "WITHHELD_NUMERICALLY_INVALID",
            totalBmDays > 0 ? "AVAILABLE" : "NO_OBSERVATIONS",
            "ALL_CONSTITUENTS_AVAILABLE",
            "SYNCHRONIZED_COMMON_SUBSET",
            "COMPLETE_PORTFOLIO_UNIVERSE",
            "NUMERICALLY_INVALID",
            "COMMON_DATE_SUBSET",
            commonSubsetStart,
            commonSubsetEnd,
            ep.calendarDaysToTrough(),
            ep.calendarDaysToRecovery(),
            null, null,
            ep.calendarDaysToTrough(),
            ep.calendarDaysToRecovery(),
            portfolioAllocationSnapshotId
        );
    }

    /**
     * Annualized volatility calculation: std(returns) * sqrt(252).
     */
    private BigDecimal calculateAnnualizedVolPct(List<Double> returns) {
        int n = returns.size();
        if (n < 2) {
            return BigDecimal.ZERO;
        }

        double sum = 0.0;
        for (double r : returns) {
            sum += r;
        }
        double mean = sum / n;

        double sumSqDiff = 0.0;
        for (double r : returns) {
            double diff = r - mean;
            sumSqDiff += diff * diff;
        }

        double variance = sumSqDiff / (n - 1);
        double stdDaily = Math.sqrt(variance);
        double annVolPct = stdDaily * SQRT_ANNUALIZATION * 100.0;

        return BigDecimal.valueOf(annVolPct).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Generates deterministic stress investigation findings and persists them to the durable evidence repository (Tasks 34.3, 44, 50, 65, 71, 80, 81).
     * Refuses to generate findings when evidence is unavailable, and prevents descriptive results
     * from being converted into unsupported claims of alpha, skill, or permanent impairment.
     * Includes calculationRunId, episodeId, allocationSnapshotId, and findingVersion for continuity.
     * Every finding is strictly grounded in an empirical investigation question without predictive claims (Task 71).
     */
    private void generateStressInvestigationFindings(
        Long investorId,
        Long portfolioId,
        List<PortfolioStressEpisodeDto> episodes,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String runId,
        String allocationSnapshotId,
        String inputFingerprint,
        List<RiskInvestigationFindingDto> findings,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        if (findings == null || episodes.isEmpty()) {
            return;
        }

        // Benchmark Fallback finding if applicable (Task 37, 44, 65, 71)
        if (isFallbackBenchmark) {
            String question = "Does the stress-period result remain comparable when benchmark identity and observation coverage are considered?";
            String headline = "Stress Reference Benchmark Fallback (" + benchmarkCode + ")";
            String detail = "Benchmark " + benchmarkCode + " represents large-cap stocks and may differ in episode timing and drawdown depth compared to the broad-market NIFTY 500 TRI.";
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-STRESS-BENCHMARK-FALLBACK-" + runId,
                "DATA_LIMITATION",
                headline,
                String.format(Locale.US,
                    "Stress episodes were identified using fallback benchmark %s instead of preferred %s.",
                    benchmarkCode, DEFAULT_BENCHMARK_CODE
                ),
                detail,
                question,
                "Inspect benchmark fallback details: requested preferred " + DEFAULT_BENCHMARK_CODE + ", resolved fallback " + benchmarkCode + ". CalculationRunId: " + runId + ", Methodology: " + METHODOLOGY_VERSION + ".",
                runId, null, allocationSnapshotId, "V2", "DESCRIPTIVE_SAMPLE", false, null,
                "FINDING-STRESS-BENCHMARK-FALLBACK", "ACTIVE", inputFingerprint, null, question
            ));
            persistInvestorFinding(
                investorId, portfolioId, "FINDING-STRESS-BENCHMARK-FALLBACK", "DATA_LIMITATION", "MEDIUM",
                headline, detail, "PORTFOLIO_BENCHMARK", "Reference Benchmark Selection", runId,
                allocationSnapshotId, inputFingerprint, asOfDate, knowledgeCutoff
            );
        }

        for (PortfolioStressEpisodeDto ep : episodes) {
            // Missing constituent finding (Task 35, 44, 50, 65, 71)
            if (ep.missingConstituentCount() > 0) {
                String question = "Did a constituent's apparent lower loss or withheld proxy result from a shorter available observation history?";
                String headline = "Constituent History Truncation (" + ep.episodeName() + " [" + ep.startDate() + " to " + ep.endDate() + "])";
                String detail = "Under §4 AGENTS.md, YUKIRA refuses to fabricate pre-inception returns or silently re-weight the portfolio. Portfolio-level proxy return is withheld for this episode.";
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-STRESS-MISSING-CONSTITUENT-" + ep.episodeId() + "-" + runId,
                    "DATA_LIMITATION",
                    headline,
                    String.format(Locale.US,
                        "%d constituent fund(s) (%s, representing %.2f%% of capital allocation) lack historical observations during %s [%s to %s] against benchmark %s due to post-dating inception dates.",
                        ep.missingConstituentCount(), String.join(", ", ep.excludedOrUnavailableConstituents()),
                        ep.missingAllocationWeightPct().doubleValue(), ep.episodeName(),
                        ep.startDate(), ep.endDate(), benchmarkCode
                    ),
                    detail,
                    question,
                    "Investigate constituent history truncation in episode " + ep.episodeId() + " (" + ep.startDate() + " to " + ep.endDate() + ") against benchmark " + benchmarkCode + ". CalculationRunId: " + runId + ", Methodology: " + METHODOLOGY_VERSION + ", EvidencePolicy: " + EVIDENCE_POLICY_VERSION + ".",
                    runId, ep.episodeId(), allocationSnapshotId, "V2", ep.evidenceState(), false, null,
                    "FINDING-STRESS-MISSING-CONSTITUENT-" + ep.episodeId(), "ACTIVE", inputFingerprint, null, question
                ));
                persistInvestorFinding(
                    investorId, portfolioId, "FINDING-STRESS-MISSING-CONSTITUENT-" + ep.episodeId(), "DATA_LIMITATION", "HIGH",
                    headline, detail, ep.episodeId(), ep.episodeName(), runId,
                    allocationSnapshotId, inputFingerprint, asOfDate, knowledgeCutoff
                );
            }

            // Finding 1: Stress Drawdown Severity (portfolio dropped more severely than benchmark) (Task 44, 50, 65, 71)
            if (ep.portfolioMaxDrawdownPct() != null && ep.benchmarkMaxDrawdownPct() != null
                && !"INSUFFICIENT_EVIDENCE".equals(ep.evidenceState())
                && !"NUMERICALLY_INVALID".equals(ep.evidenceState())) {
                double pDD = Math.abs(ep.portfolioMaxDrawdownPct().doubleValue());
                double bDD = Math.abs(ep.benchmarkMaxDrawdownPct().doubleValue());
                if (pDD > bDD * 1.15 && pDD >= 10.0) {
                    String question = "Was the portfolio's stress-period outcome driven by one or more concentrated constituent exposures?";
                    String headline = "Elevated Historical Stress Drawdown (" + ep.episodeName() + " [" + ep.startDate() + " to " + ep.endDate() + "])";
                    String detail = "Static current-allocation historical replay indicates portfolio composition exhibited higher maximum drawdown than reference benchmark during this historical decline under candidate Policy V2.";
                    findings.add(new RiskInvestigationFindingDto(
                        "FINDING-STRESS-DRAWDOWN-SEVERITY-" + ep.episodeId() + "-" + runId,
                        "CALCULATED_RISK",
                        headline,
                        String.format(Locale.US,
                            "Portfolio proxy suffered %.2f%% max drawdown during %s [%s to %s], exceeding benchmark %s drawdown of %.2f%% (1.15x severity threshold).",
                            ep.portfolioMaxDrawdownPct().doubleValue(), ep.episodeName(),
                            ep.startDate(), ep.endDate(), benchmarkCode, ep.benchmarkMaxDrawdownPct().doubleValue()
                        ),
                        detail,
                        question,
                        "Analyze elevated drawdown severity for episode " + ep.episodeId() + " (" + ep.startDate() + " to " + ep.endDate() + ") against benchmark " + benchmarkCode + ". CalculationRunId: " + runId + ", Methodology: " + METHODOLOGY_VERSION + ".",
                        runId, ep.episodeId(), allocationSnapshotId, "V2", ep.evidenceState(), false, null,
                        "FINDING-STRESS-DRAWDOWN-SEVERITY-" + ep.episodeId(), "ACTIVE", inputFingerprint, null, question
                    ));
                    persistInvestorFinding(
                        investorId, portfolioId, "FINDING-STRESS-DRAWDOWN-SEVERITY-" + ep.episodeId(), "CALCULATED_RISK", "HIGH",
                        headline, detail, ep.episodeId(), ep.episodeName(), runId,
                        allocationSnapshotId, inputFingerprint, asOfDate, knowledgeCutoff
                    );
                }
            }

            // Finding 2: Active Return Divergence (|active| >= 5.0%) (Task 44, 50, 65, 71 Non-Alpha Governance)
            if (ep.activeReturnPct() != null && Math.abs(ep.activeReturnPct().doubleValue()) >= 5.0
                && !"INSUFFICIENT_EVIDENCE".equals(ep.evidenceState())
                && !"NUMERICALLY_INVALID".equals(ep.evidenceState())) {
                String question = "Which constituent holdings accounted for the descriptive return divergence from reference benchmark, and how do multi-period compounding differences affect that attribution?";
                String headline = "Stress Episode Active Return Divergence (" + ep.episodeName() + " [" + ep.startDate() + " to " + ep.endDate() + "])";
                String detail = "Descriptive historical difference under Static Current-Allocation Constant-Weight Daily Rebalanced Proxy over window " + ep.startDate() + " to " + ep.endDate() + " (Benchmark: " + benchmarkCode + "). Does NOT measure or imply portfolio alpha, superior timing, or manager skill.";
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-STRESS-DIVERGENCE-" + ep.episodeId() + "-" + runId,
                    "OBSERVED_FACT",
                    headline,
                    String.format(Locale.US,
                        "Portfolio proxy cumulative return (%+.2f%%) diverged from benchmark %s (%+.2f%%) by %+.2f%% active return during %s [%s to %s].",
                        ep.portfolioCumulativeReturnPct().doubleValue(), benchmarkCode, ep.benchmarkCumulativeReturnPct().doubleValue(),
                        ep.activeReturnPct().doubleValue(), ep.episodeName(), ep.startDate(), ep.endDate()
                    ),
                    detail,
                    question,
                    "Inspect constituent return attribution divergence in episode " + ep.episodeId() + " (" + ep.startDate() + " to " + ep.endDate() + ") against benchmark " + benchmarkCode + ". CalculationRunId: " + runId + ", Methodology: " + METHODOLOGY_VERSION + ".",
                    runId, ep.episodeId(), allocationSnapshotId, "V2", ep.evidenceState(), false, null,
                    "FINDING-STRESS-DIVERGENCE-" + ep.episodeId(), "ACTIVE", inputFingerprint, null, question
                ));
                persistInvestorFinding(
                    investorId, portfolioId, "FINDING-STRESS-DIVERGENCE-" + ep.episodeId(), "OBSERVED_FACT", "MEDIUM",
                    headline, detail, ep.episodeId(), ep.episodeName(), runId,
                    allocationSnapshotId, inputFingerprint, asOfDate, knowledgeCutoff
                );
            }

            // Finding 3: Unrecovered Episode Status (Task 44, 50, 65, 71 Non-Impairment Governance)
            if ("ONGOING_UNRECOVERED".equals(ep.recoveryState()) && ep.benchmarkHasRecovered()
                && !"INSUFFICIENT_EVIDENCE".equals(ep.evidenceState())
                && !"NUMERICALLY_INVALID".equals(ep.evidenceState())) {
                String question = "Did the unrecovered portfolio proxy status reflect constituent drawdown depth, post-trough recovery pace, or the observation window cutoff date?";
                String headline = "Extended Historical Recovery Window (" + ep.episodeName() + " [" + ep.startDate() + " to " + ep.endDate() + "])";
                String detail = "Under the static constant-weight proxy model, portfolio recovery took longer than benchmark recovery (" + benchmarkCode + ") over window " + ep.startDate() + " to " + ep.endDate() + ". This is a descriptive historical property of the constituent return series over that specific event window, NOT an assessment of permanent capital impairment or indefinite loss.";
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-STRESS-UNRECOVERED-" + ep.episodeId() + "-" + runId,
                    "CALCULATED_RISK",
                    headline,
                    String.format(Locale.US,
                        "Portfolio proxy remained below its pre-stress peak at the end of the evaluated episode window for %s [%s to %s], whereas reference benchmark %s achieved full recovery on %s.",
                        ep.episodeName(), ep.startDate(), ep.endDate(), benchmarkCode, ep.benchmarkRecoveryDate()
                    ),
                    detail,
                    question,
                    "Investigate recovery duration and drawdowns in episode " + ep.episodeId() + " (" + ep.startDate() + " to " + ep.endDate() + ") against benchmark " + benchmarkCode + ". CalculationRunId: " + runId + ", Methodology: " + METHODOLOGY_VERSION + ".",
                    runId, ep.episodeId(), allocationSnapshotId, "V2", ep.evidenceState(), false, null,
                    "FINDING-STRESS-UNRECOVERED-" + ep.episodeId(), "ACTIVE", inputFingerprint, null, question
                ));
                persistInvestorFinding(
                    investorId, portfolioId, "FINDING-STRESS-UNRECOVERED-" + ep.episodeId(), "CALCULATED_RISK", "HIGH",
                    headline, detail, ep.episodeId(), ep.episodeName(), runId,
                    allocationSnapshotId, inputFingerprint, asOfDate, knowledgeCutoff
                );
            }

            // Finding 4: Coverage Limitation (Task 35, 44, 50, 65, 71)
            if ("LIMITED_EVIDENCE".equals(ep.evidenceState())) {
                String question = "How does truncated constituent history constrain cross-episode comparison between pre-inception and post-inception market declines?";
                String headline = "Partial Constituent History (" + ep.episodeName() + " [" + ep.startDate() + " to " + ep.endDate() + "])";
                String detail = "One or more constituent funds did not exist or had missing observations during this historical period. Analysis excludes unobserved days without synthetic fabrication under §4 AGENTS.md.";
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-STRESS-COVERAGE-LIMITATION-" + ep.episodeId() + "-" + runId,
                    "DATA_LIMITATION",
                    headline,
                    String.format(Locale.US,
                        "Historical stress replay for %s [%s to %s] has partial evidence coverage (%d aligned trading days, %.1f%% of benchmark %s days) due to younger constituent fund inception dates.",
                        ep.episodeName(), ep.startDate(), ep.endDate(), ep.alignedTradingDaysCount(),
                        ep.portfolioCoverageRatioPct() != null ? ep.portfolioCoverageRatioPct().doubleValue() : 0.0,
                        benchmarkCode
                    ),
                    detail,
                    question,
                    "Review partial observation coverage for episode " + ep.episodeId() + " (" + ep.startDate() + " to " + ep.endDate() + ") against benchmark " + benchmarkCode + ". CalculationRunId: " + runId + ", EvidencePolicy: " + EVIDENCE_POLICY_VERSION + ".",
                    runId, ep.episodeId(), allocationSnapshotId, "V2", ep.evidenceState(), false, null,
                    "FINDING-STRESS-COVERAGE-LIMITATION-" + ep.episodeId(), "ACTIVE", inputFingerprint, null, question
                ));
                persistInvestorFinding(
                    investorId, portfolioId, "FINDING-STRESS-COVERAGE-LIMITATION-" + ep.episodeId(), "DATA_LIMITATION", "MEDIUM",
                    headline, detail, ep.episodeId(), ep.episodeName(), runId,
                    allocationSnapshotId, inputFingerprint, asOfDate, knowledgeCutoff
                );
            }

            // Finding 5: Data Quality Warning (Task 38, 44, 50, 65, 71)
            if (ep.dataQualityWarningCount() > 0) {
                String question = "Do the detected observation gaps or repeated flat NAV streaks correlate with market non-trading days or data provider reporting delays?";
                String headline = "Stress Episode Observation Anomaly (" + ep.episodeName() + ")";
                String detail = "Descriptive observation anomaly. Does NOT confirm defective data without authoritative source corroboration.";
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-STRESS-DATA-QUALITY-ANOMALY-" + ep.episodeId() + "-" + runId,
                    "DATA_LIMITATION",
                    headline,
                    String.format(Locale.US,
                        "Episode %s [%s to %s] exhibited %d observation warning(s) (calendar gaps > 7 days or repeated flat NAV streaks >= 3 days).",
                        ep.episodeName(), ep.startDate(), ep.endDate(), ep.dataQualityWarningCount()
                    ),
                    detail,
                    question,
                    "Examine data quality signals in stress episode " + ep.episodeId() + ". CalculationRunId: " + runId + ".",
                    runId, ep.episodeId(), allocationSnapshotId, "V2", ep.evidenceState(), false, null,
                    "FINDING-STRESS-DATA-QUALITY-ANOMALY-" + ep.episodeId(), "ACTIVE", inputFingerprint, null, question
                ));
                persistInvestorFinding(
                    investorId, portfolioId, "FINDING-STRESS-DATA-QUALITY-ANOMALY-" + ep.episodeId(), "DATA_LIMITATION", "MEDIUM",
                    headline, detail, ep.episodeId(), ep.episodeName(), runId,
                    allocationSnapshotId, inputFingerprint, asOfDate, knowledgeCutoff
                );
            }
        }
    }

    /**
     * Backward-compatible 7-parameter overload for internal callers.
     */
    private void generateStressInvestigationFindings(
        List<PortfolioStressEpisodeDto> episodes,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String runId,
        String allocationSnapshotId,
        String inputFingerprint,
        List<RiskInvestigationFindingDto> findings
    ) {
        generateStressInvestigationFindings(
            null, null, episodes, benchmarkCode, isFallbackBenchmark, runId, allocationSnapshotId,
            inputFingerprint, findings, LocalDate.now(), OffsetDateTime.now()
        );
    }

    /**
     * Backward-compatible 6-parameter overload for internal callers.
     */
    private void generateStressInvestigationFindings(
        List<PortfolioStressEpisodeDto> episodes,
        String benchmarkCode,
        boolean isFallbackBenchmark,
        String runId,
        String allocationSnapshotId,
        List<RiskInvestigationFindingDto> findings
    ) {
        generateStressInvestigationFindings(
            episodes, benchmarkCode, isFallbackBenchmark, runId, allocationSnapshotId,
            "FP-" + (runId != null && runId.length() >= 8 ? runId.substring(0, 8) : "DEFAULT"),
            findings
        );
    }

    private String getFundDisplayName(SchemeOption option) {
        if (option == null) return "Unknown Fund";
        if (option.getPlan() != null && option.getPlan().getScheme() != null) {
            return option.getPlan().getScheme().getName();
        }
        return "Scheme Option #" + option.getId();
    }

    private List<String> buildStressDisclaimers() {
        List<String> list = new ArrayList<>();
        list.add("Static Current-Allocation Constant-Weight Daily Rebalanced Historical Stress Proxy: Applies current capital allocation weights consistently across past market stress episodes with daily rebalancing to those weights. This is a hypothetical mathematical proxy, NOT a reconstruction of the investor's actual past trades, cash flows, taxes, or realized portfolio performance.");
        list.add("Exact Additive Multi-Period Return Attribution: Constituent return contributions are linked across daily rebalancing intervals using Carino Logarithmic Linking with observation-specific linking factor k_t = ln(1 + R_{p,t}) / R_{p,t} derived from portfolio return R_{p,t} and overall factor K = ln(1 + R_{p,cum}) / R_{p,cum}. Linking factors are observation-specific across time steps, NOT constituent-specific. Approximate weighted returns (w_i * R_{i,cum}) are non-additive over multi-period compounding; the difference \\Delta_i = w_i * R_{i,cum} - A_i measures the multi-period compounding difference and does not isolate pure rebalancing interaction.");
        list.add("TRI Market-Stress Reference: NIFTY 500 TRI (or identified fallback) is used strictly to identify broad market drawdown episodes (>= 10.0%), NOT as a category performance benchmark for individual mutual fund schemes.");
        list.add("Candidate Methodology: Governed under PORTFOLIO_STRESS_METHODOLOGY_V2, PORTFOLIO_STRESS_IDENTIFICATION_POLICY_V2 & PORTFOLIO_STRESS_EVIDENCE_POLICY_V2. This analysis provides deterministic empirical context and does NOT constitute investment advice or a prediction of future stress resilience.");
        list.add("Zero Synthetic NAVs & Honest Gating: Holdings lacking historical observations during an episode are classified as INSUFFICIENT_EVIDENCE without synthetic data extrapolation or silent weight redistribution (§4 AGENTS.md). Complete portfolio proxy returns are withheld when constituent history is truncated.");
        list.add("Attribution Role Separation: Constituent attribution role labels describe sample loss drag relative to portfolio proxy under candidate thresholds. RESILIENT_LOWER_LOSS_CONTRIBUTOR strictly reflects lower loss drag in the specific sample and does NOT imply positive absolute return, downside protection, or superior manager skill.");
        list.add("Statistical Evidence Gating: 15-observation threshold defines descriptive event samples for observed historical episodes and does NOT imply asymptotic statistical significance or predictive reliability.");
        return list;
    }

    private Map<String, String> buildStressMethodologyNotes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("StressModel", "Static Current-Allocation Constant-Weight Daily Rebalanced Historical Stress Proxy (R_{p,t} = \\sum w_i R_{i,t})");
        map.put("PortfolioConstructionConvention", CONSTRUCTION_CONVENTION);
        map.put("MethodologyVersion", METHODOLOGY_VERSION);
        map.put("IdentificationPolicy", IDENTIFICATION_POLICY_VERSION);
        map.put("EvidencePolicy", EVIDENCE_POLICY_VERSION);
        map.put("AttributionMethodology", ATTRIBUTION_METHODOLOGY);
        map.put("AttributionReconciliationTolerance", "+/-0.0001% (+/-1e-6 decimal)");
        map.put("BenchmarkDrawdownThreshold", ">= 10.0% Peak-to-Trough Drawdown");
        map.put("DeduplicationRule", "Non-overlapping Peak-to-Recovery windows (unified episodes, nested drawdown deduplication)");
        map.put("ReturnAttributionFormula", "A_i = (1/K) * \\sum [k_t * w_i * R_{i,t}] (Carino Logarithmic Linking where k_t = ln(1+R_{p,t})/R_{p,t} is observation-specific and K = ln(1+R_{p,cum})/R_{p,cum})");
        map.put("ApproximateReturnFormula", "W_i = w_i * R_{i,cum} (non-additive single-period weighted indicator)");
        map.put("AttributionDifferenceFormula", "\\Delta_i = w_i * R_{i,cum} - A_i (weighted return to linked attribution difference; measures multi-period compounding effect, does not isolate pure rebalancing interaction)");
        map.put("CompoundingResidualFormula", "\\Delta_{compounding} = \\sum (w_i * R_{i,cum}) - R_{p,cum} (portfolio-level compounding residual across multi-period rebalancing)");
        map.put("AnnualizationConvention", "252 Trading Days (Square Root of Time)");
        map.put("EvidenceAdequacyStandard", "15-observation threshold defines descriptive event samples; does not imply asymptotic statistical significance or predictive reliability.");
        map.put("PointInTimeContract", "effective_date <= asOfDate AND availability_time <= knowledgeCutoff");
        map.put("GovernanceClassification", "CANDIDATE_METHODOLOGY (Non-Advisory Verification Checkpoint)");
        return map;
    }

    /**
     * Persists a stress finding to the durable investor finding repository (Tasks 80, 81).
     * Enforces supersession and version incrementing when an active finding for the same episode exists.
     */
    private void persistInvestorFinding(
        Long investorId,
        Long portfolioId,
        String findingCode,
        String findingType,
        String severity,
        String headline,
        String detail,
        String episodeCode,
        String episodeName,
        String runId,
        String snapshotId,
        String inputFingerprint,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        if (investorId == null || stressFindingRepository == null || investorRepository == null) {
            return;
        }
        try {
            Optional<Investor> invOpt = investorRepository.findById(investorId);
            if (invOpt.isEmpty()) return;
            Investor investor = invOpt.get();

            List<InvestorStressFinding> activeSim = stressFindingRepository.findActiveSimilarFindings(
                investorId, portfolioId, episodeCode, findingType
            );
            int version = 1;
            Long supersededId = null;
            if (activeSim != null && !activeSim.isEmpty()) {
                InvestorStressFinding prev = activeSim.get(0);
                version = (prev.getFindingVersion() != null ? prev.getFindingVersion() : 1) + 1;
                supersededId = prev.getId();
                String oldStatus = prev.getLifecycleStatus();
                prev.setLifecycleStatus("SUPERSEDED");
                prev.setTransitionBy("SYSTEM");
                prev.setTransitionReason("Superseded by newer finding version " + version + " in calculation run " + runId);
                prev.setUpdatedAt(OffsetDateTime.now());
                stressFindingRepository.save(prev);

                if (stressFindingTransitionRepository != null) {
                    stressFindingTransitionRepository.save(new InvestorStressFindingTransition(
                        prev, prev.getFindingId(), oldStatus, "SUPERSEDED", "SYSTEM",
                        "Superseded by finding version " + version + " in calculation run " + runId,
                        prev.getInvestorNotes(), runId
                    ));
                }
            }

            InvestorStressFinding f = new InvestorStressFinding();
            f.setInvestor(investor);
            f.setPortfolioId(portfolioId);
            f.setFindingId(findingCode);
            f.setFindingVersion(version);
            f.setCalculationRunCode(runId);
            f.setSnapshotId(snapshotId);
            f.setSnapshotFingerprint(inputFingerprint);
            f.setEpisodeCode(episodeCode != null ? episodeCode : "PORTFOLIO");
            f.setEpisodeName(episodeName != null ? episodeName : "Consolidated Portfolio");
            f.setFindingType(findingType);
            f.setFindingSeverity(severity);
            f.setFindingHeadline(headline);
            f.setFindingDetail(detail);
            f.setMethodologyVersion(METHODOLOGY_VERSION);
            f.setGovernanceStatus("CANDIDATE");
            f.setKnowledgeCutoffDate(knowledgeCutoff);
            f.setAsOfDate(asOfDate);
            f.setLifecycleStatus("ACTIVE");
            f.setTransitionBy("SYSTEM");
            f.setTransitionReason("Initial calculation detection in run " + runId);
            InvestorStressFinding saved = stressFindingRepository.save(f);

            if (stressFindingTransitionRepository != null) {
                stressFindingTransitionRepository.save(new InvestorStressFindingTransition(
                    saved, saved.getFindingId(), "NONE", "ACTIVE", "SYSTEM",
                    "Initial calculation detection in run " + runId, null, runId
                ));
            }

            if (supersededId != null) {
                Optional<InvestorStressFinding> oldOpt = stressFindingRepository.findById(supersededId);
                if (oldOpt.isPresent()) {
                    InvestorStressFinding old = oldOpt.get();
                    old.setSupersededByFindingId(saved.getId());
                    stressFindingRepository.save(old);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to persist stress investigation finding {}: {}", findingCode, ex.getMessage());
        }
    }

    /**
     * Persists an immutable calculation manifest linking calculation run, investor, snapshot, and serialized results (Tasks 85, 104).
     */
    private void persistCalculationManifest(
        Long investorId,
        Long portfolioId,
        String runId,
        PortfolioAllocationSnapshotDto snapshot,
        String inputFingerprint,
        LocalDate effectiveAsOf,
        OffsetDateTime effectiveCutoff,
        StressCalculationContextDto context,
        PortfolioStressSummaryDto summaryDto,
        List<PortfolioStressEpisodeDto> episodes
    ) {
        if (investorId == null || stressManifestRepository == null || investorRepository == null) {
            return;
        }
        try {
            Optional<Investor> invOpt = investorRepository.findById(investorId);
            if (invOpt.isEmpty()) return;
            Investor investor = invOpt.get();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("calculationRunCode", runId);
            payload.put("snapshot", snapshot);
            payload.put("inputFingerprint", inputFingerprint);
            payload.put("asOfDate", effectiveAsOf != null ? effectiveAsOf.toString() : null);
            payload.put("knowledgeCutoff", effectiveCutoff != null ? effectiveCutoff.toString() : null);
            payload.put("context", context);
            payload.put("summary", summaryDto);
            payload.put("episodesCount", episodes != null ? episodes.size() : 0);
            payload.put("benchmarkCode", context != null ? context.benchmarkCode() : DEFAULT_BENCHMARK_CODE);
            payload.put("benchmarkSourceRevision", context != null ? context.benchmarkSourceRevision() : null);
            payload.put("constituentSourceRevisions", context != null ? context.constituentSourceRevisions() : Collections.emptyMap());
            payload.put("methodologyVersion", METHODOLOGY_VERSION);
            payload.put("policyVersions", Map.of(
                "identificationPolicy", IDENTIFICATION_POLICY_VERSION,
                "evidencePolicy", EVIDENCE_POLICY_VERSION
            ));
            payload.put("calculationOutcome", summaryDto != null && summaryDto.computableEpisodesCount() > 0 ? "SUCCESS" : "INSUFFICIENT_EVIDENCE");
            payload.put("withholdingReasons", summaryDto != null ? summaryDto.coverageLimitationDisclosure() : null);
            payload.put("replayabilityStatus", "REPRODUCIBLE_CONTEXT_BOUNDED_SOURCE_AVAILABILITY");
            payload.put("replayabilityLimitation", "Stores complete parameterization, snapshot fingerprint, and constituent source revisions. Complete tick-by-tick observation replay depends on persistence of underlying raw source artifacts.");

            if (episodes != null) {
                List<Map<String, Object>> epSummaries = new ArrayList<>();
                for (PortfolioStressEpisodeDto ep : episodes) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("episodeId", ep.episodeId());
                    m.put("episodeName", ep.episodeName());
                    m.put("startDate", ep.startDate());
                    m.put("endDate", ep.endDate());
                    m.put("eligibility", ep.eligibilityStatus());
                    m.put("portfolioReturnPct", ep.portfolioCumulativeReturnPct());
                    m.put("benchmarkReturnPct", ep.benchmarkCumulativeReturnPct());
                    m.put("maxDrawdownPct", ep.portfolioMaxDrawdownPct());
                    m.put("evidenceState", ep.evidenceState());
                    epSummaries.add(m);
                }
                payload.put("episodes", epSummaries);
            }
            String json = objectMapper.writeValueAsString(payload);

            InvestorStressCalculationManifest manifest = new InvestorStressCalculationManifest();
            manifest.setInvestor(investor);
            manifest.setPortfolioId(portfolioId);
            manifest.setCalculationRunCode(runId);
            manifest.setSnapshotId(snapshot != null ? snapshot.snapshotId() : null);
            manifest.setSnapshotFingerprint(snapshot != null ? snapshot.snapshotFingerprint() : null);
            manifest.setInputHash(inputFingerprint);
            manifest.setMethodologyVersion(METHODOLOGY_VERSION);
            manifest.setGovernanceStatus("CANDIDATE");
            manifest.setKnowledgeCutoff(effectiveCutoff);
            manifest.setAnalysisCutoffDate(effectiveAsOf);
            manifest.setManifestPayloadJson(json);
            stressManifestRepository.save(manifest);
        } catch (Exception ex) {
            log.warn("Failed to persist stress calculation manifest for run {}: {}", runId, ex.getMessage());
        }
    }

    /**
     * Persists an immutable allocation snapshot with constituent item details if not already present (Task 105, 106).
     */
    private void persistAllocationSnapshotIfAbsent(
        Long investorId,
        Long portfolioId,
        LocalDate effectiveAsOf,
        OffsetDateTime effectiveCutoff,
        PortfolioAllocationSnapshotDto snapshotDto,
        List<Long> fundIds,
        Map<Long, Double> weights,
        Map<Long, BigDecimal> fundMarketValues,
        Map<Long, SchemeOption> valuedSchemes
    ) {
        if (allocationSnapshotRepository == null || investorRepository == null || investorId == null || snapshotDto == null) {
            return;
        }
        try {
            String fingerprint = snapshotDto.snapshotFingerprint();
            if (fingerprint != null && allocationSnapshotRepository.findFirstByInvestorIdAndSnapshotFingerprintOrderByCreatedAtDesc(investorId, fingerprint).isPresent()) {
                return; // Immutable snapshot already exists
            }

            Optional<Investor> invOpt = investorRepository.findById(investorId);
            if (invOpt.isEmpty()) return;
            Investor investor = invOpt.get();

            InvestorPortfolioAllocationSnapshot snapshot = new InvestorPortfolioAllocationSnapshot();
            snapshot.setSnapshotId(snapshotDto.snapshotId() != null ? snapshotDto.snapshotId() : "SNAP-" + UUID.randomUUID());
            snapshot.setInvestor(investor);
            snapshot.setPortfolioId(portfolioId);
            snapshot.setEffectiveDate(effectiveAsOf != null ? effectiveAsOf : LocalDate.now());
            snapshot.setValuationAsOfDate(snapshotDto.valuationAsOfDate() != null ? snapshotDto.valuationAsOfDate() : effectiveAsOf);
            snapshot.setKnowledgeCutoff(effectiveCutoff != null ? effectiveCutoff : OffsetDateTime.now());
            snapshot.setKnownValuedCapital(snapshotDto.knownValuedCapitalAmount());
            snapshot.setTotalPortfolioCapital(snapshotDto.totalPortfolioCapitalAmount());
            snapshot.setCoveragePct(snapshotDto.knownValuedCapitalPercentage());
            snapshot.setValuedPositionCount(snapshotDto.valuedPositionCount());
            snapshot.setUnvaluedPositionCount(snapshotDto.unvaluedPositionCount());
            snapshot.setTotalPositionCount(snapshotDto.totalPositionCount());
            snapshot.setWeightCalculationConvention(snapshotDto.weightCalculationConvention() != null ? snapshotDto.weightCalculationConvention() : "TOTAL_VALUED_CAPITAL");
            snapshot.setWeightNormalizationDenominator(snapshotDto.weightNormalizationDenominator() != null ? snapshotDto.weightNormalizationDenominator() : BigDecimal.valueOf(100.0));
            snapshot.setSnapshotFingerprint(fingerprint != null ? fingerprint : snapshot.getSnapshotId());
            snapshot.setImmutable(true);

            if (fundIds != null) {
                for (Long fundId : fundIds) {
                    InvestorPortfolioAllocationSnapshotItem item = new InvestorPortfolioAllocationSnapshotItem();
                    SchemeOption opt = valuedSchemes != null ? valuedSchemes.get(fundId) : null;
                    item.setSchemeOption(opt);
                    item.setSchemeCode(opt != null ? opt.getSchemeCode() : "SCHEME-" + fundId);
                    item.setSchemeName(opt != null && opt.getScheme() != null ? opt.getScheme().getSchemeName() : "Fund " + fundId);
                    Double w = weights != null ? weights.get(fundId) : null;
                    item.setAllocationWeight(w != null ? BigDecimal.valueOf(w) : null);
                    BigDecimal mv = fundMarketValues != null ? fundMarketValues.get(fundId) : null;
                    item.setMarketValue(mv);
                    item.setValued(mv != null && mv.compareTo(BigDecimal.ZERO) > 0);
                    item.setValuationStatus(item.getValued() ? "VALUED" : "UNVALUED_MISSING_PRICE");
                    snapshot.addItem(item);
                }
            }
            allocationSnapshotRepository.save(snapshot);
        } catch (Exception ex) {
            log.warn("Failed to persist portfolio allocation snapshot: {}", ex.getMessage());
        }
    }

    /**
     * Persists generated data-quality signals with source-revision lifecycle tracking (Task 82, 103).
     */
    private void persistDataQualitySignals(
        Long investorId,
        Long portfolioId,
        String runId,
        String benchmarkCode,
        LocalDate asOfDate,
        List<PortfolioStressEpisodeDto> episodes
    ) {
        if (stressSignalRepository == null || investorRepository == null || investorId == null || episodes == null) {
            return;
        }
        try {
            Optional<Investor> invOpt = investorRepository.findById(investorId);
            if (invOpt.isEmpty()) return;
            Investor investor = invOpt.get();

            for (PortfolioStressEpisodeDto ep : episodes) {
                if (ep.dataQualitySignals() == null) continue;
                for (StressDataQualitySignalDto sigDto : ep.dataQualitySignals()) {
                    InvestorStressDataQualitySignal signal = new InvestorStressDataQualitySignal();
                    signal.setInvestor(investor);
                    signal.setPortfolioId(portfolioId);
                    signal.setCalculationRunCode(runId);
                    signal.setSignalCode(sigDto.signalId() != null ? sigDto.signalId() : "SIG-" + sigDto.signalType());
                    signal.setSignalDimension(sigDto.dimension() != null ? sigDto.dimension() : "QUALITY");
                    signal.setSignalState("ACTIVE_WARNING");
                    signal.setConstituentCode(sigDto.constituentId());
                    signal.setBenchmarkCode(benchmarkCode);
                    signal.setMessage(sigDto.message() != null ? sigDto.message() : "Observation anomaly");
                    signal.setEvidenceContext(sigDto.evidenceContext());
                    signal.setObservationIntervalStart(ep.startDate());
                    signal.setObservationIntervalEnd(ep.endDate());
                    signal.setFirstDetectedAt(OffsetDateTime.now());
                    signal.setLatestDetectedAt(OffsetDateTime.now());
                    stressSignalRepository.save(signal);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to persist stress data quality signals for run {}: {}", runId, ex.getMessage());
        }
    }

    /**
     * Retrieves stored stress calculation manifest by calculation run code (Task 85, 104).
     */
    public Optional<InvestorStressCalculationManifestDto> getCalculationManifest(String calculationRunCode) {
        if (stressManifestRepository == null || calculationRunCode == null) return Optional.empty();
        return stressManifestRepository.findByCalculationRunCode(calculationRunCode)
            .map(this::mapToManifestDto);
    }

    /**
     * Retrieves active stress investigation findings for an authenticated investor and portfolio (Task 81, 101).
     */
    public List<InvestorStressFindingDto> getActiveFindings(Long investorId, Long portfolioId) {
        if (stressFindingRepository == null || investorId == null) return Collections.emptyList();
        List<InvestorStressFinding> list = portfolioId != null
            ? stressFindingRepository.findByInvestorIdAndPortfolioIdAndLifecycleStatusOrderByCreatedAtDesc(investorId, portfolioId, "ACTIVE")
            : stressFindingRepository.findByInvestorIdAndLifecycleStatusOrderByCreatedAtDesc(investorId, "ACTIVE");
        return list.stream().map(this::mapToFindingDto).toList();
    }

    /**
     * Retrieves all stress investigation findings regardless of lifecycle status (Task 101).
     */
    public List<InvestorStressFindingDto> getAllFindings(Long investorId, Long portfolioId) {
        if (stressFindingRepository == null || investorId == null) return Collections.emptyList();
        List<InvestorStressFinding> list = portfolioId != null
            ? stressFindingRepository.findByInvestorIdAndPortfolioIdOrderByCreatedAtDesc(investorId, portfolioId)
            : stressFindingRepository.findByInvestorIdOrderByCreatedAtDesc(investorId);
        return list.stream().map(this::mapToFindingDto).toList();
    }

    /**
     * Retrieves full version history of a specific finding across calculation runs (Task 101, 102).
     */
    public List<InvestorStressFindingDto> getFindingVersionHistory(String findingId, Long investorId) {
        if (stressFindingRepository == null || findingId == null || investorId == null) return Collections.emptyList();
        return stressFindingRepository.findByInvestorIdAndFindingIdOrderByFindingVersionDesc(investorId, findingId)
            .stream().map(this::mapToFindingDto).toList();
    }

    /**
     * Retrieves transition audit ledger entries for a finding record (Task 102).
     */
    public List<InvestorStressFindingTransitionDto> getFindingTransitions(Long findingRecordId, Long investorId) {
        if (stressFindingTransitionRepository == null || findingRecordId == null || investorId == null) return Collections.emptyList();
        Optional<InvestorStressFinding> findingOpt = stressFindingRepository.findById(findingRecordId);
        if (findingOpt.isEmpty() || !investorId.equals(findingOpt.get().getInvestor().getId())) {
            throw new IllegalArgumentException("Unauthorized: Finding not found or owned by another investor.");
        }
        return stressFindingTransitionRepository.findByFindingRecordIdOrderByCreatedAtDesc(findingRecordId)
            .stream().map(t -> new InvestorStressFindingTransitionDto(
                t.getId(),
                t.getFinding().getId(),
                t.getFindingId(),
                t.getPreviousStatus(),
                t.getNewStatus(),
                t.getTransitionBy(),
                t.getTransitionReason(),
                t.getInvestorNotes(),
                t.getCalculationRunCode(),
                t.getCreatedAt()
            )).toList();
    }

    /**
     * Updates finding lifecycle status with optional investor notes and records durable transition audit (Task 81, 102).
     */
    @Transactional
    public Optional<InvestorStressFindingDto> updateFindingLifecycleStatus(Long findingRecordId, String newStatus, String investorNotes, Long investorId) {
        if (stressFindingRepository == null || findingRecordId == null || investorId == null) return Optional.empty();
        Optional<InvestorStressFinding> opt = stressFindingRepository.findById(findingRecordId);
        if (opt.isEmpty()) return Optional.empty();
        InvestorStressFinding finding = opt.get();
        if (finding.getInvestor() == null || !investorId.equals(finding.getInvestor().getId())) {
            throw new IllegalArgumentException("Unauthorized: Cannot modify finding belonging to another investor.");
        }
        String oldStatus = finding.getLifecycleStatus();
        String normalizedStatus = newStatus != null ? newStatus.toUpperCase() : "RESOLVED";
        finding.setLifecycleStatus(normalizedStatus);
        finding.setTransitionReason("Investor status transition from " + oldStatus + " to " + normalizedStatus);
        finding.setTransitionBy("INVESTOR");
        if (investorNotes != null && !investorNotes.isBlank()) {
            finding.setInvestorNotes(investorNotes);
        }
        finding.setUpdatedAt(OffsetDateTime.now());
        InvestorStressFinding saved = stressFindingRepository.save(finding);

        if (stressFindingTransitionRepository != null) {
            stressFindingTransitionRepository.save(new InvestorStressFindingTransition(
                saved, saved.getFindingId(), oldStatus, normalizedStatus, "INVESTOR",
                finding.getTransitionReason(), finding.getInvestorNotes(), saved.getCalculationRunCode()
            ));
        }
        return Optional.of(mapToFindingDto(saved));
    }

    /**
     * Backward-compatible 3-parameter lifecycle transition method.
     */
    @Transactional
    public Optional<InvestorStressFindingDto> updateFindingLifecycleStatus(Long findingRecordId, String newStatus, Long investorId) {
        return updateFindingLifecycleStatus(findingRecordId, newStatus, null, investorId);
    }

    private InvestorStressFindingDto mapToFindingDto(InvestorStressFinding f) {
        return new InvestorStressFindingDto(
            f.getId(),
            f.getInvestor() != null ? f.getInvestor().getId() : null,
            f.getPortfolioId(),
            f.getFindingId(),
            f.getFindingVersion() != null ? f.getFindingVersion() : 1,
            f.getCalculationRunCode(),
            f.getSnapshotId(),
            f.getSnapshotFingerprint(),
            f.getEpisodeCode(),
            f.getEpisodeName(),
            f.getFindingType(),
            f.getFindingSeverity(),
            f.getFindingHeadline(),
            f.getFindingDetail(),
            f.getMethodologyVersion(),
            f.getGovernanceStatus(),
            f.getKnowledgeCutoffDate(),
            f.getAsOfDate(),
            f.getLifecycleStatus(),
            f.getSupersededByFindingId(),
            f.getTransitionReason(),
            f.getTransitionBy(),
            f.getInvestorNotes(),
            f.getCreatedAt(),
            f.getUpdatedAt()
        );
    }

    private InvestorStressCalculationManifestDto mapToManifestDto(InvestorStressCalculationManifest m) {
        return new InvestorStressCalculationManifestDto(
            m.getId(),
            m.getCalculationRun() != null ? m.getCalculationRun().getId() : null,
            m.getCalculationRunCode(),
            m.getPortfolioId(),
            m.getInvestor() != null ? m.getInvestor().getId() : null,
            m.getSnapshotId(),
            m.getSnapshotFingerprint(),
            m.getInputHash(),
            m.getMethodologyVersion(),
            m.getGovernanceStatus(),
            m.getKnowledgeCutoff(),
            m.getAnalysisCutoffDate(),
            m.getManifestPayloadJson(),
            m.getCreatedAt()
        );
    }
}
