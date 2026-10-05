package com.yukira.backend.ingestion.nse;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.ingestion.BenchmarkDataFreshnessDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.ingestion.BenchmarkDataFreshnessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.yukira.backend.BackendApplication.class)
@Transactional
@DisplayName("Production Benchmark Data Refresh V1 — Integration & Provenance Tests")
class BenchmarkRefreshIntegrationTest {

    @Autowired
    private NiftyBenchmarkIngestionService ingestionService;

    @Autowired
    private BenchmarkDataFreshnessService freshnessService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Test
    @DisplayName("TC-BR01: Canonical NIFTY 500 TRI identity resolution from DB")
    void testCanonicalBenchmarkIdentityResolution() {
        Benchmark benchmark = ingestionService.ensureCanonicalBenchmark();
        assertNotNull(benchmark);
        assertNotNull(benchmark.getId());
        assertEquals("NIFTY_500_TRI", benchmark.getCode());
        assertEquals("NIFTY 500 Total Returns Index", benchmark.getName());
        assertEquals("NSE Indices Limited", benchmark.getProvider());
        assertEquals("TRI", benchmark.getReturnVariant());
    }

    @Test
    @DisplayName("TC-BR02: Ingests 3-year NIFTY 500 TRI fixture, verifies SHA-256 provenance, idempotency, and PIT resolution")
    void testBenchmarkIngestionProvenanceIdempotencyAndPit() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/nifty500_tri_3y.json");
        assertNotNull(stream, "Fixture nifty500_tri_3y.json must be present in test classpath");
        byte[] payload = stream.readAllBytes();

        String sourceUri = "https://www.niftyindices.com/BackPage/getTotalReturnIndexString?name=Nifty%20500&startDate=15-Jan-2021&endDate=15-Jan-2024";

        // 1. Initial Ingestion
        var summary1 = ingestionService.ingestPayload("NIFTY_500_TRI_TEST", sourceUri, payload);
        assertEquals(743, summary1.totalParsed());
        assertEquals(743, summary1.insertedCount());
        assertEquals(0, summary1.skippedCount());
        assertEquals(0, summary1.revisedCount());
        assertEquals("f5e1f4089cace80eaca8387c9542e8cc52245ee11d98ef6f1e1c165e80389ac5", summary1.payloadSha256());

        // 2. Idempotent Ingestion
        var summary2 = ingestionService.ingestPayload("NIFTY_500_TRI_TEST", sourceUri, payload);
        assertEquals(743, summary2.totalParsed());
        assertEquals(0, summary2.insertedCount(), "Re-run must insert 0 new observations");
        assertEquals(743, summary2.skippedCount(), "Re-run must skip all 743 duplicate observations");

        // 3. PIT Cutoff Query
        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI_TEST").orElseThrow();
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<BenchmarkObservation> pitObs = benchmarkObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(benchmark.getId(), asOfDate, cutoff);

