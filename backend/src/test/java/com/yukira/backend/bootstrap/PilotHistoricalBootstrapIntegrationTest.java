package com.yukira.backend.bootstrap;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.AnalyticalObservationSeriesDto;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.HistoricalAnalyticalDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Tag("external-integration")
public class PilotHistoricalBootstrapIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private HistoricalAnalyticalDataService historicalDataService;

    @BeforeEach
    void ensurePilotMasterExists() {
        pilotBootstrapService.ensureCanonicalPilotMaster();
    }

    @Test
    @DisplayName("Verify live historical horizon bootstrap is resilient against external portal rate-limiting and timeouts")
    void testBootstrapHistoricalHorizonResilience() {
        PilotBootstrapService.HistoricalBootstrapReport report = pilotBootstrapService.bootstrapHistoricalHorizon(5);

        assertNotNull(report, "Historical bootstrap report must not be null");
        assertEquals("118955", report.amfiCode(), "Canonical AMFI code must be 118955");
        assertEquals("INF179K01UT0", report.isin(), "Canonical ISIN must be INF179K01UT0");
        assertTrue(report.totalObservationsInLedger() >= 1000,
            "Ledger must contain full multi-year horizon (>=1000 observations), found: "
                + report.totalObservationsInLedger());
        assertNotNull(report.earliestObservationDate());
        assertNotNull(report.latestObservationDate());
        assertTrue(report.earliestObservationDate().getYear() <= 2019,
            "Earliest observation date must be 2019 or earlier, found: " + report.earliestObservationDate());
        assertTrue(report.latestObservationDate().getYear() >= 2024,
            "Latest observation date must be 2024 or later, found: " + report.latestObservationDate());
    }

    @Test
    @DisplayName("Verify multi-year historical analytical query, cryptographic provenance, and PIT series bounds")
    void testMultiYearHistoricalAnalyticalSeriesQuery() {
        SchemeOption option = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 must exist"));

        // Query across 2-year window (2019-2020) with strict PIT knowledge cutoff
        AnalyticalObservationSeriesDto series = historicalDataService.getHistoricalObservationSeries(
            option.getId(),
            LocalDate.of(2019, 1, 1),
            LocalDate.of(2020, 12, 31),
            OffsetDateTime.of(2021, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30))
        );

        assertNotNull(series);
        assertTrue(series.observations().size() >= 400,
            "Must retrieve >=400 daily observations across 2019-2020, found: " + series.observations().size());
        assertTrue(series.summary().distinctSourceArtifactsCount() >= 2,
            "Must span multiple authentic source artifacts");

        // Verify PIT bounds and chronological order
        assertEquals(LocalDate.of(2019, 1, 1), series.pitBounds().startDate());
        assertEquals(LocalDate.of(2020, 12, 31), series.pitBounds().analysisCutoffDate());

        // Verify cryptographic SHA-256 provenance is populated on all items
        for (var item : series.observations()) {
            assertNotNull(item.sourceArtifactId());
            assertNotNull(item.sourceArtifactSha256());
            assertEquals(64, item.sourceArtifactSha256().length(), "SHA-256 hash must be 64 hex characters");
            assertEquals("VALID", item.qualityAssessment());
            assertEquals("VERIFIED", item.verificationStatus());
        }
    }

    @Test
    @DisplayName("Verify FBIL 3-year historical risk-free series bootstrap and provenance")
    void testBootstrapHistoricalFbil() {
        var summary = pilotBootstrapService.bootstrapHistoricalFbil();
        if (summary != null) {
            assertTrue(summary.insertedCount() >= 700, "Must insert >=700 FBIL observations");
            assertNotNull(summary.payloadSha256());
        }
    }
}
