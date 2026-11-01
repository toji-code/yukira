package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorComparisonResearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorComparisonResearchRepository extends JpaRepository<InvestorComparisonResearch, Long> {
    List<InvestorComparisonResearch> findByInvestorIdOrderByCreatedAtDesc(Long investorId);
    List<InvestorComparisonResearch> findByInvestorOrderByUpdatedAtDesc(com.yukira.backend.domain.entity.Investor investor);
    List<InvestorComparisonResearch> findByInvestorIdAndAssociatedFindingId(Long investorId, String associatedFindingId);
}
