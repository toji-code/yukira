package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.dto.portfolio.BenchmarkFactsheetArchiveResponseDto;
import com.yukira.backend.dto.portfolio.BenchmarkFactsheetSnapshotDto;
import com.yukira.backend.repository.BenchmarkConstituentRepository;
import com.yukira.backend.repository.BenchmarkRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkFactsheetArchiveServiceTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkConstituentRepository constituentRepository;

    private BenchmarkFactsheetArchiveService archiveService;
    private Benchmark benchmark;

    @BeforeEach
    void setUp() {
        archiveService = new BenchmarkFactsheetArchiveService(benchmarkRepository, constituentRepository);
        benchmark = new Benchmark("NIFTY_500_TRI", "NIFTY 500 Total Returns Index", "NSE_INDICES", "TRI");
        benchmark.setId(100L);
    }

    @Test
    @DisplayName("Archive audit: Returns 5 independent coverage dimensions for each snapshot")
    void testGetFactsheetArchive_CoverageDimensions() {
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmark));

        OffsetDateTime availTime = OffsetDateTime.of(2024, 2, 7, 23, 39, 33, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate effDate = LocalDate.of(2024, 1, 31);

        // row: [effectiveDate, minAvailTime, artifactId, sha256, storageUri, totalCount, weightedCount, weightSum, mappedCount]
        Object[] row1 = new Object[] {
            effDate,
            availTime,
            501L,
            "0189acf6590bc224688c1e74bdc249fe867efd159791b01a86477c048a2e5e71",
            "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv",
            501L,
            10L,
            new BigDecimal("35.700000"),
            495L
        };

        List<Object[]> rows = new ArrayList<>();
        rows.add(row1);

        when(constituentRepository.findSnapshotLedgerSummaries(eq(100L), any(OffsetDateTime.class)))
            .thenReturn(rows);

        BenchmarkFactsheetArchiveResponseDto response = archiveService.getFactsheetArchive(
            "NIFTY_500_TRI",
            OffsetDateTime.now()
        );

        assertThat(response).isNotNull();
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.totalSnapshotsAvailable()).isEqualTo(1);
        assertThat(response.snapshots()).hasSize(1);

        BenchmarkFactsheetSnapshotDto snap = response.snapshots().get(0);
        assertThat(snap.effectiveDate()).isEqualTo(effDate);
        assertThat(snap.totalConstituentsCount()).isEqualTo(501);
        assertThat(snap.weightedConstituentsCount()).isEqualTo(10);
        assertThat(snap.unknownWeightsCount()).isEqualTo(491);
        assertThat(snap.disclosedWeightSum()).isEqualByComparingTo(new BigDecimal("35.70"));

        // Verify independent coverage dimensions
        assertThat(snap.membershipCoveragePct()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(snap.membershipStatus()).isEqualTo("VERIFIED_COMPLETE");

        assertThat(snap.identifierMappingCoveragePct()).isGreaterThan(new BigDecimal("95.00"));
        assertThat(snap.identifierMappingStatus()).isEqualTo("VERIFIED_COMPLETE");

        assertThat(snap.weightCoveragePct()).isEqualByComparingTo(new BigDecimal("35.70"));
        assertThat(snap.weightCoverageStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");

        assertThat(snap.sourceConfidenceStatus()).isEqualTo("VERIFIED_COMPLETE");
        assertThat(snap.sourcePublisher()).isEqualTo("NSE Indices Limited");
        assertThat(snap.licensingConstraint()).contains("DEP-05");

        // Governance disclosures and gaps
        assertThat(response.knownHistoricalGaps()).isNotEmpty();
        assertThat(response.dataIntegrityDisclosures()).isNotEmpty();
    }

    @Test
    @DisplayName("Unknown benchmark code throws IllegalArgumentException")
    void testUnknownBenchmark_ThrowsException() {
        when(benchmarkRepository.findByCode("UNKNOWN_INDEX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> archiveService.getFactsheetArchive("UNKNOWN_INDEX", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UNKNOWN_INDEX");
    }
}
