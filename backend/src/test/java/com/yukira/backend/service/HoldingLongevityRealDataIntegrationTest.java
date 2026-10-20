package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.longevity.*;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.HoldingLongevityService;
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
public class HoldingLongevityRealDataIntegrationTest {

    @Autowired
    private HoldingLongevityService holdingLongevityService;

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
    @DisplayName("Real Data: Evaluates historical holding longevity and position persistence for HDFC Flexi Cap Direct Growth")
    void shouldEvaluateRealDataHoldingLongevityForHdfcFlexiCap() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 10);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        HoldingLongevityResponseDto response = holdingLongevityService.getHoldingLongevityTrajectory(
            canonicalOptionId, asOfDate, null, null, cutoff
        );

        assertThat(response).isNotNull();
        assertThat(response.schemeOptionId()).isEqualTo(canonicalOptionId);
        assertThat(response.analysisStatus()).isEqualTo("SUCCESS");
        assertThat(response.eligibleSnapshotsCount()).isGreaterThanOrEqualTo(4);

        // Verify tenure buckets are populated
        assertThat(response.tenureBuckets()).isNotEmpty();
        boolean hasOver3Years = response.tenureBuckets().stream().anyMatch(b -> "OVER_3_YEARS".equals(b.bucketKey()));
        assertThat(hasOver3Years).isTrue();

        // Check canonical heavyweight positions
        assertThat(response.allHoldings()).isNotEmpty();
        PositionLongevityItemDto icici = response.allHoldings().stream()
            .filter(h -> h.canonicalName().contains("ICICI Bank"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ICICI Bank holding expected in pilot portfolio"));

        assertThat(icici.appearanceCount()).isGreaterThanOrEqualTo(4);
        assertThat(icici.persistenceCategory()).isEqualTo("PERSISTENT");
        assertThat(icici.latestReportedWeightPct()).isGreaterThan(new BigDecimal("5.00"));

        // Check transitions and comparability gating
        assertThat(response.transitions()).isNotEmpty();
        for (CompositionChangeTransitionDto t : response.transitions()) {
            if ("NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT".equals(t.comparabilityStatus()) ||
                "NON_COMPARABLE_TEMPORAL_GAP".equals(t.comparabilityStatus())) {
                assertThat(t.isComparable()).isFalse();
            } else if ("COMPARABLE_FULL_DISCLOSURE".equals(t.comparabilityStatus())) {
                assertThat(t.isComparable()).isTrue();
                assertThat(t.halfSumCompositionChangePct()).isGreaterThan(BigDecimal.ZERO);
                assertThat(t.unresolvedDiscrepancyPct()).isLessThanOrEqualTo(new BigDecimal("0.05"));
            }
        }

        // Strict turnover gating check
        TurnoverEvaluationDto turnover = response.turnoverEvaluation();
        assertThat(turnover).isNotNull();
        assertThat(turnover.turnoverStatus()).isEqualTo("TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING");
        assertThat(turnover.isTurnoverCalculated()).isFalse();
        assertThat(turnover.reportedRegulatoryTurnoverRatioPct()).isNull();
        assertThat(turnover.limitationsAndDisclaimers()).isNotEmpty();
    }

    @Test
    @DisplayName("Real Data: Dual PIT cutoff excludes snapshots published after cutoff time")
    void shouldStrictlyEnforceDualPitCutoffOnRealData() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 10);
        // Cutoff before 2026-09-30 portfolio availability (e.g. 2026-09-01)
        OffsetDateTime pastCutoff = OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        HoldingLongevityResponseDto response = holdingLongevityService.getHoldingLongevityTrajectory(
            canonicalOptionId, asOfDate, null, null, pastCutoff
        );

        assertThat(response).isNotNull();
        // The 2026-09-30 snapshot must NOT be present
        boolean containsSeptember2026 = response.snapshots().stream()
            .anyMatch(s -> LocalDate.of(2026, 9, 30).equals(s.portfolioDate()));
        assertThat(containsSeptember2026).isFalse();
    }
}
