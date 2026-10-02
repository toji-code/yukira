package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorPortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorPortfolioHoldingRepository extends JpaRepository<InvestorPortfolioHolding, Long> {
    List<InvestorPortfolioHolding> findByInvestorId(Long investorId);
    Optional<InvestorPortfolioHolding> findByInvestorIdAndSchemeOptionId(Long investorId, Long schemeOptionId);
}
