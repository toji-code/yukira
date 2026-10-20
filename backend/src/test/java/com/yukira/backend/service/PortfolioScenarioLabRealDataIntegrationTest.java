package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import com.yukira.backend.dto.portfolio.scenario.*;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.PortfolioScenarioLabService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
public class PortfolioScenarioLabRealDataIntegrationTest {

    @Autowired
    private PortfolioScenarioLabService scenarioLabService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private Long hdfcFlexiOptId;
    private Long ppfasFlexiOptId;
    private OffsetDateTime cutoff;

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

        cutoff = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Real Data: Retrieves metadata & available exposure dimensions for HDFC Flexi (60%) + PPFAS Flexi (40%)")
    void testRealDataMetadataRetrieval() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasFlexiOptId, new BigDecimal("40.00"))
        );

        ScenarioDimensionMetadataDto metadata = scenarioLabService.getDimensionMetadata(
            funds, "NIFTY_500_TRI", LocalDate.of(2026, 9, 30), cutoff
        );

        assertThat(metadata).isNotNull();
        assertThat(metadata.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(metadata.largeCapWeightPct()).isGreaterThan(BigDecimal.ZERO);
        assertThat(metadata.coveredWeightPct()).isGreaterThan(new BigDecimal("50.00"));
        assertThat(metadata.supportedTemplates()).isNotEmpty();
        assertThat(metadata.sectorWeightsPct()).containsKey("Financial Services");
    }

    @Test
    @DisplayName("Real Data: Executes Broad Market -10% Shock on Canonical Portfolio")
    void testRealDataBroadMarketShock() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasFlexiOptId, new BigDecimal("40.00"))
        );

        ScenarioLabRequestDto request = new ScenarioLabRequestDto(
            funds,
            "BROAD_MARKET",
            "Broad Market -10% Stress",
            "NIFTY_500_TRI",
            LocalDate.of(2026, 9, 30),
            cutoff,
            new BigDecimal("-10.00"),
            null, null, null, null, null, null, null
        );

        ScenarioLabResponseDto response = scenarioLabService.runScenario(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SENSITIVITY_ESTIMATE_ONLY");
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.constituentImpacts()).hasSize(2);

        // Beta is empirically computed against NIFTY 500 TRI
        if (response.portfolioBeta() != null) {
            assertThat(response.portfolioBeta().doubleValue()).isBetween(0.5, 1.5);
            assertThat(response.estimatedPortfolioImpactPct().doubleValue()).isBetween(-15.0, -5.0);

            // Reconciliation check: sum(constituent contribution) == portfolio impact
            BigDecimal sumContrib = response.constituentImpacts().stream()
                .map(ScenarioConstituentImpactDto::portfolioContributionPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sumContrib).isEqualByComparingTo(response.estimatedPortfolioImpactPct());
        }
    }

    @Test
    @DisplayName("Real Data: Executes Market-Cap Tiered Shocks and compares against Broad-Market Shock")
    void testRealDataMarketCapShocksAndComparison() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasFlexiOptId, new BigDecimal("40.00"))
        );

        LocalDate evalDate = LocalDate.of(2026, 9, 30);

        // Scenario A: Mild Market-Cap Shock
        ScenarioLabRequestDto scA = new ScenarioLabRequestDto(
            funds, "MARKET_CAP", "Mild Cap Shock", "NIFTY_500_TRI", evalDate, cutoff,
            null, new BigDecimal("-4.00"), new BigDecimal("-8.00"), new BigDecimal("-12.00"), BigDecimal.ZERO,
            null, null, null
        );

        // Scenario B: Severe Cap Shakeout
        ScenarioLabRequestDto scB = new ScenarioLabRequestDto(
            funds, "MARKET_CAP", "Severe Cap Shakeout", "NIFTY_500_TRI", evalDate, cutoff,
            null, new BigDecimal("-6.00"), new BigDecimal("-14.00"), new BigDecimal("-22.00"), BigDecimal.ZERO,
            null, null, null
        );

        ScenarioComparisonRequestDto compRequest = new ScenarioComparisonRequestDto(
            funds, evalDate, cutoff, "NIFTY_500_TRI", scA, scB
        );

        ScenarioComparisonResponseDto comparison = scenarioLabService.compareScenarios(compRequest);

        assertThat(comparison).isNotNull();
        assertThat(comparison.isComparable()).isTrue();
        assertThat(comparison.portfolioImpactDeltaPct()).isLessThan(BigDecimal.ZERO); // Scenario B has worse drawdown
        assertThat(comparison.divergenceSummary()).contains("Scenario B");
        assertThat(comparison.constituentDeltas()).hasSize(2);
    }

    @Test
    @DisplayName("Real Data: Executes Sector-Specific Shocks with Financials & IT drag")
    void testRealDataSectorSpecificShocks() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasFlexiOptId, new BigDecimal("40.00"))
        );

        ScenarioLabRequestDto request = new ScenarioLabRequestDto(
            funds, "SECTOR", "Financials & Tech Stress", "NIFTY_500_TRI",
            LocalDate.of(2026, 9, 30), cutoff,
            null, null, null, null, BigDecimal.ZERO,
            Map.of(
                "Financial Services", new BigDecimal("-15.00"),
                "Information Technology", new BigDecimal("-12.00")
            ),
            "EXPLICIT_ZERO_RETURN", "MUTUALLY_EXCLUSIVE_PARTITION"
        );

        ScenarioLabResponseDto response = scenarioLabService.runScenario(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SENSITIVITY_ESTIMATE_ONLY");
        assertThat(response.estimatedPortfolioImpactPct()).isLessThan(BigDecimal.ZERO);

        // Reconciles exposure contributions with portfolio impact
        BigDecimal sumExp = response.exposureImpacts().stream()
            .map(ScenarioExposureImpactDto::estimatedContributionPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumExp).isEqualByComparingTo(response.estimatedPortfolioImpactPct());
    }
}
