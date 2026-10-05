package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.ScoreDimension;
import com.yukira.backend.domain.entity.ScoreMetricContribution;
import com.yukira.backend.dto.portfolio.PortfolioHoldingRequest;
import com.yukira.backend.dto.portfolio.PortfolioScoreHistoryDto;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import com.yukira.backend.service.portfolio.PortfolioScoreHistoryService;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortfolioScoreHistoryIntegrationTest {

    private static final String TEST_USER = "auth0|score-history-user";
    private static final String OTHER_USER = "auth0|score-history-other";

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private InvestorPortfolioService investorPortfolioService;

    @Autowired
    private PortfolioScoreHistoryService portfolioScoreHistoryService;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
        investorPortfolioService.removeHolding(TEST_USER, 1L);
        investorPortfolioService.removeHolding(OTHER_USER, 1L);
        investorPortfolioService.addOrUpdateHolding(TEST_USER, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100), BigDecimal.valueOf(1500)
        ));
        persistHistoricalScores();
    }

    @Test
    @DisplayName("retrieves current score, historical fund score evidence, and exact scheme_option_id")
    void retrievesCurrentAndHistoricalFundEvidence() {
        PortfolioScoreHistoryDto response = portfolioScoreHistoryService.getPortfolioScoreHistory(TEST_USER, LocalDate.of(2024, 1, 15));

        assertNotNull(response.currentPortfolioScore());
        assertEquals("UNAVAILABLE", response.historicalPortfolioScore().state());
        assertNull(response.historicalPortfolioScore().portfolioScore());
        assertEquals(PortfolioScoreHistoryService.HISTORICAL_PORTFOLIO_COMPOSITION_UNAVAILABLE, response.historicalPortfolioScore().reason());

        PortfolioScoreHistoryDto.HoldingScoreHistoryDto holding = response.holdings().stream()
            .filter(h -> h.schemeOptionId().equals(1L))
            .findFirst()
            .orElseThrow();

        assertEquals(1L, holding.schemeOptionId());
        assertTrue(holding.fundName().contains("HDFC Flexi Cap Fund"));
        assertEquals("AVAILABLE", holding.selectedHistoricalFundScore().availabilityState());
        assertEquals(LocalDate.of(2024, 1, 15), holding.selectedHistoricalFundScore().asOfDate());
        assertNotNull(holding.holdingPerformance().absoluteGainLossPercentage());
        assertNotEquals(holding.holdingPerformance().absoluteGainLossPercentage(), holding.selectedHistoricalFundScore().score());
    }

    @Test
    @DisplayName("selects latest persisted score on or before requested date and never a future score")
    void selectsLatestSnapshotOnOrBeforeRequestedDate() {
        PortfolioScoreHistoryDto response = portfolioScoreHistoryService.getPortfolioScoreHistory(TEST_USER, LocalDate.of(2024, 1, 1));
        PortfolioScoreHistoryDto.ScoreSnapshotDto snapshot = response.holdings().get(0).selectedHistoricalFundScore();

        assertEquals("AVAILABLE", snapshot.availabilityState());
        assertEquals(LocalDate.of(2023, 12, 31), snapshot.asOfDate());
        assertNotEquals(LocalDate.of(2024, 1, 15), snapshot.asOfDate());
    }

    @Test
    @DisplayName("missing historical score remains explicit unavailable state")
    void missingHistoricalScoreIsUnavailable() {
        PortfolioScoreHistoryDto response = portfolioScoreHistoryService.getPortfolioScoreHistory(TEST_USER, LocalDate.of(2020, 1, 1));
        PortfolioScoreHistoryDto.ScoreSnapshotDto snapshot = response.holdings().get(0).selectedHistoricalFundScore();

        assertEquals("UNAVAILABLE", snapshot.availabilityState());
        assertNull(snapshot.score());
        assertTrue(snapshot.unavailableReason().contains("No persisted analytical score snapshot"));
    }

    @Test
    @DisplayName("reuses persisted dimensions and canonical MKT-05 metric identity")
    void exposesPersistedDimensionsAndCanonicalMetricIdentity() {
        PortfolioScoreHistoryDto response = portfolioScoreHistoryService.getPortfolioScoreHistory(TEST_USER, LocalDate.of(2024, 1, 15));
        PortfolioScoreHistoryDto.ScoreSnapshotDto snapshot = response.holdings().get(0).selectedHistoricalFundScore();

        assertFalse(snapshot.dimensions().isEmpty());
        PortfolioScoreHistoryDto.DimensionSnapshotDto returnQuality = snapshot.dimensions().stream()
            .filter(d -> "RETURN_QUALITY".equals(d.dimension()))
            .findFirst()
            .orElseThrow();
        assertEquals(0, new BigDecimal("0.3000").compareTo(returnQuality.weight()));

        PortfolioScoreHistoryDto.MetricContributionSnapshotDto mkt05 = snapshot.dimensions().stream()
            .flatMap(d -> d.metricContributions().stream())
            .filter(m -> "MKT-05".equals(m.metricCode()))
            .findFirst()
            .orElseThrow();
        assertEquals("Capture Spread 3Y", mkt05.metricName());
    }

    @Test
    @DisplayName("Auth0 isolation limits score history holdings to authenticated investor")
    void auth0IsolationLimitsHoldings() {
        PortfolioScoreHistoryDto other = portfolioScoreHistoryService.getPortfolioScoreHistory(OTHER_USER, LocalDate.of(2024, 1, 15));
        assertTrue(other.holdings().isEmpty());
        assertEquals("UNAVAILABLE", other.historicalPortfolioScore().state());
    }

    private void persistHistoricalScores() {
        SchemeOption option = schemeOptionRepository.findById(1L).orElseThrow();
        saveScore(option, LocalDate.of(2023, 12, 31), "63.00");
        saveScore(option, LocalDate.of(2024, 1, 15), "67.09");
    }

    private void saveScore(SchemeOption option, LocalDate asOfDate, String scoreValue) {
        AnalyticalScore score = new AnalyticalScore(
            option,
            null,
            new BigDecimal(scoreValue),
            new BigDecimal("0.91"),
            "AVAILABLE",
            "YUKIRA_SCORE_V1",
            "CANDIDATE",
            asOfDate,
            OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30)),
            "INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1",
            "Test persisted score snapshot"
        );
        ScoreDimension dimension = new ScoreDimension(
            score,
            "RETURN_QUALITY",
            "Return Quality",
            new BigDecimal(scoreValue),
            new BigDecimal("0.3000"),
            "AVAILABLE",
            new BigDecimal("0.91"),
            2,
            2,
            null
        );
        dimension.addMetricContribution(new ScoreMetricContribution(
            dimension,
            "MKT-05",
            "Capture Spread 3Y",
            new BigDecimal("4.1800000000"),
            new BigDecimal("0.6700"),
            "HIGHER_IS_BETTER",
            new BigDecimal("0.2500"),
            new BigDecimal("0.1675"),
            "ELIGIBLE",
            null,
            null,
            "%"
        ));
        score.addDimension(dimension);
        analyticalScoreRepository.save(score);
    }
}
