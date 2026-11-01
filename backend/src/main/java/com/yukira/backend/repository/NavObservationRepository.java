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

    /**
     * Canonical Point-in-Time (PIT) NAV Series Query (Tasks 368, 369, 370).
     * Enforces strict window-aware revision selection:
     * - effective_date <= asOfDate AND availability_time <= knowledgeCutoffTime
     * - Picks exactly ONE latest revision per effective date (max revision_seq at knowledge cutoff)
     *   using SQL window function ROW_NUMBER().
     * - Future revisions published after knowledge cutoff are strictly excluded.
     */
    @Query(value = """
        WITH ranked_nav AS (
            SELECT n.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY n.effective_date
                       ORDER BY n.availability_time DESC, n.revision_seq DESC, n.ingestion_time DESC, n.id DESC
                   ) AS rn
            FROM nav_observation n
            WHERE n.scheme_option_id = :schemeOptionId
              AND n.effective_date <= :asOfDate
              AND n.availability_time <= :knowledgeCutoffTime
              AND (n.revision_status IS NULL OR n.revision_status != 'RETRACTED')
              AND (n.quality_assessment IS NULL OR n.quality_assessment != 'INVALID')
        )
        SELECT id, scheme_option_id, effective_date, availability_time, revision_seq,
               nav_value, is_latest_revision, quality_assessment, verification_status,
               revision_status, freshness_status, presence_status, integrity_status,
               source_artifact_id, ingestion_time
        FROM ranked_nav
        WHERE rn = 1
        ORDER BY effective_date ASC
        """, nativeQuery = true)
    List<NavObservation> findCanonicalPitNavSeries(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    @Query(value = """
        WITH ranked_nav AS (
            SELECT n.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY n.effective_date
                       ORDER BY n.availability_time DESC, n.revision_seq DESC, n.ingestion_time DESC, n.id DESC
                   ) AS rn
            FROM nav_observation n
            WHERE n.scheme_option_id = :schemeOptionId
              AND n.effective_date >= :startDate
              AND n.effective_date <= :endDate
              AND n.availability_time <= :knowledgeCutoffTime
              AND (n.revision_status IS NULL OR n.revision_status != 'RETRACTED')
              AND (n.quality_assessment IS NULL OR n.quality_assessment != 'INVALID')
        )
        SELECT id, scheme_option_id, effective_date, availability_time, revision_seq,
               nav_value, is_latest_revision, quality_assessment, verification_status,
               revision_status, freshness_status, presence_status, integrity_status,
               source_artifact_id, ingestion_time
        FROM ranked_nav
        WHERE rn = 1
        ORDER BY effective_date ASC
        """, nativeQuery = true)
    List<NavObservation> findCanonicalPitNavSeriesInWindow(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    @Query(value = """
        SELECT n.*
        FROM nav_observation n
        WHERE n.scheme_option_id = :schemeOptionId
          AND n.effective_date <= :asOfDate
          AND n.availability_time <= :knowledgeCutoffTime
        ORDER BY n.effective_date DESC, n.availability_time DESC, n.revision_seq DESC
        LIMIT 1
        """, nativeQuery = true)
    java.util.Optional<NavObservation> findLatestAuthoritativeObservationAsOfCutoff(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime
    );

    List<NavObservation> findBySchemeOptionId(Long schemeOptionId);
    List<NavObservation> findBySchemeOptionIdOrderByEffectiveDateAsc(Long schemeOptionId);
    List<NavObservation> findBySchemeOptionIdAndEffectiveDate(Long schemeOptionId, LocalDate effectiveDate);
    @Query(value = """
        SELECT * FROM nav_observation
        WHERE scheme_option_id = :schemeOptionId
          AND is_latest_revision = true
        ORDER BY effective_date DESC, revision_seq DESC
        LIMIT 1
        """, nativeQuery = true)
    java.util.Optional<NavObservation> findLatestActiveObservation(@Param("schemeOptionId") Long schemeOptionId);

    java.util.Optional<com.yukira.backend.domain.entity.NavObservation> findTopBySchemeOptionIdOrderByEffectiveDateDesc(Long schemeOptionId);
    long countBySchemeOptionId(Long schemeOptionId);
    List<NavObservation> findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(Long schemeOptionId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(n) FROM NavObservation n WHERE n.schemeOption.id = :schemeOptionId AND n.effectiveDate >= :startDate AND n.effectiveDate <= :endDate")
    long countBySchemeOptionIdAndDateRange(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT n.schemeOption.id, COUNT(n) FROM NavObservation n WHERE n.effectiveDate >= :startDate AND n.effectiveDate <= :endDate GROUP BY n.schemeOption.id")
    List<Object[]> countByDateRangeGroupedBySchemeOption(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT MAX(n.effectiveDate) FROM NavObservation n")
    java.util.Optional<LocalDate> findMaxEffectiveDate();

    @Query("SELECT MAX(n.ingestionTime) FROM NavObservation n")
    java.util.Optional<OffsetDateTime> findMaxIngestionTime();

    @Query("SELECT n.schemeOption.id, MIN(n.effectiveDate), MAX(n.effectiveDate), COUNT(n) FROM NavObservation n GROUP BY n.schemeOption.id")
    List<Object[]> findSchemeOptionNavDateSummaries();

    @Query("""
        SELECT n.schemeOption.id,
               MIN(n.effectiveDate),
               MAX(n.effectiveDate),
               COUNT(DISTINCT n.effectiveDate),
               SUM(CASE WHEN n.qualityAssessment = 'INVALID' THEN 1 ELSE 0 END),
               SUM(CASE WHEN n.sourceArtifact IS NULL THEN 1 ELSE 0 END)
        FROM NavObservation n
        WHERE n.isLatestRevision = true
        GROUP BY n.schemeOption.id
        """)
    List<Object[]> findDistinctValidNavSummaries();

    @Query("""
        SELECT MIN(n.effectiveDate),
               MAX(n.effectiveDate),
               COUNT(DISTINCT n.effectiveDate),
               SUM(CASE WHEN n.qualityAssessment = 'INVALID' THEN 1 ELSE 0 END),
               SUM(CASE WHEN n.sourceArtifact IS NULL THEN 1 ELSE 0 END)
        FROM NavObservation n
        WHERE n.schemeOption.id = :schemeOptionId
          AND n.isLatestRevision = true
        """)
    List<Object[]> findDistinctValidNavSummaryBySchemeOptionId(@Param("schemeOptionId") Long schemeOptionId);

    @Query("""
        SELECT n.schemeOption.id,
               MIN(n.effectiveDate),
               MAX(n.effectiveDate),
               COUNT(DISTINCT n.effectiveDate)
        FROM NavObservation n
        WHERE n.isLatestRevision = true
          AND n.effectiveDate >= :startDate
          AND n.effectiveDate <= :endDate
        GROUP BY n.schemeOption.id
        """)
    List<Object[]> findDistinctValidNavSummariesInDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT MIN(n.effectiveDate),
               MAX(n.effectiveDate),
               COUNT(DISTINCT n.effectiveDate),
               SUM(CASE WHEN n.qualityAssessment = 'INVALID' THEN 1 ELSE 0 END),
               SUM(CASE WHEN n.sourceArtifact IS NULL THEN 1 ELSE 0 END)
        FROM NavObservation n
        WHERE n.schemeOption.id = :schemeOptionId
          AND n.isLatestRevision = true
          AND n.effectiveDate >= :startDate
          AND n.effectiveDate <= :endDate
        """)
    List<Object[]> findDistinctValidNavSummaryBySchemeOptionIdInDateRange(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
