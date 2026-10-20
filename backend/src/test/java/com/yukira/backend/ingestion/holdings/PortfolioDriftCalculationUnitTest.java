package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.PortfolioDriftResponseDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.PortfolioIntelligenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioDriftCalculationUnitTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioHoldingRepository holdingRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @InjectMocks
    private PortfolioIntelligenceService intelligenceService;

    private SchemeOption testOption;
    private SchemeOption otherOption;
    private PortfolioSnapshot snapEarlier;
    private PortfolioSnapshot snapLater;

    @BeforeEach
    void setUp() {
        Scheme scheme = new Scheme();
        scheme.setId(100L);
        scheme.setName("HDFC Flexi Cap Fund");

        SchemePlan plan = new SchemePlan();
        plan.setId(200L);
        plan.setScheme(scheme);

        testOption = new SchemeOption();
        testOption.setId(1L);
        testOption.setAmfiCode("118955");
        testOption.setPlan(plan);

        otherOption = new SchemeOption();
        otherOption.setId(999L);
        otherOption.setAmfiCode("999999");
        otherOption.setPlan(plan);

        snapEarlier = new PortfolioSnapshot();
        snapEarlier.setId(10L);
        snapEarlier.setSchemeOption(testOption);
        snapEarlier.setPortfolioDate(LocalDate.of(2024, 7, 31));
        snapEarlier.setAvailabilityTime(OffsetDateTime.of(2024, 8, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snapEarlier.setSumReportedWeights(new BigDecimal("0.950000"));

        snapLater = new PortfolioSnapshot();
        snapLater.setId(20L);
        snapLater.setSchemeOption(testOption);
        snapLater.setPortfolioDate(LocalDate.of(2024, 8, 31));
        snapLater.setAvailabilityTime(OffsetDateTime.of(2024, 9, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snapLater.setSumReportedWeights(new BigDecimal("0.970000"));
    }

    @Test
    @DisplayName("Calculates deterministic positive, negative, added, exited, and unchanged position deltas")
    void testPositionLevelDriftCalculations() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapEarlier));
        when(snapshotRepository.findById(20L)).thenReturn(Optional.of(snapLater));

        // Securities
        Security secHdfc = createSecurity(1L, "HDFC Bank Ltd.", "INE040A01034", "Financial Services", "Equity");
        Security secInfy = createSecurity(2L, "Infosys Ltd.", "INE009A01021", "Information Technology", "Equity");
        Security secTcs = createSecurity(3L, "Tata Consultancy Services", "INE467B01029", "Information Technology", "Equity");
        Security secRil = createSecurity(4L, "Reliance Industries Ltd.", "INE002A01018", "Energy", "Equity");
        Security secItc = createSecurity(5L, "ITC Ltd.", "INE154A01025", "Consumer Staples", "Equity");

        // Earlier snapshot: HDFC (10%), INFY (6%), RIL (5%), ITC (4%)
        List<PortfolioHolding> holdingsEarlier = List.of(
            createHolding(101L, snapEarlier, secHdfc, new BigDecimal("0.100000"), 1),
            createHolding(102L, snapEarlier, secInfy, new BigDecimal("0.060000"), 2),
            createHolding(103L, snapEarlier, secRil, new BigDecimal("0.050000"), 3),
            createHolding(104L, snapEarlier, secItc, new BigDecimal("0.040000"), 4)
        );

        // Later snapshot: HDFC (12.5% - increase), INFY (4.5% - decrease), TCS (3% - addition), ITC (4% - unchanged), RIL absent (exit)
        List<PortfolioHolding> holdingsLater = List.of(
            createHolding(201L, snapLater, secHdfc, new BigDecimal("0.125000"), 1),
            createHolding(202L, snapLater, secInfy, new BigDecimal("0.045000"), 2),
            createHolding(203L, snapLater, secItc, new BigDecimal("0.040000"), 3),
            createHolding(204L, snapLater, secTcs, new BigDecimal("0.030000"), 4)
        );

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(10L)).thenReturn(holdingsEarlier);
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(20L)).thenReturn(holdingsLater);

        PortfolioDriftResponseDto result = intelligenceService.calculatePortfolioDrift(1L, 10L, 20L, null);

        assertNotNull(result);
        assertEquals("COMPARABLE", result.status());
        assertTrue(result.isComparable());

        // Check classification counts
        var summary = result.classificationSummary();
        assertNotNull(summary);
        assertEquals(5, summary.totalComparedPositions());
        assertEquals(1, summary.newlyReportedCount(), "TCS is newly reported");
        assertEquals(1, summary.noLongerReportedCount(), "RIL is no longer reported");
        assertEquals(1, summary.weightIncreasedCount(), "HDFC Bank increased");
        assertEquals(1, summary.weightDecreasedCount(), "Infosys decreased");
        assertEquals(1, summary.unchangedCount(), "ITC is unchanged");

        // Verify ranked topIncreases: TCS (+3.00%) > HDFC Bank (+2.50%)
        assertEquals(2, result.topIncreases().size());
        assertEquals("INE467B01029", result.topIncreases().get(0).isin());
        assertEquals(new BigDecimal("3.00"), result.topIncreases().get(0).weightDeltaPct());
        assertEquals("INE040A01034", result.topIncreases().get(1).isin());
        assertEquals(new BigDecimal("2.50"), result.topIncreases().get(1).weightDeltaPct());

        // Verify ranked topDecreases
        assertFalse(result.topDecreases().isEmpty());
        // Most negative delta first: RIL was 5% -> -5.00%
        assertEquals("INE002A01018", result.topDecreases().get(0).isin());
        assertEquals(new BigDecimal("-5.00"), result.topDecreases().get(0).weightDeltaPct());

        // Verify additions
        assertEquals(1, result.additions().size());
        assertEquals("INE467B01029", result.additions().get(0).isin());
        assertEquals(new BigDecimal("3.00"), result.additions().get(0).laterWeightPct());

        // Verify exits
        assertEquals(1, result.exits().size());
        assertEquals("INE002A01018", result.exits().get(0).isin());
        assertEquals(new BigDecimal("5.00"), result.exits().get(0).earlierWeightPct());

        // Verify sector drift
        assertNotNull(result.sectorDrift());
        assertFalse(result.sectorDrift().isEmpty());

        // Investigation prompts
        assertNotNull(result.investigationPrompts());
        assertFalse(result.investigationPrompts().isEmpty());
    }

    @Test
    @DisplayName("Aggregates duplicate securities in same snapshot without dropping weights")
    void testDuplicateSecurityDeduplication() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapEarlier));
        when(snapshotRepository.findById(20L)).thenReturn(Optional.of(snapLater));

        Security secBond = createSecurity(10L, "GOI 7.18% 2033", "IN0020230085", "Sovereign", "Debt");

        // Two lots of the same bond in earlier snapshot: 3% and 2% = total 5%
        List<PortfolioHolding> holdingsEarlier = List.of(
            createHolding(301L, snapEarlier, secBond, new BigDecimal("0.030000"), 1),
            createHolding(302L, snapEarlier, secBond, new BigDecimal("0.020000"), 2)
        );

        // Later snapshot has single lot: 6%
        List<PortfolioHolding> holdingsLater = List.of(
            createHolding(303L, snapLater, secBond, new BigDecimal("0.060000"), 1)
        );

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(10L)).thenReturn(holdingsEarlier);
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(20L)).thenReturn(holdingsLater);

        PortfolioDriftResponseDto result = intelligenceService.calculatePortfolioDrift(1L, 10L, 20L, null);

        assertNotNull(result);
        assertEquals(1, result.holdingChanges().size());
        var item = result.holdingChanges().get(0);
        assertEquals("WEIGHT_INCREASED", item.changeType());
        assertEquals(new BigDecimal("5.00"), item.earlierWeightPct(), "Combined earlier weight must be 5.00%");
        assertEquals(new BigDecimal("6.00"), item.laterWeightPct(), "Later weight must be 6.00%");
        assertEquals(new BigDecimal("1.00"), item.weightDeltaPct(), "Delta must be +1.00%");
    }

    @Test
    @DisplayName("Rejects cross-scheme snapshot comparison with INVALID_SELECTION")
    void testCrossSchemeRejection() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));

        PortfolioSnapshot snapOther = new PortfolioSnapshot();
        snapOther.setId(99L);
        snapOther.setSchemeOption(otherOption);
        snapOther.setPortfolioDate(LocalDate.of(2024, 8, 31));
        snapOther.setAvailabilityTime(OffsetDateTime.now());

        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapEarlier));
        when(snapshotRepository.findById(99L)).thenReturn(Optional.of(snapOther));

        PortfolioDriftResponseDto result = intelligenceService.calculatePortfolioDrift(1L, 10L, 99L, null);

        assertNotNull(result);
        assertEquals("INVALID_SELECTION", result.status());
        assertFalse(result.isComparable());
        assertTrue(result.statusReason().contains("different scheme options"));
    }

    @Test
    @DisplayName("Returns INSUFFICIENT_HISTORY when scheme has fewer than 2 snapshots available")
    void testInsufficientHistoryState() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snapEarlier));

        PortfolioDriftResponseDto result = intelligenceService.calculatePortfolioDrift(1L, null, null, null);

        assertNotNull(result);
        assertEquals("INSUFFICIENT_HISTORY", result.status());
        assertFalse(result.isComparable());
        assertTrue(result.statusReason().contains("requires at least 2 distinct monthly disclosure snapshots"));
        assertTrue(result.holdingChanges().isEmpty());
    }

    @Test
    @DisplayName("Enforces PIT cutoff: rejects snapshots published after knowledge cutoff")
    void testPitKnowledgeCutoffEnforcement() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapEarlier));
        when(snapshotRepository.findById(20L)).thenReturn(Optional.of(snapLater));

        // Cutoff before snapLater availability (which was 2024-09-05)
        OffsetDateTime earlyCutoff = OffsetDateTime.of(2024, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioDriftResponseDto result = intelligenceService.calculatePortfolioDrift(1L, 10L, 20L, earlyCutoff);

        assertNotNull(result);
        assertEquals("UNAVAILABLE", result.status());
        assertFalse(result.isComparable());
        assertTrue(result.statusReason().contains("knowledge cutoff"));
    }

    @Test
    @DisplayName("Identifies unclassified sector exposure and flags PARTIAL_COVERAGE when unclassified exceeds 10%")
    void testUnclassifiedSectorHandling() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(snapshotRepository.findById(10L)).thenReturn(Optional.of(snapEarlier));
        when(snapshotRepository.findById(20L)).thenReturn(Optional.of(snapLater));

        Security secClassified = createSecurity(1L, "HDFC Bank Ltd.", "INE040A01034", "Financial Services", "Equity");
        Security secCash = createSecurity(2L, "TREPS / Reverse Repo", null, "Cash & Equivalents", "Cash");

        // 80% classified, 15% cash/unclassified
        List<PortfolioHolding> holdingsEarlier = List.of(
            createHolding(101L, snapEarlier, secClassified, new BigDecimal("0.800000"), 1),
            createHolding(102L, snapEarlier, secCash, new BigDecimal("0.150000"), 2)
        );

        List<PortfolioHolding> holdingsLater = List.of(
            createHolding(201L, snapLater, secClassified, new BigDecimal("0.820000"), 1),
            createHolding(202L, snapLater, secCash, new BigDecimal("0.140000"), 2)
        );

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(10L)).thenReturn(holdingsEarlier);
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(20L)).thenReturn(holdingsLater);

        PortfolioDriftResponseDto result = intelligenceService.calculatePortfolioDrift(1L, 10L, 20L, null);

        assertNotNull(result);
        assertNotNull(result.sectorCoverage());
        assertEquals("PARTIAL_COVERAGE", result.sectorCoverage().coverageStatus(),
            "Unclassified cash exposure of 15% must trigger PARTIAL_COVERAGE");
        assertTrue(result.sectorCoverage().unclassifiedWeightPctEarlier().compareTo(new BigDecimal("10.00")) > 0);
    }

    private Security createSecurity(Long id, String name, String isin, String sector, String assetClass) {
        Security s = new Security();
        s.setId(id);
        s.setCanonicalName(name);
        s.setSector(sector);
        s.setAssetClass(assetClass);
        s.setInstrumentType("EQUITY_SHARES");

        if (isin != null) {
            SecurityIdentifier si = new SecurityIdentifier(s, "ISIN", isin, LocalDate.of(2000, 1, 1));
            when(securityIdentifierRepository.findTopBySecurityIdAndIdType(id, "ISIN")).thenReturn(Optional.of(si));
        } else {
            when(securityIdentifierRepository.findTopBySecurityIdAndIdType(id, "ISIN")).thenReturn(Optional.empty());
        }

        return s;
    }

    private PortfolioHolding createHolding(Long id, PortfolioSnapshot snap, Security sec, BigDecimal weight, int rank) {
        PortfolioHolding h = new PortfolioHolding();
        h.setId(id);
        h.setPortfolioSnapshot(snap);
        h.setSecurity(sec);
        h.setReportedWeight(weight);
        h.setHoldingRank(rank);
        return h;
    }
}
