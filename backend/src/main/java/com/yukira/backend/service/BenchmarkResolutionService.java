package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeBenchmarkHist;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeBenchmarkHistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class BenchmarkResolutionService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkResolutionService.class);
    public static final String DEFAULT_BENCHMARK_CODE = "NIFTY_500_TRI";

    private final SchemeBenchmarkHistRepository schemeBenchmarkHistRepository;
    private final BenchmarkRepository benchmarkRepository;

    public BenchmarkResolutionService(
        SchemeBenchmarkHistRepository schemeBenchmarkHistRepository,
        BenchmarkRepository benchmarkRepository
    ) {
        this.schemeBenchmarkHistRepository = schemeBenchmarkHistRepository;
        this.benchmarkRepository = benchmarkRepository;
    }

    public record BenchmarkResolutionResult(
        Benchmark benchmark,
        boolean isExplicitMapping,
        String resolutionMode,
        LocalDate effectiveFrom,
        String message
    ) {}

    /**
     * Resolves the authoritative primary benchmark for a scheme as of a given analytical date.
     *
     * Strict PIT Invariant:
     *   effective_from <= asOfDate AND (effective_to IS NULL OR effective_to >= asOfDate)
     */
    @Transactional(readOnly = true)
    public BenchmarkResolutionResult resolvePrimaryBenchmark(Long schemeId, LocalDate asOfDate) {
        if (schemeId == null) {
            throw new IllegalArgumentException("schemeId must not be null");
        }
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        Optional<SchemeBenchmarkHist> explicitOpt = schemeBenchmarkHistRepository
            .findPrimaryBenchmarkAsOfDate(schemeId, asOfDate);

        if (explicitOpt.isPresent()) {
            SchemeBenchmarkHist hist = explicitOpt.get();
            return new BenchmarkResolutionResult(
                hist.getBenchmark(),
                true,
                "EXPLICIT_HISTORICAL_MAPPING",
                hist.getEffectiveFrom(),
                String.format("Resolved explicit benchmark %s (%s) effective from %s",
                    hist.getBenchmark().getCode(), hist.getBenchmark().getName(), hist.getEffectiveFrom())
            );
        }

        // Fallback resolution diagnostic
        Benchmark defaultBm = benchmarkRepository.findByCode(DEFAULT_BENCHMARK_CODE)
            .orElseGet(() -> benchmarkRepository.findAll().stream().findFirst().orElse(null));

        if (defaultBm != null) {
            return new BenchmarkResolutionResult(
                defaultBm,
                false,
                "DEFAULT_FALLBACK_UNMAPPED",
                null,
                String.format("No explicit benchmark mapping found for scheme #%d as of %s; defaulting to system benchmark %s",
                    schemeId, asOfDate, defaultBm.getCode())
            );
        }

        return new BenchmarkResolutionResult(
            null,
            false,
            "BENCHMARK_UNAVAILABLE",
            null,
            String.format("No explicit mapping or system default benchmark available for scheme #%d as of %s", schemeId, asOfDate)
        );
    }
}
