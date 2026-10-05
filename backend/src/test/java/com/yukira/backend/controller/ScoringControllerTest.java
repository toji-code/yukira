package com.yukira.backend.controller;

import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class ScoringControllerTest {

    @Mock
    private AnalyticalScoringService scoringService;

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    private ScoreMethodologyConfig methodologyConfig;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        methodologyConfig = new ScoreMethodologyConfig();
        ScoringController controller = new ScoringController(scoringService, schemeOptionRepository, methodologyConfig);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private AnalyticalScoreResponse createSampleResponse() {
        AnalyticalScoreResponse.MetricContributionDto mc1 = new AnalyticalScoreResponse.MetricContributionDto(
            101L, "RET-03", "CAGR (Annualized Return)", new BigDecimal("0.2725"), "0.2725",
            new BigDecimal("87.19"), "HIGHER_IS_BETTER", new BigDecimal("0.6000"),
            new BigDecimal("0.6000"), new BigDecimal("52.3115"), 739,
            "ELIGIBLE", null, "PERCENTAGE", 501L
        );
        AnalyticalScoreResponse.MetricContributionDto mc2 = new AnalyticalScoreResponse.MetricContributionDto(
            102L, "RET-07", "3-Year Annualized Active Return", new BigDecimal("0.0742"), "0.0742",
            new BigDecimal("4.03"), "HIGHER_IS_BETTER", new BigDecimal("0.4000"),
            new BigDecimal("0.4000"), new BigDecimal("1.6110"), 738,
            "ELIGIBLE", null, "PERCENTAGE", 502L
        );

        AnalyticalScoreResponse.DimensionScoreDto dim = new AnalyticalScoreResponse.DimensionScoreDto(
            201L, "RETURN_QUALITY", "Return Quality", new BigDecimal("53.92"),
            new BigDecimal("0.3000"), new BigDecimal("0.3000"), new BigDecimal("16.1760"),
            "AVAILABLE", new BigDecimal("95.00"), 2, 2, List.of(mc1, mc2)
        );

        AnalyticalScoreResponse.EvidenceConfidenceDto ev = new AnalyticalScoreResponse.EvidenceConfidenceDto(
            739, 739, 0, 0, 739, 738, true, true, true,
            new BigDecimal("95.00"), "HIGH_CONFIDENCE",
            "739 verified trading days (NAV level observations); 738 synchronous daily return intervals against benchmark."
        );

        return new AnalyticalScoreResponse(
            1L, 1L, "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            new BigDecimal("61.45"), new BigDecimal("95.00"), "AVAILABLE",
            "YUKIRA_SCORE_V1", "CANDIDATE",
            LocalDate.of(2024, 1, 15),
            OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30)),
            7767L, "INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1",
            "Test summary", "NOT investment advice",
            List.of(dim), ev
        );
    }

    @Test
    @DisplayName("GET /api/v1/scores/{id}/latest: Full API contract verification for investor UI readiness")
    void testGetLatestScoreFullContract() throws Exception {
        when(schemeOptionRepository.existsById(1L)).thenReturn(true);
        when(scoringService.getLatestScore(1L)).thenReturn(Optional.of(createSampleResponse()));

        mockMvc.perform(get("/api/v1/scores/1/latest")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            // Top level score metadata
            .andExpect(jsonPath("$.scoreId", is(1)))
            .andExpect(jsonPath("$.schemeOptionId", is(1)))
            .andExpect(jsonPath("$.schemeName", is("HDFC Flexi Cap Fund")))
            .andExpect(jsonPath("$.amfiCode", is("118955")))
            .andExpect(jsonPath("$.isin", is("INF179K01UT0")))
            .andExpect(jsonPath("$.score", is(61.45)))
            .andExpect(jsonPath("$.confidence", is(95.00)))
            .andExpect(jsonPath("$.status", is("AVAILABLE")))
            .andExpect(jsonPath("$.scoreVersion", is("YUKIRA_SCORE_V1")))
            .andExpect(jsonPath("$.methodologyStatus", is("CANDIDATE")))
            .andExpect(jsonPath("$.asOfDate", is("2024-01-15")))
            .andExpect(jsonPath("$.calculationRunId", is(7767)))
            .andExpect(jsonPath("$.referencePopulation", is("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1")))
            .andExpect(jsonPath("$.disclaimer", containsString("NOT investment advice")))

            // Dimensions contract
            .andExpect(jsonPath("$.dimensions", hasSize(1)))
            .andExpect(jsonPath("$.dimensions[0].dimension", is("RETURN_QUALITY")))
            .andExpect(jsonPath("$.dimensions[0].dimensionName", is("Return Quality")))
            .andExpect(jsonPath("$.dimensions[0].score", is(53.92)))
            .andExpect(jsonPath("$.dimensions[0].weight", is(0.3000)))
            .andExpect(jsonPath("$.dimensions[0].effectiveWeight", is(0.3000)))
            .andExpect(jsonPath("$.dimensions[0].contribution", is(16.1760)))
            .andExpect(jsonPath("$.dimensions[0].status", is("AVAILABLE")))

            // Metric Contributions contract (zero frontend calculation required)
            .andExpect(jsonPath("$.dimensions[0].metricContributions", hasSize(2)))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].metricCode", is("RET-03")))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].rawValue", is(0.2725)))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].formattedRawValue", is("0.2725")))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].normalizedValue", is(87.19)))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].direction", is("HIGHER_IS_BETTER")))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].weight", is(0.6000)))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].effectiveWeight", is(0.6000)))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].contribution", is(52.3115)))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].observationCount", is(739)))
            .andExpect(jsonPath("$.dimensions[0].metricContributions[0].eligibility", is("ELIGIBLE")))

            // Evidence and confidence contract
            .andExpect(jsonPath("$.evidenceConfidence.totalObservations", is(739)))
            .andExpect(jsonPath("$.evidenceConfidence.validObservations", is(739)))
            .andExpect(jsonPath("$.evidenceConfidence.pairedReturnPeriods", is(738)))
            .andExpect(jsonPath("$.evidenceConfidence.confidenceScore", is(95.00)))
            .andExpect(jsonPath("$.evidenceConfidence.assessment", is("HIGH_CONFIDENCE")))
            .andExpect(jsonPath("$.evidenceConfidence.observationNotes", containsString("739 verified trading days")));
    }

    @Test
    @DisplayName("GET /api/v1/scores/methodology: Declares score vs confidence separation and candidate disclaimer")
    void testGetMethodologyConfig() throws Exception {
        mockMvc.perform(get("/api/v1/scores/methodology")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scoreVersion", is("YUKIRA_SCORE_V1")))
            .andExpect(jsonPath("$.methodologyStatus", is("CANDIDATE")))
            .andExpect(jsonPath("$.referencePopulation", is("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1")))
            .andExpect(jsonPath("$.scoredDimensionCodes", contains("RETURN_QUALITY", "RISK_QUALITY", "BENCHMARK_RELATIVE_QUALITY", "CONSISTENCY_DOWNSIDE_QUALITY")))
            .andExpect(jsonPath("$.authorizedScoreInputMetrics", contains("RET-03", "RET-07", "RSK-01", "RSK-02", "RSK-03", "REL-02", "RAT-04", "MKT-01", "MKT-05", "MKT-02")))
            .andExpect(jsonPath("$.analyticalNonScoreMetrics", hasItems("REL-01", "REL-03", "RAT-01", "RAT-02", "RAT-03", "RET-02", "MKT-03", "MKT-04", "RSK-04", "RSK-05", "RSK-06", "RSK-07")))
            .andExpect(jsonPath("$.evidenceConfidenceWeight", is(0)))
            .andExpect(jsonPath("$.evidenceConfidenceRole", containsString("strictly zero weight in numerical analytical score")))
            .andExpect(jsonPath("$.mkt05CalibrationStatus", containsString("UNCALIBRATED")))
            .andExpect(jsonPath("$.unauthorizedMetrics", hasItems("MKT-06", "REL-04", "REL-05", "REL-06", "RAT-05")))
            .andExpect(jsonPath("$.disclaimer", containsString("IMPLEMENTED != VALIDATED != APPROVED")));
    }

    @Test
    @DisplayName("POST /api/v1/scores/calculate: Validates missing parameter and returns calculated score")
    void testCalculateScore() throws Exception {
        // Missing parameter
        mockMvc.perform(post("/api/v1/scores/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("MISSING_PARAMETER")));

        // Valid execution
        when(schemeOptionRepository.existsById(1L)).thenReturn(true);
        when(scoringService.calculateScore(any(ScoreCalculationRequest.class))).thenReturn(createSampleResponse());

        String validBody = """
            {
                "schemeOptionId": 1,
                "asOfDate": "2024-01-15",
                "knowledgeCutoffTime": "2024-01-31T23:59:59+05:30"
            }
            """;

        mockMvc.perform(post("/api/v1/scores/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score", is(61.45)))
            .andExpect(jsonPath("$.status", is("AVAILABLE")));
    }
}
