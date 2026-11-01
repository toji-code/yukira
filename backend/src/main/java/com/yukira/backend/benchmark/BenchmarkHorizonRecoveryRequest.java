package com.yukira.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Request payload for bounded historical benchmark TRI recovery (Phase A4).
 */
public record BenchmarkHorizonRecoveryRequest(
    @JsonProperty("benchmarkCode") String benchmarkCode,
    @JsonProperty("horizon") String horizon,
    @JsonProperty("referenceDate") LocalDate referenceDate,
    @JsonProperty("maxChunksPerBenchmark") Integer maxChunksPerBenchmark,
    @JsonProperty("maxChunkDays") Integer maxChunkDays
) {
    public int resolvedMaxChunks() {
        return (maxChunksPerBenchmark != null && maxChunksPerBenchmark > 0) ? maxChunksPerBenchmark : 10;
    }

    public int resolvedMaxChunkDays() {
        return (maxChunkDays != null && maxChunkDays > 0) ? maxChunkDays : 365;
    }
}
