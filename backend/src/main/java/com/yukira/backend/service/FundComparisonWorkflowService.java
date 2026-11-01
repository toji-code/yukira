package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class FundComparisonWorkflowService {

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final PeriodReturnCalculationService returnCalculationService;
    private final RiskCalculationService riskCalculationService;
    private final HoldingsConstructionService holdingsConstructionService;
    private final HoldingsOverlapService holdingsOverlapService;
    private final SchemeBenchmarkMappingRepository benchmarkMappingRepository;

    public FundComparisonWorkflowService(
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        PeriodReturnCalculationService returnCalculationService,
        RiskCalculationService riskCalculationService,
        HoldingsConstructionService holdingsConstructionService,
        HoldingsOverlapService holdingsOverlapService,
        SchemeBenchmarkMappingRepository benchmarkMappingRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.returnCalculationService = returnCalculationService;
        this.riskCalculationService = riskCalculationService;
        this.holdingsConstructionService = holdingsConstructionService;
        this.holdingsOverlapService = holdingsOverlapService;
        this.benchmarkMappingRepository = benchmarkMappingRepository;
    }

    public record FundComparisonColumnDto(
        Long schemeOptionId,
        Long schemeId,
        String schemeName,
        String optionName,
        String amcName,
        String category,
        String planType,
        String optionType,
        String amfiSchemeCode,
        String isin,
        BigDecimal latestNav,
        LocalDate latestNavDate,
        Double ret02OneYear,
        Double ret02ThreeYear,
        Double ret02FiveYear,
        Double rsk01Volatility,
        Double maxDrawdown,
        String primaryBenchmarkCode,
        Double top5Concentration,
        Double top10Concentration,
        Double equityPercentage,
        Double debtPercentage,
        Double cashPercentage,
        String dataReadiness
    ) {}

    public record PairwiseOverlapMatrixCellDto(
        Long optionIdA,
        Long optionIdB,
        double overlapPercentage,
        boolean dateMismatch
    ) {}

    public record ComprehensiveFundComparisonDto(
        List<FundComparisonColumnDto> funds,
        List<PairwiseOverlapMatrixCellDto> overlapMatrix,
        LocalDate comparisonDate,
        String epistemicGovernanceNotice
    ) {}

    public ComprehensiveFundComparisonDto compareFunds(List<Long> schemeOptionIds) {
        if (schemeOptionIds == null || schemeOptionIds.isEmpty()) {
            return new ComprehensiveFundComparisonDto(List.of(), List.of(), LocalDate.now(), "No funds provided for comparison");
        }

        List<FundComparisonColumnDto> columns = new ArrayList<>();
        LocalDate today = LocalDate.now();
        OffsetDateTime kc = OffsetDateTime.now();

        for (Long optId : schemeOptionIds) {
            Optional<SchemeOption> optEntity = schemeOptionRepository.findById(optId);
            if (optEntity.isEmpty()) continue;

            SchemeOption opt = optEntity.get();
            Scheme scheme = opt.getScheme();
            SchemePlan plan = opt.getSchemePlan();
            Amc amc = scheme != null ? scheme.getAmc() : null;

            var latestObs = navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(optId);
            BigDecimal nav = latestObs.map(NavObservation::getNavValue).orElse(null);
            LocalDate navDate = latestObs.map(NavObservation::getEffectiveDate).orElse(null);
            long count = navObservationRepository.countBySchemeOptionId(optId);

            Double r1 = null;
            Double r3 = null;
            Double r5 = null;
            Double vol = null;
            Double mdd = null;

            if (navDate != null) {
                try {
                    if (count >= 200) {
                        var res1 = returnCalculationService.calculatePeriodReturn(optId, navDate.minusYears(1), navDate, kc);
                        if (res1.isCalculated()) r1 = res1.getReturnPercentage();
                    }
                    if (count >= 600) {
                        var res3 = returnCalculationService.calculatePeriodReturn(optId, navDate.minusYears(3), navDate, kc);
                        if (res3.isCalculated()) r3 = res3.getReturnPercentage();
                    }
                    if (count >= 1000) {
                        var res5 = returnCalculationService.calculatePeriodReturn(optId, navDate.minusYears(5), navDate, kc);
                        if (res5.isCalculated()) r5 = res5.getReturnPercentage();
                    }
                    if (count >= 200) {
                        var vRes = riskCalculationService.calculateRsk01Volatility(optId, navDate.minusYears(1), navDate, kc);
                        if (vRes.isCalculated()) vol = vRes.getMetricValue();
                    }
                } catch (Exception ignored) {}
            }

            // Benchmark
            String bmCode = "NIFTY_500_TRI";
            if (scheme != null) {
                var bmMap = benchmarkMappingRepository.findBySchemeId(scheme.getId());
                if (!bmMap.isEmpty() && bmMap.get(0).getBenchmark() != null) {
                    bmCode = bmMap.get(0).getBenchmark().getCode();
                }
            }

            // Holdings construction
            Double top5 = null;
            Double top10 = null;
            Double eqPct = null;
            Double debtPct = null;
            Double cashPct = null;

            var hcOpt = holdingsConstructionService.getHoldingsConstruction(optId, null);
            if (hcOpt.isPresent()) {
                var hc = hcOpt.get();
                top5 = hc.top5ConcentrationPercentage();
                top10 = hc.top10ConcentrationPercentage();
                if (hc.assetAllocation() != null) {
                    eqPct = hc.assetAllocation().netEquityPercentage();
                    debtPct = hc.assetAllocation().debtPercentage();
                    cashPct = hc.assetAllocation().cashPercentage();
                }
            }

            String readiness = count >= 600 ? "COMPLETE" : (count >= 200 ? "PARTIAL" : "INSUFFICIENT");

            columns.add(new FundComparisonColumnDto(
                opt.getId(),
                scheme != null ? scheme.getId() : null,
                scheme != null ? scheme.getName() : "Unknown",
                opt.getOptionName(),
                amc != null ? amc.getName() : "Unknown",
                scheme != null ? scheme.getCategory() : "Unknown",
                plan != null ? plan.getPlanType() : "DIRECT",
                opt.getOptionType() != null ? opt.getOptionType() : "GROWTH",
                opt.getAmfiSchemeCode(),
                opt.getIsin(),
                nav,
                navDate,
                r1, r3, r5, vol, mdd,
                bmCode,
                top5, top10,
                eqPct, debtPct, cashPct,
                readiness
            ));
        }

        // Pairwise Overlap Matrix
        List<PairwiseOverlapMatrixCellDto> matrix = new ArrayList<>();
        for (int i = 0; i < schemeOptionIds.size(); i++) {
            for (int j = i + 1; j < schemeOptionIds.size(); j++) {
                Long idA = schemeOptionIds.get(i);
                Long idB = schemeOptionIds.get(j);
                try {
                    var overlap = holdingsOverlapService.calculatePairwiseOverlap(idA, idB);
                    matrix.add(new PairwiseOverlapMatrixCellDto(idA, idB, overlap.totalOverlapPercentage(), overlap.hasReportingDateMismatch()));
                } catch (Exception e) {
                    matrix.add(new PairwiseOverlapMatrixCellDto(idA, idB, 0.0, false));
                }
            }
        }

        return new ComprehensiveFundComparisonDto(
            columns,
            matrix,
            today,
            "RET-02 (Simple Period Return) is the approved operational baseline. Other risk and ratio metrics represent candidate methodologies. Calculations are point-in-time and deterministic."
        );
    }
}

