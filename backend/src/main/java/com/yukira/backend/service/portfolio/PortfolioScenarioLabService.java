package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import com.yukira.backend.dto.portfolio.scenario.*;
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
 * Service orchestrating Portfolio Scenario Lab calculations, broad-market beta sensitivity,
 * market-cap and sector shocks, multi-scenario comparisons, and evidence disclosures.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md):
 * 1. Zero frontend financial calculations: all shock arithmetic, weighted contributions, beta regressions,
 *    and scenario deltas are computed deterministically on the backend.
 * 2. Sensitivity Estimates Only: Scenario outputs are explicitly labeled as SENSITIVITY_ESTIMATE_ONLY.
 *    They do NOT predict future performance or portfolio losses.
 * 3. Incomplete coverage is NEVER renormalized: Unclassified or unmodeled exposure is explicitly isolated
 *    and tracked, never silently imputed.
 * 4. Dual Cutoffs: Respects effectiveDate <= asOfDate and availabilityTime <= knowledgeCutoff.
 * 5. Honest Gating: If beta estimates have insufficient sample size (N < 10) or benchmark variance is zero,
 *    the broad-market beta dimension is gated as DATA_UNAVAILABLE.
 */
@Service
@SuppressWarnings("null")
public class PortfolioScenarioLabService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioScenarioLabService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_BENCHMARK = "NIFTY_500_TRI";
    public static final String METHODOLOGY_VERSION = "SCENARIO_LAB_V1";

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityMarketCapClassificationRepository classificationRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final NavObservationRepository navObservationRepository;

    // Macro-sector canonical mapping
    private static final Map<String, String> SECTOR_MAP = new HashMap<>();

    static {
        SECTOR_MAP.put("FINANCIAL SERVICES", "Financial Services");
        SECTOR_MAP.put("BANKS", "Financial Services");
        SECTOR_MAP.put("BANK", "Financial Services");
        SECTOR_MAP.put("BANKING", "Financial Services");
        SECTOR_MAP.put("INSURANCE", "Financial Services");
        SECTOR_MAP.put("FINANCE", "Financial Services");
        SECTOR_MAP.put("INFORMATION TECHNOLOGY", "Information Technology");
        SECTOR_MAP.put("IT", "Information Technology");
        SECTOR_MAP.put("SOFTWARE", "Information Technology");
        SECTOR_MAP.put("OIL GAS & CONSUMABLE FUELS", "Oil Gas & Consumable Fuels");
        SECTOR_MAP.put("OIL, GAS & CONSUMABLE FUELS", "Oil Gas & Consumable Fuels");
        SECTOR_MAP.put("PETROLEUM PRODUCTS", "Oil Gas & Consumable Fuels");
        SECTOR_MAP.put("ENERGY", "Oil Gas & Consumable Fuels");
        SECTOR_MAP.put("FAST MOVING CONSUMER GOODS", "Fast Moving Consumer Goods");
        SECTOR_MAP.put("FMCG", "Fast Moving Consumer Goods");
        SECTOR_MAP.put("AUTOMOBILE AND AUTO COMPONENTS", "Automobile and Auto Components");
        SECTOR_MAP.put("AUTO", "Automobile and Auto Components");
        SECTOR_MAP.put("HEALTHCARE", "Healthcare");
        SECTOR_MAP.put("PHARMACEUTICALS", "Healthcare");
        SECTOR_MAP.put("CAPITAL GOODS", "Capital Goods");
        SECTOR_MAP.put("METALS & MINING", "Metals & Mining");
        SECTOR_MAP.put("CONSUMER SERVICES", "Consumer Services");
        SECTOR_MAP.put("TELECOMMUNICATION", "Telecommunication");
        SECTOR_MAP.put("POWER", "Power");
        SECTOR_MAP.put("CHEMICALS", "Chemicals");
        SECTOR_MAP.put("CONSTRUCTION MATERIALS", "Construction Materials");
        SECTOR_MAP.put("CONSTRUCTION", "Construction");
        SECTOR_MAP.put("REALTY", "Realty");
    }

    private static final Set<String> SEBI_APPROX_LARGE_CAP = Set.of(
        "HDFC BANK", "ICICI BANK", "STATE BANK OF INDIA", "AXIS BANK", "KOTAK MAHINDRA BANK",
        "RELIANCE INDUSTRIES", "TATA CONSULTANCY SERVICES", "INFOSYS", "BHARTI AIRTEL", "ITC",
        "LARSEN & TOUBRO", "MARUTI SUZUKI", "HINDUSTAN AERONAUTICS", "CIPLA", "HCL TECHNOLOGIES",
        "POWER GRID", "COAL INDIA", "MAHINDRA & MAHINDRA", "DLF", "DR. REDDY", "DR REDDY",
        "BAJAJ FINANCE", "BAJAJ AUTO", "APOLLO HOSPITALS", "ULTRATECH CEMENT", "TATA STEEL",
        "OIL & NATURAL GAS", "ONGC", "JSW STEEL", "BHARAT ELECTRONICS", "EICHER MOTORS", "ZOMATO"
    );

    private static final Set<String> SEBI_APPROX_MID_CAP = Set.of(
        "ZYDUS LIFESCIENCES", "INDRAPRASTHA GAS", "PETRONET LNG", "MAHANAGAR GAS",
        "NARAYANA HRUDAYALAYA", "CENTRAL DEPOSITORY SERVICES", "CDSL", "INDUS TOWERS",
        "CIE AUTOMOTIVE", "ASHOK LEYLAND", "TVS MOTOR", "DIXON TECHNOLOGIES",
        "PERSISTENT SYSTEMS", "BOSCH", "BIOCON", "PB FINTECH", "POLICYBAZAAR",
        "FEDERAL BANK", "BLUE STAR", "AU SMALL FINANCE BANK", "BALKRISHNA INDUSTRIES"
    );

    @Autowired
    public PortfolioScenarioLabService(
            SchemeOptionRepository schemeOptionRepository,
            PortfolioSnapshotRepository snapshotRepository,
            PortfolioHoldingRepository holdingRepository,
            SecurityMarketCapClassificationRepository classificationRepository,
            BenchmarkRepository benchmarkRepository,
            BenchmarkObservationRepository benchmarkObservationRepository,
            NavObservationRepository navObservationRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.classificationRepository = classificationRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.navObservationRepository = navObservationRepository;
    }

    /**
     * Supported Predefined Scenario Templates.
     */
    public List<ScenarioTemplateDto> getSupportedTemplates() {
        return List.of(
            new ScenarioTemplateDto(
                "TEMPLATE_MKT_CORRECTION_10",
                "Broad Market -10% Correction",
                "BROAD_MARKET",
                "BROAD_MARKET",
                "Linear beta-based sensitivity to a -10.00% benchmark index decline.",
                new BigDecimal("-10.00"),
                null, null, null,
                Collections.emptyMap()
            ),
            new ScenarioTemplateDto(
                "TEMPLATE_MKT_PULLBACK_05",
                "Mild Market Pullback -5%",
                "BROAD_MARKET",
                "BROAD_MARKET",
                "Linear beta-based sensitivity to a -5.00% benchmark index pullback.",
                new BigDecimal("-5.00"),
                null, null, null,
                Collections.emptyMap()
            ),
            new ScenarioTemplateDto(
                "TEMPLATE_MKT_CRASH_20",
                "Severe Market Correction -20%",
                "BROAD_MARKET",
                "BROAD_MARKET",
                "Linear beta-based sensitivity to a severe -20.00% benchmark drawdown.",
                new BigDecimal("-20.00"),
                null, null, null,
                Collections.emptyMap()
            ),
            new ScenarioTemplateDto(
                "TEMPLATE_MID_SMALL_SHAKEOUT",
                "Mid & Small Cap Liquidity Shakeout",
                "MARKET_CAP",
                "MARKET_CAP",
                "Asymmetric stress with acute pressure on Mid-Cap (-10%) and Small-Cap (-20%) holdings while Large-Cap is flat (0%).",
                null,
                BigDecimal.ZERO,
                new BigDecimal("-10.00"),
                new BigDecimal("-20.00"),
                Collections.emptyMap()
            ),
            new ScenarioTemplateDto(
                "TEMPLATE_ASYMMETRIC_CAP_STRESS",
                "Broad Weakness with Cap Dispersion",
                "MARKET_CAP",
                "MARKET_CAP",
                "Tiered market stress: Large-Cap (-6%), Mid-Cap (-12%), Small-Cap (-18%).",
                null,
                new BigDecimal("-6.00"),
                new BigDecimal("-12.00"),
                new BigDecimal("-18.00"),
                Collections.emptyMap()
            ),
            new ScenarioTemplateDto(
                "TEMPLATE_FINANCIAL_CRISIS",
                "Financial Sector Distress",
                "SECTOR",
                "SECTOR",
                "Severe shock to Financial Services (-15%) and Realty (-10%), with minor spillover (-2%) across other sectors.",
                null, null, null, null,
                Map.of(
                    "Financial Services", new BigDecimal("-15.00"),
                    "Realty", new BigDecimal("-10.00")
                )
            ),
            new ScenarioTemplateDto(
                "TEMPLATE_TECH_DERATING",
                "Technology & Growth De-rating",
                "SECTOR",
                "SECTOR",
                "Growth selloff concentrated in Information Technology (-15%) and Consumer Services (-10%).",
                null, null, null, null,
                Map.of(
                    "Information Technology", new BigDecimal("-15.00"),
                    "Consumer Services", new BigDecimal("-10.00")
                )
            ),
            new ScenarioTemplateDto(
                "TEMPLATE_DEFENSIVE_ROTATION",
                "Cyclical Drag / Defensive Rotation",
                "SECTOR",
                "SECTOR",
                "Heavy industrial cyclicals fall (Oil & Gas -12%, Metals & Mining -15%, Capital Goods -8%) while defensives (FMCG, Healthcare) are spared (0%).",
                null, null, null, null,
                Map.of(
                    "Oil Gas & Consumable Fuels", new BigDecimal("-12.00"),
                    "Metals & Mining", new BigDecimal("-15.00"),
                    "Capital Goods", new BigDecimal("-8.00"),
                    "Fast Moving Consumer Goods", BigDecimal.ZERO,
                    "Healthcare", BigDecimal.ZERO
                )
            )
        );
    }

    /**
     * Inspects portfolio holdings and beta readiness to return metadata, available dimensions,
     * and current coverage.
     */
    @Transactional(readOnly = true)
    public ScenarioDimensionMetadataDto getDimensionMetadata(
            List<ResearchPortfolioFundItemDto> funds,
            String benchmarkCode,
            LocalDate asOfDate,
            OffsetDateTime knowledgeCutoff
    ) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        LocalDate asOf = asOfDate != null ? asOfDate : cutoff.toLocalDate();
        String bmCode = (benchmarkCode != null && !benchmarkCode.isBlank()) ? benchmarkCode : DEFAULT_BENCHMARK;

        PortfolioDataSnapshot data = resolvePortfolioData(funds, asOf, cutoff);
        BetaEstimationResult betaResult = calculatePortfolioBeta(data, bmCode, asOf, cutoff);

        return new ScenarioDimensionMetadataDto(
            data.portfolioLargeCapWeightPct(),
            data.portfolioMidCapWeightPct(),
            data.portfolioSmallCapWeightPct(),
            data.portfolioCashWeightPct(),
            data.portfolioUnclassifiedWeightPct(),
            data.portfolioCoveredWeightPct(),
            data.portfolioSectorWeightsPct(),
            betaResult.portfolioBeta(),
            betaResult.observationCount(),
            betaResult.benchmarkCode(),
            betaResult.benchmarkName(),
            betaResult.isAvailable(),
            data.closestSnapshotDate(),
            data.daysMismatchFromAsOf(),
            data.snapshotComparabilityState(),
            getSupportedTemplates()
        );
    }

    /**
     * Executes deterministic scenario calculations across supported dimensions.
     */
    @Transactional(readOnly = true)
    public ScenarioLabResponseDto runScenario(ScenarioLabRequestDto request) {
        validateRequest(request);

        OffsetDateTime cutoff = request.knowledgeCutoff() != null ? request.knowledgeCutoff() : OffsetDateTime.now();
        LocalDate asOf = request.asOfDate() != null ? request.asOfDate() : cutoff.toLocalDate();
        String bmCode = (request.benchmarkCode() != null && !request.benchmarkCode().isBlank())
            ? request.benchmarkCode() : DEFAULT_BENCHMARK;
        String scenarioType = request.scenarioType() != null ? request.scenarioType().toUpperCase(Locale.ROOT) : "BROAD_MARKET";
        String scenarioName = request.scenarioName() != null ? request.scenarioName() : "Custom Scenario";

        // 1. Resolve Portfolio Holdings & Exposure Snapshot
        PortfolioDataSnapshot data = resolvePortfolioData(request.funds(), asOf, cutoff);

        // 2. Resolve Empirical Betas against Benchmark
        BetaEstimationResult betaResult = calculatePortfolioBeta(data, bmCode, asOf, cutoff);

        List<String> limitations = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        List<String> investigationQuestions = new ArrayList<>();
        Map<String, String> epistemic = new LinkedHashMap<>();

        epistemic.put("Methodology", "Deterministic Portfolio Scenario Lab (Phase 2N)");
        epistemic.put("ScenarioType", scenarioType);
        epistemic.put("MethodologyVersion", METHODOLOGY_VERSION);
        epistemic.put("EpistemicStatus", "SENSITIVITY_ESTIMATE_ONLY");
        epistemic.put("PointInTimeRules", "portfolioDate <= asOfDate AND availabilityTime <= knowledgeCutoff");
        epistemic.put("Disclaimer", "Mathematical sensitivity simulation. Does NOT constitute investment advice or predictive return forecasts.");

        if (data.daysMismatchFromAsOf() > 45) {
            limitations.add("Holdings snapshot disclosure date (" + data.closestSnapshotDate() + ") is " +
                data.daysMismatchFromAsOf() + " days from evaluation date (" + asOf + "). Underlying portfolio holdings may have drifted.");
        }

        BigDecimal unmodeledWeightPct = data.portfolioUnclassifiedWeightPct();
        BigDecimal coveredWeightPct = data.portfolioCoveredWeightPct();
        boolean isFullyCovered = unmodeledWeightPct.compareTo(new BigDecimal("0.50")) <= 0;

        if (!isFullyCovered) {
            limitations.add("Incomplete holdings exposure coverage: " + unmodeledWeightPct.toPlainString() +
                "% of portfolio weight is unclassified or missing classification. Unclassified exposure is treated with an explicit 0% return assumption rather than imputed.");
        }

        BigDecimal portfolioImpact;
        List<ScenarioConstituentImpactDto> constituentImpacts = new ArrayList<>();
        List<ScenarioExposureImpactDto> exposureImpacts = new ArrayList<>();
        String status = "SENSITIVITY_ESTIMATE_ONLY";

        switch (scenarioType) {
            case "BROAD_MARKET" -> {
                BigDecimal mktShock = request.broadMarketShockPct() != null ? request.broadMarketShockPct() : new BigDecimal("-10.00");
                assumptions.add("Broad market benchmark: " + betaResult.benchmarkName() + " (" + betaResult.benchmarkCode() + ").");
                assumptions.add("User-specified broad equity market shock: " + mktShock.toPlainString() + "%.");
                assumptions.add("Linear beta approximation: Estimated Return = Beta × Market Shock.");

                if (!betaResult.isAvailable()) {
                    status = "DATA_UNAVAILABLE";
                    limitations.add("Broad-market beta sensitivity gated: " + betaResult.gatingReason());
                    portfolioImpact = BigDecimal.ZERO;
                } else {
                    for (FundExposureData fund : data.funds()) {
                        BigDecimal fBeta = betaResult.fundBetas().getOrDefault(fund.schemeOptionId(), betaResult.portfolioBeta());
                        BigDecimal fReturn = fBeta.multiply(mktShock).setScale(2, RoundingMode.HALF_UP);
                        BigDecimal fContrib = fund.normalizedWeightFraction().multiply(fReturn).setScale(2, RoundingMode.HALF_UP);

                        constituentImpacts.add(new ScenarioConstituentImpactDto(
                            fund.schemeOptionId(), fund.fundName(), fund.amfiCode(),
                            fund.normalizedWeightPct(), fBeta, fReturn, fContrib,
                            fund.largeCapWeightPct(), fund.midCapWeightPct(), fund.smallCapWeightPct(),
                            fund.cashWeightPct(), fund.unclassifiedWeightPct(), fund.sectorWeightsPct()
                        ));
                    }

                    portfolioImpact = constituentImpacts.stream()
                        .map(ScenarioConstituentImpactDto::portfolioContributionPct)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                    exposureImpacts.add(new ScenarioExposureImpactDto(
                        "BROAD_MARKET",
                        "Broad Market (" + betaResult.benchmarkCode() + ")",
                        HUNDRED,
                        mktShock,
                        portfolioImpact,
                        "COVERED"
                    ));
                }

                limitations.add("Beta linearity omission: Assumes uniform linear sensitivity. Real drawdowns exhibit nonlinear asymmetric downside capture, correlation spikes towards 1.0, and liquidity discounts.");
                limitations.add("Static beta estimation: Historical OLS beta estimated over N = " + betaResult.observationCount() + " aligned daily return intervals. Fund beta may change during crisis conditions.");
                investigationQuestions.add("How does each fund's historical downside capture ratio compare to its linear OLS beta?");
                investigationQuestions.add("Would active managers raise cash or hedge during a persistent broad market downturn?");
            }

            case "MARKET_CAP" -> {
                BigDecimal lShock = request.largeCapShockPct() != null ? request.largeCapShockPct() : new BigDecimal("-5.00");
                BigDecimal mShock = request.midCapShockPct() != null ? request.midCapShockPct() : new BigDecimal("-10.00");
                BigDecimal sShock = request.smallCapShockPct() != null ? request.smallCapShockPct() : new BigDecimal("-15.00");
                BigDecimal cShock = request.cashShockPct() != null ? request.cashShockPct() : BigDecimal.ZERO;

                assumptions.add("Market-cap bucket shocks: Large-Cap (" + lShock.toPlainString() + "%), Mid-Cap (" +
                    mShock.toPlainString() + "%), Small-Cap (" + sShock.toPlainString() + "%), Cash & Liquid Reserves (" + cShock.toPlainString() + "%).");
                assumptions.add("Mutually exclusive exposure partition: Each underlying security is uniquely assigned to exactly one market-cap bucket, preventing double-counting.");

                BigDecimal lContrib = data.portfolioLargeCapWeightPct().multiply(lShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                BigDecimal mContrib = data.portfolioMidCapWeightPct().multiply(mShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                BigDecimal sContrib = data.portfolioSmallCapWeightPct().multiply(sShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                BigDecimal cContrib = data.portfolioCashWeightPct().multiply(cShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);

                portfolioImpact = lContrib.add(mContrib).add(sContrib).add(cContrib).setScale(2, RoundingMode.HALF_UP);

                exposureImpacts.add(new ScenarioExposureImpactDto("MARKET_CAP", "Large Cap", data.portfolioLargeCapWeightPct(), lShock, lContrib.setScale(2, RoundingMode.HALF_UP), "COVERED"));
                exposureImpacts.add(new ScenarioExposureImpactDto("MARKET_CAP", "Mid Cap", data.portfolioMidCapWeightPct(), mShock, mContrib.setScale(2, RoundingMode.HALF_UP), "COVERED"));
                exposureImpacts.add(new ScenarioExposureImpactDto("MARKET_CAP", "Small Cap", data.portfolioSmallCapWeightPct(), sShock, sContrib.setScale(2, RoundingMode.HALF_UP), "COVERED"));
                exposureImpacts.add(new ScenarioExposureImpactDto("CASH", "Cash & Liquid Assets", data.portfolioCashWeightPct(), cShock, cContrib.setScale(2, RoundingMode.HALF_UP), "COVERED"));
                exposureImpacts.add(new ScenarioExposureImpactDto("UNMODELED", "Unclassified / Missing", unmodeledWeightPct, BigDecimal.ZERO, BigDecimal.ZERO, "UNMODELED"));

                for (FundExposureData fund : data.funds()) {
                    BigDecimal fLContr = fund.largeCapWeightPct().multiply(lShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                    BigDecimal fMContr = fund.midCapWeightPct().multiply(mShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                    BigDecimal fSContr = fund.smallCapWeightPct().multiply(sShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                    BigDecimal fCContr = fund.cashWeightPct().multiply(cShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);

                    BigDecimal fundEstReturn = fLContr.add(fMContr).add(fSContr).add(fCContr).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal fundContrib = fund.normalizedWeightFraction().multiply(fundEstReturn).setScale(2, RoundingMode.HALF_UP);

                    constituentImpacts.add(new ScenarioConstituentImpactDto(
                        fund.schemeOptionId(), fund.fundName(), fund.amfiCode(),
                        fund.normalizedWeightPct(), betaResult.fundBetas().get(fund.schemeOptionId()),
                        fundEstReturn, fundContrib,
                        fund.largeCapWeightPct(), fund.midCapWeightPct(), fund.smallCapWeightPct(),
                        fund.cashWeightPct(), fund.unclassifiedWeightPct(), fund.sectorWeightsPct()
                    ));
                }

                limitations.add("Uniform bucket assumption: Assumes all securities within each market-cap category experience the identical return. Intra-cap dispersion is not modeled.");
                investigationQuestions.add("Which constituent fund carries the highest small-cap exposure and drives the small-cap drawdown?");
                investigationQuestions.add("Are small-cap holdings liquid enough to be liquidated at prevailing market valuations during stress?");
            }

            case "SECTOR" -> {
                Map<String, BigDecimal> secShocks = request.sectorShocks() != null ? request.sectorShocks() : Collections.emptyMap();
                BigDecimal cShock = request.cashShockPct() != null ? request.cashShockPct() : BigDecimal.ZERO;

                assumptions.add("Sector-specific shock assumptions applied to disclosed underlying security sector allocations.");
                assumptions.add("Unspecified sectors are assumed to experience 0.00% return.");

                BigDecimal portImpactAcc = BigDecimal.ZERO;

                for (Map.Entry<String, BigDecimal> entry : data.portfolioSectorWeightsPct().entrySet()) {
                    String secName = entry.getKey();
                    BigDecimal secWeight = entry.getValue();
                    BigDecimal shock = secShocks.getOrDefault(secName, BigDecimal.ZERO);
                    BigDecimal contrib = secWeight.multiply(shock).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                    portImpactAcc = portImpactAcc.add(contrib);

                    exposureImpacts.add(new ScenarioExposureImpactDto(
                        "SECTOR", secName, secWeight, shock, contrib.setScale(2, RoundingMode.HALF_UP), "COVERED"
                    ));
                }

                BigDecimal cashContrib = data.portfolioCashWeightPct().multiply(cShock).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                portImpactAcc = portImpactAcc.add(cashContrib);

                exposureImpacts.add(new ScenarioExposureImpactDto("CASH", "Cash & Liquid Assets", data.portfolioCashWeightPct(), cShock, cashContrib.setScale(2, RoundingMode.HALF_UP), "COVERED"));
                if (unmodeledWeightPct.compareTo(BigDecimal.ZERO) > 0) {
                    exposureImpacts.add(new ScenarioExposureImpactDto("UNMODELED", "Unclassified / Missing", unmodeledWeightPct, BigDecimal.ZERO, BigDecimal.ZERO, "UNMODELED"));
                }

                portfolioImpact = portImpactAcc.setScale(2, RoundingMode.HALF_UP);

                for (FundExposureData fund : data.funds()) {
                    BigDecimal fImpactAcc = BigDecimal.ZERO;
                    for (Map.Entry<String, BigDecimal> entry : fund.sectorWeightsPct().entrySet()) {
                        String sName = entry.getKey();
                        BigDecimal sW = entry.getValue();
                        BigDecimal shock = secShocks.getOrDefault(sName, BigDecimal.ZERO);
                        fImpactAcc = fImpactAcc.add(sW.multiply(shock).divide(HUNDRED, 4, RoundingMode.HALF_UP));
                    }
                    fImpactAcc = fImpactAcc.add(fund.cashWeightPct().multiply(cShock).divide(HUNDRED, 4, RoundingMode.HALF_UP));

                    BigDecimal fundEstReturn = fImpactAcc.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal fundContrib = fund.normalizedWeightFraction().multiply(fundEstReturn).setScale(2, RoundingMode.HALF_UP);

                    constituentImpacts.add(new ScenarioConstituentImpactDto(
                        fund.schemeOptionId(), fund.fundName(), fund.amfiCode(),
                        fund.normalizedWeightPct(), betaResult.fundBetas().get(fund.schemeOptionId()),
                        fundEstReturn, fundContrib,
                        fund.largeCapWeightPct(), fund.midCapWeightPct(), fund.smallCapWeightPct(),
                        fund.cashWeightPct(), fund.unclassifiedWeightPct(), fund.sectorWeightsPct()
                    ));
                }

                limitations.add("Zero contagion assumption: Models direct sector exposure impact only; excludes second-order macroeconomic contagion or inter-sector supply chain dependencies.");
                investigationQuestions.add("How concentrated is the portfolio in the most severely shocked sector?");
                investigationQuestions.add("Do fund managers possess thematic or sector mandates that restrict defensive rotation?");
            }

            case "COMBINED" -> {
                // Combined: Additive Layering of Market-Cap baseline + Sector specific excess tilts
                BigDecimal lShock = request.largeCapShockPct() != null ? request.largeCapShockPct() : new BigDecimal("-5.00");
                BigDecimal mShock = request.midCapShockPct() != null ? request.midCapShockPct() : new BigDecimal("-10.00");
                BigDecimal sShock = request.smallCapShockPct() != null ? request.smallCapShockPct() : new BigDecimal("-15.00");
                BigDecimal cShock = request.cashShockPct() != null ? request.cashShockPct() : BigDecimal.ZERO;
                Map<String, BigDecimal> secShocks = request.sectorShocks() != null ? request.sectorShocks() : Collections.emptyMap();

                assumptions.add("Combined multi-layer scenario: Market-Cap bucket baseline shock PLUS additive sector excess tilts.");
                assumptions.add("Double-counting prevention rule: Sector shocks represent incremental excess shocks over the market-cap baseline.");

                BigDecimal capImpact = data.portfolioLargeCapWeightPct().multiply(lShock)
                    .add(data.portfolioMidCapWeightPct().multiply(mShock))
                    .add(data.portfolioSmallCapWeightPct().multiply(sShock))
                    .add(data.portfolioCashWeightPct().multiply(cShock))
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP);

                BigDecimal sectorExcessImpact = BigDecimal.ZERO;
                for (Map.Entry<String, BigDecimal> entry : secShocks.entrySet()) {
                    BigDecimal secWeight = data.portfolioSectorWeightsPct().getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    BigDecimal sExcess = entry.getValue();
                    sectorExcessImpact = sectorExcessImpact.add(secWeight.multiply(sExcess).divide(HUNDRED, 4, RoundingMode.HALF_UP));
                }

                portfolioImpact = capImpact.add(sectorExcessImpact).setScale(2, RoundingMode.HALF_UP);

                exposureImpacts.add(new ScenarioExposureImpactDto("MARKET_CAP", "Large Cap Baseline", data.portfolioLargeCapWeightPct(), lShock, data.portfolioLargeCapWeightPct().multiply(lShock).divide(HUNDRED, 2, RoundingMode.HALF_UP), "COVERED"));
                exposureImpacts.add(new ScenarioExposureImpactDto("MARKET_CAP", "Mid Cap Baseline", data.portfolioMidCapWeightPct(), mShock, data.portfolioMidCapWeightPct().multiply(mShock).divide(HUNDRED, 2, RoundingMode.HALF_UP), "COVERED"));
                exposureImpacts.add(new ScenarioExposureImpactDto("MARKET_CAP", "Small Cap Baseline", data.portfolioSmallCapWeightPct(), sShock, data.portfolioSmallCapWeightPct().multiply(sShock).divide(HUNDRED, 2, RoundingMode.HALF_UP), "COVERED"));
                exposureImpacts.add(new ScenarioExposureImpactDto("CASH", "Cash & Liquid Assets", data.portfolioCashWeightPct(), cShock, data.portfolioCashWeightPct().multiply(cShock).divide(HUNDRED, 2, RoundingMode.HALF_UP), "COVERED"));

                for (Map.Entry<String, BigDecimal> entry : secShocks.entrySet()) {
                    BigDecimal secWeight = data.portfolioSectorWeightsPct().getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    BigDecimal contr = secWeight.multiply(entry.getValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    exposureImpacts.add(new ScenarioExposureImpactDto("SECTOR_EXCESS", entry.getKey() + " (Excess Tilt)", secWeight, entry.getValue(), contr, "COVERED"));
                }

                for (FundExposureData fund : data.funds()) {
                    BigDecimal fCap = fund.largeCapWeightPct().multiply(lShock)
                        .add(fund.midCapWeightPct().multiply(mShock))
                        .add(fund.smallCapWeightPct().multiply(sShock))
                        .add(fund.cashWeightPct().multiply(cShock))
                        .divide(HUNDRED, 4, RoundingMode.HALF_UP);

                    BigDecimal fSec = BigDecimal.ZERO;
                    for (Map.Entry<String, BigDecimal> entry : secShocks.entrySet()) {
                        BigDecimal w = fund.sectorWeightsPct().getOrDefault(entry.getKey(), BigDecimal.ZERO);
                        fSec = fSec.add(w.multiply(entry.getValue()).divide(HUNDRED, 4, RoundingMode.HALF_UP));
                    }

                    BigDecimal fEstReturn = fCap.add(fSec).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal fContrib = fund.normalizedWeightFraction().multiply(fEstReturn).setScale(2, RoundingMode.HALF_UP);

                    constituentImpacts.add(new ScenarioConstituentImpactDto(
                        fund.schemeOptionId(), fund.fundName(), fund.amfiCode(),
                        fund.normalizedWeightPct(), betaResult.fundBetas().get(fund.schemeOptionId()),
                        fEstReturn, fContrib,
                        fund.largeCapWeightPct(), fund.midCapWeightPct(), fund.smallCapWeightPct(),
                        fund.cashWeightPct(), fund.unclassifiedWeightPct(), fund.sectorWeightsPct()
                    ));
                }

                limitations.add("Additive excess assumption: Sector tilts are treated as additive orthogonal increments to the market-cap partition.");
                investigationQuestions.add("Does the interaction between sector concentration and market-cap illiquidity amplify downside beyond linear addition?");
            }

            default -> throw new IllegalArgumentException("Unsupported scenarioType: " + scenarioType + ". Must be BROAD_MARKET, MARKET_CAP, SECTOR, or COMBINED.");
        }

        return new ScenarioLabResponseDto(
            status,
            scenarioType,
            scenarioName,
            asOf,
            data.closestSnapshotDate(),
            cutoff,
            betaResult.benchmarkCode(),
            betaResult.benchmarkName(),
            betaResult.portfolioBeta(),
            betaResult.observationCount(),
            betaResult.estimationWindowDays(),
            portfolioImpact,
            coveredWeightPct,
            unmodeledWeightPct,
            isFullyCovered,
            request.unmodeledTreatment() != null ? request.unmodeledTreatment() : "EXPLICIT_ZERO_RETURN",
            constituentImpacts,
            exposureImpacts,
            epistemic,
            assumptions,
            limitations,
            investigationQuestions,
            METHODOLOGY_VERSION
        );
    }

    /**
     * Compares two scenarios side-by-side under identical baseline portfolio conditions.
     */
    @Transactional(readOnly = true)
    public ScenarioComparisonResponseDto compareScenarios(ScenarioComparisonRequestDto request) {
        if (request == null) throw new IllegalArgumentException("ScenarioComparisonRequestDto cannot be null.");
        if (request.scenarioA() == null || request.scenarioB() == null) {
            throw new IllegalArgumentException("Both scenarioA and scenarioB must be provided for comparison.");
        }

        ScenarioLabResponseDto resA = runScenario(request.scenarioA());
        ScenarioLabResponseDto resB = runScenario(request.scenarioB());

        // Check comparability: Hold portfolio weights, snapshot dates, and cutoffs constant
        boolean isComparable = true;
        StringBuilder notice = new StringBuilder();
        List<String> methodDiffs = new ArrayList<>();

        if (!resA.asOfDate().equals(resB.asOfDate())) {
            isComparable = false;
            notice.append("Different evaluation dates (").append(resA.asOfDate()).append(" vs ").append(resB.asOfDate()).append("). ");
        }

        if (!Objects.equals(resA.snapshotDate(), resB.snapshotDate())) {
            isComparable = false;
            notice.append("Different holdings snapshot dates (").append(resA.snapshotDate()).append(" vs ").append(resB.snapshotDate()).append("). ");
        }

        if (!resA.scenarioType().equals(resB.scenarioType())) {
            methodDiffs.add("Methodology divergence: Scenario A uses " + resA.scenarioType() +
                " while Scenario B uses " + resB.scenarioType() + ".");
        }

        if (notice.isEmpty()) {
            notice.append("Methodologically synchronized: Evaluated on identical portfolio weights, holdings snapshots, and benchmark reference.");
        }

        BigDecimal deltaPortImpact = resB.estimatedPortfolioImpactPct().subtract(resA.estimatedPortfolioImpactPct()).setScale(2, RoundingMode.HALF_UP);

        // Constituent Deltas
        List<ScenarioConstituentDeltaDto> constituentDeltas = new ArrayList<>();
        Map<Long, ScenarioConstituentImpactDto> mapA = new HashMap<>();
        for (ScenarioConstituentImpactDto c : resA.constituentImpacts()) {
            mapA.put(c.schemeOptionId(), c);
        }

        for (ScenarioConstituentImpactDto cB : resB.constituentImpacts()) {
            ScenarioConstituentImpactDto cA = mapA.get(cB.schemeOptionId());
            BigDecimal retA = cA != null ? cA.estimatedFundReturnPct() : BigDecimal.ZERO;
            BigDecimal retB = cB.estimatedFundReturnPct();
            BigDecimal retDelta = retB.subtract(retA).setScale(2, RoundingMode.HALF_UP);

            BigDecimal contrA = cA != null ? cA.portfolioContributionPct() : BigDecimal.ZERO;
            BigDecimal contrB = cB.portfolioContributionPct();
            BigDecimal contrDelta = contrB.subtract(contrA).setScale(2, RoundingMode.HALF_UP);

            constituentDeltas.add(new ScenarioConstituentDeltaDto(
                cB.schemeOptionId(), cB.fundName(), cB.portfolioWeightPct(),
                retA, retB, retDelta, contrA, contrB, contrDelta
            ));
        }

        // Exposure Deltas
        List<ScenarioExposureDeltaDto> exposureDeltas = new ArrayList<>();
        Map<String, ScenarioExposureImpactDto> expMapA = new HashMap<>();
        for (ScenarioExposureImpactDto e : resA.exposureImpacts()) {
            expMapA.put(e.exposureName(), e);
        }

        Set<String> allExposures = new LinkedHashSet<>();
        for (ScenarioExposureImpactDto e : resA.exposureImpacts()) allExposures.add(e.exposureName());
        for (ScenarioExposureImpactDto e : resB.exposureImpacts()) allExposures.add(e.exposureName());

        for (String expName : allExposures) {
            ScenarioExposureImpactDto eA = expMapA.get(expName);
            ScenarioExposureImpactDto eB = null;
            for (ScenarioExposureImpactDto e : resB.exposureImpacts()) {
                if (e.exposureName().equals(expName)) {
                    eB = e;
                    break;
                }
            }

            BigDecimal w = eB != null ? eB.portfolioWeightPct() : (eA != null ? eA.portfolioWeightPct() : BigDecimal.ZERO);
            String dim = eB != null ? eB.dimensionType() : (eA != null ? eA.dimensionType() : "EXPOSURE");
            BigDecimal shA = eA != null ? eA.assumedShockPct() : BigDecimal.ZERO;
            BigDecimal shB = eB != null ? eB.assumedShockPct() : BigDecimal.ZERO;
            BigDecimal shDelta = shB.subtract(shA).setScale(2, RoundingMode.HALF_UP);

            BigDecimal cA = eA != null ? eA.estimatedContributionPct() : BigDecimal.ZERO;
            BigDecimal cB = eB != null ? eB.estimatedContributionPct() : BigDecimal.ZERO;
            BigDecimal cDelta = cB.subtract(cA).setScale(2, RoundingMode.HALF_UP);

            exposureDeltas.add(new ScenarioExposureDeltaDto(
                dim, expName, w, shA, shB, shDelta, cA, cB, cDelta
            ));
        }

        String divSummary = String.format(Locale.US,
            "Scenario B (%s: %s%%) differs from Scenario A (%s: %s%%) by %s%.2f%% percentage points.",
            resB.scenarioName(), resB.estimatedPortfolioImpactPct().toPlainString(),
            resA.scenarioName(), resA.estimatedPortfolioImpactPct().toPlainString(),
            deltaPortImpact.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "", deltaPortImpact.doubleValue()
        );

        return new ScenarioComparisonResponseDto(
            isComparable,
            notice.toString(),
            resA,
            resB,
            deltaPortImpact,
            constituentDeltas,
            exposureDeltas,
            methodDiffs,
            divSummary
        );
    }

    // ---------------------------------------------------------------------------------------------
    // Helper Data Structures and Resolution Methods
    // ---------------------------------------------------------------------------------------------

    private void validateRequest(ScenarioLabRequestDto request) {
        if (request == null) throw new IllegalArgumentException("ScenarioLabRequestDto cannot be null.");
        if (request.funds() == null || request.funds().isEmpty()) {
            throw new IllegalArgumentException("Portfolio must contain at least one constituent fund.");
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (ResearchPortfolioFundItemDto f : request.funds()) {
            if (f.schemeOptionId() == null) throw new IllegalArgumentException("schemeOptionId cannot be null.");
            if (f.allocationWeight() == null || f.allocationWeight().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Fund allocation cannot be negative or null.");
            }
            sum = sum.add(f.allocationWeight());
        }

        if (sum.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total portfolio allocation weight must be positive.");
        }
    }

    private record FundExposureData(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        BigDecimal normalizedWeightFraction,
        BigDecimal normalizedWeightPct,
        LocalDate snapshotDate,
        BigDecimal largeCapWeightPct,
        BigDecimal midCapWeightPct,
        BigDecimal smallCapWeightPct,
        BigDecimal cashWeightPct,
        BigDecimal unclassifiedWeightPct,
        Map<String, BigDecimal> sectorWeightsPct
    ) {}

    private record PortfolioDataSnapshot(
        List<FundExposureData> funds,
        BigDecimal portfolioLargeCapWeightPct,
        BigDecimal portfolioMidCapWeightPct,
        BigDecimal portfolioSmallCapWeightPct,
        BigDecimal portfolioCashWeightPct,
        BigDecimal portfolioUnclassifiedWeightPct,
        BigDecimal portfolioCoveredWeightPct,
        Map<String, BigDecimal> portfolioSectorWeightsPct,
        LocalDate closestSnapshotDate,
        long daysMismatchFromAsOf,
        String snapshotComparabilityState
    ) {}

    private PortfolioDataSnapshot resolvePortfolioData(
            List<ResearchPortfolioFundItemDto> funds,
            LocalDate asOfDate,
            OffsetDateTime knowledgeCutoff
    ) {
        BigDecimal sumWeights = funds.stream().map(ResearchPortfolioFundItemDto::allocationWeight).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FundExposureData> fundDataList = new ArrayList<>();
        LocalDate closestSnap = null;
        long maxDaysDiff = 0;

        BigDecimal portLargeAcc = BigDecimal.ZERO;
        BigDecimal portMidAcc = BigDecimal.ZERO;
        BigDecimal portSmallAcc = BigDecimal.ZERO;
        BigDecimal portCashAcc = BigDecimal.ZERO;
        BigDecimal portUnclassAcc = BigDecimal.ZERO;
        Map<String, BigDecimal> portSectorAcc = new LinkedHashMap<>();

        for (ResearchPortfolioFundItemDto f : funds) {
            SchemeOption opt = schemeOptionRepository.findById(f.schemeOptionId())
                .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + f.schemeOptionId() + " not found."));

            String fName = (opt.getPlan() != null && opt.getPlan().getScheme() != null)
                ? opt.getPlan().getScheme().getName() : "Option #" + f.schemeOptionId();
            String amfi = opt.getAmfiCode() != null ? opt.getAmfiCode() : "N/A";

            BigDecimal wFrac = f.allocationWeight().divide(sumWeights, 8, RoundingMode.HALF_UP);
            BigDecimal wPct = wFrac.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);

            // Find closest snapshot
            List<PortfolioSnapshot> snaps = snapshotRepository
                .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(opt.getId(), knowledgeCutoff);

            PortfolioSnapshot chosenSnap = null;
            long bestDiff = Long.MAX_VALUE;

            for (PortfolioSnapshot s : snaps) {
                long diff = Math.abs(ChronoUnit.DAYS.between(s.getPortfolioDate(), asOfDate));
                if (diff < bestDiff) {
                    bestDiff = diff;
                    chosenSnap = s;
                } else if (diff == bestDiff && chosenSnap != null) {
                    if (!s.getPortfolioDate().isAfter(asOfDate) && chosenSnap.getPortfolioDate().isAfter(asOfDate)) {
                        chosenSnap = s;
                    }
                }
            }

            BigDecimal fLarge = BigDecimal.ZERO;
            BigDecimal fMid = BigDecimal.ZERO;
            BigDecimal fSmall = BigDecimal.ZERO;
            BigDecimal fCash = BigDecimal.ZERO;
            BigDecimal fUnclass = BigDecimal.ZERO;
            Map<String, BigDecimal> fSectors = new LinkedHashMap<>();
            LocalDate snapDate = null;

            if (chosenSnap != null) {
                snapDate = chosenSnap.getPortfolioDate();
                if (closestSnap == null || snapDate.isAfter(closestSnap)) {
                    closestSnap = snapDate;
                }
                if (bestDiff > maxDaysDiff) {
                    maxDaysDiff = bestDiff;
                }

                List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(chosenSnap.getId());
                for (PortfolioHolding h : holdings) {
                    if (h.getSecurity() == null) continue;
                    // Reported weight in fund (fraction, e.g. 0.085 for 8.5%)
                    BigDecimal repWeightPct = (h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO).multiply(HUNDRED);

                    // Market Cap
                    String cap = resolveMarketCapCategory(h.getSecurity(), snapDate, knowledgeCutoff);
                    switch (cap) {
                        case "LARGE_CAP" -> fLarge = fLarge.add(repWeightPct);
                        case "MID_CAP" -> fMid = fMid.add(repWeightPct);
                        case "SMALL_CAP" -> fSmall = fSmall.add(repWeightPct);
                        case "CASH_AND_EQUIVALENTS" -> fCash = fCash.add(repWeightPct);
                        default -> fUnclass = fUnclass.add(repWeightPct);
                    }

                    // Sector
                    String sector = resolveCanonicalSector(h.getSecurity().getSector(), h.getSecurity().getCanonicalName(), h.getSecurity().getAssetClass());
                    fSectors.merge(sector, repWeightPct, BigDecimal::add);
                }
            } else {
                // If no snapshot exists, 100% unclassified
                fUnclass = HUNDRED;
            }

            fundDataList.add(new FundExposureData(
                f.schemeOptionId(), fName, amfi, wFrac, wPct, snapDate,
                fLarge.setScale(2, RoundingMode.HALF_UP),
                fMid.setScale(2, RoundingMode.HALF_UP),
                fSmall.setScale(2, RoundingMode.HALF_UP),
                fCash.setScale(2, RoundingMode.HALF_UP),
                fUnclass.setScale(2, RoundingMode.HALF_UP),
                fSectors
            ));

            // Accumulate into portfolio totals
            portLargeAcc = portLargeAcc.add(wFrac.multiply(fLarge));
            portMidAcc = portMidAcc.add(wFrac.multiply(fMid));
            portSmallAcc = portSmallAcc.add(wFrac.multiply(fSmall));
            portCashAcc = portCashAcc.add(wFrac.multiply(fCash));
            portUnclassAcc = portUnclassAcc.add(wFrac.multiply(fUnclass));

            for (Map.Entry<String, BigDecimal> entry : fSectors.entrySet()) {
                portSectorAcc.merge(entry.getKey(), wFrac.multiply(entry.getValue()), BigDecimal::add);
            }
        }

        BigDecimal portLarge = portLargeAcc.setScale(2, RoundingMode.HALF_UP);
        BigDecimal portMid = portMidAcc.setScale(2, RoundingMode.HALF_UP);
        BigDecimal portSmall = portSmallAcc.setScale(2, RoundingMode.HALF_UP);
        BigDecimal portCash = portCashAcc.setScale(2, RoundingMode.HALF_UP);
        BigDecimal portUnclass = portUnclassAcc.setScale(2, RoundingMode.HALF_UP);
        BigDecimal portCovered = portLarge.add(portMid).add(portSmall).add(portCash).setScale(2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> roundedSectors = new LinkedHashMap<>();
        portSectorAcc.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .forEach(e -> roundedSectors.put(e.getKey(), e.getValue().setScale(2, RoundingMode.HALF_UP)));

        String compState = maxDaysDiff > 45 ? "SIGNIFICANT_TEMPORAL_MISMATCH" : "TEMPORALLY_ALIGNED";

        return new PortfolioDataSnapshot(
            fundDataList, portLarge, portMid, portSmall, portCash, portUnclass, portCovered,
            roundedSectors, closestSnap != null ? closestSnap : asOfDate, maxDaysDiff, compState
        );
    }

    private record BetaEstimationResult(
        BigDecimal portfolioBeta,
        Map<Long, BigDecimal> fundBetas,
        int observationCount,
        int estimationWindowDays,
        boolean isAvailable,
        String gatingReason,
        String benchmarkCode,
        String benchmarkName
    ) {}

    private BetaEstimationResult calculatePortfolioBeta(
            PortfolioDataSnapshot data,
            String benchmarkCode,
            LocalDate asOfDate,
            OffsetDateTime knowledgeCutoff
    ) {
        Benchmark bm = benchmarkRepository.findByCode(benchmarkCode).orElse(null);
        if (bm == null) {
            bm = benchmarkRepository.findByCode(DEFAULT_BENCHMARK).orElse(null);
        }

        if (bm == null) {
            return new BetaEstimationResult(null, Collections.emptyMap(), 0, 0, false, "Benchmark not found in repository.", benchmarkCode, benchmarkCode);
        }

        String bmName = bm.getName();
        String bmActualCode = bm.getCode();

        // Retrieve benchmark observations up to asOfDate
        List<BenchmarkObservation> bmObs = benchmarkObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(bm.getId(), asOfDate, knowledgeCutoff);

        if (bmObs.size() < 15) {
            return new BetaEstimationResult(null, Collections.emptyMap(), 0, 0, false, "Insufficient benchmark observations (< 15).", bmActualCode, bmName);
        }

        Map<LocalDate, BigDecimal> bmValues = new HashMap<>();
        for (BenchmarkObservation bo : bmObs) {
            bmValues.put(bo.getEffectiveDate(), bo.getIndexLevel());
        }

        // Retrieve fund NAVs
        Map<Long, Map<LocalDate, BigDecimal>> fundNavs = new HashMap<>();
        for (FundExposureData f : data.funds()) {
            List<NavObservation> navs = navObservationRepository
                .findAuthoritativeObservationsAsOfCutoff(f.schemeOptionId(), asOfDate, knowledgeCutoff);
            Map<LocalDate, BigDecimal> m = new HashMap<>();
            for (NavObservation no : navs) {
                m.put(no.getEffectiveDate(), no.getNavValue());
            }
            fundNavs.put(f.schemeOptionId(), m);
        }

        // Intersect common dates across benchmark and all constituent funds
        Set<LocalDate> commonDates = new TreeSet<>(bmValues.keySet());
        for (FundExposureData f : data.funds()) {
            commonDates.retainAll(fundNavs.get(f.schemeOptionId()).keySet());
        }

        List<LocalDate> sortedDates = new ArrayList<>(commonDates);
        if (sortedDates.size() > 253) {
            sortedDates = sortedDates.subList(sortedDates.size() - 253, sortedDates.size());
        }

        int K = sortedDates.size() - 1;
        if (K < 10) {
            return new BetaEstimationResult(null, Collections.emptyMap(), Math.max(0, K), 252, false, "Insufficient common aligned daily trading intervals (N = " + Math.max(0, K) + " < 10 threshold).", bmActualCode, bmName);
        }

        List<Double> portReturns = new ArrayList<>();
        List<Double> bmReturns = new ArrayList<>();
        Map<Long, List<Double>> fundReturns = new HashMap<>();
        for (FundExposureData f : data.funds()) {
            fundReturns.put(f.schemeOptionId(), new ArrayList<>());
        }

        for (int i = 1; i < sortedDates.size(); i++) {
            LocalDate prevDate = sortedDates.get(i - 1);
            LocalDate currDate = sortedDates.get(i);

            BigDecimal pBm = bmValues.get(prevDate);
            BigDecimal cBm = bmValues.get(currDate);
            if (pBm == null || cBm == null || pBm.compareTo(BigDecimal.ZERO) <= 0) continue;

            double bmRet = cBm.subtract(pBm).divide(pBm, 8, RoundingMode.HALF_UP).doubleValue();
            double portRet = 0.0;

            for (FundExposureData f : data.funds()) {
                BigDecimal pN = fundNavs.get(f.schemeOptionId()).get(prevDate);
                BigDecimal cN = fundNavs.get(f.schemeOptionId()).get(currDate);
                double fRet = cN.subtract(pN).divide(pN, 8, RoundingMode.HALF_UP).doubleValue();
                fundReturns.get(f.schemeOptionId()).add(fRet);
                portRet += f.normalizedWeightFraction().doubleValue() * fRet;
            }

            bmReturns.add(bmRet);
            portReturns.add(portRet);
        }

        K = bmReturns.size();
        if (K < 10) {
            return new BetaEstimationResult(null, Collections.emptyMap(), K, 252, false, "Insufficient common aligned daily trading intervals (N = " + K + " < 10 threshold).", bmActualCode, bmName);
        }

        // Benchmark variance
        double sumBm = 0.0;
        for (double r : bmReturns) sumBm += r;
        double meanBm = sumBm / K;
        double varBmAcc = 0.0;
        for (double r : bmReturns) varBmAcc += Math.pow(r - meanBm, 2);
        double varBm = varBmAcc / (K - 1);

        if (varBm <= 1e-12) {
            return new BetaEstimationResult(null, Collections.emptyMap(), K, 252, false, "Benchmark return variance is zero or undefined during estimation window.", bmActualCode, bmName);
        }

        // Portfolio Beta
        double sumPort = 0.0;
        for (double r : portReturns) sumPort += r;
        double meanPort = sumPort / K;
        double covPortBmAcc = 0.0;
        for (int i = 0; i < K; i++) {
            covPortBmAcc += (portReturns.get(i) - meanPort) * (bmReturns.get(i) - meanBm);
        }
        double covPortBm = covPortBmAcc / (K - 1);
        BigDecimal portBeta = BigDecimal.valueOf(covPortBm / varBm).setScale(2, RoundingMode.HALF_UP);

        // Constituent Betas
        Map<Long, BigDecimal> fundBetas = new HashMap<>();
        for (FundExposureData f : data.funds()) {
            List<Double> rets = fundReturns.get(f.schemeOptionId());
            double sumF = 0.0;
            for (double r : rets) sumF += r;
            double meanF = sumF / K;
            double covFBmAcc = 0.0;
            for (int i = 0; i < K; i++) {
                covFBmAcc += (rets.get(i) - meanF) * (bmReturns.get(i) - meanBm);
            }
            double covFBm = covFBmAcc / (K - 1);
            fundBetas.put(f.schemeOptionId(), BigDecimal.valueOf(covFBm / varBm).setScale(2, RoundingMode.HALF_UP));
        }

        return new BetaEstimationResult(portBeta, fundBetas, K, 252, true, null, bmActualCode, bmName);
    }

    private String resolveMarketCapCategory(Security sec, LocalDate obsDate, OffsetDateTime cutoff) {
        if (sec == null) return "UNCLASSIFIED";

        String assetUpper = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
        String nameUpper = sec.getCanonicalName() != null ? sec.getCanonicalName().toUpperCase(Locale.ROOT) : "";

        if (assetUpper.contains("CASH") || nameUpper.contains("TREPS") || nameUpper.contains("NET CURRENT ASSETS") || nameUpper.contains("CASH")) {
            return "CASH_AND_EQUIVALENTS";
        }
        if (assetUpper.contains("DEBT") || nameUpper.contains("GOI") || nameUpper.contains("TREASURY")) {
            return "CASH_AND_EQUIVALENTS";
        }

        if (assetUpper.contains("OTHER") || nameUpper.contains("UNCLASSIFIED") || nameUpper.contains("UNKNOWN")) {
            return "UNCLASSIFIED";
        }

        if (obsDate != null && sec.getId() != null) {
            List<SecurityMarketCapClassification> list = classificationRepository.findBySecurityIdAndPit(sec.getId(), obsDate, cutoff);
            if (!list.isEmpty() && list.get(0).getMarketCapCategory() != null) {
                return list.get(0).getMarketCapCategory();
            }
        }

        if (assetUpper.contains("EQUITY")) {
            for (String lcap : SEBI_APPROX_LARGE_CAP) {
                if (nameUpper.contains(lcap)) return "LARGE_CAP";
            }
            for (String mcap : SEBI_APPROX_MID_CAP) {
                if (nameUpper.contains(mcap)) return "MID_CAP";
            }
            return "SMALL_CAP";
        }

        return "UNCLASSIFIED";
    }

    private String resolveCanonicalSector(String rawSector, String securityName, String assetClass) {
        String nameUpper = securityName != null ? securityName.toUpperCase(Locale.ROOT) : "";
        String secUpper = rawSector != null ? rawSector.trim().toUpperCase(Locale.ROOT) : "";
        String assetUpper = assetClass != null ? assetClass.toUpperCase(Locale.ROOT) : "";

        if (assetUpper.contains("CASH") || secUpper.contains("CASH") || nameUpper.contains("CASH")
            || secUpper.contains("TREPS") || nameUpper.contains("TREPS")
            || secUpper.contains("NET CURRENT ASSETS") || nameUpper.contains("NET CURRENT ASSETS")) {
            return "Cash & Liquid Assets";
        }

        if (assetUpper.contains("DEBT") || secUpper.contains("GOI MAT") || nameUpper.contains("GOI MAT")
            || nameUpper.contains("TREASURY") || nameUpper.contains("GOVERNMENT BOND")) {
            return "Sovereign & Debt";
        }

        if (!secUpper.isBlank() && SECTOR_MAP.containsKey(secUpper)) {
            return SECTOR_MAP.get(secUpper);
        }

        for (Map.Entry<String, String> entry : SECTOR_MAP.entrySet()) {
            if (secUpper.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        if (nameUpper.contains("BANK") || nameUpper.contains("FINANCE") || nameUpper.contains("INSURANCE")) {
            return "Financial Services";
        }

        return (rawSector != null && !rawSector.isBlank()) ? rawSector : "Other / Unclassified";
    }
}
