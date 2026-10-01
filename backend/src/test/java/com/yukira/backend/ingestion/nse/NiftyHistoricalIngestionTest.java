package com.yukira.backend.ingestion.nse;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
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
@DisplayName("NSE NIFTY 500 TRI 3-Year Historical Ingestion and PIT Resolution Test")
class NiftyHistoricalIngestionTest {

    @Autowired
    private NiftyBenchmarkIngestionService ingestionService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Test
    @DisplayName("Ingests official 3-year NIFTY 500 TRI artifact, verifies idempotency, PIT resolution, and >700 observations")
    void testHistorical3YBenchmarkIngestionAndPitResolution() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/nifty500_tri_3y.json");
        assertNotNull(stream, "Fixture nifty500_tri_3y.json must be present in test classpath");
        byte[] payload = stream.readAllBytes();

        String sourceUri = "https://www.niftyindices.com/BackPage/getTotalReturnIndexString?name=Nifty%20500&startDate=15-Jan-2021&endDate=15-Jan-2024";

        // 1. First Ingestion
        var firstSummary = ingestionService.ingestPayload("NIFTY_500_TRI_TEST", sourceUri, payload);
        assertEquals(743, firstSummary.totalParsed(), "Must parse exactly 743 Nifty 500 TRI observations");
        assertEquals(743, firstSummary.insertedCount(), "Must insert exactly 743 observations");
        assertEquals(0, firstSummary.skippedCount(), "Zero skipped count on first ingestion");
        assertEquals(0, firstSummary.revisedCount(), "Zero revised count on first ingestion");
        assertEquals("f5e1f4089cace80eaca8387c9542e8cc52245ee11d98ef6f1e1c165e80389ac5", firstSummary.payloadSha256());

        // 2. Idempotency: Second Ingestion
        var secondSummary = ingestionService.ingestPayload("NIFTY_500_TRI_TEST", sourceUri, payload);
        assertEquals(743, secondSummary.totalParsed());
        assertEquals(0, secondSummary.insertedCount(), "Second ingestion must insert 0 rows (idempotency)");
        assertEquals(743, secondSummary.skippedCount(), "Second ingestion must skip all 743 rows");

        // 3. PIT Resolution Check
        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI_TEST").orElseThrow();
        LocalDate analysisCutoff = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<BenchmarkObservation> pitObs = benchmarkObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(benchmark.getId(), analysisCutoff, knowledgeCutoff);

        assertEquals(743, pitObs.size(), "PIT query must return all 743 observations");
        assertEquals(LocalDate.of(2021, 1, 15), pitObs.get(0).getEffectiveDate());
        assertEquals(new BigDecimal("18098.51000000"), pitObs.get(0).getIndexLevel());

        assertEquals(LocalDate.of(2024, 1, 15), pitObs.get(pitObs.size() - 1).getEffectiveDate());
        assertEquals(new BigDecimal("31132.31000000"), pitObs.get(pitObs.size() - 1).getIndexLevel());

        // 4. Verify Future Exclusion
        LocalDate earlyCutoff = LocalDate.of(2023, 1, 15);
        List<BenchmarkObservation> earlyPitObs = benchmarkObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(benchmark.getId(), earlyCutoff, knowledgeCutoff);

        assertTrue(earlyPitObs.size() < 743);
        assertTrue(earlyPitObs.get(earlyPitObs.size() - 1).getEffectiveDate().isBefore(LocalDate.of(2023, 1, 16)));
    }
}
