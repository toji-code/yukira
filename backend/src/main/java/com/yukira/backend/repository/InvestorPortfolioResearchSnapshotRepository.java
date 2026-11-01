package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorPortfolioResearchSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorPortfolioResearchSnapshotRepository extends JpaRepository<InvestorPortfolioResearchSnapshot, Long> {

    List<InvestorPortfolioResearchSnapshot> findByInvestorOrderByAsOfDateDesc(Investor investor);

    List<InvestorPortfolioResearchSnapshot> findByInvestorOrderByCreatedAtDesc(Investor investor);

    Optional<InvestorPortfolioResearchSnapshot> findByIdAndInvestor(Long id, Investor investor);
}

