package com.yukira.backend.repository;

import com.yukira.backend.domain.entity.BenchmarkHistoricalCoverageLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BenchmarkHistoricalCoverageLedgerRepository extends JpaRepository<BenchmarkHistoricalCoverageLedger, Long> {

    Optional<BenchmarkHistoricalCoverageLedger> findByBenchmarkCode(String benchmarkCode);

    List<BenchmarkHistoricalCoverageLedger> findByIngestionState(String ingestionState);
}

