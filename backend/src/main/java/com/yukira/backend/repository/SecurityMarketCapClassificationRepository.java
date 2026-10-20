package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SecurityMarketCapClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityMarketCapClassificationRepository extends JpaRepository<SecurityMarketCapClassification, Long> {

    @Query("SELECT c FROM SecurityMarketCapClassification c " +
           "WHERE c.effectiveFrom <= :asOfDate " +
           "AND (c.effectiveTo IS NULL OR c.effectiveTo >= :asOfDate) " +
           "AND c.availabilityTime <= :cutoff " +
           "AND c.isLatestRevision = true")
    List<SecurityMarketCapClassification> findEligibleClassifications(
        @Param("asOfDate") LocalDate asOfDate,
        @Param("cutoff") OffsetDateTime cutoff
    );

    @Query("SELECT c FROM SecurityMarketCapClassification c " +
           "WHERE c.isin = :isin " +
           "AND c.effectiveFrom <= :asOfDate " +
           "AND (c.effectiveTo IS NULL OR c.effectiveTo >= :asOfDate) " +
           "AND c.availabilityTime <= :cutoff " +
           "ORDER BY c.revisionSeq DESC")
    List<SecurityMarketCapClassification> findByIsinAndPit(
        @Param("isin") String isin,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("cutoff") OffsetDateTime cutoff
    );

    @Query("SELECT c FROM SecurityMarketCapClassification c " +
           "WHERE c.security.id = :securityId " +
           "AND c.effectiveFrom <= :asOfDate " +
           "AND (c.effectiveTo IS NULL OR c.effectiveTo >= :asOfDate) " +
           "AND c.availabilityTime <= :cutoff " +
           "ORDER BY c.revisionSeq DESC")
    List<SecurityMarketCapClassification> findBySecurityIdAndPit(
        @Param("securityId") Long securityId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("cutoff") OffsetDateTime cutoff
    );

    List<SecurityMarketCapClassification> findByRankingPeriodEndDateOrderByMarketCapRankAsc(LocalDate rankingPeriodEndDate);

    boolean existsByRankingPeriodEndDateAndRevisionSeq(LocalDate rankingPeriodEndDate, Integer revisionSeq);

    long countByRankingPeriodEndDate(LocalDate rankingPeriodEndDate);
}
