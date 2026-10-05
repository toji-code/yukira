package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.ScoreMetricContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreMetricContributionRepository extends JpaRepository<ScoreMetricContribution, Long> {
    List<ScoreMetricContribution> findByScoreDimensionId(Long scoreDimensionId);

    @Query("""
        SELECT c FROM ScoreMetricContribution c
        JOIN FETCH c.scoreDimension d
        WHERE d.id IN :scoreDimensionIds
        ORDER BY d.id ASC, c.metricCode ASC
        """)
    List<ScoreMetricContribution> findByScoreDimensionIdIn(@Param("scoreDimensionIds") List<Long> scoreDimensionIds);
}
