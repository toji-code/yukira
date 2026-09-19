package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MetricResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetricResultRepository extends JpaRepository<MetricResult, Long> {
    List<MetricResult> findByCalculationRunId(Long calculationRunId);
    List<MetricResult> findByCalculationRunIdAndMetricCode(Long calculationRunId, String metricCode);
}
