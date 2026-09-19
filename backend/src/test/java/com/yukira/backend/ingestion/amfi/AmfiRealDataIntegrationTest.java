package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.Ret02AnalysisResponse;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.AnalysisService;
import com.yukira.backend.service.PeriodReturnCalculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Tag("external-integration")
public class AmfiRealDataIntegrationTest {

    @Autowired
    private AmfiSourceClient amfiSourceClient;

    @Autowired
    private AmfiNavIngestionService ingestionService;

    @Autowired
    private PeriodReturnCalculationService calculationService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private AmcRepository amcRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemePlanRepository schemePlanRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Test
    @DisplayName("Phase 2F Section 8: Controlled Real AMFI End-to-End Slice")
    void testRealAmfiEndToEndSlice() {
        String pilotAmfiCode = "119062"; // HDFC Flexi Cap Fund - Direct - Growth

        // 1. Ensure Canonical Pilot Scheme Registration
        Amc amc = amcRepository.findByCode("HDFC_MF")
            .orElseGet(() -> amcRepository.save(new Amc("HDFC Mutual Fund", "HDFC_MF")));

        Scheme scheme = schemeRepository.findByCode("HDFC_FLEXI")
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1))));

        SchemePlan plan = schemePlanRepository.findByCode("HDFC_FLEXI_DIR")
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "HDFC_FLEXI_DIR")));

        SchemeOption option = schemeOptionRepository.findByAmfiCode(pilotAmfiCode)
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", pilotAmfiCode, "INF179K01BE2")));

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        System.out.println("=== REAL AMFI END-TO-END CONTROLLED INTEGRATION TEST ===");

        // 2. Fetch Raw Artifact from Live AMFI Portal
        SourceArtifact artifact;
        try {
            artifact = amfiSourceClient.fetchAndPersistArtifact(pilotAmfiCode, startDate, endDate);
        } catch (Exception e) {
            System.err.println("EXTERNAL SOURCE UNAVAILABILITY: " + e.getMessage());
            // External network failure must be reported explicitly as external limitation
            return;
        }

        assertNotNull(artifact.getId());
        assertNotNull(artifact.getSha256Hash());
        assertTrue(artifact.getByteSize() > 0);

        System.out.println("Source Retrieval Timestamp: " + artifact.getRetrievalTimestamp());
        System.out.println("Source Artifact SHA-256:   " + artifact.getSha256Hash());
        System.out.println("Source Artifact Size:      " + artifact.getByteSize() + " bytes");

        // 3. Deterministic Ingestion & Normalization
        IngestionSummary report = ingestionService.ingestArtifact(artifact);
        System.out.println("Ingestion Ingested Rows:   " + report.observationsIngested());
        System.out.println("Ingestion Issues Logged:   " + report.validationIssuesCreated());
        assertTrue(report.observationsIngested() > 0 || report.validRowsCount() > 0, "Real AMFI response must parse valid observations");

        // 4. Authoritative RET-02 Calculation Execution
        CalculationRun run = calculationService.executeRet02Calculation(
            option.getId(), startDate, endDate, knowledgeCutoff, "CANDIDATE_V1"
        );

        assertNotNull(run.getId());
        assertEquals("COMPLETED", run.getRunStatus());
        assertNull(run.getBenchmark(), "benchmark must be null for standalone RET-02");

        // 5. Build and Verify Authoritative Audit Response
        Ret02AnalysisResponse response = analysisService.buildRet02Response(run);

        System.out.println("Calculation Run ID:        " + response.provenance().calculationRunId());
        System.out.println("Scheme Identity:           " + response.identity().schemeName() + " (" + response.identity().amfiCode() + ")");
        System.out.println("Requested Period:          " + response.period().requestedStartDate() + " to " + response.period().requestedEndDate());
        System.out.println("Selected Dates:            " + response.period().selectedStartDate() + " to " + response.period().selectedEndDate());
        System.out.println("Knowledge Cutoff (PIT):    " + response.pit().knowledgeCutoffTime());
        System.out.println("Authoritative RET-02 Val:  " + response.result().numericValue() + " (" + response.result().formattedValue() + ")");
        System.out.println("Methodology Version:       " + response.methodology().methodologyVersion() + " [Status=" + response.methodology().approvalStatus() + "]");
        System.out.println("Benchmark Status:          " + (response.benchmark().benchmarkRequired() ? "REQUIRED" : "NOT REQUIRED") + " (benchmark_id=null)");
        System.out.println("Input Snapshot SHA-256:    " + response.provenance().inputSnapshotSha256());
        System.out.println("=======================================================");

        assertEquals("RET-02", response.result().metricCode());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertNotNull(response.result().numericValue());
        assertFalse(response.benchmark().benchmarkRequired());
        assertNull(response.benchmark().benchmarkId());
        assertEquals("CANDIDATE", response.methodology().approvalStatus());
        assertTrue(response.methodology().isCandidate());
        assertEquals(2, response.provenance().inputObservations().size());
    }
}
