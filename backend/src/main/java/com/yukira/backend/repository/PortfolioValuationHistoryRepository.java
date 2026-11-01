package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.PortfolioValuationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioValuationHistoryRepository extends JpaRepository<PortfolioValuationHistory, Long> {

    List<PortfolioValuationHistory> findByInvestorOrderByValuationDateDesc(Investor investor);

    Optional<PortfolioValuationHistory> findFirstByInvestorOrderByValuationDateDesc(Investor investor);
}

