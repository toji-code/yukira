package com.yukira.backend.service.portfolio;

import com.yukira.backend.dto.portfolio.drivers.*;
import com.yukira.backend.dto.portfolio.lookthrough.*;
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
 * Service orchestrating Phase 2P: Investor Portfolio Risk Drivers & Concentration Investigation.
 *
 * Epistemic & Methodological Guardrails:
 * 1. Zero frontend math: all concentration ratios, rankings, cross-fund overlap, and diagnostic findings
 *    are computed deterministically on the backend.
 * 2. Deterministic findings: triggered strictly from observable metrics against transparent, centralized thresholds.
 * 3. Never produces buy/sell/hold investment recommendations or predictive risk scores.
 * 4. Zero renormalization: incomplete coverage is reported explicitly as unclassified weight.
 */
@Service
@SuppressWarnings("null")
public class PortfolioRiskDriversService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRiskDriversService.class);

    // Centralized Deterministic Concentration Thresholds
    public static final BigDecimal THRESHOLD_SINGLE_SECURITY_PCT = new BigDecimal("7.00");
    public static final BigDecimal THRESHOLD_SINGLE_SECURITY_HIGH_PCT = new BigDecimal("10.00");
    public static final BigDecimal THRESHOLD_TOP5_PCT = new BigDecimal("30.00");
    public static final BigDecimal THRESHOLD_TOP10_PCT = new BigDecimal("45.00");
    public static final BigDecimal THRESHOLD_PAIRWISE_OVERLAP_PCT = new BigDecimal("20.00");
    public static final BigDecimal THRESHOLD_SHARED_STOCK_PCT = new BigDecimal("3.00");
    public static final BigDecimal THRESHOLD_SECTOR_CONCENTRATION_PCT = new BigDecimal("25.00");
    public static final int THRESHOLD_BREADTH_MIN_SECURITIES = 40;
    public static final BigDecimal THRESHOLD_DISCLOSED_COVERAGE_PCT = new BigDecimal("85.00");
    public static final int THRESHOLD_SNAPSHOT_LAG_DAYS = 30;

    private final MultiFundLookThroughService lookThroughService;

    public PortfolioRiskDriversService(MultiFundLookThroughService lookThroughService) {
        this.lookThroughService = lookThroughService;
    }

    /**
     * Computes the complete Risk Drivers & Concentration Investigation response.
     */
    @Transactional(readOnly = true)
    public PortfolioRiskDriversResponseDto calculateRiskDrivers(MultiFundLookThroughRequestDto request) {
        if (request == null || request.funds() == null || request.funds().isEmpty()) {
            return new PortfolioRiskDriversResponseDto(
                "UNAVAILABLE",
                "No constituent funds provided for concentration and risk drivers analysis.",
                request != null ? request.asOfDate() : LocalDate.now(),
                request != null ? request.knowledgeCutoff() : OffsetDateTime.now(),
                BigDecimal.ZERO,
                false,
                List.of(),
                new ConcentrationSummaryDto(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "None",
                    0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, "UNAVAILABLE"
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("No constituent funds selected in the portfolio builder."),
                Map.of("note", "Select mutual funds to evaluate concentration drivers.")
            );
        }

        // 1. Execute Multi-Fund Look-Through
        MultiFundLookThroughResponseDto lookThrough = lookThroughService.calculateMultiFundLookThrough(request);

        List<ConsolidatedSecurityExposureDto> securities = lookThrough.consolidatedSecurities() != null
            ? lookThrough.consolidatedSecurities()
            : List.of();
        PortfolioConcentrationMetricsDto metrics = lookThrough.concentrationMetrics();
        List<PairwiseOverlapResultDto> overlapMatrix = lookThrough.pairwiseOverlapMatrix() != null
            ? lookThrough.pairwiseOverlapMatrix()
            : List.of();
        List<ConsolidatedSectorExposureDto> sectors = lookThrough.consolidatedSectors() != null
            ? lookThrough.consolidatedSectors()
            : List.of();
        List<ConsolidatedMarketCapExposureDto> marketCaps = lookThrough.consolidatedMarketCap() != null
            ? lookThrough.consolidatedMarketCap()
            : List.of();
        List<ConstituentFundSummaryDto> fundSummaries = lookThrough.constituentFunds() != null
            ? lookThrough.constituentFunds()
            : List.of();

        // 2. Build Concentration Summary
        BigDecimal largestSecWeight = BigDecimal.ZERO;
        String largestSecName = "None";
        if (!securities.isEmpty()) {
            ConsolidatedSecurityExposureDto top = securities.get(0);
            largestSecWeight = top.consolidatedWeightPct();
            largestSecName = top.canonicalName();
        }

        int distinctSecurities = securities.size();
        BigDecimal top5 = metrics != null && metrics.top5WeightPct() != null ? metrics.top5WeightPct() : BigDecimal.ZERO;
        BigDecimal top10 = metrics != null && metrics.top10WeightPct() != null ? metrics.top10WeightPct() : BigDecimal.ZERO;
        int sharedCount = metrics != null ? metrics.sharedSecuritiesCount() : 0;
        BigDecimal sharedWeight = metrics != null && metrics.sharedSecuritiesWeightPct() != null ? metrics.sharedSecuritiesWeightPct() : BigDecimal.ZERO;
        BigDecimal disclosedCoverage = metrics != null && metrics.disclosedEquityCoveragePct() != null ? metrics.disclosedEquityCoveragePct() : BigDecimal.ZERO;
        BigDecimal unclassifiedWeight = metrics != null && metrics.unclassifiedWeightPct() != null ? metrics.unclassifiedWeightPct() : BigDecimal.ZERO;
        BigDecimal cashWeight = metrics != null && metrics.cashAndEquivalentsWeightPct() != null ? metrics.cashAndEquivalentsWeightPct() : BigDecimal.ZERO;
        BigDecimal hhi = metrics != null && metrics.herfindahlHirschmanIndex() != null ? metrics.herfindahlHirschmanIndex() : BigDecimal.ZERO;
        String hhiInterp = metrics != null && metrics.hhiInterpretation() != null ? metrics.hhiInterpretation() : "UNKNOWN";

        ConcentrationSummaryDto summary = new ConcentrationSummaryDto(
            top5, top10, largestSecWeight, largestSecName, distinctSecurities,
            sharedCount, sharedWeight, disclosedCoverage, unclassifiedWeight,
            cashWeight, hhi, hhiInterp
        );

        // 3. Generate Deterministic Investigation Findings
        List<RiskDriverFindingDto> findings = generateDeterministicFindings(
            securities, summary, overlapMatrix, sectors, marketCaps, fundSummaries
        );

        // 4. Data Quality Warnings
        List<String> warnings = new ArrayList<>();
        if (lookThrough.limitations() != null) {
            warnings.addAll(lookThrough.limitations());
        }
        if (unclassifiedWeight.compareTo(new BigDecimal("5.00")) > 0) {
            warnings.add(String.format(Locale.US,
                "%.2f%% of consolidated portfolio assets are unclassified or held in cash/debt instruments.",
                unclassifiedWeight.doubleValue()));
        }

        // 5. Methodology Disclosures
        Map<String, String> methodologyNotes = new LinkedHashMap<>();
        methodologyNotes.put("Pairwise Overlap Definition", "O_ij = sum_{s in S_i cap S_j} min(w_{i,s}, w_{j,s}) using reported fund equity weights (excluding cash/TREPS).");
        methodologyNotes.put("Consolidated Position Weight", "Aggregated as sum_k (allocation_weight_k * holding_weight_{k,s}) where allocation weights sum to 100%.");
        methodologyNotes.put("Zero Renormalization Discipline", "Unmodeled or unclassified exposure is explicitly reported; holdings weights are never scaled to 100%.");
        methodologyNotes.put("Investigation Nature", "Findings are deterministic diagnostic prompts based on published disclosures, not investment advice or return forecasts.");

        return new PortfolioRiskDriversResponseDto(
            lookThrough.qualityState(),
            null,
            lookThrough.asOfDate(),
            lookThrough.knowledgeCutoff(),
            lookThrough.inputTotalAllocation(),
            lookThrough.isFullyAllocated(),
            fundSummaries,
            summary,
            securities,
            overlapMatrix,
            sectors,
            marketCaps,
            findings,
            warnings,
            methodologyNotes
        );
    }

    /**
     * Evaluates transparent rule-based triggers to generate institutional investigation findings.
     */
    private List<RiskDriverFindingDto> generateDeterministicFindings(
        List<ConsolidatedSecurityExposureDto> securities,
        ConcentrationSummaryDto summary,
        List<PairwiseOverlapResultDto> overlapMatrix,
        List<ConsolidatedSectorExposureDto> sectors,
        List<ConsolidatedMarketCapExposureDto> marketCaps,
        List<ConstituentFundSummaryDto> fundSummaries
    ) {
        List<RiskDriverFindingDto> findings = new ArrayList<>();
        int findingSeq = 1;

        // --- Finding 1: Single Security Concentration ---
        if (!securities.isEmpty()) {
            ConsolidatedSecurityExposureDto topSec = securities.get(0);
            if (topSec.consolidatedWeightPct().compareTo(THRESHOLD_SINGLE_SECURITY_PCT) >= 0) {
                boolean isHigh = topSec.consolidatedWeightPct().compareTo(THRESHOLD_SINGLE_SECURITY_HIGH_PCT) >= 0;
                findings.add(new RiskDriverFindingDto(
                    "FINDING-" + (findingSeq++),
                    "SINGLE_SECURITY",
                    isHigh ? "HIGH" : "MEDIUM",
                    "Substantial Single-Security Exposure: " + topSec.canonicalName(),
                    String.format(Locale.US, "%.2f%% of portfolio (held across %d fund%s)",
                        topSec.consolidatedWeightPct().doubleValue(), topSec.fundCount(), topSec.fundCount() > 1 ? "s" : ""),
                    "Single security weight >= " + THRESHOLD_SINGLE_SECURITY_PCT + "%",
                    String.format(Locale.US,
                        "%s represents %.2f%% of your total portfolio, exceeding the %.2f%% concentration threshold. Performance and drawdown risk are significantly sensitive to this individual issuer.",
                        topSec.canonicalName(), topSec.consolidatedWeightPct().doubleValue(), THRESHOLD_SINGLE_SECURITY_PCT.doubleValue()),
                    "Verify whether active fund managers are simultaneously overweight this issuer and whether the combined allocation complies with your single-stock risk tolerance.",
                    "Aggregated from individual fund holdings: " + formatContributionsEvidence(topSec.contributions())
                ));
            }
        }

        // --- Finding 2: Top-5 and Top-10 Concentration Density ---
        if (summary.top5WeightPct().compareTo(THRESHOLD_TOP5_PCT) >= 0 || summary.top10WeightPct().compareTo(THRESHOLD_TOP10_PCT) >= 0) {
            boolean isHigh = summary.top5WeightPct().compareTo(new BigDecimal("40.00")) >= 0 || summary.top10WeightPct().compareTo(new BigDecimal("55.00")) >= 0;
            findings.add(new RiskDriverFindingDto(
                "FINDING-" + (findingSeq++),
                "TOP_HOLDINGS",
                isHigh ? "HIGH" : "MEDIUM",
                "Concentrated Top-Holdings Weight",
                String.format(Locale.US, "Top 5 = %.2f%% | Top 10 = %.2f%%",
                    summary.top5WeightPct().doubleValue(), summary.top10WeightPct().doubleValue()),
                String.format(Locale.US, "Top 5 >= %.2f%% or Top 10 >= %.2f%%",
                    THRESHOLD_TOP5_PCT.doubleValue(), THRESHOLD_TOP10_PCT.doubleValue()),
                String.format(Locale.US,
                    "The top 10 underlying securities account for %.2f%% of total portfolio assets. The portfolio's capital is heavily clustered in a narrow tier of leading stocks.",
                    summary.top10WeightPct().doubleValue()),
                "Check if multi-manager overlap is unintentionally compounding exposure to the same handful of mega-cap stocks.",
                "Computed by summing the weights of the largest 5 and 10 consolidated equity positions."
            ));
        }

        // --- Finding 3: Cross-Fund Holdings Overlap & Duplication ---
        for (PairwiseOverlapResultDto pair : overlapMatrix) {
            if (pair.overlapWeightPct().compareTo(THRESHOLD_PAIRWISE_OVERLAP_PCT) >= 0) {
                boolean isHigh = pair.overlapWeightPct().compareTo(new BigDecimal("35.00")) >= 0;
                findings.add(new RiskDriverFindingDto(
                    "FINDING-" + (findingSeq++),
                    "SHARED_HOLDINGS",
                    isHigh ? "HIGH" : "MEDIUM",
                    "Elevated Pairwise Holdings Overlap: " + pair.fundAName() + " & " + pair.fundBName(),
                    String.format(Locale.US, "%.2f%% overlap (%d shared securities)",
                        pair.overlapWeightPct().doubleValue(), pair.sharedSecurityCount()),
                    "Pairwise equity overlap >= " + THRESHOLD_PAIRWISE_OVERLAP_PCT + "%",
                    String.format(Locale.US,
                        "These two funds share %d common holdings totaling %.2f%% overlapping portfolio weight. Investors holding both funds receive substantial holdings replication rather than complementary style diversification.",
                        pair.sharedSecurityCount(), pair.overlapWeightPct().doubleValue()),
                    "Examine the top shared positions to confirm whether holding both funds provides sufficient active differentiation to justify double fee loads.",
                    "Formula: sum(min(w_A, w_B)) across common equity lines (excluding cash equivalents)."
                ));
            }
        }

        // Check for specific large shared positions across funds
        for (ConsolidatedSecurityExposureDto sec : securities) {
            if (sec.fundCount() >= 2 && sec.consolidatedWeightPct().compareTo(THRESHOLD_SHARED_STOCK_PCT) >= 0) {
                findings.add(new RiskDriverFindingDto(
                    "FINDING-" + (findingSeq++),
                    "SHARED_HOLDINGS",
                    "INFO",
                    "Multi-Fund Duplicated Position: " + sec.canonicalName(),
                    String.format(Locale.US, "%.2f%% weight (held across %d funds)",
                        sec.consolidatedWeightPct().doubleValue(), sec.fundCount()),
                    "Shared across >= 2 funds and weight >= " + THRESHOLD_SHARED_STOCK_PCT + "%",
                    String.format(Locale.US,
                        "%s is held independently by %d constituent funds, aggregating to %.2f%% of your total capital.",
                        sec.canonicalName(), sec.fundCount(), sec.consolidatedWeightPct().doubleValue()),
                    "Review whether portfolio managers are holding this stock for the same active thesis or whether one fund is passive/closet-indexing.",
                    "Disclosed across constituent monthly portfolio statements."
                ));
                // Only report up to 2 individual duplicated stock findings to avoid clutter
                if (findingSeq > 6) break;
            }
        }

        // --- Finding 4: Sector Concentration ---
        for (ConsolidatedSectorExposureDto sector : sectors) {
            if (sector.consolidatedWeightPct().compareTo(THRESHOLD_SECTOR_CONCENTRATION_PCT) >= 0) {
                boolean isHigh = sector.consolidatedWeightPct().compareTo(new BigDecimal("35.00")) >= 0;
                findings.add(new RiskDriverFindingDto(
                    "FINDING-" + (findingSeq++),
                    "SECTOR_CONCENTRATION",
                    isHigh ? "HIGH" : "MEDIUM",
                    "Prominent Sector Concentration: " + sector.macroSector(),
                    String.format(Locale.US, "%.2f%% of portfolio (%d underlying stocks)",
                        sector.consolidatedWeightPct().doubleValue(), sector.classifiedSecuritiesCount()),
                    "Sector weight >= " + THRESHOLD_SECTOR_CONCENTRATION_PCT + "%",
                    String.format(Locale.US,
                        "%s accounts for %.2f%% of total look-through equity exposure across %d stocks. Sector-specific macroeconomic cycles, interest rate changes, or regulatory actions will have an outsized impact on portfolio NAV.",
                        sector.macroSector(), sector.consolidatedWeightPct().doubleValue(), sector.classifiedSecuritiesCount()),
                    "Assess whether this sector exposure aligns with your tactical asset allocation or whether it creates unintended sector bias.",
                    "Mapped using official NSE Indices macro-sector classification taxonomy."
                ));
            }
        }

        // --- Finding 5: Portfolio Breadth & Line Diversification ---
        if (summary.distinctSecuritiesCount() > 0 && summary.distinctSecuritiesCount() < THRESHOLD_BREADTH_MIN_SECURITIES) {
            findings.add(new RiskDriverFindingDto(
                "FINDING-" + (findingSeq++),
                "PORTFOLIO_BREADTH",
                "MEDIUM",
                "Narrow Portfolio Equity Breadth",
                String.format(Locale.US, "%d distinct securities (HHI: %.1f)",
                    summary.distinctSecuritiesCount(), summary.herfindahlHirschmanIndex().doubleValue()),
                "Distinct securities count < " + THRESHOLD_BREADTH_MIN_SECURITIES,
                String.format(Locale.US,
                    "The combined portfolio holds only %d distinct securities across all selected funds. While this reflects high active conviction, it reduces the buffer against individual stock-specific drawdowns.",
                    summary.distinctSecuritiesCount()),
                "Consider whether adding an active mid-cap or broad-market fund would broaden stock-specific diversification.",
                "Distinct securities count established via unique canonical ISIN mapping."
            ));
        } else if (summary.distinctSecuritiesCount() >= THRESHOLD_BREADTH_MIN_SECURITIES) {
            findings.add(new RiskDriverFindingDto(
                "FINDING-" + (findingSeq++),
                "PORTFOLIO_BREADTH",
                "INFO",
                "Well-Distributed Equity Breadth",
                String.format(Locale.US, "%d distinct securities (HHI: %.1f - %s)",
                    summary.distinctSecuritiesCount(), summary.herfindahlHirschmanIndex().doubleValue(), summary.hhiInterpretation()),
                "Distinct securities count >= " + THRESHOLD_BREADTH_MIN_SECURITIES,
                String.format(Locale.US,
                    "Capital is distributed across %d distinct securities with an HHI of %.1f (%s), indicating good dispersion across underlying holdings.",
                    summary.distinctSecuritiesCount(), summary.herfindahlHirschmanIndex().doubleValue(), summary.hhiInterpretation()),
                "Confirm that this broad distribution does not result in unintentional benchmark-tracking (closet indexing) that dilutes active returns.",
                "Herfindahl-Hirschman Index computed as sum(w_i^2) across all consolidated positions."
            ));
        }

        // --- Finding 6: Holdings Disclosure Coverage ---
        if (summary.disclosedEquityCoveragePct().compareTo(THRESHOLD_DISCLOSED_COVERAGE_PCT) < 0) {
            findings.add(new RiskDriverFindingDto(
                "FINDING-" + (findingSeq++),
                "DATA_COVERAGE",
                "WARNING",
                "Incomplete Look-Through Coverage",
                String.format(Locale.US, "Disclosed Equity: %.2f%% | Unmodeled: %.2f%%",
                    summary.disclosedEquityCoveragePct().doubleValue(), summary.unclassifiedWeightPct().doubleValue()),
                "Disclosed equity coverage < " + THRESHOLD_DISCLOSED_COVERAGE_PCT + "%",
                String.format(Locale.US,
                    "%.2f%% of portfolio assets are unclassified or held in cash/debt instruments. Full security-level look-through is not available for this portion of capital.",
                    summary.unclassifiedWeightPct().doubleValue()),
                "Consult the funds' monthly portfolio disclosures to review cash, derivative, or overseas holdings not covered in this equity look-through.",
                "Under YUKIRA's Zero Renormalization principle, missing weights are NEVER scaled to 100%."
            ));
        }

        // --- Finding 7: Temporal Snapshot Alignment ---
        int maxTemporalDelta = 0;
        for (PairwiseOverlapResultDto pair : overlapMatrix) {
            if (pair.temporalDeltaDays() > maxTemporalDelta) {
                maxTemporalDelta = pair.temporalDeltaDays();
            }
        }
        if (maxTemporalDelta > THRESHOLD_SNAPSHOT_LAG_DAYS) {
            findings.add(new RiskDriverFindingDto(
                "FINDING-" + (findingSeq++),
                "TEMPORAL_ALIGNMENT",
                "WARNING",
                "Asynchronous Portfolio Snapshot Dates",
                String.format(Locale.US, "Max reporting lag = %d days", maxTemporalDelta),
                "Snapshot gap > " + THRESHOLD_SNAPSHOT_LAG_DAYS + " days",
                String.format(Locale.US,
                    "Constituent fund portfolio statements are separated by %d days. Look-through weights and overlap measures reflect different market dates rather than a synchronized point in time.",
                    maxTemporalDelta),
                "Request updated portfolio disclosures from AMC to align look-through dates to the same calendar month-end.",
                "Identified by comparing portfolio_date across constituent fund monthly filings."
            ));
        } else {
            findings.add(new RiskDriverFindingDto(
                "FINDING-" + (findingSeq++),
                "TEMPORAL_ALIGNMENT",
                "INFO",
                "Synchronized Portfolio Snapshots",
                String.format(Locale.US, "Max reporting lag = %d days (Synchronized)", maxTemporalDelta),
                "Snapshot gap <= " + THRESHOLD_SNAPSHOT_LAG_DAYS + " days",
                "Constituent fund disclosures represent synchronized calendar month-end reporting windows, ensuring high comparability.",
                "Snapshot timing is sound for multi-fund look-through analysis.",
                "Portfolio dates verified from authoritative AMC disclosure filings."
            ));
        }

        return findings;
    }

    private String formatContributionsEvidence(List<FundSecurityContributionDto> contributions) {
        if (contributions == null || contributions.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contributions.size(); i++) {
            FundSecurityContributionDto c = contributions.get(i);
            if (i > 0) sb.append(", ");
            sb.append(c.fundName()).append(" (")
              .append(c.effectiveWeightContributionPct().setScale(2, RoundingMode.HALF_UP))
              .append("%)");
        }
        return sb.toString();
    }
}
