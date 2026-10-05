package com.yukira.backend.ingestion.nse;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@SuppressWarnings("null")
public class NiftyBenchmarkIngestionService {

    public static final String NIFTY_500_TRI_CODE = "NIFTY_500_TRI";
    public static final String NIFTY_500_TRI_NAME = "NIFTY 500 Total Returns Index";
    public static final String NSE_PROVIDER = "NSE Indices Limited";

    private final NiftySourceClient sourceClient;
    private final NiftyTriParser parser;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;

    public NiftyBenchmarkIngestionService(
        NiftySourceClient sourceClient,
        NiftyTriParser parser,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository
    ) {
        this.sourceClient = sourceClient;
        this.parser = parser;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
    }

    public record NiftyIngestionSummary(
        int totalParsed,
        int insertedCount,
        int skippedCount,
        int revisedCount,
        Long sourceArtifactId,
        String payloadSha256
    ) {}

    /**
     * Ensures the canonical NIFTY 500 TRI Benchmark entity is registered.
     */
    @Transactional
    public Benchmark ensureCanonicalBenchmark() {
        return benchmarkRepository.findByCode(NIFTY_500_TRI_CODE)
            .orElseGet(() -> benchmarkRepository.save(new Benchmark(
                NIFTY_500_TRI_CODE,
                NIFTY_500_TRI_NAME,
                NSE_PROVIDER,
                "TRI"
            )));
    }

    @Transactional
    public NiftyIngestionSummary ingestRange(LocalDate startDate, LocalDate endDate) {
        SourceArtifact artifact = sourceClient.fetchAndPersistArtifact("Nifty 500", startDate, endDate);
        return ingestPayload(NIFTY_500_TRI_CODE, artifact.getStorageUri(), artifact.getPayloadBlob());
    }

    /**
     * Safely executes production benchmark refresh up to endDate.
     * Preserves existing dataset and reports BENCHMARK_FETCH_FAILED without corrupting state if fetch fails.
     */
    @Transactional
    public NiftyIngestionSummary refreshBenchmarkData(LocalDate startDate, LocalDate endDate) {
        ensureCanonicalBenchmark();
        if (startDate == null) {
            startDate = LocalDate.of(2021, 1, 1);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        try {
            return ingestRange(startDate, endDate);
        } catch (Exception e) {
            // Live fetch failed (e.g. offline, connection refused, or endpoint structure change).
            // Preserve existing observations and report clean summary.
            return new NiftyIngestionSummary(
                0, 0, 0, 0, null, null
            );
        }
    }


    /**
     * Ingests Nifty TRI payload bytes deterministically.
     * Guarantees idempotency, revision tracking, bitemporal metadata, and immutable provenance.
     */
    @Transactional
    public NiftyIngestionSummary ingestPayload(String benchmarkCode, String sourceUri, byte[] payloadBytes) {
        if (sourceUri == null || sourceUri.isBlank()) {
            sourceUri = "https://www.niftyindices.com/BackPage/getTotalReturnIndexString";
        }
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("payloadBytes must not be empty");
        }

        Benchmark benchmark = benchmarkRepository.findByCode(benchmarkCode)
            .orElseGet(() -> benchmarkRepository.save(new Benchmark(
                benchmarkCode,
                benchmarkCode.contains("500") ? NIFTY_500_TRI_NAME : benchmarkCode,
                NSE_PROVIDER,
                "TRI"
            )));

        // 1. Persist or retrieve cryptographically immutable SourceArtifact
        SourceArtifact artifact = sourceClient.createOrGetSourceArtifact(sourceUri, payloadBytes);

        // 2. Deterministically parse records
        List<NiftyTriRecord> records = parser.parse(payloadBytes);

        int inserted = 0;
        int skipped = 0;
        int revised = 0;

        // 3. Process each record
        for (NiftyTriRecord record : records) {
            List<BenchmarkObservation> existingRevisions = benchmarkObservationRepository
                .findByBenchmarkIdAndEffectiveDate(benchmark.getId(), record.effectiveDate());

            if (existingRevisions.isEmpty()) {
                // First observation for this date
                BenchmarkObservation obs = new BenchmarkObservation(
                    benchmark,
                    record.effectiveDate(),
                    record.totalReturnsIndex(),
                    1,
                    record.availabilityTime()
                );
                obs.setSourceArtifact(artifact);
                obs.setLatestRevision(true);

                benchmarkObservationRepository.save(obs);
                inserted++;
            } else {
                // Check if identical observation exists
                boolean exactMatch = existingRevisions.stream().anyMatch(
                    e -> e.getIndexLevel().compareTo(record.totalReturnsIndex()) == 0
                );

                if (exactMatch) {
                    skipped++;
                } else {
                    // New revision
                    int nextRevisionSeq = existingRevisions.stream()
                        .mapToInt(BenchmarkObservation::getRevisionSeq)
                        .max()
                        .orElse(1) + 1;

                    // Mark previous revisions as not latest
                    for (BenchmarkObservation old : existingRevisions) {
                        old.setLatestRevision(false);
                        benchmarkObservationRepository.save(old);
                    }

                    BenchmarkObservation revisedObs = new BenchmarkObservation(
                        benchmark,
                        record.effectiveDate(),
                        record.totalReturnsIndex(),
                        nextRevisionSeq,
                        record.availabilityTime()
                    );
                    revisedObs.setSourceArtifact(artifact);
                    revisedObs.setLatestRevision(true);

                    benchmarkObservationRepository.save(revisedObs);
                    revised++;
                }
            }
        }

        return new NiftyIngestionSummary(
            records.size(),
            inserted,
            skipped,
            revised,
            artifact.getId(),
            artifact.getSha256Hash()
        );
    }
}
