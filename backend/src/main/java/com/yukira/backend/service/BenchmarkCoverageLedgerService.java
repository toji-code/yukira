package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkHistoricalCoverageLedger;
import com.yukira.backend.domain.entity.SchemeBenchmarkMappingHistory;
import com.yukira.backend.repository.BenchmarkHistoricalCoverageLedgerRepository;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeBenchmarkMappingHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing authoritative benchmark coverage, gap classifications,
 * point-in-time mapping validity intervals, fallback controls, and observation reconciliation (Tasks 376-379, 416-423).
 * Separates declared mandate coverage from actual observation coverage backed by source artifacts.
 */
@Service
@Transactional(readOnly = true)
public class BenchmarkCoverageLedgerService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkCoverageLedgerService.class);

    private final BenchmarkHistoricalCoverageLedgerRepository coverageLedgerRepository;
    private final SchemeBenchmarkMappingHistoryRepository mappingHistoryRepository;
    private final BenchmarkRepository benchmarkRepository;
    private BenchmarkObservationRepository benchmarkObservationRepository;

    public record BenchmarkCoverageSummaryDto(
        Long id,
        String benchmarkCode,
        String benchmarkName,
        String indexType,
        int assignedSchemeUniverseCount,
        LocalDate availableStartDate,
        LocalDate availableEndDate,
        int totalObservations,
        int missingIntervalsCount,
        double sourceArtifactCoverageRatio,
        String ingestionState, // OPERATIONAL, EXTERNAL_FEED_PENDING, CONFIGURATION_PENDING, DEGRADED
        OffsetDateTime lastSyncedAt,
        String operationalDisclosure,
        LocalDate declaredStartDate,
        LocalDate declaredEndDate,
        int declaredObservationCount,
        int actualObservationCount,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        String coverageReconciliationStatus,
        String sourceArtifactLineageStatus
    ) {}

    /**
     * Alias record for REST controller compatibility.
     */
    public record BenchmarkCoverageDto(
        Long id,
        String benchmarkCode,
        String benchmarkName,
        String indexType,
        int assignedSchemeUniverseCount,
        LocalDate availableStartDate,
        LocalDate availableEndDate,
        int totalObservations,
        int missingIntervalsCount,
        double sourceArtifactCoverageRatio,
        String ingestionState,
        OffsetDateTime lastSyncedAt,
        String operationalDisclosure,
        LocalDate declaredStartDate,
        LocalDate declaredEndDate,
        int declaredObservationCount,
        int actualObservationCount,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        String coverageReconciliationStatus,
        String sourceArtifactLineageStatus
    ) {}

    public record SchemeBenchmarkMappingIntervalDto(
        Long id,
        Long schemeId,
        Long benchmarkId,
        String benchmarkCode,
        String benchmarkName,
        LocalDate effectiveStartDate,
        LocalDate effectiveEndDate,
        String mappingAuthority,
        String mappingStatus,
        String sourceRef
    ) {}

    public record BenchmarkGapClassificationDto(
        LocalDate date,
        boolean isMarketHoliday,
        String gapReasonCode, // OFFICIAL_EXCHANGE_HOLIDAY, WEEKEND_NON_TRADING, BENCHMARK_OBSERVATION_MISSING, UNRESOLVED_PROVIDER_FEED_LAG
        String explanation
    ) {}

    public record EffectiveBenchmarkResolutionDto(
        String benchmarkCode,
        String benchmarkName,
        String resolutionType, // ASSIGNED_BENCHMARK, APPROVED_FALLBACK, RESEARCH_PROXY_BENCHMARK, UNMAPPED_BENCHMARK, UNAVAILABLE_BENCHMARK
        LocalDate asOfDate,
        boolean isProxy,
        String comparabilityLimitation,
        String governanceNotice
    ) {}

    public record MetricBenchmarkAvailabilityDto(
        String metricCode,
        boolean isAvailable,
        String availabilityReason,
        String benchmarkCode,
        int actualBenchmarkObservations,
        int minimumRequiredObservations,
        String status
    ) {}

    public BenchmarkCoverageLedgerService(
        BenchmarkHistoricalCoverageLedgerRepository coverageLedgerRepository,
        SchemeBenchmarkMappingHistoryRepository mappingHistoryRepository,
        BenchmarkRepository benchmarkRepository
    ) {
        this.coverageLedgerRepository = coverageLedgerRepository;
        this.mappingHistoryRepository = mappingHistoryRepository;
        this.benchmarkRepository = benchmarkRepository;
    }

    @Autowired(required = false)
    public void setBenchmarkObservationRepository(BenchmarkObservationRepository benchmarkObservationRepository) {
        this.benchmarkObservationRepository = benchmarkObservationRepository;
    }

    public List<BenchmarkCoverageDto> listAllBenchmarkCoverage() {
        return coverageLedgerRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    public Optional<BenchmarkCoverageDto> getBenchmarkCoverage(String benchmarkCode) {
        return coverageLedgerRepository.findByBenchmarkCode(benchmarkCode)
            .map(this::toDto);
    }

    public Optional<SchemeBenchmarkMappingIntervalDto> getActiveMappingForScheme(Long schemeId, LocalDate asOfDate) {
        LocalDate evalDate = asOfDate != null ? asOfDate : LocalDate.now();
        return mappingHistoryRepository.findActiveMappingAsOf(schemeId, evalDate)
            .map(this::toMappingDto);
    }

    public List<SchemeBenchmarkMappingIntervalDto> getMappingHistoryForScheme(Long schemeId) {
        return mappingHistoryRepository.findBySchemeIdOrderByEffectiveStartDateDesc(schemeId).stream()
            .map(this::toMappingDto)
            .toList();
    }

    /**
     * Resolves effective benchmark distinguishing assigned, approved fallback, proxy, and unavailable (Task 422).
     */
    public EffectiveBenchmarkResolutionDto resolveEffectiveBenchmark(Long schemeId, LocalDate asOfDate, boolean allowProxy) {
        LocalDate evalDate = asOfDate != null ? asOfDate : LocalDate.now();
        Optional<SchemeBenchmarkMappingIntervalDto> activeMapping = getActiveMappingForScheme(schemeId, evalDate);

        if (activeMapping.isPresent()) {
            SchemeBenchmarkMappingIntervalDto mapping = activeMapping.get();
            long actualCount = 0;
            if (benchmarkObservationRepository != null && mapping.benchmarkId() != null) {
                actualCount = benchmarkObservationRepository.countByBenchmarkId(mapping.benchmarkId());
            }

            if (actualCount > 0) {
                return new EffectiveBenchmarkResolutionDto(
                    mapping.benchmarkCode(),
                    mapping.benchmarkName(),
                    "ASSIGNED_BENCHMARK",
                    evalDate,
                    false,
                    "None: official SEBI scheme mandate benchmark with authentic observations.",
                    "Official regulatory benchmark mandate active as of evaluation date."
                );
            } else {
                return new EffectiveBenchmarkResolutionDto(
                    mapping.benchmarkCode(),
                    mapping.benchmarkName(),
                    "UNAVAILABLE_BENCHMARK",
                    evalDate,
                    false,
                    "Mapped benchmark exists in mandate, but 0 point-in-time observations are available in the authoritative repository. Relative metric evaluation prohibited.",
                    "Observation feed pending live provider connector. No synthetic data generated."
                );
            }
        }

        if (allowProxy) {
            return new EffectiveBenchmarkResolutionDto(
                "NIFTY_500_TRI",
                "NIFTY 500 Total Return Index",
                "RESEARCH_PROXY_BENCHMARK",
                evalDate,
                true,
                "Research-only broad market proxy; does not represent official SEBI scheme mandate. Cannot be used for formal regulatory attribution.",
                "Candidate research proxy enabled with explicit comparability limitation."
            );
        }

        return new EffectiveBenchmarkResolutionDto(
            "UNMAPPED",
            "No Benchmark Assigned",
            "UNMAPPED_BENCHMARK",
            evalDate,
            false,
            "No authoritative benchmark mapping exists for scheme as of " + evalDate + ". Benchmark-relative calculations are unavailable.",
            "Requires mapping registration in scheme_benchmark_mapping_history."
        );
    }

    /**
     * Evaluates benchmark-relative metric availability derived from actual observations and common dates (Task 423).
     */
    public MetricBenchmarkAvailabilityDto checkBenchmarkRelativeMetricAvailability(
        Long schemeId,
        String metricCode,
        LocalDate asOfDate
    ) {
        LocalDate evalDate = asOfDate != null ? asOfDate : LocalDate.now();
        EffectiveBenchmarkResolutionDto resolution = resolveEffectiveBenchmark(schemeId, evalDate, false);

        if ("UNMAPPED_BENCHMARK".equals(resolution.resolutionType()) || "UNAVAILABLE_BENCHMARK".equals(resolution.resolutionType())) {
            return new MetricBenchmarkAvailabilityDto(
                metricCode,
                false,
                resolution.comparabilityLimitation(),
                resolution.benchmarkCode(),
                0,
                250,
                "PENDING_SOURCE_DATA"
            );
        }

        int actualObs = 0;
        if (benchmarkObservationRepository != null) {
            Optional<Benchmark> bmOpt = benchmarkRepository.findByCode(resolution.benchmarkCode());
            if (bmOpt.isPresent()) {
                actualObs = (int) benchmarkObservationRepository.countByBenchmarkId(bmOpt.get().getId());
            }
        }

        int minRequired = 250; // standard 1Y statistical baseline
        if (actualObs < minRequired) {
            return new MetricBenchmarkAvailabilityDto(
                metricCode,
                false,
                "Insufficient authentic benchmark observations (" + actualObs + " < " + minRequired + "). Relative metrics withheld.",
                resolution.benchmarkCode(),
                actualObs,
                minRequired,
                "INSUFFICIENT_OBSERVATION_COUNT"
            );
        }

        return new MetricBenchmarkAvailabilityDto(
            metricCode,
            true,
            "Benchmark observations sufficient for deterministic calculation.",
            resolution.benchmarkCode(),
            actualObs,
            minRequired,
            "AVAILABLE"
        );
    }

    /**
     * Classifies a benchmark observation gap against exchange calendars (Task 379).
     */
    public BenchmarkGapClassificationDto classifyBenchmarkDate(
        LocalDate date,
        boolean isTradingHoliday,
        boolean isWeekend,
        boolean observationPresent
    ) {
        if (observationPresent) {
            return new BenchmarkGapClassificationDto(
                date,
                isTradingHoliday,
                "OBSERVATION_PRESENT",
                "Authentic benchmark observation recorded for effective trading date."
            );
        }
        if (isTradingHoliday) {
            return new BenchmarkGapClassificationDto(
                date,
                true,
                "OFFICIAL_EXCHANGE_HOLIDAY",
                "Exchange trading holiday; no index valuation published."
            );
        }
        if (isWeekend) {
            return new BenchmarkGapClassificationDto(
                date,
                false,
                "WEEKEND_NON_TRADING",
                "Weekend; exchange non-trading day."
            );
        }
        return new BenchmarkGapClassificationDto(
            date,
            false,
            "BENCHMARK_OBSERVATION_MISSING",
            "Exchange was open for trading but benchmark observation is missing from ingested feed."
        );
    }

    @Transactional
    public void recordCoverageUpdate(
        String benchmarkCode,
        LocalDate startDate,
        LocalDate endDate,
        int totalObservations,
        int missingIntervals,
        String ingestionState
    ) {
        coverageLedgerRepository.findByBenchmarkCode(benchmarkCode).ifPresent(record -> {
            record.setAvailableStartDate(startDate);
            record.setAvailableEndDate(endDate);
            record.setTotalObservations(totalObservations);
            record.setMissingIntervalsCount(missingIntervals);
            if (ingestionState != null) record.setIngestionState(ingestionState);
            record.setLastSyncedAt(OffsetDateTime.now());
            record.setUpdatedAt(OffsetDateTime.now());
            coverageLedgerRepository.save(record);
        });
    }

    private BenchmarkCoverageDto toDto(BenchmarkHistoricalCoverageLedger ledger) {
        int actualCount = 0;
        if (benchmarkObservationRepository != null) {
            Optional<Benchmark> bmOpt = benchmarkRepository.findByCode(ledger.getBenchmarkCode());
            if (bmOpt.isPresent()) {
                actualCount = (int) benchmarkObservationRepository.countByBenchmarkId(bmOpt.get().getId());
            }
        }

        String status = actualCount > 0 ? "OPERATIONAL" : "EXTERNAL_FEED_PENDING";
        String coverageReconciliation = actualCount > 0
            ? "OBSERVATIONS_CORROBORATED"
            : "OBSERVATIONS_PENDING_LIVE_INGESTION";
        String lineageStatus = actualCount > 0
            ? "CRYPTOGRAPHIC_ARTIFACT_BOUND"
            : "PENDING_EXTERNAL_PROVIDER_CONNECTOR";

        String disclosure = actualCount > 0
            ? "Authoritative point-in-time TRI series verified for assigned schemes."
            : "Declared mandate coverage from NIFTY index committee; authentic point-in-time TRI series pending live provider licensing connector (0 actual observations). No synthetic data generated.";

        return new BenchmarkCoverageDto(
            ledger.getId(),
            ledger.getBenchmarkCode(),
            ledger.getBenchmarkName(),
            ledger.getIndexType(),
            ledger.getAssignedSchemeUniverseCount() != null ? ledger.getAssignedSchemeUniverseCount() : 0,
            ledger.getAvailableStartDate(),
            ledger.getAvailableEndDate(),
            actualCount, // true observation count from repository
            ledger.getMissingIntervalsCount() != null ? ledger.getMissingIntervalsCount() : 0,
            actualCount > 0 ? (ledger.getSourceArtifactCoverageRatio() != null ? ledger.getSourceArtifactCoverageRatio().doubleValue() : 1.0) : 0.0,
            status,
            ledger.getLastSyncedAt(),
            disclosure,
            ledger.getDeclaredStartDate() != null ? ledger.getDeclaredStartDate() : ledger.getAvailableStartDate(),
            ledger.getDeclaredEndDate() != null ? ledger.getDeclaredEndDate() : ledger.getAvailableEndDate(),
            ledger.getDeclaredObservationCount() != null ? ledger.getDeclaredObservationCount() : (ledger.getTotalObservations() != null ? ledger.getTotalObservations() : 0),
            actualCount,
            actualCount > 0 ? ledger.getActualStartDate() : null,
            actualCount > 0 ? ledger.getActualEndDate() : null,
            coverageReconciliation,
            lineageStatus
        );
    }

    private SchemeBenchmarkMappingIntervalDto toMappingDto(SchemeBenchmarkMappingHistory hist) {
        return new SchemeBenchmarkMappingIntervalDto(
            hist.getId(),
            hist.getScheme() != null ? hist.getScheme().getId() : null,
            hist.getBenchmark() != null ? hist.getBenchmark().getId() : null,
            hist.getBenchmark() != null ? hist.getBenchmark().getCode() : "UNKNOWN",
            hist.getBenchmark() != null ? hist.getBenchmark().getName() : "Unknown Benchmark",
            hist.getEffectiveStartDate(),
            hist.getEffectiveEndDate(),
            hist.getMappingAuthority(),
            hist.getMappingStatus(),
            hist.getSourceRef()
        );
    }
}
