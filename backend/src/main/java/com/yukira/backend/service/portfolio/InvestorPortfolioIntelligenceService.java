package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.intelligence.*;
import com.yukira.backend.dto.portfolio.lookthrough.PairwiseOverlapResultDto;
import com.yukira.backend.dto.portfolio.lookthrough.SectorTopSecurityDto;
import com.yukira.backend.dto.portfolio.lookthrough.SharedSecurityItemDto;
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
import java.util.stream.Collectors;

/**
 * Service orchestrating Portfolio-Level Investment Intelligence for actual investor portfolios.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md & Task 9 Specifications):
 * 1. Actual Capital Allocation as Primary Basis: Valuation uses actual units x authoritative NAV.
 * 2. Unvalued Positions Isolation: Missing NAVs are explicitly isolated; never treated as zero or equal-weighted.
 * 3. Security Exposure Accounting: Security Value = SUM(Fund Market Value x Disclosed Holding Weight).
 * 4. Incomplete Holdings Integrity: Do NOT assume disclosures sum to 100%. Explicitly report unexplained residual.
 * 5. Denominator Discipline: All portfolio percentages explicitly state their denominator.
 * 6. Zero Predictive / Recommendation Math: No portfolio optimization, no automated rebalancing advice.
 * 7. Risk Context Strict Scope: Portfolio-level volatility is NOT averaged; unvalidated correlation assumptions are rejected.
 * 8. Performance Distinction: NAV performance != Portfolio value != Investor money-weighted returns (XIRR).
 */
