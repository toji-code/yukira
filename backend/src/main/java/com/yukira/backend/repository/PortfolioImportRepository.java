package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.PortfolioImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioImportRepository extends JpaRepository<PortfolioImport, Long> {
    List<PortfolioImport> findByInvestorIdOrderByCreatedAtDesc(Long investorId);
    Optional<PortfolioImport> findByIdAndInvestorId(Long id, Long investorId);
}
