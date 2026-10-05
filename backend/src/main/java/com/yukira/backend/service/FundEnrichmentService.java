package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.enrichment.*;
import com.yukira.backend.dto.portfolio.HoldingDto;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating factual, authoritative Fund Information Enrichment:
 * AUM, Expense Ratio (TER), Fund Manager history, SIP/Lumpsum investment terms, and Holdings.
 *
 * Epistemic Rules:
 * 1. Zero financial math or estimations in presentation tier.
 * 2. If data is not present in authoritative ledgers, return explicit MISSING / NOT AVAILABLE states.
 * 3. Exact plan/option separation (Direct plan never displays Regular plan figures).
 * 4. Deterministic formatting performed exclusively on backend.
 */
@Service
@Transactional(readOnly = true)
public class FundEnrichmentService {

    private final SchemeRepository schemeRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final SchemeManagerHistRepository managerHistRepository;
    private final SchemeExpenseRatioRepository expenseRatioRepository;
    private final SchemeInvestmentTermsRepository investmentTermsRepository;

    public FundEnrichmentService(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        SchemeManagerHistRepository managerHistRepository,
        SchemeExpenseRatioRepository expenseRatioRepository,
        SchemeInvestmentTermsRepository investmentTermsRepository
    ) {
        this.schemeRepository = schemeRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.managerHistRepository = managerHistRepository;
        this.expenseRatioRepository = expenseRatioRepository;
        this.investmentTermsRepository = investmentTermsRepository;
    }

