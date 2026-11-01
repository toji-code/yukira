package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.MonitoringEvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MonitoringEvaluationRunRepository extends JpaRepository<MonitoringEvaluationRun, Long> {
    Optional<MonitoringEvaluationRun> findByIdempotencyKey(String idempotencyKey);
    List<MonitoringEvaluationRun> findByDataCutoffOrderByEvaluationTimestampDesc(LocalDate dataCutoff);
    List<MonitoringEvaluationRun> findTop20ByOrderByEvaluationTimestampDesc();
}

