package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.RiskFreeObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RiskFreeObservationRepository extends JpaRepository<RiskFreeObservation, Long> {

    List<RiskFreeObservation> findByBenchmarkCodeAndEffectiveDate(String benchmarkCode, LocalDate effectiveDate);

    List<RiskFreeObservation> findByBenchmarkCode(String benchmarkCode);

    Optional<RiskFreeObservation> findByBenchmarkCodeAndEffectiveDateAndRevisionSeq(
        String benchmarkCode, LocalDate effectiveDate, Integer revisionSeq
    );

    @Query(value = """
        SELECT DISTINCT ON (r.effective_date) r.*
        FROM risk_free_observation r
        WHERE r.benchmark_code = :benchmarkCode
          AND r.effective_date <= :asOfDate
          AND r.availability_time <= :knowledgeCutoffTime
        ORDER BY r.effective_date ASC, r.availability_time DESC, r.revision_seq DESC, r.source_artifact_id DESC
        """, nativeQuery = true)
    List<RiskFreeObservation> findAuthoritativeObservationsAsOfCutoff(
        @Param("benchmarkCode") String benchmarkCode,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    @Query(value = """
        SELECT DISTINCT ON (r.effective_date) r.*
        FROM risk_free_observation r
        WHERE r.benchmark_code = :benchmarkCode
          AND r.effective_date >= :startDate
          AND r.effective_date <= :endDate
          AND r.availability_time <= :knowledgeCutoffTime
        ORDER BY r.effective_date ASC, r.availability_time DESC, r.revision_seq DESC, r.source_artifact_id DESC
        """, nativeQuery = true)
    List<RiskFreeObservation> findAuthoritativeObservationsBetween(
        @Param("benchmarkCode") String benchmarkCode,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    @Query("SELECT COUNT(r) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode AND r.effectiveDate >= :startDate AND r.effectiveDate <= :endDate")
    long countByBenchmarkCodeAndDateRange(
        @Param("benchmarkCode") String benchmarkCode,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT MAX(r.effectiveDate) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode")
    Optional<LocalDate> findMaxEffectiveDateByBenchmarkCode(@Param("benchmarkCode") String benchmarkCode);

    @Query("SELECT MIN(r.effectiveDate) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode")
    Optional<LocalDate> findMinEffectiveDateByBenchmarkCode(@Param("benchmarkCode") String benchmarkCode);

    @Query("SELECT MAX(r.availabilityTime) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode")
    Optional<OffsetDateTime> findMaxAvailabilityTimeByBenchmarkCode(@Param("benchmarkCode") String benchmarkCode);

    @Query("SELECT MAX(r.ingestionTime) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode")
    Optional<OffsetDateTime> findMaxIngestionTimeByBenchmarkCode(@Param("benchmarkCode") String benchmarkCode);

    @Query("SELECT COUNT(r) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode")
    long countByBenchmarkCode(@Param("benchmarkCode") String benchmarkCode);

    List<RiskFreeObservation> findByBenchmarkCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
        String benchmarkCode,
        LocalDate startDate,
        LocalDate endDate
    );

    @Query("SELECT COUNT(DISTINCT r.effectiveDate) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode AND r.effectiveDate >= :startDate AND r.effectiveDate <= :endDate AND r.isLatestRevision = true")
    long countDistinctEffectiveDatesInWindow(
        @Param("benchmarkCode") String benchmarkCode,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT MIN(r.effectiveDate) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode AND r.effectiveDate >= :startDate AND r.effectiveDate <= :endDate AND r.isLatestRevision = true")
    Optional<LocalDate> findMinEffectiveDateInWindow(
        @Param("benchmarkCode") String benchmarkCode,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT MAX(r.effectiveDate) FROM RiskFreeObservation r WHERE r.benchmarkCode = :benchmarkCode AND r.effectiveDate >= :startDate AND r.effectiveDate <= :endDate AND r.isLatestRevision = true")
    Optional<LocalDate> findMaxEffectiveDateInWindow(
        @Param("benchmarkCode") String benchmarkCode,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
