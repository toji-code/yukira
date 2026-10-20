package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeBenchmarkHist;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.UniverseReadinessReportDto;
import com.yukira.backend.dto.analysis.UniverseReadinessReportDto.*;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.repository.SchemeBenchmarkHistRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class UniverseAnalyticalReadinessService {

    private static final Logger log = LoggerFactory.getLogger(UniverseAnalyticalReadinessService.class);

    private final SchemeOptionRepository schemeOptionRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final SchemeBenchmarkHistRepository schemeBenchmarkHistRepository;
    private final BenchmarkResolutionService benchmarkResolutionService;
    private final EntityManager entityManager;

    public UniverseAnalyticalReadinessService(
        SchemeOptionRepository schemeOptionRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        RiskFreeObservationRepository riskFreeObservationRepository,
        SchemeBenchmarkHistRepository schemeBenchmarkHistRepository,
        BenchmarkResolutionService benchmarkResolutionService,
        EntityManager entityManager
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.schemeBenchmarkHistRepository = schemeBenchmarkHistRepository;
        this.benchmarkResolutionService = benchmarkResolutionService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public UniverseReadinessReportDto generateReadinessReport(LocalDate asOfDate) {
        long startTime = System.currentTimeMillis();
        int queryCount = 0;

        if (asOfDate == null) {
            asOfDate = LocalDate.of(2024, 1, 15);
        }

        LocalDate start1Y = asOfDate.minusYears(1);
        LocalDate start3Y = asOfDate.minusYears(3);
        LocalDate start5Y = asOfDate.minusYears(5);

        // 1. Total catalog & Active Universe
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        queryCount++;
        long totalCatalog = allOptions.size();

        List<SchemeOption> activeOptions = allOptions.stream()
            .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()) || o.getStatus() == null)
            .toList();
        long activeUniverse = activeOptions.size();

        // 2. NAV Observations counts per option via native SQL
        @SuppressWarnings("unchecked")
        Object[] navBounds = (Object[]) entityManager.createNativeQuery(
            "SELECT MIN(effective_date), MAX(effective_date) FROM nav_observation"
        ).getSingleResult();
        log.info("Database nav_observation date range: MIN={}, MAX={}", navBounds[0], navBounds[1]);

        @SuppressWarnings("unchecked")
        List<Object[]> totalNavRows = entityManager.createNativeQuery(
            "SELECT scheme_option_id, COUNT(*) FROM nav_observation GROUP BY scheme_option_id"
        ).getResultList();
        queryCount++;
        Map<Long, Long> totalNavMap = new HashMap<>();
        for (Object[] r : totalNavRows) {
            totalNavMap.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> nav1YRows = entityManager.createNativeQuery(
            "SELECT scheme_option_id, COUNT(DISTINCT effective_date) FROM nav_observation " +
            "WHERE effective_date BETWEEN :sDate AND :eDate GROUP BY scheme_option_id"
        ).setParameter("sDate", start1Y).setParameter("eDate", asOfDate).getResultList();
        queryCount++;
        Map<Long, Long> nav1YMap = new HashMap<>();
        for (Object[] r : nav1YRows) {
            nav1YMap.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> nav3YRows = entityManager.createNativeQuery(
            "SELECT scheme_option_id, COUNT(DISTINCT effective_date) FROM nav_observation " +
            "WHERE effective_date BETWEEN :sDate AND :eDate GROUP BY scheme_option_id"
        ).setParameter("sDate", start3Y).setParameter("eDate", asOfDate).getResultList();
        queryCount++;
        Map<Long, Long> nav3YMap = new HashMap<>();
        for (Object[] r : nav3YRows) {
            nav3YMap.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> nav5YRows = entityManager.createNativeQuery(
            "SELECT scheme_option_id, COUNT(DISTINCT effective_date) FROM nav_observation " +
            "WHERE effective_date BETWEEN :sDate AND :eDate GROUP BY scheme_option_id"
        ).setParameter("sDate", start5Y).setParameter("eDate", asOfDate).getResultList();
        queryCount++;
        Map<Long, Long> nav5YMap = new HashMap<>();
        for (Object[] r : nav5YRows) {
            nav5YMap.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        // 3. Scheme -> Benchmark Mappings
        List<SchemeBenchmarkHist> bmMappings = schemeBenchmarkHistRepository.findAllPrimaryMappingsWithBenchmark();
        queryCount++;
        Map<Long, Benchmark> schemeBenchmarkMap = new HashMap<>();
        for (SchemeBenchmarkHist hist : bmMappings) {
            schemeBenchmarkMap.put(hist.getScheme().getId(), hist.getBenchmark());
        }

        // Default primary benchmark (e.g. NIFTY_500_TRI)
        Benchmark defaultBenchmark = benchmarkRepository.findByCode("NIFTY_500_TRI").orElse(null);
        queryCount++;
        if (defaultBenchmark == null) {
            List<Benchmark> bms = benchmarkRepository.findAll();
            queryCount++;
            if (!bms.isEmpty()) {
                defaultBenchmark = bms.get(0);
            }
        }

        // 4. Benchmark observations and readiness
        List<Benchmark> allBenchmarks = benchmarkRepository.findAll();
        queryCount++;
        List<BenchmarkAuditItem> benchmarkAudits = new ArrayList<>();
        Map<Long, Long> benchmark3YObsCountMap = new HashMap<>();
        Map<Long, Set<LocalDate>> benchmark3YDatesMap = new HashMap<>();

        for (Benchmark bm : allBenchmarks) {
            long totalBmObs = benchmarkObservationRepository.findByBenchmarkId(bm.getId()).size();
            queryCount++;

            @SuppressWarnings("unchecked")
            List<?> bmDateList = entityManager.createNativeQuery(
                "SELECT DISTINCT effective_date FROM benchmark_observation WHERE benchmark_id = :bId AND effective_date BETWEEN :sDate AND :eDate"
            ).setParameter("bId", bm.getId()).setParameter("sDate", start3Y).setParameter("eDate", asOfDate).getResultList();
            queryCount++;

            Set<LocalDate> bm3YDates = new HashSet<>();
            for (Object d : bmDateList) {
                LocalDate ld = toLocalDate(d);
                if (ld != null) bm3YDates.add(ld);
            }

            long obs3YCount = bm3YDates.size();
            benchmark3YObsCountMap.put(bm.getId(), obs3YCount);
            benchmark3YDatesMap.put(bm.getId(), bm3YDates);

            @SuppressWarnings("unchecked")
            Object[] dateBounds = (Object[]) entityManager.createNativeQuery(
                "SELECT MIN(effective_date), MAX(effective_date) FROM benchmark_observation WHERE benchmark_id = :bId"
            ).setParameter("bId", bm.getId()).getSingleResult();
            queryCount++;

            LocalDate minDate = toLocalDate(dateBounds[0]);
            LocalDate maxDate = toLocalDate(dateBounds[1]);

            boolean is1YReady = obs3YCount >= 250;
            boolean is3YReady = obs3YCount >= 700;

            long mappedCount = activeOptions.stream()
                .filter(o -> o.getPlan() != null && o.getPlan().getScheme() != null &&
                             bm.equals(schemeBenchmarkMap.get(o.getPlan().getScheme().getId())))
                .count();

            benchmarkAudits.add(new BenchmarkAuditItem(
                bm.getId(),
                bm.getCode(),
                bm.getName(),
                bm.getProvider(),
                totalBmObs,
                minDate,
                maxDate,
                0L,
                0L,
                0L,
                is1YReady,
                is3YReady,
                mappedCount,
                obs3YCount
            ));
        }

        // 5. Risk-free series audit
        long riskFreeObsCount = riskFreeObservationRepository.countByBenchmarkCodeAndDateRange(
            "FBIL_91D_TBILL", start3Y, asOfDate
        );
        queryCount++;

        @SuppressWarnings("unchecked")
        Object[] rfBounds = (Object[]) entityManager.createNativeQuery(
            "SELECT MIN(effective_date), MAX(effective_date) FROM risk_free_observation WHERE benchmark_code = 'FBIL_91D_TBILL'"
        ).getSingleResult();
        queryCount++;

        LocalDate rfMinDate = toLocalDate(rfBounds[0]);
        LocalDate rfMaxDate = toLocalDate(rfBounds[1]);
        boolean isRf3YReady = riskFreeObsCount >= 700;

        RiskFreeAuditItem riskFreeAudit = new RiskFreeAuditItem(
            "FBIL_91D_TBILL",
            riskFreeObsCount,
            rfMinDate,
            rfMaxDate,
            0L,
            isRf3YReady
        );

        // 6. Fund-level PIT pairing native query
        @SuppressWarnings("unchecked")
        List<Object[]> pairedRows = entityManager.createNativeQuery(
            "SELECT n.scheme_option_id, COUNT(DISTINCT n.effective_date) FROM nav_observation n " +
            "JOIN benchmark_observation b ON n.effective_date = b.effective_date " +
            "WHERE b.benchmark_id = :bmId " +
            "GROUP BY n.scheme_option_id"
        ).setParameter("bmId", defaultBenchmark != null ? defaultBenchmark.getId() : 1L)
         .getResultList();
        queryCount++;

        Map<Long, Long> pitPaired3YMap = new HashMap<>();
        for (Object[] r : pairedRows) {
            pitPaired3YMap.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }

        // 7. Aggregate Universe Readiness Counters & Blocker Classification
        long activeWithNavCount = 0;
        long activeZeroNavCount = 0;
        long active1YReadyCount = 0;
        long active3YReadyCount = 0;
        long active5YReadyCount = 0;
        long activeWithBenchmarkMappingCount = 0;
        long explicitBenchmarkMappedCount = 0;
        long fallbackBenchmarkCount = 0;
        long unmappedBenchmarkCount = 0;
        long activeWithValidBenchmarkHistoryCount = 0;
        long activeWithRiskFreeHistoryCount = 0;
        long active3YPitPairReadyCount = 0;
        long active3YQuantReadyCount = 0;
        long active3YScoreReadyCount = 0;

        Map<String, Long> blockerCounts = new LinkedHashMap<>();
        Map<String, List<String>> blockerExamples = new LinkedHashMap<>();

        List<String> blockerCategories = List.of(
            "NO_NAV_HISTORY",
            "INSUFFICIENT_NAV_HISTORY",
            "NO_BENCHMARK_MAPPING",
            "BENCHMARK_HISTORY_INSUFFICIENT",
            "RISK_FREE_HISTORY_INSUFFICIENT",
            "PIT_PAIRING_INSUFFICIENT",
            "REQUIRED_METRIC_UNAVAILABLE",
            "DATA_QUALITY_LIMITED",
            "SCORE_METHODOLOGY_EXCLUSION",
            "SCORE_READY"
        );
        for (String cat : blockerCategories) {
            blockerCounts.put(cat, 0L);
            blockerExamples.put(cat, new ArrayList<>());
        }

        List<SchemeOption> active3YReadyOptions = new ArrayList<>();

        for (SchemeOption opt : activeOptions) {
            long totalNav = totalNavMap.getOrDefault(opt.getId(), 0L);
            long nav1Y = nav1YMap.getOrDefault(opt.getId(), 0L);
            long nav3Y = nav3YMap.getOrDefault(opt.getId(), 0L);
            long nav5Y = nav5YMap.getOrDefault(opt.getId(), 0L);

            long effective1Y = Math.max(nav1Y, totalNav >= 250 ? totalNav : 0);
            long effective3Y = Math.max(nav3Y, totalNav >= 700 ? totalNav : 0);
            long effective5Y = Math.max(nav5Y, totalNav >= 1200 ? totalNav : 0);

            if (totalNav > 0) activeWithNavCount++; else activeZeroNavCount++;
            if (effective1Y >= 250) active1YReadyCount++;
            if (effective3Y >= 700) {
                active3YReadyCount++;
                active3YReadyOptions.add(opt);
            }
            if (effective5Y >= 1200) active5YReadyCount++;

            Long schemeId = opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getId() : null;
            Benchmark mappedBm = null;
            boolean isExplicit = false;

            if (schemeId != null && schemeBenchmarkMap.containsKey(schemeId)) {
                mappedBm = schemeBenchmarkMap.get(schemeId);
                isExplicit = true;
            } else if (defaultBenchmark != null) {
                mappedBm = defaultBenchmark;
                isExplicit = false;
            }

            if (isExplicit) {
                explicitBenchmarkMappedCount++;
                activeWithBenchmarkMappingCount++;
            } else if (mappedBm != null) {
                fallbackBenchmarkCount++;
                activeWithBenchmarkMappingCount++;
            } else {
                unmappedBenchmarkCount++;
            }

            boolean hasBenchmarkMapping = mappedBm != null;

            boolean hasValidBenchmarkHistory = mappedBm != null && benchmark3YObsCountMap.getOrDefault(mappedBm.getId(), 0L) >= 700;
            if (hasValidBenchmarkHistory) activeWithValidBenchmarkHistoryCount++;

            if (isRf3YReady) activeWithRiskFreeHistoryCount++;

            long pitPaired3Y = pitPaired3YMap.getOrDefault(opt.getId(), 0L);
            boolean isPitPairReady = pitPaired3Y >= 700;
            if (isPitPairReady) active3YPitPairReadyCount++;

            boolean isQuantReady = effective3Y >= 700 && hasValidBenchmarkHistory && isRf3YReady && isPitPairReady;
            if (isQuantReady) active3YQuantReadyCount++;

            boolean isScoreReady = isQuantReady && "GROWTH".equalsIgnoreCase(opt.getOptionType());
            if (isScoreReady) active3YScoreReadyCount++;

            // Evaluate Mutually Exclusive Blocker Category (for Active Universe / 3Y cohort)
            String category;
            if (totalNav == 0) {
                category = "NO_NAV_HISTORY";
            } else if (effective3Y < 700) {
                category = "INSUFFICIENT_NAV_HISTORY";
            } else if (!hasBenchmarkMapping) {
                category = "NO_BENCHMARK_MAPPING";
            } else if (!hasValidBenchmarkHistory) {
                category = "BENCHMARK_HISTORY_INSUFFICIENT";
            } else if (!isRf3YReady) {
                category = "RISK_FREE_HISTORY_INSUFFICIENT";
            } else if (!isPitPairReady) {
                category = "PIT_PAIRING_INSUFFICIENT";
            } else if (!"GROWTH".equalsIgnoreCase(opt.getOptionType())) {
                category = "SCORE_METHODOLOGY_EXCLUSION";
            } else {
                category = "SCORE_READY";
            }

            blockerCounts.put(category, blockerCounts.get(category) + 1);
            if (blockerExamples.get(category).size() < 3) {
                String name = opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getName() : "Option #" + opt.getId();
                blockerExamples.get(category).add(name + " (AMFI: " + opt.getAmfiCode() + ")");
            }
        }

        long active3YCohortSize = active3YReadyOptions.size();

        Map<String, BlockerStat> blockerClassification = new LinkedHashMap<>();
        for (String cat : blockerCategories) {
            long count = blockerCounts.get(cat);
            double pctTargetCohort = active3YCohortSize > 0 ? (double) count / active3YCohortSize * 100.0 : 0.0;
            double pctActiveUniv = activeUniverse > 0 ? (double) count / activeUniverse * 100.0 : 0.0;
            blockerClassification.put(cat, new BlockerStat(
                cat,
                count,
                Math.round(pctTargetCohort * 100.0) / 100.0,
                Math.round(pctActiveUniv * 100.0) / 100.0,
                blockerExamples.get(cat)
            ));
        }

        // 8. Funnel Construction
        List<FunnelStepItem> funnel = List.of(
            new FunnelStepItem("ACTIVE UNIVERSE", activeUniverse, 100.0, 0, "Initial active scheme option population"),
            new FunnelStepItem("NAV READY", activeWithNavCount, roundPct(activeWithNavCount, activeUniverse), activeUniverse - activeWithNavCount, "Zero NAV history in AMFI source"),
            new FunnelStepItem("3Y NAV READY", active3YReadyCount, roundPct(active3YReadyCount, activeUniverse), activeWithNavCount - active3YReadyCount, "Inception date < 3 years or incomplete 3Y NAV history"),
            new FunnelStepItem("EXPLICIT BENCHMARK MAPPED", explicitBenchmarkMappedCount, roundPct(explicitBenchmarkMappedCount, activeUniverse), active3YReadyCount - explicitBenchmarkMappedCount, "Scheme lacks explicit category benchmark mapping in scheme_benchmark_hist"),
            new FunnelStepItem("BENCHMARK HISTORY READY", activeWithValidBenchmarkHistoryCount, roundPct(activeWithValidBenchmarkHistoryCount, activeUniverse), explicitBenchmarkMappedCount - activeWithValidBenchmarkHistoryCount, "Benchmark history incomplete"),
            new FunnelStepItem("RISK-FREE READY", activeWithRiskFreeHistoryCount, roundPct(activeWithRiskFreeHistoryCount, activeUniverse), activeWithValidBenchmarkHistoryCount - activeWithRiskFreeHistoryCount, "FBIL risk-free rate series gap"),
            new FunnelStepItem("PIT PAIR READY", active3YPitPairReadyCount, roundPct(active3YPitPairReadyCount, activeUniverse), activeWithRiskFreeHistoryCount - active3YPitPairReadyCount, "Calendar mismatch between NAV dates and benchmark dates"),
            new FunnelStepItem("QUANT METRIC READY", active3YQuantReadyCount, roundPct(active3YQuantReadyCount, activeUniverse), active3YPitPairReadyCount - active3YQuantReadyCount, "Data quality/calculation exception"),
            new FunnelStepItem("YUKIRA SCORE READY", active3YScoreReadyCount, roundPct(active3YScoreReadyCount, activeUniverse), active3YQuantReadyCount - active3YScoreReadyCount, "Non-growth plan option or methodology exclusion")
        );

        long duration = System.currentTimeMillis() - startTime;
        long peakMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        AuditPerformanceMetrics perfMetrics = new AuditPerformanceMetrics(
            activeUniverse,
            queryCount,
            duration,
            peakMem
        );

        return new UniverseReadinessReportDto(
            asOfDate,
            totalCatalog,
            activeUniverse,
            activeWithNavCount,
            activeZeroNavCount,
            active1YReadyCount,
            active3YReadyCount,
            active5YReadyCount,
            activeWithBenchmarkMappingCount,
            explicitBenchmarkMappedCount,
            fallbackBenchmarkCount,
            unmappedBenchmarkCount,
            activeWithValidBenchmarkHistoryCount,
            activeWithRiskFreeHistoryCount,
            active3YPitPairReadyCount,
            active3YQuantReadyCount,
            active3YScoreReadyCount,
            blockerClassification,
            benchmarkAudits,
            riskFreeAudit,
            funnel,
            perfMetrics
        );
    }

    private double roundPct(long num, long den) {
        if (den == 0) return 0.0;
        return Math.round(((double) num / den * 100.0) * 100.0) / 100.0;
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate ld) return ld;
        if (obj instanceof java.sql.Date sd) return sd.toLocalDate();
        return LocalDate.parse(obj.toString());
    }
}
