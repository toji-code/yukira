package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.CalculationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CalculationRunRepository extends JpaRepository<CalculationRun, Long> {
    List<CalculationRun> findBySchemeOptionIdAndAsOfDateOrderByExecutionStartedAtDesc(Long schemeOptionId, LocalDate asOfDate);
}
