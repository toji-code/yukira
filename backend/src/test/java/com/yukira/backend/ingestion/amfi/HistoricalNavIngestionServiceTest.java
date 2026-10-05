package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoricalNavIngestionServiceTest {

    @Mock
    private AmfiSourceClient amfiSourceClient;

    @Mock
    private AmfiNavIngestionService amfiNavIngestionService;

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @InjectMocks
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Test
    @DisplayName("Controlled sample targets correctly specify 5 diverse funds across categories and options")
    void testControlledSampleTargetDefinitions() {
        var targets = HistoricalNavIngestionService.CONTROLLED_SAMPLE_TARGETS;
        assertEquals(5, targets.size());

        // Target 1: Canonical pilot
        assertEquals("118955", targets.get(0).amfiCode());
        assertEquals("INF179K01UT0", targets.get(0).isin());
        assertEquals("DIRECT", targets.get(0).planType());
        assertEquals("GROWTH", targets.get(0).optionType());

        // Target 2: Large Cap Equity
        assertEquals("119018", targets.get(1).amfiCode());

        // Target 3: Small Cap High Volatility
        assertEquals("118778", targets.get(2).amfiCode());

        // Target 4: Liquid / Debt
        assertEquals("119091", targets.get(3).amfiCode());

        // Target 5: IDCW Variant
        assertEquals("118954", targets.get(4).amfiCode());
        assertEquals("IDCW", targets.get(4).optionType());
    }

    @Test
    @DisplayName("Ingest single historical NAV calls source client and nav ingestion service with exact provenance")
    void testIngestHistoricalNav() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);
        SourceArtifact artifact = new SourceArtifact();
        artifact.setId(101L);

        IngestionSummary mockSummary = new IngestionSummary(
            101L, "sha256hash", 11, 11, 11, 0, 0, 0, List.of("Success")
        );

        when(amfiSourceClient.fetchAndPersistArtifact("118955", "9", start, end)).thenReturn(artifact);
        when(amfiNavIngestionService.ingestArtifact(artifact)).thenReturn(mockSummary);

        IngestionSummary summary = historicalNavIngestionService.ingestHistoricalNav("118955", "9", start, end);

        assertNotNull(summary);
        assertEquals(11, summary.observationsIngested());
        assertEquals(101L, summary.sourceArtifactId());
        verify(amfiSourceClient).fetchAndPersistArtifact("118955", "9", start, end);
        verify(amfiNavIngestionService).ingestArtifact(artifact);
    }

    @Test
    @DisplayName("Targeted backfill resolves exact scheme option and ingests only that AMFI code")
    void testTargetedBackfillUsesExactSchemeOptionSelection() {
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 15);
        SchemeOption opt = new SchemeOption();
        opt.setId(10189L);
        opt.setAmfiCode("120564");
        opt.setIsin("INF209K01UN8");
        SourceArtifact artifact = new SourceArtifact();
        artifact.setId(202L);

        IngestionSummary mockSummary = new IngestionSummary(
            202L, "targetedhash", 10, 10, 10, 0, 0, 0, List.of("Success")
        );

        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(opt));
        when(amfiSourceClient.fetchAndPersistArtifact("120564", null, start, end)).thenReturn(artifact);
        when(amfiNavIngestionService.ingestArtifact(artifact, "120564")).thenReturn(mockSummary);

        var result = historicalNavIngestionService.ingestHistoricalNavForSchemeOptions(
            List.of(10189L), start, end
        );

        assertEquals(1, result.fundsRequested());
        assertEquals(1, result.fundsSucceeded());
        assertEquals(0, result.fundsFailed());
        assertEquals(10, result.totalObservationsIngested());
        assertEquals("SUCCESS", result.funds().get(0).status());
        assertEquals("120564", result.funds().get(0).amfiCode());
        verify(amfiSourceClient).fetchAndPersistArtifact("120564", null, start, end);
        verify(amfiNavIngestionService).ingestArtifact(artifact, "120564");
        verify(amfiNavIngestionService, never()).ingestArtifact(artifact);
    }

    @Test
    @DisplayName("Targeted backfill is repeatable and reports duplicate observations from existing ingestion")
    void testTargetedBackfillRepeatReportsIdempotentDuplicates() {
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 15);
        SchemeOption opt = new SchemeOption();
        opt.setId(10189L);
        opt.setAmfiCode("120564");
        SourceArtifact firstArtifact = new SourceArtifact();
        firstArtifact.setId(301L);
        SourceArtifact secondArtifact = new SourceArtifact();
        secondArtifact.setId(301L);

        IngestionSummary firstSummary = new IngestionSummary(
            301L, "repeatHash", 10, 10, 10, 0, 0, 0, List.of("Initial ingest")
        );
        IngestionSummary secondSummary = new IngestionSummary(
            301L, "repeatHash", 10, 10, 0, 0, 10, 0, List.of("Duplicate observations skipped")
        );

        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(opt));
        when(amfiSourceClient.fetchAndPersistArtifact("120564", null, start, end))
            .thenReturn(firstArtifact)
            .thenReturn(secondArtifact);
        when(amfiNavIngestionService.ingestArtifact(firstArtifact, "120564")).thenReturn(firstSummary);
        when(amfiNavIngestionService.ingestArtifact(secondArtifact, "120564")).thenReturn(secondSummary);

        var first = historicalNavIngestionService.ingestHistoricalNavForSchemeOption(10189L, start, end);
        var second = historicalNavIngestionService.ingestHistoricalNavForSchemeOption(10189L, start, end);

        assertEquals("SUCCESS", first.status());
        assertEquals(10, first.summary().observationsIngested());
        assertEquals("SUCCESS", second.status());
        assertEquals(0, second.summary().observationsIngested());
        assertEquals(10, second.summary().duplicateRowsSkipped());
        verify(amfiSourceClient, times(2)).fetchAndPersistArtifact("120564", null, start, end);
        verify(amfiNavIngestionService, times(2)).ingestArtifact(any(SourceArtifact.class), eq("120564"));
    }

    @Test
    @DisplayName("Targeted backfill skips already dense chunks during resume")
    void testTargetedBackfillSkipsAlreadyDenseChunks() {
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 15);
        SchemeOption opt = new SchemeOption();
        opt.setId(10189L);
        opt.setAmfiCode("120564");

        List<com.yukira.backend.domain.entity.NavObservation> existing = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            com.yukira.backend.domain.entity.NavObservation observation = mock(com.yukira.backend.domain.entity.NavObservation.class);
            when(observation.getLatestRevision()).thenReturn(true);
            existing.add(observation);
        }

        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(opt));
        when(navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(10189L, start, end))
            .thenReturn(existing);

        var result = historicalNavIngestionService.ingestHistoricalNavForSchemeOption(10189L, start, end);

        assertEquals("SUCCESS", result.status());
        assertEquals(0, result.summary().observationsIngested());
        assertEquals(0, result.summary().duplicateRowsSkipped());
        assertTrue(result.summary().messages().get(0).contains("Skipped"));
        verify(amfiSourceClient, never()).fetchAndPersistArtifact(anyString(), any(), any(), any());
        verify(amfiNavIngestionService, never()).ingestArtifact(any(SourceArtifact.class), anyString());
    }

    @Test
    @DisplayName("Targeted backfill records source failure without fabricating observations")
    void testTargetedBackfillSourceFailureDoesNotIngest() {
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 15);
        SchemeOption opt = new SchemeOption();
        opt.setId(10189L);
        opt.setAmfiCode("120564");

        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(opt));
        when(amfiSourceClient.fetchAndPersistArtifact("120564", null, start, end))
            .thenThrow(new RuntimeException("AMFI source unavailable"));

        var result = historicalNavIngestionService.ingestHistoricalNavForSchemeOption(10189L, start, end);

        assertEquals("FAILED", result.status());
        assertNull(result.summary());
        assertTrue(result.message().contains("AMFI source unavailable"));
        verify(amfiNavIngestionService, never()).ingestArtifact(any(SourceArtifact.class), anyString());
        verify(amfiNavIngestionService, never()).ingestArtifact(any(SourceArtifact.class));
    }

    @Test
    @DisplayName("Controlled sample coverage reflects database observation counts")
    void testControlledSampleCoverageReporting() {
        SchemeOption opt = new SchemeOption();
        opt.setId(10L);
        opt.setAmfiCode("118955");

        when(schemeOptionRepository.findByAmfiCode("118955")).thenReturn(Optional.of(opt));
        when(navObservationRepository.countBySchemeOptionId(10L)).thenReturn(1247L);

        Map<String, Object> coverage = historicalNavIngestionService.getControlledSampleCoverage();
        assertNotNull(coverage);
        assertTrue(coverage.containsKey("funds"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> funds = (List<Map<String, Object>>) coverage.get("funds");
        assertEquals(5, funds.size());

        Map<String, Object> pilotFund = funds.get(0);
        assertEquals("118955", pilotFund.get("amfiCode"));
        assertEquals("REGISTERED", pilotFund.get("catalogStatus"));
        assertEquals(1247L, pilotFund.get("totalObservations"));
    }
}
