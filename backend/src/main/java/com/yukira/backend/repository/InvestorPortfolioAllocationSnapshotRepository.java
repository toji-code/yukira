package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorPortfolioAllocationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorPortfolioAllocationSnapshotRepository extends JpaRepository<InvestorPortfolioAllocationSnapshot, Long> {

    Optional<InvestorPortfolioAllocationSnapshot> findBySnapshotId(String snapshotId);

    Optional<InvestorPortfolioAllocationSnapshot> findFirstByInvestorIdAndSnapshotFingerprintOrderByCreatedAtDesc(
        Long investorId, String snapshotFingerprint
    );

    List<InvestorPortfolioAllocationSnapshot> findByInvestorIdOrderByCreatedAtDesc(Long investorId);

    List<InvestorPortfolioAllocationSnapshot> findByInvestorIdAndPortfolioIdOrderByCreatedAtDesc(
        Long investorId, Long portfolioId
    );
}