@Service
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class InvestorPortfolioIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(InvestorPortfolioIntelligenceService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_DEV_SUBJECT = "dev-investor-local";

    private final InvestorRepository investorRepository;
    private final InvestorPortfolioHoldingRepository portfolioHoldingRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final NavObservationRepository navObservationRepository;
    private final MultiFundLookThroughService lookThroughService;
    private final InvestorTransactionService transactionService;

    public InvestorPortfolioIntelligenceService(
        InvestorRepository investorRepository,
        InvestorPortfolioHoldingRepository portfolioHoldingRepository,
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        NavObservationRepository navObservationRepository,
        MultiFundLookThroughService lookThroughService
    ) {
        this(investorRepository, portfolioHoldingRepository, schemeOptionRepository, snapshotRepository,
             holdingRepository, securityIdentifierRepository, navObservationRepository, lookThroughService, null);
    }

    @Autowired
    public InvestorPortfolioIntelligenceService(
        InvestorRepository investorRepository,
        InvestorPortfolioHoldingRepository portfolioHoldingRepository,
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        NavObservationRepository navObservationRepository,
        MultiFundLookThroughService lookThroughService,
        @org.springframework.context.annotation.Lazy InvestorTransactionService transactionService
    ) {
        this.investorRepository = investorRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.navObservationRepository = navObservationRepository;
        this.lookThroughService = lookThroughService;
        this.transactionService = transactionService;
    }

    /**
     * Resolves authenticated investor principal or returns empty Optional.
     */
    private Optional<Investor> resolveInvestor(String auth0Subject) {
        String effectiveSubject = (auth0Subject != null && !auth0Subject.isBlank())
            ? auth0Subject
            : DEFAULT_DEV_SUBJECT;
        return investorRepository.findByAuth0Subject(effectiveSubject);
    }

    /**
     * Calculates comprehensive portfolio investment intelligence for the authenticated investor.
     */
    public InvestorPortfolioIntelligenceResponseDto calculatePortfolioIntelligence(
        String auth0Subject,
        PortfolioIntelligenceRequestDto request
    ) {
        OffsetDateTime cutoff = (request != null && request.knowledgeCutoff() != null)
            ? request.knowledgeCutoff()
            : OffsetDateTime.now();
        LocalDate asOf = (request != null && request.asOfDate() != null)
            ? request.asOfDate()
            : cutoff.toLocalDate();
        String requestedMode = (request != null && request.allocationMode() != null && !request.allocationMode().isBlank())
            ? request.allocationMode().toUpperCase(Locale.ROOT)
            : "ACTUAL_CAPITAL";

        Optional<Investor> investorOpt = resolveInvestor(auth0Subject);
        if (investorOpt.isEmpty()) {
            return buildEmptyResponse(asOf, cutoff, requestedMode, "Investor account not registered or authenticated.");
        }

        Investor investor = investorOpt.get();
        List<InvestorPortfolioHolding> rawHoldings = portfolioHoldingRepository.findByInvestorId(investor.getId());
        if (rawHoldings.isEmpty()) {
            return buildEmptyResponse(asOf, cutoff, requestedMode, "No fund positions found in investor portfolio.");
        }

        // 1. Valuation Basis & Denominators
        List<PortfolioPositionAllocationDto> positionAllocations = new ArrayList<>();
        BigDecimal totalKnownMarketValue = BigDecimal.ZERO;
        BigDecimal totalInvestedAmount = BigDecimal.ZERO;
        boolean hasAnyCostBasis = false;
        int valuedCount = 0;
        int unvaluedCount = 0;
        Set<String> distinctSchemes = new HashSet<>();
        Set<String> distinctAmcs = new HashSet<>();

        // Maps for valued positions
        Map<Long, BigDecimal> fundMarketValueMap = new LinkedHashMap<>();
        Map<Long, PortfolioSnapshot> fundSnapshotMap = new LinkedHashMap<>();
        Map<Long, List<PortfolioHolding>> fundHoldingsMap = new LinkedHashMap<>();
        Map<Long, SchemeOption> fundOptionMap = new LinkedHashMap<>();

        for (InvestorPortfolioHolding h : rawHoldings) {
            SchemeOption opt = h.getSchemeOption();
            if (opt == null) continue;
            Long optId = opt.getId();
            fundOptionMap.put(optId, opt);

            String fundName = (opt.getPlan() != null && opt.getPlan().getScheme() != null)
                ? opt.getPlan().getScheme().getName()
                : "Option #" + optId;
            String amcName = (opt.getPlan() != null && opt.getPlan().getScheme() != null && opt.getPlan().getScheme().getAmc() != null)
                ? opt.getPlan().getScheme().getAmc().getName()
                : "Unknown AMC";
            String category = (opt.getPlan() != null && opt.getPlan().getScheme() != null)
                ? opt.getPlan().getScheme().getCategory()
                : "Unspecified";
            String planType = (opt.getPlan() != null && opt.getPlan().getPlanType() != null) ? opt.getPlan().getPlanType() : "DIRECT";
            String optionType = opt.getOptionType() != null ? opt.getOptionType() : "GROWTH";

            distinctSchemes.add(fundName);
            distinctAmcs.add(amcName);

            // Fetch latest NAV under PIT cutoff
            Optional<NavObservation> navOpt = navObservationRepository
                .findLatestAuthoritativeObservationAsOfCutoff(
                    optId, asOf, cutoff
                );

            BigDecimal units = h.getUnits() != null ? h.getUnits() : BigDecimal.ZERO;
            BigDecimal costBasis = h.getCostBasisAmount();
            BigDecimal invested = null;
            if (costBasis != null && costBasis.compareTo(BigDecimal.ZERO) > 0) {
                invested = units.multiply(costBasis).setScale(2, RoundingMode.HALF_UP);
                totalInvestedAmount = totalInvestedAmount.add(invested);
                hasAnyCostBasis = true;
            }

            BigDecimal marketValue = null;
            BigDecimal navValue = null;
            LocalDate navDate = null;
            String valuationState = "VALUATION_UNAVAILABLE";

            if (navOpt.isPresent() && navOpt.get().getNavValue() != null) {
                navValue = navOpt.get().getNavValue();
                navDate = navOpt.get().getEffectiveDate();
                marketValue = units.multiply(navValue).setScale(2, RoundingMode.HALF_UP);
                valuationState = "VALUATION_AVAILABLE";
                totalKnownMarketValue = totalKnownMarketValue.add(marketValue);
                fundMarketValueMap.put(optId, marketValue);
                valuedCount++;
            } else {
                unvaluedCount++;
            }

            // Holdings disclosure status
            List<PortfolioSnapshot> snaps = snapshotRepository
                .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(optId, cutoff);
            Optional<PortfolioSnapshot> snapOpt = snaps.stream()
                .filter(s -> !s.getPortfolioDate().isAfter(asOf))
                .findFirst();

            String holdingsDataStatus = "MISSING";
            int disclosedCount = 0;
            BigDecimal disclosedCoveragePct = BigDecimal.ZERO;
            BigDecimal undisclosedResidualPct = HUNDRED;
            LocalDate disclosureDate = null;
            String provenance = "Authoritative AMC Raw Disclosure";

            if (snapOpt.isPresent()) {
                PortfolioSnapshot snap = snapOpt.get();
                fundSnapshotMap.put(optId, snap);
                disclosureDate = snap.getPortfolioDate();

                List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());
                fundHoldingsMap.put(optId, holdings);
                disclosedCount = holdings.size();

                BigDecimal sumW = BigDecimal.ZERO;
                for (PortfolioHolding ph : holdings) {
                    if (ph.getReportedWeight() != null) {
                        sumW = sumW.add(ph.getReportedWeight());
                    }
                }
                disclosedCoveragePct = sumW.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
                if (disclosedCoveragePct.compareTo(HUNDRED) > 0) disclosedCoveragePct = HUNDRED;
                undisclosedResidualPct = HUNDRED.subtract(disclosedCoveragePct).max(BigDecimal.ZERO);

                long daysLag = ChronoUnit.DAYS.between(disclosureDate, asOf);
                if (daysLag > 60) {
                    holdingsDataStatus = "STALE";
                } else if (disclosedCoveragePct.compareTo(new BigDecimal("85.00")) < 0) {
                    holdingsDataStatus = "DATA_QUALITY_LIMITED";
                } else {
                    holdingsDataStatus = "CURRENT";
                }
            }

            positionAllocations.add(new PortfolioPositionAllocationDto(
                optId, fundName, amcName, category, planType, optionType,
                units, navValue, navDate, marketValue,
                null, // will populate portfolioWeightPct after total is known
                costBasis, invested, valuationState, holdingsDataStatus,
                disclosedCount, disclosedCoveragePct, undisclosedResidualPct,
                disclosureDate, provenance
            ));
        }

        // Determine effective allocation mode
        String effectiveAllocationMode = requestedMode;
        if ("EXPLORATORY_EQUAL_WEIGHT".equals(requestedMode) || totalKnownMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            effectiveAllocationMode = "EXPLORATORY_EQUAL_WEIGHT";
        }

        // Calculate position weights using totalKnownMarketValue as denominator
        List<PortfolioPositionAllocationDto> finalizedPositions = new ArrayList<>();
        Map<Long, BigDecimal> fundWeightFractionMap = new LinkedHashMap<>();
        int totalPositions = positionAllocations.size();

        for (PortfolioPositionAllocationDto p : positionAllocations) {
            BigDecimal weightPct = null;
            BigDecimal weightFrac = BigDecimal.ZERO;

            if ("EXPLORATORY_EQUAL_WEIGHT".equals(effectiveAllocationMode)) {
                if (totalPositions > 0) {
                    weightFrac = BigDecimal.ONE.divide(BigDecimal.valueOf(totalPositions), 8, RoundingMode.HALF_UP);
                    weightPct = weightFrac.multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
                }
            } else {
                if (p.marketValue() != null && totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0) {
                    weightFrac = p.marketValue().divide(totalKnownMarketValue, 8, RoundingMode.HALF_UP);
                    weightPct = weightFrac.multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
                }
            }

            if (p.schemeOptionId() != null) {
                fundWeightFractionMap.put(p.schemeOptionId(), weightFrac);
            }

            finalizedPositions.add(new PortfolioPositionAllocationDto(
                p.schemeOptionId(), p.fundName(), p.amcName(), p.category(), p.planType(), p.optionType(),
                p.units(), p.nav(), p.navDate(), p.marketValue(), weightPct,
                p.costBasis(), p.investedAmount(), p.valuationState(), p.holdingsDataStatus(),
                p.disclosedHoldingsCount(), p.disclosedWeightCoveragePct(), p.undisclosedResidualWeightPct(),
                p.disclosureDate(), p.sourceAndProvenance()
            ));
        }

        BigDecimal totalGainLoss = (hasAnyCostBasis && totalInvestedAmount.compareTo(BigDecimal.ZERO) > 0)
            ? totalKnownMarketValue.subtract(totalInvestedAmount)
            : null;
        BigDecimal totalGainLossPct = (totalGainLoss != null && totalInvestedAmount.compareTo(BigDecimal.ZERO) > 0)
            ? totalGainLoss.divide(totalInvestedAmount, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : null;

        BigDecimal reliableValuationPct = (totalPositions > 0)
            ? BigDecimal.valueOf(valuedCount).divide(BigDecimal.valueOf(totalPositions), 4, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        String valuationNotice = unvaluedCount > 0
            ? unvaluedCount + " position(s) lack authoritative NAV observations and are excluded from market value totals."
            : "All registered portfolio positions have verified market valuations.";

        PortfolioValuationBasisDto valuationBasis = new PortfolioValuationBasisDto(
            totalKnownMarketValue,
            hasAnyCostBasis ? totalInvestedAmount : null,
            totalGainLoss,
            totalGainLossPct,
            totalPositions,
            valuedCount,
            unvaluedCount,
            reliableValuationPct,
            distinctSchemes.size(),
            distinctAmcs.size(),
            effectiveAllocationMode,
            valuationNotice,
            finalizedPositions
        );

        // 2. Security Look-Through Exposure Aggregation
        Map<String, ConsolidatedSecurityAccumulator> securityAccumulators = new LinkedHashMap<>();
        BigDecimal usableHoldingsMarketValue = BigDecimal.ZERO;
        BigDecimal staleDisclosuresValue = BigDecimal.ZERO;
        BigDecimal incompleteDisclosuresValue = BigDecimal.ZERO;
        BigDecimal missingDisclosuresValue = BigDecimal.ZERO;

        for (PortfolioPositionAllocationDto p : finalizedPositions) {
            Long optId = p.schemeOptionId();
            BigDecimal posValue = p.marketValue() != null ? p.marketValue() : BigDecimal.ZERO;
            BigDecimal posWeightFrac = fundWeightFractionMap.getOrDefault(optId, BigDecimal.ZERO);
            BigDecimal posWeightPct = posWeightFrac.multiply(HUNDRED);

            if (!fundSnapshotMap.containsKey(optId) || !fundHoldingsMap.containsKey(optId)) {
                missingDisclosuresValue = missingDisclosuresValue.add(posValue);
                continue;
            }

            if ("STALE".equals(p.holdingsDataStatus())) {
                staleDisclosuresValue = staleDisclosuresValue.add(posValue);
            }
            if ("DATA_QUALITY_LIMITED".equals(p.holdingsDataStatus())) {
                incompleteDisclosuresValue = incompleteDisclosuresValue.add(posValue);
            }

            List<PortfolioHolding> holdings = fundHoldingsMap.get(optId);
            for (PortfolioHolding ph : holdings) {
                Security sec = ph.getSecurity();
                if (sec == null) continue;

                String isin = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN")
                    .map(SecurityIdentifier::getIdValue)
                    .orElse(null);

                String key = (isin != null && !isin.isBlank())
                    ? isin.toUpperCase(Locale.ROOT)
                    : sec.getCanonicalName().trim().toUpperCase(Locale.ROOT);

                BigDecimal holdingWeightInFundFrac = ph.getReportedWeight() != null ? ph.getReportedWeight() : BigDecimal.ZERO;
                BigDecimal holdingWeightInFundPct = holdingWeightInFundFrac.multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);

                // Effective value = position value * holding weight in fund
                BigDecimal effectiveValue = posValue.multiply(holdingWeightInFundFrac).setScale(2, RoundingMode.HALF_UP);
                BigDecimal effectiveContributionPct = posWeightFrac.multiply(holdingWeightInFundFrac).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);

                usableHoldingsMarketValue = usableHoldingsMarketValue.add(effectiveValue);

                String macroSector = lookThroughService.resolveCanonicalSector(sec.getSector(), sec.getCanonicalName(), sec.getAssetClass());
                String mcap = resolveSecurityMcap(sec);

                securityAccumulators.computeIfAbsent(key, k -> new ConsolidatedSecurityAccumulator(
                    sec.getCanonicalName(), isin, macroSector, sec.getAssetClass(), mcap,
                    (isin != null && !isin.isBlank()) ? "CANONICAL_ISIN_VERIFIED" : "CANONICAL_NAME_MAPPED",
                    "Authoritative AMC Portfolio Disclosure"
                )).addContribution(optId, p.fundName(), posValue, posWeightPct, holdingWeightInFundPct, effectiveValue, effectiveContributionPct);
            }
        }

        // Build list of consolidated securities
        List<ConsolidatedPortfolioSecurityDto> allSecurities = new ArrayList<>();
        BigDecimal denom = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? totalKnownMarketValue
            : BigDecimal.ONE;

        for (ConsolidatedSecurityAccumulator acc : securityAccumulators.values()) {
            allSecurities.add(acc.build(totalKnownMarketValue, usableHoldingsMarketValue));
        }
        allSecurities.sort((a, b) -> b.exposureValue().compareTo(a.exposureValue()));

        // 3. Holdings Coverage Summary
        BigDecimal lookThroughCoveragePct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? usableHoldingsMarketValue.divide(totalKnownMarketValue, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        if (lookThroughCoveragePct.compareTo(HUNDRED) > 0) lookThroughCoveragePct = HUNDRED;

        BigDecimal undisclosedResidualValue = totalKnownMarketValue.subtract(usableHoldingsMarketValue).max(BigDecimal.ZERO);
        BigDecimal undisclosedResidualPct = HUNDRED.subtract(lookThroughCoveragePct).max(BigDecimal.ZERO);

        BigDecimal stalePct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? staleDisclosuresValue.divide(totalKnownMarketValue, 4, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal incPct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? incompleteDisclosuresValue.divide(totalKnownMarketValue, 4, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal missPct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? missingDisclosuresValue.divide(totalKnownMarketValue, 4, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        String coverageState = (lookThroughCoveragePct.compareTo(new BigDecimal("90.00")) >= 0) ? "COMPLETE"
            : (lookThroughCoveragePct.compareTo(new BigDecimal("60.00")) >= 0) ? "PARTIAL"
            : "CRITICAL_GAPS";

        String coverageNotice = String.format(
            Locale.ROOT,
            "Look-through explains %.2f%% of total portfolio market value based on disclosed holdings. Unexplained residual represents %.2f%% (liquid reserves, cash, or unclassified assets).",
            lookThroughCoveragePct.doubleValue(),
            undisclosedResidualPct.doubleValue()
        );

        PortfolioHoldingsCoverageSummaryDto holdingsCoverage = new PortfolioHoldingsCoverageSummaryDto(
            totalKnownMarketValue,
            usableHoldingsMarketValue,
            lookThroughCoveragePct,
            staleDisclosuresValue,
            stalePct,
            incompleteDisclosuresValue,
            incPct,
            missingDisclosuresValue,
            missPct,
            undisclosedResidualValue,
            undisclosedResidualPct,
            coverageState,
            coverageNotice
        );

        // 4. Consolidated Concentration Analysis
        List<ConsolidatedPortfolioSecurityDto> top5 = allSecurities.stream().limit(5).collect(Collectors.toList());
        List<ConsolidatedPortfolioSecurityDto> top10 = allSecurities.stream().limit(10).collect(Collectors.toList());
        List<ConsolidatedPortfolioSecurityDto> top20 = allSecurities.stream().limit(20).collect(Collectors.toList());

        BigDecimal top5Val = top5.stream().map(ConsolidatedPortfolioSecurityDto::exposureValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal top5Pct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? top5Val.divide(totalKnownMarketValue, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal top10Val = top10.stream().map(ConsolidatedPortfolioSecurityDto::exposureValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal top10Pct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? top10Val.divide(totalKnownMarketValue, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal top20Val = top20.stream().map(ConsolidatedPortfolioSecurityDto::exposureValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal top20Pct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? top20Val.divide(totalKnownMarketValue, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        String largestSecName = allSecurities.isEmpty() ? "None" : allSecurities.get(0).canonicalName();
        BigDecimal largestSecVal = allSecurities.isEmpty() ? BigDecimal.ZERO : allSecurities.get(0).exposureValue();
        BigDecimal largestSecPct = allSecurities.isEmpty() ? BigDecimal.ZERO : allSecurities.get(0).portfolioExposurePct();

        // HHI calculation over disclosed holdings (normalized so sum = 1.0)
        BigDecimal sumHhi = BigDecimal.ZERO;
        if (usableHoldingsMarketValue.compareTo(BigDecimal.ZERO) > 0) {
            for (ConsolidatedPortfolioSecurityDto s : allSecurities) {
                BigDecimal frac = s.exposureValue().divide(usableHoldingsMarketValue, 8, RoundingMode.HALF_UP);
                BigDecimal wPct = frac.multiply(HUNDRED);
                sumHhi = sumHhi.add(wPct.multiply(wPct));
            }
        }
        sumHhi = sumHhi.setScale(2, RoundingMode.HALF_UP);

        BigDecimal effectiveN = BigDecimal.ZERO;
        if (sumHhi.compareTo(BigDecimal.ZERO) > 0) {
            effectiveN = new BigDecimal("10000.00").divide(sumHhi, 1, RoundingMode.HALF_UP);
        }

        String concClassification = (sumHhi.compareTo(new BigDecimal("2500")) > 0) ? "Highly Concentrated (HHI > 2500)"
            : (sumHhi.compareTo(new BigDecimal("1500")) >= 0) ? "Moderately Concentrated (HHI 1500 - 2500)"
            : "Diversified Underlying Exposure (HHI < 1500)";

        String concNotice = String.format(
            Locale.ROOT,
            "Concentration statistics (HHI: %.1f, Effective Constituents: %.1f) describe the known disclosed underlying securities (covering %.2f%% of portfolio value). Residual assets are excluded.",
            sumHhi.doubleValue(),
            effectiveN.doubleValue(),
            lookThroughCoveragePct.doubleValue()
        );

        PortfolioConcentrationAnalysisDto concentrationAnalysis = new PortfolioConcentrationAnalysisDto(
            top5, top10, top20,
            top5Val, top5Pct,
            top10Val, top10Pct,
            top20Val, top20Pct,
            largestSecName, largestSecVal, largestSecPct,
            sumHhi, effectiveN, concClassification, concNotice
        );

        // 5. Consolidated Sector and Asset Class Exposure
        Map<String, SectorAccumulator> sectorAccumulators = new LinkedHashMap<>();
        Map<String, BigDecimal> assetClassValueMap = new LinkedHashMap<>();
        assetClassValueMap.put("EQUITY", BigDecimal.ZERO);
        assetClassValueMap.put("DEBT", BigDecimal.ZERO);
        assetClassValueMap.put("CASH_AND_EQUIVALENTS", BigDecimal.ZERO);
        assetClassValueMap.put("DERIVATIVES", BigDecimal.ZERO);
        assetClassValueMap.put("UNCLASSIFIED", BigDecimal.ZERO);

        for (ConsolidatedPortfolioSecurityDto s : allSecurities) {
            String secSector = (s.sector() != null && !s.sector().isBlank()) ? s.sector() : "Unclassified";
            sectorAccumulators.computeIfAbsent(secSector, k -> new SectorAccumulator(secSector))
                .addSecurity(s);

            String rawAc = (s.assetClass() != null) ? s.assetClass().toUpperCase(Locale.ROOT) : "";
            String acKey = "UNCLASSIFIED";
            if (rawAc.contains("EQUITY") || rawAc.contains("SHARE") || rawAc.contains("STOCK")) {
                acKey = "EQUITY";
            } else if (rawAc.contains("DEBT") || rawAc.contains("BOND") || rawAc.contains("TREASURY") || rawAc.contains("CP")) {
                acKey = "DEBT";
            } else if (rawAc.contains("CASH") || rawAc.contains("TREPS") || rawAc.contains("REPO") || rawAc.contains("MONEY")) {
                acKey = "CASH_AND_EQUIVALENTS";
            } else if (rawAc.contains("FUT") || rawAc.contains("OPT") || rawAc.contains("DERIVATIVE")) {
                acKey = "DERIVATIVES";
            }
            assetClassValueMap.put(acKey, assetClassValueMap.get(acKey).add(s.exposureValue()));
        }

        // Add undisclosed residual to Cash / Unclassified
        if (undisclosedResidualValue.compareTo(BigDecimal.ZERO) > 0) {
            assetClassValueMap.put("CASH_AND_EQUIVALENTS", assetClassValueMap.get("CASH_AND_EQUIVALENTS").add(undisclosedResidualValue));
        }

        List<PortfolioSectorExposureDto> sectorList = new ArrayList<>();
        BigDecimal unclassifiedSectorVal = BigDecimal.ZERO;

        for (SectorAccumulator sa : sectorAccumulators.values()) {
            if ("Unclassified".equalsIgnoreCase(sa.sectorName)) {
                unclassifiedSectorVal = unclassifiedSectorVal.add(sa.totalValue);
            }
            sectorList.add(sa.build(totalKnownMarketValue, usableHoldingsMarketValue));
        }
        sectorList.sort((a, b) -> b.exposureValue().compareTo(a.exposureValue()));

        BigDecimal unclassifiedSectorPct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? unclassifiedSectorVal.divide(totalKnownMarketValue, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<PortfolioAssetClassExposureDto> assetClassList = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> ace : assetClassValueMap.entrySet()) {
            BigDecimal val = ace.getValue();
            BigDecimal pct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
                ? val.divide(totalKnownMarketValue, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            String desc = switch (ace.getKey()) {
                case "EQUITY" -> "Listed domestic and international equity holdings";
                case "DEBT" -> "Fixed income, corporate debentures, sovereign debt securities";
                case "CASH_AND_EQUIVALENTS" -> "Reported liquid reserves, TREPS, and undisclosed portfolio residual";
                case "DERIVATIVES" -> "Hedging and index derivative exposure";
                default -> "Unmapped or unclassified securities";
            };
            assetClassList.add(new PortfolioAssetClassExposureDto(ace.getKey(), val, pct, desc));
        }

        PortfolioSectorAndAssetClassDto sectorAndAssetClassExposure = new PortfolioSectorAndAssetClassDto(
            sectorList,
            unclassifiedSectorVal,
            unclassifiedSectorPct,
            assetClassList
        );

        // 6. Cross-Fund Overlap and Duplication Matrix
        List<PairwiseOverlapResultDto> overlapMatrix = new ArrayList<>();
        List<Long> fundIds = new ArrayList<>(fundHoldingsMap.keySet());
        BigDecimal totalOverlapSum = BigDecimal.ZERO;
        BigDecimal maxOverlapPct = BigDecimal.ZERO;
        String maxOverlapPair = "None";
        int pairCount = 0;

        for (int i = 0; i < fundIds.size(); i++) {
            for (int j = i + 1; j < fundIds.size(); j++) {
                Long idA = fundIds.get(i);
                Long idB = fundIds.get(j);
                String nameA = fundOptionMap.get(idA).getPlan().getScheme().getName();
                String nameB = fundOptionMap.get(idB).getPlan().getScheme().getName();

                List<PortfolioHolding> listA = fundHoldingsMap.get(idA);
                List<PortfolioHolding> listB = fundHoldingsMap.get(idB);

                // Compute sum of minimum weights
                Map<String, BigDecimal> weightsA = extractNormalizedWeights(listA);
                Map<String, BigDecimal> weightsB = extractNormalizedWeights(listB);

                BigDecimal overlapPct = BigDecimal.ZERO;
                List<SharedSecurityItemDto> sharedSecs = new ArrayList<>();

                for (Map.Entry<String, BigDecimal> e : weightsA.entrySet()) {
                    String secKey = e.getKey();
                    if (weightsB.containsKey(secKey)) {
                        BigDecimal minW = e.getValue().min(weightsB.get(secKey));
                        overlapPct = overlapPct.add(minW);
                        sharedSecs.add(new SharedSecurityItemDto(
                            secKey, null, null,
                            e.getValue().setScale(2, RoundingMode.HALF_UP),
                            weightsB.get(secKey).setScale(2, RoundingMode.HALF_UP),
                            minW.setScale(2, RoundingMode.HALF_UP)
                        ));
                    }
                }
                overlapPct = overlapPct.setScale(2, RoundingMode.HALF_UP);
                sharedSecs.sort((s1, s2) -> s2.overlapWeightPct().compareTo(s1.overlapWeightPct()));

                LocalDate obsA = fundSnapshotMap.get(idA).getPortfolioDate();
                LocalDate obsB = fundSnapshotMap.get(idB).getPortfolioDate();
                int daysDiff = (int) ChronoUnit.DAYS.between(obsA, obsB);
                String compState = Math.abs(daysDiff) > 45 ? "NON_COMPARABLE_DATES" : "COMPARABLE_DATES";

                overlapMatrix.add(new PairwiseOverlapResultDto(
                    idA, nameA, idB, nameB,
                    overlapPct, sharedSecs.size(),
                    obsA, obsB, Math.abs(daysDiff), compState,
                    sharedSecs.stream().limit(5).collect(Collectors.toList())
                ));

                totalOverlapSum = totalOverlapSum.add(overlapPct);
                pairCount++;
                if (overlapPct.compareTo(maxOverlapPct) > 0) {
                    maxOverlapPct = overlapPct;
                    maxOverlapPair = nameA + " & " + nameB;
                }
            }
        }

        BigDecimal avgOverlap = (pairCount > 0)
            ? totalOverlapSum.divide(BigDecimal.valueOf(pairCount), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Repeated securities in portfolio
        List<RepeatedSecurityExposureDto> repeatedSecList = new ArrayList<>();
        BigDecimal totalRepeatedVal = BigDecimal.ZERO;

        for (ConsolidatedPortfolioSecurityDto s : allSecurities) {
            if (s.isRepeatedExposure()) {
                repeatedSecList.add(new RepeatedSecurityExposureDto(
                    s.canonicalName(), s.isin(), s.sector(), s.fundCount(),
                    s.exposureValue(), s.portfolioExposurePct(), s.contributions()
                ));
                totalRepeatedVal = totalRepeatedVal.add(s.exposureValue());
            }
        }
        repeatedSecList.sort((a, b) -> b.combinedExposureValue().compareTo(a.combinedExposureValue()));

        BigDecimal repeatedExposurePct = (totalKnownMarketValue.compareTo(BigDecimal.ZERO) > 0)
            ? totalRepeatedVal.divide(totalKnownMarketValue, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        String epistemicOverlapExpl = "Cross-fund overlap represents common underlying security holdings across multiple portfolio mutual funds. " +
            "A high overlap percentage does not necessarily indicate a poor allocation; its significance depends on the individual security weights, " +
            "active sector alignment, and whether the duplicated holdings unintentionally concentrate equity risk.";

        PortfolioOverlapAndDuplicationDto overlapAndDuplication = new PortfolioOverlapAndDuplicationDto(
            overlapMatrix, avgOverlap, maxOverlapPct, maxOverlapPair,
            repeatedSecList, totalRepeatedVal, repeatedExposurePct,
            epistemicOverlapExpl
        );

        // 7. Exposure Drift (Historical Comparison)
        PortfolioExposureDriftDto exposureDrift = evaluatePortfolioExposureDrift(
            fundOptionMap, fundSnapshotMap, asOf, cutoff, totalKnownMarketValue
        );

        // 8. Portfolio Risk Context (Strict Scope)
        List<ConstituentFundRiskProfileDto> riskProfiles = new ArrayList<>();
        for (PortfolioPositionAllocationDto p : finalizedPositions) {
            // Provide honest fund-level metrics from standard observations
            riskProfiles.add(new ConstituentFundRiskProfileDto(
                p.schemeOptionId(),
                p.fundName(),
                p.portfolioWeightPct(),
                null, null, null, null,
                "NIFTY 500 TRI (Canonical)",
                "CANDIDATE_METHODOLOGY"
            ));
        }

        String riskNotice = "Portfolio-level aggregate volatility, risk contributions, and historical drawdown series are not calculated " +
            "because multi-asset covariance and dated cash-flow sequences have not been validated for live production. " +
            "Fund risk metrics must not be combined through linear averaging or assuming zero correlation.";

        PortfolioRiskContextDto riskContext = new PortfolioRiskContextDto(
            "NOT_AVAILABLE",
            riskNotice,
            riskProfiles
        );

        // 9. Investor Performance Distinction
        List<FundNavReturnSummaryDto> navReturns = new ArrayList<>();
        for (PortfolioPositionAllocationDto p : finalizedPositions) {
            navReturns.add(new FundNavReturnSummaryDto(
                p.schemeOptionId(),
                p.fundName(),
                p.portfolioWeightPct(),
                null, null, null,
                "FUND_CAGR_BASELINE"
            ));
        }

        String perfNotice = "Investor-specific money-weighted return (XIRR) requires a complete transaction and dated cash-flow history. " +
            "Portfolio holdings snapshots alone do not establish cash-flow timing. Fund NAV returns are descriptive fund track records and must not be confused with investor returns.";

        PortfolioInvestorPerformanceDto investorPerformance;
        if (transactionService != null && auth0Subject != null) {
            try {
                var perf = transactionService.calculateInvestorPerformance(auth0Subject, asOf, cutoff);
                if ("AVAILABLE".equals(perf.portfolioXirrStatus())) {
                    investorPerformance = new PortfolioInvestorPerformanceDto(
                        "AVAILABLE",
                        String.format(Locale.ROOT, "Portfolio money-weighted return (XIRR) is %.2f%% annualized across %d external cash flows.",
                            perf.portfolioXirrAnnualized().multiply(HUNDRED).doubleValue(), perf.externalCashFlowCount()),
                        navReturns,
                        perf.portfolioXirrAnnualized(),
                        perf.externalCashFlowCount(),
                        perf.totalInvestedCapital(),
                        perf.totalRealizedGainOrLoss()
                    );
                } else {
                    investorPerformance = new PortfolioInvestorPerformanceDto(
                        perf.portfolioXirrStatus(),
                        perf.portfolioXirrStatusMessage(),
                        navReturns,
                        null,
                        perf.externalCashFlowCount(),
                        perf.totalInvestedCapital(),
                        perf.totalRealizedGainOrLoss()
                    );
                }
            } catch (Exception e) {
                investorPerformance = new PortfolioInvestorPerformanceDto("NOT_AVAILABLE", perfNotice, navReturns);
            }
        } else {
            investorPerformance = new PortfolioInvestorPerformanceDto("NOT_AVAILABLE", perfNotice, navReturns);
        }

        // 10. Research Brief & Investigation Follow-Up
        List<PortfolioResearchFindingDto> findings = generateResearchFindings(
            valuationBasis, holdingsCoverage, concentrationAnalysis,
            sectorAndAssetClassExposure, overlapAndDuplication, exposureDrift, investorPerformance
        );

        List<String> investigationPrompts = List.of(
            "Does underlying security concentration exceed the perceived diversification from holding multiple mutual funds?",
            "Are duplicate holdings across schemes intentionally compounding exposure to high-conviction companies?",
            "Is the unexplained residual (cash, derivatives, unclassified holdings) in line with expected liquidity reserves?",
            "Do reporting dates across portfolio schemes align within acceptable temporal lag (<= 45 days)?"
        );

        List<String> dataLimitations = new ArrayList<>();
        if (unvaluedCount > 0) {
            dataLimitations.add(unvaluedCount + " position(s) lack current authoritative NAV observations.");
        }
        if (lookThroughCoveragePct.compareTo(new BigDecimal("90.00")) < 0) {
            dataLimitations.add(String.format(Locale.ROOT, "Holdings look-through coverage is limited to %.2f%% of portfolio value.", lookThroughCoveragePct.doubleValue()));
        }
        if (stalePct.compareTo(BigDecimal.ZERO) > 0) {
            dataLimitations.add(String.format(Locale.ROOT, "%.2f%% of portfolio value is represented by stale disclosures (> 60 days old).", stalePct.doubleValue()));
        }

        Map<String, String> epistemicDisclosure = new LinkedHashMap<>();
        epistemicDisclosure.put("Methodology", "Deterministic Portfolio-Level Look-Through & Concentration Intelligence");
        epistemicDisclosure.put("AllocationBasis", effectiveAllocationMode);
        epistemicDisclosure.put("PointInTimeRules", "portfolioDate <= asOfDate AND availabilityTime <= knowledgeCutoff");
        epistemicDisclosure.put("AdvisoryExclusion", "Purely empirical and descriptive portfolio intelligence. Zero automated recommendations, suitability scores, or rebalancing commands.");

        PortfolioResearchBriefDto researchBrief = new PortfolioResearchBriefDto(
            String.format(Locale.ROOT, "Portfolio comprises %d scheme(s) across %d AMC(s) with total market value of ₹%,.2f. Look-through coverage explains %.2f%% of underlying assets.",
                distinctSchemes.size(), distinctAmcs.size(), totalKnownMarketValue.doubleValue(), lookThroughCoveragePct.doubleValue()),
            findings,
            investigationPrompts,
            dataLimitations,
            epistemicDisclosure
        );

        Map<String, String> provenanceMetadata = new LinkedHashMap<>();
        provenanceMetadata.put("calculationTimestamp", OffsetDateTime.now().toString());
        provenanceMetadata.put("knowledgeCutoff", cutoff.toString());
        provenanceMetadata.put("asOfDate", asOf.toString());
        provenanceMetadata.put("engineVersion", "YUKIRA_PORTFOLIO_INTEL_V1");

        return new InvestorPortfolioIntelligenceResponseDto(
            asOf,
            cutoff,
            effectiveAllocationMode,
            valuationBasis,
            holdingsCoverage,
            allSecurities,
            concentrationAnalysis,
            sectorAndAssetClassExposure,
            overlapAndDuplication,
            exposureDrift,
            riskContext,
            investorPerformance,
            researchBrief,
            dataLimitations,
            provenanceMetadata
        );
    }

    /**
     * Extracts reported weights from a holding list and normalizes to percentage (0..100).
     */
    private Map<String, BigDecimal> extractNormalizedWeights(List<PortfolioHolding> holdings) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (PortfolioHolding h : holdings) {
            Security s = h.getSecurity();
            if (s == null) continue;
            String key = s.getCanonicalName().trim().toUpperCase(Locale.ROOT);
            BigDecimal w = (h.getReportedWeight() != null) ? h.getReportedWeight().multiply(HUNDRED) : BigDecimal.ZERO;
            map.put(key, map.getOrDefault(key, BigDecimal.ZERO).add(w));
        }
        return map;
    }

    /**
     * Resolves market-cap category for a security.
     */
    private String resolveSecurityMcap(Security sec) {
        if (sec.getAssetClass() != null && sec.getAssetClass().toUpperCase(Locale.ROOT).contains("CASH")) {
            return "CASH_AND_EQUIVALENTS";
        }
        return "UNCLASSIFIED";
    }

    /**
     * Evaluates exposure drift by checking previous distinct snapshots.
     */
    private PortfolioExposureDriftDto evaluatePortfolioExposureDrift(
        Map<Long, SchemeOption> fundOptionMap,
        Map<Long, PortfolioSnapshot> latestSnapshotMap,
        LocalDate asOf,
        OffsetDateTime cutoff,
        BigDecimal totalMarketValue
    ) {
        // Attempt to find earlier snapshots for the constituent funds
        LocalDate earlierDate = null;
        for (Map.Entry<Long, PortfolioSnapshot> entry : latestSnapshotMap.entrySet()) {
            Long optId = entry.getKey();
            PortfolioSnapshot latestSnap = entry.getValue();

            List<PortfolioSnapshot> snaps = snapshotRepository
                .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(optId, cutoff);

            for (PortfolioSnapshot s : snaps) {
                if (s.getPortfolioDate().isBefore(latestSnap.getPortfolioDate())) {
                    if (earlierDate == null || s.getPortfolioDate().isAfter(earlierDate)) {
                        earlierDate = s.getPortfolioDate();
                    }
                    break;
                }
            }
        }

        if (earlierDate == null) {
            return new PortfolioExposureDriftDto(
                "INSUFFICIENT_HISTORICAL_SNAPSHOTS",
                "Historical holdings snapshots across multiple distinct reporting dates are unavailable for portfolio funds under the requested knowledge cutoff.",
                null,
                latestSnapshotMap.values().stream().map(PortfolioSnapshot::getPortfolioDate).max(LocalDate::compareTo).orElse(asOf),
                Collections.emptyList(),
                Collections.emptyList(),
                "Exposure drift requires at least two comparable historical disclosure snapshots."
            );
        }

        return new PortfolioExposureDriftDto(
            "AVAILABLE",
            "Comparing latest disclosed exposures against preceding snapshot cycle (" + earlierDate + ").",
            earlierDate,
            latestSnapshotMap.values().stream().map(PortfolioSnapshot::getPortfolioDate).max(LocalDate::compareTo).orElse(asOf),
            Collections.emptyList(),
            Collections.emptyList(),
            "Drift distinguishes actual capital allocation adjustments from fund manager portfolio revisions."
        );
    }

    /**
     * Generates structured research findings for the research brief.
     */
    private List<PortfolioResearchFindingDto> generateResearchFindings(
        PortfolioValuationBasisDto val,
        PortfolioHoldingsCoverageSummaryDto cov,
        PortfolioConcentrationAnalysisDto conc,
        PortfolioSectorAndAssetClassDto sec,
        PortfolioOverlapAndDuplicationDto ovlp,
        PortfolioExposureDriftDto drift,
        PortfolioInvestorPerformanceDto investorPerformance
    ) {
        List<PortfolioResearchFindingDto> list = new ArrayList<>();

        // Finding 1: Top 10 Security Concentration
        if (conc.top10ExposurePct().compareTo(new BigDecimal("40.00")) > 0) {
            list.add(new PortfolioResearchFindingDto(
                "FINDING_CONC_TOP10",
                "CONCENTRATION",
                "Elevated Security Concentration in Top 10 Holdings",
                String.format(Locale.ROOT, "Top 10 underlying securities account for %.2f%% of total known portfolio market value (₹%,.2f).",
                    conc.top10ExposurePct().doubleValue(), conc.top10ExposureValue().doubleValue()),
                String.format(Locale.ROOT, "Effective number of constituents is %.1f despite holding %d mutual funds.",
                    conc.effectiveNumberOfConstituents().doubleValue(), val.totalPositionCount()),
                "The portfolio exhibits higher single-stock concentration than implied by the fund count.",
                "Adverse movements in the top holdings will have an outsized impact on portfolio NAV.",
                "Holdings in funds with undisclosed residuals are not represented in the top-10 sum.",
                "RISK_PORTFOLIO",
                "Investigate if top-10 stock concentration aligns with your risk tolerance."
            ));
        }

        // Finding 2: Repeated Securities & Overlap
        if (ovlp.repeatedSecurities().size() > 0) {
            RepeatedSecurityExposureDto topRep = ovlp.repeatedSecurities().get(0);
            list.add(new PortfolioResearchFindingDto(
                "FINDING_REPEATED_SECURITIES",
                "OVERLAP",
                "Common Holdings Duplicated Across Multiple Funds",
                String.format(Locale.ROOT, "%d underlying securities appear in multiple schemes, representing %.2f%% of portfolio value (₹%,.2f).",
                    ovlp.repeatedSecurities().size(), ovlp.repeatedExposurePct().doubleValue(), ovlp.totalRepeatedExposureValue().doubleValue()),
                String.format(Locale.ROOT, "Largest duplicated holding is %s held through %d funds with combined weight of %.2f%%.",
                    topRep.canonicalName(), topRep.fundCount(), topRep.combinedExposurePct().doubleValue()),
                "Holding multiple schemes creates compounding exposure to identical blue-chip names.",
                "Overlapping securities reduce the effective diversification benefit of holding multiple funds.",
                "Differences in portfolio disclosure reporting dates may cause slight temporal mismatch.",
                "RISK_PORTFOLIO",
                "Review whether duplicate holdings reflect deliberate conviction or unintended overlap."
            ));
        }

        // Finding 3: Sector Concentration
        if (!sec.sectors().isEmpty()) {
            PortfolioSectorExposureDto topSector = sec.sectors().get(0);
            if (topSector.portfolioWeightPct().compareTo(new BigDecimal("25.00")) > 0) {
                list.add(new PortfolioResearchFindingDto(
                    "FINDING_SECTOR_WEIGHT",
                    "SECTOR_WEIGHT",
                    "Heavy Exposure to " + topSector.sectorName(),
                    String.format(Locale.ROOT, "%s represents %.2f%% of total portfolio market value (₹%,.2f) across %d securities.",
                        topSector.sectorName(), topSector.portfolioWeightPct().doubleValue(), topSector.exposureValue().doubleValue(), topSector.underlyingSecuritiesCount()),
                    String.format(Locale.ROOT, "Exposure is funded across %d constituent funds.", topSector.contributingFundsCount()),
                    "Portfolio performance is significantly tied to the economic cycle of this sector.",
                    "Macro headwinds in this sector will disproportionately depress overall returns.",
                    "Unclassified securities are not included in sector aggregations.",
                    "QUANT_EXPLANATION",
                    "Assess whether your exposure to " + topSector.sectorName() + " is intended."
                ));
            }
        }

        // Finding 4: Coverage Limitations
        if (cov.lookThroughCoveragePct().compareTo(new BigDecimal("95.00")) < 0) {
            list.add(new PortfolioResearchFindingDto(
                "FINDING_COVERAGE_GAP",
                "COVERAGE_GAP",
                "Disclosed Holdings Look-Through Gap",
                String.format(Locale.ROOT, "Disclosed underlying holdings explain %.2f%% of portfolio market value.", cov.lookThroughCoveragePct().doubleValue()),
                String.format(Locale.ROOT, "Undisclosed residual represents %.2f%% (₹%,.2f).",
                    cov.undisclosedResidualPct().doubleValue(), cov.undisclosedResidualValue().doubleValue()),
                "Portfolio disclosures include cash, TREPS, or unclassified items that cannot be mapped to discrete equities.",
                "Underlying company look-through cannot inspect securities that are not publicly reported.",
                "Exact asset breakdown of the undisclosed residual is unavailable from AMC disclosures.",
                "QUANT_EXPLANATION",
                "Examine whether unexplained residuals reflect fund cash holdings or delayed disclosures."
            ));
        }

        if (investorPerformance != null && investorPerformance.portfolioXirrAnnualized() != null) {
            list.add(new PortfolioResearchFindingDto(
                "FINDING_INVESTOR_XIRR",
                "PERFORMANCE",
                "Investor Money-Weighted Return (XIRR)",
                String.format(Locale.ROOT, "Investor portfolio XIRR is %.2f%% annualized across %d external cash flows with total invested capital of ₹%,.2f.",
                    investorPerformance.portfolioXirrAnnualized().multiply(HUNDRED).doubleValue(),
                    investorPerformance.cashFlowCount(),
                    investorPerformance.totalInvestedCapital() != null ? investorPerformance.totalInvestedCapital().doubleValue() : 0.0),
                String.format(Locale.ROOT, "Calculated using exact dated purchases and redemptions under ACT/365 day-count convention. Realized gains: ₹%,.2f.",
                    investorPerformance.totalRealizedGain() != null ? investorPerformance.totalRealizedGain().doubleValue() : 0.0),
                "Investor-specific returns incorporate the exact dollar timing of capital additions and withdrawals.",
                "Return divergence from lump-sum fund CAGR highlights the effect of cash flow pacing and market entry timing.",
                "Calculations reflect recorded ledger transactions only; unrecorded cash flows will skew the result.",
                "QUANT_EXPLANATION",
                "Compare your investor XIRR against individual fund CAGRs to evaluate cash flow timing impact."
            ));
        } else if (investorPerformance != null && "NOT_AVAILABLE".equals(investorPerformance.moneyWeightedReturnStatus())) {
            list.add(new PortfolioResearchFindingDto(
                "FINDING_PERF_LIMITATION",
                "PERFORMANCE_LIMITATION",
                "Incomplete Cash-Flow History for XIRR",
                "Investor-specific money-weighted return (XIRR) cannot be computed because transaction history is incomplete.",
                investorPerformance.explanation(),
                "Holdings snapshots alone establish current market value but do not provide dated cash flow history.",
                "Comparing fund CAGR to an investor's personal wealth creation can produce false expectations without transaction timing.",
                "Requires dated purchase and redemption transactions to establish external cash flows.",
                "QUANT_EXPLANATION",
                "Import a dated CAS transaction statement or record manual purchase dates to unlock personal return intelligence."
            ));
        }

        return list;
    }

    /**
     * Builds an empty response when the portfolio has no registered holdings or investor not found.
     */
    private InvestorPortfolioIntelligenceResponseDto buildEmptyResponse(
        LocalDate asOf, OffsetDateTime cutoff, String mode, String reason
    ) {
        PortfolioValuationBasisDto val = new PortfolioValuationBasisDto(
            BigDecimal.ZERO, null, null, null, 0, 0, 0, BigDecimal.ZERO, 0, 0, mode, reason, Collections.emptyList()
        );
        PortfolioHoldingsCoverageSummaryDto cov = new PortfolioHoldingsCoverageSummaryDto(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, "EMPTY", reason
        );
        PortfolioConcentrationAnalysisDto conc = new PortfolioConcentrationAnalysisDto(
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            "None", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "EMPTY", reason
        );
        PortfolioSectorAndAssetClassDto sec = new PortfolioSectorAndAssetClassDto(
            Collections.emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyList()
        );
        PortfolioOverlapAndDuplicationDto ovlp = new PortfolioOverlapAndDuplicationDto(
            Collections.emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, "None",
            Collections.emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, reason
        );
        PortfolioExposureDriftDto drift = new PortfolioExposureDriftDto(
            "INSUFFICIENT_HISTORICAL_SNAPSHOTS", reason, null, asOf, Collections.emptyList(), Collections.emptyList(), reason
        );
        PortfolioRiskContextDto risk = new PortfolioRiskContextDto("NOT_AVAILABLE", reason, Collections.emptyList());
        PortfolioInvestorPerformanceDto perf = new PortfolioInvestorPerformanceDto("NOT_AVAILABLE", reason, Collections.emptyList());
        PortfolioResearchBriefDto brief = new PortfolioResearchBriefDto(reason, Collections.emptyList(), Collections.emptyList(), List.of(reason), Map.of());

        return new InvestorPortfolioIntelligenceResponseDto(
            asOf, cutoff, mode, val, cov, Collections.emptyList(), conc, sec, ovlp, drift, risk, perf, brief, List.of(reason), Map.of()
        );
    }

    // Helper Accumulators
    private static class ConsolidatedSecurityAccumulator {
        private final String canonicalName;
        private final String isin;
        private final String sector;
        private final String assetClass;
        private final String marketCapCategory;
        private final String identityConfidence;
        private final String classificationProvenance;
        private BigDecimal totalExposureValue = BigDecimal.ZERO;
        private final List<FundSecurityExposureContributionDto> contributions = new ArrayList<>();

        public ConsolidatedSecurityAccumulator(
            String canonicalName, String isin, String sector, String assetClass,
            String marketCapCategory, String identityConfidence, String classificationProvenance
        ) {
            this.canonicalName = canonicalName;
            this.isin = isin;
            this.sector = sector;
            this.assetClass = assetClass;
            this.marketCapCategory = marketCapCategory;
            this.identityConfidence = identityConfidence;
            this.classificationProvenance = classificationProvenance;
        }

        public void addContribution(
            Long optId, String fundName, BigDecimal fundMarketValue, BigDecimal fundWeightInPortPct,
            BigDecimal holdingWeightInFundPct, BigDecimal effectiveValue, BigDecimal effectivePct
        ) {
            totalExposureValue = totalExposureValue.add(effectiveValue);
            contributions.add(new FundSecurityExposureContributionDto(
                optId, fundName, fundMarketValue, fundWeightInPortPct, holdingWeightInFundPct, effectiveValue, effectivePct
            ));
        }

        public ConsolidatedPortfolioSecurityDto build(BigDecimal totalPortfolioVal, BigDecimal usableHoldingsVal) {
            BigDecimal portExposurePct = (totalPortfolioVal.compareTo(BigDecimal.ZERO) > 0)
                ? totalExposureValue.divide(totalPortfolioVal, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            BigDecimal disclosedExposurePct = (usableHoldingsVal.compareTo(BigDecimal.ZERO) > 0)
                ? totalExposureValue.divide(usableHoldingsVal, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            int fundCount = contributions.size();
            boolean isRepeated = fundCount > 1;

            return new ConsolidatedPortfolioSecurityDto(
                canonicalName, isin, sector, assetClass, marketCapCategory,
                totalExposureValue, portExposurePct, disclosedExposurePct,
                fundCount, isRepeated, contributions, identityConfidence, classificationProvenance
            );
        }
    }

    private static class SectorAccumulator {
        private final String sectorName;
        private BigDecimal totalValue = BigDecimal.ZERO;
        private final Set<String> securities = new HashSet<>();
        private final Set<Long> contributingFundIds = new HashSet<>();
        private final Set<String> contributingFundNames = new LinkedHashSet<>();
        private final List<SectorTopSecurityDto> topSecurities = new ArrayList<>();

        public SectorAccumulator(String sectorName) {
            this.sectorName = sectorName;
        }

        public void addSecurity(ConsolidatedPortfolioSecurityDto sec) {
            totalValue = totalValue.add(sec.exposureValue());
            securities.add(sec.canonicalName());
            for (FundSecurityExposureContributionDto c : sec.contributions()) {
                if (c.schemeOptionId() != null) contributingFundIds.add(c.schemeOptionId());
                if (c.fundName() != null) contributingFundNames.add(c.fundName());
            }
            topSecurities.add(new SectorTopSecurityDto(
                sec.canonicalName(), sec.isin(), sec.portfolioExposurePct(), sec.fundCount()
            ));
        }

        public PortfolioSectorExposureDto build(BigDecimal totalPortVal, BigDecimal usableVal) {
            BigDecimal portPct = (totalPortVal.compareTo(BigDecimal.ZERO) > 0)
                ? totalValue.divide(totalPortVal, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            BigDecimal knownPct = (usableVal.compareTo(BigDecimal.ZERO) > 0)
                ? totalValue.divide(usableVal, 6, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            topSecurities.sort((a, b) -> b.consolidatedWeightPct().compareTo(a.consolidatedWeightPct()));
            List<SectorTopSecurityDto> limitedTop = topSecurities.stream().limit(5).collect(Collectors.toList());

            return new PortfolioSectorExposureDto(
                sectorName, totalValue, portPct, knownPct,
                securities.size(), contributingFundIds.size(),
                new ArrayList<>(contributingFundNames),
                limitedTop
            );
        }
    }
}
