import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { PortfolioScoreHistoryDto, HoldingScoreHistoryDto } from '../../lib/api/portfolio';

describe('Portfolio Score History & PIT Intelligence V1 — Frontend Contract Tests', () => {

  it('TC-FE-SH01: Portfolio score history contract distinguishes current score, historical fund score, and historical portfolio score availability', () => {
    const mockResponse: PortfolioScoreHistoryDto = {
      requestedAsOfDate: '2024-01-15',
      selectionPolicy: 'Select the latest persisted analytical score snapshot whose asOfDate is less than or equal to the requested date.',
      currentPortfolioScore: {
        portfolioScore: 67.09,
        scoreState: 'COMPLETE',
        asOfDate: '2024-01-15',
        methodologyStatus: 'CANDIDATE',
        coveredHoldingCount: 1,
        totalHoldingCount: 1,
        coveredPortfolioWeight: 100.0,
        coveredPortfolioValue: 167067.20,
        totalValuedPortfolioValue: 167067.20,
        excludedHoldingCount: 0,
        scoreVersion: 'YUKIRA_SCORE_V1',
        contributingHoldings: [],
        excludedHoldings: []
      },
      historicalPortfolioScore: {
        state: 'UNAVAILABLE',
        requestedAsOfDate: '2024-01-15',
        portfolioScore: null,
        reason: 'Historical portfolio composition is not available for this date.',
        evidenceBoundary: 'Current holdings do not constitute a historical holdings ledger; no historical portfolio score is reconstructed.'
      },
      holdings: [
        {
          schemeOptionId: 1,
          fundName: 'HDFC Flexi Cap Fund',
          amcName: 'HDFC Mutual Fund',
          amfiCode: '118955',
          isin: 'INF179K01UT0',
          holdingPerformance: {
            valuationState: 'CURRENT',
            units: 100.0,
            availableValue: 167067.20,
            investedAmount: 150000.0,
            absoluteGainLoss: 17067.20,
            absoluteGainLossPercentage: 11.38,
            currentPortfolioWeight: 100.0
          },
          currentFundScore: {
            availabilityState: 'AVAILABLE',
            analyticalScoreId: 10,
            schemeOptionId: 1,
            score: 67.09,
            confidence: 0.91,
            status: 'AVAILABLE',
            scoreVersion: 'YUKIRA_SCORE_V1',
            methodologyStatus: 'CANDIDATE',
            asOfDate: '2024-01-15',
            knowledgeCutoffTime: '2024-01-31T23:59:59+05:30',
            calculationRunId: 100,
            referencePopulation: 'INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1',
            unavailableReason: null,
            dimensions: []
          },
          selectedHistoricalFundScore: {
            availabilityState: 'AVAILABLE',
            analyticalScoreId: 9,
            schemeOptionId: 1,
            score: 63.00,
            confidence: 0.91,
            status: 'AVAILABLE',
            scoreVersion: 'YUKIRA_SCORE_V1',
            methodologyStatus: 'CANDIDATE',
            asOfDate: '2023-12-31',
            knowledgeCutoffTime: '2023-12-31T23:59:59+05:30',
            calculationRunId: 99,
            referencePopulation: 'INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1',
            unavailableReason: null,
            dimensions: [
              {
                id: 1,
                dimension: 'RETURN_QUALITY',
                dimensionName: 'Return Quality',
                score: 63.00,
                weight: 0.3000,
                status: 'AVAILABLE',
                confidence: 0.91,
                eligibleMetricCount: 2,
                totalMetricCount: 2,
                metricContributions: [
                  {
                    id: 1,
                    metricCode: 'MKT-05',
                    metricName: 'Capture Spread 3Y',
                    rawValue: 4.18,
                    normalizedValue: 0.67,
                    direction: 'HIGHER_IS_BETTER',
                    weight: 0.25,
                    contribution: 0.1675,
                    eligibility: 'ELIGIBLE',
                    exclusionReason: null,
                    unit: '%'
                  }
                ]
              }
            ]
          },
          historicalFundScores: []
        }
      ],
      dataQualityLimitations: [
        'Historical portfolio composition is not available for this date.'
      ],
      investigationQuestions: [
        'Review historical fund score evidence separately from investor-specific gain/loss until historical portfolio holdings composition is available.'
      ]
    };

    assert.equal(mockResponse.currentPortfolioScore?.portfolioScore, 67.09);
    assert.equal(mockResponse.historicalPortfolioScore.state, 'UNAVAILABLE');
    assert.equal(mockResponse.historicalPortfolioScore.portfolioScore, null);
    assert.equal(mockResponse.holdings[0].selectedHistoricalFundScore.score, 63.00);
    assert.equal(mockResponse.holdings[0].selectedHistoricalFundScore.asOfDate, '2023-12-31');
    assert.notEqual(mockResponse.holdings[0].holdingPerformance.absoluteGainLossPercentage, mockResponse.holdings[0].selectedHistoricalFundScore.score);
  });

  it('TC-FE-SH02: MKT-05 metric identity is preserved in score snapshot dimensions', () => {
    const holding: HoldingScoreHistoryDto = {
      schemeOptionId: 1,
      fundName: 'HDFC Flexi Cap Fund',
      amcName: 'HDFC Mutual Fund',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      holdingPerformance: {
        valuationState: 'CURRENT',
        units: 100.0,
        availableValue: 167067.20,
        investedAmount: 150000.0,
        absoluteGainLoss: 17067.20,
        absoluteGainLossPercentage: 11.38,
        currentPortfolioWeight: 100.0
      },
      currentFundScore: null,
      selectedHistoricalFundScore: {
        availabilityState: 'AVAILABLE',
        analyticalScoreId: 9,
        schemeOptionId: 1,
        score: 63.00,
        confidence: 0.91,
        status: 'AVAILABLE',
        scoreVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'CANDIDATE',
        asOfDate: '2023-12-31',
        knowledgeCutoffTime: '2023-12-31T23:59:59+05:30',
        calculationRunId: 99,
        referencePopulation: 'INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1',
        unavailableReason: null,
        dimensions: [
          {
            id: 1,
            dimension: 'RETURN_QUALITY',
            dimensionName: 'Return Quality',
            score: 63.00,
            weight: 0.3000,
            status: 'AVAILABLE',
            confidence: 0.91,
            eligibleMetricCount: 2,
            totalMetricCount: 2,
            metricContributions: [
              {
                id: 1,
                metricCode: 'MKT-05',
                metricName: 'Capture Spread 3Y',
                rawValue: 4.18,
                normalizedValue: 0.67,
                direction: 'HIGHER_IS_BETTER',
                weight: 0.25,
                contribution: 0.1675,
                eligibility: 'ELIGIBLE',
                exclusionReason: null,
                unit: '%'
              }
            ]
          }
        ]
      },
      historicalFundScores: []
    };

    const metric = holding.selectedHistoricalFundScore.dimensions[0].metricContributions[0];
    assert.equal(metric.metricCode, 'MKT-05');
    assert.equal(metric.metricName, 'Capture Spread 3Y');
  });

  it('TC-FE-SH03: Missing historical fund score resolves to explicit UNAVAILABLE state without fabrication', () => {
    const holding: HoldingScoreHistoryDto = {
      schemeOptionId: 1,
      fundName: 'HDFC Flexi Cap Fund',
      amcName: 'HDFC Mutual Fund',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      holdingPerformance: {
        valuationState: 'CURRENT',
        units: 100.0,
        availableValue: 167067.20,
        investedAmount: 150000.0,
        absoluteGainLoss: 17067.20,
        absoluteGainLossPercentage: 11.38,
        currentPortfolioWeight: 100.0
      },
      currentFundScore: null,
      selectedHistoricalFundScore: {
        availabilityState: 'UNAVAILABLE',
        analyticalScoreId: null,
        schemeOptionId: 1,
        score: null,
        confidence: null,
        status: 'NOT_AVAILABLE',
        scoreVersion: null,
        methodologyStatus: null,
        asOfDate: null,
        knowledgeCutoffTime: null,
        calculationRunId: null,
        referencePopulation: null,
        unavailableReason: 'No persisted analytical score snapshot exists on or before 2020-01-01.',
        dimensions: []
      },
      historicalFundScores: []
    };

    assert.equal(holding.selectedHistoricalFundScore.availabilityState, 'UNAVAILABLE');
    assert.equal(holding.selectedHistoricalFundScore.score, null);
    assert.equal(holding.selectedHistoricalFundScore.unavailableReason, 'No persisted analytical score snapshot exists on or before 2020-01-01.');
  });
});
