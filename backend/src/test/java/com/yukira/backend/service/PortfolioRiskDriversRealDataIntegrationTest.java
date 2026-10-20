package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.drivers.ConcentrationSummaryDto;
import com.yukira.backend.dto.portfolio.drivers.PortfolioRiskDriversResponseDto;
import com.yukira.backend.dto.portfolio.drivers.RiskDriverFindingDto;
import com.yukira.backend.dto.portfolio.lookthrough.MultiFundLookThroughRequestDto;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.PortfolioRiskDriversService;
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
public class PortfolioRiskDriversRealDataIntegrationTest {

    @Autowired
    private PortfolioRiskDriversService riskDriversService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private Long hdfcFlexiOptId;
    private Long ppfasElssOptId;
    private OffsetDateTime cutoff;

    @BeforeEach
    void setUp() {
        SchemeOption hdfc = schemeOptionRepository.findAll().stream()
            .filter(o -> "118955".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("HDFC Flexi Cap #118955 not found in pilot database"));
        hdfcFlexiOptId = hdfc.getId();

        SchemeOption ppfasElss = schemeOptionRepository.findAll().stream()
            .filter(o -> "147481".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("PPFAS ELSS #147481 not found in pilot database"));
        ppfasElssOptId = ppfasElss.getId();

        cutoff = OffsetDateTime.of(2026, 10, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
    }

    @Test
    @DisplayName("Real Data: Evaluates Risk Drivers & Concentration on Canonical HDFC (60%) + PPFAS (40%) Portfolio")
    void shouldEvaluateRiskDriversOnRealPortfolio() {
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);

        MultiFundLookThroughRequestDto request = new MultiFundLookThroughRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(hdfcFlexiOptId, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(ppfasElssOptId, new BigDecimal("40.00"))
            ),
            asOfDate,
            cutoff,
            "RAW"
        );

        PortfolioRiskDriversResponseDto response = riskDriversService.calculateRiskDrivers(request);

        assertThat(response).isNotNull();
        assertThat(response.evaluationStatus()).isIn("COMPUTED_VALIDATED_INPUTS", "PARTIAL_HOLDINGS_COVERAGE");

        // 1. Concentration Summary
        ConcentrationSummaryDto summary = response.concentrationSummary();
        assertThat(summary).isNotNull();
        assertThat(summary.largestSecurityWeightPct()).isGreaterThan(BigDecimal.ZERO);
        assertThat(summary.top5WeightPct()).isGreaterThan(BigDecimal.ZERO);
        assertThat(summary.top10WeightPct()).isGreaterThan(summary.top5WeightPct());
        assertThat(summary.distinctSecuritiesCount()).isGreaterThanOrEqualTo(10);
        assertThat(summary.disclosedEquityCoveragePct()).isGreaterThan(BigDecimal.ZERO);

        // 2. Underlying Securities
        assertThat(response.underlyingSecurities()).isNotEmpty();
        assertThat(response.underlyingSecurities().get(0).consolidatedWeightPct()).isGreaterThan(BigDecimal.ZERO);

        // 3. Pairwise Overlap
        assertThat(response.pairwiseOverlap()).hasSize(1);
        assertThat(response.pairwiseOverlap().get(0).overlapWeightPct()).isGreaterThan(BigDecimal.ZERO);

        // 4. Sector & Market-Cap
        assertThat(response.sectors()).isNotEmpty();
        assertThat(response.marketCaps()).isNotEmpty();

        // 5. Deterministic Findings
        List<RiskDriverFindingDto> findings = response.findings();
        assertThat(findings).isNotEmpty();
        assertThat(findings).anyMatch(f -> "TEMPORAL_ALIGNMENT".equals(f.category()));
    }
}
