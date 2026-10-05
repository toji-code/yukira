package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.ScoreDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoreDimensionRepository extends JpaRepository<ScoreDimension, Long> {
    List<ScoreDimension> findByAnalyticalScoreId(Long analyticalScoreId);

    @Query("""
        SELECT d FROM ScoreDimension d
        JOIN FETCH d.analyticalScore s
        WHERE s.id IN :analyticalScoreIds
        ORDER BY s.id ASC, d.dimension ASC
        """)
    List<ScoreDimension> findByAnalyticalScoreIdIn(@Param("analyticalScoreIds") List<Long> analyticalScoreIds);
}
