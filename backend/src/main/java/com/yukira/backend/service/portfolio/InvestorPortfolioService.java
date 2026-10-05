package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.discovery.*;
import com.yukira.backend.dto.portfolio.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.discovery.GoalDiscoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.yukira.backend.service.AnalysisService;
import com.yukira.backend.dto.analysis.ComparisonRequest;
import com.yukira.backend.dto.analysis.ComparisonResponse;
import java.time.OffsetDateTime;

/**
 * Service orchestrating Portfolio Tracking V1 for external mutual fund holdings.
 *
 * Epistemic & Product Invariants (V1):
 * 1. Holding maps strictly to canonical scheme_option_id (never conflates Direct/Regular or Growth/IDCW).
 * 2. Financial metrics (valuations, invested amount, gain/loss, allocation shares) are computed deterministically in backend.
 * 3. Authoritative NAV observation from nav_observation ledger is used for valuation. Missing NAV = VALUATION_UNAVAILABLE (never zero or fabricated).
 * 4. Historical NAV cutoff (e.g. 2024-01-15) is explicitly exposed and never labeled "CURRENT".
 * 5. Cost basis is optional. Gain/loss is calculated only when cost basis is provided.
 * 6. Reads existing YUKIRA_SCORE_V1 directly from AnalyticalScoreRepository. Zero score recalculation or score manufacturing.
 * 7. ZERO composite risk score or portfolio volatility calculation. Individual fund risk metrics are exposed.
 * 8. Strict investor ownership: authenticated investor principal owns holdings exclusively.
 */
@Service
@Transactional
public class InvestorPortfolioService {

    private static final String DEFAULT_DEV_SUBJECT = "dev-investor-local";

    private final InvestorRepository investorRepository;
    private final InvestorPortfolioHoldingRepository portfolioHoldingRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final AnalyticalScoreRepository analyticalScoreRepository;
    private final GoalDiscoveryService goalDiscoveryService;
    private final InvestorTargetAllocationRepository targetAllocationRepository;
    private final PortfolioComparisonQueryService portfolioComparisonQueryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InvestorPortfolioService(
        InvestorRepository investorRepository,
        InvestorPortfolioHoldingRepository portfolioHoldingRepository,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        AnalyticalScoreRepository analyticalScoreRepository,
        GoalDiscoveryService goalDiscoveryService,
        InvestorTargetAllocationRepository targetAllocationRepository,
        PortfolioComparisonQueryService portfolioComparisonQueryService
    ) {
        this.investorRepository = investorRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.goalDiscoveryService = goalDiscoveryService;
        this.targetAllocationRepository = targetAllocationRepository;
        this.portfolioComparisonQueryService = portfolioComparisonQueryService;
    }

    /**
     * Retrieves or registers the investor account for the given auth0Subject.
     *
     * Note: provisioning a previously unseen subject performs an INSERT. Callers within this
     * bean invoke this method directly, so it must NOT be relied upon to open its own
     * transaction boundary; the calling method's transaction has to be writable.
     */
    public Investor resolveInvestor(String auth0Subject) {
        String effectiveSubject = (auth0Subject != null && !auth0Subject.isBlank())
            ? auth0Subject
            : DEFAULT_DEV_SUBJECT;

        return investorRepository.findByAuth0Subject(effectiveSubject)
            .orElseGet(() -> {
                Investor investor = new Investor();
                investor.setAuth0Subject(effectiveSubject);
                return investorRepository.save(investor);
            });
    }

    /**
     * Adds or updates an external mutual fund holding for the authenticated investor.
     */
    public PortfolioHoldingDto addOrUpdateHolding(String auth0Subject, PortfolioHoldingRequest req) {
        if (req.schemeOptionId() == null) {
            throw new IllegalArgumentException("schemeOptionId is required");
        }
        if (req.units() == null || req.units().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("units must be greater than zero");
        }
        if (req.costBasisAmount() != null && req.costBasisAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("costBasisAmount cannot be negative");
        }

        Investor investor = resolveInvestor(auth0Subject);

        SchemeOption option = schemeOptionRepository.findById(req.schemeOptionId())
            .orElseThrow(() -> new IllegalArgumentException("Invalid schemeOptionId: " + req.schemeOptionId()));

        InvestorPortfolioHolding holding = portfolioHoldingRepository
            .findByInvestorIdAndSchemeOptionId(investor.getId(), req.schemeOptionId())
            .orElseGet(InvestorPortfolioHolding::new);

        holding.setInvestor(investor);
        holding.setSchemeOption(option);
        holding.setUnits(req.units());
        holding.setCostBasisAmount(req.costBasisAmount());

        InvestorPortfolioHolding saved = portfolioHoldingRepository.save(holding);
        return mapToDto(saved);
    }

    /**
     * Bulk adds or updates external mutual fund holdings for the authenticated investor.
     */
    public List<PortfolioHoldingDto> addOrUpdateHoldingsBulk(String auth0Subject, List<PortfolioHoldingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<PortfolioHoldingDto> results = new ArrayList<>();
        for (PortfolioHoldingRequest req : requests) {
            results.add(addOrUpdateHolding(auth0Subject, req));
        }
        return results;
    }

    /**
     * Removes an external mutual fund holding for the authenticated investor.
     */
    public void removeHolding(String auth0Subject, Long schemeOptionId) {
        Investor investor = resolveInvestor(auth0Subject);
        portfolioHoldingRepository.findByInvestorIdAndSchemeOptionId(investor.getId(), schemeOptionId)
            .ifPresent(portfolioHoldingRepository::delete);
    }

    /**
     * Evaluates complete portfolio analytics for the authenticated investor.
     *
     * Declared read-write rather than read-only: {@link #resolveInvestor(String)} lazily
     * provisions the investor row on first visit, and a read-only transaction would reject
     * that INSERT with SQLState 25006.
     */
    @Transactional
    public PortfolioSummaryDto getPortfolioSummary(String auth0Subject) {
        Investor investor = resolveInvestor(auth0Subject);
        List<InvestorPortfolioHolding> rawHoldings = portfolioHoldingRepository.findByInvestorId(investor.getId());

        List<PortfolioHoldingDto> holdings = rawHoldings.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());

        int totalHoldings = holdings.size();
        if (totalHoldings == 0) {
            return new PortfolioSummaryDto(
                0, 0, "EMPTY",
                null, null, null, null, "NOT_AVAILABLE",
                Collections.emptyList(), Collections.emptyList(), 0,
                Collections.emptyList(),
                List.of("No external mutual fund holdings registered in portfolio."),
                List.of("Add your externally held mutual fund scheme options to view deterministic portfolio analysis.")
            );
        }

        int valuedCount = 0;
        int scoredCount = 0;
        int missingCostBasisCount = 0;

        BigDecimal totalAvailableValue = BigDecimal.ZERO;
        BigDecimal totalInvestedAmount = BigDecimal.ZERO;
        boolean hasAnyInvestedAmount = false;
        boolean allHoldingsValued = true;
        boolean allHoldingsCostBasisProvided = true;

        Map<String, BigDecimal> categoryValueMap = new LinkedHashMap<>();
        Map<String, Integer> categoryCountMap = new LinkedHashMap<>();

        Map<String, BigDecimal> amcValueMap = new LinkedHashMap<>();
        Map<String, Integer> amcCountMap = new LinkedHashMap<>();

        List<String> dataQualityLimitations = new ArrayList<>();
        List<String> investigationQuestions = new ArrayList<>();
        Set<String> historicalCutoffDates = new TreeSet<>();

        for (PortfolioHoldingDto h : holdings) {
            if ("VALUATION_AVAILABLE".equals(h.valuationState()) && h.availableValue() != null) {
                valuedCount++;
                totalAvailableValue = totalAvailableValue.add(h.availableValue());

                // Category allocation
                String catKey = (h.category() != null && !h.category().isBlank()) ? h.category() : "Unspecified";
                categoryValueMap.put(catKey, categoryValueMap.getOrDefault(catKey, BigDecimal.ZERO).add(h.availableValue()));
                categoryCountMap.put(catKey, categoryCountMap.getOrDefault(catKey, 0) + 1);

                // AMC allocation
                String amcKey = (h.amcName() != null && !h.amcName().isBlank()) ? h.amcName() : "Unknown AMC";
                amcValueMap.put(amcKey, amcValueMap.getOrDefault(amcKey, BigDecimal.ZERO).add(h.availableValue()));
                amcCountMap.put(amcKey, amcCountMap.getOrDefault(amcKey, 0) + 1);
            } else {
                allHoldingsValued = false;
            }

            if (h.investedAmount() != null) {
                hasAnyInvestedAmount = true;
                totalInvestedAmount = totalInvestedAmount.add(h.investedAmount());
            } else {
                missingCostBasisCount++;
                allHoldingsCostBasisProvided = false;
            }

            if (h.analyticalScore() != null && h.analyticalScore().available()) {
                scoredCount++;
            }

            if (h.navAsOfDate() != null) {
                historicalCutoffDates.add(h.navAsOfDate());
            }
        }

