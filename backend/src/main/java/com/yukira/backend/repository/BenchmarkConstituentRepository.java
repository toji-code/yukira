package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.BenchmarkConstituent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for historical point-in-time Benchmark Constituent observations.
 *
 * Epistemic & Constitutional Principles (AGENTS.md):
 * 1. Dual Cutoffs: Both effectiveDate <= asOfDate AND availabilityTime <= knowledgeCutoff.
 * 2. Revision Isolation: Revisions published after knowledgeCutoff must NEVER leak into past queries.
 * 3. Append-only corrections: Multiple revisions for the same effective date are resolved
 *    to the latest revision available as of knowledgeCutoff.
 */
@Repository
public interface BenchmarkConstituentRepository extends JpaRepository<BenchmarkConstituent, Long> {

    @Query("SELECT bc FROM BenchmarkConstituent bc " +
           "WHERE bc.benchmark.id = :benchmarkId " +
           "AND bc.effectiveDate = (" +
           "    SELECT MAX(bc2.effectiveDate) FROM BenchmarkConstituent bc2 " +
           "    WHERE bc2.benchmark.id = :benchmarkId " +
           "    AND bc2.effectiveDate <= :asOfDate " +
           "    AND bc2.availabilityTime <= :knowledgeCutoff" +
           ") " +
           "AND bc.availabilityTime <= :knowledgeCutoff " +
           "AND bc.revisionSeq = (" +
           "    SELECT MAX(bc3.revisionSeq) FROM BenchmarkConstituent bc3 " +
           "    WHERE bc3.benchmark.id = :benchmarkId " +
           "    AND bc3.isin = bc.isin " +
           "    AND bc3.effectiveDate = bc.effectiveDate " +
           "    AND bc3.availabilityTime <= :knowledgeCutoff" +
           ") " +
           "AND bc.isMember = true " +
           "ORDER BY bc.weight DESC NULLS LAST, bc.companyName ASC")
    List<BenchmarkConstituent> findEligibleConstituents(
        @Param("benchmarkId") Long benchmarkId,
        @Param("asOfDate") LocalDate asOfDate,
        @Param("knowledgeCutoff") OffsetDateTime knowledgeCutoff
    );

    @Query("SELECT bc FROM BenchmarkConstituent bc " +
           "WHERE bc.benchmark.id = :benchmarkId " +
           "AND bc.effectiveDate = :effectiveDate " +
           "AND bc.availabilityTime <= :knowledgeCutoff " +
           "AND bc.revisionSeq = (" +
           "    SELECT MAX(bc3.revisionSeq) FROM BenchmarkConstituent bc3 " +
           "    WHERE bc3.benchmark.id = :benchmarkId " +
           "    AND bc3.isin = bc.isin " +
           "    AND bc3.effectiveDate = bc.effectiveDate " +
           "    AND bc3.availabilityTime <= :knowledgeCutoff" +
           ") " +
           "AND bc.isMember = true " +
           "ORDER BY bc.weight DESC NULLS LAST, bc.companyName ASC")
    List<BenchmarkConstituent> findByBenchmarkAndDateAndCutoff(
        @Param("benchmarkId") Long benchmarkId,
        @Param("effectiveDate") LocalDate effectiveDate,
        @Param("knowledgeCutoff") OffsetDateTime knowledgeCutoff
    );

    @Query("SELECT DISTINCT bc.effectiveDate FROM BenchmarkConstituent bc " +
           "WHERE bc.benchmark.id = :benchmarkId " +
           "AND bc.availabilityTime <= :knowledgeCutoff " +
           "ORDER BY bc.effectiveDate DESC")
    List<LocalDate> findAvailableCompositionDates(
        @Param("benchmarkId") Long benchmarkId,
        @Param("knowledgeCutoff") OffsetDateTime knowledgeCutoff
    );

    List<BenchmarkConstituent> findByBenchmarkIdAndEffectiveDate(Long benchmarkId, LocalDate effectiveDate);

    boolean existsByBenchmarkIdAndEffectiveDateAndRevisionSeq(Long benchmarkId, LocalDate effectiveDate, Integer revisionSeq);

    long countByBenchmarkIdAndEffectiveDate(Long benchmarkId, LocalDate effectiveDate);

    @Query("SELECT COALESCE(MAX(bc.revisionSeq), 0) FROM BenchmarkConstituent bc " +
           "WHERE bc.benchmark.id = :benchmarkId AND bc.effectiveDate = :effectiveDate")
    int findMaxRevisionSeq(
        @Param("benchmarkId") Long benchmarkId,
        @Param("effectiveDate") LocalDate effectiveDate
    );

    Optional<BenchmarkConstituent> findTopByBenchmarkIdAndEffectiveDateOrderByRevisionSeqDesc(
        Long benchmarkId, LocalDate effectiveDate
    );

    Optional<BenchmarkConstituent> findTopByBenchmarkIdAndIsinAndEffectiveDateOrderByRevisionSeqDesc(
        Long benchmarkId, String isin, LocalDate effectiveDate
    );

    @Query("SELECT bc.effectiveDate, bc.availabilityTime, bc.sourceArtifact.id, bc.sourceArtifact.sha256Hash, " +
           "bc.sourceArtifact.storageUri, COUNT(bc), COUNT(bc.weight), SUM(COALESCE(bc.weight, 0)), " +
           "SUM(CASE WHEN bc.mappingStatus = 'MAPPED' THEN 1 ELSE 0 END) " +
           "FROM BenchmarkConstituent bc " +
           "WHERE bc.benchmark.id = :benchmarkId " +
           "AND bc.availabilityTime <= :knowledgeCutoff " +
           "AND bc.isLatestRevision = true " +
           "GROUP BY bc.effectiveDate, bc.availabilityTime, bc.sourceArtifact.id, bc.sourceArtifact.sha256Hash, bc.sourceArtifact.storageUri " +
           "ORDER BY bc.effectiveDate DESC")
    List<Object[]> findSnapshotLedgerSummaries(
        @Param("benchmarkId") Long benchmarkId,
        @Param("knowledgeCutoff") OffsetDateTime knowledgeCutoff
    );
}
