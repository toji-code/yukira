package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.HoldingsArtifactReconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingsArtifactReconciliationRepository extends JpaRepository<HoldingsArtifactReconciliation, Long> {

    List<HoldingsArtifactReconciliation> findByAmcOrderByReportingPeriodDesc(Amc amc);

    Optional<HoldingsArtifactReconciliation> findByAmcAndReportingPeriod(Amc amc, LocalDate reportingPeriod);

    List<HoldingsArtifactReconciliation> findTop20ByOrderByCreatedAtDesc();
}

