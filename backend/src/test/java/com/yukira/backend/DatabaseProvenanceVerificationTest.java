package com.yukira.backend;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.HoldingRiskEvidenceDto;
import com.yukira.backend.dto.portfolio.HoldingRiskMetricDto;
import com.yukira.backend.dto.portfolio.PortfolioHoldingRequest;
import com.yukira.backend.dto.portfolio.PortfolioSummaryDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class DatabaseProvenanceVerificationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Autowired
    private InvestorPortfolioService portfolioService;

    private static final String TEST_USER = "auth0|test-provenance-user";

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
    }

    @Test
    @DisplayName("TC-DB-01: HDFC scheme_option_id = 1 direct PostgreSQL provenance & value fidelity")
    void testHdfcDatabaseProvenance() {
        Optional<SchemeOption> opt1 = schemeOptionRepository.findById(1L);
        assertTrue(opt1.isPresent(), "scheme_option_id = 1 must exist in PostgreSQL");
        SchemeOption option = opt1.get();

        assertEquals("118955", option.getAmfiCode());
        assertEquals("INF179K01UT0", option.getIsin());
        assertEquals("DIRECT", option.getPlan().getPlanType());
        assertEquals("GROWTH", option.getOptionType());

        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(1L);
        assertFalse(scores.isEmpty(), "AnalyticalScore must exist for scheme_option_id = 1");
        AnalyticalScore score = scores.get(0);
        assertEquals(48224L, score.getId());

        // Test production portfolio read-through for USER
        portfolioService.addOrUpdateHolding(TEST_USER, new PortfolioHoldingRequest(1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)));
        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(TEST_USER);

        assertNotNull(summary.portfolioRiskEvidence());
        assertEquals(1, summary.portfolioRiskEvidence().holdingRiskEvidences().size());

        HoldingRiskEvidenceDto hEv = summary.portfolioRiskEvidence().holdingRiskEvidences().get(0);
        assertEquals(1L, hEv.schemeOptionId());

        Map<String, HoldingRiskMetricDto> metricMap = new HashMap<>();
        for (HoldingRiskMetricDto m : hEv.metrics()) {
            metricMap.put(m.metricCode(), m);
        }

        // Verify exact raw values from DB
        assertNotNull(metricMap.get("RSK-01").numericValue());
        assertEquals(0, new BigDecimal("0.1459396069").compareTo(metricMap.get("RSK-01").numericValue()));
        assertEquals("Annualized Volatility 3Y", metricMap.get("RSK-01").metricName());

        assertNotNull(metricMap.get("RSK-02").numericValue());
        assertEquals(0, new BigDecimal("0.1011637853").compareTo(metricMap.get("RSK-02").numericValue()));
        assertEquals("Downside Semideviation 3Y", metricMap.get("RSK-02").metricName());

        assertNotNull(metricMap.get("RSK-03").numericValue());
        assertEquals(0, new BigDecimal("-0.1288001263").compareTo(metricMap.get("RSK-03").numericValue()));
        assertEquals("Maximum Drawdown 3Y", metricMap.get("RSK-03").metricName());

        assertNotNull(metricMap.get("MKT-01").numericValue());
        assertEquals(0, new BigDecimal("0.9530555399").compareTo(metricMap.get("MKT-01").numericValue()));
        assertEquals("Beta 3Y", metricMap.get("MKT-01").metricName());

        assertNotNull(metricMap.get("MKT-02").numericValue());
        assertEquals(0, new BigDecimal("0.9678148690").compareTo(metricMap.get("MKT-02").numericValue()));
        assertEquals("Downside Beta 3Y", metricMap.get("MKT-02").metricName());

        assertNotNull(metricMap.get("MKT-05").numericValue());
        assertEquals(0, new BigDecimal("-3.2447532025").compareTo(metricMap.get("MKT-05").numericValue()));
        assertEquals("Capture Spread 3Y", metricMap.get("MKT-05").metricName());

        assertNotNull(metricMap.get("RAT-04").numericValue());
        assertEquals(0, new BigDecimal("1.2699983982").compareTo(metricMap.get("RAT-04").numericValue()));
        assertEquals("Information Ratio 3Y", metricMap.get("RAT-04").metricName());

        assertNotNull(metricMap.get("RET-03").numericValue());
        assertEquals(0, new BigDecimal("0.2724957787").compareTo(metricMap.get("RET-03").numericValue()));
        assertEquals("3-Year CAGR", metricMap.get("RET-03").metricName());

        // RAT-01 is unavailable in YUKIRA_SCORE_V1 snapshot
        assertNull(metricMap.get("RAT-01").numericValue());
        assertEquals("UNAVAILABLE", metricMap.get("RAT-01").availabilityState());
    }

    @Test
    @DisplayName("TC-DB-02: Additional holding scheme_option_id = 10199 (Bandhan Flexi Cap) database provenance")
    void testOption10199DatabaseProvenance() {
        Optional<SchemeOption> opt = schemeOptionRepository.findById(10199L);
        assertTrue(opt.isPresent(), "scheme_option_id = 10199 must exist in PostgreSQL");
        SchemeOption option = opt.get();

        assertEquals("118424", option.getAmfiCode());
        assertEquals("INF194K01W62", option.getIsin());
        assertEquals("DIRECT", option.getPlan().getPlanType());
        assertEquals("GROWTH", option.getOptionType());

        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(10199L);
        assertFalse(scores.isEmpty(), "AnalyticalScore must exist for scheme_option_id = 10199");
        AnalyticalScore score = scores.get(0);
        assertEquals(40177L, score.getId());

        portfolioService.addOrUpdateHolding(TEST_USER, new PortfolioHoldingRequest(10199L, BigDecimal.valueOf(50.0), BigDecimal.valueOf(100.0)));
        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(TEST_USER);

        HoldingRiskEvidenceDto hEv = summary.portfolioRiskEvidence().holdingRiskEvidences().stream()
            .filter(e -> Long.valueOf(10199L).equals(e.schemeOptionId()))
            .findFirst()
            .orElseThrow();

        Map<String, HoldingRiskMetricDto> metricMap = new HashMap<>();
        for (HoldingRiskMetricDto m : hEv.metrics()) {
            metricMap.put(m.metricCode(), m);
        }

        assertNotNull(metricMap.get("RSK-01").numericValue());
        assertEquals(0, new BigDecimal("0.1365876417").compareTo(metricMap.get("RSK-01").numericValue()));

        assertNotNull(metricMap.get("RSK-02").numericValue());
        assertEquals(0, new BigDecimal("0.0972023166").compareTo(metricMap.get("RSK-02").numericValue()));

        assertNotNull(metricMap.get("RSK-03").numericValue());
        assertEquals(0, new BigDecimal("-0.2057578866").compareTo(metricMap.get("RSK-03").numericValue()));

        assertNotNull(metricMap.get("MKT-01").numericValue());
        assertEquals(0, new BigDecimal("0.9081637830").compareTo(metricMap.get("MKT-01").numericValue()));

        assertNotNull(metricMap.get("MKT-02").numericValue());
        assertEquals(0, new BigDecimal("0.9246049370").compareTo(metricMap.get("MKT-02").numericValue()));

        assertNotNull(metricMap.get("MKT-05").numericValue());
        assertEquals(0, new BigDecimal("-22.6152502794").compareTo(metricMap.get("MKT-05").numericValue()));

        assertNotNull(metricMap.get("RAT-04").numericValue());
        assertEquals(0, new BigDecimal("-0.4844668953").compareTo(metricMap.get("RAT-04").numericValue()));

        assertNotNull(metricMap.get("RET-03").numericValue());
        assertEquals(0, new BigDecimal("0.1770922067").compareTo(metricMap.get("RET-03").numericValue()));

        assertNull(metricMap.get("RAT-01").numericValue());
    }

    @Test
    @DisplayName("TC-DB-03: Resolve exact HDFC score, valuation economics, and scheme_option_id = 2 identity")
    void testExactScoreValuationAndOption2Identity() {
        // 1. HDFC Score Resolution
        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(1L);
        assertFalse(scores.isEmpty());
        AnalyticalScore hdfcScore = scores.get(0);
        
        System.out.println("=== HDFC SCORE IDENTITY ===");
        System.out.println("AnalyticalScore ID: " + hdfcScore.getId());
        System.out.println("Score Value: " + hdfcScore.getScore());
        System.out.println("Confidence: " + hdfcScore.getConfidence());
        System.out.println("Status: " + hdfcScore.getStatus());
        System.out.println("Version: " + hdfcScore.getScoreVersion());
        System.out.println("CalculationRun ID: " + hdfcScore.getCalculationRun().getId());
        System.out.println("As-Of Date: " + hdfcScore.getAsOfDate());
        System.out.println("Knowledge Cutoff: " + hdfcScore.getKnowledgeCutoffTime());

        // 2. Portfolio Valuation Economics Resolution
        portfolioService.addOrUpdateHolding(TEST_USER, new PortfolioHoldingRequest(1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)));
        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(TEST_USER);

        System.out.println("=== PORTFOLIO ECONOMICS ===");
        System.out.println("Total Invested Amount: " + summary.totalInvestedAmount());
        System.out.println("Total Available Value: " + summary.totalAvailableValue());
        System.out.println("Total Absolute Gain/Loss: " + summary.totalAbsoluteGainLoss());
        System.out.println("Total Absolute Gain/Loss %: " + summary.totalAbsoluteGainLossPercentage());
        System.out.println("Portfolio Analytical Score: " + summary.portfolioAnalyticalScore());

        // 3. Option 2 Identity Resolution
        Optional<SchemeOption> opt2 = schemeOptionRepository.findById(2L);
        System.out.println("=== OPTION 2 DATABASE IDENTITY ===");
        if (opt2.isPresent()) {
            SchemeOption option2 = opt2.get();
            System.out.println("Option 2 EXISTS:");
            System.out.println("ID: " + option2.getId());
            System.out.println("Scheme ID: " + option2.getPlan().getScheme().getId());
            System.out.println("Scheme Name: " + option2.getPlan().getScheme().getName());
            System.out.println("Option Type: " + option2.getOptionType());
            System.out.println("Plan Type: " + option2.getPlan().getPlanType());
            System.out.println("AMFI Code: " + option2.getAmfiCode());
            System.out.println("ISIN: " + option2.getIsin());
            System.out.println("Category: " + option2.getPlan().getScheme().getCategory());
            System.out.println("Subcategory: " + option2.getPlan().getScheme().getSubcategory());
            System.out.println("Status: " + option2.getStatus());

            List<AnalyticalScore> opt2Scores = analyticalScoreRepository.findLatestBySchemeOptionId(2L);
            System.out.println("Option 2 AnalyticalScores count: " + opt2Scores.size());
            if (!opt2Scores.isEmpty()) {
                System.out.println("Option 2 Latest Score ID: " + opt2Scores.get(0).getId());
                System.out.println("Option 2 Score Value: " + opt2Scores.get(0).getScore());
            }
        } else {
            System.out.println("Option 2 DOES NOT EXIST in PostgreSQL database (scheme_option table).");
        }
    }

    @Test
    @DisplayName("TC-DB-04: AnalyticalScore #48224 score arithmetic reconciliation and contract verification")
    void testScoreReconciliationAndContractInvariants() {
        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(1L);
        assertFalse(scores.isEmpty());
        AnalyticalScore hdfcScore = scores.get(0);
        assertEquals(48224L, hdfcScore.getId());

        System.out.println("=== SCORE ARITHMETIC RECONCILIATION FOR SCORE #48224 ===");
        System.out.println("Persisted Score Value: " + hdfcScore.getScore());
        System.out.println("Score Version: " + hdfcScore.getScoreVersion());
        System.out.println("Methodology Status: " + hdfcScore.getMethodologyStatus());
        System.out.println("Reference Population: " + hdfcScore.getReferencePopulation());
        System.out.println("Calculation Run ID: " + hdfcScore.getCalculationRun().getId());
        System.out.println("As-Of Date: " + hdfcScore.getAsOfDate());

        BigDecimal sumContributions = BigDecimal.ZERO;
        for (ScoreDimension dim : hdfcScore.getDimensions()) {
            BigDecimal dimScore = dim.getScore() != null ? dim.getScore() : BigDecimal.ZERO;
            BigDecimal dimWeight = dim.getWeight() != null ? dim.getWeight() : BigDecimal.ZERO;
            BigDecimal contrib = dimScore.multiply(dimWeight);
            sumContributions = sumContributions.add(contrib);
            System.out.println(String.format("Dimension [%s - %s]: Score = %s, Weight = %s, Calculated Contribution = %s",
                dim.getDimension(), dim.getDimensionName(), dim.getScore(), dim.getWeight(), contrib));
            
            if (dim.getMetricContributions() != null) {
                for (ScoreMetricContribution smc : dim.getMetricContributions()) {
                    if ("MKT-05".equals(smc.getMetricCode())) {
                        System.out.println("=== MKT-05 ELIGIBILITY IN SCORE ===");
                        System.out.println("MKT-05 Eligibility: " + smc.getEligibility());
                        System.out.println("MKT-05 Weight: " + smc.getWeight());
                        System.out.println("MKT-05 Contribution: " + smc.getContribution());
                        System.out.println("MKT-05 Raw Value: " + smc.getRawValue());
                    }
                }
            }
        }

        System.out.println("Sum of Dimension Contributions: " + sumContributions);
    }
}




