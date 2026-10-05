package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.enrichment.*;
import com.yukira.backend.dto.portfolio.HoldingDto;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundEnrichmentServiceTest {

    @Mock
    private SchemeRepository schemeRepository;
    @Mock
    private SchemeOptionRepository schemeOptionRepository;
    @Mock
    private PortfolioSnapshotRepository snapshotRepository;
    @Mock
    private PortfolioHoldingRepository holdingRepository;
    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;
    @Mock
    private SchemeManagerHistRepository managerHistRepository;
    @Mock
    private SchemeExpenseRatioRepository expenseRatioRepository;
    @Mock
    private SchemeInvestmentTermsRepository investmentTermsRepository;

    private FundEnrichmentService enrichmentService;

    private Scheme testScheme;
    private SchemePlan directPlan;
    private SchemeOption directOption;

    @BeforeEach
    void setUp() {
        enrichmentService = new FundEnrichmentService(
                schemeRepository,
                schemeOptionRepository,
                snapshotRepository,
                holdingRepository,
                securityIdentifierRepository,
                managerHistRepository,
                expenseRatioRepository,
                investmentTermsRepository
        );

        testScheme = new Scheme();
        testScheme.setId(10L);
        testScheme.setCode("HDFC_FLEXI");
        testScheme.setName("HDFC Flexi Cap Fund");

        directPlan = new SchemePlan();
        directPlan.setId(20L);
        directPlan.setScheme(testScheme);
        directPlan.setPlanType("DIRECT");

        directOption = new SchemeOption();
        directOption.setId(1L);
        directOption.setPlan(directPlan);
        directOption.setAmfiCode("118955");
        directOption.setIsin("INF179K01UT0");
        directOption.setOptionType("GROWTH");
    }

    @Test
    @DisplayName("Should return MISSING states cleanly when no enrichment data exists")
    void shouldReturnMissingStatesWhenNoDataExists() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(directOption));
        when(snapshotRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(expenseRatioRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByAsOfDateDesc(eq(1L), any()))
                .thenReturn(Optional.empty());
        when(managerHistRepository.findBySchemeIdOrderByStartDateDesc(10L))
                .thenReturn(Collections.emptyList());
        when(investmentTermsRepository.findTopBySchemeIdOrderByAsOfDateDesc(10L))
                .thenReturn(Optional.empty());

        EnrichedFundProfileDto profile = enrichmentService.getEnrichedProfile(1L, null);

        assertNotNull(profile);
        assertEquals(1L, profile.schemeOptionId());
        assertEquals("HDFC_FLEXI", profile.schemeCode());
        assertEquals("HDFC Flexi Cap Fund", profile.schemeName());
        assertEquals("DIRECT", profile.planType());
        assertEquals("GROWTH", profile.optionType());
        assertEquals("SOURCE_NOT_FOUND", profile.epistemicStatus());

        // Check Expense Ratio
        assertEquals("MISSING", profile.expenseRatio().status());
        assertNull(profile.expenseRatio().expenseRatio());
        assertNull(profile.expenseRatio().formattedPercentage());

        // Check AUM
        assertEquals("MISSING", profile.aum().status());
        assertNull(profile.aum().amount());
        assertNull(profile.aum().formattedAmount());

        // Check Manager
        assertTrue(profile.fundManagers().isEmpty());

        // Check Investment Terms
        assertEquals("MISSING", profile.investmentTerms().status());
        assertNull(profile.investmentTerms().minSipAmount());

        // Check Holdings Summary
        assertEquals("MISSING", profile.holdingsSummary().status());
    }

    @Test
    @DisplayName("Should strictly isolate Direct vs Regular expense ratio and format values")
    void shouldIsolateDirectVsRegularExpenseRatio() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(directOption));

        SchemeExpenseRatio directTer = new SchemeExpenseRatio();
        directTer.setId(101L);
        directTer.setSchemeOption(directOption);
        directTer.setAsOfDate(LocalDate.of(2024, 1, 31));
        directTer.setExpenseRatio(new BigDecimal("0.0078")); // 0.78%
        directTer.setRegularPlanRatio(new BigDecimal("0.0144")); // 1.44%
        directTer.setPlanType("DIRECT");
        directTer.setOptionType("GROWTH");
        directTer.setQualityAssessment("VERIFIED");

        when(expenseRatioRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByAsOfDateDesc(eq(1L), any()))
                .thenReturn(Optional.of(directTer));

        EnrichedFundProfileDto profile = enrichmentService.getEnrichedProfile(1L, null);

        assertNotNull(profile);
        ExpenseRatioDto ter = profile.expenseRatio();
        assertEquals("AVAILABLE", ter.status());
        assertEquals("VERIFIED", ter.dataQuality());
        assertEquals("0.78%", ter.formattedPercentage());
        assertEquals(new BigDecimal("0.0078"), ter.expenseRatio());
        assertEquals("1.44%", ter.formattedRegularPlanPercentage());
        assertEquals("DIRECT", ter.planType());
        assertEquals(LocalDate.of(2024, 1, 31), ter.asOfDate());
    }

    @Test
    @DisplayName("Should preserve AUM, Manager, Investment Terms, and Holdings with provenance")
    void shouldPreserveAumManagerAndHoldingsValues() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(directOption));

        // Expense ratio
        when(expenseRatioRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByAsOfDateDesc(eq(1L), any()))
                .thenReturn(Optional.empty());

        // Manager
        SchemeManagerHist manager = new SchemeManagerHist();
        manager.setScheme(testScheme);
        manager.setManagerName("Ms. Roshi Jain");
        manager.setRole("Senior Fund Manager");
        manager.setStartDate(LocalDate.of(2022, 7, 29));
        manager.setAsOfDate(LocalDate.of(2024, 1, 31));
        manager.setQualityAssessment("VERIFIED");
        when(managerHistRepository.findBySchemeIdOrderByStartDateDesc(10L))
                .thenReturn(List.of(manager));

        // Investment Terms
        SchemeInvestmentTerms terms = new SchemeInvestmentTerms();
        terms.setScheme(testScheme);
        terms.setAsOfDate(LocalDate.of(2024, 1, 1));
        terms.setMinSipAmount(new BigDecimal("100.00"));
        terms.setSipFrequencies("Monthly, Weekly, Daily, Quarterly");
        terms.setMinLumpsumAmount(new BigDecimal("100.00"));
        terms.setMinAdditionalAmount(new BigDecimal("100.00"));
        terms.setExitLoadDescription("1.00% if redeemed within 1 year; Nil thereafter");
        terms.setQualityAssessment("VERIFIED");
        when(investmentTermsRepository.findTopBySchemeIdOrderByAsOfDateDesc(10L))
                .thenReturn(Optional.of(terms));

        // Portfolio Snapshot (AUM)
        PortfolioSnapshot snapshot = new PortfolioSnapshot();
        snapshot.setId(501L);
        snapshot.setSchemeOption(directOption);
        snapshot.setPortfolioDate(LocalDate.of(2024, 1, 31));
        snapshot.setReportedTotalNetAssets(new BigDecimal("47642.42")); // In Crores
        snapshot.setReportedHoldingsCount(10);
        snapshot.setSumReportedWeights(new BigDecimal("0.5082"));
        when(snapshotRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(eq(1L), any()))
                .thenReturn(Optional.of(snapshot));

        EnrichedFundProfileDto profile = enrichmentService.getEnrichedProfile(1L, null);

        assertNotNull(profile);

        // AUM check
        AumDto aum = profile.aum();
        assertEquals("AVAILABLE", aum.status());
        assertEquals("VERIFIED", aum.dataQuality());
        assertEquals("INR", aum.currency());
        assertEquals("Crores", aum.unit());
        assertEquals(new BigDecimal("47642.42"), aum.amount());
        assertEquals("₹47,642.42 Cr", aum.formattedAmount());
        assertEquals(LocalDate.of(2024, 1, 31), aum.asOfDate());

        // Manager check
        assertEquals(1, profile.fundManagers().size());
        FundManagerDto mgr = profile.fundManagers().get(0);
        assertEquals("VERIFIED", mgr.dataQuality());
        assertEquals("Ms. Roshi Jain", mgr.name());
        assertEquals("Senior Fund Manager", mgr.role());
        assertEquals(LocalDate.of(2022, 7, 29), mgr.startDate());

        // Investment Terms check
        InvestmentTermsDto termDto = profile.investmentTerms();
        assertEquals("AVAILABLE", termDto.status());
        assertEquals("VERIFIED", termDto.dataQuality());
        assertEquals("₹100", termDto.formattedMinSip());
        assertEquals("₹100", termDto.formattedMinLumpsum());
        assertEquals("1.00% if redeemed within 1 year; Nil thereafter", termDto.exitLoadDescription());

        // Holdings Summary check
        HoldingsSummaryDto holdings = profile.holdingsSummary();
        assertEquals("AVAILABLE", holdings.status());
        assertEquals(10, holdings.reportedHoldingsCount());
        assertEquals("50.82%", holdings.formattedTopConcentration());
    }

    @Test
    @DisplayName("Should retrieve detailed holdings with ISIN and formatted weights without renormalization")
    void shouldRetrieveDetailedHoldings() {
        PortfolioSnapshot snapshot = new PortfolioSnapshot();
        snapshot.setId(501L);
        snapshot.setSchemeOption(directOption);
        snapshot.setPortfolioDate(LocalDate.of(2024, 1, 31));

        Security sec1 = new Security();
        sec1.setId(1001L);
        sec1.setCanonicalName("ICICI Bank Ltd.");
        sec1.setAssetClass("Equity");
        sec1.setSector("Financial Services");

        Security sec2 = new Security();
        sec2.setId(1002L);
        sec2.setCanonicalName("Cipla Ltd.");
        sec2.setAssetClass("Equity");
        sec2.setSector("Healthcare");

        PortfolioHolding holding1 = new PortfolioHolding();
        holding1.setId(1L);
        holding1.setPortfolioSnapshot(snapshot);
        holding1.setSecurity(sec1);
        holding1.setReportedWeight(new BigDecimal("0.0950"));

        PortfolioHolding holding2 = new PortfolioHolding();
        holding2.setId(2L);
        holding2.setPortfolioSnapshot(snapshot);
        holding2.setSecurity(sec2);
        holding2.setReportedWeight(new BigDecimal("0.0539"));

        SecurityIdentifier isin1 = new SecurityIdentifier();
        isin1.setSecurity(sec1);
        isin1.setIdType("ISIN");
        isin1.setIdValue("INE090A01021");

        SecurityIdentifier isin2 = new SecurityIdentifier();
        isin2.setSecurity(sec2);
        isin2.setIdType("ISIN");
        isin2.setIdValue("INE059A01026");

        when(snapshotRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(eq(1L), any()))
                .thenReturn(Optional.of(snapshot));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(501L))
                .thenReturn(List.of(holding1, holding2));
        when(securityIdentifierRepository.findBySecurityIdInAndIdType(List.of(1001L, 1002L), "ISIN"))
                .thenReturn(List.of(isin1, isin2));

        List<HoldingDto> result = enrichmentService.getHoldings(1L, null);

        assertNotNull(result);
        assertEquals(2, result.size());

        HoldingDto dto1 = result.get(0);
        assertEquals("ICICI Bank Ltd.", dto1.securityName());
        assertEquals("9.50%", dto1.formattedWeight());
        assertEquals(new BigDecimal("0.0950"), dto1.weight());
        assertEquals("INE090A01021", dto1.isin());

        HoldingDto dto2 = result.get(1);
        assertEquals("Cipla Ltd.", dto2.securityName());
        assertEquals("5.39%", dto2.formattedWeight());
        assertEquals(new BigDecimal("0.0539"), dto2.weight());
        assertEquals("INE059A01026", dto2.isin());

        // Verify weights are raw source fractions without forced 100% renormalization
        BigDecimal rawSum = result.stream().map(HoldingDto::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("0.1489").compareTo(rawSum));
    }

    @Test
    @DisplayName("Should strictly isolate scheme-level enrichment from option-specific facts")
    void shouldStrictlyIsolateSchemeLevelEnrichment() {
        when(schemeRepository.findById(10L)).thenReturn(Optional.of(testScheme));

        SchemeManagerHist manager = new SchemeManagerHist();
        manager.setScheme(testScheme);
        manager.setManagerName("Ms. Roshi Jain");
        manager.setRole("Senior Fund Manager");
        manager.setStartDate(LocalDate.of(2022, 7, 29));
        manager.setAsOfDate(LocalDate.of(2024, 1, 31));
        manager.setQualityAssessment("VERIFIED");
        when(managerHistRepository.findBySchemeIdOrderByStartDateDesc(10L))
                .thenReturn(List.of(manager));

        SchemeInvestmentTerms terms = new SchemeInvestmentTerms();
        terms.setScheme(testScheme);
        terms.setAsOfDate(LocalDate.of(2024, 1, 1));
        terms.setMinSipAmount(new BigDecimal("100.00"));
        terms.setMinLumpsumAmount(new BigDecimal("100.00"));
        terms.setMinAdditionalAmount(new BigDecimal("100.00"));
        terms.setExitLoadDescription("1.00% if redeemed within 1 year; Nil after 1 year");
        terms.setQualityAssessment("VERIFIED");
        when(investmentTermsRepository.findTopBySchemeIdOrderByAsOfDateDesc(10L))
                .thenReturn(Optional.of(terms));

        EnrichedFundProfileDto schemeProfile = enrichmentService.getSchemeLevelEnrichment(10L, null);

        assertNotNull(schemeProfile);
        assertEquals(10L, schemeProfile.schemeId());
        assertNull(schemeProfile.schemeOptionId());
        assertNull(schemeProfile.planType());
        assertNull(schemeProfile.optionType());
        assertNull(schemeProfile.amfiCode());
        assertNull(schemeProfile.isin());

        // Option-specific fields MUST be MISSING
        assertEquals("MISSING", schemeProfile.expenseRatio().status());
        assertEquals("MISSING", schemeProfile.aum().status());
        assertEquals("MISSING", schemeProfile.holdingsSummary().status());

        // Scheme-level fields MUST be AVAILABLE
        assertEquals("AVAILABLE", schemeProfile.investmentTerms().status());
        assertEquals("₹100", schemeProfile.investmentTerms().formattedMinSip());
        assertEquals(1, schemeProfile.fundManagers().size());
        assertEquals("Ms. Roshi Jain", schemeProfile.fundManagers().get(0).name());
    }

    @Test
    @DisplayName("Should deterministically format AUM in Crores")
    void shouldFormatAumDeterministically() {
        assertEquals("₹47,642.42 Cr", FundEnrichmentService.formatAumInCrores(new BigDecimal("47642.42")));
        assertEquals("Not available", FundEnrichmentService.formatAumInCrores(null));
    }

    @Test
    @DisplayName("Should preserve null lock-in period for open-ended schemes and never convert missing lock-in to zero")
    void shouldNotConvertMissingLockInToNumericZero() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(directOption));

        SchemeInvestmentTerms terms = new SchemeInvestmentTerms();
        terms.setScheme(testScheme);
        terms.setAsOfDate(LocalDate.of(2024, 1, 1));
        terms.setMinSipAmount(new BigDecimal("100.00"));
        terms.setLockInPeriodDays(null); // Unspecified / Open-ended

        when(investmentTermsRepository.findTopBySchemeIdOrderByAsOfDateDesc(10L))
                .thenReturn(Optional.of(terms));

        EnrichedFundProfileDto profile = enrichmentService.getEnrichedProfile(1L, null);

        assertNotNull(profile.investmentTerms());
        assertNull(profile.investmentTerms().lockInPeriodDays(), "Missing or non-applicable lock-in must remain null and NEVER be converted to numeric 0");
    }
}