    /**
     * Retrieves enriched fund profile for a specific scheme option under point-in-time constraints.
     */
    public EnrichedFundProfileDto getEnrichedProfile(Long schemeOptionId, OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme option #" + schemeOptionId + " not found."));

        SchemePlan plan = option.getPlan();
        Scheme scheme = plan != null ? plan.getScheme() : null;
        Long schemeId = scheme != null ? scheme.getId() : null;
        String schemeName = scheme != null ? scheme.getName() : "Unknown Scheme";
        String schemeCode = scheme != null ? scheme.getCode() : "UNKNOWN";
        String planType = plan != null ? plan.getPlanType() : "DIRECT";
        String optionType = option.getOptionType() != null ? option.getOptionType() : "GROWTH";

        // 1. AUM & Holdings Summary from Portfolio Snapshot
        Optional<PortfolioSnapshot> latestSnapshotOpt = snapshotRepository
            .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(schemeOptionId, cutoff);

        AumDto aumDto;
        HoldingsSummaryDto holdingsSummaryDto;

        if (latestSnapshotOpt.isPresent()) {
            PortfolioSnapshot snapshot = latestSnapshotOpt.get();
            BigDecimal netAssets = snapshot.getReportedTotalNetAssets();
            String formattedAum = netAssets != null ? formatAumInCrores(netAssets) : null;
            Long artifactId = snapshot.getSourceArtifact() != null ? snapshot.getSourceArtifact().getId() : null;

            aumDto = new AumDto(
                netAssets,
                formattedAum,
                "INR",
                "Crores",
                snapshot.getPortfolioDate(),
                artifactId,
                "Official AMC Monthly Portfolio Disclosure",
                "VERIFIED",
                netAssets != null ? "AVAILABLE" : "MISSING"
            );

            holdingsSummaryDto = new HoldingsSummaryDto(
                snapshot.getPortfolioDate(),
                snapshot.getReportedHoldingsCount() != null ? snapshot.getReportedHoldingsCount() : 0,
                snapshot.getSumReportedWeights(),
                snapshot.getSumReportedWeights() != null ? formatPercentage(snapshot.getSumReportedWeights()) : null,
                artifactId,
                "SEBI Monthly Portfolio Disclosure",
                "VERIFIED",
                "AVAILABLE"
            );
        } else {
            aumDto = AumDto.missing();
            holdingsSummaryDto = HoldingsSummaryDto.missing();
        }

        // 2. Expense Ratio (TER)
        Optional<SchemeExpenseRatio> expenseOpt = expenseRatioRepository
            .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByAsOfDateDesc(schemeOptionId, cutoff);

        ExpenseRatioDto expenseRatioDto;
        if (expenseOpt.isPresent()) {
            SchemeExpenseRatio ser = expenseOpt.get();
            Long artifactId = ser.getSourceArtifact() != null ? ser.getSourceArtifact().getId() : null;

            expenseRatioDto = new ExpenseRatioDto(
                ser.getExpenseRatio(),
                formatPercentage(ser.getExpenseRatio()),
                ser.getPlanType(),
                ser.getOptionType(),
                ser.getRegularPlanRatio(),
                ser.getRegularPlanRatio() != null ? formatPercentage(ser.getRegularPlanRatio()) : null,
                ser.getAsOfDate(),
                artifactId,
                "AMC Statutory Total Expense Ratio (TER) Disclosure",
                ser.getQualityAssessment() != null ? ser.getQualityAssessment() : "VALID",
                "AVAILABLE"
            );
        } else {
            expenseRatioDto = ExpenseRatioDto.missing(planType, optionType);
        }

        // 3. Fund Managers
        List<FundManagerDto> fundManagers = new ArrayList<>();
        if (schemeId != null) {
            List<SchemeManagerHist> managers = managerHistRepository.findBySchemeIdOrderByStartDateDesc(schemeId);
            for (SchemeManagerHist mgr : managers) {
                Long artifactId = mgr.getSourceArtifact() != null ? mgr.getSourceArtifact().getId() : null;
                fundManagers.add(new FundManagerDto(
                    mgr.getManagerName(),
                    mgr.getRole(),
                    mgr.getStartDate(),
                    mgr.getEndDate(),
                    mgr.getAsOfDate() != null ? mgr.getAsOfDate() : mgr.getStartDate(),
                    artifactId,
                    "Official AMC Scheme Factsheet & SID",
                    mgr.getQualityAssessment() != null ? mgr.getQualityAssessment() : "VALID",
                    "AVAILABLE"
                ));
            }
        }

        // 4. Investment Terms (SIP / Lumpsum)
        InvestmentTermsDto investmentTermsDto;
        if (schemeId != null) {
            Optional<SchemeInvestmentTerms> termsOpt = investmentTermsRepository
                .findTopBySchemeIdOrderByAsOfDateDesc(schemeId);

            if (termsOpt.isPresent()) {
                SchemeInvestmentTerms sit = termsOpt.get();
                List<String> frequencies = sit.getSipFrequencies() != null
                    ? Arrays.stream(sit.getSipFrequencies().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList())
                    : List.of("Monthly");

                Long artifactId = sit.getSourceArtifact() != null ? sit.getSourceArtifact().getId() : null;

                investmentTermsDto = new InvestmentTermsDto(
                    sit.getMinSipAmount(),
                    sit.getMinSipAmount() != null ? "₹" + formatIndianNumber(sit.getMinSipAmount()) : null,
                    frequencies,
                    sit.getMinLumpsumAmount(),
                    sit.getMinLumpsumAmount() != null ? "₹" + formatIndianNumber(sit.getMinLumpsumAmount()) : null,
                    sit.getMinAdditionalAmount(),
                    sit.getMinAdditionalAmount() != null ? "₹" + formatIndianNumber(sit.getMinAdditionalAmount()) : null,
                    sit.getLockInPeriodDays(),
                    sit.getExitLoadDescription(),
                    sit.getAsOfDate(),
                    artifactId,
                    sit.getSourceDocumentTitle() != null ? sit.getSourceDocumentTitle() : "Official Key Information Memorandum (KIM)",
                    sit.getQualityAssessment() != null ? sit.getQualityAssessment() : "VALID",
                    "AVAILABLE"
                );
            } else {
                investmentTermsDto = InvestmentTermsDto.missing();
            }
        } else {
            investmentTermsDto = InvestmentTermsDto.missing();
        }

        // 5. Consolidated epistemic status
        boolean hasAum = "AVAILABLE".equals(aumDto.status());
        boolean hasTer = "AVAILABLE".equals(expenseRatioDto.status());
        boolean hasMgr = !fundManagers.isEmpty();
        boolean hasTerms = "AVAILABLE".equals(investmentTermsDto.status());

        String epistemicStatus;
        if (hasAum && hasTer && hasMgr && hasTerms) {
            epistemicStatus = "VERIFIED_PRIMARY_SOURCE";
        } else if (hasAum || hasTer || hasMgr || hasTerms) {
            epistemicStatus = "PARTIAL_SOURCE_COVERAGE";
        } else {
            epistemicStatus = "SOURCE_NOT_FOUND";
        }

        return new EnrichedFundProfileDto(
            schemeId,
            schemeOptionId,
            schemeName,
            schemeCode,
            planType,
            optionType,
            option.getAmfiCode(),
            option.getIsin(),
            aumDto,
            expenseRatioDto,
            fundManagers,
            investmentTermsDto,
            holdingsSummaryDto,
            epistemicStatus
        );
    }

