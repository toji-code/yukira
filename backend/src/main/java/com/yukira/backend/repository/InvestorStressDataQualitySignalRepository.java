package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorStressDataQualitySignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorStressDataQualitySignalRepository extends JpaRepository<InvestorStressDataQualitySignal, Long> {

    List<InvestorStressDataQualitySignal> findByInvestorIdOrderByCreatedAtDesc(Long investorId);

    List<InvestorStressDataQualitySignal> findByCalculationRunId(Long calculationRunId);

    List<InvestorStressDataQualitySignal> findByInvestorIdAndSignalStateOrderByCreatedAtDesc(Long investorId, String signalState);

    List<InvestorStressDataQualitySignal> findByInvestorIdAndPortfolioIdOrderByCreatedAtDesc(Long investorId, Long portfolioId);
}

