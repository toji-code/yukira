package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.attribution.*;
import com.yukira.backend.dto.portfolio.lookthrough.*;
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
 * Service orchestrating Portfolio Risk Attribution (Phase 2O).
 * Implements deterministic covariance-based volatility decomposition:
 * RC_i = w_i * (Sigma * w)_i / sigma_p
 *
 * Epistemic & Methodological Guardrails (AGENTS.md):
 * 1. Zero frontend financial calculations: all volatility arithmetic, covariances,
 *    Euler risk contributions, down-side deviations, and betas are computed deterministically.
 * 2. Exact Euler Reconciliation: Sum of component risk contributions equals portfolio volatility.
 * 3. Valid Negative Contributions: Diversification effects are preserved, never clamped to zero.
 * 4. Zero Renormalization: Partial holdings disclosures and unclassified assets are explicitly disclosed.
 * 5. Bitemporal PIT: effectiveDate <= asOfDate AND availabilityTime <= knowledgeCutoff.
 * 6. No LLM: Interpretations are rule-based, deterministic, and purely informational without advice.
 */
@Service
@SuppressWarnings("null")
public class PortfolioRiskAttributionService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRiskAttributionService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_BENCHMARK = "NIFTY_500_TRI";
    public static final String METHODOLOGY_VERSION = "RISK_ATTRIBUTION_V1";
    public static final int ANNUALIZATION_FACTOR = 252;

    private final SchemeOptionRepository schemeOptionRepository;
    private final MultiFundLookThroughService lookThroughService;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final NavObservationRepository navObservationRepository;

    @Autowired
    public PortfolioRiskAttributionService(
        SchemeOptionRepository schemeOptionRepository,
        MultiFundLookThroughService lookThroughService,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.lookThroughService = lookThroughService;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.navObservationRepository = navObservationRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioRiskAttributionResponseDto calculateRiskAttribution(PortfolioRiskAttributionRequestDto request) {
        OffsetDateTime cutoff = request != null && request.knowledgeCutoff() != null
            ? request.knowledgeCutoff() : OffsetDateTime.now();
        LocalDate asOf = request != null && request.asOfDate() != null
            ? request.asOfDate() : cutoff.toLocalDate();
        String bmCode = request != null && request.benchmarkCode() != null && !request.benchmarkCode().isBlank()
            ? request.benchmarkCode().trim() : DEFAULT_BENCHMARK;

        List<String> warnings = new ArrayList<>();
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("Methodology", "Euler Covariance-Based Volatility Decomposition (Phase 2O)");
        notes.put("DecompositionFormula", "RC_i = w_i * (Sigma * w)_i / sigma_p");
        notes.put("AnnualizationConvention", "252 Trading Days (Square Root of Time)");
        notes.put("EulerIdentity", "SUM(RC_i) = sigma_p (exact mathematical reconciliation)");
        notes.put("Disclaimers", "Historical risk decomposition is point-in-time empirical evidence; not a loss forecast or financial advice.");

        if (request == null || request.funds() == null || request.funds().isEmpty()) {
            return buildEmptyResponse("PORTFOLIO_EMPTY", asOf, cutoff, bmCode, warnings, notes, "No constituent funds provided.");
        }

        // 1. Resolve Schemes and Weights
        BigDecimal totalInputWeight = BigDecimal.ZERO;
        Map<Long, BigDecimal> rawWeights = new LinkedHashMap<>();
        Map<Long, SchemeOption> schemeMap = new LinkedHashMap<>();

        for (ResearchPortfolioFundItemDto item : request.funds()) {
            if (item.schemeOptionId() == null || item.allocationWeight() == null || item.allocationWeight().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Optional<SchemeOption> opt = schemeOptionRepository.findById(item.schemeOptionId());
            if (opt.isPresent()) {
                schemeMap.put(item.schemeOptionId(), opt.get());
                rawWeights.put(item.schemeOptionId(), item.allocationWeight());
                totalInputWeight = totalInputWeight.add(item.allocationWeight());
            } else {
                warnings.add("Scheme option ID " + item.schemeOptionId() + " not found in system repository.");
            }
        }

        if (schemeMap.isEmpty() || totalInputWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return buildEmptyResponse("INVALID_CONSTITUENTS", asOf, cutoff, bmCode, warnings, notes, "No valid constituent funds with positive allocation.");
        }

        // Fractional weights summing to 1.0
        List<Long> fundIds = new ArrayList<>(schemeMap.keySet());
        int M = fundIds.size();
        double[] w = new double[M];
        for (int i = 0; i < M; i++) {
            w[i] = rawWeights.get(fundIds.get(i)).divide(totalInputWeight, MC).doubleValue();
        }

        // 2. Fetch Authoritative Benchmark Observations
        Benchmark benchmark = benchmarkRepository.findByCode(bmCode)
            .or(() -> benchmarkRepository.findByCode("NIFTY_500_TRI"))
            .or(() -> benchmarkRepository.findByCode("NIFTY_50"))
            .or(() -> benchmarkRepository.findAll().stream().findFirst())
            .orElse(null);

        String activeBmCode = benchmark != null ? benchmark.getCode() : bmCode;
        String activeBmName = benchmark != null ? benchmark.getName() : "Nifty 500 Total Returns Index";

        Map<LocalDate, BigDecimal> bmValues = new HashMap<>();
        if (benchmark != null) {
            List<BenchmarkObservation> bmObs = benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(
                benchmark.getId(), asOf, cutoff
            );
            for (BenchmarkObservation bo : bmObs) {
                if (bo.getEffectiveDate() != null && bo.getIndexLevel() != null) {
                    bmValues.put(bo.getEffectiveDate(), bo.getIndexLevel());
                }
            }
        }

        // 3. Fetch Authoritative Constituent NAV Observations
        Map<Long, Map<LocalDate, BigDecimal>> fundNavs = new LinkedHashMap<>();
        for (Long id : fundIds) {
            List<NavObservation> navList = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(id, asOf, cutoff);
            Map<LocalDate, BigDecimal> navMap = new HashMap<>();
            for (NavObservation no : navList) {
                if (no.getEffectiveDate() != null && no.getNavValue() != null && no.getNavValue().compareTo(BigDecimal.ZERO) > 0) {
                    navMap.put(no.getEffectiveDate(), no.getNavValue());
                }
            }
            fundNavs.put(id, navMap);
        }

        // 4. Align Common Synchronized Trading Dates
        Set<LocalDate> commonDates = null;
        for (Map<LocalDate, BigDecimal> navMap : fundNavs.values()) {
            if (commonDates == null) {
                commonDates = new HashSet<>(navMap.keySet());
            } else {
                commonDates.retainAll(navMap.keySet());
            }
        }
        if (commonDates == null) {
            commonDates = Collections.emptySet();
        }
        if (!bmValues.isEmpty()) {
            commonDates.retainAll(bmValues.keySet());
        }

        List<LocalDate> sortedDates = new ArrayList<>(commonDates);
        Collections.sort(sortedDates);

        if (sortedDates.size() < 20) {
            warnings.add("Insufficient synchronized trading days (" + sortedDates.size() + " days found, minimum 20 required). Return risk attribution cannot be calculated reliably.");
            return buildEmptyResponse("INSUFFICIENT_RETURN_OBSERVATIONS", asOf, cutoff, activeBmCode, warnings, notes,
                "Insufficient synchronized return observations (" + sortedDates.size() + " days).");
        }

        // 5. Compute Synchronized Daily Returns
        int nDates = sortedDates.size();
        List<Double> portReturns = new ArrayList<>();
        List<Double> bmReturns = new ArrayList<>();
        Map<Long, List<Double>> fundReturns = new LinkedHashMap<>();
        for (Long id : fundIds) {
            fundReturns.put(id, new ArrayList<>());
        }

        for (int k = 1; k < nDates; k++) {
            LocalDate prevDate = sortedDates.get(k - 1);
            LocalDate currDate = sortedDates.get(k);

            // Skip interval if abnormal gap > 7 calendar days
            if (ChronoUnit.DAYS.between(prevDate, currDate) > 7) {
                continue;
            }

            boolean validInterval = true;
            double[] rStep = new double[M];
            for (int i = 0; i < M; i++) {
                Long id = fundIds.get(i);
                BigDecimal pNav = fundNavs.get(id).get(prevDate);
                BigDecimal cNav = fundNavs.get(id).get(currDate);
                if (pNav == null || cNav == null || pNav.compareTo(BigDecimal.ZERO) <= 0) {
                    validInterval = false;
                    break;
                }
                rStep[i] = cNav.subtract(pNav).divide(pNav, 10, RoundingMode.HALF_UP).doubleValue();
            }

            if (!validInterval) continue;

            double pDailyRet = 0.0;
            for (int i = 0; i < M; i++) {
                fundReturns.get(fundIds.get(i)).add(rStep[i]);
                pDailyRet += w[i] * rStep[i];
            }
            portReturns.add(pDailyRet);

            double bmDailyRet = 0.0;
            if (!bmValues.isEmpty() && bmValues.containsKey(prevDate) && bmValues.containsKey(currDate)) {
                BigDecimal pBm = bmValues.get(prevDate);
                BigDecimal cBm = bmValues.get(currDate);
                if (pBm != null && cBm != null && pBm.compareTo(BigDecimal.ZERO) > 0) {
                    bmDailyRet = cBm.subtract(pBm).divide(pBm, 10, RoundingMode.HALF_UP).doubleValue();
                }
            }
            bmReturns.add(bmDailyRet);
        }

        int K = portReturns.size();
        if (K < 20) {
            warnings.add("Insufficient valid consecutive return intervals (" + K + " intervals after filtering calendar gaps).");
            return buildEmptyResponse("INSUFFICIENT_RETURN_OBSERVATIONS", asOf, cutoff, activeBmCode, warnings, notes,
                "Insufficient valid return intervals (" + K + " intervals).");
        }

        LocalDate startDate = sortedDates.get(0);
        LocalDate endDate = sortedDates.get(nDates - 1);

        // 6. Covariance Matrix & Volatility Calculations
        // Compute means
        double[] meanR = new double[M];
        for (int i = 0; i < M; i++) {
            List<Double> rets = fundReturns.get(fundIds.get(i));
            double sum = 0.0;
            for (double r : rets) sum += r;
            meanR[i] = sum / K;
        }

        double sumPort = 0.0;
        for (double r : portReturns) sumPort += r;
        double meanPort = sumPort / K;

        double sumBm = 0.0;
        for (double r : bmReturns) sumBm += r;
        double meanBm = sumBm / K;

        // Daily Covariance Matrix: Cov(i, j) = 1/(K-1) * sum((R_i - mean_i)*(R_j - mean_j))
        // Annualized Covariance Matrix: Sigma(i, j) = 252 * Cov_daily(i, j)
        double[][] sigma = new double[M][M];
        for (int i = 0; i < M; i++) {
            List<Double> rI = fundReturns.get(fundIds.get(i));
            for (int j = i; j < M; j++) {
                List<Double> rJ = fundReturns.get(fundIds.get(j));
                double acc = 0.0;
                for (int t = 0; t < K; t++) {
                    acc += (rI.get(t) - meanR[i]) * (rJ.get(t) - meanR[j]);
                }
                double dailyCov = acc / (K - 1);
                double annCov = dailyCov * ANNUALIZATION_FACTOR;
                sigma[i][j] = annCov;
                sigma[j][i] = annCov;
            }
        }

        // Sigma * w vector: (Sigma * w)_i = sum_j (Sigma_{i, j} * w_j)
        double[] sigmaW = new double[M];
        double portVar = 0.0;
        for (int i = 0; i < M; i++) {
            double dot = 0.0;
            for (int j = 0; j < M; j++) {
                dot += sigma[i][j] * w[j];
            }
            sigmaW[i] = dot;
            portVar += w[i] * dot;
        }

        if (portVar <= 1e-12) {
            warnings.add("Portfolio annualized variance is zero or negligible. Risk attribution is undefined.");
            return buildEmptyResponse("ZERO_VARIANCE", asOf, cutoff, activeBmCode, warnings, notes,
                "Portfolio annualized volatility is zero.");
        }

        double portVolDec = Math.sqrt(portVar); // annualized decimal volatility
        double portVolPct = portVolDec * 100.0; // annualized percentage volatility

        // Downside Semideviation
        double downAcc = 0.0;
        for (double r : portReturns) {
            if (r < 0.0) {
                downAcc += Math.pow(r, 2);
            }
        }
        double portDownsideSemideviationPct = Math.sqrt((downAcc / (K - 1)) * ANNUALIZATION_FACTOR) * 100.0;

        // Max Drawdown
        double portMaxDrawdownPct = computeMaxDrawdown(portReturns);

        // Portfolio Beta
        double varBmAcc = 0.0;
        double covPortBmAcc = 0.0;
        for (int t = 0; t < K; t++) {
            varBmAcc += Math.pow(bmReturns.get(t) - meanBm, 2);
            covPortBmAcc += (portReturns.get(t) - meanPort) * (bmReturns.get(t) - meanBm);
        }
        double varBm = varBmAcc / (K - 1);
        double covPortBm = covPortBmAcc / (K - 1);
        Double portBeta = varBm > 1e-12 ? (covPortBm / varBm) : null;

        // 7. SECTION A — Portfolio Risk Overview Metrics
        List<RiskOverviewMetricDto> overviewMetrics = new ArrayList<>();
        overviewMetrics.add(new RiskOverviewMetricDto(
            "ANNUALIZED_VOLATILITY",
            "Annualized Portfolio Volatility",
            BigDecimal.valueOf(portVolPct).setScale(2, RoundingMode.HALF_UP),
            String.format(Locale.US, "%.2f%%", portVolPct),
            "%",
            "Annualized standard deviation of daily portfolio returns (scaled by sqrt(252)). Measures aggregate return dispersion.",
            "AVAILABLE"
        ));
        overviewMetrics.add(new RiskOverviewMetricDto(
            "MAX_DRAWDOWN",
            "Maximum Drawdown",
            BigDecimal.valueOf(portMaxDrawdownPct).setScale(2, RoundingMode.HALF_UP),
            String.format(Locale.US, "%.2f%%", portMaxDrawdownPct),
            "%",
            "Maximum peak-to-trough cumulative capital decline over the synchronized historical return window.",
            "AVAILABLE"
        ));
        overviewMetrics.add(new RiskOverviewMetricDto(
            "PORTFOLIO_BETA",
            "Portfolio Beta (" + activeBmCode + ")",
            portBeta != null ? BigDecimal.valueOf(portBeta).setScale(2, RoundingMode.HALF_UP) : null,
            portBeta != null ? String.format(Locale.US, "%.2f", portBeta) : "N/A",
            "x",
            "Systematic sensitivity of daily portfolio returns relative to " + activeBmName + " benchmark returns.",
            portBeta != null ? "AVAILABLE" : "DATA_UNAVAILABLE"
        ));
        overviewMetrics.add(new RiskOverviewMetricDto(
            "DOWNSIDE_SEMIDEVIATION",
            "Downside Semideviation",
            BigDecimal.valueOf(portDownsideSemideviationPct).setScale(2, RoundingMode.HALF_UP),
            String.format(Locale.US, "%.2f%%", portDownsideSemideviationPct),
            "%",
            "Annualized volatility considering only negative daily return observations. Isolates pure downside risk.",
            "AVAILABLE"
        ));
        overviewMetrics.add(new RiskOverviewMetricDto(
            "OBSERVATION_COUNT",
            "Aligned Daily Observations",
            BigDecimal.valueOf(K),
            String.valueOf(K) + " days",
            "days",
            "Number of common synchronized market trading days aligned across all constituent mutual funds and benchmark.",
            "AVAILABLE"
        ));
        overviewMetrics.add(new RiskOverviewMetricDto(
            "CALCULATION_WINDOW",
            "Calculation Return Window",
            null,
            startDate.toString() + " to " + endDate.toString(),
            "date-range",
            "Point-in-time historical evaluation window over which NAV return observations were aligned and analyzed.",
            "AVAILABLE"
        ));

        // 8. SECTION B — Fund-Level Volatility Contributions
        // Euler Volatility Decomposition:
        // RC_i = w_i * (Sigma * w)_i / sigma_p (percentage points)
        // %RC_i = RC_i / sigma_p * 100%
        // MC_i = (Sigma * w)_i / sigma_p (marginal volatility contribution)
        List<FundRiskContributionDto> fundContributions = new ArrayList<>();
        double sumRc = 0.0;
        double sumPctRc = 0.0;

        for (int i = 0; i < M; i++) {
            Long id = fundIds.get(i);
            SchemeOption opt = schemeMap.get(id);

            double fundVar = sigma[i][i];
            double fundVolPct = Math.sqrt(Math.max(0.0, fundVar)) * 100.0;

            // Marginal Contribution to Risk: MC_i = (Sigma * w)_i / sigma_p (in decimal, then * 100)
            double mcDec = sigmaW[i] / portVolDec;
            double mcPct = mcDec * 100.0;

            // Risk Contribution: RC_i = w_i * MC_i (in percentage points)
            double rcPct = w[i] * mcPct;
            sumRc += rcPct;

            // Percentage Contribution to Risk: %RC_i = RC_i / sigma_p * 100%
            double pctRc = (rcPct / portVolPct) * 100.0;
            sumPctRc += pctRc;

            boolean isNeg = rcPct < -1e-6;
            // Diversification dampener: fund's risk contribution is less than its capital proportion of volatility
            boolean isDampener = rcPct < (w[i] * portVolPct);

            String interpretation;
            if (isNeg) {
                interpretation = String.format(Locale.US,
                    "Active volatility dampener: exhibits negative covariance with portfolio, reducing aggregate risk by %.2f%% points.",
                    Math.abs(rcPct));
            } else if (isDampener) {
                interpretation = String.format(Locale.US,
                    "Diversifying holding: contributes %.1f%% of portfolio risk while holding %.1f%% of capital allocation.",
                    pctRc, w[i] * 100.0);
            } else {
                interpretation = String.format(Locale.US,
                    "Primary risk driver: accounts for %.1f%% of portfolio risk with %.1f%% of capital allocation.",
                    pctRc, w[i] * 100.0);
            }

            fundContributions.add(new FundRiskContributionDto(
                id,
                opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getName() : "Fund " + id,
                opt.getAmfiCode(),
                opt.getPlan() != null ? opt.getPlan().getPlanType() : "DIRECT",
                BigDecimal.valueOf(w[i] * 100.0).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(w[i]).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(fundVolPct).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(mcPct).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(rcPct).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(pctRc).setScale(2, RoundingMode.HALF_UP),
                isNeg,
                isDampener,
                BigDecimal.valueOf(sigmaW[i] * 10000.0).setScale(4, RoundingMode.HALF_UP),
                interpretation
            ));
        }

        // Euler Reconciliation
        double reconDiff = Math.abs(sumRc - portVolPct);
        boolean isReconciled = reconDiff < 0.01; // within normal floating-point decimal rounding

        RiskReconciliationDto reconciliation = new RiskReconciliationDto(
            BigDecimal.valueOf(portVolPct).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(sumRc).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(reconDiff).setScale(4, RoundingMode.HALF_UP),
            BigDecimal.valueOf(sumPctRc).setScale(2, RoundingMode.HALF_UP),
            isReconciled,
            isReconciled ? "EXACT_EULER_RECONCILED" : "ROUNDING_TOLERANCE_ACCEPTED",
            "SUM(RC_i) = SUM(w_i * (Sigma * w)_i / sigma_p) = sigma_p"
        );

        // 9. SECTION C — Portfolio Look-Through Exposure Context
        RiskExposureContextDto exposureContext = buildExposureContext(request, asOf, cutoff);

        // 10. SECTION D — Risk Interpretation and Due-Diligence Investigation Panel
        RiskInterpretationPanelDto interpretationPanel = buildInterpretationPanel(
            fundContributions, portVolPct, K, startDate, endDate, activeBmCode, activeBmName, exposureContext
        );

        return new PortfolioRiskAttributionResponseDto(
            "AVAILABLE",
            asOf,
            cutoff,
            startDate,
            endDate,
            K,
            ANNUALIZATION_FACTOR,
            "252 Trading Days (Square Root of Time)",
            activeBmCode,
            activeBmName,
            overviewMetrics,
            fundContributions,
            reconciliation,
            exposureContext,
            interpretationPanel,
            warnings,
            notes
        );
    }

    private RiskExposureContextDto buildExposureContext(
        PortfolioRiskAttributionRequestDto request,
        LocalDate asOf,
        OffsetDateTime cutoff
    ) {
        if (lookThroughService == null || request == null || request.funds() == null || request.funds().isEmpty()) {
            return buildFallbackExposureContext();
        }

        try {
            MultiFundLookThroughRequestDto ltReq = new MultiFundLookThroughRequestDto(
                request.funds(), asOf, cutoff, "RAW"
            );
            MultiFundLookThroughResponseDto ltResp = lookThroughService.calculateMultiFundLookThrough(ltReq);
            if (ltResp == null) {
                return buildFallbackExposureContext();
            }

            List<AttributionSectorExposureDto> sectors = new ArrayList<>();
            if (ltResp.consolidatedSectors() != null) {
                for (ConsolidatedSectorExposureDto s : ltResp.consolidatedSectors()) {
                    Map<String, BigDecimal> fundContr = new LinkedHashMap<>();
                    if (s.fundContributions() != null) {
                        for (FundSectorContributionDto fc : s.fundContributions()) {
                            fundContr.put(fc.fundName(), fc.effectiveContributionPct());
                        }
                    }
                    sectors.add(new AttributionSectorExposureDto(s.macroSector(), s.consolidatedWeightPct(), fundContr));
                }
            }

            List<AttributionMarketCapExposureDto> marketCaps = new ArrayList<>();
            if (ltResp.consolidatedMarketCap() != null) {
                for (ConsolidatedMarketCapExposureDto m : ltResp.consolidatedMarketCap()) {
                    marketCaps.add(new AttributionMarketCapExposureDto(m.category(), m.consolidatedWeightPct(), Collections.emptyMap()));
                }
            }

            List<AttributionSecurityExposureDto> topSecurities = new ArrayList<>();
            if (ltResp.consolidatedSecurities() != null) {
                int limit = Math.min(10, ltResp.consolidatedSecurities().size());
                for (int i = 0; i < limit; i++) {
                    ConsolidatedSecurityExposureDto sec = ltResp.consolidatedSecurities().get(i);
                    topSecurities.add(new AttributionSecurityExposureDto(
                        sec.canonicalName(), sec.isin(), sec.sector(), sec.marketCapCategory(), sec.consolidatedWeightPct(), sec.fundCount()
                    ));
                }
            }

            BigDecimal equityCoverage = ltResp.concentrationMetrics() != null && ltResp.concentrationMetrics().disclosedEquityCoveragePct() != null
                ? ltResp.concentrationMetrics().disclosedEquityCoveragePct() : BigDecimal.ZERO;
            BigDecimal unclassified = ltResp.concentrationMetrics() != null && ltResp.concentrationMetrics().unclassifiedWeightPct() != null
                ? ltResp.concentrationMetrics().unclassifiedWeightPct() : BigDecimal.ZERO;
            BigDecimal cashAndDebt = ltResp.concentrationMetrics() != null && ltResp.concentrationMetrics().cashAndEquivalentsWeightPct() != null
                ? ltResp.concentrationMetrics().cashAndEquivalentsWeightPct() : BigDecimal.ZERO;

            List<String> disclaimers = List.of(
                "Holdings exposures measure direct capital allocation weights, NOT volatility contributions. A sector with high allocation may have low risk contribution if its underlying securities have low covariance.",
                "Zero-Renormalization Rule: Incomplete holdings disclosures and cash/derivatives balances are explicitly isolated (" +
                    String.format(Locale.US, "%.1f%% unclassified/cash", unclassified.add(cashAndDebt).doubleValue()) + "), never silently scaled to 100%."
            );

            return new RiskExposureContextDto(
                sectors,
                marketCaps,
                topSecurities,
                equityCoverage,
                unclassified,
                cashAndDebt,
                disclaimers
            );
        } catch (Exception e) {
            log.warn("Failed to build look-through exposure context for risk attribution: {}", e.getMessage());
            return buildFallbackExposureContext();
        }
    }

    private RiskExposureContextDto buildFallbackExposureContext() {
        return new RiskExposureContextDto(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            BigDecimal.ZERO,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(100),
            List.of("Holdings exposure context unavailable for the selected portfolio configuration.")
        );
    }

    private RiskInterpretationPanelDto buildInterpretationPanel(
        List<FundRiskContributionDto> fundContributions,
        double portVolPct,
        int K,
        LocalDate startDate,
        LocalDate endDate,
        String bmCode,
        String bmName,
        RiskExposureContextDto exposureContext
    ) {
        List<String> primaryDrivers = new ArrayList<>();
        List<String> divInsights = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        List<String> prompts = new ArrayList<>();

        // Sort by percentage risk contribution descending
        List<FundRiskContributionDto> sorted = new ArrayList<>(fundContributions);
        sorted.sort((a, b) -> b.percentageRiskContribution().compareTo(a.percentageRiskContribution()));

        // Risk HHI: sum((%RC_i)^2)
        double riskHhi = 0.0;
        for (FundRiskContributionDto f : sorted) {
            double pct = f.percentageRiskContribution().doubleValue();
            riskHhi += Math.pow(pct, 2);
        }

        // Primary risk drivers
        if (!sorted.isEmpty()) {
            FundRiskContributionDto top = sorted.get(0);
            primaryDrivers.add(String.format(Locale.US,
                "%s is the primary risk contributor, generating %.1f%% of total portfolio volatility (%s) with a %.1f%% capital weight.",
                top.fundName(), top.percentageRiskContribution().doubleValue(),
                top.volatilityContributionPct() + "% points", top.allocationWeightPct().doubleValue()
            ));
            if (sorted.size() > 1) {
                FundRiskContributionDto second = sorted.get(1);
                double combinedRisk = top.percentageRiskContribution().add(second.percentageRiskContribution()).doubleValue();
                double combinedCap = top.allocationWeightPct().add(second.allocationWeightPct()).doubleValue();
                primaryDrivers.add(String.format(Locale.US,
                    "Top 2 funds jointly account for %.1f%% of portfolio risk compared to %.1f%% of portfolio capital.",
                    combinedRisk, combinedCap
                ));
            }
        }

        // Diversification insights & negative contributions
        boolean foundNegative = false;
        for (FundRiskContributionDto f : sorted) {
            if (f.isNegativeContribution()) {
                foundNegative = true;
                divInsights.add(String.format(Locale.US,
                    "%s demonstrates an empirical negative risk contribution of %.2f%% points. Its negative covariance with other portfolio assets actively dampens overall portfolio volatility.",
                    f.fundName(), f.volatilityContributionPct().doubleValue()
                ));
            } else if (f.isDiversificationDampener()) {
                divInsights.add(String.format(Locale.US,
                    "%s provides beneficial risk dilution: its risk share (%.1f%%) is substantially lower than its capital weight (%.1f%%) due to moderate constituent covariance.",
                    f.fundName(), f.percentageRiskContribution().doubleValue(), f.allocationWeightPct().doubleValue()
                ));
            }
        }

        if (!foundNegative) {
            divInsights.add("All constituent funds exhibit positive risk contributions, indicating positive return covariance across all active equity holdings.");
        }

        // Concentration assessment
        String concAssessment;
        if (riskHhi > 5000.0) {
            concAssessment = String.format(Locale.US,
                "High Risk Concentration (Risk HHI: %.0f). Portfolio volatility is heavily dominated by a single constituent fund.",
                riskHhi);
        } else if (riskHhi > 3000.0) {
            concAssessment = String.format(Locale.US,
                "Moderate Risk Concentration (Risk HHI: %.0f). Top constituents generate the majority of portfolio volatility.",
                riskHhi);
        } else {
            concAssessment = String.format(Locale.US,
                "Balanced Risk Distribution (Risk HHI: %.0f). Volatility is evenly distributed across constituent funds.",
                riskHhi);
        }

        // Coverage limitations
        limitations.add(String.format(Locale.US,
            "Calculation window covers %d aligned trading days from %s to %s. Periods before %s are not reflected in this volatility decomposition.",
            K, startDate, endDate, startDate
        ));
        limitations.add(String.format(Locale.US,
            "Disclosed equity holdings account for %.1f%% of portfolio value. The remaining %.1f%% is unclassified, cash, or derivatives.",
            exposureContext.disclosedEquityCoveragePct().doubleValue(), exposureContext.unclassifiedWeightPct().doubleValue()
        ));
        limitations.add("Beta sensitivity is measured against " + bmName + " (" + bmCode + ") over the aligned historical trading window.");

        // Actionable due-diligence prompts
        prompts.add("Inspect pairwise return correlations to see if the top risk contributors move synchronously during market stress.");
        prompts.add("Examine whether high volatility contribution is driven by high standalone fund volatility or disproportionate portfolio allocation.");
        prompts.add("Review underlying top security overlap to ensure shared equity positions (e.g. large banking stocks) are not quietly concentrating risk.");
        prompts.add("Test simulated candidate reallocations in the Candidate Simulation or Scenario Lab tab to observe volatility impact.");

        return new RiskInterpretationPanelDto(
            primaryDrivers,
            divInsights,
            concAssessment,
            BigDecimal.valueOf(riskHhi).setScale(0, RoundingMode.HALF_UP),
            limitations,
            prompts
        );
    }

    private double computeMaxDrawdown(List<Double> returns) {
        double peak = 1.0;
        double current = 1.0;
        double maxDd = 0.0;
        for (double r : returns) {
            current *= (1.0 + r);
            if (current > peak) {
                peak = current;
            }
            double dd = (current - peak) / peak;
            if (dd < maxDd) {
                maxDd = dd;
            }
        }
        return maxDd * 100.0; // in negative percentage e.g. -12.45
    }

    private PortfolioRiskAttributionResponseDto buildEmptyResponse(
        String status,
        LocalDate asOf,
        OffsetDateTime cutoff,
        String bmCode,
        List<String> warnings,
        Map<String, String> notes,
        String reason
    ) {
        warnings.add(reason);
        return new PortfolioRiskAttributionResponseDto(
            status,
            asOf,
            cutoff,
            null,
            null,
            0,
            ANNUALIZATION_FACTOR,
            "252 Trading Days (Square Root of Time)",
            bmCode,
            "Nifty 500 Total Returns Index",
            Collections.emptyList(),
            Collections.emptyList(),
            new RiskReconciliationDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, "NOT_APPLICABLE", "RC_i = w_i * (Sigma * w)_i / sigma_p"),
            new RiskExposureContextDto(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.valueOf(100), List.of("Exposure data unavailable for unallocated portfolio.")),
            new RiskInterpretationPanelDto(Collections.emptyList(), Collections.emptyList(), "Calculation Unavailable: " + reason, BigDecimal.ZERO, warnings, Collections.emptyList()),
            warnings,
            notes
        );
    }

}
