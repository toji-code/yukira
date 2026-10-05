package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.scoring.dto.LatestAnalyticalScoreProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyticalScoreRepository extends JpaRepository<AnalyticalScore, Long> {

    List<AnalyticalScore> findBySchemeOptionIdOrderByCreatedAtDesc(Long schemeOptionId);

    Optional<AnalyticalScore> findByCalculationRunId(Long calculationRunId);

    @Query("SELECT s FROM AnalyticalScore s WHERE s.schemeOption.id = :schemeOptionId AND s.asOfDate = :asOfDate AND s.scoreVersion = :scoreVersion ORDER BY s.createdAt DESC")
    List<AnalyticalScore> findBySchemeOptionAndDateAndVersion(
        @Param("schemeOptionId") Long schemeOptionId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("scoreVersion") String scoreVersion
    );

    @Query("SELECT s FROM AnalyticalScore s WHERE s.schemeOption.id = :schemeOptionId ORDER BY s.asOfDate DESC, s.createdAt DESC")
    List<AnalyticalScore> findLatestBySchemeOptionId(@Param("schemeOptionId") Long schemeOptionId);

    List<AnalyticalScore> findByAsOfDateAndScoreVersion(LocalDate asOfDate, String scoreVersion);
    @Query("""
        SELECT s FROM AnalyticalScore s
        JOIN FETCH s.schemeOption o
        LEFT JOIN FETCH s.calculationRun cr
        WHERE o.id IN :schemeOptionIds
        ORDER BY o.id ASC, s.asOfDate DESC, s.createdAt DESC, s.id DESC
        """)
    List<AnalyticalScore> findHistoryBySchemeOptionIds(@Param("schemeOptionIds") List<Long> schemeOptionIds);

    @Query("""
        SELECT s FROM AnalyticalScore s
        JOIN FETCH s.schemeOption o
        LEFT JOIN FETCH s.calculationRun cr
        WHERE o.id IN :schemeOptionIds
          AND s.asOfDate <= :asOfDate
          AND NOT EXISTS (
              SELECT 1 FROM AnalyticalScore newer
              WHERE newer.schemeOption.id = o.id
                AND newer.asOfDate <= :asOfDate
                AND (
                    newer.asOfDate > s.asOfDate
                    OR (newer.asOfDate = s.asOfDate AND newer.createdAt > s.createdAt)
                    OR (newer.asOfDate = s.asOfDate AND newer.createdAt = s.createdAt AND newer.id > s.id)
                )
          )
        ORDER BY o.id ASC
        """)
    List<AnalyticalScore> findLatestOnOrBeforeBySchemeOptionIds(
        @Param("schemeOptionIds") List<Long> schemeOptionIds,
        @Param("asOfDate") LocalDate asOfDate
    );
    @Query("""
        SELECT s FROM AnalyticalScore s
        JOIN FETCH s.schemeOption o
        LEFT JOIN FETCH s.calculationRun cr
        WHERE o.id IN :schemeOptionIds
          AND NOT EXISTS (
              SELECT 1 FROM AnalyticalScore newer
              WHERE newer.schemeOption.id = o.id
                AND (
                    newer.asOfDate > s.asOfDate
                    OR (newer.asOfDate = s.asOfDate AND newer.createdAt > s.createdAt)
                    OR (newer.asOfDate = s.asOfDate AND newer.createdAt = s.createdAt AND newer.id > s.id)
                )
          )
        """)
    List<AnalyticalScore> findLatestBySchemeOptionIds(@Param("schemeOptionIds") List<Long> schemeOptionIds);

    @Query("SELECT s FROM AnalyticalScore s WHERE s.id IN (SELECT MAX(s2.id) FROM AnalyticalScore s2 GROUP BY s2.schemeOption.id)")
    List<AnalyticalScore> findLatestScoresForAllOptions();

    /**
     * Selects the latest AnalyticalScore per SchemeOption, flattened across the
     * schemeOption -> plan -> scheme associations.
     *
     * Selection criteria and result cardinality are identical to
     * {@link #findLatestScoresForAllOptions()}, but the associations are joined in the
     * selecting statement instead of being dereferenced lazily per row. Ordering by
     * scheme option id makes the discovery tie-break deterministic; the original query
     * left row order unspecified to the database.
     */
    @Query("""
        SELECT s.id AS scoreId,
               o.id AS schemeOptionId,
               sc.id AS schemeId,
               p.planType AS planType,
               o.optionType AS optionType,
               s.score AS score,
               s.confidence AS confidence,
               s.status AS status,
               s.scoreVersion AS scoreVersion,
               s.methodologyStatus AS methodologyStatus,
               s.asOfDate AS asOfDate,
               s.summary AS summary
        FROM AnalyticalScore s
        JOIN s.schemeOption o
        JOIN o.plan p
        JOIN p.scheme sc
        WHERE s.id IN (SELECT MAX(s2.id) FROM AnalyticalScore s2 GROUP BY s2.schemeOption.id)
        ORDER BY o.id ASC
        """)
    List<LatestAnalyticalScoreProjection> findLatestScoreSummariesForAllOptions();
}
