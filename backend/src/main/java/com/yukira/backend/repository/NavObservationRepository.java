package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.NavObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NavObservationRepository extends JpaRepository<NavObservation, Long> {

    /**
     * Point-in-Time (PIT) Eligible Candidates at Latest Availability Query.
     *
     * For each distinct effective date, identifies the maximum eligible availability_time
     * known as of knowledgeCutoffTime, and returns ALL candidate observations at that instant.
     *
     * Epistemic Invariant:
     * Does NOT use DISTINCT ON, ensuring competing observations at the latest availability instant
     * are never discarded prior to downstream ambiguity detection and conflict verification.
     */
    @Query(value = """
        WITH latest_eligible AS (
            SELECT n.scheme_option_id,
                   n.effective_date,
                   MAX(n.availability_time) AS max_availability_time
            FROM nav_observation n
            WHERE n.scheme_option_id = :schemeOptionId
              AND n.effective_date <= :asOfDate
              AND n.availability_time <= :knowledgeCutoffTime
            GROUP BY n.scheme_option_id, n.effective_date
        )
        SELECT n.*
        FROM nav_observation n
        JOIN latest_eligible le
          ON n.scheme_option_id = le.scheme_option_id
         AND n.effective_date = le.effective_date
         AND n.availability_time = le.max_availability_time
        ORDER BY n.effective_date ASC, n.availability_time DESC, n.revision_seq DESC, n.source_artifact_id DESC
        """, nativeQuery = true)
    List<NavObservation> findAuthoritativeObservationsAsOfCutoff(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    List<NavObservation> findBySchemeOptionIdAndEffectiveDate(Long schemeOptionId, LocalDate effectiveDate);
    List<NavObservation> findBySchemeOptionId(Long schemeOptionId);

    @Query("SELECT COUNT(n) FROM NavObservation n WHERE n.schemeOption.id = :schemeOptionId AND n.effectiveDate >= :startDate AND n.effectiveDate <= :endDate")
    long countBySchemeOptionIdAndDateRange(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
