package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MetricValidationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetricValidationEvidenceRepository extends JpaRepository<MetricValidationEvidence, Long> {
    List<MetricValidationEvidence> findByMetricCodeOrderByValidationDateDesc(String metricCode);
    Optional<MetricValidationEvidence> findFirstByMetricCodeAndAuthorizationStatusOrderByValidationDateDesc(String metricCode, String authorizationStatus);
    List<MetricValidationEvidence> findAllByOrderByValidationDateDesc();
}

