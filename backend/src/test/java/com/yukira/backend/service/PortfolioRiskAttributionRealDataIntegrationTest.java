package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.attribution.PortfolioRiskAttributionRequestDto;
import com.yukira.backend.dto.portfolio.attribution.PortfolioRiskAttributionResponseDto;
import com.yukira.backend.dto.portfolio.attribution.RiskReconciliationDto;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.PortfolioRiskAttributionService;
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
@SuppressWarnings("null")
public class PortfolioRiskAttributionRealDataIntegrationTest {

    @Autowired
    private PortfolioRiskAttributionService riskAttributionService;

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
            .orElseThrow(() -> new IllegalStateException("HDFC Flexi Cap #118955 not found in pilot database"));
        hdfcFlexiOptId = hdfc.getId();

        SchemeOption ppfasFlexi = schemeOptionRepository.findAll().stream()
            .filter(o -> "122639".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("PPFAS Flexi Cap #122639 not found in pilot database"));
        ppfasFlexiOptId = ppfasFlexi.getId();

        cutoff = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Real Data: Executes Covariance Risk Attribution on Canonical HDFC (60%) + PPFAS (40%) Portfolio")
    void testRealDataRiskAttributionExecution() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(ppfasFlexiOptId, new BigDecimal("40.00"))
        );

        PortfolioRiskAttributionRequestDto request = new PortfolioRiskAttributionRequestDto(
            funds,
            LocalDate.of(2026, 9, 30),
            cutoff,
            "NIFTY_500_TRI"
        );

        PortfolioRiskAttributionResponseDto response = riskAttributionService.calculateRiskAttribution(request);

        assertThat(response).isNotNull();
        // If observations exist for canonical funds in DB, status should be AVAILABLE; if sparse in test profile, it gates cleanly
        if ("AVAILABLE".equals(response.evaluationStatus())) {
            assertThat(response.alignedObservationCount()).isGreaterThanOrEqualTo(20);
            assertThat(response.fundContributions()).hasSize(2);

            // Euler mathematical reconciliation: sum(RC_i) = sigma_p
            RiskReconciliationDto recon = response.reconciliation();
            assertThat(recon).isNotNull();
            assertThat(recon.isReconciled()).isTrue();
            assertThat(recon.reconciliationDifferencePct()).isLessThanOrEqualTo(new BigDecimal("0.05"));

            // Section A: Overview Metrics
            assertThat(response.overviewMetrics()).isNotEmpty();
            assertThat(response.overviewMetrics().stream().anyMatch(m -> "ANNUALIZED_VOLATILITY".equals(m.metricCode()))).isTrue();

            // Section C: Exposure Context
            assertThat(response.exposureContext()).isNotNull();
            assertThat(response.exposureContext().exposureDisclaimers()).isNotEmpty();

            // Section D: Interpretation Panel
            assertThat(response.interpretation()).isNotNull();
            assertThat(response.interpretation().primaryRiskDrivers()).isNotEmpty();
            assertThat(response.interpretation().investigationPrompts()).isNotEmpty();
        } else {
            assertThat(response.evaluationStatus()).isIn("INSUFFICIENT_RETURN_OBSERVATIONS", "PORTFOLIO_EMPTY");
            assertThat(response.dataQualityWarnings()).isNotEmpty();
        }
    }
}
