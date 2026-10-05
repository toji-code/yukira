package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Service for deterministic ingestion of historical NAV observations from authoritative AMFI sources.
 *
 * Implements bounded sample loads, bitemporal PIT observation capture,
 * cryptographic source artifact provenance, and data-quality evaluation.
 */
@Service
public class HistoricalNavIngestionService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalNavIngestionService.class);

    private final AmfiSourceClient amfiSourceClient;
    private final AmfiNavIngestionService amfiNavIngestionService;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;

    public record ControlledFundTarget(
        String label,
        String amfiCode,
        String isin,
        String amcMfCode,
        String expectedCategory,
        String planType,
        String optionType
    ) {}

    public record TargetedBackfillFundResult(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        IngestionSummary summary,
        String message
    ) {}

    public record TargetedBackfillResult(
        LocalDate startDate,
        LocalDate endDate,
        int fundsRequested,
        int fundsSucceeded,
        int fundsFailed,
        int totalObservationsIngested,
        int totalDuplicatesSkipped,
        int totalRevisionsCreated,
        List<TargetedBackfillFundResult> funds
    ) {}

    public static final List<ControlledFundTarget> CONTROLLED_SAMPLE_TARGETS = List.of(
        new ControlledFundTarget("Canonical Pilot (Flexi Cap)", "118955", "INF179K01UT0", "9", "Flexi Cap Fund", "DIRECT", "GROWTH"),
        new ControlledFundTarget("Equity Core (Large Cap)", "119018", "INF179K01YV8", "9", "Large Cap Fund", "DIRECT", "GROWTH"),
        new ControlledFundTarget("Small Cap (High Volatility)", "118778", "INF204K01K15", "21", "Small Cap Fund", "DIRECT", "GROWTH"),
        new ControlledFundTarget("Liquid / Debt Fund", "119091", "INF179KB1HP9", "9", "Liquid Fund", "DIRECT", "GROWTH"),
        new ControlledFundTarget("IDCW Variant", "118954", "INF179K01VL5", "9", "Flexi Cap Fund", "DIRECT", "IDCW")
    );

    public HistoricalNavIngestionService(
        AmfiSourceClient amfiSourceClient,
        AmfiNavIngestionService amfiNavIngestionService,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.amfiSourceClient = amfiSourceClient;
        this.amfiNavIngestionService = amfiNavIngestionService;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
    }

    /**
     * Ingests historical NAV observations for a single fund within a bounded date window.
     */
    @Transactional
    public IngestionSummary ingestHistoricalNav(String amfiSchemeCode, String amcMfCode, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching and ingesting historical NAV for AMFI code {} (MF code {}) from {} to {}",
            amfiSchemeCode, amcMfCode, startDate, endDate);

        SourceArtifact artifact = amfiSourceClient.fetchAndPersistArtifact(amfiSchemeCode, amcMfCode, startDate, endDate);
        return amfiNavIngestionService.ingestArtifact(artifact);
    }

    /**
     * Ingests historical NAV observations in 90-day chunked date windows to prevent HTTP timeouts.
     */
    public IngestionSummary ingestHistoricalNavInChunks(String amfiSchemeCode, String amcMfCode, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching and ingesting historical NAV in 90-day chunks for AMFI code {} from {} to {}",
            amfiSchemeCode, startDate, endDate);

        int totalParsed = 0;
        int observationsIngested = 0;
        int duplicateRowsSkipped = 0;
        int revisionsCreated = 0;
        int validationIssuesCreated = 0;
        Long lastArtifactId = null;
        String lastHash = null;
        List<String> allMessages = new ArrayList<>();

        LocalDate currentStart = startDate;
        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = currentStart.plusDays(89);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }

            log.info("Fetching chunk: {} to {} for AMFI code {}", currentStart, currentEnd, amfiSchemeCode);
            SourceArtifact artifact = amfiSourceClient.fetchAndPersistArtifact(amfiSchemeCode, amcMfCode, currentStart, currentEnd);
            log.info("Fetched artifact #{}, size {} bytes. Ingesting observations for {}...", artifact.getId(), artifact.getByteSize(), amfiSchemeCode);
            IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, amfiSchemeCode);
            log.info("Chunk {} to {} completed: ingested={}, skipped={}, revisions={}",
                currentStart, currentEnd, summary.observationsIngested(), summary.duplicateRowsSkipped(), summary.revisionsCreated());

            totalParsed += summary.totalRowsParsed();
            observationsIngested += summary.observationsIngested();
            duplicateRowsSkipped += summary.duplicateRowsSkipped();
            revisionsCreated += summary.revisionsCreated();
            validationIssuesCreated += summary.validationIssuesCreated();
            lastArtifactId = summary.sourceArtifactId();
            lastHash = summary.sha256Hash();
            allMessages.addAll(summary.messages());

            currentStart = currentEnd.plusDays(1);
        }

        return new IngestionSummary(
            lastArtifactId,
            lastHash,
            totalParsed,
            totalParsed,
            observationsIngested,
            revisionsCreated,
            duplicateRowsSkipped,
            validationIssuesCreated,
            allMessages
        );
    }

    /**
     * Ingests only sparse or missing chunks for a known scheme option, preserving restart safety.
     */
    public IngestionSummary ingestHistoricalNavInChunksForSchemeOption(
        Long schemeOptionId,
        String amfiSchemeCode,
        String amcMfCode,
        LocalDate startDate,
        LocalDate endDate
    ) {
        log.info("Fetching and ingesting resumable historical NAV in 90-day chunks for scheme_option_id {} / AMFI code {} from {} to {}",
            schemeOptionId, amfiSchemeCode, startDate, endDate);

        int totalParsed = 0;
        int observationsIngested = 0;
        int duplicateRowsSkipped = 0;
        int revisionsCreated = 0;
        int validationIssuesCreated = 0;
        Long lastArtifactId = null;
        String lastHash = null;
        List<String> allMessages = new ArrayList<>();

        LocalDate currentStart = startDate;
        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = currentStart.plusDays(89);
            if (currentEnd.isAfter(endDate)) {
                currentEnd = endDate;
            }

            int latestObservationCount = countLatestObservationsInWindow(schemeOptionId, currentStart, currentEnd);
            int expectedBusinessDays = countWeekdays(currentStart, currentEnd);
            int minimumCompleteCoverage = Math.max(1, (int) Math.floor(expectedBusinessDays * 0.70));
            if (latestObservationCount >= minimumCompleteCoverage) {
                log.info("Skipping chunk {} to {} for scheme_option_id {}: existing latest observations {} meet resume threshold {}",
                    currentStart, currentEnd, schemeOptionId, latestObservationCount, minimumCompleteCoverage);
                allMessages.add(String.format(
                    "Skipped %s to %s: existing latest observations %d meet resume threshold %d",
                    currentStart, currentEnd, latestObservationCount, minimumCompleteCoverage
                ));
                currentStart = currentEnd.plusDays(1);
                continue;
            }

            log.info("Fetching sparse chunk: {} to {} for AMFI code {} (existing latest observations={}, resume threshold={})",
                currentStart, currentEnd, amfiSchemeCode, latestObservationCount, minimumCompleteCoverage);
            SourceArtifact artifact = amfiSourceClient.fetchAndPersistArtifact(amfiSchemeCode, amcMfCode, currentStart, currentEnd);
            log.info("Fetched artifact #{}, size {} bytes. Ingesting observations for {}...", artifact.getId(), artifact.getByteSize(), amfiSchemeCode);
            IngestionSummary summary = amfiNavIngestionService.ingestArtifact(artifact, amfiSchemeCode);
            log.info("Chunk {} to {} completed: ingested={}, skipped={}, revisions={}",
                currentStart, currentEnd, summary.observationsIngested(), summary.duplicateRowsSkipped(), summary.revisionsCreated());

            totalParsed += summary.totalRowsParsed();
            observationsIngested += summary.observationsIngested();
            duplicateRowsSkipped += summary.duplicateRowsSkipped();
            revisionsCreated += summary.revisionsCreated();
            validationIssuesCreated += summary.validationIssuesCreated();
            lastArtifactId = summary.sourceArtifactId();
            lastHash = summary.sha256Hash();
            allMessages.addAll(summary.messages());

            currentStart = currentEnd.plusDays(1);
        }

        return new IngestionSummary(
            lastArtifactId,
            lastHash,
            totalParsed,
            totalParsed,
            observationsIngested,
            revisionsCreated,
            duplicateRowsSkipped,
            validationIssuesCreated,
            allMessages
        );
    }

    private int countLatestObservationsInWindow(Long schemeOptionId, LocalDate startDate, LocalDate endDate) {
        int count = 0;
        for (NavObservation observation : navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
            schemeOptionId, startDate, endDate
        )) {
            if (Boolean.TRUE.equals(observation.getLatestRevision())) {
                count++;
            }
        }
        return count;
    }

    private int countWeekdays(LocalDate startDate, LocalDate endDate) {
        int count = 0;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            java.time.DayOfWeek day = cursor.getDayOfWeek();
            if (day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    /**
     * Executes a bounded historical NAV backfill for explicit scheme option IDs.
     *
     * This is an ingestion-only path: it fetches authoritative AMFI artifacts and delegates
     * observation persistence to the existing provenance/revision-aware NAV ingestion service.
     */
    public TargetedBackfillResult ingestHistoricalNavForSchemeOptions(
        List<Long> schemeOptionIds,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validateDateWindow(startDate, endDate);
        if (schemeOptionIds == null || schemeOptionIds.isEmpty()) {
            throw new IllegalArgumentException("At least one scheme_option_id is required for targeted NAV backfill");
        }

        List<TargetedBackfillFundResult> results = new ArrayList<>();
        for (Long schemeOptionId : new LinkedHashSet<>(schemeOptionIds)) {
            results.add(ingestHistoricalNavForSchemeOption(schemeOptionId, startDate, endDate));
        }

        int fundsSucceeded = 0;
        int totalObservationsIngested = 0;
        int totalDuplicatesSkipped = 0;
        int totalRevisionsCreated = 0;
        for (TargetedBackfillFundResult result : results) {
            if ("SUCCESS".equals(result.status())) {
                fundsSucceeded++;
            }
            if (result.summary() != null) {
                totalObservationsIngested += result.summary().observationsIngested();
                totalDuplicatesSkipped += result.summary().duplicateRowsSkipped();
                totalRevisionsCreated += result.summary().revisionsCreated();
            }
        }

        return new TargetedBackfillResult(
            startDate,
            endDate,
            results.size(),
            fundsSucceeded,
            results.size() - fundsSucceeded,
            totalObservationsIngested,
            totalDuplicatesSkipped,
            totalRevisionsCreated,
            List.copyOf(results)
        );
    }

    public TargetedBackfillFundResult ingestHistoricalNavForSchemeOption(
        Long schemeOptionId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        validateDateWindow(startDate, endDate);
        if (schemeOptionId == null) {
            return failedTarget(null, null, null, startDate, endDate, "scheme_option_id must not be null");
        }

        Optional<SchemeOption> option = schemeOptionRepository.findById(schemeOptionId);
        if (option.isEmpty()) {
            return failedTarget(schemeOptionId, null, null, startDate, endDate, "Scheme option not found");
        }

        SchemeOption schemeOption = option.get();
        if (schemeOption.getAmfiCode() == null || schemeOption.getAmfiCode().isBlank()) {
            return failedTarget(
                schemeOptionId,
                schemeOption.getAmfiCode(),
                schemeOption.getIsin(),
                startDate,
                endDate,
                "Scheme option has no AMFI scheme code"
            );
        }

        try {
            IngestionSummary summary = ingestHistoricalNavInChunksForSchemeOption(
                schemeOptionId,
                schemeOption.getAmfiCode(),
                null,
                startDate,
                endDate
            );
            return new TargetedBackfillFundResult(
                schemeOptionId,
                schemeOption.getAmfiCode(),
                schemeOption.getIsin(),
                startDate,
                endDate,
                "SUCCESS",
                summary,
                "Historical NAV backfill completed"
            );
        } catch (Exception e) {
            log.error(
                "Targeted historical NAV backfill failed for scheme_option_id {} / AMFI {}: {}",
                schemeOptionId,
                schemeOption.getAmfiCode(),
                e.getMessage()
            );
            return failedTarget(
                schemeOptionId,
                schemeOption.getAmfiCode(),
                schemeOption.getIsin(),
                startDate,
                endDate,
                e.getMessage()
            );
        }
    }

    private void validateDateWindow(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date");
        }
    }

    private TargetedBackfillFundResult failedTarget(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        LocalDate startDate,
        LocalDate endDate,
        String message
    ) {
        return new TargetedBackfillFundResult(
            schemeOptionId,
            amfiCode,
            isin,
            startDate,
            endDate,
            "FAILED",
            null,
            message
        );
    }

    /**
     * Executes the Part D controlled sample historical NAV load across 5 representative funds.
     * Window: 2024-01-01 to 2024-01-15 (canonical baseline window).
     */
    @Transactional
    public Map<String, Object> ingestControlledSample() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 15);

        Map<String, Object> report = new LinkedHashMap<>();
        List<Map<String, Object>> fundSummaries = new ArrayList<>();
        int totalIngested = 0;
        int totalSkipped = 0;

        for (ControlledFundTarget target : CONTROLLED_SAMPLE_TARGETS) {
            Map<String, Object> fundInfo = new LinkedHashMap<>();
            fundInfo.put("label", target.label());
            fundInfo.put("amfiCode", target.amfiCode());
            fundInfo.put("isin", target.isin());
            fundInfo.put("category", target.expectedCategory());
            fundInfo.put("planType", target.planType());
            fundInfo.put("optionType", target.optionType());

            try {
                IngestionSummary summary = ingestHistoricalNav(target.amfiCode(), target.amcMfCode(), startDate, endDate);
                fundInfo.put("status", "SUCCESS");
                fundInfo.put("observationsIngested", summary.observationsIngested());
                fundInfo.put("duplicatesSkipped", summary.duplicateRowsSkipped());
                fundInfo.put("sourceArtifactId", summary.sourceArtifactId());
                fundInfo.put("sourceHash", summary.sha256Hash());

                totalIngested += summary.observationsIngested();
                totalSkipped += summary.duplicateRowsSkipped();

                // Check stored observations
                Optional<SchemeOption> opt = schemeOptionRepository.findByAmfiCode(target.amfiCode());
                if (opt.isPresent()) {
                    List<NavObservation> obs = navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                        opt.get().getId(), startDate, endDate
                    );
                    fundInfo.put("persistedObservationCount", obs.size());
                    if (!obs.isEmpty()) {
                        fundInfo.put("minDate", obs.get(0).getEffectiveDate().toString());
                        fundInfo.put("maxDate", obs.get(obs.size() - 1).getEffectiveDate().toString());
                        fundInfo.put("firstNav", obs.get(0).getNavValue().toPlainString());
                        fundInfo.put("lastNav", obs.get(obs.size() - 1).getNavValue().toPlainString());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to ingest sample NAV for {}: {}", target.label(), e.getMessage());
                fundInfo.put("status", "FAILED");
                fundInfo.put("error", e.getMessage());
            }

            fundSummaries.add(fundInfo);
        }

        report.put("totalFundsAttempted", CONTROLLED_SAMPLE_TARGETS.size());
        report.put("startDate", startDate.toString());
        report.put("endDate", endDate.toString());
        report.put("totalObservationsIngested", totalIngested);
        report.put("totalDuplicatesSkipped", totalSkipped);
        report.put("sampleFunds", fundSummaries);

        return report;
    }

    /**
     * Inspects current database historical NAV coverage for the controlled sample funds.
     */
    public Map<String, Object> getControlledSampleCoverage() {
        Map<String, Object> coverage = new LinkedHashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();

        for (ControlledFundTarget target : CONTROLLED_SAMPLE_TARGETS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", target.label());
            item.put("amfiCode", target.amfiCode());
            item.put("isin", target.isin());
            item.put("category", target.expectedCategory());

            Optional<SchemeOption> opt = schemeOptionRepository.findByAmfiCode(target.amfiCode());
            if (opt.isPresent()) {
                long count = navObservationRepository.countBySchemeOptionId(opt.get().getId());
                item.put("catalogStatus", "REGISTERED");
                item.put("schemeOptionId", opt.get().getId());
                item.put("totalObservations", count);
            } else {
                item.put("catalogStatus", "NOT_IN_CATALOG");
                item.put("totalObservations", 0);
            }
            details.add(item);
        }

        coverage.put("funds", details);
        return coverage;
    }
}
