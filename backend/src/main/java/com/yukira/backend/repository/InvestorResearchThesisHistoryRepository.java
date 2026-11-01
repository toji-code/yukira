package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorResearchThesisHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestorResearchThesisHistoryRepository extends JpaRepository<InvestorResearchThesisHistory, Long> {

    List<InvestorResearchThesisHistory> findByWatchlistIdOrderByThesisVersionDesc(Long watchlistId);

    List<InvestorResearchThesisHistory> findByInvestorIdOrderByCreatedAtDesc(Long investorId);

    List<InvestorResearchThesisHistory> findByAssociatedFindingIdOrderByCreatedAtDesc(String associatedFindingId);
}

