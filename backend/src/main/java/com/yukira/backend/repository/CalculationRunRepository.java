package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.CalculationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CalculationRunRepository extends JpaRepository<CalculationRun, Long> {
    List<CalculationRun> findBySchemeOptionIdAndAsOfDateOrderByExecutionStartedAtDesc(Long schemeOptionId, LocalDate asOfDate);
    List<CalculationRun> findBySchemeOptionId(Long schemeOptionId);
    boolean existsByMethodologyVersionId(Long methodologyVersionId);

    @Query("""
        SELECT r FROM CalculationRun r
        JOIN FETCH r.schemeOption o
        WHERE o.id IN :schemeOptionIds
          AND r.asOfDate = :asOfDate
        ORDER BY o.id ASC, r.executionStartedAt DESC, r.id DESC
        """)
    List<CalculationRun> findComparisonRunsBySchemeOptionIdsAndAsOfDate(
        @Param("schemeOptionIds") List<Long> schemeOptionIds,
        @Param("asOfDate") LocalDate asOfDate
    );

    @Query("""
        SELECT r FROM CalculationRun r
        JOIN FETCH r.schemeOption o
        WHERE o.id IN :schemeOptionIds
        ORDER BY o.id ASC, r.executionStartedAt DESC, r.id DESC
        """)
    List<CalculationRun> findComparisonRunsBySchemeOptionIds(@Param("schemeOptionIds") List<Long> schemeOptionIds);
}
