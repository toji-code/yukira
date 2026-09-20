package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MetricDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, Long> {
    Optional<MetricDefinition> findByMetricCode(String metricCode);
    List<MetricDefinition> findByAnalyticalDimension(String analyticalDimension);
}
