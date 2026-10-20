package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.PortfolioConstructionRiskResponseDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.PortfolioConstructionRiskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioConstructionRiskServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioHoldingRepository holdingRepository;

    @Mock
    private SecurityRepository securityRepository;

    @InjectMocks
    private PortfolioConstructionRiskService constructionRiskService;

    private SchemeOption option;
    private PortfolioSnapshot snap2024;
    private PortfolioSnapshot snap2026June;
    private PortfolioSnapshot snap2026Sep;

    @BeforeEach
    void setUp() {
        Scheme scheme = new Scheme();
        scheme.setId(10L);
        scheme.setName("HDFC Flexi Cap Fund");
        scheme.setCode("HDFC_FLEXI");

        SchemePlan plan = new SchemePlan();
        plan.setScheme(scheme);
        plan.setPlanType("DIRECT");

        option = new SchemeOption();
        option.setId(1L);
        option.setPlan(plan);
        option.setAmfiCode("118955");
        option.setOptionType("GROWTH");

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(option));

        // Snapshot 1: 2024-01-31 (Factsheet Top 10 disclosure: 54.82% weight)
        snap2024 = new PortfolioSnapshot();
        snap2024.setId(2L);
        snap2024.setSchemeOption(option);
        snap2024.setPortfolioDate(LocalDate.of(2024, 1, 31));
        snap2024.setAvailabilityTime(OffsetDateTime.of(2024, 2, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap2024.setRevisionSeq(1);
        snap2024.setReportedHoldingsCount(2);

        // Snapshot 2: 2026-06-30 (Full portfolio disclosure: 100.00% weight, with duplicate Cash security)
        snap2026June = new PortfolioSnapshot();
        snap2026June.setId(1943L);
        snap2026June.setSchemeOption(option);
        snap2026June.setPortfolioDate(LocalDate.of(2026, 6, 30));
        snap2026June.setAvailabilityTime(OffsetDateTime.of(2026, 7, 10, 12, 0, 0, 0, ZoneOffset.UTC));
        snap2026June.setRevisionSeq(1);
        snap2026June.setReportedHoldingsCount(3);

        // Snapshot 3: 2026-09-30 (Full portfolio with 100.53% accrual deviation)
        snap2026Sep = new PortfolioSnapshot();
        snap2026Sep.setId(1940L);
        snap2026Sep.setSchemeOption(option);
        snap2026Sep.setPortfolioDate(LocalDate.of(2026, 9, 30));
        snap2026Sep.setAvailabilityTime(OffsetDateTime.of(2026, 10, 10, 12, 0, 0, 0, ZoneOffset.UTC));
        snap2026Sep.setRevisionSeq(1);
        snap2026Sep.setReportedHoldingsCount(4);
    }

    @Test
    @DisplayName("Detects 100.53% net assets accrual deviation and aggregates multiple instruments under same issuer")
    void shouldReconcileWeightsAndAggregateIssuers() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 15, 23, 59, 59, 0, ZoneOffset.UTC);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap2026Sep));

        // Holdings for Sep 2026:
        // Instrument 1: ICICI Bank Equity (8.80%)
        Security iciciEquity = new Security();
        iciciEquity.setId(101L);
        iciciEquity.setCanonicalName("ICICI Bank Ltd.");
        iciciEquity.setIssuerName("ICICI Bank Ltd.");
        iciciEquity.setSector("Financial Services");
        iciciEquity.setAssetClass("Equity");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setId(1L);
        h1.setPortfolioSnapshot(snap2026Sep);
        h1.setSecurity(iciciEquity);
        h1.setReportedWeight(new BigDecimal("0.088000"));

        // Instrument 2: ICICI Bank Tier-2 Bond (1.20%) - Same issuer
        Security iciciBond = new Security();
        iciciBond.setId(102L);
        iciciBond.setCanonicalName("ICICI Bank 8.15% 2028");
        iciciBond.setIssuerName("ICICI Bank Ltd.");
        iciciBond.setSector("Financial Services");
        iciciBond.setAssetClass("Debt");

        PortfolioHolding h2 = new PortfolioHolding();
        h2.setId(2L);
        h2.setPortfolioSnapshot(snap2026Sep);
        h2.setSecurity(iciciBond);
        h2.setReportedWeight(new BigDecimal("0.012000"));

        // Instrument 3: Infosys Equity (87.72%)
        Security infy = new Security();
        infy.setId(103L);
        infy.setCanonicalName("Infosys Ltd.");
        infy.setIssuerName("Infosys Ltd.");
        infy.setSector("Information Technology");
        infy.setAssetClass("Equity");

        PortfolioHolding h3 = new PortfolioHolding();
        h3.setId(3L);
        h3.setPortfolioSnapshot(snap2026Sep);
        h3.setSecurity(infy);
        h3.setReportedWeight(new BigDecimal("0.877200"));

        // Instrument 4: TREPS Cash (2.81%)
        Security cash = new Security();
        cash.setId(9870L);
        cash.setCanonicalName("TREPS / Cash Margin");
        cash.setIssuerName("Cash & Equivalents");
        cash.setSector("Cash & Cash Equivalents");
        cash.setAssetClass("Cash");

        PortfolioHolding h4 = new PortfolioHolding();
        h4.setId(4L);
        h4.setPortfolioSnapshot(snap2026Sep);
        h4.setSecurity(cash);
        h4.setReportedWeight(new BigDecimal("0.028100"));

        // Total weights = 0.088 + 0.012 + 0.8772 + 0.0281 = 1.005300 (100.53%)
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1940L))
            .thenReturn(List.of(h3, h1, h4, h2));

        PortfolioConstructionRiskResponseDto response = constructionRiskService.calculateConstructionRisk(1L, LocalDate.of(2026, 9, 30), cutoff);

        assertThat(response.status()).isEqualTo("SUCCESS");
        var dataRel = response.dataReliability();
        assertThat(dataRel.sumReportedWeightsPct()).isEqualTo("100.53%");
        assertThat(dataRel.weightReconciliationCode()).isEqualTo("NET_ASSETS_ACCRUAL_DEVIATION");
        assertThat(dataRel.weightReconciliationExplanation()).contains("gross asset weights naturally exceed 100.00%");

        // Issuer Aggregation: ICICI Bank (0.088 + 0.012 = 0.1000 = 10.00% across 2 instruments)
        var issuerConc = response.issuerConcentration();
        assertThat(issuerConc.totalIssuersDisclosed()).isEqualTo(3); // Infosys, ICICI Bank, Cash
        var iciciIssuer = issuerConc.topIssuers().stream()
            .filter(iss -> iss.issuerName().equals("ICICI Bank Ltd."))
            .findFirst()
            .orElseThrow();
        assertThat(iciciIssuer.totalWeightPct()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(iciciIssuer.instrumentsHeldCount()).isEqualTo(2);

        // Standard HHI and Effective Number of Holdings
        // Issuer weights: Infosys = 87.72%, ICICI Bank = 10.00%, Cash = 2.81%
        // HHI = 87.72^2 + 10^2 + 2.81^2 = 7694.7984 + 100 + 7.8961 = 7802.69
        assertThat(issuerConc.issuerHhi()).isGreaterThan(new BigDecimal("7000.00"));
        assertThat(issuerConc.effectiveNumberOfIssuers()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Identifies duplicate security positions and flags non-comparable 29-month gap")
    void shouldIdentifyDuplicateHoldingsAndFlagGap() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 7, 15, 23, 59, 59, 0, ZoneOffset.UTC);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap2026June, snap2024));

        // Holdings for 2024 (Factsheet Top 10: 54.82%)
        Security hdfc = new Security();
        hdfc.setId(201L);
        hdfc.setCanonicalName("HDFC Bank Ltd.");
        hdfc.setSector("Financial Services");
        hdfc.setAssetClass("Equity");

        PortfolioHolding ph2024 = new PortfolioHolding();
        ph2024.setPortfolioSnapshot(snap2024);
        ph2024.setSecurity(hdfc);
        ph2024.setReportedWeight(new BigDecimal("0.548200"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(2L))
            .thenReturn(List.of(ph2024));

        // Holdings for June 2026 (77 holdings, with Cash security 9870 duplicated across 2 rows)
        Security cashSec = new Security();
        cashSec.setId(9870L);
        cashSec.setCanonicalName("Cash & Cash Equivalents");
        cashSec.setSector("Cash & Cash Equivalents");
        cashSec.setAssetClass("Cash");

        PortfolioHolding cashRow1 = new PortfolioHolding();
        cashRow1.setPortfolioSnapshot(snap2026June);
        cashRow1.setSecurity(cashSec);
        cashRow1.setReportedWeight(new BigDecimal("0.031800")); // TREPS

        PortfolioHolding cashRow2 = new PortfolioHolding();
        cashRow2.setPortfolioSnapshot(snap2026June);
        cashRow2.setSecurity(cashSec);
        cashRow2.setReportedWeight(new BigDecimal("0.000700")); // Net Receivables

        Security reliance = new Security();
        reliance.setId(202L);
        reliance.setCanonicalName("Reliance Industries Ltd.");
        reliance.setSector("Oil & Gas");
        reliance.setAssetClass("Equity");

        PortfolioHolding relRow = new PortfolioHolding();
        relRow.setPortfolioSnapshot(snap2026June);
        relRow.setSecurity(reliance);
        relRow.setReportedWeight(new BigDecimal("0.967500"));

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1943L))
            .thenReturn(List.of(relRow, cashRow1, cashRow2));

        PortfolioConstructionRiskResponseDto response = constructionRiskService.calculateConstructionRisk(1L, LocalDate.of(2026, 6, 30), cutoff);

        assertThat(response.status()).isEqualTo("SUCCESS");
        var dataRel = response.dataReliability();
        assertThat(dataRel.totalHoldingsDisclosed()).isEqualTo(3);
        assertThat(dataRel.uniqueSecuritiesCount()).isEqualTo(2);
        assertThat(dataRel.duplicateHoldingsCount()).isEqualTo(1);
        assertThat(dataRel.duplicateHoldings()).hasSize(1);
        assertThat(dataRel.duplicateHoldings().get(0).securityId()).isEqualTo(9870L);

        // Comparability Matrix: Jan 2024 vs Jun 2026
        assertThat(response.comparabilityMatrix()).hasSize(1);
        var pair = response.comparabilityMatrix().get(0);
        assertThat(pair.earlierDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        assertThat(pair.laterDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(pair.intervalDays()).isGreaterThan(800L);
        assertThat(pair.isDataGapExceeded()).isTrue();
        assertThat(pair.comparabilityStatus()).isEqualTo("NON_COMPARABLE");
        assertThat(pair.reasonCodes()).contains("DISCLOSURE_SCOPE_MISMATCH");
    }

    @Test
    @DisplayName("Point-in-time cutoff excludes future snapshots deterministically")
    void shouldStrictlyEnforcePointInTimeKnowledgeCutoff() {
        // Knowledge cutoff is set before June 2026 availability time (e.g. as of 2024)
        OffsetDateTime pastCutoff = OffsetDateTime.of(2024, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, pastCutoff))
            .thenReturn(List.of(snap2024));

        Security hdfc = new Security();
        hdfc.setId(201L);
        hdfc.setCanonicalName("HDFC Bank Ltd.");
        hdfc.setSector("Financial Services");
        hdfc.setAssetClass("Equity");

        PortfolioHolding ph = new PortfolioHolding();
        ph.setPortfolioSnapshot(snap2024);
        ph.setSecurity(hdfc);
        ph.setReportedWeight(new BigDecimal("0.548200"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(2L))
            .thenReturn(List.of(ph));

        PortfolioConstructionRiskResponseDto response = constructionRiskService.calculateConstructionRisk(1L, LocalDate.of(2024, 1, 31), pastCutoff);

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.dataReliability().snapshotDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        assertThat(response.snapshotHistory()).hasSize(1);
        assertThat(response.comparabilityMatrix()).isEmpty(); // Only 1 snapshot eligible, no historical pairs
    }
}
