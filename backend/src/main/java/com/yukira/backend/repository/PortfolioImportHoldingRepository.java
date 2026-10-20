package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.PortfolioImportHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioImportHoldingRepository extends JpaRepository<PortfolioImportHolding, Long> {
    List<PortfolioImportHolding> findByPortfolioImportId(Long portfolioImportId);
}
