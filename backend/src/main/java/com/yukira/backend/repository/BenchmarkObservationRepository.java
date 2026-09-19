package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.BenchmarkObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface BenchmarkObservationRepository extends JpaRepository<BenchmarkObservation, Long> {

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
}
