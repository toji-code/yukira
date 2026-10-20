package com.yukira.backend.service.portfolio;

import com.yukira.backend.dto.portfolio.drivers.*;
import com.yukira.backend.dto.portfolio.lookthrough.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class PortfolioRiskDriversServiceTest {

    @Mock
    private MultiFundLookThroughService lookThroughService;

    private PortfolioRiskDriversService service;

    private LocalDate asOfDate;
    private OffsetDateTime cutoff;

    @BeforeEach
    void setUp() {
        service = new PortfolioRiskDriversService(lookThroughService);
        asOfDate = LocalDate.of(2024, 3, 28);
        cutoff = OffsetDateTime.of(2024, 3, 28, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
    }

    @Test
    @DisplayName("Empty or null fund request returns UNAVAILABLE status gracefully")
    void shouldReturnUnavailableOnEmptyFunds() {
        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(), asOfDate, cutoff, "RAW"
        );

        PortfolioRiskDriversResponseDto response = service.calculateRiskDrivers(req);

        assertThat(response).isNotNull();
        assertThat(response.evaluationStatus()).isEqualTo("UNAVAILABLE");
        assertThat(response.unavailabilityReason()).contains("No constituent funds provided");
        assertThat(response.underlyingSecurities()).isEmpty();
        assertThat(response.findings()).isEmpty();
    }

    @Test
    @DisplayName("Computes Concentration Summary and triggers deterministic investigation findings")
    void shouldComputeConcentrationAndTriggerDeterministicFindings() {
        // Mock underlying securities: HDFC Bank (8.02%), ICICI Bank (5.75%), Infosys (4.18%)
        List<ConsolidatedSecurityExposureDto> mockSecurities = List.of(
            new ConsolidatedSecurityExposureDto(
                "HDFC Bank Ltd.", "INE040A01034", "Financial Services", "EQUITY", "LARGE_CAP",
                new BigDecimal("8.02"), 2, List.of(), "VERIFIED", "AMFI_SEBI_PROVENANCE"
            ),
            new ConsolidatedSecurityExposureDto(
                "ICICI Bank Ltd.", "INE090A01021", "Financial Services", "EQUITY", "LARGE_CAP",
                new BigDecimal("5.75"), 2, List.of(), "VERIFIED", "AMFI_SEBI_PROVENANCE"
            ),
            new ConsolidatedSecurityExposureDto(
                "Infosys Ltd.", "INE009A01021", "Information Technology", "EQUITY", "LARGE_CAP",
                new BigDecimal("4.18"), 1, List.of(), "VERIFIED", "AMFI_SEBI_PROVENANCE"
            )
        );

        PortfolioConcentrationMetricsDto mockMetrics = new PortfolioConcentrationMetricsDto(
            new BigDecimal("32.50"), // top 5 >= 30% -> trigger Top Holdings Finding
            new BigDecimal("48.20"), // top 10 >= 45%
            new BigDecimal("420.50"), // HHI
            "LOW_CONCENTRATION",
            45, // distinct count >= 40 -> Well-Distributed Breadth
            2,
            new BigDecimal("13.77"),
            new BigDecimal("98.15"), // disclosed coverage >= 85%
            new BigDecimal("1.00"),  // cashAndEquivalentsWeightPct
            new BigDecimal("0.85")   // unclassifiedWeightPct
        );

        List<PairwiseOverlapResultDto> mockOverlap = List.of(
            new PairwiseOverlapResultDto(
                1L, "HDFC Flexi Cap Fund", 2L, "Parag Parikh Flexi Cap Fund",
                new BigDecimal("28.40"), 18, asOfDate, asOfDate, 0, "SYNCHRONIZED", List.of()
            )
        );

        List<ConsolidatedSectorExposureDto> mockSectors = List.of(
            new ConsolidatedSectorExposureDto(
                "Financial Services", new BigDecimal("30.93"), 8, List.of(), "AMFI", List.of()
            ),
            new ConsolidatedSectorExposureDto(
                "Information Technology", new BigDecimal("15.20"), 5, List.of(), "AMFI", List.of()
            )
        );

        List<ConsolidatedMarketCapExposureDto> mockMarketCaps = List.of(
            new ConsolidatedMarketCapExposureDto("LARGE_CAP", new BigDecimal("75.40"), 25, "AMFI"),
            new ConsolidatedMarketCapExposureDto("MID_CAP", new BigDecimal("15.10"), 10, "AMFI"),
            new ConsolidatedMarketCapExposureDto("SMALL_CAP", new BigDecimal("7.65"), 10, "AMFI")
        );

        MultiFundLookThroughResponseDto mockResponse = new MultiFundLookThroughResponseDto(
            asOfDate, cutoff, new BigDecimal("100.00"), true, "COMPUTED_VALIDATED_INPUTS",
            List.of(), mockSecurities, mockSectors, mockMarketCaps,
            mockOverlap, mockMetrics, List.of(), List.of(), Map.of()
        );

        when(lookThroughService.calculateMultiFundLookThrough(any())).thenReturn(mockResponse);

        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
            ),
            asOfDate, cutoff, "RAW"
        );

        PortfolioRiskDriversResponseDto response = service.calculateRiskDrivers(req);

        assertThat(response).isNotNull();
        assertThat(response.evaluationStatus()).isEqualTo("COMPUTED_VALIDATED_INPUTS");

        // 1. Verify Concentration Summary
        ConcentrationSummaryDto summary = response.concentrationSummary();
        assertThat(summary.largestSecurityName()).isEqualTo("HDFC Bank Ltd.");
        assertThat(summary.largestSecurityWeightPct()).isEqualTo(new BigDecimal("8.02"));
        assertThat(summary.top5WeightPct()).isEqualTo(new BigDecimal("32.50"));
        assertThat(summary.top10WeightPct()).isEqualTo(new BigDecimal("48.20"));
        assertThat(summary.distinctSecuritiesCount()).isEqualTo(3);
        assertThat(summary.disclosedEquityCoveragePct()).isEqualTo(new BigDecimal("98.15"));
        assertThat(summary.cashAndEquivalentsWeightPct()).isEqualTo(new BigDecimal("1.00"));
        assertThat(summary.unclassifiedWeightPct()).isEqualTo(new BigDecimal("0.85"));

        // 2. Verify Deterministic Findings
        List<RiskDriverFindingDto> findings = response.findings();
        assertThat(findings).isNotEmpty();

        // Finding 1: Single Security (HDFC Bank 8.02% >= 7.00%)
        assertThat(findings).anyMatch(f -> "SINGLE_SECURITY".equals(f.category()) && f.title().contains("HDFC Bank"));

        // Finding 2: Top Holdings (Top 5 32.50% >= 30.00%)
        assertThat(findings).anyMatch(f -> "TOP_HOLDINGS".equals(f.category()));

        // Finding 3: Shared Holdings Overlap (28.40% >= 20.00%)
        assertThat(findings).anyMatch(f -> "SHARED_HOLDINGS".equals(f.category()) && f.observedValue().contains("28.40%"));

        // Finding 4: Sector Concentration (Financial Services 30.93% >= 25.00%)
        assertThat(findings).anyMatch(f -> "SECTOR_CONCENTRATION".equals(f.category()) && f.title().contains("Financial Services"));

        // Finding 7: Synchronized Snapshots (delta = 0 days <= 30)
        assertThat(findings).anyMatch(f -> "TEMPORAL_ALIGNMENT".equals(f.category()) && f.title().contains("Synchronized"));
    }
}
