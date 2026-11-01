package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.InvestorStressFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestorStressFindingRepository extends JpaRepository<InvestorStressFinding, Long> {

    List<InvestorStressFinding> findByInvestorIdOrderByCreatedAtDesc(Long investorId);

    List<InvestorStressFinding> findByInvestorIdAndLifecycleStatusOrderByCreatedAtDesc(Long investorId, String lifecycleStatus);

    List<InvestorStressFinding> findByInvestorIdAndPortfolioIdOrderByCreatedAtDesc(Long investorId, Long portfolioId);

    List<InvestorStressFinding> findByInvestorIdAndPortfolioIdAndLifecycleStatusOrderByCreatedAtDesc(
        Long investorId, Long portfolioId, String lifecycleStatus
    );

    List<InvestorStressFinding> findByCalculationRunId(Long calculationRunId);

    List<InvestorStressFinding> findByInvestorIdAndFindingIdOrderByFindingVersionDesc(Long investorId, String findingId);

    Optional<InvestorStressFinding> findFirstByInvestorIdAndFindingIdOrderByFindingVersionDesc(Long investorId, String findingId);

    @Query("""
        SELECT f FROM InvestorStressFinding f
        WHERE f.investor.id = :investorId
          AND (:portfolioId IS NULL OR f.portfolioId = :portfolioId)
          AND f.episodeCode = :episodeCode
          AND f.findingType = :findingType
          AND f.lifecycleStatus = 'ACTIVE'
        ORDER BY f.findingVersion DESC
        """)
    List<InvestorStressFinding> findActiveSimilarFindings(
        @Param("investorId") Long investorId,
        @Param("portfolioId") Long portfolioId,
        @Param("episodeCode") String episodeCode,
        @Param("findingType") String findingType
    );
}

