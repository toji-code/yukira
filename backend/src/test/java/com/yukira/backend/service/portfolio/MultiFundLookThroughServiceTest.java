package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.lookthrough.*;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class MultiFundLookThroughServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioHoldingRepository holdingRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private SecurityMarketCapClassificationRepository classificationRepository;

    @Mock
    private InvestorTargetAllocationRepository targetAllocationRepository;

    @Mock
    private InvestorRepository investorRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    private MultiFundLookThroughService service;

    private SchemeOption fundA;
    private SchemeOption fundB;
    private SchemeOption candidateFundC;

    private Security secHdfcBank;
    private Security secInfosys;
    private Security secTcs;
    private Security secZomato;
    private Security secCash;

    @BeforeEach
    void setUp() {
        service = new MultiFundLookThroughService(
            schemeOptionRepository,
            snapshotRepository,
            holdingRepository,
            securityIdentifierRepository,
            classificationRepository,
            targetAllocationRepository,
            investorRepository,
            navObservationRepository,
            benchmarkRepository,
            benchmarkObservationRepository
        );

        // Setup Funds
        Scheme schemeA = new Scheme();
        schemeA.setId(101L);
        schemeA.setName("Bluechip Growth Fund");
        SchemePlan planA = new SchemePlan();
        planA.setScheme(schemeA);
        planA.setPlanType("DIRECT");
        fundA = new SchemeOption();
        fundA.setId(1L);
        fundA.setPlan(planA);
        fundA.setAmfiCode("100001");
        fundA.setIsin("INF100000001");

        Scheme schemeB = new Scheme();
        schemeB.setId(102L);
        schemeB.setName("Dynamic Equity Fund");
        SchemePlan planB = new SchemePlan();
        planB.setScheme(schemeB);
        planB.setPlanType("DIRECT");
        fundB = new SchemeOption();
        fundB.setId(2L);
        fundB.setPlan(planB);
        fundB.setAmfiCode("100002");
        fundB.setIsin("INF100000002");

        Scheme schemeC = new Scheme();
        schemeC.setId(103L);
        schemeC.setName("Flexi Cap Candidate Fund");
        SchemePlan planC = new SchemePlan();
        planC.setScheme(schemeC);
        planC.setPlanType("DIRECT");
        candidateFundC = new SchemeOption();
        candidateFundC.setId(3L);
        candidateFundC.setPlan(planC);
        candidateFundC.setAmfiCode("100003");
        candidateFundC.setIsin("INF100000003");

        // Setup Securities
        secHdfcBank = new Security();
        secHdfcBank.setId(10L);
        secHdfcBank.setCanonicalName("HDFC Bank Ltd");
        secHdfcBank.setSector("Banks");
        secHdfcBank.setAssetClass("Equity");

        secInfosys = new Security();
        secInfosys.setId(11L);
        secInfosys.setCanonicalName("Infosys Ltd");
        secInfosys.setSector("Information Technology");
        secInfosys.setAssetClass("Equity");

        secTcs = new Security();
        secTcs.setId(12L);
        secTcs.setCanonicalName("Tata Consultancy Services Ltd");
        secTcs.setSector("Information Technology");
        secTcs.setAssetClass("Equity");

        secZomato = new Security();
        secZomato.setId(13L);
        secZomato.setCanonicalName("Zomato Ltd");
        secZomato.setSector("Consumer Services");
        secZomato.setAssetClass("Equity");

        secCash = new Security();
        secCash.setId(14L);
        secCash.setCanonicalName("TREPS / Net Current Assets");
        secCash.setSector("Cash & Liquid");
        secCash.setAssetClass("Cash");

        // Mock ISIN lookups
        lenient().when(securityIdentifierRepository.findTopBySecurityIdAndIdType(10L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(secHdfcBank, "ISIN", "INE040A01034", LocalDate.of(2020, 1, 1))));
        lenient().when(securityIdentifierRepository.findTopBySecurityIdAndIdType(11L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(secInfosys, "ISIN", "INE009A01021", LocalDate.of(2020, 1, 1))));
        lenient().when(securityIdentifierRepository.findTopBySecurityIdAndIdType(12L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(secTcs, "ISIN", "INE467B01029", LocalDate.of(2020, 1, 1))));
        lenient().when(securityIdentifierRepository.findTopBySecurityIdAndIdType(13L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(secZomato, "ISIN", "INE758T01015", LocalDate.of(2020, 1, 1))));
        lenient().when(securityIdentifierRepository.findTopBySecurityIdAndIdType(14L, "ISIN"))
            .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Validation: Rejects negative allocation weights with explicit exception")
    void shouldRejectNegativeAllocation() {
        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(2L, new BigDecimal("-10.00"))
            ),
            LocalDate.of(2026, 9, 30),
            OffsetDateTime.now(),
            "RAW"
        );

        assertThatThrownBy(() -> service.calculateMultiFundLookThrough(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Validation: Rejects total allocation weight <= 0")
    void shouldRejectZeroTotalAllocation() {
        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, BigDecimal.ZERO),
                new ResearchPortfolioFundItemDto(2L, BigDecimal.ZERO)
            ),
            LocalDate.of(2026, 9, 30),
            OffsetDateTime.now(),
            "RAW"
        );

        assertThatThrownBy(() -> service.calculateMultiFundLookThrough(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("Validation: Marks partial allocation when weights do not sum to 100%")
    void shouldMarkPartialAllocation() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(Collections.emptyList());

        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(new ResearchPortfolioFundItemDto(1L, new BigDecimal("45.00"))),
            LocalDate.of(2026, 9, 30),
            OffsetDateTime.now(),
            "RAW"
        );

        MultiFundLookThroughResponseDto result = service.calculateMultiFundLookThrough(req);

        assertThat(result.inputTotalAllocation()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(result.isFullyAllocated()).isFalse();
    }

    @Test
    @DisplayName("Arithmetic: Accurately computes multi-fund look-through security & sector weights")
    void shouldComputeMultiFundLookThroughArithmetic() {
        LocalDate obsDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.now();

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(schemeOptionRepository.findById(2L)).thenReturn(Optional.of(fundB));

        // Fund A: 60% of portfolio. Holdings: HDFC Bank 50% (0.50), Infosys 40% (0.40), Cash 10% (0.10)
        PortfolioSnapshot snapA = new PortfolioSnapshot();
        snapA.setId(1001L);
        snapA.setPortfolioDate(obsDate);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snapA));

        PortfolioHolding hA1 = new PortfolioHolding();
        hA1.setSecurity(secHdfcBank);
        hA1.setReportedWeight(new BigDecimal("0.50"));
        PortfolioHolding hA2 = new PortfolioHolding();
        hA2.setSecurity(secInfosys);
        hA2.setReportedWeight(new BigDecimal("0.40"));
        PortfolioHolding hA3 = new PortfolioHolding();
        hA3.setSecurity(secCash);
        hA3.setReportedWeight(new BigDecimal("0.10"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1001L))
            .thenReturn(List.of(hA1, hA2, hA3));

        // Fund B: 40% of portfolio. Holdings: Infosys 60% (0.60), TCS 30% (0.30), Cash 10% (0.10)
        PortfolioSnapshot snapB = new PortfolioSnapshot();
        snapB.setId(1002L);
        snapB.setPortfolioDate(obsDate);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(2L), any()))
            .thenReturn(List.of(snapB));

        PortfolioHolding hB1 = new PortfolioHolding();
        hB1.setSecurity(secInfosys);
        hB1.setReportedWeight(new BigDecimal("0.60"));
        PortfolioHolding hB2 = new PortfolioHolding();
        hB2.setSecurity(secTcs);
        hB2.setReportedWeight(new BigDecimal("0.30"));
        PortfolioHolding hB3 = new PortfolioHolding();
        hB3.setSecurity(secCash);
        hB3.setReportedWeight(new BigDecimal("0.10"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1002L))
            .thenReturn(List.of(hB1, hB2, hB3));

        // Request: 60% Fund A + 40% Fund B
        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
            ),
            obsDate, cutoff, "RAW"
        );

        MultiFundLookThroughResponseDto response = service.calculateMultiFundLookThrough(req);

        assertThat(response.qualityState()).isEqualTo("COMPUTED_VALIDATED_INPUTS");
        assertThat(response.isFullyAllocated()).isTrue();
        assertThat(response.constituentFunds()).hasSize(2);

        // Verification of Consolidated Security Weights:
        // Infosys: 60% * 40% + 40% * 60% = 24% + 24% = 48.00% (held in 2 funds)
        // HDFC Bank: 60% * 50% + 40% * 0% = 30.00% (held in 1 fund)
        // TCS: 60% * 0% + 40% * 30% = 12.00% (held in 1 fund)
        // Cash: 60% * 10% + 40% * 10% = 10.00%

        List<ConsolidatedSecurityExposureDto> securities = response.consolidatedSecurities();
        assertThat(securities).isNotEmpty();

        ConsolidatedSecurityExposureDto topSec = securities.get(0);
        assertThat(topSec.canonicalName()).isEqualTo("Infosys Ltd");
        assertThat(topSec.consolidatedWeightPct()).isEqualByComparingTo(new BigDecimal("48.00"));
        assertThat(topSec.fundCount()).isEqualTo(2);

        ConsolidatedSecurityExposureDto sec2 = securities.get(1);
        assertThat(sec2.canonicalName()).isEqualTo("HDFC Bank Ltd");
        assertThat(sec2.consolidatedWeightPct()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(sec2.fundCount()).isEqualTo(1);

        ConsolidatedSecurityExposureDto sec3 = securities.get(2);
        assertThat(sec3.canonicalName()).isEqualTo("Tata Consultancy Services Ltd");
        assertThat(sec3.consolidatedWeightPct()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(sec3.fundCount()).isEqualTo(1);

        // Verification of Consolidated Sector Weights:
        // Information Technology: Infosys (48%) + TCS (12%) = 60.00%
        // Financial Services: HDFC Bank = 30.00%
        // Cash & Liquid Assets = 10.00%
        List<ConsolidatedSectorExposureDto> sectors = response.consolidatedSectors();
        assertThat(sectors).hasSize(3);

        assertThat(sectors.get(0).macroSector()).isEqualTo("Information Technology");
        assertThat(sectors.get(0).consolidatedWeightPct()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(sectors.get(0).classifiedSecuritiesCount()).isEqualTo(2);

        assertThat(sectors.get(1).macroSector()).isEqualTo("Financial Services");
        assertThat(sectors.get(1).consolidatedWeightPct()).isEqualByComparingTo(new BigDecimal("30.00"));

        // Verification of Pairwise Overlap between Fund A and Fund B:
        // Shared security: Infosys (Fund A = 40%, Fund B = 60% -> min = 40.00%)
        // Cash is not an equity ISIN or ignored in pairwise overlap comparison
        List<PairwiseOverlapResultDto> overlaps = response.pairwiseOverlapMatrix();
        assertThat(overlaps).hasSize(1);
        PairwiseOverlapResultDto overlapAB = overlaps.get(0);
        assertThat(overlapAB.overlapWeightPct()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(overlapAB.comparabilityState()).isEqualTo("SYNCHRONIZED");

        // Concentration Metrics:
        // Top 5 weight: 48 + 30 + 12 + 10 = 100.00%
        // Shared security weight: Infosys = 48.00%
        // HHI: 48^2 + 30^2 + 12^2 + 10^2 = 2304 + 900 + 144 + 100 = 3448.00
        PortfolioConcentrationMetricsDto conc = response.concentrationMetrics();
        assertThat(conc.top5WeightPct()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(conc.sharedSecuritiesWeightPct()).isEqualByComparingTo(new BigDecimal("48.00"));
        assertThat(conc.sharedSecuritiesCount()).isEqualTo(1);
        assertThat(conc.herfindahlHirschmanIndex()).isEqualByComparingTo(new BigDecimal("3448.00"));
    }

    @Test
    @DisplayName("Temporal Mismatch: Flags NON_COMPARABLE_OBSERVATION_DATES when observation dates span > 45 days")
    void shouldFlagTemporalMismatchWhenDatesSpanOver45Days() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(schemeOptionRepository.findById(2L)).thenReturn(Optional.of(fundB));

        // Fund A snapshot: 2026-09-30
        PortfolioSnapshot snapA = new PortfolioSnapshot();
        snapA.setId(1001L);
        snapA.setPortfolioDate(LocalDate.of(2026, 9, 30));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snapA));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1001L))
            .thenReturn(Collections.emptyList());

        // Fund B snapshot: 2026-06-30 (92 days prior)
        PortfolioSnapshot snapB = new PortfolioSnapshot();
        snapB.setId(1002L);
        snapB.setPortfolioDate(LocalDate.of(2026, 6, 30));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(2L), any()))
            .thenReturn(List.of(snapB));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1002L))
            .thenReturn(Collections.emptyList());

        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("50.00")),
                new ResearchPortfolioFundItemDto(2L, new BigDecimal("50.00"))
            ),
            LocalDate.of(2026, 9, 30),
            OffsetDateTime.now(),
            "RAW"
        );

        MultiFundLookThroughResponseDto response = service.calculateMultiFundLookThrough(req);

        assertThat(response.qualityState()).isEqualTo("NON_COMPARABLE_OBSERVATION_DATES");
        assertThat(response.limitations()).anyMatch(l -> l.contains("Temporal mismatch") && l.contains("92 days"));
    }

    @Test
    @DisplayName("Candidate Simulation: Correctly applies proportional dilution and computes before/after deltas")
    void shouldSimulateCandidateFundAdditionWithProportionalDilution() {
        LocalDate obsDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.now();

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(schemeOptionRepository.findById(2L)).thenReturn(Optional.of(fundB));
        when(schemeOptionRepository.findById(3L)).thenReturn(Optional.of(candidateFundC));

        // Fund A: HDFC Bank 60%, Infosys 40%
        PortfolioSnapshot snapA = new PortfolioSnapshot();
        snapA.setId(1001L);
        snapA.setPortfolioDate(obsDate);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snapA));

        PortfolioHolding hA1 = new PortfolioHolding();
        hA1.setSecurity(secHdfcBank);
        hA1.setReportedWeight(new BigDecimal("0.60"));
        PortfolioHolding hA2 = new PortfolioHolding();
        hA2.setSecurity(secInfosys);
        hA2.setReportedWeight(new BigDecimal("0.40"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1001L))
            .thenReturn(List.of(hA1, hA2));

        // Fund B: Infosys 70%, TCS 30%
        PortfolioSnapshot snapB = new PortfolioSnapshot();
        snapB.setId(1002L);
        snapB.setPortfolioDate(obsDate);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(2L), any()))
            .thenReturn(List.of(snapB));

        PortfolioHolding hB1 = new PortfolioHolding();
        hB1.setSecurity(secInfosys);
        hB1.setReportedWeight(new BigDecimal("0.70"));
        PortfolioHolding hB2 = new PortfolioHolding();
        hB2.setSecurity(secTcs);
        hB2.setReportedWeight(new BigDecimal("0.30"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1002L))
            .thenReturn(List.of(hB1, hB2));

        // Candidate Fund C: Zomato 50% (new security), HDFC Bank 50% (augmented position)
        PortfolioSnapshot snapC = new PortfolioSnapshot();
        snapC.setId(1003L);
        snapC.setPortfolioDate(obsDate);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(3L), any()))
            .thenReturn(List.of(snapC));

        PortfolioHolding hC1 = new PortfolioHolding();
        hC1.setSecurity(secZomato);
        hC1.setReportedWeight(new BigDecimal("0.50"));
        PortfolioHolding hC2 = new PortfolioHolding();
        hC2.setSecurity(secHdfcBank);
        hC2.setReportedWeight(new BigDecimal("0.50"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1003L))
            .thenReturn(List.of(hC1, hC2));

        // Baseline: 50% Fund A + 50% Fund B
        // Propose adding Candidate Fund C at 20.00%
        CandidateSimulationRequestDto simReq = new CandidateSimulationRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("50.00")),
                new ResearchPortfolioFundItemDto(2L, new BigDecimal("50.00"))
            ),
            3L,
            new BigDecimal("20.00"),
            "PROPORTIONAL",
            obsDate,
            cutoff
        );

        CandidateSimulationResponseDto result = service.simulateCandidateFund(simReq);

        assertThat(result.candidateFundName()).isEqualTo("Flexi Cap Candidate Fund");
        assertThat(result.proposedAllocationPct()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(result.fundingMethod()).isEqualTo("PROPORTIONAL");

        // Baseline portfolio:
        // HDFC Bank = 50% * 60% = 30.00%
        // Infosys = 50% * 40% + 50% * 70% = 20% + 35% = 55.00%
        // TCS = 50% * 30% = 15.00%
        // Unique = 3
        assertThat(result.baselinePortfolio().concentrationMetrics().uniqueSecuritiesCount()).isEqualTo(3);

        // After adding 20% Candidate C (existing funds scaled by 80% -> Fund A=40%, Fund B=40%, Candidate C=20%):
        // HDFC Bank = 40% * 60% + 20% * 50% = 24% + 10% = 34.00% (augmented by +4.00%)
        // Infosys = 40% * 40% + 40% * 70% = 16% + 28% = 44.00%
        // TCS = 40% * 30% = 12.00%
        // Zomato = 20% * 50% = 10.00% (NEW security!)
        // Unique = 4 (+1 new)
        assertThat(result.simulatedPortfolio().concentrationMetrics().uniqueSecuritiesCount()).isEqualTo(4);
        assertThat(result.deltaUniqueSecuritiesCount()).isEqualTo(1);
        assertThat(result.newSecuritiesIntroducedCount()).isEqualTo(1);
        assertThat(result.topNewSecurities()).anyMatch(s -> "Zomato Ltd".equals(s.canonicalName()));

        assertThat(result.augmentedSecuritiesCount()).isEqualTo(1);
        assertThat(result.topAugmentedSecurities()).anyMatch(a ->
            "HDFC Bank Ltd".equals(a.securityName())
            && a.baselineWeightPct().compareTo(new BigDecimal("30.00")) == 0
            && a.simulatedWeightPct().compareTo(new BigDecimal("34.00")) == 0
        );

        assertThat(result.analyticalObservations()).isNotEmpty();
    }

    @Test
    @DisplayName("Sector Top Securities: Verifies top contributing securities are aggregated per macro-sector")
    void shouldIncludeTopSecuritiesInSectorAggregation() {
        LocalDate obsDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));

        // Fund A has HDFC Bank (60%) and Infosys (40%)
        PortfolioSnapshot snapA = new PortfolioSnapshot();
        snapA.setId(1001L);
        snapA.setPortfolioDate(obsDate);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snapA));

        PortfolioHolding hA1 = new PortfolioHolding();
        hA1.setSecurity(secHdfcBank);
        hA1.setReportedWeight(new BigDecimal("0.60"));
        PortfolioHolding hA2 = new PortfolioHolding();
        hA2.setSecurity(secInfosys);
        hA2.setReportedWeight(new BigDecimal("0.40"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1001L))
            .thenReturn(List.of(hA1, hA2));

        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(new ResearchPortfolioFundItemDto(1L, new BigDecimal("100.00"))),
            obsDate, cutoff, "RAW"
        );

        MultiFundLookThroughResponseDto response = service.calculateMultiFundLookThrough(req);

        assertThat(response.consolidatedSectors()).isNotEmpty();
        ConsolidatedSectorExposureDto finServices = response.consolidatedSectors().stream()
            .filter(s -> "Financial Services".equals(s.macroSector()))
            .findFirst()
            .orElseThrow();

        assertThat(finServices.topSecurities()).isNotEmpty();
        SectorTopSecurityDto topSec = finServices.topSecurities().get(0);
        assertThat(topSec.canonicalName()).isEqualTo("HDFC Bank Ltd");
        assertThat(topSec.isin()).isEqualTo("INE040A01034");
        assertThat(topSec.consolidatedWeightPct()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(topSec.fundCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Risk Decomposition & Factor Gates: Synthesizes daily portfolio returns, verifies diversification benefit and gates")
    void shouldComputePortfolioRiskDecompositionWithDiversificationBenefit() {
        LocalDate obsDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(schemeOptionRepository.findById(2L)).thenReturn(Optional.of(fundB));

        // Mock snapshots for holdings
        PortfolioSnapshot snapA = new PortfolioSnapshot();
        snapA.setId(1001L);
        snapA.setPortfolioDate(obsDate);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snapA));
        PortfolioHolding hA = new PortfolioHolding();
        hA.setSecurity(secHdfcBank);
        hA.setReportedWeight(new BigDecimal("1.00"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1001L)).thenReturn(List.of(hA));

        PortfolioSnapshot snapB = new PortfolioSnapshot();
        snapB.setId(1002L);
        snapB.setPortfolioDate(obsDate);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(2L), any()))
            .thenReturn(List.of(snapB));
        PortfolioHolding hB = new PortfolioHolding();
        hB.setSecurity(secInfosys);
        hB.setReportedWeight(new BigDecimal("1.00"));
        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1002L)).thenReturn(List.of(hB));

        // Mock Benchmark NIFTY_500_TRI
        Benchmark bm = new Benchmark();
        bm.setId(500L);
        bm.setCode("NIFTY_500_TRI");
        bm.setName("Nifty 500 Total Returns Index");
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(bm));

        // Generate 35 synchronized daily observations
        List<NavObservation> navsA = new ArrayList<>();
        List<NavObservation> navsB = new ArrayList<>();
        List<BenchmarkObservation> bmObs = new ArrayList<>();

        double baseA = 100.0;
        double baseB = 100.0;
        double baseBm = 10000.0;

        for (int i = 0; i < 35; i++) {
            LocalDate d = obsDate.minusDays(40 - i);
            // Alternate daily changes to produce imperfect correlation
            double retA = (i % 2 == 0) ? 0.01 : -0.005;
            double retB = (i % 3 == 0) ? 0.012 : -0.004;
            double retBm = 0.002;

            baseA *= (1.0 + retA);
            baseB *= (1.0 + retB);
            baseBm *= (1.0 + retBm);

            NavObservation noA = new NavObservation();
            noA.setEffectiveDate(d);
            noA.setNavValue(BigDecimal.valueOf(baseA).setScale(4, RoundingMode.HALF_UP));
            navsA.add(noA);

            NavObservation noB = new NavObservation();
            noB.setEffectiveDate(d);
            noB.setNavValue(BigDecimal.valueOf(baseB).setScale(4, RoundingMode.HALF_UP));
            navsB.add(noB);

            BenchmarkObservation bo = new BenchmarkObservation();
            bo.setEffectiveDate(d);
            bo.setIndexLevel(BigDecimal.valueOf(baseBm).setScale(4, RoundingMode.HALF_UP));
            bmObs.add(bo);
        }

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any()))
            .thenReturn(navsA);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(2L), any(), any()))
            .thenReturn(navsB);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(500L), any(), any()))
            .thenReturn(bmObs);

        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("50.00")),
                new ResearchPortfolioFundItemDto(2L, new BigDecimal("50.00"))
            ),
            obsDate, cutoff, "RAW"
        );

        MultiFundLookThroughResponseDto response = service.calculateMultiFundLookThrough(req);

        assertThat(response.riskDecomposition()).isNotNull();
        PortfolioRiskDecompositionDto risk = response.riskDecomposition();
        assertThat(risk.evaluationStatus()).isEqualTo("COMPUTED_VALIDATED_INPUTS");
        assertThat(risk.portfolioRealizedVolatilityPct()).isGreaterThan(BigDecimal.ZERO);
        assertThat(risk.weightedAverageFundVolatilityPct()).isGreaterThan(BigDecimal.ZERO);

        // Mathematical diversification benefit: bar(sigma) - sigma_port >= 0 due to imperfect correlation
        assertThat(risk.diversificationBenefitPct()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(risk.constituentRiskProfiles()).hasSize(2);
        assertThat(risk.pairwiseCorrelations()).hasSize(1);
        assertThat(risk.pairwiseCorrelations().get(0).correlationCoefficient()).isLessThan(BigDecimal.ONE);

        // Factor Gates Verification
        assertThat(risk.factorExposures()).hasSize(6);
        assertThat(risk.factorExposures()).anyMatch(f ->
            "SIZE".equals(f.factorCode()) && "VALIDATED_PROXY".equals(f.status())
        );
        assertThat(risk.factorExposures()).anyMatch(f ->
            "MARKET_BETA".equals(f.factorCode()) && "EMPIRICALLY_COMPUTED".equals(f.status())
        );
        assertThat(risk.factorExposures()).anyMatch(f ->
            "LOW_VOLATILITY".equals(f.factorCode()) && "EMPIRICALLY_COMPUTED".equals(f.status())
        );
        assertThat(risk.factorExposures()).anyMatch(f ->
            "VALUE".equals(f.factorCode()) && "FACTOR_INPUTS_UNAVAILABLE".equals(f.status())
        );
        assertThat(risk.factorExposures()).anyMatch(f ->
            "QUALITY".equals(f.factorCode()) && "FACTOR_INPUTS_UNAVAILABLE".equals(f.status())
        );
        assertThat(risk.factorExposures()).anyMatch(f ->
            "MOMENTUM".equals(f.factorCode()) && "FACTOR_INPUTS_UNAVAILABLE".equals(f.status())
        );
    }
}
