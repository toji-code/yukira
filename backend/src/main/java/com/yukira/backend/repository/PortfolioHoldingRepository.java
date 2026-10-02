package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {
    List<PortfolioHolding> findByPortfolioSnapshotIdOrderByReportedWeightDesc(Long portfolioSnapshotId);
}
