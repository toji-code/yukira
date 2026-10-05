package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.scoring.population.PeerMetricResultProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface MetricResultRepository extends JpaRepository<MetricResult, Long> {
    List<MetricResult> findByCalculationRunId(Long calculationRunId);
    List<MetricResult> findByCalculationRunIdAndMetricCode(Long calculationRunId, String metricCode);

    /**
     * Reads canonical MetricResults for a calculation run, strictly excluding legacy non-canonical REL-02.
     */
    @Query("""
        SELECT mr FROM MetricResult mr
        WHERE mr.calculationRun.id = :calculationRunId
          AND (mr.metricCode <> 'REL-02' OR cast(mr.diagnostics as string) LIKE '%OLS intercept%')
        """)
    List<MetricResult> findCanonicalByCalculationRunId(@Param("calculationRunId") Long calculationRunId);

    /**
     * Reads canonical MetricResult by calculation run and metric code, strictly enforcing canonical semantics for REL-02.
     */
    @Query("""
        SELECT mr FROM MetricResult mr
        WHERE mr.calculationRun.id = :calculationRunId
          AND mr.metricCode = :metricCode
          AND (mr.metricCode <> 'REL-02' OR cast(mr.diagnostics as string) LIKE '%OLS intercept%')
        """)
    List<MetricResult> findCanonicalByCalculationRunIdAndMetricCode(
        @Param("calculationRunId") Long calculationRunId,
        @Param("metricCode") String metricCode
    );

    @Query("""
        SELECT mr FROM MetricResult mr
        JOIN FETCH mr.calculationRun cr
        JOIN FETCH cr.schemeOption o
        WHERE cr.id IN :calculationRunIds
          AND mr.metricCode IN :metricCodes
          AND (mr.metricCode <> 'REL-02' OR cast(mr.diagnostics as string) LIKE '%OLS intercept%')
        """)
    List<MetricResult> findCanonicalByCalculationRunIdInAndMetricCodeIn(
        @Param("calculationRunIds") List<Long> calculationRunIds,
        @Param("metricCodes") List<String> metricCodes
    );
    /**
     * Canonical peer-calibration read model.
     *
     * Reads persisted, deterministic MetricResults produced by the canonical calculation pipeline
     * (CalculationOrchestratorService -> Python quant engine) for an exact
     * (asOfDate, knowledgeCutoffTime, benchmark, peer cohort) calibration configuration.
     *
     * No financial mathematics is performed here. This query only selects values that were
     * already computed and persisted by the quant engine.
     *
     * @param excludedSchemeOptionId subject fund excluded from the peer reference population
     */
    @Query("""
        SELECT mr.metricCode            AS metricCode,
               mr.periodType            AS periodType,
               mr.numericValue           AS numericValue,
               mr.units                  AS units,
               mr.calculationStatus      AS calculationStatus,
               cr.id                     AS calculationRunId,
               cr.schemeOption.id        AS schemeOptionId
        FROM MetricResult mr
        JOIN mr.calculationRun cr
        WHERE cr.asOfDate = :asOfDate
          AND cr.knowledgeCutoffTime = :knowledgeCutoffTime
          AND cr.benchmark.id = :benchmarkId
          AND cr.schemeOption.id IN :schemeOptionIds
          AND cr.schemeOption.id <> :excludedSchemeOptionId
          AND mr.metricCode IN :metricCodes
          AND (mr.metricCode <> 'REL-02' OR cast(mr.diagnostics as string) LIKE '%OLS intercept%')
        """)
    List<PeerMetricResultProjection> findCalibrationMetricResults(
        @Param("schemeOptionIds") List<Long> schemeOptionIds,
        @Param("excludedSchemeOptionId") Long excludedSchemeOptionId,
        @Param("benchmarkId") Long benchmarkId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime,
        @Param("metricCodes") List<String> metricCodes
    );
}
