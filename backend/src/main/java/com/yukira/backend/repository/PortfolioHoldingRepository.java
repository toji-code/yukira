package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {
    List<PortfolioHolding> findByPortfolioSnapshotIdOrderByReportedWeightDesc(Long portfolioSnapshotId);
    List<PortfolioHolding> findByPortfolioSnapshotIdOrderByHoldingRankAsc(Long portfolioSnapshotId);

    @Query("SELECT h FROM PortfolioHolding h WHERE h.portfolioSnapshot.id = :snapshotId ORDER BY h.reportedWeight DESC")
    List<PortfolioHolding> findBySnapshotIdOrderByWeightPercentageDesc(@Param("snapshotId") Long snapshotId);
}