    /**
     * Retrieves scheme-level enrichment facts (Fund Managers and statutory KIM investment terms).
     * Modeled as scheme-level based on the identified source/document structure; source payload remains SOURCE_REFERENCE_ONLY.
     * Option-specific facts (TER, AUM, holdings) are strictly set to MISSING/NOT_APPLICABLE because they
     * require an exact scheme_option_id and cannot be substituted or assumed across plans/options.
     */
    public EnrichedFundProfileDto getSchemeLevelEnrichment(Long schemeId, OffsetDateTime knowledgeCutoff) {
        Scheme scheme = schemeRepository.findById(schemeId)
            .orElseThrow(() -> new IllegalArgumentException("Scheme #" + schemeId + " not found."));

        // Option-specific fields are strictly unavailable at scheme level
        AumDto aumDto = AumDto.missing();
        ExpenseRatioDto expenseRatioDto = ExpenseRatioDto.missing("ALL_PLANS", "ALL_OPTIONS");
        HoldingsSummaryDto holdingsSummaryDto = HoldingsSummaryDto.missing();

        // 1. Fund Managers (Modeled as scheme-level based on identified source structure; source payload remains SOURCE_REFERENCE_ONLY)
        List<FundManagerDto> fundManagers = new ArrayList<>();
        List<SchemeManagerHist> managers = managerHistRepository.findBySchemeIdOrderByStartDateDesc(schemeId);
        for (SchemeManagerHist mgr : managers) {
            Long artifactId = mgr.getSourceArtifact() != null ? mgr.getSourceArtifact().getId() : null;
            fundManagers.add(new FundManagerDto(
                mgr.getManagerName(),
                mgr.getRole(),
                mgr.getStartDate(),
                mgr.getEndDate(),
                mgr.getAsOfDate() != null ? mgr.getAsOfDate() : mgr.getStartDate(),
                artifactId,
                "Official AMC Scheme Factsheet & SID",
                mgr.getQualityAssessment() != null ? mgr.getQualityAssessment() : "VALID",
                "AVAILABLE"
            ));
        }

        // 2. Investment Terms (Modeled as scheme-level based on identified source structure; source payload remains SOURCE_REFERENCE_ONLY)
        InvestmentTermsDto investmentTermsDto;
        Optional<SchemeInvestmentTerms> termsOpt = investmentTermsRepository
            .findTopBySchemeIdOrderByAsOfDateDesc(schemeId);

        if (termsOpt.isPresent()) {
            SchemeInvestmentTerms sit = termsOpt.get();
            List<String> frequencies = sit.getSipFrequencies() != null
                ? Arrays.stream(sit.getSipFrequencies().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList())
                : List.of("Monthly");

            Long artifactId = sit.getSourceArtifact() != null ? sit.getSourceArtifact().getId() : null;

            investmentTermsDto = new InvestmentTermsDto(
                sit.getMinSipAmount(),
                sit.getMinSipAmount() != null ? "₹" + formatIndianNumber(sit.getMinSipAmount()) : null,
                frequencies,
                sit.getMinLumpsumAmount(),
                sit.getMinLumpsumAmount() != null ? "₹" + formatIndianNumber(sit.getMinLumpsumAmount()) : null,
                sit.getMinAdditionalAmount(),
                sit.getMinAdditionalAmount() != null ? "₹" + formatIndianNumber(sit.getMinAdditionalAmount()) : null,
                sit.getLockInPeriodDays(),
                sit.getExitLoadDescription(),
                sit.getAsOfDate(),
                artifactId,
                sit.getSourceDocumentTitle() != null ? sit.getSourceDocumentTitle() : "Statutory KIM / SID",
                sit.getQualityAssessment() != null ? sit.getQualityAssessment() : "VALID",
                "AVAILABLE"
            );
        } else {
            investmentTermsDto = InvestmentTermsDto.missing();
        }

        return new EnrichedFundProfileDto(
            scheme.getId(),
            null, // schemeOptionId is null for scheme-level queries
            scheme.getName(),
            scheme.getCode(),
            null, // planType is null for scheme-level
            null, // optionType is null for scheme-level
            null, // amfiCode is null for scheme-level
            null, // isin is null for scheme-level
            aumDto,
            expenseRatioDto,
            fundManagers,
            investmentTermsDto,
            holdingsSummaryDto,
            "SCHEME_LEVEL_AGGREGATE"
        );
    }

