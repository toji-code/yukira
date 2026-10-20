package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Execution job orchestrating the end-to-end production monthly holdings ingestion workflow:
 *
 * SOURCE DESCRIPTOR → HTTP ACQUIRE → IMMUTABLE ARTIFACT → PARSE → IDENTITY RESOLUTION → SNAPSHOT PERSISTENCE → RESULT REPORT
 *
 * Epistemic Rules:
 * 1. Safe Boundary: Network outages, invalid HTML walls, or unsupported binary formats immediately return explicit machine-readable failure statuses.
 * 2. Immutable Provenance: Raw bytes are preserved in SourceArtifact before parsing; duplicate SHA-256 hashes bypass redundant processing.
 * 3. Zero Partial Mutations: If scheme identity cannot be resolved or source fetch fails, no portfolio snapshots or holdings observations are created.
 */
@Service
@SuppressWarnings("null")
public class MonthlyHoldingsIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyHoldingsIngestionJob.class);

    private final SchemeOptionRepository schemeOptionRepository;
    private final AuthoritativeHoldingsSourceFetcher sourceFetcher;
    private final MonthlyHoldingsIngestionService holdingsIngestionService;

    public MonthlyHoldingsIngestionJob(
        SchemeOptionRepository schemeOptionRepository,
        AuthoritativeHoldingsSourceFetcher sourceFetcher,
        MonthlyHoldingsIngestionService holdingsIngestionService
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.sourceFetcher = sourceFetcher;
        this.holdingsIngestionService = holdingsIngestionService;
    }

    /**
     * Executes the monthly holdings ingestion workflow for a given source descriptor.
     */
    @Transactional
    public MonthlyHoldingsAcquisitionResult execute(MonthlyHoldingsSourceDescriptor descriptor) {
        OffsetDateTime startTime = OffsetDateTime.now();
        List<String> messages = new ArrayList<>();

        if (descriptor == null) {
            return buildErrorResult(descriptor, MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, "MonthlyHoldingsSourceDescriptor must not be null", null, startTime, messages);
        }

        // 1. Resolve Scheme Identity
        Optional<SchemeOption> optionOpt = resolveSchemeOption(descriptor);
        if (optionOpt.isEmpty()) {
            String unresolvedMsg = String.format("Target SchemeOption not found in database for AMFI code '%s' or Option ID %s",
                descriptor.amfiCode(), descriptor.targetSchemeOptionId());
            log.warn("MonthlyHoldingsIngestionJob rejected: {}", unresolvedMsg);
            messages.add(unresolvedMsg);
            return buildErrorResult(descriptor, MonthlyHoldingsAcquisitionResult.Status.SCHEME_UNRESOLVED, unresolvedMsg, null, startTime, messages);
        }

        SchemeOption targetOption = optionOpt.get();
        messages.add(String.format("Resolved target scheme option #%d (AMFI: %s, ISIN: %s)", targetOption.getId(), targetOption.getAmfiCode(), targetOption.getIsin()));

        // 2. Controlled HTTP / Byte Source Acquisition
        AuthoritativeHoldingsSourceFetcher.FetchResult fetchResult = sourceFetcher.acquireArtifact(descriptor);

        if (!fetchResult.isSuccess()) {
            log.warn("MonthlyHoldingsIngestionJob acquisition failed for {}: [{}] {}", descriptor.sourceUrl(), fetchResult.status(), fetchResult.statusMessage());
            messages.add(fetchResult.statusMessage());
            return buildErrorResult(
                descriptor,
                fetchResult.status(),
                fetchResult.statusMessage(),
                fetchResult.httpStatusCode(),
                startTime,
                messages
            );
        }

        messages.add(String.format("Successfully acquired SourceArtifact #%d (SHA-256: %s, Size: %d bytes)",
            fetchResult.artifact().getId(), fetchResult.artifact().getSha256Hash(), fetchResult.artifact().getByteSize()));

        // 3. Ingestion & Snapshot Persistence
        LocalDate portfolioDate = descriptor.explicitReportingDate();

        try {
            HoldingsIngestionSummary summary = holdingsIngestionService.ingestArtifact(targetOption.getId(), portfolioDate, fetchResult.artifact(), descriptor);
            messages.addAll(summary.messages());

            MonthlyHoldingsAcquisitionResult.Status finalStatus = MonthlyHoldingsAcquisitionResult.Status.INGESTED;
            if (summary.revisionsCreated() > 0) {
                finalStatus = MonthlyHoldingsAcquisitionResult.Status.RESTATED_REVISION;
            } else if (summary.holdingsIngested() == 0 && summary.messages().stream().anyMatch(m -> m.contains("skipping redundant"))) {
                finalStatus = MonthlyHoldingsAcquisitionResult.Status.DUPLICATE;
            }

            return new MonthlyHoldingsAcquisitionResult(
                descriptor,
                finalStatus,
                "Holdings ingestion completed successfully",
                200,
                fetchResult.artifact().getId(),
                fetchResult.artifact().getSha256Hash(),
                fetchResult.artifact().getByteSize(),
                summary.portfolioDate(),
                targetOption.getId(),
                null,
                summary.revisionsCreated() > 0 ? 2 : 1,
                summary.totalRowsParsed(),
                summary.validRowsParsed(),
                summary.holdingsIngested(),
                summary.sumReportedWeights(),
                startTime,
                messages
            );

        } catch (Exception e) {
            log.error("Parsing/Ingestion failed for artifact #{}: {}", fetchResult.artifact().getId(), e.getMessage());
            messages.add("Ingestion failed: " + e.getMessage());
            return buildErrorResult(descriptor, MonthlyHoldingsAcquisitionResult.Status.PARSE_FAILED, e.getMessage(), 200, startTime, messages);
        }
    }

    /**
     * Executes the monthly holdings ingestion workflow using direct raw payload bytes (e.g. for offline verification or test fixtures).
     */
    @Transactional
    public MonthlyHoldingsAcquisitionResult executeWithRawPayload(MonthlyHoldingsSourceDescriptor descriptor, byte[] rawBytes) {
        OffsetDateTime startTime = OffsetDateTime.now();
        List<String> messages = new ArrayList<>();

        if (descriptor == null || rawBytes == null) {
            return buildErrorResult(descriptor, MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, "Descriptor and rawBytes must not be null", null, startTime, messages);
        }

        Optional<SchemeOption> optionOpt = resolveSchemeOption(descriptor);
        if (optionOpt.isEmpty()) {
            String unresolvedMsg = String.format("Target SchemeOption not found in database for AMFI code '%s' or Option ID %s",
                descriptor.amfiCode(), descriptor.targetSchemeOptionId());
            messages.add(unresolvedMsg);
            return buildErrorResult(descriptor, MonthlyHoldingsAcquisitionResult.Status.SCHEME_UNRESOLVED, unresolvedMsg, null, startTime, messages);
        }

        SchemeOption targetOption = optionOpt.get();
        AuthoritativeHoldingsSourceFetcher.FetchResult fetchResult = sourceFetcher.processRawBytes(rawBytes, descriptor.sourceUrl(), descriptor);

        if (!fetchResult.isSuccess()) {
            messages.add(fetchResult.statusMessage());
            return buildErrorResult(descriptor, fetchResult.status(), fetchResult.statusMessage(), fetchResult.httpStatusCode(), startTime, messages);
        }

        LocalDate portfolioDate = descriptor.explicitReportingDate();
        HoldingsIngestionSummary summary = holdingsIngestionService.ingestArtifact(targetOption.getId(), portfolioDate, fetchResult.artifact(), descriptor);
        messages.addAll(summary.messages());

        MonthlyHoldingsAcquisitionResult.Status finalStatus = MonthlyHoldingsAcquisitionResult.Status.INGESTED;
        if (summary.revisionsCreated() > 0) {
            finalStatus = MonthlyHoldingsAcquisitionResult.Status.RESTATED_REVISION;
        } else if (summary.holdingsIngested() == 0 && summary.messages().stream().anyMatch(m -> m.contains("skipping redundant"))) {
            finalStatus = MonthlyHoldingsAcquisitionResult.Status.DUPLICATE;
        }

        return new MonthlyHoldingsAcquisitionResult(
            descriptor,
            finalStatus,
            "Holdings ingestion completed successfully",
            200,
            fetchResult.artifact().getId(),
            fetchResult.artifact().getSha256Hash(),
            fetchResult.artifact().getByteSize(),
            summary.portfolioDate(),
            targetOption.getId(),
            null,
            summary.revisionsCreated() > 0 ? 2 : 1,
            summary.totalRowsParsed(),
            summary.validRowsParsed(),
            summary.holdingsIngested(),
            summary.sumReportedWeights(),
            startTime,
            messages
        );
    }

    private Optional<SchemeOption> resolveSchemeOption(MonthlyHoldingsSourceDescriptor descriptor) {
        if (descriptor.targetSchemeOptionId() != null) {
            Optional<SchemeOption> byId = schemeOptionRepository.findById(descriptor.targetSchemeOptionId());
            if (byId.isPresent()) return byId;
        }
        if (descriptor.amfiCode() != null && !descriptor.amfiCode().isBlank()) {
            return schemeOptionRepository.findByAmfiCode(descriptor.amfiCode().trim());
        }
        return Optional.empty();
    }

    private MonthlyHoldingsAcquisitionResult buildErrorResult(
        MonthlyHoldingsSourceDescriptor descriptor,
        MonthlyHoldingsAcquisitionResult.Status status,
        String message,
        Integer httpStatusCode,
        OffsetDateTime startTime,
        List<String> messages
    ) {
        return new MonthlyHoldingsAcquisitionResult(
            descriptor,
            status,
            message,
            httpStatusCode,
            null,
            null,
            null,
            descriptor != null ? descriptor.explicitReportingDate() : null,
            descriptor != null ? descriptor.targetSchemeOptionId() : null,
            null,
            0,
            0,
            0,
            0,
            null,
            startTime,
            messages
        );
    }
}
