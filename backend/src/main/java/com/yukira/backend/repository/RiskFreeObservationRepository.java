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
        WITH latest_eligible AS (
            SELECT r.benchmark_code,
                   r.effective_date,
                   MAX(r.availability_time) AS max_availability_time
            FROM risk_free_observation r
            WHERE r.benchmark_code = :benchmarkCode
              AND r.effective_date <= :asOfDate
              AND r.availability_time <= :knowledgeCutoffTime
            GROUP BY r.benchmark_code, r.effective_date
        )
        SELECT r.*
        FROM risk_free_observation r
        JOIN latest_eligible le
          ON r.benchmark_code = le.benchmark_code
         AND r.effective_date = le.effective_date
         AND r.availability_time = le.max_availability_time
        ORDER BY r.effective_date ASC, r.availability_time DESC, r.revision_seq DESC, r.source_artifact_id DESC
        """, nativeQuery = true)
    List<RiskFreeObservation> findAuthoritativeObservationsAsOfCutoff(
        @Param("benchmarkCode") String benchmarkCode,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    @Query(value = """
        WITH latest_eligible AS (
            SELECT r.benchmark_code,
                   r.effective_date,
                   MAX(r.availability_time) AS max_availability_time
            FROM risk_free_observation r
            WHERE r.benchmark_code = :benchmarkCode
              AND r.effective_date >= :startDate
              AND r.effective_date <= :endDate
              AND r.availability_time <= :knowledgeCutoffTime
            GROUP BY r.benchmark_code, r.effective_date
        )
        SELECT r.*
        FROM risk_free_observation r
        JOIN latest_eligible le
          ON r.benchmark_code = le.benchmark_code
         AND r.effective_date = le.effective_date
         AND r.availability_time = le.max_availability_time
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
}