    /**
     * Retrieves detailed portfolio holdings for a scheme option under point-in-time constraints.
     */
    public List<HoldingDto> getHoldings(Long schemeOptionId, OffsetDateTime knowledgeCutoff) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        return snapshotRepository
            .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(schemeOptionId, cutoff)
            .map(snapshot -> {
                List<PortfolioHolding> holdings = holdingRepository
                    .findByPortfolioSnapshotIdOrderByReportedWeightDesc(snapshot.getId());

                if (holdings.isEmpty()) {
                    return Collections.<HoldingDto>emptyList();
                }

                // Batch resolve ISIN security identifiers
                List<Long> secIds = holdings.stream()
                    .map(h -> h.getSecurity().getId())
                    .distinct()
                    .collect(Collectors.toList());

                Map<Long, String> isinMap = securityIdentifierRepository
                    .findBySecurityIdInAndIdType(secIds, "ISIN")
                    .stream()
                    .collect(Collectors.toMap(
                        si -> si.getSecurity().getId(),
                        SecurityIdentifier::getIdValue,
                        (existing, replacement) -> existing
                    ));

                String sourceUri = snapshot.getSourceArtifact() != null && snapshot.getSourceArtifact().getStorageUri() != null
                    ? snapshot.getSourceArtifact().getStorageUri()
                    : "SEBI Monthly Portfolio Disclosure";

                return holdings.stream().map(h -> {
                    Security sec = h.getSecurity();
                    BigDecimal weight = h.getReportedWeight();
                    String formattedWeight = weight != null ? formatPercentage(weight) : "Not available";
                    String isin = isinMap.get(sec.getId());
                    String category = sec.getSector() != null ? sec.getSector() : sec.getAssetClass();

                    return new HoldingDto(
                        sec.getCanonicalName(),
                        weight,
                        formattedWeight,
                        category,
                        sec.getAssetClass(),
                        sec.getSector(),
                        isin,
                        snapshot.getPortfolioDate(),
                        sourceUri,
                        "VERIFIED"
                    );
                }).collect(Collectors.toList());
            })
            .orElse(Collections.emptyList());
    }

    // -------------------------------------------------------------------------
    // Deterministic formatting helpers (Zero math in frontend)
    // -------------------------------------------------------------------------

    public static String formatPercentage(BigDecimal val) {
        if (val == null) return "Not available";
        BigDecimal pct = val.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        return pct.toPlainString() + "%";
    }

    public static String formatAumInCrores(BigDecimal val) {
        if (val == null) return "Not available";
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return "₹" + df.format(val) + " Cr";
    }

    public static String formatIndianNumber(BigDecimal val) {
        if (val == null) return "Not available";
        DecimalFormat df = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return df.format(val);
    }
}
