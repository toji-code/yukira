package com.yukira.backend.scoring;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@SuppressWarnings("null")
public class AnalyticalQualityScoreIntegrationTest {

    @Autowired
    private AnalyticalScoringService scoringService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Test
    @DisplayName("HDFC Pilot Scoring: Calculate YUKIRA_SCORE_V1 deterministically against canonical pilot")
    void testCanonicalPilotScoringEndToEnd() {
        // 1. Identify canonical pilot: HDFC Flexi Cap Fund Direct Growth (AMFI 118955, ISIN INF179K01UT0)
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        assertEquals("INF179K01UT0", pilot.getIsin());
        assertEquals("GROWTH", pilot.getOptionType());

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseGet(() -> benchmarkRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Benchmark not found")));

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        ScoreCalculationRequest request = new ScoreCalculationRequest(
            pilot.getId(),
            null, // Auto-resolve / execute run
            asOfDate,
            knowledgeCutoff,
            benchmark.getId()
        );

        long scoresBefore = analyticalScoreRepository.count();

        // 2. Execute Scoring Pipeline
        AnalyticalScoreResponse response = scoringService.calculateScore(request);

        assertNotNull(response);
        assertEquals(scoresBefore + 1, analyticalScoreRepository.count());

        // 3. Verify Score and Confidence separation
        assertNotNull(response.score());
        assertTrue(response.score().compareTo(BigDecimal.ZERO) >= 0 && response.score().compareTo(new BigDecimal("100.00")) <= 0,
            "Analytical Score must be strictly bounded in [0, 100]");

        assertNotNull(response.confidence());
        assertTrue(response.confidence().compareTo(BigDecimal.ZERO) >= 0 && response.confidence().compareTo(new BigDecimal("100.00")) <= 0,
            "Confidence must be strictly bounded in [0, 100]");

        System.out.println("=== HDFC PILOT SCORE RESULT ===");
        System.out.println("Fund:              " + response.schemeName() + " (" + response.amfiCode() + " / " + response.isin() + ")");
        System.out.println("Analytical Score:  " + response.score() + " / 100.00");
        System.out.println("Confidence:        " + response.confidence() + " / 100.00 (" + response.evidenceConfidence().assessment() + ")");
        System.out.println("Score Status:      " + response.status());
        System.out.println("Methodology:       " + response.scoreVersion() + " [" + response.methodologyStatus() + "]");
        System.out.println("Ref Population:    " + response.referencePopulation());
        System.out.println("Calculation Run:   #" + response.calculationRunId());

        // 4. Verify Methodology and Status (must remain strictly CANDIDATE)
        assertEquals("YUKIRA_SCORE_V1", response.scoreVersion());
        assertEquals("CANDIDATE", response.methodologyStatus());
        assertEquals("AVAILABLE", response.status());
        assertNotNull(response.disclaimer());
        assertTrue(response.disclaimer().contains("NOT investment advice"));

        // 5. Verify Dimensions and Full Mathematical Decomposition (PART A Item 7)
        assertEquals(4, response.dimensions().size(), "Must evaluate exactly 4 performance dimensions");

        BigDecimal sumDimensionContributions = BigDecimal.ZERO;

        for (AnalyticalScoreResponse.DimensionScoreDto dim : response.dimensions()) {
            assertNotNull(dim.score(), "Dimension " + dim.dimension() + " score must be non-null");
            assertTrue(dim.score().compareTo(BigDecimal.ZERO) >= 0 && dim.score().compareTo(new BigDecimal("100.00")) <= 0);
            assertEquals("AVAILABLE", dim.status(), "Dimension " + dim.dimension() + " status must be AVAILABLE for complete pilot");
            assertTrue(dim.eligibleMetricCount() > 0);
            assertFalse(dim.metricContributions().isEmpty());

            assertNotNull(dim.effectiveWeight(), "Effective dimension weight must be non-null");
            assertNotNull(dim.contribution(), "Dimension contribution must be non-null");
            sumDimensionContributions = sumDimensionContributions.add(dim.contribution());

            System.out.println("\n  Dimension: " + dim.dimensionName() + " (" + dim.dimension() + ")");
            System.out.println("  Score: " + dim.score() + " / 100.00 | Config Weight: " + dim.weight()
                + " | Eff Weight: " + dim.effectiveWeight() + " | Contrib: " + dim.contribution() + " | Status: " + dim.status());

            BigDecimal sumMetricContributions = BigDecimal.ZERO;

            for (AnalyticalScoreResponse.MetricContributionDto mc : dim.metricContributions()) {
                assertEquals("ELIGIBLE", mc.eligibility(), "Metric " + mc.metricCode() + " must be ELIGIBLE");
                assertNotNull(mc.rawValue(), "Metric " + mc.metricCode() + " raw value must be non-null");
                assertNotNull(mc.normalizedValue(), "Metric " + mc.metricCode() + " normalized value must be non-null");
                assertNotNull(mc.contribution(), "Metric " + mc.metricCode() + " contribution must be non-null");
                assertNotNull(mc.effectiveWeight(), "Metric " + mc.metricCode() + " effective weight must be non-null");
                assertNotNull(mc.observationCount(), "Metric " + mc.metricCode() + " observation count must be non-null");

                // Audit: normalized_value * effective_metric_weight = contribution
                BigDecimal expectedContrib = mc.normalizedValue().multiply(mc.effectiveWeight()).setScale(4, RoundingMode.HALF_UP);
                assertEquals(expectedContrib, mc.contribution(),
                    "Metric " + mc.metricCode() + ": normalized_value * effective_weight must equal contribution");

                sumMetricContributions = sumMetricContributions.add(mc.contribution());

                System.out.printf(java.util.Locale.ENGLISH,
                    "    - %s (N=%d): raw=%s (%s), normalized=%.2f, dir=%s, effWeight=%.4f, contrib=%.4f%n",
                    mc.metricCode(), mc.observationCount(), mc.formattedRawValue(), mc.unit(),
                    mc.normalizedValue().doubleValue(), mc.direction(),
                    mc.effectiveWeight().doubleValue(), mc.contribution().doubleValue()
                );
            }

            // Audit: sum(metric contributions) = dimension score (within 0.01 tolerance)
            BigDecimal roundedMetricSum = sumMetricContributions.setScale(2, RoundingMode.HALF_UP);
            assertEquals(dim.score().doubleValue(), roundedMetricSum.doubleValue(), 0.01,
                "Sum of metric contributions must equal dimension score for " + dim.dimension());
        }

        // Audit: sum(weighted dimension contributions) = final score (within 0.01 tolerance)
        BigDecimal roundedDimensionSum = sumDimensionContributions.setScale(2, RoundingMode.HALF_UP);
        assertEquals(response.score().doubleValue(), roundedDimensionSum.doubleValue(), 0.01,
            "Sum of weighted dimension contributions must equal final analytical score");

        // 6. Verify Evidence & Data Confidence (PART A Item 1: N=739 trading days, N=738 paired returns)
        AnalyticalScoreResponse.EvidenceConfidenceDto ev = response.evidenceConfidence();
        assertNotNull(ev);
        assertEquals(739, ev.totalObservations(), "Total trading days (NAV level observations) must be 739");
        assertEquals(739, ev.validObservations(), "Valid trading days must be 739");
        assertEquals(0, ev.suspiciousObservations(), "Suspicious observations must be 0");
        assertEquals(0, ev.invalidObservations(), "Invalid observations must be 0");
        assertEquals(739, ev.pairedBenchmarkObservations(), "Paired benchmark trading days must be 739");
        assertEquals(738, ev.pairedReturnPeriods(), "Paired daily return intervals (N-1) must be exactly 738");
        assertTrue(ev.meetsObservationThreshold());
        assertTrue(ev.sourceArtifactVerified());
        assertTrue(ev.confidenceScore().compareTo(new BigDecimal("80.00")) >= 0, "Pilot confidence must be high (>= 80)");
        assertNotNull(ev.observationNotes(), "Observation notes explaining 739 vs 738 must be present");
        assertTrue(ev.observationNotes().contains("739 verified trading days"));
        assertTrue(ev.observationNotes().contains("738 synchronous daily return intervals"));

        // 7. Verify Metric-Specific Observation Counts from MetricResult diagnostics
        for (AnalyticalScoreResponse.DimensionScoreDto dim : response.dimensions()) {
            for (AnalyticalScoreResponse.MetricContributionDto mc : dim.metricContributions()) {
                if ("RSK-01".equals(mc.metricCode()) || "RSK-02".equals(mc.metricCode()) || "RSK-03".equals(mc.metricCode()) || "RET-03".equals(mc.metricCode())) {
                    assertEquals(739, mc.observationCount(), "Standalone NAV metrics must have 739 observations");
                } else if ("REL-02".equals(mc.metricCode()) || "RAT-04".equals(mc.metricCode()) || "MKT-01".equals(mc.metricCode()) || "MKT-02".equals(mc.metricCode()) || "MKT-05".equals(mc.metricCode())) {
                    assertEquals(738, mc.observationCount(), "Benchmark-relative return metrics must have 738 paired returns");
                }
            }
        }

        // 8. Verify Database Persistence & Reconstructability entirely from persisted records
        AnalyticalScore persisted = analyticalScoreRepository.findById(response.scoreId()).orElseThrow();
        assertEquals(response.score(), persisted.getScore());
        assertEquals(response.confidence(), persisted.getConfidence());
        assertEquals(response.status(), persisted.getStatus());

        // 5 dimensions persisted (A, B, C, D + E Evidence)
        assertEquals(5, persisted.getDimensions().size());

        // Verify persisted Dimension E (Evidence) has STRICTLY ZERO weight
        ScoreDimension persistedEvidenceDim = persisted.getDimensions().stream()
            .filter(d -> "EVIDENCE_CONFIDENCE".equalsIgnoreCase(d.getDimension()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Evidence dimension must be persisted"));
        assertEquals(0, BigDecimal.ZERO.compareTo(persistedEvidenceDim.getWeight()),
            "Evidence & Data Confidence dimension must have STRICTLY ZERO weight in numerical score");

        // 9. Verify Retrieval via getLatestScore (Faithful reconstruction, zero hardcoded 745)
        Optional<AnalyticalScoreResponse> retrieved = scoringService.getLatestScore(pilot.getId());
        assertTrue(retrieved.isPresent());
        AnalyticalScoreResponse retrievedResp = retrieved.get();
        assertEquals(response.score(), retrievedResp.score());
        assertEquals(response.confidence(), retrievedResp.confidence());
        assertEquals(739, retrievedResp.evidenceConfidence().totalObservations(), "Retrieved evidence must report 739 total observations (not hardcoded 745!)");
        assertEquals(738, retrievedResp.evidenceConfidence().pairedReturnPeriods(), "Retrieved evidence must report 738 paired return periods");
        assertEquals(4, retrievedResp.dimensions().size());
    }
}
