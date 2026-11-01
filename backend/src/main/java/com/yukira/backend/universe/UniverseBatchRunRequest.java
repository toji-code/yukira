package com.yukira.backend.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Request payload for operator-triggered bounded reference-universe batch ingestion
 * and calculation runs (TSK-M1-06).
 */
public record UniverseBatchRunRequest(
    @JsonProperty("batchSize") Integer batchSize,
    @JsonProperty("category") EquityUniverseCategory category,
    @JsonProperty("startDate") LocalDate startDate,
    @JsonProperty("endDate") LocalDate endDate,
    @JsonProperty("asOfDate") LocalDate asOfDate,
    @JsonProperty("knowledgeCutoff") OffsetDateTime knowledgeCutoff,
    @JsonProperty("dryRun") Boolean dryRun,
    @JsonProperty("backfillNavIfMissing") Boolean backfillNavIfMissing,
    @JsonProperty("ingestBenchmarkIfMissing") Boolean ingestBenchmarkIfMissing,
    @JsonProperty("calculateMetrics") Boolean calculateMetrics,
    @JsonProperty("operator") String operator,
    @JsonProperty("forceRecalculate") Boolean forceRecalculate
) {
    public static final int DEFAULT_BATCH_SIZE = 5;
    public static final int MAX_BATCH_SIZE = 250;
    public static final LocalDate DEFAULT_CANONICAL_START = LocalDate.of(2021, 1, 1);
    public static final LocalDate DEFAULT_CANONICAL_END = LocalDate.of(2024, 1, 15);
    public static final LocalDate DEFAULT_CANONICAL_AS_OF = LocalDate.of(2024, 1, 15);

    public UniverseBatchRunRequest(
        Integer batchSize,
        EquityUniverseCategory category,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        Boolean dryRun,
        Boolean backfillNavIfMissing,
        Boolean ingestBenchmarkIfMissing,
        Boolean calculateMetrics,
        String operator
    ) {
        this(batchSize, category, startDate, endDate, asOfDate, knowledgeCutoff, dryRun, backfillNavIfMissing, ingestBenchmarkIfMissing, calculateMetrics, operator, false);
    }

    public UniverseBatchRunRequest {
        if (batchSize == null) {
            batchSize = DEFAULT_BATCH_SIZE;
        } else if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE + "; received: " + batchSize);
        }
        if (dryRun == null) {
            dryRun = false;
        }
        if (backfillNavIfMissing == null) {
            backfillNavIfMissing = true;
        }
        if (ingestBenchmarkIfMissing == null) {
            ingestBenchmarkIfMissing = true;
        }
        if (calculateMetrics == null) {
            calculateMetrics = true;
        }
        if (forceRecalculate == null) {
            forceRecalculate = false;
        }
        if (asOfDate == null) {
            asOfDate = DEFAULT_CANONICAL_AS_OF;
        }
        if (startDate == null) {
            startDate = DEFAULT_CANONICAL_START;
        }
        if (endDate == null) {
            endDate = asOfDate;
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate (" + startDate + ") cannot be after endDate (" + endDate + ")");
        }
        if (knowledgeCutoff == null) {
            knowledgeCutoff = asOfDate.atTime(23, 59, 59).atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        }
        if (operator == null || operator.isBlank()) {
            operator = "authenticated-operator";
        }
    }

    public static UniverseBatchRunRequest defaultRequest() {
        return new UniverseBatchRunRequest(
            DEFAULT_BATCH_SIZE,
            null,
            DEFAULT_CANONICAL_START,
            DEFAULT_CANONICAL_END,
            DEFAULT_CANONICAL_AS_OF,
            DEFAULT_CANONICAL_AS_OF.atTime(23, 59, 59).atOffset(ZoneOffset.ofHoursMinutes(5, 30)),
            false,
            true,
            true,
            true,
            "admin-operator"
        );
    }

    public UniverseBatchRunRequest withDryRun(boolean newDryRun) {
        return new UniverseBatchRunRequest(
            this.batchSize,
            this.category,
            this.startDate,
            this.endDate,
            this.asOfDate,
            this.knowledgeCutoff,
            newDryRun,
            this.backfillNavIfMissing,
            this.ingestBenchmarkIfMissing,
            this.calculateMetrics,
            this.operator,
            this.forceRecalculate
        );
    }
}
