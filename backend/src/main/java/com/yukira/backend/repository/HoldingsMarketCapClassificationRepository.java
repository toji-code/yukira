package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.HoldingsMarketCapClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingsMarketCapClassificationRepository extends JpaRepository<HoldingsMarketCapClassification, Long> {
    Optional<HoldingsMarketCapClassification> findByIsinAndEffectivePeriodStartLessThanEqualAndEffectivePeriodEndGreaterThanEqual(
            String isin, LocalDate date1, LocalDate date2);

    List<HoldingsMarketCapClassification> findByEffectivePeriodStartLessThanEqualAndEffectivePeriodEndGreaterThanEqual(
            LocalDate date1, LocalDate date2);

    @Query("SELECT m FROM HoldingsMarketCapClassification m WHERE m.isin = :isin ORDER BY m.effectivePeriodEnd DESC")
    List<HoldingsMarketCapClassification> findLatestByIsin(@Param("isin") String isin);
}

