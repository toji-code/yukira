package com.yukira.backend.ingestion.fbil;

import com.yukira.backend.domain.entity.RiskFreeObservation;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.domain.entity.ValidationIssue;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.repository.ValidationIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@SuppressWarnings("null")
public class FbilTBillIngestionService {

    private final FbilSourceClient sourceClient;
    private final FbilTBillParser parser;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final ValidationIssueRepository validationIssueRepository;

    public FbilTBillIngestionService(
        FbilSourceClient sourceClient,
        FbilTBillParser parser,
        RiskFreeObservationRepository riskFreeObservationRepository,
        ValidationIssueRepository validationIssueRepository
    ) {
        this.sourceClient = sourceClient;
        this.parser = parser;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.validationIssueRepository = validationIssueRepository;
    }

    public record FbilIngestionSummary(
        int totalParsed,
        int insertedCount,
        int skippedCount,
        int revisedCount,
        Long sourceArtifactId,
        String payloadSha256
    ) {}

    @Transactional
    public FbilIngestionSummary ingestRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        SourceArtifact artifact = sourceClient.fetchAndPersistArtifact(startDate, endDate);
        return ingestPayload(artifact.getStorageUri(), artifact.getPayloadBlob());
    }

    /**
     * Ingests FBIL Treasury Bill payload bytes deterministically.
     * Guarantees idempotency, revision tracking, conflict detection, and immutable provenance.
     */
    @Transactional
    public FbilIngestionSummary ingestPayload(String sourceUri, byte[] payloadBytes) {
        return ingestPayload("FBIL_91D_TBILL", sourceUri, payloadBytes);
    }

    @Transactional
    public FbilIngestionSummary ingestPayload(String benchmarkCode, String sourceUri, byte[] payloadBytes) {
        if (sourceUri == null || sourceUri.isBlank()) {
            sourceUri = "https://www.fbil.org.in/money-market/tbill-curve";
        }
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("payloadBytes must not be empty");
        }

        // 1. Persist or retrieve cryptographically immutable SourceArtifact
        SourceArtifact artifact = sourceClient.createOrGetSourceArtifact(sourceUri, payloadBytes);

        // 2. Deterministically parse records
        List<FbilTBillRecord> records = parser.parse(payloadBytes, benchmarkCode);

        int inserted = 0;
        int skipped = 0;
        int revised = 0;

        // 3. Process each record
        for (FbilTBillRecord record : records) {
            List<RiskFreeObservation> existingRevisions = riskFreeObservationRepository
                .findByBenchmarkCodeAndEffectiveDate(record.benchmarkCode(), record.effectiveDate());

            if (existingRevisions.isEmpty()) {
                // First observation for this date
                RiskFreeObservation obs = new RiskFreeObservation(
                    record.benchmarkCode(),
                    record.effectiveDate(),
                    record.quotedYield(),
                    record.daycountConvention(),
                    1,
                    record.availabilityTime()
                );
                obs.setSourceArtifact(artifact);
                obs.setQualityAssessment("VALID");
                obs.setVerificationStatus("VERIFIED");
                obs.setRevisionStatus("ORIGINAL");
                obs.setTemporalStatus("CURRENT");
                obs.setPresenceStatus("AVAILABLE");
                obs.setIntegrityCondition("NONE");

                riskFreeObservationRepository.save(obs);
                inserted++;
            } else {
                // Check if identical observation exists
                boolean exactMatch = existingRevisions.stream().anyMatch(
                    e -> e.getQuotedYield().compareTo(record.quotedYield()) == 0
                        && e.getAvailabilityTime().equals(record.availabilityTime())
                );

                if (exactMatch) {
                    skipped++;
                    continue;
                }

                // Check latest revision
                RiskFreeObservation latest = existingRevisions.stream()
                    .filter(RiskFreeObservation::getLatestRevision)
                    .findFirst()
                    .orElse(existingRevisions.get(existingRevisions.size() - 1));

                if (record.availabilityTime().isAfter(latest.getAvailabilityTime())) {
                    // Legitimate retroactive revision with later availability time
                    latest.setLatestRevision(false);
                    riskFreeObservationRepository.save(latest);

                    int nextSeq = existingRevisions.stream()
                        .mapToInt(RiskFreeObservation::getRevisionSeq)
                        .max()
                        .orElse(1) + 1;

                    RiskFreeObservation revisedObs = new RiskFreeObservation(
                        record.benchmarkCode(),
                        record.effectiveDate(),
                        record.quotedYield(),
                        record.daycountConvention(),
                        nextSeq,
                        record.availabilityTime()
                    );
                    revisedObs.setSourceArtifact(artifact);
                    revisedObs.setQualityAssessment("VALID");
                    revisedObs.setVerificationStatus("VERIFIED");
                    revisedObs.setRevisionStatus("REVISED");
                    revisedObs.setTemporalStatus("CURRENT");
                    revisedObs.setPresenceStatus("AVAILABLE");
                    revisedObs.setIntegrityCondition("NONE");

                    riskFreeObservationRepository.save(revisedObs);
                    revised++;
                } else if (record.availabilityTime().equals(latest.getAvailabilityTime())
                    && record.quotedYield().compareTo(latest.getQuotedYield()) != 0) {
                    // Conflicting quote published at the exact same availability instant
                    ValidationIssue issue = new ValidationIssue(
                        "RISK_FREE_BENCHMARK",
                        0L,
                        "INGESTION_CONFLICT",
                        String.format("Conflicting FBIL yield for %s on %s: existing=%s, new=%s at availability %s",
                            record.benchmarkCode(), record.effectiveDate(), latest.getQuotedYield(), record.quotedYield(), record.availabilityTime())
                    );
                    issue.setQualityAssessment("SUSPICIOUS");
                    issue.setIntegrityCondition("CONFLICTING");
                    validationIssueRepository.save(issue);

                    int nextSeq = existingRevisions.stream()
                        .mapToInt(RiskFreeObservation::getRevisionSeq)
                        .max()
                        .orElse(1) + 1;

                    RiskFreeObservation conflictingObs = new RiskFreeObservation(
                        record.benchmarkCode(),
                        record.effectiveDate(),
                        record.quotedYield(),
                        record.daycountConvention(),
                        nextSeq,
                        record.availabilityTime()
                    );
                    conflictingObs.setSourceArtifact(artifact);
                    conflictingObs.setQualityAssessment("SUSPICIOUS");
                    conflictingObs.setVerificationStatus("UNVERIFIED");
                    conflictingObs.setRevisionStatus("SUPERSEDED");
                    conflictingObs.setTemporalStatus("CURRENT");
                    conflictingObs.setPresenceStatus("AVAILABLE");
                    conflictingObs.setIntegrityCondition("CONFLICTING");

                    riskFreeObservationRepository.save(conflictingObs);
                    inserted++;
                } else {
                    // Observation with earlier availability time than existing latest
                    int nextSeq = existingRevisions.stream()
                        .mapToInt(RiskFreeObservation::getRevisionSeq)
                        .max()
                        .orElse(1) + 1;

                    RiskFreeObservation priorObs = new RiskFreeObservation(
                        record.benchmarkCode(),
                        record.effectiveDate(),
                        record.quotedYield(),
                        record.daycountConvention(),
                        nextSeq,
                        record.availabilityTime()
                    );
                    priorObs.setLatestRevision(false);
                    priorObs.setSourceArtifact(artifact);
                    priorObs.setQualityAssessment("VALID");
                    priorObs.setVerificationStatus("VERIFIED");
                    priorObs.setRevisionStatus("SUPERSEDED");
                    priorObs.setTemporalStatus("STALE");
                    priorObs.setPresenceStatus("AVAILABLE");
                    priorObs.setIntegrityCondition("NONE");

                    riskFreeObservationRepository.save(priorObs);
                    inserted++;
                }
            }
        }

        return new FbilIngestionSummary(
            records.size(),
            inserted,
            skipped,
            revised,
            artifact.getId(),
            artifact.getSha256Hash()
        );
    }
}
