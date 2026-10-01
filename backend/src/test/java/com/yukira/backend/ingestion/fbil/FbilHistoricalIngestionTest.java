package com.yukira.backend.ingestion.fbil;

import com.yukira.backend.domain.entity.RiskFreeObservation;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@DisplayName("FBIL 3-Year Historical Ingestion and PIT Resolution Test")
@SuppressWarnings("null")
class FbilHistoricalIngestionTest {

    @Autowired
    private FbilTBillIngestionService ingestionService;

    @Autowired
    private RiskFreeObservationRepository riskFreeObservationRepository;

    @Test
    @DisplayName("Ingests official 3-year FBIL artifact, verifies idempotency, PIT selection, and >700 observations")
    void testHistorical3YIngestionAndPitResolution() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/fbil_tbill_3y.json");
        assertNotNull(stream, "Fixture fbil_tbill_3y.json must be present in test classpath");
        byte[] payload = stream.readAllBytes();

        String sourceUri = "https://www.fbil.org.in/wasdm/tbill/fetchfiltered?fromDate=2021-01-15&toDate=2024-01-15&authenticated=false";

        // First Ingestion
        var firstSummary = ingestionService.ingestPayload("FBIL_91D_TBILL_TEST", sourceUri, payload);
        assertEquals(727, firstSummary.totalParsed(), "Must parse exactly 727 3M observations");
        assertTrue(firstSummary.insertedCount() >= 700, "Must insert >= 700 observations");
        assertEquals(0, firstSummary.revisedCount(), "Zero revision count expected on first ingestion");
        assertEquals("94876ff3a94bdbfdf6fe74bcb976e8400eeddf0e1839f3e6c0bc1e1495f99ba0", firstSummary.payloadSha256());

        // Idempotency: Second Ingestion
        var secondSummary = ingestionService.ingestPayload("FBIL_91D_TBILL_TEST", sourceUri, payload);
        assertEquals(727, secondSummary.totalParsed());
        assertEquals(0, secondSummary.insertedCount(), "Second ingestion must insert 0 rows (idempotency)");
        assertEquals(727, secondSummary.skippedCount(), "Second ingestion must skip all 727 rows");

        // PIT Resolution Check
        LocalDate analysisCutoff = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<RiskFreeObservation> pitObs = riskFreeObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            "FBIL_91D_TBILL_TEST", analysisCutoff, knowledgeCutoff
        );

        assertTrue(pitObs.size() >= 700, "PIT resolved observations must be >= 700 for 3Y analytical window");
        assertEquals(727, pitObs.size());

        // Verify ordering and boundary dates
        assertEquals(LocalDate.of(2021, 1, 15), pitObs.get(0).getEffectiveDate(), "Earliest observation date must be 2021-01-15");
        assertEquals(LocalDate.of(2024, 1, 15), pitObs.get(pitObs.size() - 1).getEffectiveDate(), "Latest observation date must be 2024-01-15");

        // Verify quality dimensions
        for (RiskFreeObservation obs : pitObs) {
            assertEquals("VALID", obs.getQualityAssessment());
            assertEquals("VERIFIED", obs.getVerificationStatus());
            assertEquals("ORIGINAL", obs.getRevisionStatus());
            assertEquals("ACT_365", obs.getDaycountConvention());
            assertTrue(obs.getEffectiveDate().compareTo(analysisCutoff) <= 0, "No look-ahead date permitted");
            assertTrue(obs.getAvailabilityTime().compareTo(knowledgeCutoff) <= 0, "No future knowledge timestamp permitted");
        }
    }
}
