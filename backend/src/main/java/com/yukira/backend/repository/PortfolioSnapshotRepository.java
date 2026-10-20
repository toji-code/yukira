package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    Optional<PortfolioSnapshot> findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(Long schemeOptionId, OffsetDateTime knowledgeCutoff);

    default Optional<PortfolioSnapshot> findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDesc(Long schemeOptionId, OffsetDateTime knowledgeCutoff) {
        return findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, knowledgeCutoff);
    }

    Optional<PortfolioSnapshot> findTopBySchemeOptionIdAndPortfolioDateOrderByRevisionSeqDesc(Long schemeOptionId, LocalDate portfolioDate);

    default Optional<PortfolioSnapshot> findBySchemeOptionIdAndPortfolioDate(Long schemeOptionId, LocalDate portfolioDate) {
        return findTopBySchemeOptionIdAndPortfolioDateOrderByRevisionSeqDesc(schemeOptionId, portfolioDate);
    }

    Optional<PortfolioSnapshot> findTopBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(Long schemeOptionId);

    default Optional<PortfolioSnapshot> findTopBySchemeOptionIdOrderByPortfolioDateDesc(Long schemeOptionId) {
        return findTopBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId);
    }

    java.util.List<PortfolioSnapshot> findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(Long schemeOptionId, OffsetDateTime knowledgeCutoff);

    java.util.List<PortfolioSnapshot> findBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(Long schemeOptionId);

    boolean existsBySchemeOptionIdAndPortfolioDateAfter(Long schemeOptionId, LocalDate portfolioDate);
}
