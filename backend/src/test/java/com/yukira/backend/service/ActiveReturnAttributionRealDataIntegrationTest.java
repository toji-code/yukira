package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.ActiveReturnAttributionResponseDto;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.ActiveReturnAttributionService;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ActiveReturnAttributionRealDataIntegrationTest {

    @Autowired
    private ActiveReturnAttributionService attributionService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private Long canonicalOptionId;

    @BeforeEach
    void setUp() {
        SchemeOption directGrowth = schemeOptionRepository.findAll().stream()
            .filter(o -> "118955".equals(o.getAmfiCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Canonical pilot #118955 not found"));

        canonicalOptionId = directGrowth.getId();
    }

    @Test
    @DisplayName("Real Data: Computes active return and macro decomposition with verified algebraic reconciliation on HDFC Flexi Cap")
    void shouldComputeRealActiveReturnAndMacroAttributionForPilot() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 10);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        ActiveReturnAttributionResponseDto response = attributionService.calculateAttribution(
            canonicalOptionId, asOfDate, cutoff, "1Y"
        );

        assertThat(response).isNotNull();
        assertThat(response.schemeOptionId()).isEqualTo(canonicalOptionId);
        assertThat(response.amfiCode()).isEqualTo("118955");
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.modelStatus()).isEqualTo("PARTIAL_EVIDENCE_SUPPORTED");

        // Returns
        assertThat(response.fundReturnPct()).isNotNull();
        assertThat(response.benchmarkReturnPct()).isNotNull();
        assertThat(response.activeReturnPct()).isNotNull();

        // Exact Macro Reconciliation: MarketTilt + CashDrag + Residual = ActiveReturn
        assertThat(response.macroAttribution()).isNotNull();
        assertThat(response.macroAttribution().isReconciled()).isTrue();
        assertThat(response.macroAttribution().reconciliationToleranceDeltaPct().abs())
            .isLessThan(new BigDecimal("0.000001"));

        // Historical Exposure Timeline
        assertThat(response.exposureTimeline()).isNotEmpty();
        assertThat(response.exposureTimeline().size()).isGreaterThanOrEqualTo(4);

        // Brinson Gate: Strictly BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE on real data
        assertThat(response.brinsonSegmentation()).isNotNull();
        assertThat(response.brinsonSegmentation().executionStatus()).isEqualTo("BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE");
        assertThat(response.brinsonSegmentation().benchmarkDisclosedWeightPct()).isLessThan(new BigDecimal("99.50"));
        assertThat(response.brinsonSegmentation().reasonIfNotCalculated()).contains("threshold");

        // Data-readiness gate
        assertThat(response.dataReadinessGate()).isNotNull();
        assertThat(response.dataReadinessGate().totalDependencies()).isEqualTo(8);
        assertThat(response.dataReadinessGate().missingDependencies()).isEqualTo(3);
    }

    @Test
    @DisplayName("Real Data: Strict Point-in-Time cutoff isolates historical knowledge state")
    void shouldRespectPointInTimeCutoffForPilot() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime earlyCutoff = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        ActiveReturnAttributionResponseDto response = attributionService.calculateAttribution(
            canonicalOptionId, asOfDate, earlyCutoff, "ALL"
        );

        assertThat(response).isNotNull();
        assertThat(response.knowledgeCutoff()).isEqualTo(earlyCutoff.toString());
        // All observations in exposure timeline must have availabilityTime <= earlyCutoff
        for (var obs : response.exposureTimeline()) {
            assertThat(obs.availabilityTime()).isBeforeOrEqualTo(earlyCutoff);
            assertThat(obs.portfolioDate()).isBeforeOrEqualTo(asOfDate);
        }
    }
}
