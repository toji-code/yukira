package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    default java.util.List<PortfolioSnapshot> findBySchemeOptionIdOrderByPortfolioDateDesc(Long schemeOptionId) {
        return findBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId);
    }

    List<PortfolioSnapshot> findBySchemeOptionIdAndPortfolioDateLessThanEqualOrderByPortfolioDateDesc(Long schemeOptionId, LocalDate portfolioDate);

    @Query("SELECT s FROM PortfolioSnapshot s WHERE s.schemeOption.plan.scheme.id = :schemeId ORDER BY s.portfolioDate DESC")
    List<PortfolioSnapshot> findBySchemeIdOrderByReportingDateDesc(@Param("schemeId") Long schemeId);

    boolean existsBySchemeOptionIdAndPortfolioDateAfter(Long schemeOptionId, LocalDate portfolioDate);
}