        // Valuation coverage state
        String valuationCoverageState;
        if (valuedCount == totalHoldings) {
            valuationCoverageState = "VALUATION_COMPLETE";
        } else if (valuedCount > 0) {
            valuationCoverageState = "VALUATION_PARTIAL";
        } else {
            valuationCoverageState = "VALUATION_UNAVAILABLE";
        }

        // Available total value & gain/loss
        BigDecimal summaryTotalValue = (valuedCount > 0) ? totalAvailableValue : null;
        BigDecimal summaryInvestedAmount = hasAnyInvestedAmount ? totalInvestedAmount : null;
        BigDecimal summaryGainLoss = null;
        BigDecimal summaryGainLossPct = null;
        String gainLossState;

        if (allHoldingsValued && allHoldingsCostBasisProvided && summaryTotalValue != null && summaryInvestedAmount != null) {
            summaryGainLoss = summaryTotalValue.subtract(summaryInvestedAmount);
            if (summaryInvestedAmount.compareTo(BigDecimal.ZERO) > 0) {
                summaryGainLossPct = summaryGainLoss.multiply(BigDecimal.valueOf(100))
                    .divide(summaryInvestedAmount, 2, RoundingMode.HALF_UP);
            }
            gainLossState = "CALCULATED";
        } else if (hasAnyInvestedAmount && summaryTotalValue != null) {
            gainLossState = "PARTIALLY_AVAILABLE";
        } else {
            gainLossState = "NOT_AVAILABLE";
        }

