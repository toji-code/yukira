package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.PairwiseCorrelationDto;
import com.yukira.backend.dto.portfolio.PortfolioOverlapResponseDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.PortfolioIntelligenceService;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioIntelligenceServiceTest {

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

    private SchemeOption fundAOption;
    private SchemeOption fundBOption;

    @BeforeEach
    void setUp() {
        Scheme schemeA = new Scheme();
        schemeA.setName("HDFC Flexi Cap Fund");
        SchemePlan planA = new SchemePlan();
        planA.setScheme(schemeA);
        planA.setPlanType("DIRECT");
        fundAOption = new SchemeOption();
        fundAOption.setId(1L);
        fundAOption.setPlan(planA);

        Scheme schemeB = new Scheme();
        schemeB.setName("Parag Parikh Flexi Cap Fund");
        SchemePlan planB = new SchemePlan();
        planB.setScheme(schemeB);
        planB.setPlanType("DIRECT");
        fundBOption = new SchemeOption();
        fundBOption.setId(10262L);
        fundBOption.setPlan(planB);

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundAOption));
        when(schemeOptionRepository.findById(10262L)).thenReturn(Optional.of(fundBOption));
    }

    @Test
    @DisplayName("TC-INT-01: Pairwise Correlation calculates deterministic Pearson correlation for aligned returns")
    void testPairwiseCorrelationAlignedReturns() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-10-01T23:59:59+05:30");
        LocalDate baseDate = LocalDate.parse("2025-01-01");

        // Generate 300 daily NAV observations with positive co-movement
        List<NavObservation> obsA = new ArrayList<>();
        List<NavObservation> obsB = new ArrayList<>();
        double navA = 100.0;
        double navB = 200.0;

        for (int i = 0; i < 300; i++) {
            LocalDate d = baseDate.plusDays(i);
            double ret = (i % 2 == 0) ? 0.01 : -0.005;
            navA = navA * (1.0 + ret);
            navB = navB * (1.0 + ret * 0.8); // Highly correlated

            NavObservation oA = new NavObservation();
            oA.setEffectiveDate(d);
            oA.setNavValue(BigDecimal.valueOf(navA));
            obsA.add(oA);

            NavObservation oB = new NavObservation();
            oB.setEffectiveDate(d);
            oB.setNavValue(BigDecimal.valueOf(navB));
            obsB.add(oB);
        }

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any()))
            .thenReturn(obsA);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(10262L), any(), any()))
            .thenReturn(obsB);

        PairwiseCorrelationDto result = intelligenceService.calculatePairwiseCorrelation(1L, 10262L, cutoff);

        assertThat(result).isNotNull();
        assertThat(result.calculationStatus()).isEqualTo("CALCULATED");
        assertThat(result.comparabilityStatus()).isEqualTo("COMPARABLE");
        assertThat(result.pairedObservationsCount()).isEqualTo(299);
        assertThat(result.correlation()).isNotNull();
        assertThat(result.correlation().doubleValue()).isCloseTo(1.0, within(0.01));
        assertThat(result.limitations()).anyMatch(l -> l.contains("Candidate methodology"));
        assertThat(result.interpretation()).contains("High co-movement");
    }

    @Test
    @DisplayName("TC-INT-02: Pairwise Correlation handles sample size < 30 with INSUFFICIENT_DATA")
    void testPairwiseCorrelationInsufficientSample() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-10-01T23:59:59+05:30");
        LocalDate baseDate = LocalDate.parse("2026-09-01");

        List<NavObservation> obsA = new ArrayList<>();
        List<NavObservation> obsB = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            LocalDate d = baseDate.plusDays(i);
            NavObservation oA = new NavObservation();
            oA.setEffectiveDate(d);
            oA.setNavValue(BigDecimal.valueOf(100.0 + i));
            obsA.add(oA);

            NavObservation oB = new NavObservation();
            oB.setEffectiveDate(d);
            oB.setNavValue(BigDecimal.valueOf(200.0 + i));
            obsB.add(oB);
        }

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any()))
            .thenReturn(obsA);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(10262L), any(), any()))
            .thenReturn(obsB);

        PairwiseCorrelationDto result = intelligenceService.calculatePairwiseCorrelation(1L, 10262L, cutoff);

        assertThat(result.calculationStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.comparabilityStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.correlation()).isNull();
        assertThat(result.limitations()).anyMatch(l -> l.contains("Fewer than 30"));
    }

    @Test
    @DisplayName("TC-INT-03: Pairwise Correlation returns INSUFFICIENT_DATA when one fund has missing NAVs")
    void testPairwiseCorrelationMissingNav() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-10-01T23:59:59+05:30");

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any()))
            .thenReturn(List.of(new NavObservation()));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(10262L), any(), any()))
            .thenReturn(Collections.emptyList());

        PairwiseCorrelationDto result = intelligenceService.calculatePairwiseCorrelation(1L, 10262L, cutoff);

        assertThat(result.calculationStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.correlation()).isNull();
        assertThat(result.limitations()).anyMatch(l -> l.contains("Insufficient historical NAV observations"));
    }

    @Test
    @DisplayName("TC-INT-04: Pairwise Overlap handles missing snapshot as UNAVAILABLE without zeroing")
    void testPairwiseOverlapMissingSnapshot() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-10-01T23:59:59+05:30");

        when(snapshotRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(eq(1L), any()))
            .thenReturn(Optional.empty());
        when(snapshotRepository.findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(eq(10262L), any()))
            .thenReturn(Optional.of(new PortfolioSnapshot()));

        PortfolioOverlapResponseDto result = intelligenceService.calculatePairwiseOverlap(1L, 10262L, cutoff);

        assertThat(result.qualityState()).isEqualTo("UNAVAILABLE");
        assertThat(result.limitations()).anyMatch(l -> l.contains("No holdings snapshot available for fund A"));
    }
}
