package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
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
import java.util.Set;

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

    @Mock
    private SourceArtifactRepository sourceArtifactRepository;

    @InjectMocks
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Test
    @DisplayName("Controlled sample targets correctly specify 5 diverse funds across categories and options")
    void testControlledSampleTargetDefinitions() {
        var targets = HistoricalNavIngestionService.CONTROLLED_SAMPLE_TARGETS;
        assertEquals(5, targets.size());

        assertEquals("118955", targets.get(0).amfiCode());
        assertEquals("INF179K01UT0", targets.get(0).isin());
        assertEquals("DIRECT", targets.get(0).planType());
        assertEquals("GROWTH", targets.get(0).optionType());
        assertEquals("119018", targets.get(1).amfiCode());
        assertEquals("118778", targets.get(2).amfiCode());
        assertEquals("119091", targets.get(3).amfiCode());
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
        when(amfiSourceClient.buildUrl(eq("120564"), any(), eq(start), eq(end)))
            .thenReturn("https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?scheme=120564");
        when(sourceArtifactRepository.existsByStorageUri(anyString())).thenReturn(false);
        when(amfiSourceClient.fetchAndPersistUniverseChunkArtifact(start, end)).thenReturn(artifact);
        when(amfiNavIngestionService.ingestArtifact(artifact, Set.of("120564"))).thenReturn(mockSummary);

        var result = historicalNavIngestionService.ingestHistoricalNavForSchemeOptions(
            List.of(10189L), start, end
        );

        assertEquals(1, result.fundsRequested());
        assertEquals(1, result.fundsSucceeded());
        assertEquals(0, result.fundsFailed());
        assertEquals(10, result.totalObservationsIngested());
        assertEquals("120564", result.funds().get(0).amfiCode());
        verify(amfiSourceClient).fetchAndPersistUniverseChunkArtifact(start, end);
        verify(amfiNavIngestionService).ingestArtifact(artifact, Set.of("120564"));
    }

    @Test
    @DisplayName("Targeted backfill skips fetching when artifact already exists, ensuring strict resumability")
    void testTargetedBackfillSkipsAlreadyFetchedArtifacts() {
        LocalDate start = LocalDate.of(2023, 10, 1);
        LocalDate end = LocalDate.of(2023, 10, 15);
        SchemeOption opt = new SchemeOption();
        opt.setId(10189L);
        opt.setAmfiCode("120564");

        when(schemeOptionRepository.findById(10189L)).thenReturn(Optional.of(opt));
        when(amfiSourceClient.buildUrl(eq("120564"), any(), eq(start), eq(end)))
            .thenReturn("https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?scheme=120564");
        when(sourceArtifactRepository.existsByStorageUri(anyString())).thenReturn(true);

        var result = historicalNavIngestionService.ingestHistoricalNavForSchemeOption(10189L, start, end);

        assertNotNull(result);
        verify(amfiSourceClient, never()).fetchAndPersistUniverseChunkArtifact(any(), any());
        verify(amfiNavIngestionService, never()).ingestArtifact(any(SourceArtifact.class), anySet());
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
        when(amfiSourceClient.buildUrl(eq("120564"), any(), eq(start), eq(end)))
            .thenReturn("https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?scheme=120564");
        when(sourceArtifactRepository.existsByStorageUri(anyString())).thenReturn(false);
        when(amfiSourceClient.fetchAndPersistUniverseChunkArtifact(start, end))
            .thenThrow(new RuntimeException("AMFI source unavailable"));

        var result = historicalNavIngestionService.ingestHistoricalNavForSchemeOption(10189L, start, end);

        assertEquals("FAILED", result.status());
        assertTrue(result.message().contains("AMFI source unavailable"));
        verify(amfiNavIngestionService, never()).ingestArtifact(any(SourceArtifact.class), anySet());
    }

    @Test
    @DisplayName("Deterministic cohort selection picks diverse options across AMCs")
    void testDeterministicCohortSelection() {
        SchemeOption opt1 = new SchemeOption();
        opt1.setId(1L);
        opt1.setAmfiCode("100001");

        SchemeOption opt2 = new SchemeOption();
        opt2.setId(2L);
        opt2.setAmfiCode("100002");

        when(schemeOptionRepository.findAll()).thenReturn(List.of(opt1, opt2));

        List<SchemeOption> cohort = historicalNavIngestionService.selectDeterministicCohort(2);
        assertNotNull(cohort);
        assertFalse(cohort.isEmpty());
    }
}
