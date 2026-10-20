package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import com.yukira.backend.dto.portfolio.stress.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service orchestrating Historical Market-Regime Replay, Portfolio Stress Testing,
 * and Evidence-Based Loss Analysis for research portfolios.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md):
 * 1. Zero frontend math: All cumulative returns, drawdowns, active returns, semideviations,
 *    and linear sensitivity estimates are computed deterministically on the backend.
 * 2. Hypothetical Historical Reconstruction: When today's target weights are applied to historical
 *    NAV series, the result is explicitly labeled as a HYPOTHETICAL_RECONSTRUCTION under daily
 *    target-weight rebalancing, NOT actual investor trading experience.
 * 3. Point-in-Time Compliance: Observations must satisfy effectiveDate <= asOfDate and
 *    availabilityTime <= knowledgeCutoff.
 * 4. Temporal Mismatch Disclosure: If holdings snapshots are temporally distant (>45 days) from
 *    the stress episode, the mismatch is explicitly flagged.
 * 5. Honest Gating: Episodes without required underlying observations are flagged as
 *    DATA_UNAVAILABLE rather than silently approximated or fabricated.
 */
@Service
@SuppressWarnings("null")
public class PortfolioStressReplayService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioStressReplayService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_BENCHMARK = "NIFTY_500_TRI";

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final SecurityMarketCapClassificationRepository classificationRepository;

    @Autowired
    public PortfolioStressReplayService(
            SchemeOptionRepository schemeOptionRepository,
            NavObservationRepository navObservationRepository,
            BenchmarkRepository benchmarkRepository,
            BenchmarkObservationRepository benchmarkObservationRepository,
            PortfolioSnapshotRepository portfolioSnapshotRepository,
            PortfolioHoldingRepository portfolioHoldingRepository,
            SecurityMarketCapClassificationRepository classificationRepository) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.classificationRepository = classificationRepository;
    }

    /**
     * Definition of verified historical market stress episodes.
     */
    public record PredefinedEpisodeSpec(
        String episodeId,
        String title,
        String category,
        LocalDate startDate,
        LocalDate endDate,
        String peakTroughConvention,
        String regimeType,
        String description,
        boolean isGuaranteedAvailable,
        String benchmarkCode
    ) {}

    private static final List<PredefinedEpisodeSpec> PREDEFINED_EPISODES = List.of(
        new PredefinedEpisodeSpec(
            "EPISODE_2021_2022_RATE_HIKE_DRAWDOWN",
            "2021–2022 Global Rate-Hike & Inflation Drawdown",
            "GLOBAL_MONETARY_TIGHTENING",
            LocalDate.of(2021, 10, 18),
            LocalDate.of(2022, 6, 17),
            "Peak-to-Trough of NIFTY 500 TRI (24,305.72 to 20,052.33, -17.50% correction)",
            "FULL_DECLINE",
            "Coordinated central bank policy tightening, elevated global inflation, and Eastern European geopolitical conflict.",
            true,
            "NIFTY_500_TRI"
        ),
        new PredefinedEpisodeSpec(
            "EPISODE_2022_2023_WINTER_CORRECTION",
            "Winter 2022–2023 Rate Peak & US Banking Shock",
            "BANKING_LIQUIDITY_SHOCK",
            LocalDate.of(2022, 12, 1),
            LocalDate.of(2023, 3, 28),
            "Peak-to-Trough of NIFTY 500 TRI (24,828.21 to 22,071.73, -11.10% correction)",
            "FULL_DECLINE",
            "Terminal policy rate uncertainty, collapse of Silicon Valley Bank & Credit Suisse, and corporate earnings deceleration.",
            true,
            "NIFTY_500_TRI"
        ),
        new PredefinedEpisodeSpec(
            "EPISODE_2023_AUTUMN_YIELD_SPIKE",
            "Autumn 2023 US 10Y Yield Spike & Middle East Tension",
            "GEOPOLITICAL_YIELD_SPIKE",
            LocalDate.of(2023, 9, 15),
            LocalDate.of(2023, 10, 26),
            "Peak-to-Trough of NIFTY 500 TRI (28,349 to 26,458, -6.67% pullback)",
            "FULL_DECLINE",
            "US 10-year Treasury yield surged above 5.00% alongside heightened geopolitical escalation in the Middle East.",
            true,
            "NIFTY_500_TRI"
        ),
        new PredefinedEpisodeSpec(
            "EPISODE_2024_SEBI_MIDSMALL_SHAKEOUT",
            "Feb–March 2024 Small/Mid-Cap Regulatory Stress",
            "REGULATORY_LIQUIDITY_STRESS",
            LocalDate.of(2024, 2, 8),
            LocalDate.of(2024, 3, 19),
            "Broad market consolidation and mid/small-cap liquidity shakeout following SEBI froth advisory",
            "FULL_DECLINE",
            "SEBI advisory on mid/small-cap market froth and regulatory mandate requiring bi-weekly portfolio liquidity stress metrics.",
            true,
            "NIFTY_500_TRI"
        ),
        new PredefinedEpisodeSpec(
            "EPISODE_2024_ELECTION_RESULTS_SHOCK",
            "June 2024 Indian General Election Shock & Rebound",
            "POLITICAL_VOLATILITY",
            LocalDate.of(2024, 5, 24),
            LocalDate.of(2024, 6, 5),
            "Pre-results peak to single-day result drawdown (June 4) and immediate Day 1 recovery",
            "FULL_CYCLE_WITH_RECOVERY",
            "High-amplitude single-day volatility on June 4, 2024 as election results diverged from exit poll narratives, followed by prompt recovery.",
            true,
            "NIFTY_500_TRI"
        ),
        new PredefinedEpisodeSpec(
            "EPISODE_2020_COVID_SHOCK",
            "February–March 2020 COVID-19 Liquidity Shock",
            "SYSTEMIC_PANDEMIC_CRISIS",
            LocalDate.of(2020, 2, 19),
            LocalDate.of(2020, 3, 23),
            "Systemic pandemic liquidity crash",
            "FULL_DECLINE",
            "Global pandemic declaration, nationwide lockdown in India, and sudden synchronized worldwide market liquidation.",
            false, // Gated: Pre-2021 data not available in local dataset for PPFAS or NIFTY 500 TRI
            "NIFTY_500_TRI"
        )
    );

    /**
     * Inspects historical coverage for the given portfolio funds and returns the episode catalog
     * with explicit dynamic availability flags and unavailability rationales.
     */
    @Transactional(readOnly = true)
    public List<HistoricalMarketEpisodeDto> getAvailableEpisodes(
            List<ResearchPortfolioFundItemDto> funds,
            OffsetDateTime knowledgeCutoff
    ) {
        return getAvailableEpisodes(funds, DEFAULT_BENCHMARK, knowledgeCutoff);
    }

    @Transactional(readOnly = true)
    public List<HistoricalMarketEpisodeDto> getAvailableEpisodes(
            List<ResearchPortfolioFundItemDto> funds,
            String requestedBenchmarkCode,
            OffsetDateTime knowledgeCutoff
    ) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        List<HistoricalMarketEpisodeDto> result = new ArrayList<>();

        String targetBenchmarkCode = requestedBenchmarkCode != null && !requestedBenchmarkCode.isBlank()
            ? requestedBenchmarkCode : DEFAULT_BENCHMARK;

        if (funds == null || funds.isEmpty()) {
            for (PredefinedEpisodeSpec spec : PREDEFINED_EPISODES) {
                String epBmCode = spec.benchmarkCode() != null ? spec.benchmarkCode() : targetBenchmarkCode;
                result.add(new HistoricalMarketEpisodeDto(
                    spec.episodeId(), spec.title(), spec.category(),
                    spec.startDate(), spec.endDate(), spec.peakTroughConvention(),
                    spec.regimeType(), spec.description(), false,
                    "No funds selected in research portfolio.", null, null,
                    epBmCode
                ));
            }
            return result;
        }

        // Determine earliest valid date for each constituent fund
        Map<Long, LocalDate> fundMinDates = new HashMap<>();
        Map<Long, String> fundNames = new HashMap<>();

        for (ResearchPortfolioFundItemDto item : funds) {
            SchemeOption option = schemeOptionRepository.findById(item.schemeOptionId()).orElse(null);
            String name = option != null && option.getPlan() != null && option.getPlan().getScheme() != null
                ? option.getPlan().getScheme().getName() : "Fund #" + item.schemeOptionId();
            fundNames.put(item.schemeOptionId(), name);

            List<NavObservation> navs = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
                item.schemeOptionId(), LocalDate.now(), cutoff
            );
            if (!navs.isEmpty()) {
                fundMinDates.put(item.schemeOptionId(), navs.get(0).getEffectiveDate());
            } else {
                fundMinDates.put(item.schemeOptionId(), LocalDate.MAX);
            }
        }

        // Determine benchmark min date and observations
        Benchmark bm = benchmarkRepository.findByCode(targetBenchmarkCode).orElse(null);
        LocalDate bmMinDate = LocalDate.of(2021, 1, 15);
        Map<LocalDate, BigDecimal> bmLevelsByDate = new TreeMap<>();
        if (bm != null) {
            Optional<LocalDate> minOpt = benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(bm.getId());
            if (minOpt.isPresent()) {
                bmMinDate = minOpt.get();
            }
            try {
                List<BenchmarkObservation> allBmObs = benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(
                    bm.getId(), LocalDate.of(2026, 12, 31), cutoff
                );
                if (allBmObs != null) {
                    for (BenchmarkObservation o : allBmObs) {
                        bmLevelsByDate.put(o.getEffectiveDate(), o.getIndexLevel());
                    }
                }
            } catch (Exception e) {
                log.debug("Could not load benchmark observations for episode metadata: {}", e.getMessage());
            }
        }

        for (PredefinedEpisodeSpec spec : PREDEFINED_EPISODES) {
            String epBmCode = spec.benchmarkCode() != null ? spec.benchmarkCode() : targetBenchmarkCode;
            boolean available = true;
            String reason = null;

            if (bm == null) {
                available = false;
                reason = "Benchmark '" + targetBenchmarkCode + "' is not registered in the system repository.";
            } else if (spec.startDate().isBefore(bmMinDate)) {
                available = false;
                reason = "Benchmark '" + targetBenchmarkCode + "' observations start on " + bmMinDate +
                    ". Pre-2021 historical data is unavailable without synthetic extrapolation (§4 AGENTS.md).";
            } else {
                for (ResearchPortfolioFundItemDto item : funds) {
                    LocalDate minDate = fundMinDates.get(item.schemeOptionId());
                    if (minDate != null && minDate.isAfter(spec.startDate())) {
                        available = false;
                        reason = "Constituent fund '" + fundNames.get(item.schemeOptionId()) +
                            "' historical observations start on " + minDate + ", after episode start date " + spec.startDate() + ".";
                        break;
                    }
                }
            }

            Integer tradingDays = null;
            BigDecimal bmReturnPct = null;

            if (available) {
                List<LocalDate> episodeBmDates = new ArrayList<>();
                for (LocalDate d : bmLevelsByDate.keySet()) {
                    if (!d.isBefore(spec.startDate()) && !d.isAfter(spec.endDate())) {
                        episodeBmDates.add(d);
                    }
                }
                if (episodeBmDates.size() >= 2) {
                    tradingDays = episodeBmDates.size();
                    BigDecimal startVal = bmLevelsByDate.get(episodeBmDates.get(0));
                    BigDecimal endVal = bmLevelsByDate.get(episodeBmDates.get(episodeBmDates.size() - 1));
                    if (startVal != null && startVal.compareTo(BigDecimal.ZERO) > 0 && endVal != null) {
                        bmReturnPct = endVal.subtract(startVal).divide(startVal, MC).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    tradingDays = (int) ChronoUnit.DAYS.between(spec.startDate(), spec.endDate());
                }
            }

            result.add(new HistoricalMarketEpisodeDto(
                spec.episodeId(), spec.title(), spec.category(),
                spec.startDate(), spec.endDate(), spec.peakTroughConvention(),
                spec.regimeType(), spec.description(), available,
                reason, tradingDays, bmReturnPct,
                epBmCode
            ));
        }

        return result;
    }

    /**
     * Executes deterministic historical market-regime replay and loss analysis.
     */
    @Transactional(readOnly = true)
    public PortfolioStressReplayResponseDto executeStressReplay(PortfolioStressReplayRequestDto request) {
        OffsetDateTime cutoff = request.knowledgeCutoff() != null ? request.knowledgeCutoff() : OffsetDateTime.now();
        List<String> limitations = new ArrayList<>();
        Map<String, String> epistemic = new LinkedHashMap<>();

        epistemic.put("ReturnSeries", "OBSERVED_RETURN_RESULT");
        epistemic.put("PortfolioReconstruction", "HYPOTHETICAL_RECONSTRUCTION (Target-Weight Daily Rebalanced)");
        epistemic.put("LossAttribution", "ESTIMATED_ATTRIBUTION (Weighted Component Return Decomposition)");
        epistemic.put("ModeledStress", "SENSITIVITY_ESTIMATE_ONLY (Linear Empirical OLS Beta Approximation)");

        if (request.funds() == null || request.funds().isEmpty()) {
            throw new IllegalArgumentException("Portfolio funds list cannot be empty.");
        }

        // 1. Resolve Episode or Custom Dates
        LocalDate startDate;
        LocalDate endDate;
        String episodeId = request.episodeId() != null && !request.episodeId().isBlank() ? request.episodeId() : "CUSTOM_PERIOD";
        String episodeTitle = "Custom Historical Period";

        if (request.episodeId() != null && !request.episodeId().isBlank() && !"CUSTOM_PERIOD".equalsIgnoreCase(request.episodeId())) {
            PredefinedEpisodeSpec spec = PREDEFINED_EPISODES.stream()
                .filter(s -> s.episodeId().equalsIgnoreCase(request.episodeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown historical episode ID: " + request.episodeId()));

            List<HistoricalMarketEpisodeDto> catalog = getAvailableEpisodes(request.funds(), cutoff);
            Optional<HistoricalMarketEpisodeDto> epMeta = catalog.stream()
                .filter(e -> e.episodeId().equalsIgnoreCase(spec.episodeId()))
                .findFirst();
            if (epMeta.isPresent() && !epMeta.get().isAvailable()) {
                throw new IllegalArgumentException("Episode '" + request.episodeId() + "' is not available for this portfolio: " + epMeta.get().unavailabilityReason());
            }

            startDate = spec.startDate();
            endDate = spec.endDate();
            episodeTitle = spec.title();
        } else {
            if (request.startDate() == null || request.endDate() == null) {
                throw new IllegalArgumentException("Start date and end date must be provided for custom historical replay.");
            }
            startDate = request.startDate();
            endDate = request.endDate();
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Replay start date (" + startDate + ") cannot be after end date (" + endDate + ").");
        }

        // 2. Normalize Portfolio Fund Weights
        BigDecimal totalRawWeight = BigDecimal.ZERO;
        for (ResearchPortfolioFundItemDto item : request.funds()) {
            if (item.allocationWeight() == null || item.allocationWeight().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Allocation for scheme option #" + item.schemeOptionId() + " cannot be negative or null.");
            }
            totalRawWeight = totalRawWeight.add(item.allocationWeight());
        }

        if (totalRawWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total portfolio allocation must be greater than zero.");
        }

        Map<Long, BigDecimal> normalizedWeights = new HashMap<>();
        Map<Long, SchemeOption> schemeOptions = new HashMap<>();
        for (ResearchPortfolioFundItemDto item : request.funds()) {
            SchemeOption option = schemeOptionRepository.findById(item.schemeOptionId())
                .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + item.schemeOptionId() + " not found."));
            schemeOptions.put(item.schemeOptionId(), option);
            BigDecimal normWeight = item.allocationWeight().divide(totalRawWeight, MC);
            normalizedWeights.put(item.schemeOptionId(), normWeight);
        }

        // 3. Resolve Benchmark
        String benchmarkCode = request.benchmarkCode() != null && !request.benchmarkCode().isBlank()
            ? request.benchmarkCode() : DEFAULT_BENCHMARK;
        Benchmark benchmark = benchmarkRepository.findByCode(benchmarkCode)
            .orElseThrow(() -> new IllegalArgumentException("Benchmark '" + benchmarkCode + "' not found."));

        // 4. Fetch Point-in-Time Observations
        Map<Long, Map<LocalDate, BigDecimal>> fundNavsByDate = new HashMap<>();
        for (ResearchPortfolioFundItemDto item : request.funds()) {
            List<NavObservation> obs = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
                item.schemeOptionId(), endDate, cutoff
            );
            Map<LocalDate, BigDecimal> byDate = new TreeMap<>();
            for (NavObservation o : obs) {
                if (!o.getEffectiveDate().isBefore(startDate) && !o.getEffectiveDate().isAfter(endDate)) {
                    byDate.put(o.getEffectiveDate(), o.getNavValue());
                }
            }
            fundNavsByDate.put(item.schemeOptionId(), byDate);
        }

        List<BenchmarkObservation> bmObs = benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            benchmark.getId(), endDate, cutoff
        );
        Map<LocalDate, BigDecimal> bmByDate = new TreeMap<>();
        for (BenchmarkObservation o : bmObs) {
            if (!o.getEffectiveDate().isBefore(startDate) && !o.getEffectiveDate().isAfter(endDate)) {
                bmByDate.put(o.getEffectiveDate(), o.getIndexLevel());
            }
        }

        // 5. Intersect Common Trading Dates
        Set<LocalDate> commonDates = new TreeSet<>(bmByDate.keySet());
        for (ResearchPortfolioFundItemDto item : request.funds()) {
            commonDates.retainAll(fundNavsByDate.get(item.schemeOptionId()).keySet());
        }

        List<LocalDate> sortedDates = new ArrayList<>(commonDates);
        int alignedCount = sortedDates.size();

        if (alignedCount < 2) {
            return buildUnavailableResponse(
                episodeId, episodeTitle, startDate, endDate, alignedCount,
                "Insufficient common trading dates (found " + alignedCount +
                ", minimum required is 2) between " + startDate + " and " + endDate +
                " across all constituent funds and benchmark '" + benchmarkCode + "'.",
                benchmark.getCode(),
                benchmark.getName(),
                benchmark.getId()
            );
        }

        // 6. Compute Daily Return Series and Cumulative Paths
        List<TrajectoryPointDto> trajectory = new ArrayList<>();
        Map<Long, List<BigDecimal>> fundDailyReturns = new HashMap<>();
        for (ResearchPortfolioFundItemDto item : request.funds()) {
            fundDailyReturns.put(item.schemeOptionId(), new ArrayList<>());
        }
        List<BigDecimal> portDailyReturns = new ArrayList<>();
        List<BigDecimal> bmDailyReturns = new ArrayList<>();

        double portCumMult = 1.0;
        double bmCumMult = 1.0;
        Map<Long, Double> fundCumMults = new HashMap<>();
        for (ResearchPortfolioFundItemDto item : request.funds()) {
            fundCumMults.put(item.schemeOptionId(), 1.0);
        }

        double portPeak = 1.0;
        double bmPeak = 1.0;
        double portMaxDrawdown = 0.0;
        double bmMaxDrawdown = 0.0;
        LocalDate portPeakDate = sortedDates.get(0);
        LocalDate portTroughDate = sortedDates.get(0);
        LocalDate portRecoveryDate = null;

        double portWorstDailyReturn = 0.0;
        LocalDate portWorstDate = sortedDates.get(0);
        double bmWorstDailyReturn = 0.0;

        // Day 0: Baseline (0% cumulative return, 0% drawdown)
        trajectory.add(new TrajectoryPointDto(
            sortedDates.get(0),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        ));

        for (int i = 1; i < alignedCount; i++) {
            LocalDate prevDate = sortedDates.get(i - 1);
            LocalDate currDate = sortedDates.get(i);

            // Fund daily returns
            double portDailyReturnDbl = 0.0;
            for (ResearchPortfolioFundItemDto item : request.funds()) {
                BigDecimal prevNav = fundNavsByDate.get(item.schemeOptionId()).get(prevDate);
                BigDecimal currNav = fundNavsByDate.get(item.schemeOptionId()).get(currDate);
                BigDecimal rJ = currNav.subtract(prevNav).divide(prevNav, MC);
                fundDailyReturns.get(item.schemeOptionId()).add(rJ);

                double fundRDbl = rJ.doubleValue();
                double weightDbl = normalizedWeights.get(item.schemeOptionId()).doubleValue();
                portDailyReturnDbl += weightDbl * fundRDbl;

                double updatedFundMult = fundCumMults.get(item.schemeOptionId()) * (1.0 + fundRDbl);
                fundCumMults.put(item.schemeOptionId(), updatedFundMult);
            }

            // Benchmark daily return
            BigDecimal prevBm = bmByDate.get(prevDate);
            BigDecimal currBm = bmByDate.get(currDate);
            BigDecimal rBm = currBm.subtract(prevBm).divide(prevBm, MC);
            bmDailyReturns.add(rBm);
            double bmRDbl = rBm.doubleValue();

            portDailyReturns.add(BigDecimal.valueOf(portDailyReturnDbl));

            // Track worst days
            if (portDailyReturnDbl < portWorstDailyReturn) {
                portWorstDailyReturn = portDailyReturnDbl;
                portWorstDate = currDate;
            }
            if (bmRDbl < bmWorstDailyReturn) {
                bmWorstDailyReturn = bmRDbl;
            }

            // Update cumulative values
            portCumMult *= (1.0 + portDailyReturnDbl);
            bmCumMult *= (1.0 + bmRDbl);

            // Update peaks and drawdowns
            if (portCumMult > portPeak) {
                portPeak = portCumMult;
                portPeakDate = currDate;
                if (portMaxDrawdown < -0.0001 && portRecoveryDate == null) {
                    portRecoveryDate = currDate;
                }
            }
            double portCurrentDD = (portCumMult - portPeak) / portPeak;
            if (portCurrentDD < portMaxDrawdown) {
                portMaxDrawdown = portCurrentDD;
                portTroughDate = currDate;
                portRecoveryDate = null; // Reset recovery until post-trough peak is surpassed
            }

            if (bmCumMult > bmPeak) {
                bmPeak = bmCumMult;
            }
            double bmCurrentDD = (bmCumMult - bmPeak) / bmPeak;
            if (bmCurrentDD < bmMaxDrawdown) {
                bmMaxDrawdown = bmCurrentDD;
            }

            double portCumReturnPct = (portCumMult - 1.0) * 100.0;
            double bmCumReturnPct = (bmCumMult - 1.0) * 100.0;
            double activeReturnPct = portCumReturnPct - bmCumReturnPct;

            trajectory.add(new TrajectoryPointDto(
                currDate,
                BigDecimal.valueOf(portCumReturnPct).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(bmCumReturnPct).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(activeReturnPct).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(portCurrentDD * 100.0).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(bmCurrentDD * 100.0).setScale(2, RoundingMode.HALF_UP)
            ));
        }

        // 7. Summary Risk Metrics
        BigDecimal portCumReturnFinal = BigDecimal.valueOf((portCumMult - 1.0) * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bmCumReturnFinal = BigDecimal.valueOf((bmCumMult - 1.0) * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal activeReturnFinal = portCumReturnFinal.subtract(bmCumReturnFinal).setScale(2, RoundingMode.HALF_UP);

        BigDecimal portVol = calculateAnnualizedVolatility(portDailyReturns);
        BigDecimal bmVol = calculateAnnualizedVolatility(bmDailyReturns);
        BigDecimal portDownsideDev = calculateDownsideSemideviation(portDailyReturns);

        BigDecimal portMaxDdFinal = BigDecimal.valueOf(portMaxDrawdown * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bmMaxDdFinal = BigDecimal.valueOf(bmMaxDrawdown * 100.0).setScale(2, RoundingMode.HALF_UP);

        BigDecimal worstDayPct = BigDecimal.valueOf(portWorstDailyReturn * 100.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bmWorstDayPct = BigDecimal.valueOf(bmWorstDailyReturn * 100.0).setScale(2, RoundingMode.HALF_UP);

        // 8. Constituent Fund Results, Carino Logarithmic Linking & Loss Contributions
        double rPortTotal = portCumMult - 1.0;
        double kPortTotal = calculateCarinoScalingFactor(rPortTotal);

        Map<Long, Double> carinoContributions = new HashMap<>();
        for (ResearchPortfolioFundItemDto item : request.funds()) {
            carinoContributions.put(item.schemeOptionId(), 0.0);
        }

        int numIntervals = portDailyReturns.size();
        for (int t = 0; t < numIntervals; t++) {
            double rPt = portDailyReturns.get(t).doubleValue();
            double kPt = calculateCarinoScalingFactor(rPt);
            double linkWeight = kPortTotal != 0.0 ? kPt / kPortTotal : 1.0;

            for (ResearchPortfolioFundItemDto item : request.funds()) {
                double wJ = normalizedWeights.get(item.schemeOptionId()).doubleValue();
                double rJt = fundDailyReturns.get(item.schemeOptionId()).get(t).doubleValue();
                double dailyContrib = wJ * rJt;
                double linkedContrib = dailyContrib * linkWeight;
                carinoContributions.put(item.schemeOptionId(), carinoContributions.get(item.schemeOptionId()) + linkedContrib);
            }
        }

        List<ConstituentReplayResultDto> constituentResults = new ArrayList<>();
        Map<Long, BigDecimal> fundBetas = new HashMap<>();
        BigDecimal standaloneSumReturnPct = BigDecimal.ZERO;
        BigDecimal carinoSumReturnPct = BigDecimal.ZERO;

        for (ResearchPortfolioFundItemDto item : request.funds()) {
            SchemeOption option = schemeOptions.get(item.schemeOptionId());
            String name = option.getPlan() != null && option.getPlan().getScheme() != null
                ? option.getPlan().getScheme().getName() : "Fund #" + item.schemeOptionId();
            String amfi = option.getAmfiCode() != null ? option.getAmfiCode() : "N/A";

            BigDecimal normWeight = normalizedWeights.get(item.schemeOptionId());
            BigDecimal normWeightPct = normWeight.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            double cMult = fundCumMults.get(item.schemeOptionId());
            BigDecimal fundCumPct = BigDecimal.valueOf((cMult - 1.0) * 100.0).setScale(2, RoundingMode.HALF_UP);

            // 1. Carino logarithmic linked contribution: C_j
            double cContribDbl = carinoContributions.getOrDefault(item.schemeOptionId(), 0.0) * 100.0;
            BigDecimal carinoContribPct = BigDecimal.valueOf(cContribDbl).setScale(2, RoundingMode.HALF_UP);
            carinoSumReturnPct = carinoSumReturnPct.add(carinoContribPct);

            // 2. Standalone linear contribution: a_j * R_{j,cum}
            BigDecimal standaloneContribPct = normWeight.multiply(fundCumPct).setScale(2, RoundingMode.HALF_UP);
            standaloneSumReturnPct = standaloneSumReturnPct.add(standaloneContribPct);

            List<BigDecimal> fReturns = fundDailyReturns.get(item.schemeOptionId());
            BigDecimal fVol = calculateAnnualizedVolatility(fReturns);
            BigDecimal fMaxDd = calculateSeriesMaxDrawdown(fReturns);
            BigDecimal fWorstDay = calculateWorstDailyReturn(fReturns);

            // Fund Beta to benchmark
            BigDecimal fBeta = calculateOlsBeta(fReturns, bmDailyReturns);
            if (fBeta != null) {
                fundBetas.put(item.schemeOptionId(), fBeta);
            }

            constituentResults.add(new ConstituentReplayResultDto(
                item.schemeOptionId(), name, amfi, normWeightPct,
                fundCumPct, carinoContribPct, standaloneContribPct, fVol, fMaxDd, fWorstDay,
                alignedCount
            ));
        }

        // Compounding & Daily Rebalancing Interaction Residual: R_port - sum(a_j * R_{j,cum})
        BigDecimal compoundingResidualPct = portCumReturnFinal.subtract(standaloneSumReturnPct).setScale(2, RoundingMode.HALF_UP);
        // Attribution Residual: Mathematical Carino linking residual (sum(C_j) - R_port == 0.00% down to machine precision)
        double unroundedCarinoSum = carinoContributions.values().stream().mapToDouble(Double::doubleValue).sum();
        double exactAttrResidual = (unroundedCarinoSum - rPortTotal) * 100.0;
        BigDecimal attributionResidualPct = BigDecimal.valueOf(exactAttrResidual).setScale(2, RoundingMode.HALF_UP);

        // 9. Closest Holdings Exposure Context (with true proximity snapshot selection)
        StressExposureContextDto exposureContext = buildExposureContext(request.funds(), normalizedWeights, endDate, cutoff);

        // 10. Modeled Sensitivity Scenarios (Defensible Linear Beta Shock Approximation)
        BigDecimal portBeta = calculateOlsBeta(portDailyReturns, bmDailyReturns);
        List<ModeledSensitivityScenarioDto> modeledScenarios = buildModeledScenarios(
            request.funds(), normalizedWeights, schemeOptions, portBeta, fundBetas, numIntervals
        );

        // 11. Epistemic Limitations & Disclaimers
        limitations.add("Hypothetical historical reconstruction: Assumes continuous daily rebalancing to target fund allocations (" +
            request.funds().stream().map(f -> schemeOptions.get(f.schemeOptionId()).getPlan().getScheme().getName() + " @ " + normalizedWeights.get(f.schemeOptionId()).multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP) + "%").reduce((a, b) -> a + ", " + b).orElse("") + ").");
        limitations.add("Attribution methodology: Carino multi-period logarithmic linking reconciles daily rebalanced constituent contributions with total portfolio return (attribution residual: " + attributionResidualPct.toPlainString() + "%). The compounding interaction residual (" + compoundingResidualPct.toPlainString() + "%) quantifies the divergence between daily constant-weight rebalancing and a static buy-and-hold portfolio.");
        limitations.add("Transaction costs, exit loads, taxes, cash management drag, and real investor behavior are not represented.");
        limitations.add("Benchmark comparison strictly aligned to common trading days ($N = " + alignedCount + "$) with '" + benchmarkCode + "'.");
        if (exposureContext.comparabilityState().equals("SIGNIFICANT_TEMPORAL_MISMATCH")) {
            limitations.add("Significant temporal distance between closest portfolio holdings disclosure (" +
                exposureContext.observationDate() + ") and stress period end (" + endDate + "): " +
                exposureContext.daysMismatchFromEpisodeEnd() + " days. Security-level causal attribution cannot be established.");
        }
        if (portBeta == null) {
            limitations.add("Modeled sensitivity scenarios gated: Benchmark return variance during replay period is zero or undefined, preventing empirical OLS beta estimation.");
        } else if (numIntervals < 10) {
            limitations.add("Modeled sensitivity scenarios caution: Empirical OLS beta estimated on small sample size (N = " + numIntervals + " return intervals, below typical N >= 10 guideline).");
        }

        return new PortfolioStressReplayResponseDto(
            "HYPOTHETICAL_RECONSTRUCTION",
            episodeId,
            episodeTitle,
            startDate,
            endDate,
            alignedCount,
            portCumReturnFinal,
            bmCumReturnFinal,
            activeReturnFinal,
            portVol,
            bmVol,
            portDownsideDev,
            portMaxDdFinal,
            bmMaxDdFinal,
            portPeakDate,
            portTroughDate,
            portRecoveryDate,
            worstDayPct,
            portWorstDate,
            bmWorstDayPct,
            constituentResults,
            trajectory,
            exposureContext,
            modeledScenarios,
            epistemic,
            limitations,
            "DAILY_REBALANCED_TARGET_WEIGHTS",
            "CARINO_LOGARITHMIC_LINKING",
            standaloneSumReturnPct,
            compoundingResidualPct,
            attributionResidualPct,
            benchmark.getCode(),
            benchmark.getName(),
            benchmark.getId()
        );
    }

    private StressExposureContextDto buildExposureContext(
            List<ResearchPortfolioFundItemDto> funds,
            Map<Long, BigDecimal> normalizedWeights,
            LocalDate asOfDate,
            OffsetDateTime cutoff
    ) {
        LocalDate closestDate = null;
        long minDaysDiff = Long.MAX_VALUE;
        long maxDaysDiff = 0;
        boolean hasAnySnapshot = false;

        Map<String, BigDecimal> sectorWeights = new LinkedHashMap<>();
        BigDecimal largeCapWeight = BigDecimal.ZERO;
        BigDecimal midCapWeight = BigDecimal.ZERO;
        BigDecimal smallCapWeight = BigDecimal.ZERO;
        BigDecimal cashWeight = BigDecimal.ZERO;
        BigDecimal unclassifiedWeight = BigDecimal.ZERO;

        for (ResearchPortfolioFundItemDto item : funds) {
            List<PortfolioSnapshot> snaps = portfolioSnapshotRepository
                .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(
                    item.schemeOptionId(), cutoff
                );
            if (!snaps.isEmpty()) {
                // Find snapshot closest to asOfDate
                PortfolioSnapshot snap = null;
                long bestFundDiff = Long.MAX_VALUE;
                for (PortfolioSnapshot s : snaps) {
                    long diff = Math.abs(ChronoUnit.DAYS.between(s.getPortfolioDate(), asOfDate));
                    if (diff < bestFundDiff) {
                        bestFundDiff = diff;
                        snap = s;
                    } else if (diff == bestFundDiff && snap != null) {
                        // Tie-breaking:
                        // 1. Prefer snapshot on or before asOfDate over future snapshot
                        boolean sPrecedes = !s.getPortfolioDate().isAfter(asOfDate);
                        boolean snapPrecedes = !snap.getPortfolioDate().isAfter(asOfDate);
                        if (sPrecedes && !snapPrecedes) {
                            snap = s;
                        } else if (sPrecedes == snapPrecedes) {
                            // 2. If both precede or both follow, prefer the higher revisionSeq
                            int sRev = s.getRevisionSeq() != null ? s.getRevisionSeq() : 0;
                            int snapRev = snap.getRevisionSeq() != null ? snap.getRevisionSeq() : 0;
                            if (sRev > snapRev) {
                                snap = s;
                            }
                        }
                    }
                }
                if (snap == null) continue;

                hasAnySnapshot = true;
                if (bestFundDiff < minDaysDiff) {
                    minDaysDiff = bestFundDiff;
                    closestDate = snap.getPortfolioDate();
                }
                if (bestFundDiff > maxDaysDiff) {
                    maxDaysDiff = bestFundDiff;
                }

                BigDecimal fundWeight = normalizedWeights.get(item.schemeOptionId());
                List<PortfolioHolding> holdings = portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());

                for (PortfolioHolding h : holdings) {
                    if (h.getSecurity() == null) continue;
                    BigDecimal secWeightInPort = (h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO)
                        .multiply(fundWeight, MC);

                    String sector = h.getSecurity().getSector() != null ? h.getSecurity().getSector() : "Unclassified";
                    sectorWeights.merge(sector, secWeightInPort, BigDecimal::add);

                    // Market Cap
                    String cap = "UNCLASSIFIED";
                    if ("Cash and Cash Equivalents".equalsIgnoreCase(h.getSecurity().getAssetClass())) {
                        cap = "CASH";
                    } else if (classificationRepository != null) {
                        List<SecurityMarketCapClassification> mcapList = classificationRepository.findBySecurityIdAndPit(h.getSecurity().getId(), snap.getPortfolioDate(), cutoff);
                        if (!mcapList.isEmpty() && mcapList.get(0).getMarketCapCategory() != null) {
                            cap = mcapList.get(0).getMarketCapCategory();
                        }
                    }

                    if ("LARGE_CAP".equalsIgnoreCase(cap)) largeCapWeight = largeCapWeight.add(secWeightInPort);
                    else if ("MID_CAP".equalsIgnoreCase(cap)) midCapWeight = midCapWeight.add(secWeightInPort);
                    else if ("SMALL_CAP".equalsIgnoreCase(cap)) smallCapWeight = smallCapWeight.add(secWeightInPort);
                    else if ("CASH".equalsIgnoreCase(cap)) cashWeight = cashWeight.add(secWeightInPort);
                    else unclassifiedWeight = unclassifiedWeight.add(secWeightInPort);
                }
            }
        }

        if (!hasAnySnapshot || closestDate == null) {
            return new StressExposureContextDto(
                null, 0, "NO_HOLDINGS_SNAPSHOT_AVAILABLE",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Collections.emptyList(),
                "No disclosed month-end portfolio holdings snapshots available in repository for this stress period."
            );
        }

        long effectiveDiff = Math.max(minDaysDiff, maxDaysDiff);
        String compState = effectiveDiff > 45 ? "SIGNIFICANT_TEMPORAL_MISMATCH" : "TEMPORALLY_ALIGNED";
        String warning = effectiveDiff > 45
            ? "Holdings disclosure date (" + closestDate + ") is " + effectiveDiff +
              " days from stress period. Underlying holdings during market drawdown likely diverged from this static snapshot."
            : "Observed month-end holdings snapshot near stress period (" + closestDate + "). Reflects disclosed weights.";

        List<SectorExposureContextItemDto> topSectors = new ArrayList<>();
        sectorWeights.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(5)
            .forEach(e -> topSectors.add(new SectorExposureContextItemDto(
                e.getKey(), e.getValue().multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            )));

        return new StressExposureContextDto(
            closestDate,
            effectiveDiff,
            compState,
            largeCapWeight.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP),
            midCapWeight.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP),
            smallCapWeight.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP),
            cashWeight.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP),
            unclassifiedWeight.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP),
            topSectors,
            warning
        );
    }

    private List<ModeledSensitivityScenarioDto> buildModeledScenarios(
            List<ResearchPortfolioFundItemDto> funds,
            Map<Long, BigDecimal> normalizedWeights,
            Map<Long, SchemeOption> schemeOptions,
            BigDecimal portBeta,
            Map<Long, BigDecimal> fundBetas,
            int sampleSize
    ) {
        if (portBeta == null) {
            return Collections.emptyList();
        }

        List<ModeledSensitivityScenarioDto> scenarios = new ArrayList<>();
        List<BigDecimal> shocks = List.of(
            new BigDecimal("-5.00"),
            new BigDecimal("-10.00"),
            new BigDecimal("-15.00")
        );

        List<String> omittedEffects = new ArrayList<>(List.of(
            "Static linear beta approximation: Assumes ΔR = β × ΔR_market without higher-order convexities or tail fatness.",
            "Omits correlation breakdown: Cross-fund correlations typically approach 1.0 during acute market panics.",
            "Excludes liquidity shocks: Does not model bid-ask widening, small-cap illiquidity discounts, or redemption runs.",
            "Omits manager discretion: Fund managers may raise cash or dynamically hedge during prolonged selloffs."
        ));

        if (sampleSize < 10) {
            omittedEffects.add("Small sample estimation: Empirical OLS beta estimated on N = " + sampleSize + " trading days. Estimates carry wide confidence intervals.");
        }

        for (BigDecimal shock : shocks) {
            BigDecimal estLoss = portBeta.multiply(shock).setScale(2, RoundingMode.HALF_UP);
            String label = "Broad Equity Market " + shock.toPlainString() + "% Shock";
            String scId = "SCENARIO_MKT_" + shock.abs().setScale(0, RoundingMode.DOWN) + "PCT";

            List<ConstituentSensitivityDto> constituents = new ArrayList<>();
            for (ResearchPortfolioFundItemDto item : funds) {
                SchemeOption opt = schemeOptions.get(item.schemeOptionId());
                String name = opt.getPlan() != null && opt.getPlan().getScheme() != null
                    ? opt.getPlan().getScheme().getName() : "Fund #" + item.schemeOptionId();
                BigDecimal weightPct = normalizedWeights.get(item.schemeOptionId()).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
                BigDecimal fBeta = fundBetas.getOrDefault(item.schemeOptionId(), portBeta);
                BigDecimal fLoss = fBeta.multiply(shock).setScale(2, RoundingMode.HALF_UP);
                BigDecimal fContrib = normalizedWeights.get(item.schemeOptionId()).multiply(fLoss).setScale(2, RoundingMode.HALF_UP);

                constituents.add(new ConstituentSensitivityDto(
                    item.schemeOptionId(), name, weightPct, fBeta, fLoss, fContrib
                ));
            }

            scenarios.add(new ModeledSensitivityScenarioDto(
                scId, label, shock, estLoss, portBeta,
                "SENSITIVITY_ESTIMATE_ONLY",
                constituents, omittedEffects
            ));
        }

        return scenarios;
    }

    private PortfolioStressReplayResponseDto buildUnavailableResponse(
            String episodeId, String title, LocalDate startDate, LocalDate endDate,
            int alignedCount, String reason,
            String benchmarkCode, String benchmarkName, Long benchmarkId
    ) {
        return new PortfolioStressReplayResponseDto(
            "UNAVAILABLE_EVIDENCE",
            episodeId,
            title,
            startDate,
            endDate,
            alignedCount,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO,
            null, null, null,
            BigDecimal.ZERO, null, BigDecimal.ZERO,
            Collections.emptyList(),
            Collections.emptyList(),
            new StressExposureContextDto(null, 0, "NO_HOLDINGS_SNAPSHOT_AVAILABLE",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Collections.emptyList(), reason),
            Collections.emptyList(),
            Map.of("Status", "UNAVAILABLE_EVIDENCE", "Reason", reason),
            List.of(reason),
            "DAILY_REBALANCED_TARGET_WEIGHTS",
            "CARINO_LOGARITHMIC_LINKING",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            benchmarkCode,
            benchmarkName,
            benchmarkId
        );
    }

    private BigDecimal calculateAnnualizedVolatility(List<BigDecimal> returns) {
        if (returns == null || returns.size() < 2) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int n = returns.size();
        double sum = 0.0;
        for (BigDecimal r : returns) sum += r.doubleValue();
        double mean = sum / n;
        double sumSq = 0.0;
        for (BigDecimal r : returns) {
            double diff = r.doubleValue() - mean;
            sumSq += diff * diff;
        }
        double variance = sumSq / (n - 1);
        double dailyVol = Math.sqrt(variance);
        double annVol = dailyVol * Math.sqrt(252) * 100.0;
        return BigDecimal.valueOf(annVol).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDownsideSemideviation(List<BigDecimal> returns) {
        if (returns == null || returns.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int n = returns.size();
        double sumSqDownside = 0.0;
        for (BigDecimal r : returns) {
            double v = r.doubleValue();
            if (v < 0.0) sumSqDownside += v * v;
        }
        double semiVar = sumSqDownside / n;
        double annDownside = Math.sqrt(semiVar) * Math.sqrt(252) * 100.0;
        return BigDecimal.valueOf(annDownside).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSeriesMaxDrawdown(List<BigDecimal> returns) {
        if (returns == null || returns.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        double cumMult = 1.0;
        double peak = 1.0;
        double maxDd = 0.0;
        for (BigDecimal r : returns) {
            cumMult *= (1.0 + r.doubleValue());
            if (cumMult > peak) peak = cumMult;
            double dd = (cumMult - peak) / peak;
            if (dd < maxDd) maxDd = dd;
        }
        return BigDecimal.valueOf(maxDd * 100.0).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateWorstDailyReturn(List<BigDecimal> returns) {
        if (returns == null || returns.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        double worst = 0.0;
        for (BigDecimal r : returns) {
            double v = r.doubleValue();
            if (v < worst) worst = v;
        }
        return BigDecimal.valueOf(worst * 100.0).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateOlsBeta(List<BigDecimal> assetReturns, List<BigDecimal> bmReturns) {
        if (assetReturns == null || bmReturns == null || assetReturns.size() != bmReturns.size() || assetReturns.size() < 2) {
            return null;
        }
        int n = assetReturns.size();
        double sumA = 0.0, sumB = 0.0;
        for (int i = 0; i < n; i++) {
            sumA += assetReturns.get(i).doubleValue();
            sumB += bmReturns.get(i).doubleValue();
        }
        double meanA = sumA / n;
        double meanB = sumB / n;

        double cov = 0.0;
        double varB = 0.0;
        for (int i = 0; i < n; i++) {
            double diffA = assetReturns.get(i).doubleValue() - meanA;
            double diffB = bmReturns.get(i).doubleValue() - meanB;
            cov += diffA * diffB;
            varB += diffB * diffB;
        }

        if (varB <= 1e-12) return null;
        double beta = cov / varB;
        return BigDecimal.valueOf(beta).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes the Carino logarithmic linking scaling factor for multi-period arithmetic attribution:
     * k(r) = ln(1 + r) / r (with limit k(0) = 1.0).
     * Uses Math.log1p(r) / r for IEEE 754 double precision stability near zero,
     * and guards extreme loss / invalid domains.
     */
    public static double calculateCarinoScalingFactor(double r) {
        if (Double.isNaN(r) || Double.isInfinite(r)) {
            return 1.0;
        }
        if (Math.abs(r) < 1e-15) {
            return 1.0;
        }
        if (1.0 + r <= 1e-12) {
            // Extreme loss or invalid domain (r <= -1.0 + 1e-12)
            return 1.0;
        }
        return Math.log1p(r) / r;
    }
}
