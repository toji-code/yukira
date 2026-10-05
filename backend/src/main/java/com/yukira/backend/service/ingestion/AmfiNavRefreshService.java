package com.yukira.backend.service.ingestion;

import com.yukira.backend.domain.entity.DataSource;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.ingestion.AmfiDataFreshnessDto;
import com.yukira.backend.dto.ingestion.AmfiNavRefreshResultDto;
import com.yukira.backend.ingestion.amfi.AmfiNavIngestionService;
import com.yukira.backend.ingestion.amfi.AmfiSourceClient;
import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.IngestionSummary;
import com.yukira.backend.ingestion.amfi.UniverseIngestionSummary;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AmfiNavRefreshService {

    private static final Logger log = LoggerFactory.getLogger(AmfiNavRefreshService.class);

    private final AmfiSourceClient amfiSourceClient;
    private final AmfiUniverseIngestionService universeIngestionService;
    private final AmfiNavIngestionService navIngestionService;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final DataSourceRepository dataSourceRepository;
    private final AmfiDataFreshnessService dataFreshnessService;

    public AmfiNavRefreshService(
        AmfiSourceClient amfiSourceClient,
        AmfiUniverseIngestionService universeIngestionService,
        AmfiNavIngestionService navIngestionService,
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository,
        AmfiDataFreshnessService dataFreshnessService
    ) {
        this.amfiSourceClient = amfiSourceClient;
        this.universeIngestionService = universeIngestionService;
        this.navIngestionService = navIngestionService;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dataFreshnessService = dataFreshnessService;
    }

    /**
     * Executes the production NAV refresh workflow against official AMFI daily NAVAll.txt feed.
     *
     * Workflow:
     * FETCH → STORE SOURCE ARTIFACT → HASH → PARSE → VALIDATE → RESOLVE SCHEME IDENTITY → UPSERT/INSERT OBSERVATIONS → REVISION HANDLING → FRESHNESS
     *
     * Invariants:
     * - Idempotent: re-running against identical SHA-256 source artifact skips redundant parsing.
     * - Quant Engine Isolation: Does NOT recalculate scores or invoke analytical calculation orchestrator.
     */
    @Transactional
    public AmfiNavRefreshResultDto refreshLatestNav() {
        log.info("Starting production AMFI NAV refresh workflow from official portal feed...");
        List<String> messages = new ArrayList<>();

        SourceArtifact artifact;
        try {
            artifact = amfiSourceClient.fetchAndPersistUniverseCatalogArtifact();
        } catch (Exception e) {
            log.error("Failed to retrieve source artifact from AMFI portal: {}", e.getMessage(), e);
            AmfiDataFreshnessDto currentFreshness = dataFreshnessService.getFreshnessStatus();
            messages.add("AMFI source fetch failed: " + e.getMessage() + ". Preserved last valid dataset.");
            return new AmfiNavRefreshResultDto(
                "FAILED",
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                currentFreshness,
                messages
            );
        }

        return processArtifactRefresh(artifact, messages);
    }

    /**
     * Ingests a raw AMFI text payload byte array (used for test fixtures or offline ingestion).
     */
    @Transactional
    public AmfiNavRefreshResultDto refreshFromPayload(byte[] payloadBytes, String sourceUri) {
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("Payload bytes must not be null or empty");
        }

        String sha256 = AmfiSourceClient.computeSha256(payloadBytes);
        Optional<SourceArtifact> existingOpt = sourceArtifactRepository.findBySha256Hash(sha256);

        SourceArtifact artifact;
        if (existingOpt.isPresent()) {
            artifact = existingOpt.get();
        } else {
            DataSource ds = dataSourceRepository.findByCode("AMFI_PORTAL")
                .orElseGet(() -> dataSourceRepository.save(new DataSource("AMFI_PORTAL", "AMFI NAV Historical Portal", "AMFI")));
            artifact = new SourceArtifact(
                ds,
                OffsetDateTime.now(),
                "UNIVERSE_CATALOG_TEXT",
                sha256,
                (long) payloadBytes.length
            );
            artifact.setStorageUri(sourceUri != null ? sourceUri : "amfi://catalog/" + sha256);
            artifact.setPayloadBlob(payloadBytes);
            artifact = sourceArtifactRepository.save(artifact);
        }

        List<String> messages = new ArrayList<>();
        return processArtifactRefresh(artifact, messages);
    }

    /**
     * Ingests a historical single-scheme NAV artifact.
     */
    @Transactional
    public AmfiNavRefreshResultDto refreshFromHistoricalArtifact(SourceArtifact artifact) {
        List<String> messages = new ArrayList<>();
        IngestionSummary summary = navIngestionService.ingestArtifact(artifact);
        AmfiDataFreshnessDto freshness = dataFreshnessService.getFreshnessStatus();

        messages.addAll(summary.messages());
        return new AmfiNavRefreshResultDto(
            "SUCCESS",
            summary.sourceArtifactId(),
            summary.sha256Hash(),
            summary.totalRowsParsed(),
            summary.validRowsCount(),
            summary.observationsIngested(),
            summary.revisionsCreated(),
            summary.duplicateRowsSkipped(),
            summary.validationIssuesCreated(),
            freshness,
            messages
        );
    }

    private AmfiNavRefreshResultDto processArtifactRefresh(SourceArtifact artifact, List<String> messages) {
        // First ensure universe catalog (AMC, Scheme, SchemePlan, SchemeOption) is updated
        UniverseIngestionSummary universeSummary = universeIngestionService.ingestUniverseArtifact(artifact);
        
        // Next ingest bitemporal NAV observations into nav_observation ledger
        IngestionSummary navSummary = navIngestionService.ingestArtifact(artifact);
        
        AmfiDataFreshnessDto freshness = dataFreshnessService.getFreshnessStatus();

        messages.addAll(navSummary.messages());
        messages.add(String.format("Ingested universe catalog (%d scheme options) & %d NAV observations (%d revisions, %d duplicates skipped) from artifact #%d",
            universeSummary.optionsRegistered(), navSummary.observationsIngested(), navSummary.revisionsCreated(), navSummary.duplicateRowsSkipped(), artifact.getId()));

        return new AmfiNavRefreshResultDto(
            "SUCCESS",
            artifact.getId(),
            artifact.getSha256Hash(),
            navSummary.totalRowsParsed(),
            navSummary.validRowsCount(),
            navSummary.observationsIngested(),
            navSummary.revisionsCreated(),
            navSummary.duplicateRowsSkipped(),
            navSummary.validationIssuesCreated(),
            freshness,
            messages
        );
    }
}
