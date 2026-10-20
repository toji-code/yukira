package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.lookthrough.*;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.MultiFundLookThroughService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MultiFundLookThroughRealDataIntegrationTest {

    @Autowired
    private MultiFundLookThroughService lookThroughService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private Long hdfcFlexiOptId;
    private Long ppfasElssOptId;
    private Long ppfasFlexiOptId;

    @BeforeEach
    void setUp() {
        SchemeOption hdfc = schemeOptionRepository.findAll().stream()
            .filter(o -> "118955".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("HDFC Flexi Cap #118955 not found"));
        hdfcFlexiOptId = hdfc.getId();

        SchemeOption ppfasElss = schemeOptionRepository.findAll().stream()
            .filter(o -> "147481".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("PPFAS ELSS #147481 not found"));
        ppfasElssOptId = ppfasElss.getId();

        SchemeOption ppfasFlexi = schemeOptionRepository.findAll().stream()
            .filter(o -> "122639".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("PPFAS Flexi Cap #122639 not found"));
        ppfasFlexiOptId = ppfasFlexi.getId();
    }

    @Test
    @DisplayName("Real Data: Computes multi-fund look-through, overlap, and concentration across HDFC Flexi Cap + PPFAS ELSS")
    void shouldEvaluateMultiFundLookThroughWithRealData() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // 60% HDFC Flexi Cap + 40% Parag Parikh ELSS
        MultiFundLookThroughRequestDto req = new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(ppfasElssOptId, new BigDecimal("40.00"))
            ),
            asOfDate,
            cutoff,
            "RAW"
        );

        MultiFundLookThroughResponseDto response = lookThroughService.calculateMultiFundLookThrough(req);

        assertThat(response).isNotNull();
        assertThat(response.qualityState()).isEqualTo("COMPUTED_VALIDATED_INPUTS");
        assertThat(response.isFullyAllocated()).isTrue();
        assertThat(response.constituentFunds()).hasSize(2);

        // Verify that securities were aggregated
        assertThat(response.consolidatedSecurities()).isNotEmpty();
        assertThat(response.consolidatedSecurities().size()).isGreaterThan(50);

        // Top holdings should include major shared securities (e.g. HDFC Bank, ICICI Bank, etc.)
        ConsolidatedSecurityExposureDto topSec = response.consolidatedSecurities().get(0);
        assertThat(topSec.consolidatedWeightPct()).isGreaterThan(BigDecimal.ZERO);
        assertThat(topSec.contributions()).isNotEmpty();

        // Verify sector aggregation
        assertThat(response.consolidatedSectors()).isNotEmpty();
        assertThat(response.consolidatedSectors()).anyMatch(s -> "Financial Services".equals(s.macroSector()));

        // Verify market-cap aggregation
        assertThat(response.consolidatedMarketCap()).isNotEmpty();
        assertThat(response.consolidatedMarketCap()).anyMatch(m -> "LARGE_CAP".equals(m.category()) && m.consolidatedWeightPct().compareTo(BigDecimal.ZERO) > 0);

        // Verify pairwise overlap
        assertThat(response.pairwiseOverlapMatrix()).hasSize(1);
        PairwiseOverlapResultDto overlap = response.pairwiseOverlapMatrix().get(0);
        assertThat(overlap.comparabilityState()).isEqualTo("SYNCHRONIZED");
        assertThat(overlap.sharedSecurityCount()).isGreaterThan(0);
        assertThat(overlap.overlapWeightPct()).isGreaterThan(BigDecimal.ZERO);

        // Verify concentration metrics
        PortfolioConcentrationMetricsDto conc = response.concentrationMetrics();
        assertThat(conc.top5WeightPct()).isGreaterThan(BigDecimal.ZERO);
        assertThat(conc.top10WeightPct()).isGreaterThan(conc.top5WeightPct());
        assertThat(conc.herfindahlHirschmanIndex()).isGreaterThan(BigDecimal.ZERO);
        assertThat(conc.uniqueSecuritiesCount()).isGreaterThan(50);
        assertThat(conc.sharedSecuritiesCount()).isGreaterThan(0);

        // Verify Real Risk Decomposition
        assertThat(response.riskDecomposition()).isNotNull();
        PortfolioRiskDecompositionDto risk = response.riskDecomposition();
        assertThat(risk.evaluationStatus()).isEqualTo("COMPUTED_VALIDATED_INPUTS");
        assertThat(risk.alignedTradingDaysCount()).isGreaterThan(200);
        assertThat(risk.portfolioRealizedVolatilityPct()).isGreaterThan(BigDecimal.ZERO);
        assertThat(risk.weightedAverageFundVolatilityPct()).isGreaterThan(BigDecimal.ZERO);
        assertThat(risk.diversificationBenefitPct()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(risk.portfolioBeta()).isNotNull();
        assertThat(risk.constituentRiskProfiles()).hasSize(2);
        assertThat(risk.pairwiseCorrelations()).hasSize(1);

        // Verify Factor Gates
        assertThat(risk.factorExposures()).hasSize(6);
        assertThat(risk.factorExposures()).anyMatch(f -> "SIZE".equals(f.factorCode()) && "VALIDATED_PROXY".equals(f.status()));
        assertThat(risk.factorExposures()).anyMatch(f -> "MARKET_BETA".equals(f.factorCode()) && "EMPIRICALLY_COMPUTED".equals(f.status()));
        assertThat(risk.factorExposures()).anyMatch(f -> "LOW_VOLATILITY".equals(f.factorCode()) && "EMPIRICALLY_COMPUTED".equals(f.status()));
        assertThat(risk.factorExposures()).anyMatch(f -> "VALUE".equals(f.factorCode()) && "FACTOR_INPUTS_UNAVAILABLE".equals(f.status()));
        assertThat(risk.factorExposures()).anyMatch(f -> "QUALITY".equals(f.factorCode()) && "FACTOR_INPUTS_UNAVAILABLE".equals(f.status()));
        assertThat(risk.factorExposures()).anyMatch(f -> "MOMENTUM".equals(f.factorCode()) && "FACTOR_INPUTS_UNAVAILABLE".equals(f.status()));
    }

    @Test
    @DisplayName("Real Data: Simulates adding Parag Parikh Flexi Cap to baseline portfolio")
    void shouldSimulateCandidateAdditionWithRealData() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Baseline: 60% HDFC Flexi Cap + 40% Parag Parikh ELSS
        // Add 20% Parag Parikh Flexi Cap
        CandidateSimulationRequestDto simReq = new CandidateSimulationRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(ppfasElssOptId, new BigDecimal("40.00"))
            ),
            ppfasFlexiOptId,
            new BigDecimal("20.00"),
            "PROPORTIONAL",
            asOfDate,
            cutoff
        );

        CandidateSimulationResponseDto simResponse = lookThroughService.simulateCandidateFund(simReq);

        assertThat(simResponse).isNotNull();
        assertThat(simResponse.candidateFundName()).contains("Parag Parikh Flexi Cap");
        assertThat(simResponse.baselinePortfolio()).isNotNull();
        assertThat(simResponse.simulatedPortfolio()).isNotNull();

        // Exact deltas
        assertThat(simResponse.deltaTop5WeightPct()).isNotNull();
        assertThat(simResponse.deltaHhi()).isNotNull();
        assertThat(simResponse.topAugmentedSecurities()).isNotEmpty();
        assertThat(simResponse.analyticalObservations()).isNotEmpty();

        // Exact Risk Deltas
        assertThat(simResponse.deltaVolatilityPct()).isNotNull();
        assertThat(simResponse.deltaDiversificationBenefitPct()).isNotNull();
        assertThat(simResponse.deltaBeta()).isNotNull();
        assertThat(simResponse.deltaMaxDrawdownPct()).isNotNull();
    }
}
