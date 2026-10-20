package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.mcap.*;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.MarketCapDriftService;
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
public class MarketCapDriftRealDataIntegrationTest {

    @Autowired
    private MarketCapDriftService marketCapDriftService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private Long canonicalOptionId;

    @BeforeEach
    void setUp() {
        SchemeOption directGrowth = schemeOptionRepository.findAll().stream()
            .filter(o -> "118955".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Canonical pilot #118955 not found"));

        canonicalOptionId = directGrowth.getId();

        benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not found"));
    }

    @Test
    @DisplayName("Real Data: Evaluates historical market-cap style drift trajectory for HDFC Flexi Cap Direct Growth")
    void shouldEvaluateRealDataMarketCapDriftTrajectory() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 10);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        MarketCapDriftResponseDto response = marketCapDriftService.getMarketCapDriftTrajectory(
            canonicalOptionId, asOfDate, null, null, cutoff
        );

        assertThat(response).isNotNull();
        assertThat(response.schemeOptionId()).isEqualTo(canonicalOptionId);
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.eligibleSnapshotsCount()).isGreaterThanOrEqualTo(4);

        // Verify each snapshot has valid buckets and reconciliation
        for (MarketCapObservationDto obs : response.observations()) {
            assertThat(obs.portfolioDisclosureScope()).isNotNull();
            assertThat(obs.totalHoldingsCount()).isGreaterThan(0);
            assertThat(obs.classificationCoverageWeightPct()).isGreaterThan(new BigDecimal("70.00"));
            assertThat(obs.bucketExposures()).isNotEmpty();

            // Strict benchmark gating check:
            // NIFTY 500 top constituents are partially disclosed, so active weight must be gated
            MarketCapBenchmarkComparisonDto comp = obs.benchmarkComparison();
            if (comp != null) {
                assertThat(comp.isWeightCoverageComplete()).isFalse();
                assertThat(comp.gatingStatus()).isIn("GATED_INCOMPLETE_BENCHMARK_WEIGHTS", "TEMPORAL_MISMATCH", "BENCHMARK_UNAVAILABLE");
                if (LocalDate.of(2026, 9, 30).equals(obs.portfolioDate())) {
                    assertThat(comp.gatingStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
                    assertThat(comp.benchmarkWeightCoveragePct()).isLessThan(new BigDecimal("99.50"));
                }
            }

            // Check that market-cap buckets are distinct from cash and debt
            boolean hasLarge = obs.bucketExposures().stream().anyMatch(b -> "LARGE_CAP".equals(b.category()));
            boolean hasCash = obs.bucketExposures().stream().anyMatch(b -> "CASH".equals(b.category()));
            assertThat(hasLarge).isTrue();
            assertThat(hasCash).isTrue();
        }

        // Verify drift transitions mathematical reconciliation
        assertThat(response.transitions()).isNotEmpty();
        for (MarketCapDriftChangeDto transition : response.transitions()) {
            assertThat(transition.isReconciled()).isTrue();
            BigDecimal sumComponents = transition.weightChangeComponent()
                .add(transition.reclassificationComponent())
                .add(transition.disclosureScopeComponent())
                .add(transition.unresolvedComponent());
            BigDecimal residual = transition.deltaWeightPct().subtract(sumComponents).abs();
            assertThat(residual).isLessThanOrEqualTo(new BigDecimal("0.02"));
        }
    }

    @Test
    @DisplayName("Real Data: Dual PIT cutoff excludes snapshots published after cutoff time")
    void shouldStrictlyEnforceDualPitCutoffOnRealData() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 10);
        // Cutoff before 2026-09-30 portfolio availability (e.g. 2026-09-01)
        OffsetDateTime pastCutoff = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        MarketCapDriftResponseDto response = marketCapDriftService.getMarketCapDriftTrajectory(
            canonicalOptionId, asOfDate, null, null, pastCutoff
        );

        assertThat(response).isNotNull();
        // The 2026-09-30 snapshot must NOT be present
        boolean containsSeptember2026 = response.observations().stream()
            .anyMatch(o -> LocalDate.of(2026, 9, 30).equals(o.portfolioDate()));
        assertThat(containsSeptember2026).isFalse();
    }
}
