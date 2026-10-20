package com.yukira.backend.service.portfolio;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.portfolio.FundConcentrationDto;
import com.yukira.backend.dto.portfolio.PortfolioOverlapResponseDto;
import com.yukira.backend.dto.portfolio.PortfolioSecurityAggregationDto;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.PilotHoldingsDataService;
import com.yukira.backend.service.PortfolioIntelligenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PortfolioIntelligenceTest {

    private static final Logger log = LoggerFactory.getLogger(PortfolioIntelligenceTest.class);

    @Autowired
    private PortfolioIntelligenceService intelligenceService;

    @Autowired
    private PilotBootstrapService bootstrapService;

    @Autowired
    private PilotHoldingsDataService holdingsDataService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Test
    @DisplayName("Single-Fund Concentration and Herfindahl-Hirschman Index (HHI) Verification")
    void testSingleFundConcentrationAndHhi() {
        bootstrapService.bootstrapPilot();
        holdingsDataService.seedControlledPilotHoldingsCohort();

        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        FundConcentrationDto concentration = intelligenceService.calculateFundConcentration(hdfcOpt.getId(), cutoff);

        assertNotNull(concentration);
        assertEquals("118955", hdfcOpt.getAmfiCode());
        assertEquals(10, concentration.positionCount());
        assertEquals(54.82, concentration.sumDisclosedWeightsPct().doubleValue(), 0.01);
        assertEquals(34.35, concentration.top5WeightPct().doubleValue(), 0.01);
        assertEquals(54.82, concentration.top10WeightPct().doubleValue(), 0.01);

        // Verify HHI calculation:
        // ICICI(9.5^2=90.25) + HDFC(9.27^2=85.9329) + Cipla(5.39^2=29.0521) + HAL(5.16^2=26.6256) + HCL(5.03^2=25.3009)
        // + SBI(4.88^2=23.8144) + Axis(4.52^2=20.4304) + L&T(4.1^2=16.81) + Infy(3.75^2=14.0625) + Bharti(3.22^2=10.3684) = 342.6472 -> ~342.65
        assertTrue(concentration.hhi().doubleValue() > 300.0 && concentration.hhi().doubleValue() < 400.0, "HHI must be ~342.65");
        assertEquals("DIVERSIFIED", concentration.hhiClassification());
        assertEquals("DATA_QUALITY_LIMITED", concentration.qualityState());
        assertFalse(concentration.limitations().isEmpty());

        log.info("Concentration test PASS for HDFC Flexi Cap: Top 5={}% , Top 10={}% , HHI={} ({})",
            concentration.top5WeightPct(), concentration.top10WeightPct(), concentration.hhi(), concentration.hhiClassification());
    }

    @Test
    @DisplayName("Pairwise Portfolio Overlap Deterministic Math Verification")
    void testPairwisePortfolioOverlap() {
        bootstrapService.bootstrapPilot();
        holdingsDataService.seedControlledPilotHoldingsCohort();

        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        SchemeOption ppfasOpt = schemeOptionRepository.findByAmfiCode("122639").orElseThrow();
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        PortfolioOverlapResponseDto overlap = intelligenceService.calculatePairwiseOverlap(hdfcOpt.getId(), ppfasOpt.getId(), cutoff);

        assertNotNull(overlap);
        assertEquals(hdfcOpt.getId(), overlap.schemeOptionIdA());
        assertEquals(ppfasOpt.getId(), overlap.schemeOptionIdB());

        // Common holdings: ICICI (6.80), HDFC (8.15), HCL (3.60), Axis (4.52), L&T (3.20)
        // Total Overlap = 6.80 + 8.15 + 3.60 + 4.52 + 3.20 = 26.27%
        assertEquals(5, overlap.commonSecurityCount(), "HDFC and PPFAS must share exactly 5 common holdings");
        assertEquals(26.27, overlap.overlapPercentage().doubleValue(), 0.05, "Overlap percentage must equal 26.27%");
        assertEquals("26.27%", overlap.formattedOverlapPercentage());

        assertFalse(overlap.topOverlappingSecurities().isEmpty());
        assertEquals("HDFC Bank Ltd.", overlap.topOverlappingSecurities().get(0).securityName());
        assertEquals(8.15, overlap.topOverlappingSecurities().get(0).overlapContribution().doubleValue(), 0.01);

        log.info("Pairwise Overlap test PASS: Overlap between HDFC and PPFAS = {} (Common Securities: {})",
            overlap.formattedOverlapPercentage(), overlap.commonSecurityCount());
    }

    @Test
    @DisplayName("Multi-Fund Look-Through Security Exposure Aggregation Verification")
    void testMultiFundLookThroughSecurityAggregation() {
        bootstrapService.bootstrapPilot();
        holdingsDataService.seedControlledPilotHoldingsCohort();

        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        SchemeOption ppfasOpt = schemeOptionRepository.findByAmfiCode("122639").orElseThrow();
        SchemeOption sbiOpt = schemeOptionRepository.findByAmfiCode("120503").orElseThrow();
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        // Investor portfolio: 50% HDFC, 30% PPFAS, 20% SBI
        Map<Long, BigDecimal> fundWeights = Map.of(
            hdfcOpt.getId(), new BigDecimal("50.0"),
            ppfasOpt.getId(), new BigDecimal("30.0"),
            sbiOpt.getId(), new BigDecimal("20.0")
        );

        PortfolioSecurityAggregationDto aggregation = intelligenceService.aggregateLookThroughExposures(fundWeights, cutoff);

        assertNotNull(aggregation);
        assertEquals(3, aggregation.totalConstituentFunds());
        assertEquals(3, aggregation.valuedConstituentFunds());
        assertTrue(aggregation.uniqueSecuritiesCount() > 10, "Unique look-through securities count should be > 10");

        // Top look-through holding should be ICICI Bank Ltd. or HDFC Bank Ltd.
        // HDFC Bank: 0.50 * 9.27 + 0.30 * 8.15 + 0.20 * 8.20 = 4.635 + 2.445 + 1.64 = 8.72%
        // ICICI Bank: 0.50 * 9.50 + 0.30 * 6.80 + 0.20 * 8.90 = 4.75 + 2.04 + 1.78 = 8.57%
        var topSec = aggregation.topSecurityExposures().get(0);
        assertNotNull(topSec.securityName());
        assertTrue(topSec.lookThroughWeightPct().doubleValue() > 8.0, "Top look-through security weight must be > 8.0%");
        assertEquals(3, topSec.holdingFundCount(), "Top security must be held across all 3 constituent funds");

        log.info("Look-Through Security Aggregation test PASS: Top Security='{}' (Look-Through Weight={}) across {} unique securities",
            topSec.securityName(), topSec.formattedLookThroughWeight(), aggregation.uniqueSecuritiesCount());
    }

    @Test
    @DisplayName("Deterministic Repeatability of Portfolio Intelligence Analytics")
    void testDeterministicRepeatability() {
        bootstrapService.bootstrapPilot();
        holdingsDataService.seedControlledPilotHoldingsCohort();

        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        SchemeOption ppfasOpt = schemeOptionRepository.findByAmfiCode("122639").orElseThrow();
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        var conc1 = intelligenceService.calculateFundConcentration(hdfcOpt.getId(), cutoff);
        var conc2 = intelligenceService.calculateFundConcentration(hdfcOpt.getId(), cutoff);
        assertEquals(conc1.hhi(), conc2.hhi(), "Repeated HHI calculations must be identical");

        var ov1 = intelligenceService.calculatePairwiseOverlap(hdfcOpt.getId(), ppfasOpt.getId(), cutoff);
        var ov2 = intelligenceService.calculatePairwiseOverlap(hdfcOpt.getId(), ppfasOpt.getId(), cutoff);
        assertEquals(ov1.overlapPercentage(), ov2.overlapPercentage(), "Repeated overlap calculations must be identical");
    }
}
