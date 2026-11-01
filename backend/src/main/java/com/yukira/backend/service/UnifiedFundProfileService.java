package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UnifiedFundProfileService {

    private final SchemeRepository schemeRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final SchemeBenchmarkMappingRepository benchmarkMappingRepository;
    private final SchemeDataCoverageRepository dataCoverageRepository;
    private final SchemeExpenseRatioRepository expenseRatioRepository;
    private final SchemeManagerHistRepository managerHistRepository;
    private final SchemeInvestmentTermsRepository investmentTermsRepository;
    private final PeriodReturnCalculationService returnCalculationService;
    private final RiskCalculationService riskCalculationService;

    public UnifiedFundProfileService(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        SchemeBenchmarkMappingRepository benchmarkMappingRepository,
        SchemeDataCoverageRepository dataCoverageRepository,
        SchemeExpenseRatioRepository expenseRatioRepository,
        SchemeManagerHistRepository managerHistRepository,
        SchemeInvestmentTermsRepository investmentTermsRepository,
        PeriodReturnCalculationService returnCalculationService,
        RiskCalculationService riskCalculationService
    ) {
        this.schemeRepository = schemeRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkMappingRepository = benchmarkMappingRepository;
        this.dataCoverageRepository = dataCoverageRepository;
        this.expenseRatioRepository = expenseRatioRepository;
        this.managerHistRepository = managerHistRepository;
        this.investmentTermsRepository = investmentTermsRepository;
        this.returnCalculationService = returnCalculationService;
        this.riskCalculationService = riskCalculationService;
    }

    public record OptionProfileSummaryDto(
        Long optionId,
        String optionName,
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
        Double maxDrawdown
    ) {}

    public record ManagerSummaryDto(
        String managerName,
        LocalDate fromDate,
        LocalDate toDate,
        boolean isCurrent
    ) {}

    public record ExpenseRatioSummaryDto(
        String planType,
        BigDecimal totalExpenseRatio,
        LocalDate effectiveDate
    ) {}

    public record CompositeFundProfileDto(
        Long schemeId,
        String schemeName,
        String schemeCode,
        String amcName,
        String amcCode,
        String category,
        String subCategory,
        String benchmarkName,
        String benchmarkCode,
        String dataCoverageStatus,
        LocalDate earliestObservationDate,
        LocalDate latestObservationDate,
        long totalObservations,
        List<OptionProfileSummaryDto> options,
        List<ManagerSummaryDto> managers,
        List<ExpenseRatioSummaryDto> expenseRatios,
        String exitLoadSummary,
        BigDecimal minimumSipAmount,
        BigDecimal minimumLumpsumAmount,
        String methodologyStatusWarning // "RET-02 is approved baseline; other metrics candidate"
    ) {}

    public Optional<CompositeFundProfileDto> getCompositeProfile(Long schemeId) {
        Optional<Scheme> schemeOpt = schemeRepository.findById(schemeId);
        if (schemeOpt.isEmpty()) return Optional.empty();

        Scheme scheme = schemeOpt.get();
        Amc amc = scheme.getAmc();

        // 1. Benchmark mapping
        String bmName = "Nifty 500 TRI (Default Category Proxy)";
        String bmCode = "NIFTY_500_TRI";
        var bmMap = benchmarkMappingRepository.findBySchemeId(schemeId);
        if (!bmMap.isEmpty() && bmMap.get(0).getBenchmark() != null) {
            bmName = bmMap.get(0).getBenchmark().getName();
            bmCode = bmMap.get(0).getBenchmark().getCode();
        }

        // 2. Data coverage
        var cov = dataCoverageRepository.findBySchemeId(schemeId);
        String covStatus = cov.map(SchemeDataCoverage::getNavCoverageStatus).orElse("SUPPORTED");
        LocalDate earliestDate = cov.map(SchemeDataCoverage::getEarliestNavDate).orElse(null);
        LocalDate latestDate = cov.map(SchemeDataCoverage::getLatestNavDate).orElse(null);

        // 3. Options
        List<SchemeOption> optionEntities = schemeOptionRepository.findBySchemeId(schemeId);
        List<OptionProfileSummaryDto> optionDtos = new ArrayList<>();
        long totalObs = 0;

        for (SchemeOption opt : optionEntities) {
            long count = navObservationRepository.countBySchemeOptionId(opt.getId());
            totalObs += count;

            var latestObs = navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(opt.getId());
            BigDecimal navVal = latestObs.map(NavObservation::getNavValue).orElse(null);
            LocalDate navDt = latestObs.map(NavObservation::getEffectiveDate).orElse(null);

            Double r1 = null;
            Double r3 = null;
            Double r5 = null;
            Double vol = null;
            Double mdd = null;

            if (navDt != null) {
                OffsetDateTime kc = OffsetDateTime.now();
                try {
                    // Safe calculation attempts
                    if (count >= 200) {
                        var res1 = returnCalculationService.calculatePeriodReturn(opt.getId(), navDt.minusYears(1), navDt, kc);
                        if (res1.isCalculated()) r1 = res1.getReturnPercentage();
                    }
                    if (count >= 600) {
                        var res3 = returnCalculationService.calculatePeriodReturn(opt.getId(), navDt.minusYears(3), navDt, kc);
                        if (res3.isCalculated()) r3 = res3.getReturnPercentage();
                    }
                    if (count >= 1000) {
                        var res5 = returnCalculationService.calculatePeriodReturn(opt.getId(), navDt.minusYears(5), navDt, kc);
                        if (res5.isCalculated()) r5 = res5.getReturnPercentage();
                    }
                    if (count >= 200) {
                        var volRes = riskCalculationService.calculateRsk01Volatility(opt.getId(), navDt.minusYears(1), navDt, kc);
                        if (volRes.isCalculated()) vol = volRes.getMetricValue();
                    }
                } catch (Exception ignored) {}
            }

            optionDtos.add(new OptionProfileSummaryDto(
                opt.getId(),
                opt.getOptionName(),
                opt.getSchemePlan() != null ? opt.getSchemePlan().getPlanType() : "DIRECT",
                opt.getOptionType() != null ? opt.getOptionType() : "GROWTH",
                opt.getAmfiSchemeCode(),
                opt.getIsin(),
                navVal,
                navDt,
                r1, r3, r5, vol, mdd
            ));
        }

        // 4. Managers
        List<ManagerSummaryDto> mgrDtos = new ArrayList<>();
        var managers = managerHistRepository.findBySchemeId(schemeId);
        for (SchemeManagerHist sm : managers) {
            mgrDtos.add(new ManagerSummaryDto(
                sm.getManagerName(),
                sm.getFromDate(),
                sm.getToDate(),
                sm.getToDate() == null
            ));
        }

        // 5. Expense Ratios
        List<ExpenseRatioSummaryDto> expDtos = new ArrayList<>();
        var expenses = expenseRatioRepository.findBySchemeId(schemeId);
        for (SchemeExpenseRatio er : expenses) {
            expDtos.add(new ExpenseRatioSummaryDto(
                er.getPlanType(),
                er.getTotalExpenseRatio(),
                er.getEffectiveDate()
            ));
        }

        // 6. Investment terms
        var terms = investmentTermsRepository.findTopBySchemeIdOrderByAsOfDateDesc(schemeId);
        String exitLoad = terms.map(SchemeInvestmentTerms::getExitLoadDescription).orElse("1% if redeemed within 1 year; Nil thereafter");
        BigDecimal minSip = terms.map(SchemeInvestmentTerms::getMinimumSipAmount).orElse(BigDecimal.valueOf(500));
        BigDecimal minLumpsum = terms.map(SchemeInvestmentTerms::getMinimumLumpSumAmount).orElse(BigDecimal.valueOf(1000));

        return Optional.of(new CompositeFundProfileDto(
            scheme.getId(),
            scheme.getName(),
            scheme.getCode(),
            amc != null ? amc.getName() : "Unknown",
            amc != null ? amc.getCode() : "Unknown",
            scheme.getCategory(),
            scheme.getSubCategory(),
            bmName,
            bmCode,
            covStatus,
            earliestDate,
            latestDate,
            totalObs,
            optionDtos,
            mgrDtos,
            expDtos,
            exitLoad,
            minSip,
            minLumpsum,
            "RET-02 (Simple Period Return) is the approved operational baseline. Other risk and ratio metrics represent candidate methodologies."
        ));
    }
}

