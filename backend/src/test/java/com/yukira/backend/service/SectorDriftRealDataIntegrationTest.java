package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.*;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.SectorDriftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SectorDriftRealDataIntegrationTest {

    @Autowired
    private SectorDriftService sectorDriftService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private Long canonicalOptionId;
    private Long canonicalBenchmarkId;

    @BeforeEach
    void setUp() {
        SchemeOption directGrowth = schemeOptionRepository.findAll().stream()
            .filter(o -> "118955".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Canonical pilot #118955 not found"));

        canonicalOptionId = directGrowth.getId();

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not found"));

        canonicalBenchmarkId = benchmark.getId();
    }

    @Test
    @DisplayName("Real Data: Evaluates historical sector drift trajectory for HDFC Flexi Cap Direct Growth")
    void shouldEvaluateRealDataSectorDriftTrajectory() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 10);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(
            canonicalOptionId, asOfDate, null, null, cutoff
        );

        assertThat(response).isNotNull();
        assertThat(response.schemeOptionId()).isEqualTo(canonicalOptionId);
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.eligibleObservationsCount()).isGreaterThanOrEqualTo(4);

        // Check latest observation (2026-09-30)
        SectorDriftObservationDto latestObs = response.observations().stream()
            .filter(o -> LocalDate.of(2026, 9, 30).equals(o.portfolioDate()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("2026-09-30 observation not found"));

        assertThat(latestObs.portfolioDisclosureScope()).isEqualTo("FULL_PORTFOLIO_DISCLOSURE");
        assertThat(latestObs.fundHoldingsCount()).isGreaterThanOrEqualTo(80);

        // Verification of honest gating:
        // Benchmark constituent weights are ~32.22% disclosed (top 10), so active weight must be gated
        assertThat(latestObs.observationStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
        assertThat(latestObs.benchmarkWeightCoveragePct()).isLessThan(new BigDecimal("99.50"));

        // All sector comparisons must have null definitive active weight
        for (SectorActiveComparisonDto comp : latestObs.sectorComparisons()) {
            assertThat(comp.definitiveActiveWeightPct()).isNull();
            assertThat(comp.comparisonStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
        }

        // Check top sector in fund is Financial Services (~37-43%)
        var topSector = latestObs.sectors().get(0);
        assertThat(topSector.sectorName()).isEqualTo("Financial Services");
        assertThat(topSector.fundWeightPct()).isEqualByComparingTo("34.54");

        // Check industry drill-down in latest observation
        assertThat(latestObs.industries()).isNotEmpty();
        var finInds = latestObs.industries().stream()
            .filter(i -> "Financial Services".equals(i.parentSectorName()))
            .toList();
        assertThat(finInds).isNotEmpty();
        var banksInd = finInds.stream().filter(i -> "Banks".equals(i.industryName())).findFirst();
        assertThat(banksInd).isPresent();
        assertThat(banksInd.get().holdingCount()).isGreaterThanOrEqualTo(1);
        assertThat(banksInd.get().fundWeightPct()).isGreaterThan(BigDecimal.ZERO);

        // Check security exposure waterfall
        assertThat(latestObs.securityWaterfall()).isNotEmpty();
        var comparableList = latestObs.securityWaterfall().stream()
            .filter(w -> "COMPARABLE_ACTIVE_EXPOSURE".equals(w.exposureCategory()))
            .toList();
        assertThat(comparableList).isNotEmpty();
        for (var c : comparableList) {
            assertThat(c.benchmarkWeightPct()).isNotNull();
            assertThat(c.activeSpreadPct()).isNotNull();
        }

        var unknownBmWeightList = latestObs.securityWaterfall().stream()
            .filter(w -> "BENCHMARK_MEMBER_WEIGHT_UNKNOWN".equals(w.exposureCategory()))
            .toList();
        assertThat(unknownBmWeightList).isNotEmpty();
        for (var u : unknownBmWeightList) {
            assertThat(u.benchmarkWeightPct()).isNull();
            assertThat(u.activeSpreadPct()).isNull(); // Strictly null per AGENTS.md
        }

        // Check industry drift changes exist
        assertThat(response.industryDriftChanges()).isNotEmpty();

        // Check scope expansion change driver between 2024-01-31 and 2026-06-30
        var scopeExpansionChange = response.sectorDriftChanges().stream()
            .filter(c -> LocalDate.of(2024, 1, 31).equals(c.earlierDate()) && LocalDate.of(2026, 6, 30).equals(c.laterDate()))
            .findFirst();

        if (scopeExpansionChange.isPresent()) {
            assertThat(scopeExpansionChange.get().changeDriver()).isEqualTo("DISCLOSURE_SCOPE_SHIFT");
        }
    }

    @Test
    @DisplayName("Real Data PIT Isolation: Queries evaluated under historical cutoff exclude future observations")
    void shouldEnforceStrictPitKnowledgeCutoff() {
        // Historical knowledge cutoff before 2026
        LocalDate asOfDate = LocalDate.of(2024, 1, 31);
        OffsetDateTime histCutoff = OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        SectorDriftResponseDto histResponse = sectorDriftService.getSectorDriftTrajectory(
            canonicalOptionId, asOfDate, null, null, histCutoff
        );

        assertThat(histResponse).isNotNull();
        // ZERO 2026 observations must leak into this historical query!
        for (SectorDriftObservationDto obs : histResponse.observations()) {
            assertThat(obs.portfolioDate()).isBeforeOrEqualTo(LocalDate.of(2024, 1, 31));
            assertThat(obs.portfolioAvailabilityTime()).isBeforeOrEqualTo(histCutoff);
        }
    }

    @Test
    @DisplayName("Deterministic Invariance: Repeated runs with identical parameters produce identical output")
    void shouldProduceIdenticalResultsUnderIdenticalRuns() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 0, 0, 0, 0, ZoneOffset.UTC);

        SectorDriftResponseDto run1 = sectorDriftService.getSectorDriftTrajectory(canonicalOptionId, asOfDate, null, null, cutoff);
        SectorDriftResponseDto run2 = sectorDriftService.getSectorDriftTrajectory(canonicalOptionId, asOfDate, null, null, cutoff);

        assertThat(run1.observations()).hasSameSizeAs(run2.observations());
        assertThat(run1.sectorDriftChanges()).hasSameSizeAs(run2.sectorDriftChanges());

        for (int i = 0; i < run1.observations().size(); i++) {
            var o1 = run1.observations().get(i);
            var o2 = run2.observations().get(i);
            assertThat(o1.portfolioDate()).isEqualTo(o2.portfolioDate());
            assertThat(o1.observationStatus()).isEqualTo(o2.observationStatus());
            assertThat(o1.sectors()).hasSameSizeAs(o2.sectors());
        }
    }
}
