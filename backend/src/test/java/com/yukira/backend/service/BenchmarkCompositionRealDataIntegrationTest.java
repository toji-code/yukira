package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkConstituent;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.ingestion.BenchmarkConstituentIngestionResultDto;
import com.yukira.backend.dto.portfolio.BenchmarkCompositionComparisonResponseDto;
import com.yukira.backend.repository.BenchmarkConstituentRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BenchmarkCompositionRealDataIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkCompositionRealDataIntegrationTest.class);

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private PilotMarketCapDataService pilotMarketCapDataService;

    @Autowired
    private PilotBenchmarkConstituentDataService pilotBenchmarkConstituentDataService;

    @Autowired
    private BenchmarkCompositionComparisonService benchmarkCompositionComparisonService;

    @Autowired
    private BenchmarkConstituentRepository benchmarkConstituentRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private Long canonicalOptionId;
    private Long canonicalBenchmarkId;

    @BeforeEach
    void setUp() {
        // Bootstrap canonical pilot data
        pilotBootstrapService.bootstrapPilot();
        pilotMarketCapDataService.seedAuthoritativeMarketCapClassifications();
        pilotBenchmarkConstituentDataService.seedCanonicalBenchmarkConstituents();

        SchemeOption option = schemeOptionRepository.findByAmfiCode("118955")
            .orElseGet(() -> schemeOptionRepository.findAll().get(0));
        canonicalOptionId = option.getId();

        Benchmark bm = benchmarkRepository.findByCode("NIFTY_500_TRI").orElseThrow();
        canonicalBenchmarkId = bm.getId();
    }

    @Test
    @DisplayName("Verify Real Historical Benchmark Snapshot 1 (2024-01-15 Baseline)")
    void shouldVerifyHistoricalBenchmarkSnapshot() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 20, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<BenchmarkConstituent> constituents = benchmarkConstituentRepository
            .findEligibleConstituents(canonicalBenchmarkId, asOfDate, cutoff);

        assertThat(constituents).isNotEmpty();
        assertThat(constituents).hasSize(501);

        BenchmarkConstituent first = constituents.get(0);
        assertThat(first.getSourceArtifact()).isNotNull();
        assertThat(first.getSourceArtifact().getSha256Hash())
            .isEqualTo("2b150cd1ed08cc6eaef87e2687103ae112eea3a2a1ce211828cc3a5bb12f83ae");

        long mapped = constituents.stream().filter(c -> "MAPPED".equals(c.getMappingStatus())).count();
        long unmapped = constituents.stream().filter(c -> "UNMAPPED".equals(c.getMappingStatus())).count();
        log.info("Historical Snapshot 1 (2024-01-15): Total={}, Mapped={}, Unmapped={}",
            constituents.size(), mapped, unmapped);

        assertThat(mapped).isGreaterThan(0);
        assertThat(unmapped).isGreaterThan(0);
        assertThat(mapped + unmapped).isEqualTo(501);

        // Check top weights disclosed
        long weightedCount = constituents.stream().filter(c -> c.getWeight() != null).count();
        assertThat(weightedCount).isGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("Verify Real Current Benchmark Snapshot 2 (2026-09-30 Reconstitution)")
    void shouldVerifyCurrentBenchmarkSnapshot() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<BenchmarkConstituent> constituents = benchmarkConstituentRepository
            .findEligibleConstituents(canonicalBenchmarkId, asOfDate, cutoff);

        assertThat(constituents).isNotEmpty();
        assertThat(constituents).hasSize(500);

        BenchmarkConstituent first = constituents.get(0);
        assertThat(first.getSourceArtifact()).isNotNull();
        assertThat(first.getSourceArtifact().getSha256Hash())
            .isEqualTo("2959bf206239284e145f7aecc65095b18d11556d2642323a70f2d26efe0f5cb3");

        long mapped = constituents.stream().filter(c -> "MAPPED".equals(c.getMappingStatus())).count();
        long unmapped = constituents.stream().filter(c -> "UNMAPPED".equals(c.getMappingStatus())).count();
        log.info("Current Snapshot 2 (2026-09-30): Total={}, Mapped={}, Unmapped={}",
            constituents.size(), mapped, unmapped);

        assertThat(mapped + unmapped).isEqualTo(500);
    }

    @Test
    @DisplayName("Verify Real Fund-versus-Benchmark Composition Comparison for HDFC Flexi Cap")
    void shouldVerifyFundVersusBenchmarkComparison() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 2, 5, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        BenchmarkCompositionComparisonResponseDto response = benchmarkCompositionComparisonService
            .compareComposition(canonicalOptionId, asOfDate, cutoff);

        assertThat(response).isNotNull();
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.benchmarkConstituentCount()).isEqualTo(501);

        log.info("Fund-versus-Benchmark Comparison for {}: HoldingsOverlap={}%, CommonStocks={}, ActivePositions={}",
            response.fundName(), response.holdingsOverlapPct(), response.commonConstituentCount(),
            response.activePositions().size());

        // Epistemic Demarcation & Brinson Gate
        assertThat(response.brinsonAttributionStatus()).isEqualTo("BLOCKED_PENDING_STOCK_RETURNS");
        assertThat(response.brinsonBlockerReason()).contains("Constituent-level Brinson-Fachler attribution");
        assertThat(response.investorOrientation()).contains("do NOT constitute causal return attribution");

        // Verify PIT invariance: when knowledge cutoff is set before constituent publication (2019-01-01)
        OffsetDateTime earlyCutoff = OffsetDateTime.of(2019, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<BenchmarkConstituent> pitExcluded = benchmarkConstituentRepository
            .findEligibleConstituents(canonicalBenchmarkId, asOfDate, earlyCutoff);
        assertThat(pitExcluded).isEmpty();
    }
}
