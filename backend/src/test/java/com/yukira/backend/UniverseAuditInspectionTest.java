package com.yukira.backend;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
public class UniverseAuditInspectionTest {

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Autowired
    private RiskFreeObservationRepository riskFreeObservationRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Audit Current AMFI Universe Database Snapshot (Optimized)")
    void auditCurrentUniverseState() {
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        System.out.println("============================================================");
        System.out.println("YUKIRA DATABASE UNIVERSE SNAPSHOT AUDIT");
        System.out.println("============================================================");
        System.out.println("Total Scheme Options in DB: " + allOptions.size());

        long activeCount = allOptions.stream()
            .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()) || o.getStatus() == null)
            .count();
        System.out.println("Active Scheme Options: " + activeCount);

        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<String, Integer> planTypeCounts = new HashMap<>();
        Map<String, Integer> optionTypeCounts = new HashMap<>();

        for (SchemeOption opt : allOptions) {
            String cat = "UNKNOWN";
            if (opt.getPlan() != null && opt.getPlan().getScheme() != null && opt.getPlan().getScheme().getCategory() != null) {
                cat = opt.getPlan().getScheme().getCategory();
            }
            categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0) + 1);

            String planType = opt.getPlan() != null ? opt.getPlan().getPlanType() : "UNKNOWN";
            planTypeCounts.put(planType, planTypeCounts.getOrDefault(planType, 0) + 1);

            String optionType = opt.getOptionType() != null ? opt.getOptionType() : "UNKNOWN";
            optionTypeCounts.put(optionType, optionTypeCounts.getOrDefault(optionType, 0) + 1);
        }

        // Single query for NAV counts per scheme_option_id
        @SuppressWarnings("unchecked")
        List<Object[]> navCounts = entityManager.createNativeQuery(
            "SELECT scheme_option_id, COUNT(*) FROM nav_observation GROUP BY scheme_option_id"
        ).getResultList();

        Map<Long, Long> optionNavMap = new HashMap<>();
        for (Object[] row : navCounts) {
            Long sid = ((Number) row[0]).longValue();
            Long cnt = ((Number) row[1]).longValue();
            optionNavMap.put(sid, cnt);
        }

        long navCountGte700 = 0;
        long navCountGte500 = 0;
        long navCountWithAnyNav = 0;
        long navCountZero = 0;

        for (SchemeOption opt : allOptions) {
            long obs = optionNavMap.getOrDefault(opt.getId(), 0L);
            if (obs >= 700) {
                navCountGte700++;
            } else if (obs >= 500) {
                navCountGte500++;
            }
            if (obs > 0) {
                navCountWithAnyNav++;
            } else {
                navCountZero++;
            }
        }

        System.out.println("\nCategory Distribution:");
        categoryCounts.forEach((k, v) -> System.out.println("  - " + k + ": " + v));

        System.out.println("\nPlan Type Distribution:");
        planTypeCounts.forEach((k, v) -> System.out.println("  - " + k + ": " + v));

        System.out.println("\nOption Type Distribution:");
        optionTypeCounts.forEach((k, v) -> System.out.println("  - " + k + ": " + v));

        System.out.println("\nNAV Coverage Statistics:");
        System.out.println("  - Options with >0 NAV observations: " + navCountWithAnyNav);
        System.out.println("  - Options with >=700 NAV observations (3-year complete): " + navCountGte700);
        System.out.println("  - Options with >=500 NAV observations (3-year partial): " + (navCountGte500 + navCountGte700));
        System.out.println("  - Options with 0 NAV observations: " + navCountZero);

        List<Benchmark> benchmarks = benchmarkRepository.findAll();
        System.out.println("\nBenchmark Coverage:");
        for (Benchmark b : benchmarks) {
            long obsCount = benchmarkObservationRepository.countByBenchmarkIdAndDateRange(
                b.getId(), LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 31)
            );
            System.out.println("  - Benchmark [" + b.getCode() + "] (" + b.getName() + "): " + obsCount + " observations in 2021-2024 range");
        }

        long riskFreeObs = riskFreeObservationRepository.countByBenchmarkCodeAndDateRange(
            "FBIL_91D_TBILL", LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 31)
        );
        System.out.println("\nRisk-Free Coverage (FBIL 91D T-Bill): " + riskFreeObs + " observations");

        List<AnalyticalScore> existingScores = analyticalScoreRepository.findAll();
        System.out.println("\nExisting Persisted Analytical Scores: " + existingScores.size());
        for (AnalyticalScore s : existingScores) {
            System.out.println("  - Score ID " + s.getId() + " | SchemeOption #" + s.getSchemeOption().getId()
                + " | Score: " + s.getScore() + " | Confidence: " + s.getConfidence()
                + " | Status: " + s.getStatus() + " | AsOf: " + s.getAsOfDate());
        }

        System.out.println("\n============================================================");
        System.out.println("AUDIT OF OPTIONS WITH >=500 NAV OBSERVATIONS:");
        System.out.println("============================================================");
        for (SchemeOption opt : allOptions) {
            long obs = optionNavMap.getOrDefault(opt.getId(), 0L);
            if (obs >= 500) {
                String schemeName = opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getName() : "N/A";
                String category = opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getCategory() : "N/A";
                String plan = opt.getPlan() != null ? opt.getPlan().getPlanType() : "N/A";
                String optType = opt.getOptionType();
                System.out.println(String.format("ID: %d | AMFI: %s | Name: %s | Cat: %s | Plan: %s | Option: %s | NAV Obs: %d",
                    opt.getId(), opt.getAmfiCode(), schemeName, category, plan, optType, obs));
            }
        }
        System.out.println("============================================================");

        assertNotNull(allOptions);
    }
}
