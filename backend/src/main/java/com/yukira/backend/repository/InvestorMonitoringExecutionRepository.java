package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorMonitoringExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvestorMonitoringExecutionRepository extends JpaRepository<InvestorMonitoringExecution, Long> {

    Optional<InvestorMonitoringExecution> findTopByInvestorOrderByStartedAtDesc(Investor investor);

    Page<InvestorMonitoringExecution> findByInvestorOrderByStartedAtDesc(Investor investor, Pageable pageable);

    Optional<InvestorMonitoringExecution> findTopByOrderByStartedAtDesc();

    long countByInvestor(Investor investor);
}