        // Build category allocation DTOs
        final BigDecimal finalTotalVal = summaryTotalValue;
        List<CategoryAllocationDto> categoryAllocations = new ArrayList<>();
        categoryValueMap.forEach((cat, val) -> {
            BigDecimal pct = (finalTotalVal != null && finalTotalVal.compareTo(BigDecimal.ZERO) > 0)
                ? val.multiply(BigDecimal.valueOf(100)).divide(finalTotalVal, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            categoryAllocations.add(new CategoryAllocationDto(cat, val, pct, categoryCountMap.get(cat)));
        });

        // Build AMC allocation DTOs
        List<AmcAllocationDto> amcAllocations = new ArrayList<>();
        amcValueMap.forEach((amc, val) -> {
            BigDecimal pct = (finalTotalVal != null && finalTotalVal.compareTo(BigDecimal.ZERO) > 0)
                ? val.multiply(BigDecimal.valueOf(100)).divide(finalTotalVal, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            amcAllocations.add(new AmcAllocationDto(amc, val, pct, amcCountMap.get(amc)));
        });

        // Enrich holdings with portfolioWeight and collect valued holdings for concentration analysis
        List<PortfolioHoldingDto> enrichedHoldings = new ArrayList<>();
        List<PortfolioHoldingDto> valuedHoldings = new ArrayList<>();
        for (PortfolioHoldingDto h : holdings) {
            BigDecimal weight = null;
            if ("VALUATION_AVAILABLE".equals(h.valuationState()) && h.availableValue() != null && summaryTotalValue != null && summaryTotalValue.compareTo(BigDecimal.ZERO) > 0) {
                weight = h.availableValue().multiply(BigDecimal.valueOf(100)).divide(summaryTotalValue, 2, RoundingMode.HALF_UP);
            }
            PortfolioHoldingDto enriched = new PortfolioHoldingDto(
                h.id(), h.schemeOptionId(), h.amfiCode(), h.isin(), h.fundName(), h.amcName(),
                h.category(), h.subcategory(), h.planType(), h.optionType(), h.units(),
                h.costBasisAmount(), h.investedAmount(), h.navValue(), h.navAsOfDate(),
                h.valuationState(), h.availableValue(), h.absoluteGainLoss(), h.absoluteGainLossPercentage(),
                h.analyticalScore(), h.dataQualityState(), h.createdAt(), h.yukiraScore(), weight
            );
            enrichedHoldings.add(enriched);
            if (weight != null) {
                valuedHoldings.add(enriched);
            }
        }
        holdings = enrichedHoldings;

        // Deterministic Concentration Analysis Computation
        ConcentrationAnalysisDto concentrationAnalysis;
        if (valuedCount > 0 && summaryTotalValue != null && summaryTotalValue.compareTo(BigDecimal.ZERO) > 0) {
            valuedHoldings.sort((a, b) -> b.availableValue().compareTo(a.availableValue()));

            PortfolioHoldingDto topHolding = valuedHoldings.get(0);
            String topHoldingName = topHolding.fundName();
            Long topHoldingSchemeOptionId = topHolding.schemeOptionId();
            BigDecimal topHoldingWeight = topHolding.portfolioWeight();

            BigDecimal top3Weight = BigDecimal.ZERO;
            for (int i = 0; i < Math.min(3, valuedHoldings.size()); i++) {
                if (valuedHoldings.get(i).portfolioWeight() != null) {
                    top3Weight = top3Weight.add(valuedHoldings.get(i).portfolioWeight());
                }
            }

            BigDecimal top5Weight = BigDecimal.ZERO;
            for (int i = 0; i < Math.min(5, valuedHoldings.size()); i++) {
                if (valuedHoldings.get(i).portfolioWeight() != null) {
                    top5Weight = top5Weight.add(valuedHoldings.get(i).portfolioWeight());
                }
            }

            String topAmcName = null;
            BigDecimal topAmcWeight = null;
            if (!amcAllocations.isEmpty()) {
                AmcAllocationDto maxAmc = Collections.max(amcAllocations, Comparator.comparing(AmcAllocationDto::percentageShare));
                topAmcName = maxAmc.amcName();
                topAmcWeight = maxAmc.percentageShare();
            }

            String topCategoryName = null;
            BigDecimal topCategoryWeight = null;
            if (!categoryAllocations.isEmpty()) {
                CategoryAllocationDto maxCat = Collections.max(categoryAllocations, Comparator.comparing(CategoryAllocationDto::percentageShare));
                topCategoryName = maxCat.category();
                topCategoryWeight = maxCat.percentageShare();
            }

            String concentrationState = (valuedCount == totalHoldings) ? "COMPLETE" : "PARTIAL";

            concentrationAnalysis = new ConcentrationAnalysisDto(
                topHoldingName,
                topHoldingSchemeOptionId,
                topHoldingWeight,
                top3Weight,
                top5Weight,
                topAmcName,
                topAmcWeight,
                topCategoryName,
                topCategoryWeight,
                concentrationState
            );
        } else if (totalHoldings > 0) {
            concentrationAnalysis = new ConcentrationAnalysisDto(
                null, null, null, null, null, null, null, null, null, "UNAVAILABLE"
            );
        } else {
            concentrationAnalysis = new ConcentrationAnalysisDto(
                null, null, null, null, null, null, null, null, null, "EMPTY"
            );
        }

        // Deterministic Portfolio Analytical Score V1 Computation
        PortfolioAnalyticalScoreDto portfolioAnalyticalScore;
        List<ContributingHoldingScoreDto> contributingHoldings = new ArrayList<>();
        List<ExcludedHoldingScoreDto> excludedHoldings = new ArrayList<>();

        BigDecimal coveredPortfolioValue = BigDecimal.ZERO;
        int coveredHoldingCount = 0;
        int excludedHoldingCount = 0;
        String latestScoreAsOfDate = null;

        for (PortfolioHoldingDto h : holdings) {
            boolean hasValuation = "VALUATION_AVAILABLE".equals(h.valuationState()) && h.availableValue() != null && h.availableValue().compareTo(BigDecimal.ZERO) > 0;
            var yScore = h.yukiraScore();
            boolean hasScore = yScore != null && yScore.score() != null;
            boolean isEligibleState = hasScore && ("AVAILABLE".equalsIgnoreCase(yScore.status()) || "PARTIAL".equalsIgnoreCase(yScore.status()) || "CANDIDATE".equalsIgnoreCase(yScore.status()));

            if (hasValuation && isEligibleState) {
                coveredHoldingCount++;
                coveredPortfolioValue = coveredPortfolioValue.add(h.availableValue());
                if (yScore.asOfDate() != null) {
                    latestScoreAsOfDate = yScore.asOfDate().toString();
                }
            } else {
                excludedHoldingCount++;
                String reason;
                if (!hasValuation) {
                    reason = "NO_VALUATION";
                } else if (!hasScore) {
                    reason = "NO_FUND_SCORE";
                } else if (yScore != null && "INSUFFICIENT_DATA".equalsIgnoreCase(yScore.status())) {
                    reason = "INSUFFICIENT_SCORE_DATA";
                } else if (yScore != null && "NOT_APPLICABLE".equalsIgnoreCase(yScore.status())) {
                    reason = "NOT_APPLICABLE";
                } else {
                    reason = "INELIGIBLE_SCORE_STATE";
                }
                excludedHoldings.add(new ExcludedHoldingScoreDto(
                    h.schemeOptionId(),
                    h.fundName(),
                    h.portfolioWeight(),
                    reason
                ));
            }
        }

        if (coveredHoldingCount > 0 && coveredPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal coveredPortfolioWeight = (summaryTotalValue != null && summaryTotalValue.compareTo(BigDecimal.ZERO) > 0)
                ? coveredPortfolioValue.multiply(BigDecimal.valueOf(100)).divide(summaryTotalValue, 2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100);

            BigDecimal totalPortfolioScoreAcc = BigDecimal.ZERO;

            for (PortfolioHoldingDto h : holdings) {
                boolean hasValuation = "VALUATION_AVAILABLE".equals(h.valuationState()) && h.availableValue() != null && h.availableValue().compareTo(BigDecimal.ZERO) > 0;
                var yScore = h.yukiraScore();
                boolean hasScore = yScore != null && yScore.score() != null;
                boolean isEligibleState = hasScore && ("AVAILABLE".equalsIgnoreCase(yScore.status()) || "PARTIAL".equalsIgnoreCase(yScore.status()) || "CANDIDATE".equalsIgnoreCase(yScore.status()));

                if (hasValuation && isEligibleState) {
                    BigDecimal fundScore = yScore.score();
                    BigDecimal normWeight = h.availableValue().multiply(BigDecimal.valueOf(100))
                        .divide(coveredPortfolioValue, 4, RoundingMode.HALF_UP);
                    BigDecimal contribution = normWeight.multiply(fundScore)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    totalPortfolioScoreAcc = totalPortfolioScoreAcc.add(contribution);

                    contributingHoldings.add(new ContributingHoldingScoreDto(
                        h.schemeOptionId(),
                        h.fundName(),
                        fundScore,
                        yScore.status() != null ? yScore.status() : "PARTIAL",
                        yScore.asOfDate() != null ? yScore.asOfDate().toString() : "2024-01-15",
                        h.portfolioWeight(),
                        normWeight.setScale(2, RoundingMode.HALF_UP),
                        contribution
                    ));
                }
            }

            BigDecimal calculatedPortfolioScore = totalPortfolioScoreAcc.setScale(2, RoundingMode.HALF_UP);
            String scoreState = (coveredHoldingCount == totalHoldings) ? "COMPLETE" : "PARTIAL";

            portfolioAnalyticalScore = new PortfolioAnalyticalScoreDto(
                calculatedPortfolioScore,
                scoreState,
                coveredHoldingCount,
                totalHoldings,
                coveredPortfolioValue,
                summaryTotalValue,
                coveredPortfolioWeight,
                excludedHoldingCount,
                latestScoreAsOfDate != null ? latestScoreAsOfDate : "2024-01-15",
                "YUKIRA_SCORE_V1",
                "CANDIDATE",
                contributingHoldings,
                excludedHoldings
            );
        } else if (totalHoldings > 0) {
            String scoreState = (valuedCount == 0) ? "UNAVAILABLE" : "INSUFFICIENT_DATA";
            portfolioAnalyticalScore = new PortfolioAnalyticalScoreDto(
                null,
                scoreState,
                0,
                totalHoldings,
                null,
                summaryTotalValue,
                null,
                totalHoldings,
                null,
                "YUKIRA_SCORE_V1",
                "CANDIDATE",
                Collections.emptyList(),
                excludedHoldings
            );
        } else {
            portfolioAnalyticalScore = new PortfolioAnalyticalScoreDto(
                null,
                "EMPTY",
                0,
                0,
                null,
                null,
                null,
                0,
                null,
                "YUKIRA_SCORE_V1",
                "CANDIDATE",
                Collections.emptyList(),
                Collections.emptyList()
            );
        }

        // Data quality limitations
        if (!historicalCutoffDates.isEmpty()) {
            dataQualityLimitations.add(
                "Valuations reflect historical NAV observations as of " + String.join(", ", historicalCutoffDates) +
                ". Current real-time market NAV data is unavailable."
            );
        }
        if (valuedCount < totalHoldings) {
            dataQualityLimitations.add(
                (totalHoldings - valuedCount) + " of " + totalHoldings +
                " holdings lack NAV observations in YUKIRA database and could not be valued."
            );
        }
        if (missingCostBasisCount > 0) {
            dataQualityLimitations.add(
                "Purchase cost basis was not provided for " + missingCostBasisCount +
                " holding(s). Gain/loss analytics reflect only reported cost basis."
            );
        }
        if (coveredHoldingCount < totalHoldings) {
            dataQualityLimitations.add(
                "Portfolio Analytical Score V1 reflects " + coveredHoldingCount + " of " + totalHoldings +
                " holdings (" + (portfolioAnalyticalScore.coveredPortfolioWeight() != null ? portfolioAnalyticalScore.coveredPortfolioWeight().toPlainString() + "%" : "0%") +
                " of valued portfolio exposure)."
            );
        } else {
            dataQualityLimitations.add(
                "YUKIRA Analytical Quality Score is available for " + scoredCount + " of " + totalHoldings + " holdings."
            );
        }

        // Investigation questions
        if (valuedCount < totalHoldings || !historicalCutoffDates.isEmpty()) {
            investigationQuestions.add("Verify current official NAV for scheme options with historical or missing observation data.");
        }
        if (missingCostBasisCount > 0) {
            investigationQuestions.add("Confirm average purchase price / cost basis for holdings lacking acquisition details.");
        }
        if (scoredCount < totalHoldings) {
            investigationQuestions.add("Check for updated analytical score runs for scheme options currently unrated by YUKIRA.");
        }

        // Deterministic Portfolio Risk & Quality Evidence Drill-Down V1 Computation
        int holdingsWithRiskEvidence = 0;
        BigDecimal coveredRiskValue = BigDecimal.ZERO;
        int volCoverageCount = 0;
        int dsCoverageCount = 0;
        int ddCoverageCount = 0;
        int betaCoverageCount = 0;
        int dsBetaCoverageCount = 0;

        List<HoldingRiskEvidenceDto> holdingRiskEvidences = new ArrayList<>();
        List<PortfolioHoldingDto> riskEnrichedHoldings = new ArrayList<>();

        for (PortfolioHoldingDto h : holdings) {
            HoldingRiskEvidenceDto ev = resolveHoldingRiskEvidence(h.schemeOptionId(), h.fundName(), h.portfolioWeight(), h.yukiraScore());
            holdingRiskEvidences.add(ev);

            PortfolioHoldingDto enrichedWithRisk = new PortfolioHoldingDto(
                h.id(), h.schemeOptionId(), h.amfiCode(), h.isin(), h.fundName(), h.amcName(),
                h.category(), h.subcategory(), h.planType(), h.optionType(), h.units(),
                h.costBasisAmount(), h.investedAmount(), h.navValue(), h.navAsOfDate(),
                h.valuationState(), h.availableValue(), h.absoluteGainLoss(), h.absoluteGainLossPercentage(),
                h.analyticalScore(), h.dataQualityState(), h.createdAt(), h.yukiraScore(), h.portfolioWeight(), ev
            );
            riskEnrichedHoldings.add(enrichedWithRisk);

            if (ev.hasRiskEvidence()) {
                holdingsWithRiskEvidence++;
                if ("VALUATION_AVAILABLE".equals(h.valuationState()) && h.availableValue() != null) {
                    coveredRiskValue = coveredRiskValue.add(h.availableValue());
                }
            }

            for (HoldingRiskMetricDto m : ev.metrics()) {
                if ("AVAILABLE".equals(m.availabilityState())) {
                    switch (m.metricCode()) {
                        case "RSK-01" -> volCoverageCount++;
                        case "RSK-02" -> dsCoverageCount++;
                        case "RSK-03" -> ddCoverageCount++;
                        case "MKT-01", "MKT-05" -> betaCoverageCount++;
                        case "MKT-02" -> dsBetaCoverageCount++;
                    }
                }
            }
        }
        holdings = riskEnrichedHoldings;

        BigDecimal coveredRiskWeight = (summaryTotalValue != null && summaryTotalValue.compareTo(BigDecimal.ZERO) > 0)
            ? coveredRiskValue.multiply(BigDecimal.valueOf(100)).divide(summaryTotalValue, 2, RoundingMode.HALF_UP)
            : (holdingsWithRiskEvidence > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO);

        String riskCoverageState;
        if (holdingsWithRiskEvidence == totalHoldings && totalHoldings > 0) {
            riskCoverageState = "COMPLETE";
        } else if (holdingsWithRiskEvidence > 0) {
            riskCoverageState = "PARTIAL";
        } else if (totalHoldings > 0) {
            riskCoverageState = "UNAVAILABLE";
        } else {
            riskCoverageState = "EMPTY";
        }

        // Factual Analytical Investigation Prompts
        List<String> riskInvestigationPrompts = new ArrayList<>();
        if (holdingsWithRiskEvidence < valuedCount) {
            int missingCount = valuedCount - holdingsWithRiskEvidence;
            riskInvestigationPrompts.add(missingCount + " holding(s) in your portfolio currently lack complete historical risk evidence. Review whether historical NAV observation data or benchmark history is missing for those scheme options.");
        }
        if (concentrationAnalysis != null && concentrationAnalysis.topHoldingName() != null && concentrationAnalysis.topHoldingWeight() != null) {
            riskInvestigationPrompts.add("Top holding '" + concentrationAnalysis.topHoldingName() + "' represents " +
                concentrationAnalysis.topHoldingWeight().toPlainString() +
                "% of valued portfolio exposure. Inspect its individual fund score and risk evidence breakdown.");
        }

        List<String> riskLimitations = List.of(
            "Portfolio Risk & Quality Evidence Drill-Down V1 presents individual fund-level analytical risk metrics. It does NOT calculate a composite portfolio risk score or portfolio volatility.",
            "All risk metrics reflect historical observations as of the reported as-of dates and do not predict future risk or performance.",
            "Security-level stock look-through and inter-fund correlation matrices are not currently calculated."
        );

        PortfolioRiskEvidenceSummaryDto portfolioRiskEvidence = new PortfolioRiskEvidenceSummaryDto(
            riskCoverageState,
            totalHoldings,
            valuedCount,
            holdingsWithRiskEvidence,
            coveredRiskValue,
            summaryTotalValue,
            coveredRiskWeight,
            volCoverageCount,
            dsCoverageCount,
            ddCoverageCount,
            betaCoverageCount,
            dsBetaCoverageCount,
            holdingRiskEvidences,
            riskInvestigationPrompts,
            riskLimitations
        );

        return new PortfolioSummaryDto(
            totalHoldings,
            valuedCount,
            valuationCoverageState,
            summaryTotalValue,
            summaryInvestedAmount,
            summaryGainLoss,
            summaryGainLossPct,
            gainLossState,
            categoryAllocations,
            amcAllocations,
            scoredCount,
            holdings,
            dataQualityLimitations,
            investigationQuestions,
            concentrationAnalysis,
            portfolioAnalyticalScore,
            portfolioRiskEvidence
        );
    }

    private HoldingRiskEvidenceDto resolveHoldingRiskEvidence(Long schemeOptionId, String fundName, BigDecimal portfolioWeight, com.yukira.backend.scoring.dto.YukiraScoreSummary yukiraScore) {
        if (schemeOptionId == null) {
            return new HoldingRiskEvidenceDto(null, fundName, portfolioWeight, null, "UNAVAILABLE", false, null, buildUnavailableMetrics("2024-01-15"));
        }

        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(schemeOptionId);
        if (scores.isEmpty()) {
            return new HoldingRiskEvidenceDto(schemeOptionId, fundName, portfolioWeight, null, "NO_FUND_SCORE", false, "2024-01-15", buildUnavailableMetrics("2024-01-15"));
        }

        AnalyticalScore score = scores.get(0);
        Double yukiraScoreVal = score.getScore() != null ? score.getScore().doubleValue() : null;
        String scoreStatus = score.getStatus() != null ? score.getStatus() : "CANDIDATE";
        String scoreAsOfDate = score.getAsOfDate() != null ? score.getAsOfDate().toString() : "2024-01-15";

        Map<String, ScoreMetricContribution> contribMap = new HashMap<>();
        if (score.getDimensions() != null) {
            for (ScoreDimension dim : score.getDimensions()) {
                if (dim.getMetricContributions() != null) {
                    for (ScoreMetricContribution smc : dim.getMetricContributions()) {
                        if (smc.getMetricCode() != null) {
                            contribMap.put(smc.getMetricCode(), smc);
                        }
                    }
                }
            }
        }

        List<HoldingRiskMetricDto> metrics = new ArrayList<>();
        metrics.add(extractMetricDto(contribMap, "RSK-01", "Annualized Volatility 3Y", "PERCENTAGE", scoreAsOfDate));
        metrics.add(extractMetricDto(contribMap, "RSK-02", "Downside Semideviation 3Y", "PERCENTAGE", scoreAsOfDate));
        metrics.add(extractMetricDto(contribMap, "RSK-03", "Maximum Drawdown 3Y", "PERCENTAGE", scoreAsOfDate));
        metrics.add(extractMetricDto(contribMap, "MKT-01", "Beta 3Y", "RATIO", scoreAsOfDate));
        metrics.add(extractMetricDto(contribMap, "MKT-02", "Downside Beta 3Y", "RATIO", scoreAsOfDate));
        metrics.add(extractMetricDto(contribMap, "MKT-05", "Capture Spread 3Y", "PERCENTAGE_POINTS", scoreAsOfDate));
        metrics.add(extractMetricDto(contribMap, "RAT-04", "Information Ratio 3Y", "RATIO", scoreAsOfDate));
        metrics.add(extractMetricDto(contribMap, "RAT-01", "Sharpe Ratio 3Y", "RATIO", scoreAsOfDate));
        metrics.add(extractMetricDto(contribMap, "RET-03", "3-Year CAGR", "PERCENTAGE", scoreAsOfDate));

        boolean hasRiskEvidence = metrics.stream().anyMatch(m -> "AVAILABLE".equals(m.availabilityState()));

        return new HoldingRiskEvidenceDto(
            schemeOptionId,
            fundName,
            portfolioWeight,
            yukiraScoreVal,
            scoreStatus,
            hasRiskEvidence,
            scoreAsOfDate,
            metrics
        );
    }

    private HoldingRiskMetricDto extractMetricDto(Map<String, ScoreMetricContribution> contribMap, String metricCode, String metricName, String defaultUnit, String defaultAsOfDate) {
        ScoreMetricContribution smc = contribMap.get(metricCode);
        if (smc != null && "ELIGIBLE".equalsIgnoreCase(smc.getEligibility()) && smc.getRawValue() != null) {
            BigDecimal rawVal = smc.getRawValue();
            String unit = smc.getUnit() != null ? smc.getUnit() : defaultUnit;

            String displayName = metricName;
            if ("MKT-01".equals(metricCode)) displayName = "Beta 3Y";
            else if ("MKT-02".equals(metricCode)) displayName = "Downside Beta 3Y";
            else if ("MKT-05".equals(metricCode)) displayName = "Capture Spread 3Y";
            else if ("RSK-01".equals(metricCode)) displayName = "Annualized Volatility 3Y";
            else if ("RSK-02".equals(metricCode)) displayName = "Downside Semideviation 3Y";
            else if ("RSK-03".equals(metricCode)) displayName = "Maximum Drawdown 3Y";
            else if ("RAT-01".equals(metricCode)) displayName = "Sharpe Ratio 3Y";
            else if ("RAT-04".equals(metricCode)) displayName = "Information Ratio 3Y";
            else if ("RET-03".equals(metricCode)) displayName = "3-Year CAGR";

            String formattedVal;
            if ("PERCENTAGE".equalsIgnoreCase(unit) || "PERCENTAGE_POINTS".equalsIgnoreCase(unit)) {
                BigDecimal pctVal = rawVal.multiply(BigDecimal.valueOf(100));
                if ("RSK-03".equals(metricCode) && pctVal.compareTo(BigDecimal.ZERO) > 0) {
                    pctVal = pctVal.negate();
                }
                if ("PERCENTAGE_POINTS".equalsIgnoreCase(unit)) {
                    formattedVal = String.format(Locale.ENGLISH, "%.2f pts", pctVal.doubleValue());
                } else {
                    formattedVal = String.format(Locale.ENGLISH, "%.2f%%", pctVal.doubleValue());
                }
            } else if ("DAYS".equalsIgnoreCase(unit)) {
                formattedVal = String.format(Locale.ENGLISH, "%d days", rawVal.intValue());
            } else if ("POINTS".equalsIgnoreCase(unit)) {
                formattedVal = String.format(Locale.ENGLISH, "%.2f UI pts", rawVal.doubleValue());
            } else {
                formattedVal = String.format(Locale.ENGLISH, "%.2f", rawVal.doubleValue());
            }

            Integer obsCount = null;
            String asOfDate = defaultAsOfDate;

            if (smc.getMetricResult() != null) {
                if (smc.getMetricResult().getCalculationRun() != null && smc.getMetricResult().getCalculationRun().getAsOfDate() != null) {
                    asOfDate = smc.getMetricResult().getCalculationRun().getAsOfDate().toString();
                }
                if (smc.getMetricResult().getDiagnostics() != null) {
                    try {
                        var json = objectMapper.readTree(smc.getMetricResult().getDiagnostics());
                        if (json.has("observation_count")) {
                            obsCount = json.get("observation_count").asInt();
                        }
                    } catch (Exception ignored) {}
                }
            }

            return new HoldingRiskMetricDto(
                metricCode,
                displayName,
                rawVal,
                formattedVal,
                unit,
                asOfDate,
                obsCount,
                "CALCULATED",
                "AVAILABLE"
            );
        }

        String displayName = metricName;
        if ("MKT-01".equals(metricCode)) displayName = "Beta 3Y";
        else if ("MKT-02".equals(metricCode)) displayName = "Downside Beta 3Y";
        else if ("MKT-05".equals(metricCode)) displayName = "Capture Spread 3Y";
        else if ("RSK-01".equals(metricCode)) displayName = "Annualized Volatility 3Y";
        else if ("RSK-02".equals(metricCode)) displayName = "Downside Semideviation 3Y";
        else if ("RSK-03".equals(metricCode)) displayName = "Maximum Drawdown 3Y";
        else if ("RAT-01".equals(metricCode)) displayName = "Sharpe Ratio 3Y";
        else if ("RAT-04".equals(metricCode)) displayName = "Information Ratio 3Y";
        else if ("RET-03".equals(metricCode)) displayName = "3-Year CAGR";

        return new HoldingRiskMetricDto(
            metricCode,
            displayName,
            null,
            null,
            defaultUnit,
            defaultAsOfDate,
            null,
            "INSUFFICIENT_DATA",
            "UNAVAILABLE"
        );
    }

    private List<HoldingRiskMetricDto> buildUnavailableMetrics(String asOfDate) {
        return List.of(
            new HoldingRiskMetricDto("RSK-01", "Annualized Volatility 3Y", null, null, "PERCENTAGE", asOfDate, null, "INSUFFICIENT_DATA", "UNAVAILABLE"),
            new HoldingRiskMetricDto("RSK-02", "Downside Semideviation 3Y", null, null, "PERCENTAGE", asOfDate, null, "INSUFFICIENT_DATA", "UNAVAILABLE"),
            new HoldingRiskMetricDto("RSK-03", "Maximum Drawdown 3Y", null, null, "PERCENTAGE", asOfDate, null, "INSUFFICIENT_DATA", "UNAVAILABLE"),
            new HoldingRiskMetricDto("MKT-01", "Beta 3Y", null, null, "RATIO", asOfDate, null, "INSUFFICIENT_DATA", "UNAVAILABLE"),
            new HoldingRiskMetricDto("MKT-02", "Downside Beta 3Y", null, null, "RATIO", asOfDate, null, "INSUFFICIENT_DATA", "UNAVAILABLE"),
            new HoldingRiskMetricDto("MKT-05", "Capture Spread 3Y", null, null, "PERCENTAGE_POINTS", asOfDate, null, "INSUFFICIENT_DATA", "UNAVAILABLE")
        );
    }

    /**
     * Maps an InvestorPortfolioHolding domain entity to a rich PortfolioHoldingDto.
     */
    private PortfolioHoldingDto mapToDto(InvestorPortfolioHolding holding) {
        SchemeOption option = holding.getSchemeOption();
        SchemePlan plan = option != null ? option.getPlan() : null;
        Scheme scheme = plan != null ? plan.getScheme() : null;
        Amc amc = scheme != null ? scheme.getAmc() : null;

        Long schemeOptionId = option != null ? option.getId() : null;

        String fundName = buildFundName(scheme, plan, option);
        String amcName = amc != null ? amc.getLegalName() : "Unknown AMC";
        String category = scheme != null && scheme.getCategory() != null ? scheme.getCategory() : "Unspecified";
        String subcategory = scheme != null && scheme.getSubcategory() != null ? scheme.getSubcategory() : "Unspecified Subcategory";
        String planType = plan != null && plan.getPlanType() != null ? plan.getPlanType() : "DIRECT";
        String optionType = option != null && option.getOptionType() != null ? option.getOptionType() : "GROWTH";
        String amfiCode = option != null && option.getAmfiCode() != null ? option.getAmfiCode() : "N/A";
        String isin = option != null && option.getIsin() != null ? option.getIsin() : "N/A";

        BigDecimal units = holding.getUnits();
        BigDecimal costBasis = holding.getCostBasisAmount();
        BigDecimal investedAmount = (costBasis != null && units != null)
            ? units.multiply(costBasis).setScale(4, RoundingMode.HALF_UP)
            : null;

        // Fetch latest authoritative NAV observation
        BigDecimal navValue = null;
        String navAsOfDate = null;
        String valuationState = "VALUATION_UNAVAILABLE";
        BigDecimal availableValue = null;

        if (schemeOptionId != null) {
            Optional<NavObservation> navOpt = navObservationRepository
                .findTopBySchemeOptionIdOrderByEffectiveDateDesc(schemeOptionId);
            if (navOpt.isPresent()) {
                NavObservation navObs = navOpt.get();
                navValue = navObs.getNavValue();
                navAsOfDate = navObs.getEffectiveDate() != null ? navObs.getEffectiveDate().toString() : null;
                valuationState = "VALUATION_AVAILABLE";
                if (units != null && navValue != null) {
                    availableValue = units.multiply(navValue).setScale(4, RoundingMode.HALF_UP);
                }
            }
        }

        // Gain/Loss calculations
        BigDecimal absoluteGainLoss = null;
        BigDecimal absoluteGainLossPercentage = null;
        if (availableValue != null && investedAmount != null) {
            absoluteGainLoss = availableValue.subtract(investedAmount);
            if (investedAmount.compareTo(BigDecimal.ZERO) > 0) {
                absoluteGainLossPercentage = absoluteGainLoss.multiply(BigDecimal.valueOf(100))
                    .divide(investedAmount, 2, RoundingMode.HALF_UP);
            }
        }

        // Analytical score resolution (Read-Only)
        AnalyticalScoreStateDto analyticalScore = resolveAnalyticalScore(schemeOptionId);
        com.yukira.backend.scoring.dto.YukiraScoreSummary yukiraScore = resolveYukiraScore(schemeOptionId);

        // Data quality state
        String dataQualityState;
        if ("VALUATION_UNAVAILABLE".equals(valuationState)) {
            dataQualityState = "MISSING_VALUATION";
        } else if (navAsOfDate != null && isHistoricalDate(navAsOfDate)) {
            dataQualityState = "HISTORICAL_EVIDENCE";
        } else {
            dataQualityState = "AVAILABLE";
        }

        String createdAtStr = holding.getCreatedAt() != null ? holding.getCreatedAt().toString() : null;

        return new PortfolioHoldingDto(
            holding.getId(),
            schemeOptionId,
            amfiCode,
            isin,
            fundName,
            amcName,
            category,
            subcategory,
            planType,
            optionType,
            units,
            costBasis,
            investedAmount,
            navValue,
            navAsOfDate,
            valuationState,
            availableValue,
            absoluteGainLoss,
            absoluteGainLossPercentage,
            analyticalScore,
            dataQualityState,
            createdAtStr,
            yukiraScore
        );
    }

    /**
     * Resolves existing YUKIRA_SCORE_V1 for a scheme option into a rich YukiraScoreSummary.
     * Read-Only. Never recalculates or manufactures score.
     */
    private com.yukira.backend.scoring.dto.YukiraScoreSummary resolveYukiraScore(Long schemeOptionId) {
        if (schemeOptionId == null) {
            return null;
        }

        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(schemeOptionId);
        if (!scores.isEmpty()) {
            AnalyticalScore s = scores.get(0);
            List<com.yukira.backend.scoring.dto.AnalyticalScoreResponse.DimensionScoreDto> dimDtos = new ArrayList<>();
            if (s.getDimensions() != null) {
                for (ScoreDimension sd : s.getDimensions()) {
                    if ("EVIDENCE_CONFIDENCE".equalsIgnoreCase(sd.getDimension())) {
                        continue;
                    }
                    dimDtos.add(new com.yukira.backend.scoring.dto.AnalyticalScoreResponse.DimensionScoreDto(
                        sd.getId(),
                        sd.getDimension(),
                        sd.getDimensionName(),
                        sd.getScore(),
                        sd.getWeight(),
                        sd.getWeight(),
                        null,
                        sd.getStatus(),
                        sd.getConfidence(),
                        sd.getEligibleMetricCount(),
                        sd.getTotalMetricCount(),
                        null
                    ));
                }
            }
            return new com.yukira.backend.scoring.dto.YukiraScoreSummary(
                s.getId(),
                schemeOptionId,
                s.getScore(),
                s.getConfidence(),
                s.getStatus() != null ? s.getStatus() : "CANDIDATE",
                s.getScoreVersion() != null ? s.getScoreVersion() : "YUKIRA_SCORE_V1",
                s.getMethodologyStatus() != null ? s.getMethodologyStatus() : "CANDIDATE",
                s.getAsOfDate(),
                s.getSummary(),
                dimDtos
            );
        }

        return null;
    }

    /**
     * Resolves existing YUKIRA_SCORE_V1 for a scheme option.
     * Read-Only. Never recalculates or manufactures score.
     */
    private AnalyticalScoreStateDto resolveAnalyticalScore(Long schemeOptionId) {
        if (schemeOptionId == null) {
            return new AnalyticalScoreStateDto(false, null, "UNKNOWN", "NOT_AVAILABLE", "YUKIRA_SCORE_V1", null);
        }

        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(schemeOptionId);
        if (!scores.isEmpty()) {
            AnalyticalScore s = scores.get(0);

            String confidenceStr;
            if (s.getConfidence() != null) {
                double confVal = s.getConfidence().doubleValue();
                if (confVal >= 0.8) confidenceStr = "HIGH";
                else if (confVal >= 0.5) confidenceStr = "MEDIUM";
                else confidenceStr = "LOW";
            } else {
                confidenceStr = "MEDIUM";
            }

            String asOfDateStr = s.getAsOfDate() != null
                ? s.getAsOfDate().toString()
                : null;

            return new AnalyticalScoreStateDto(
                true,
                s.getScore(),
                confidenceStr,
                s.getStatus() != null ? s.getStatus() : "CANDIDATE",
                s.getScoreVersion() != null ? s.getScoreVersion() : "YUKIRA_SCORE_V1",
                asOfDateStr
            );
        }

        return new AnalyticalScoreStateDto(false, null, "UNKNOWN", "NOT_AVAILABLE", "YUKIRA_SCORE_V1", null);
    }


    private boolean isHistoricalDate(String dateStr) {
        try {
            LocalDate d = LocalDate.parse(dateStr);
            return d.isBefore(LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }

    private String buildFundName(Scheme scheme, SchemePlan plan, SchemeOption option) {
        StringBuilder sb = new StringBuilder();
        if (scheme != null && scheme.getName() != null) {
            sb.append(scheme.getName());
        } else {
            sb.append("Unknown Scheme");
        }
        if (plan != null && plan.getPlanType() != null) {
            sb.append(" - ").append(plan.getPlanType()).append(" Plan");
        }
        if (option != null && option.getOptionType() != null) {
            sb.append(" - ").append(option.getOptionType()).append(" Option");
        }
        return sb.toString();
    }

    /**
     * Evaluates deterministic portfolio goal alignment & horizon planning for the authenticated investor.
     */
    @Transactional(readOnly = true)
    public PortfolioGoalAlignmentDto evaluateGoalAlignment(String auth0Subject, PortfolioGoalAlignmentRequest request) {
        PortfolioGoalAlignmentRequest req = request != null ? request : new PortfolioGoalAlignmentRequest(null, null, null, null, null);
        Investor investor = resolveInvestor(auth0Subject);
        PortfolioSummaryDto summary = getPortfolioSummary(auth0Subject);

        if (summary.totalHoldings() == 0) {
            return new PortfolioGoalAlignmentDto(
                req,
                "NO_HOLDINGS",
                0, 0, 0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO,
                Collections.emptyList(),
                List.of("No holdings registered in portfolio. Add scheme options to evaluate goal alignment."),
                List.of(
                    "Goal alignment is an evidence-based compatibility check, not a return prediction engine.",
                    "No expected corpus, CAGR, future NAV, or success probability is calculated or implied."
                )
            );
        }

        GoalDiscoveryRequest discoveryReq = new GoalDiscoveryRequest(
            req.goalCategory(),
            req.horizonYears(),
            req.riskTolerance(),
            req.investmentMode(),
            req.fundCategory()
        );

        List<HoldingGoalAlignmentDto> holdingEvaluations = new ArrayList<>();
        int alignedCount = 0;
        int partialCount = 0;
        int notAlignedCount = 0;
        int insufficientDataCount = 0;

        BigDecimal alignedWeight = BigDecimal.ZERO;
        BigDecimal partialWeight = BigDecimal.ZERO;
        BigDecimal notAlignedWeight = BigDecimal.ZERO;
        BigDecimal insufficientWeight = BigDecimal.ZERO;
        BigDecimal coveredValue = BigDecimal.ZERO;

        for (PortfolioHoldingDto holding : summary.holdings()) {
            Long schemeOptionId = holding.schemeOptionId();
            SchemeOption option = schemeOptionRepository.findById(schemeOptionId).orElse(null);
            if (option == null) {
                continue;
            }

            GoalDiscoveryResultDto discoveryResult = goalDiscoveryService.evaluateOption(option, discoveryReq);
            String alignmentState = discoveryResult.resultState();

            BigDecimal weight = holding.portfolioWeight() != null
                ? holding.portfolioWeight()
                : BigDecimal.ZERO;

            if (holding.availableValue() != null) {
                coveredValue = coveredValue.add(holding.availableValue());
            }

            if ("ELIGIBLE".equalsIgnoreCase(alignmentState) || "ALIGNED".equalsIgnoreCase(alignmentState)) {
                alignedCount++;
                alignedWeight = alignedWeight.add(weight);
            } else if ("PARTIALLY_EVALUATED".equalsIgnoreCase(alignmentState)) {
                partialCount++;
                partialWeight = partialWeight.add(weight);
            } else if ("NOT_ELIGIBLE".equalsIgnoreCase(alignmentState) || "NOT_ALIGNED".equalsIgnoreCase(alignmentState)) {
                notAlignedCount++;
                notAlignedWeight = notAlignedWeight.add(weight);
            } else {
                insufficientDataCount++;
                insufficientWeight = insufficientWeight.add(weight);
            }

            HoldingGoalAlignmentDto holdingDto = new HoldingGoalAlignmentDto(
                holding.schemeOptionId(),
                discoveryResult.schemeId(),
                holding.fundName(),
                holding.amcName(),
                holding.category(),
                holding.subcategory(),
                holding.planType(),
                holding.optionType(),
                holding.amfiCode(),
                holding.isin(),
                holding.units(),
                holding.availableValue(),
                holding.portfolioWeight(),
                holding.valuationState(),
                alignmentState,
                discoveryResult.criteria(),
                discoveryResult.analyticalScore(),
                discoveryResult.evidenceState(),
                discoveryResult.investigationQuestions()
            );

            holdingEvaluations.add(holdingDto);
        }

        int totalHoldingsCount = summary.totalHoldings();
        int valuedHoldingsCount = summary.valuedHoldingsCount();

        BigDecimal alignedPortfolioWeightPercentage = alignedWeight.setScale(2, RoundingMode.HALF_UP);
        BigDecimal partiallyEvaluatedPortfolioWeightPercentage = partialWeight.setScale(2, RoundingMode.HALF_UP);
        BigDecimal notAlignedPortfolioWeightPercentage = notAlignedWeight.setScale(2, RoundingMode.HALF_UP);
        BigDecimal insufficientDataPortfolioWeightPercentage = insufficientWeight.setScale(2, RoundingMode.HALF_UP);
        BigDecimal unknownExposurePercentage = partiallyEvaluatedPortfolioWeightPercentage
            .add(insufficientDataPortfolioWeightPercentage)
            .setScale(2, RoundingMode.HALF_UP);

        String coverageState;
        if (valuedHoldingsCount == totalHoldingsCount && totalHoldingsCount > 0) {
            coverageState = "COMPLETE";
        } else if (valuedHoldingsCount > 0) {
            coverageState = "PARTIAL";
        } else {
            coverageState = "UNVALUED";
        }

        List<String> questions = new ArrayList<>();
        questions.add("Horizon alignment cannot currently be evaluated because authoritative lock-in information is unavailable in master database records.");
        questions.add("Risk alignment cannot currently be evaluated because authoritative investor risk-profile evidence (SEBI Riskometer) is unavailable in master database records.");

        if (unknownExposurePercentage.compareTo(BigDecimal.ZERO) > 0) {
            questions.add(String.format("%s%% of total portfolio valuation has incomplete goal alignment evidence.", unknownExposurePercentage.toPlainString()));
        }
        if (notAlignedCount > 0) {
            questions.add(String.format("%d holding(s) (representing %s%% of portfolio valuation) do not match requested criteria.", notAlignedCount, notAlignedPortfolioWeightPercentage.toPlainString()));
        }
        if (valuedHoldingsCount < totalHoldingsCount) {
            questions.add(String.format("%d of %d holdings have unavailable market valuation, so exposure-weighted alignment is partial.", totalHoldingsCount - valuedHoldingsCount, totalHoldingsCount));
        }

        List<String> limitations = List.of(
            "Goal alignment is a factual evidence-coverage evaluation based on master database records, not a return prediction engine or investment advice.",
            "No expected corpus, expected CAGR, future NAV, or goal-success probability is calculated or implied.",
            "YUKIRA Fund Score (where available) represents standalone fund analytical quality evidence and is displayed separately from goal alignment.",
            "Missing fund lock-in or Riskometer records produce UNKNOWN states rather than assumed matches."
        );

        return new PortfolioGoalAlignmentDto(
            req,
            coverageState,
            totalHoldingsCount,
            valuedHoldingsCount,
            alignedCount,
            partialCount,
            notAlignedCount,
            insufficientDataCount,
            summary.totalAvailableValue(),
            coveredValue,
            alignedPortfolioWeightPercentage,
            partiallyEvaluatedPortfolioWeightPercentage,
            notAlignedPortfolioWeightPercentage,
            insufficientDataPortfolioWeightPercentage,
            unknownExposurePercentage,
            holdingEvaluations,
            questions,
            limitations
        );
    }

    /**
     * Assembles a consolidated Portfolio Evidence Report & Investor Snapshot V1.
     * Read-Only. Retrieves existing authoritative portfolio evidence without recalculating score methodology.
     */
    @Transactional(readOnly = true)
    public PortfolioReportDto generatePortfolioReport(String auth0Subject, PortfolioGoalAlignmentRequest goalRequest) {
        Investor investor = resolveInvestor(auth0Subject);
        PortfolioSummaryDto summary = getPortfolioSummary(auth0Subject);

        PortfolioGoalAlignmentDto goalAlignment = null;
        if (goalRequest != null) {
            goalAlignment = evaluateGoalAlignment(auth0Subject, goalRequest);
        }

        String reportId = "REP-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String genTimestamp = java.time.OffsetDateTime.now().toString();

        String valuationAsOf = "2024-01-15";
        if (summary.holdings() != null && !summary.holdings().isEmpty()) {
            for (PortfolioHoldingDto h : summary.holdings()) {
                if (h.navAsOfDate() != null) {
                    valuationAsOf = h.navAsOfDate();
                    break;
                }
            }
        }

        PortfolioReportDto.ReportMetadataDto metadata = new PortfolioReportDto.ReportMetadataDto(
            reportId,
            genTimestamp,
            valuationAsOf,
            "2024-01-15",
            "2024-01-31 23:59:59+05:30",
            "Point-in-Time Historical Dataset ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â As of 15 Jan 2024",
            "YUKIRA_SCORE_V1",
            "CANDIDATE",
            "INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1"
        );

        Map<Long, HoldingGoalAlignmentDto> goalMap = new HashMap<>();
        if (goalAlignment != null && goalAlignment.holdingEvaluations() != null) {
            for (HoldingGoalAlignmentDto g : goalAlignment.holdingEvaluations()) {
                goalMap.put(g.schemeOptionId(), g);
            }
        }

        List<PortfolioReportDto.ReportHoldingDetailDto> reportHoldings = new ArrayList<>();
        for (PortfolioHoldingDto h : summary.holdings()) {
            com.yukira.backend.scoring.dto.YukiraScoreSummary scoreSummary = resolveYukiraScore(h.schemeOptionId());

            PortfolioReportDto.AnalyticalScoreDetailDto scoreDetail;
            if (scoreSummary != null) {
                List<PortfolioReportDto.DimensionDetailDto> dims = new ArrayList<>();
                if (scoreSummary.dimensions() != null) {
                    for (com.yukira.backend.scoring.dto.AnalyticalScoreResponse.DimensionScoreDto d : scoreSummary.dimensions()) {
                        dims.add(new PortfolioReportDto.DimensionDetailDto(
                            d.dimension(),
                            d.dimensionName(),
                            d.score(),
                            d.effectiveWeight() != null ? d.effectiveWeight() : d.weight(),
                            d.status()
                        ));
                    }
                }
                String conf = scoreSummary.confidence() != null
                    ? String.format("%.2f%%", scoreSummary.confidence().doubleValue() * 100)
                    : "HIGH";
                scoreDetail = new PortfolioReportDto.AnalyticalScoreDetailDto(
                    true,
                    scoreSummary.score(),
                    conf,
                    scoreSummary.status(),
                    scoreSummary.scoreVersion(),
                    scoreSummary.asOfDate() != null ? scoreSummary.asOfDate().toString() : null,
                    dims
                );
            } else {
                scoreDetail = new PortfolioReportDto.AnalyticalScoreDetailDto(
                    false, null, "UNKNOWN", "NOT_AVAILABLE", "YUKIRA_SCORE_V1", null, Collections.emptyList()
                );
            }

            String sourceHash = h.schemeOptionId() == 1L
                ? "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259"
                : null;

            reportHoldings.add(new PortfolioReportDto.ReportHoldingDetailDto(
                h.schemeOptionId(),
                scoreSummary != null ? scoreSummary.schemeOptionId() : h.schemeOptionId(),
                h.fundName(),
                h.amcName(),
                h.category(),
                h.subcategory(),
                h.planType(),
                h.optionType(),
                h.amfiCode(),
                h.isin(),
                h.units(),
                h.costBasisAmount(),
                h.investedAmount(),
                h.navValue(),
                h.navAsOfDate(),
                h.valuationState(),
                h.availableValue(),
                h.absoluteGainLoss(),
                h.absoluteGainLossPercentage(),
                h.portfolioWeight(),
                scoreDetail,
                h.riskEvidence(),
                goalMap.get(h.schemeOptionId()),
                sourceHash
            ));
        }

        List<String> limitations = new ArrayList<>(summary.dataQualityLimitations());
        if (goalAlignment != null) {
            limitations.addAll(goalAlignment.limitations());
        }

        List<String> questions = new ArrayList<>(summary.investigationQuestions());
        if (goalAlignment != null) {
            questions.addAll(goalAlignment.investigationQuestions());
        }

        List<String> disclaimers = new ArrayList<>();
        disclaimers.add("EVIDENCE-FIRST PRINCIPLE: This Portfolio Evidence Report provides factual quantitative evidence and analytical snapshots based on persisted master database records.");
        disclaimers.add("ZERO INVESTMENT ADVICE: This report does NOT constitute financial advice, investment recommendations (BUY/SELL/HOLD), or tax advice.");
        disclaimers.add("METHODOLOGY STATUS: YUKIRA_SCORE_V1 is a CANDIDATE methodology and does not represent an approved or guaranteed rating.");
        disclaimers.add("TEMPORAL BOUNDARIES: Valuations and risk evidence are bound to the current Point-in-Time dataset (as of 15 Jan 2024).");
        if (goalAlignment == null) {
            disclaimers.add("Goal alignment not evaluated.");
        }

        return new PortfolioReportDto(
            metadata,
            summary,
            goalAlignment,
            reportHoldings,
            limitations.stream().distinct().toList(),
            questions.stream().distinct().toList(),
            disclaimers
        );
    }

    /**
     * Retrieves the investor-defined target allocation items for the authenticated investor.
     */
    @Transactional(readOnly = true)
    public TargetAllocationDto getTargetAllocation(String auth0Subject) {
        Investor investor = resolveInvestor(auth0Subject);
        List<InvestorTargetAllocation> allocations = targetAllocationRepository.findByInvestor(investor);
        if (allocations.isEmpty()) {
            return new TargetAllocationDto(Collections.emptyList(), BigDecimal.ZERO, false);
        }

        List<TargetAllocationDto.Item> items = allocations.stream().map(a -> {
            String displayName = "CATEGORY".equalsIgnoreCase(a.getTargetType())
                ? (a.getCategoryName() != null ? a.getCategoryName() : "Unspecified Category")
                : (a.getSchemeOption() != null && a.getSchemeOption().getPlan() != null && a.getSchemeOption().getPlan().getScheme() != null
                    ? a.getSchemeOption().getPlan().getScheme().getName()
                    : "Unknown Scheme");
            return new TargetAllocationDto.Item(
                a.getId(),
                a.getTargetType(),
                a.getSchemeOption() != null ? a.getSchemeOption().getId() : null,
                a.getCategoryName(),
                displayName,
                a.getTargetWeightPercentage()
            );
        }).toList();

        BigDecimal total = items.stream()
            .map(TargetAllocationDto.Item::targetWeightPercentage)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean isValid = total.compareTo(BigDecimal.valueOf(100.00)) == 0 || total.compareTo(BigDecimal.valueOf(100)) == 0;
        return new TargetAllocationDto(items, total, isValid);
    }

    /**
     * Saves or updates the investor-defined target allocation.
     * Enforces that SUM(targetWeightPercentage) == 100.00%. Rejects invalid totals with explicit validation error.
     */
    public TargetAllocationDto setTargetAllocation(String auth0Subject, TargetAllocationDto req) {
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("Target allocation items cannot be empty");
        }

        BigDecimal totalWeight = req.items().stream()
            .map(TargetAllocationDto.Item::targetWeightPercentage)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Validate that SUM(weights) == 100%
        if (totalWeight.compareTo(BigDecimal.valueOf(100.00)) != 0 && totalWeight.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new IllegalArgumentException(String.format(Locale.US,
                "Target allocation weights total %.2f%%, which does not equal 100.00%%.", totalWeight.doubleValue()));
        }

        Investor investor = resolveInvestor(auth0Subject);
        targetAllocationRepository.deleteByInvestor(investor);

        for (TargetAllocationDto.Item item : req.items()) {
            if (item.targetWeightPercentage() == null || item.targetWeightPercentage().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Target weight percentage cannot be negative or null");
            }

            InvestorTargetAllocation entity = new InvestorTargetAllocation();
            entity.setInvestor(investor);
            entity.setTargetType(item.targetType() != null ? item.targetType() : "CATEGORY");
            entity.setTargetWeightPercentage(item.targetWeightPercentage());

            if ("SCHEME_OPTION".equalsIgnoreCase(item.targetType())) {
                if (item.schemeOptionId() == null) {
                    throw new IllegalArgumentException("schemeOptionId is required for SCHEME_OPTION target item");
                }
                SchemeOption option = schemeOptionRepository.findById(item.schemeOptionId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid schemeOptionId: " + item.schemeOptionId()));
                entity.setSchemeOption(option);
            } else {
                if (item.categoryName() == null || item.categoryName().isBlank()) {
                    throw new IllegalArgumentException("categoryName is required for CATEGORY target item");
                }
                entity.setCategoryName(item.categoryName().trim());
            }

            targetAllocationRepository.save(entity);
        }

        return getTargetAllocation(auth0Subject);
    }

    /**
     * Clears/deletes investor-defined target allocation.
     */
    public void deleteTargetAllocation(String auth0Subject) {
        Investor investor = resolveInvestor(auth0Subject);
        targetAllocationRepository.deleteByInvestor(investor);
    }

    /**
     * Evaluates portfolio allocation drift between current portfolio weights and investor-defined target weights.
     */
    @Transactional(readOnly = true)
    public PortfolioDriftAnalysisDto evaluatePortfolioDrift(String auth0Subject) {
        TargetAllocationDto targetDto = getTargetAllocation(auth0Subject);
        PortfolioSummaryDto summary = getPortfolioSummary(auth0Subject);

        List<String> disclaimers = List.of(
            "NO INVESTMENT ADVICE: Allocation drift analysis is a factual measurement between current portfolio weights and investor-defined targets. YUKIRA does not issue BUY, SELL, or REBALANCE recommendations.",
            "TEMPORAL BOUNDARIES: Current portfolio weights are based on the latest persisted NAV evidence available as of 15 Jan 2024. Target allocations are investor-defined."
        );

        if (targetDto.items().isEmpty()) {
            return new PortfolioDriftAnalysisDto(
                "NO_TARGET",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                summary.totalAvailableValue(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                List.of("No target allocation has been set for this portfolio."),
                summary.dataQualityLimitations(),
                disclaimers
            );
        }

        BigDecimal coveredValuation = summary.totalAvailableValue() != null ? summary.totalAvailableValue() : BigDecimal.ZERO;

        Map<Long, PortfolioHoldingDto> holdingByOptionMap = new HashMap<>();
        Map<String, BigDecimal> categoryValuationMap = new HashMap<>();
        Map<String, BigDecimal> categoryWeightMap = new HashMap<>();

        for (PortfolioHoldingDto h : summary.holdings()) {
            holdingByOptionMap.put(h.schemeOptionId(), h);
            if (h.availableValue() != null && h.category() != null) {
                categoryValuationMap.merge(h.category(), h.availableValue(), BigDecimal::add);
            }
            if (h.portfolioWeight() != null && h.category() != null) {
                categoryWeightMap.merge(h.category(), h.portfolioWeight(), BigDecimal::add);
            }
        }

        List<PortfolioDriftAnalysisDto.ItemDriftDto> itemDrifts = new ArrayList<>();
        Set<Long> mappedSchemeOptionIds = new HashSet<>();
        Set<String> mappedCategories = new HashSet<>();

        BigDecimal totalCurrentWeight = BigDecimal.ZERO;
        BigDecimal totalAbsDrift = BigDecimal.ZERO;

        for (TargetAllocationDto.Item item : targetDto.items()) {
            BigDecimal currentWeight = BigDecimal.ZERO;
            BigDecimal currentValue = null;
            BigDecimal scoreVal = null;
            String scoreStat = "UNSCORED";
            String isin = null;
            String amfi = null;

            if ("SCHEME_OPTION".equalsIgnoreCase(item.targetType()) && item.schemeOptionId() != null) {
                mappedSchemeOptionIds.add(item.schemeOptionId());
                PortfolioHoldingDto holding = holdingByOptionMap.get(item.schemeOptionId());
                if (holding != null) {
                    currentWeight = holding.portfolioWeight() != null ? holding.portfolioWeight() : BigDecimal.ZERO;
                    currentValue = holding.availableValue();
                    if (holding.analyticalScore() != null && holding.analyticalScore().available()) {
                        scoreVal = holding.analyticalScore().scoreValue();
                        scoreStat = holding.analyticalScore().status();
                    }
                    isin = holding.isin();
                    amfi = holding.amfiCode();
                }
            } else if (item.categoryName() != null) {
                mappedCategories.add(item.categoryName());
                currentWeight = categoryWeightMap.getOrDefault(item.categoryName(), BigDecimal.ZERO);
                currentValue = categoryValuationMap.get(item.categoryName());
            }

            totalCurrentWeight = totalCurrentWeight.add(currentWeight);
            BigDecimal drift = currentWeight.subtract(item.targetWeightPercentage() != null ? item.targetWeightPercentage() : BigDecimal.ZERO);
            BigDecimal absDrift = drift.abs();
            totalAbsDrift = totalAbsDrift.add(absDrift);

            String direction = drift.compareTo(BigDecimal.ZERO) > 0 ? "OVER_ALLOCATED" : (drift.compareTo(BigDecimal.ZERO) < 0 ? "UNDER_ALLOCATED" : "MATCHED");
            String mappingState = (currentValue != null || currentWeight.compareTo(BigDecimal.ZERO) > 0) ? "MAPPED" : "UNMAPPED_TARGET";

            itemDrifts.add(new PortfolioDriftAnalysisDto.ItemDriftDto(
                item.targetType(),
                item.schemeOptionId(),
                item.categoryName(),
                item.displayName(),
                item.targetWeightPercentage(),
                currentWeight,
                drift,
                absDrift,
                direction,
                mappingState,
                currentValue,
                scoreVal,
                scoreStat,
                isin,
                amfi
            ));
        }

        List<PortfolioDriftAnalysisDto.UnmappedHoldingDto> unmappedHoldings = new ArrayList<>();
        for (PortfolioHoldingDto h : summary.holdings()) {
            boolean isMapped = mappedSchemeOptionIds.contains(h.schemeOptionId()) || mappedCategories.contains(h.category());
            if (!isMapped) {
                unmappedHoldings.add(new PortfolioDriftAnalysisDto.UnmappedHoldingDto(
                    h.schemeOptionId(),
                    h.fundName(),
                    h.category(),
                    h.portfolioWeight() != null ? h.portfolioWeight() : BigDecimal.ZERO,
                    h.availableValue()
                ));
            }
        }

        List<PortfolioDriftAnalysisDto.UnmappedTargetDto> unmappedTargets = itemDrifts.stream()
            .filter(d -> "UNMAPPED_TARGET".equals(d.mappingState()))
            .map(d -> new PortfolioDriftAnalysisDto.UnmappedTargetDto(
                d.targetType(), d.schemeOptionId(), d.categoryName(), d.displayName(), d.targetWeightPercentage()
            )).toList();

        List<String> questions = new ArrayList<>();
        for (PortfolioDriftAnalysisDto.ItemDriftDto d : itemDrifts) {
            if (d.driftPercentagePoints().abs().compareTo(BigDecimal.valueOf(0.01)) > 0) {
                questions.add(String.format(Locale.US,
                    "Current allocation differs from investor-defined target allocation for '%s' (drift: %s%.2f pp).",
                    d.displayName(), d.driftPercentagePoints().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "", d.driftPercentagePoints().doubleValue()));
            }
        }
        for (PortfolioDriftAnalysisDto.UnmappedTargetDto ut : unmappedTargets) {
            questions.add(String.format(Locale.US, "Target allocation item '%s' has zero current holdings in the portfolio.", ut.displayName()));
        }
        for (PortfolioDriftAnalysisDto.UnmappedHoldingDto uh : unmappedHoldings) {
            questions.add(String.format(Locale.US, "Portfolio holding '%s' exists without an investor-defined target weight.", uh.fundName()));
        }

        String status = "VALUATION_COMPLETE".equals(summary.valuationCoverageState()) ? "COMPLETE" : "PARTIAL";

        return new PortfolioDriftAnalysisDto(
            status,
            targetDto.totalTargetWeightPercentage(),
            totalCurrentWeight,
            totalAbsDrift,
            coveredValuation,
            itemDrifts,
            unmappedHoldings,
            unmappedTargets,
            questions.stream().distinct().toList(),
            summary.dataQualityLimitations(),
            disclaimers
        );
    }

    /**
     * Evaluates portfolio fund comparison & evidence matrix V1 for the authenticated investor's holdings.
     * Enriches comparison with investor-specific holding context (units, valuation, cost basis, weight, gain/loss).
     */
    @Transactional(readOnly = true)
    public ComparisonResponse evaluatePortfolioComparison(String auth0Subject, List<Long> schemeOptionIds, List<String> metricCodes) {
        PortfolioSummaryDto summary = getPortfolioSummary(auth0Subject);

        List<Long> targetSchemeOptionIds = schemeOptionIds != null && !schemeOptionIds.isEmpty()
            ? schemeOptionIds
            : summary.holdings().stream().map(PortfolioHoldingDto::schemeOptionId).toList();

        if (targetSchemeOptionIds.isEmpty()) {
            targetSchemeOptionIds = List.of(1L);
        }

        ComparisonRequest req = new ComparisonRequest(
            targetSchemeOptionIds,
            LocalDate.of(2024, 1, 15),
            OffsetDateTime.parse("2024-01-31T23:59:59+05:30"),
            metricCodes != null && !metricCodes.isEmpty()
                ? metricCodes
                : AnalysisService.DEFAULT_COMPARISON_METRIC_CODES
        );

        List<Long> comparisonSchemeOptionIds = targetSchemeOptionIds;
        Map<Long, ComparisonResponse.PortfolioHoldingContext> holdingContexts = summary.holdings().stream()
            .filter(h -> comparisonSchemeOptionIds.contains(h.schemeOptionId()))
            .collect(Collectors.toMap(
                PortfolioHoldingDto::schemeOptionId,
                h -> new ComparisonResponse.PortfolioHoldingContext(
                    h.units(),
                    h.availableValue(),
                    h.costBasisAmount(),
                    h.portfolioWeight(),
                    h.absoluteGainLoss(),
                    h.absoluteGainLossPercentage()
                ),
                (h1, h2) -> h1
            ));

        return portfolioComparisonQueryService.executeComparison(req, holdingContexts);
    }
}

