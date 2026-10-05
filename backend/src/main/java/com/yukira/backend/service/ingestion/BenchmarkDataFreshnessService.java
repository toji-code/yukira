package com.yukira.backend.service.ingestion;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.ingestion.BenchmarkDataFreshnessDto;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BenchmarkDataFreshnessService {

    public static final String NIFTY_500_TRI_CODE = "NIFTY_500_TRI";
    public static final String NIFTY_500_TRI_NAME = "NIFTY 500 Total Returns Index";
    public static final String NSE_PROVIDER = "NSE Indices Limited";
    public static final int FRESHNESS_THRESHOLD_DAYS = 3;

    public static final String GOVERNANCE_DISCLAIMER =
        "Benchmark observations are persisted with cryptographic SHA-256 source artifact provenance. " +
        "Ingestion of raw benchmark observations does NOT execute quant engine or recalculate fund scores.";

    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public BenchmarkDataFreshnessService(
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        SourceArtifactRepository sourceArtifactRepository
    ) {
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
    }

    public BenchmarkDataFreshnessDto getFreshnessStatus() {
        return getFreshnessStatus(LocalDate.now());
    }

    public BenchmarkDataFreshnessDto getFreshnessStatus(LocalDate referenceDate) {
        Optional<Benchmark> benchmarkOpt = benchmarkRepository.findByCode(NIFTY_500_TRI_CODE);

        Optional<SourceArtifact> latestArtifactOpt = sourceArtifactRepository.findTopByOrderByRetrievalTimestampDesc();

        if (benchmarkOpt.isEmpty()) {
            return new BenchmarkDataFreshnessDto(
                NIFTY_500_TRI_CODE,
                NIFTY_500_TRI_NAME,
                NSE_PROVIDER,
                "UNAVAILABLE",
                null,
                null,
                latestArtifactOpt.map(SourceArtifact::getRetrievalTimestamp).orElse(null),
                null,
                latestArtifactOpt.map(SourceArtifact::getSha256Hash).orElse(null),
                0,
                null,
                FRESHNESS_THRESHOLD_DAYS,
                GOVERNANCE_DISCLAIMER
            );
        }

        Benchmark benchmark = benchmarkOpt.get();
        Long benchmarkId = benchmark.getId();

        long totalObservations = benchmarkObservationRepository.countByBenchmarkIdAndDateRange(
            benchmarkId, LocalDate.of(1990, 1, 1), LocalDate.of(2099, 12, 31)
        );

        Optional<LocalDate> minDateOpt = benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(benchmarkId);
        Optional<LocalDate> maxDateOpt = benchmarkObservationRepository.findMaxEffectiveDateByBenchmarkId(benchmarkId);
        Optional<OffsetDateTime> maxAvailabilityOpt = benchmarkObservationRepository.findMaxAvailabilityTimeByBenchmarkId(benchmarkId);

        if (totalObservations == 0 || maxDateOpt.isEmpty()) {
            return new BenchmarkDataFreshnessDto(
                benchmark.getCode(),
                benchmark.getName(),
                benchmark.getProvider(),
                "UNAVAILABLE",
                null,
                null,
                latestArtifactOpt.map(SourceArtifact::getRetrievalTimestamp).orElse(null),
                null,
                latestArtifactOpt.map(SourceArtifact::getSha256Hash).orElse(null),
                0,
                null,
                FRESHNESS_THRESHOLD_DAYS,
                GOVERNANCE_DISCLAIMER
            );
        }

        LocalDate latestBenchmarkDate = maxDateOpt.get();
        LocalDate evalDate = referenceDate != null ? referenceDate : LocalDate.now();
        long daysDiff = ChronoUnit.DAYS.between(latestBenchmarkDate, evalDate);
        if (daysDiff < 0) {
            daysDiff = 0;
        }

        String state;
        if (daysDiff <= FRESHNESS_THRESHOLD_DAYS) {
            state = "FRESH";
        } else if (daysDiff <= 7) {
            state = "PARTIAL";
        } else {
            state = "STALE";
        }

        SourceArtifact artifact = latestArtifactOpt.orElse(null);

        return new BenchmarkDataFreshnessDto(
            benchmark.getCode(),
            benchmark.getName(),
            benchmark.getProvider(),
            state,
            minDateOpt.orElse(null),
            latestBenchmarkDate,
            artifact != null ? artifact.getRetrievalTimestamp() : null,
            maxAvailabilityOpt.orElse(null),
            artifact != null ? artifact.getSha256Hash() : null,
            totalObservations,
            daysDiff,
            FRESHNESS_THRESHOLD_DAYS,
            GOVERNANCE_DISCLAIMER
        );
    }
}
