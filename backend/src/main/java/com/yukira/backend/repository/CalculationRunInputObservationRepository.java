package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.CalculationRunInputObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalculationRunInputObservationRepository extends JpaRepository<CalculationRunInputObservation, Long> {
    List<CalculationRunInputObservation> findByCalculationRunId(Long calculationRunId);
    List<CalculationRunInputObservation> findByNavObservationId(Long navObservationId);
    List<CalculationRunInputObservation> findByBenchmarkObservationId(Long benchmarkObservationId);
    List<CalculationRunInputObservation> findByRiskFreeObservationId(Long riskFreeObservationId);
}
