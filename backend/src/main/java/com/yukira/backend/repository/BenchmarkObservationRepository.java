package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.BenchmarkObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface BenchmarkObservationRepository extends JpaRepository<BenchmarkObservation, Long> {

    List<BenchmarkObservation> findByBenchmarkIdAndEffectiveDate(Long benchmarkId, LocalDate effectiveDate);

    List<BenchmarkObservation> findByBenchmarkId(Long benchmarkId);

    long countByBenchmarkId(Long benchmarkId);

    @Query("SELECT COUNT(b) FROM BenchmarkObservation b WHERE b.benchmark.id = :benchmarkId AND b.effectiveDate >= :startDate AND b.effectiveDate <= :endDate")
    long countByBenchmarkIdAndDateRange(
        @Param("benchmarkId") Long benchmarkId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT MAX(b.effectiveDate) FROM BenchmarkObservation b WHERE b.benchmark.id = :benchmarkId")
    Optional<LocalDate> findMaxEffectiveDateByBenchmarkId(@Param("benchmarkId") Long benchmarkId);

    @Query("SELECT MIN(b.effectiveDate) FROM BenchmarkObservation b WHERE b.benchmark.id = :benchmarkId")
    Optional<LocalDate> findMinEffectiveDateByBenchmarkId(@Param("benchmarkId") Long benchmarkId);

    @Query("SELECT MAX(b.availabilityTime) FROM BenchmarkObservation b WHERE b.benchmark.id = :benchmarkId")
    Optional<OffsetDateTime> findMaxAvailabilityTimeByBenchmarkId(@Param("benchmarkId") Long benchmarkId);

    List<BenchmarkObservation> findByBenchmarkIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
        Long benchmarkId,
        LocalDate startDate,
        LocalDate endDate
    );

    @Query("SELECT COUNT(DISTINCT b.effectiveDate) FROM BenchmarkObservation b WHERE b.benchmark.id = :benchmarkId AND b.effectiveDate >= :startDate AND b.effectiveDate <= :endDate AND b.isLatestRevision = true")
    long countDistinctEffectiveDatesInWindow(
        @Param("benchmarkId") Long benchmarkId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT MIN(b.effectiveDate) FROM BenchmarkObservation b WHERE b.benchmark.id = :benchmarkId AND b.effectiveDate >= :startDate AND b.effectiveDate <= :endDate")
    Optional<LocalDate> findMinEffectiveDateInWindow(
        @Param("benchmarkId") Long benchmarkId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT MAX(b.effectiveDate) FROM BenchmarkObservation b WHERE b.benchmark.id = :benchmarkId AND b.effectiveDate >= :startDate AND b.effectiveDate <= :endDate")
    Optional<LocalDate> findMaxEffectiveDateInWindow(
        @Param("benchmarkId") Long benchmarkId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query(value = """
        WITH latest_eligible AS (
            SELECT b.benchmark_id,
                   b.effective_date,
                   MAX(b.availability_time) AS max_availability_time
            FROM benchmark_observation b
            WHERE b.benchmark_id = :benchmarkId
              AND b.effective_date <= :asOfDate
              AND b.availability_time <= :knowledgeCutoffTime
            GROUP BY b.benchmark_id, b.effective_date
        )
        SELECT b.*
        FROM benchmark_observation b
        JOIN latest_eligible le
          ON b.benchmark_id = le.benchmark_id
         AND b.effective_date = le.effective_date
         AND b.availability_time = le.max_availability_time
        ORDER BY b.effective_date ASC, b.availability_time DESC, b.revision_seq DESC, b.source_artifact_id DESC
        """, nativeQuery = true)
    List<BenchmarkObservation> findAuthoritativeObservationsAsOfCutoff(
        @Param("benchmarkId") Long benchmarkId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    /**
     * Canonical Point-in-Time (PIT) Benchmark Series Query (Tasks 368, 369, 370).
     * Enforces strict window-aware revision selection:
     * - effective_date <= asOfDate AND availability_time <= knowledgeCutoffTime
     * - Picks exactly ONE latest revision per effective date (max revision_seq at knowledge cutoff)
     *   using SQL window function ROW_NUMBER().
     * - Future revisions published after knowledge cutoff are strictly excluded.
     */
    @Query(value = """
        WITH ranked_bm AS (
            SELECT b.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY b.effective_date
                       ORDER BY b.availability_time DESC, b.revision_seq DESC, b.ingestion_time DESC, b.id DESC
                   ) AS rn
            FROM benchmark_observation b
            WHERE b.benchmark_id = :benchmarkId
              AND b.effective_date <= :asOfDate
              AND b.availability_time <= :knowledgeCutoffTime
              AND (b.revision_status IS NULL OR b.revision_status != 'RETRACTED')
              AND (b.quality_assessment IS NULL OR b.quality_assessment != 'INVALID')
        )
        SELECT id, benchmark_id, effective_date, availability_time, revision_seq,
               return_value, is_latest_revision, quality_assessment, verification_status,
               revision_status, freshness_status, presence_status, integrity_status,
               source_artifact_id, ingestion_time
        FROM ranked_bm
        WHERE rn = 1
        ORDER BY effective_date ASC
        """, nativeQuery = true)
    List<BenchmarkObservation> findCanonicalPitBenchmarkSeries(
        @Param("benchmarkId") Long benchmarkId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    @Query(value = """
        WITH ranked_bm AS (
            SELECT b.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY b.effective_date
                       ORDER BY b.availability_time DESC, b.revision_seq DESC, b.ingestion_time DESC, b.id DESC
                   ) AS rn
            FROM benchmark_observation b
            WHERE b.benchmark_id = :benchmarkId
              AND b.effective_date >= :startDate
              AND b.effective_date <= :endDate
              AND b.availability_time <= :knowledgeCutoffTime
              AND (b.revision_status IS NULL OR b.revision_status != 'RETRACTED')
              AND (b.quality_assessment IS NULL OR b.quality_assessment != 'INVALID')
        )
        SELECT id, benchmark_id, effective_date, availability_time, revision_seq,
               return_value, is_latest_revision, quality_assessment, verification_status,
               revision_status, freshness_status, presence_status, integrity_status,
               source_artifact_id, ingestion_time
        FROM ranked_bm
        WHERE rn = 1
        ORDER BY effective_date ASC
        """, nativeQuery = true)
    List<BenchmarkObservation> findCanonicalPitBenchmarkSeriesInWindow(
        @Param("benchmarkId") Long benchmarkId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );
}