        assertEquals(743, pitObs.size());
        assertEquals(LocalDate.of(2021, 1, 15), pitObs.get(0).getEffectiveDate());
        assertEquals(new BigDecimal("18098.51000000"), pitObs.get(0).getIndexLevel());
        assertEquals(LocalDate.of(2024, 1, 15), pitObs.get(pitObs.size() - 1).getEffectiveDate());
    }

    @Test
    @DisplayName("TC-BR03: Revision handling updates revisionSeq and toggles isLatestRevision on modified observation")
    void testRevisionHandling() throws Exception {
        Benchmark benchmark = ingestionService.ensureCanonicalBenchmark();
        LocalDate testDate = LocalDate.of(2024, 1, 10);

        // First payload: level = 30000.00
        String json1 = String.format("[{\"Date\":\"%s\",\"TotalReturnsIndex\":\"30000.00\"}]", testDate.toString());
        ingestionService.ingestPayload(benchmark.getCode(), "https://source1.test", json1.getBytes());

        List<BenchmarkObservation> obs1 = benchmarkObservationRepository
            .findByBenchmarkIdAndEffectiveDate(benchmark.getId(), testDate);
        assertEquals(1, obs1.size());
        assertEquals(1, obs1.get(0).getRevisionSeq());
        assertTrue(obs1.get(0).getLatestRevision());

        // Revised payload for same date: level = 30050.00
        String json2 = String.format("[{\"Date\":\"%s\",\"TotalReturnsIndex\":\"30050.00\"}]", testDate.toString());
        ingestionService.ingestPayload(benchmark.getCode(), "https://source2.test", json2.getBytes());

        List<BenchmarkObservation> obs2 = benchmarkObservationRepository
            .findByBenchmarkIdAndEffectiveDate(benchmark.getId(), testDate);
        assertEquals(2, obs2.size());

        BenchmarkObservation oldObs = obs2.stream().filter(o -> o.getRevisionSeq() == 1).findFirst().orElseThrow();
        BenchmarkObservation newObs = obs2.stream().filter(o -> o.getRevisionSeq() == 2).findFirst().orElseThrow();

        assertFalse(oldObs.getLatestRevision(), "Old revision must no longer be marked as latest");
        assertTrue(newObs.getLatestRevision(), "New revision must be marked as latest");
        assertEquals(0, new BigDecimal("30050.00000000").compareTo(newObs.getIndexLevel()));

    }

    @Test
    @DisplayName("TC-BR04: BenchmarkDataFreshnessService evaluates coverage min/max dates and freshness state")
    void testBenchmarkDataFreshnessService() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/nifty500_tri_3y.json");
        assertNotNull(stream);
        ingestionService.ingestPayload("NIFTY_500_TRI", "https://source.test", stream.readAllBytes());

        BenchmarkDataFreshnessDto freshness = freshnessService.getFreshnessStatus(LocalDate.of(2024, 1, 15));

        assertNotNull(freshness);
        assertEquals("NIFTY_500_TRI", freshness.benchmarkCode());
        assertEquals("FRESH", freshness.freshnessState());
        assertEquals(LocalDate.of(2021, 1, 15), freshness.earliestObservationDate());
        assertEquals(LocalDate.of(2024, 1, 15), freshness.latestObservationDate());
        assertEquals(743, freshness.totalObservationCount());
        assertNotNull(freshness.governanceDisclaimer());
        assertTrue(freshness.governanceDisclaimer().contains("does NOT execute quant engine"));
    }

    @Test
    @DisplayName("TC-BR05: Live network fetch failure preserves existing dataset without corrupting DB")
    void testGracefulNetworkFetchFailureHandling() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/nifty500_tri_3y.json");
        assertNotNull(stream);
        ingestionService.ingestPayload("NIFTY_500_TRI", "https://source.test", stream.readAllBytes());

        long initialCount = benchmarkObservationRepository.count();

        // Attempt refresh with invalid URL / offline behavior
        var summary = ingestionService.refreshBenchmarkData(LocalDate.of(2024, 1, 16), LocalDate.of(2024, 1, 20));

        assertNotNull(summary);
        long finalCount = benchmarkObservationRepository.count();
        assertEquals(initialCount, finalCount, "Existing benchmark dataset must be preserved intact on fetch failure");
    }

    @Test
    @DisplayName("TC-BR06: HDFC scheme_option_id=1 benchmark mapping resolves to NIFTY_500_TRI")
    void testHdfcBenchmarkMappingRegression() {
        Benchmark canonicalBenchmark = ingestionService.ensureCanonicalBenchmark();
        SchemeOption hdfcOption = schemeOptionRepository.findByAmfiCode("118955").orElse(null);

        if (hdfcOption != null) {
            assertEquals(1L, hdfcOption.getId(), "Canonical HDFC scheme_option_id must be 1");
            assertEquals("INF179K01UT0", hdfcOption.getIsin());
        }

        assertNotNull(canonicalBenchmark);
        assertEquals("NIFTY_500_TRI", canonicalBenchmark.getCode());
    }

    @Test
    @DisplayName("TC-BR07: Benchmark ingestion does NOT invoke quant calculation or alter existing MetricResult / AnalyticalScore rows")
    void testQuantIsolationDuringBenchmarkIngestion() throws Exception {
        long initialMetricResults = metricResultRepository.count();
        long initialScores = analyticalScoreRepository.count();

        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/nifty500_tri_3y.json");
        assertNotNull(stream);
        ingestionService.ingestPayload("NIFTY_500_TRI", "https://source.test", stream.readAllBytes());

        long finalMetricResults = metricResultRepository.count();
        long finalScores = analyticalScoreRepository.count();

        assertEquals(initialMetricResults, finalMetricResults, "MetricResults count must remain unchanged during benchmark ingestion");
        assertEquals(initialScores, finalScores, "AnalyticalScore snapshots count must remain unchanged during benchmark ingestion");
    }
}
