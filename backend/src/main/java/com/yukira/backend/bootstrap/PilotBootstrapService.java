package com.yukira.backend.bootstrap;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.Ret02AnalysisResponse;
import com.yukira.backend.dto.analysis.Ret02CalculationRequest;
import com.yukira.backend.ingestion.amfi.AmfiNavIngestionService;
import com.yukira.backend.ingestion.amfi.AmfiSourceClient;
import com.yukira.backend.ingestion.amfi.IngestionSummary;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Intentional, non-destructive development bootstrap service for the canonical pilot instrument:
 * HDFC Flexi Cap Fund - Direct Plan - Growth Option (AMFI 118955 / ISIN INF179K01UT0).
 *
 * Provides fully idempotent, reproducible master data registration, authoritative AMFI ingestion,
 * and baseline RET-02 calculation run execution.
 *
 * Contains ZERO destructive operations (no DELETE FROM, no TRUNCATE).
 */
@Service
public class PilotBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(PilotBootstrapService.class);

    public static final String PILOT_AMC_CODE = "HDFC_MF";
    public static final String PILOT_AMC_NAME = "HDFC Mutual Fund";
    public static final String PILOT_SCHEME_CODE = "HDFC_FLEXI";
    public static final String PILOT_SCHEME_NAME = "HDFC Flexi Cap Fund";
    public static final String PILOT_PLAN_CODE = "HDFC_FLEXI_DIR";
    public static final String PILOT_AMFI_CODE = "118955";
    public static final String PILOT_ISIN = "INF179K01UT0";

    public static final LocalDate PILOT_START_DATE = LocalDate.of(2024, 1, 1);
    public static final LocalDate PILOT_END_DATE = LocalDate.of(2024, 1, 15);
    public static final OffsetDateTime PILOT_KNOWLEDGE_CUTOFF = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

    private final AmcRepository amcRepository;
    private final SchemeRepository schemeRepository;
    private final SchemePlanRepository schemePlanRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final SourceArtifactRepository sourceArtifactRepository;
    private final AmfiSourceClient amfiSourceClient;
    private final AmfiNavIngestionService ingestionService;
    private final AnalysisService analysisService;

    public PilotBootstrapService(
        AmcRepository amcRepository,
        SchemeRepository schemeRepository,
        SchemePlanRepository schemePlanRepository,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        CalculationRunRepository calculationRunRepository,
        SourceArtifactRepository sourceArtifactRepository,
        AmfiSourceClient amfiSourceClient,
        AmfiNavIngestionService ingestionService,
        AnalysisService analysisService
    ) {
        this.amcRepository = amcRepository;
        this.schemeRepository = schemeRepository;
        this.schemePlanRepository = schemePlanRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.amfiSourceClient = amfiSourceClient;
        this.ingestionService = ingestionService;
        this.analysisService = analysisService;
    }

    /**
     * Idempotently ensures the canonical master hierarchy exists in the database.
     */
    @Transactional
    public SchemeOption ensureCanonicalPilotMaster() {
        Amc amc = amcRepository.findByCode(PILOT_AMC_CODE)
            .orElseGet(() -> amcRepository.save(new Amc(PILOT_AMC_NAME, PILOT_AMC_CODE)));

        Scheme scheme = schemeRepository.findByCode(PILOT_SCHEME_CODE)
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, PILOT_SCHEME_NAME, PILOT_SCHEME_CODE, LocalDate.of(1995, 1, 1))));

        SchemePlan plan = schemePlanRepository.findByCode(PILOT_PLAN_CODE)
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", PILOT_PLAN_CODE)));

        return schemeOptionRepository.findByAmfiCode(PILOT_AMFI_CODE)
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", PILOT_AMFI_CODE, PILOT_ISIN)));
    }

    /**
     * Executes the complete, reproducible pilot bootstrap sequence:
     * 1. Register canonical master entities.
     * 2. Ingest authoritative AMFI source records (idempotent deduplication).
     * 3. Execute baseline RET-02 calculation run.
     */
    @Transactional
    public BootstrapReport bootstrapPilot() {
        log.info("Starting intentional pilot data bootstrap for {} ({})", PILOT_SCHEME_NAME, PILOT_AMFI_CODE);

        // 1. Ensure master identity
        SchemeOption option = ensureCanonicalPilotMaster();
        log.info("Canonical master identity verified: Option ID #{}, AMFI {}, ISIN {}", option.getId(), option.getAmfiCode(), option.getIsin());

        // 2. Check existing observations
        List<NavObservation> existingStart = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), PILOT_START_DATE);
        List<NavObservation> existingEnd = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), PILOT_END_DATE);

        int ingestedRows = 0;
        if (existingStart.isEmpty() || existingEnd.isEmpty()) {
            SourceArtifact artifact = resolveOrFetchSourceArtifact();
            IngestionSummary summary = ingestionService.ingestArtifact(artifact);
            ingestedRows = summary.observationsIngested();
            log.info("AMFI ingestion completed: {} observations ingested, {} issues logged", ingestedRows, summary.validationIssuesCreated());
        } else {
            log.info("Observations for pilot date range already present in ledger; skipping redundant ingestion.");
        }

        // 3. Baseline calculation run execution
        List<CalculationRun> runs = calculationRunRepository.findBySchemeOptionId(option.getId());
        Long calculationRunId = null;
        Double ret02Value = null;

        if (runs.isEmpty()) {
            Ret02CalculationRequest req = new Ret02CalculationRequest(
                option.getId(),
                PILOT_START_DATE,
                PILOT_END_DATE,
                PILOT_KNOWLEDGE_CUTOFF,
                "CANDIDATE_V1"
            );
            Ret02AnalysisResponse resp = analysisService.executeRet02Analysis(req);
            calculationRunId = resp.provenance().calculationRunId();
            ret02Value = resp.result().numericValue() != null ? resp.result().numericValue().doubleValue() : null;
            log.info("Executed baseline RET-02 calculation run #{}: result = {}", calculationRunId, resp.result().formattedValue());
        } else {
            CalculationRun latestRun = runs.get(runs.size() - 1);
            calculationRunId = latestRun.getId();
            log.info("Existing calculation run #{} already available for pilot option.", calculationRunId);
        }

        return new BootstrapReport(
            option.getId(),
            option.getAmfiCode(),
            option.getIsin(),
            PILOT_SCHEME_NAME,
            ingestedRows,
            calculationRunId,
            ret02Value
        );
    }

    private SourceArtifact resolveOrFetchSourceArtifact() {
        try {
            return amfiSourceClient.fetchAndPersistArtifact(PILOT_AMFI_CODE, PILOT_START_DATE, PILOT_END_DATE);
        } catch (Exception e) {
            log.warn("Direct live AMFI fetch encountered exception ({}). Searching local preserved source artifacts.", e.getMessage());
            return sourceArtifactRepository.findAll().stream()
                .filter(a -> a.getPayloadBlob() != null && a.getPayloadBlob().length > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No live AMFI connection and no preserved SourceArtifact available to bootstrap pilot."));
        }
    }

    public record BootstrapReport(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String schemeName,
        int newObservationsIngested,
        Long calculationRunId,
        Double ret02Value
    ) {}
}
