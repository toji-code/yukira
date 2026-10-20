package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import com.yukira.backend.dto.portfolio.stress.*;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.PortfolioStressReplayService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
public class PortfolioStressReplayRealDataIntegrationTest {

    @Autowired
    private PortfolioStressReplayService replayService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private Long hdfcFlexiOptId;
    private Long ppfasFlexiOptId;
    private Long ppfasElssOptId;

    @BeforeEach
    void setUp() {
        SchemeOption hdfc = schemeOptionRepository.findAll().stream()
            .filter(o -> "118955".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("HDFC Flexi Cap #118955 not found"));
        hdfcFlexiOptId = hdfc.getId();

        SchemeOption ppfasFlexi = schemeOptionRepository.findAll().stream()
            .filter(o -> "122639".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("PPFAS Flexi Cap #122639 not found"));
        ppfasFlexiOptId = ppfasFlexi.getId();

        SchemeOption ppfasElss = schemeOptionRepository.findAll().stream()
            .filter(o -> "147481".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("PPFAS ELSS #147481 not found"));
        ppfasElssOptId = ppfasElss.getId();
    }

    @Test
    @DisplayName("Real Data: Replays 2021-2022 Global Rate-Hike Drawdown for HDFC Flexi (60%) + PPFAS Flexi (40%)")
    void testRealData2021RateHikeDrawdownReplay() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasFlexiOptId, new BigDecimal("40.00"))
        );

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds,
            "EPISODE_2021_2022_RATE_HIKE_DRAWDOWN",
            null,
            null,
            "NIFTY_500_TRI",
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto response = replayService.executeStressReplay(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("HYPOTHETICAL_RECONSTRUCTION");
        assertThat(response.episodeId()).isEqualTo("EPISODE_2021_2022_RATE_HIKE_DRAWDOWN");
        assertThat(response.alignedTradingDaysCount()).isEqualTo(166);

        // Benchmark (NIFTY 500 TRI) fell -17.50%
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.benchmarkName()).isEqualTo("Nifty 500 Total Returns Index");
        assertThat(response.benchmarkId()).isEqualTo(123L);
        assertThat(response.benchmarkCumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-17.50"));

        // Portfolio fell approx -13.29%
        assertThat(response.portfolioCumulativeReturnPct().doubleValue()).isBetween(-15.0, -11.0);

        // Active return was positive (~+4.21%)
        assertThat(response.activeReturnPct().doubleValue()).isGreaterThan(2.0);

        // Drawdowns are calculated
        assertThat(response.portfolioMaxDrawdownPct()).isLessThan(BigDecimal.ZERO);
        assertThat(response.benchmarkMaxDrawdownPct()).isLessThan(BigDecimal.ZERO);

        // Constituent results
        assertThat(response.constituentResults()).hasSize(2);
        ConstituentReplayResultDto hdfcRes = response.constituentResults().stream()
            .filter(c -> c.schemeOptionId().equals(hdfcFlexiOptId))
            .findFirst().orElseThrow();
        ConstituentReplayResultDto ppfasRes = response.constituentResults().stream()
            .filter(c -> c.schemeOptionId().equals(ppfasFlexiOptId))
            .findFirst().orElseThrow();

        assertThat(hdfcRes.cumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-10.41"));
        assertThat(ppfasRes.cumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-17.60"));

        // Trajectory is populated
        assertThat(response.trajectory()).hasSize(166);

        // Verify Carino attribution exact reconciliation
        assertThat(response.attributionConvention()).isEqualTo("CARINO_LOGARITHMIC_LINKING");
        BigDecimal sumContrib = hdfcRes.contributionPct().add(ppfasRes.contributionPct());
        assertThat(sumContrib.subtract(response.portfolioCumulativeReturnPct()).abs()).isLessThanOrEqualTo(new BigDecimal("0.02"));
        assertThat(response.attributionResidualPct()).isEqualByComparingTo(BigDecimal.ZERO);

        // Verify standalone linear buy-and-hold contributions & compounding residual
        assertThat(hdfcRes.standaloneContributionPct()).isNotNull();
        assertThat(ppfasRes.standaloneContributionPct()).isNotNull();
        assertThat(response.compoundingResidualPct()).isNotNull();

        // Modeled Sensitivity Scenarios
        assertThat(response.modeledScenarios()).hasSize(3);
        assertThat(response.modeledScenarios().get(0).epistemicStatus()).isEqualTo("SENSITIVITY_ESTIMATE_ONLY");
    }

    @Test
    @DisplayName("Real Data: Replays 2024 SEBI Mid/Small Cap Regulatory Shakeout for HDFC Flexi (60%) + PPFAS ELSS (40%)")
    void testRealData2024SebiShakeoutReplay() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasElssOptId, new BigDecimal("40.00"))
        );

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds,
            "EPISODE_2024_SEBI_MIDSMALL_SHAKEOUT",
            null,
            null,
            "NIFTY_500_TRI",
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto response = replayService.executeStressReplay(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("HYPOTHETICAL_RECONSTRUCTION");
        assertThat(response.alignedTradingDaysCount()).isEqualTo(28);
        assertThat(response.benchmarkCumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-1.81"));
        assertThat(response.constituentResults()).hasSize(2);

        // Attribution reconciliation
        BigDecimal sumContrib = response.constituentResults().stream()
            .map(ConstituentReplayResultDto::contributionPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumContrib.subtract(response.portfolioCumulativeReturnPct()).abs()).isLessThanOrEqualTo(new BigDecimal("0.02"));
        assertThat(response.attributionResidualPct()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Real Data: COVID-2020 Replay is rejected because observations are not available in dataset")
    void testRealDataCovidReplayRejected() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasFlexiOptId, new BigDecimal("40.00"))
        );

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds,
            "EPISODE_2020_COVID_SHOCK",
            null,
            null,
            "NIFTY_500_TRI",
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> replayService.executeStressReplay(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is not available for this portfolio");
    }

    @Test
    @DisplayName("Real Data: Reproduce Canonical Portfolio vs Look-Through Seed Portfolio and verify Benchmark Identity Integrity")
    void testRealDataCanonicalPortfolioComparisonAndBenchmarkIntegrity() {
        // Portfolio 1: Canonical Portfolio (60% HDFC Flexi #118955 + 40% PPFAS Flexi #122639)
        List<ResearchPortfolioFundItemDto> canonicalFunds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasFlexiOptId, new BigDecimal("40.00"))
        );

        PortfolioStressReplayRequestDto reqCanonical = new PortfolioStressReplayRequestDto(
            canonicalFunds,
            "EPISODE_2024_SEBI_MIDSMALL_SHAKEOUT",
            null,
            null,
            "NIFTY_500_TRI",
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto resCanonical = replayService.executeStressReplay(reqCanonical);

        // Benchmark identity must be exact and unambiguous
        assertThat(resCanonical.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(resCanonical.benchmarkName()).isEqualTo("Nifty 500 Total Returns Index");
        assertThat(resCanonical.benchmarkId()).isEqualTo(123L);
        assertThat(resCanonical.benchmarkCode()).isNotEqualTo("NIFTY_50");

        // Canonical Portfolio Return: +0.24%
        assertThat(resCanonical.portfolioCumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("0.24"));
        assertThat(resCanonical.benchmarkCumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-1.81"));
        assertThat(resCanonical.activeReturnPct()).isEqualByComparingTo(new BigDecimal("2.05"));
        assertThat(resCanonical.alignedTradingDaysCount()).isEqualTo(28);

        // Portfolio 2: Look-Through Seed Portfolio (60% HDFC Flexi #118955 + 40% PPFAS ELSS #147481)
        List<ResearchPortfolioFundItemDto> elssFunds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasElssOptId, new BigDecimal("40.00"))
        );

        PortfolioStressReplayRequestDto reqElss = new PortfolioStressReplayRequestDto(
            elssFunds,
            "EPISODE_2024_SEBI_MIDSMALL_SHAKEOUT",
            null,
            null,
            "NIFTY_500_TRI",
            OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto resElss = replayService.executeStressReplay(reqElss);

        // Look-Through Seed Portfolio Return: 0.00% (unrounded +0.0051%)
        assertThat(resElss.portfolioCumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(resElss.benchmarkCumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-1.81"));
        assertThat(resElss.activeReturnPct()).isEqualByComparingTo(new BigDecimal("1.81"));

        // Both portfolios reconcile Carino contributions to exact 0.00% attribution residual
        assertThat(resCanonical.attributionResidualPct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resElss.attributionResidualPct()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
