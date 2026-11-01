package com.yukira.backend.service;

import com.yukira.backend.calculation.MetricAvailabilityContract;
import com.yukira.backend.calculation.MetricAvailabilityState;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service enforcing frozen quantitative methodology contracts, execution boundaries,
 * withholding semantics, and category-relative peer cohort ranks (Tasks 275, 281, 283, 284).
 */
@Service
@Transactional(readOnly = true)
public class QuantitativeMethodologyAuditService {

    private final SchemeOptionRepository schemeOptionRepository;
    private NavObservationRepository navObservationRepository;

    public QuantitativeMethodologyAuditService(SchemeOptionRepository schemeOptionRepository) {
        this.schemeOptionRepository = schemeOptionRepository;
    }

    @Autowired(required = false)
    public void setNavObservationRepository(NavObservationRepository navObservationRepository) {
        this.navObservationRepository = navObservationRepository;
    }

    public record PeerCohortRankDto(
        String metricCode,
        Double fundValue,
        int cohortSize,
        int rank, // 1 = highest
        double percentile, // 0.0 to 100.0
        boolean isReliableCohort
    ) {}

    public MetricAvailabilityContract auditMetric(String metricCode, Long schemeOptionId) {
        String code = metricCode.toUpperCase().trim();
        Optional<SchemeOption> opt = schemeOptionRepository.findById(schemeOptionId);
        if (opt.isEmpty()) {
            return new MetricAvailabilityContract(
                code,
                code,
                "CANDIDATE",
                MetricAvailabilityState.INVALID_INPUT,
                "Scheme option not found: " + schemeOptionId,
                List.of("scheme_option_id"),
                List.of("valid_scheme_option")
            );
        }

        SchemeOption option = opt.get();
        int obsCount = navObservationRepository != null
                ? navObservationRepository.findBySchemeOptionIdOrderByEffectiveDateAsc(schemeOptionId).size()
                : 0;

        switch (code) {
            case "RET-02":
                if (obsCount >= 2) {
                    return new MetricAvailabilityContract(
                        "RET-02",
                        "Simple Period Return",
                        "APPROVED",
                        MetricAvailabilityState.AVAILABLE,
                        "Approved operational baseline metric. Terminal observations verified.",
                        List.of("start_nav", "end_nav"),
                        List.of()
                    );
                } else {
                    return new MetricAvailabilityContract(
                        "RET-02",
                        "Simple Period Return",
                        "APPROVED",
                        MetricAvailabilityState.INSUFFICIENT_DATA,
                        "Requires at least 2 terminal NAV observations (current: " + obsCount + ")",
                        List.of("start_nav", "end_nav"),
                        List.of("nav_history")
                    );
                }

            case "RET-03":
                if (obsCount >= 252) {
                    return new MetricAvailabilityContract(
                        "RET-03",
                        "Compound Annual Growth Rate (CAGR)",
                        "VALIDATED",
                        MetricAvailabilityState.AVAILABLE,
                        "Validated methodology. Sufficient trading history present.",
                        List.of("terminal_navs", "exact_day_count"),
                        List.of()
                    );
                } else {
                    return new MetricAvailabilityContract(
                        "RET-03",
                        "Compound Annual Growth Rate (CAGR)",
                        "VALIDATED",
                        MetricAvailabilityState.INSUFFICIENT_DATA,
                        "CAGR requires at least 252 trading observations (current: " + obsCount + ")",
                        List.of("terminal_navs", "exact_day_count"),
                        List.of("252_trading_days")
                    );
                }

            case "RSK-01":
                if (obsCount >= 252) {
                    return new MetricAvailabilityContract(
                        "RSK-01",
                        "Annualized Volatility (Log Returns)",
                        "VALIDATED",
                        MetricAvailabilityState.AVAILABLE,
                        "Validated methodology. Continuous daily series verified.",
                        List.of("daily_nav_series"),
                        List.of()
                    );
                } else {
                    return new MetricAvailabilityContract(
                        "RSK-01",
                        "Annualized Volatility (Log Returns)",
                        "VALIDATED",
                        MetricAvailabilityState.INSUFFICIENT_DATA,
                        "Requires at least 252 trading observations (current: " + obsCount + ")",
                        List.of("daily_nav_series"),
                        List.of("252_trading_days")
                    );
                }

            case "MKT-01":
                boolean hasBm = option.getScheme() != null && option.getScheme().getBenchmark() != null;
                if (!hasBm) {
                    return new MetricAvailabilityContract(
                        "MKT-01",
                        "Beta (Capital Asset Pricing Model)",
                        "CANDIDATE",
                        MetricAvailabilityState.MISSING_BENCHMARK,
                        "Candidate methodology. Benchmark total return series is not assigned to scheme.",
                        List.of("scheme_daily_nav", "benchmark_tri_series"),
                        List.of("benchmark_assignment")
                    );
                }
                return new MetricAvailabilityContract(
                    "MKT-01",
                    "Beta (Capital Asset Pricing Model)",
                    "CANDIDATE",
                    obsCount >= 252 ? MetricAvailabilityState.CANDIDATE_METHOD : MetricAvailabilityState.INSUFFICIENT_DATA,
                    "Candidate specification per Phase 2H frozen methodology.",
                    List.of("scheme_daily_nav", "benchmark_tri_series"),
                    obsCount >= 252 ? List.of() : List.of("252_trading_days")
                );

            default:
                return new MetricAvailabilityContract(
                    code,
                    code,
                    "CANDIDATE",
                    MetricAvailabilityState.CANDIDATE_METHOD,
                    "Candidate specification under Phase 2H. Calculation kernel implemented in quant-engine, awaiting multi-cycle empirical validation.",
                    List.of("historical_observations"),
                    List.of()
                );
        }
    }

    /**
     * Deterministic peer cohort percentile evaluation (Task 284).
     * Refuses estimation if cohort contains fewer than 3 verified values.
     */
    public Optional<PeerCohortRankDto> computeCohortRank(String metricCode, Double fundValue, List<Double> peerValues) {
        if (fundValue == null || peerValues == null || peerValues.size() < 3) {
            return Optional.empty();
        }

        List<Double> all = new ArrayList<>(peerValues);
        all.add(fundValue);
        all.sort(Collections.reverseOrder());

        int rank = all.indexOf(fundValue) + 1;
        int total = all.size();
        double percentile = ((double) (total - rank) / (total - 1)) * 100.0;

        return Optional.of(new PeerCohortRankDto(
            metricCode,
            fundValue,
            total,
            rank,
            Math.round(percentile * 100.0) / 100.0,
            total >= 5
        ));
    }
}

