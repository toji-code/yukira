package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.SchemeBenchmarkHist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeBenchmarkHistRepository extends JpaRepository<SchemeBenchmarkHist, Long> {

    List<SchemeBenchmarkHist> findBySchemeId(Long schemeId);

    @Query("""
        SELECT sb FROM SchemeBenchmarkHist sb
        WHERE sb.scheme.id = :schemeId
          AND sb.effectiveFrom <= :asOfDate
          AND (sb.effectiveTo IS NULL OR sb.effectiveTo >= :asOfDate)
          AND sb.isPrimary = TRUE
        ORDER BY sb.effectiveFrom DESC, sb.id DESC
        """)
    List<SchemeBenchmarkHist> findPrimaryBenchmarksAsOfDate(@Param("schemeId") Long schemeId, @Param("asOfDate") LocalDate asOfDate);

    default Optional<SchemeBenchmarkHist> findPrimaryBenchmarkAsOfDate(Long schemeId, LocalDate asOfDate) {
        List<SchemeBenchmarkHist> list = findPrimaryBenchmarksAsOfDate(schemeId, asOfDate);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Query("""
        SELECT sb FROM SchemeBenchmarkHist sb
        JOIN FETCH sb.benchmark b
        JOIN FETCH sb.scheme s
        WHERE sb.isPrimary = TRUE
        """)
    List<SchemeBenchmarkHist> findAllPrimaryMappingsWithBenchmark();
}
