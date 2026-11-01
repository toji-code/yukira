package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.CapitalMarketCalendarVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CapitalMarketCalendarVersionRepository extends JpaRepository<CapitalMarketCalendarVersion, Long> {
    List<CapitalMarketCalendarVersion> findByExchangeAndIsActiveTrueOrderByEffectiveYearAsc(String exchange);
    Optional<CapitalMarketCalendarVersion> findByExchangeAndEffectiveYearAndIsActiveTrue(String exchange, Integer effectiveYear);
}

