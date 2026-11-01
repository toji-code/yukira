package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.PortfolioImportTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioImportTransactionRepository extends JpaRepository<PortfolioImportTransaction, Long> {

    List<PortfolioImportTransaction> findByPortfolioImportIdOrderByTransactionDateDesc(Long portfolioImportId);

    List<PortfolioImportTransaction> findByPortfolioImportIdAndResolutionStatus(Long portfolioImportId, String resolutionStatus);

    long countByPortfolioImportId(Long portfolioImportId);

    long countByPortfolioImportIdAndResolutionStatus(Long portfolioImportId, String resolutionStatus);
}

