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
        SELECT DISTINCT ON (b.effective_date) b.*
        FROM benchmark_observation b
        WHERE b.benchmark_id = :benchmarkId
          AND b.effective_date <= :asOfDate
          AND b.availability_time <= :knowledgeCutoffTime
        ORDER BY b.effective_date ASC, b.revision_seq DESC, b.availability_time DESC
        """, nativeQuery = true)
    List<BenchmarkObservation> findAuthoritativeObservationsAsOfCutoff(
        @Param("benchmarkId") Long benchmarkId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );
}
