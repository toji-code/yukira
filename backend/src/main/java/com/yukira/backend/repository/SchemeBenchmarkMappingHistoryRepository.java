package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeBenchmarkMappingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeBenchmarkMappingHistoryRepository extends JpaRepository<SchemeBenchmarkMappingHistory, Long> {

    List<SchemeBenchmarkMappingHistory> findBySchemeIdOrderByEffectiveStartDateDesc(Long schemeId);

    @Query("""
        SELECT m FROM SchemeBenchmarkMappingHistory m
        WHERE m.scheme.id = :schemeId
          AND m.effectiveStartDate <= :asOfDate
          AND (m.effectiveEndDate IS NULL OR m.effectiveEndDate >= :asOfDate)
        ORDER BY m.effectiveStartDate DESC
        """)
    Optional<SchemeBenchmarkMappingHistory> findActiveMappingAsOf(
        @Param("schemeId") Long schemeId,
        @Param("asOfDate") LocalDate asOfDate
    );
}

