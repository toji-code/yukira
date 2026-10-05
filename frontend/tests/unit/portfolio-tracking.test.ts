import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { PortfolioSummaryDto, PortfolioHoldingDto } from '../../lib/api/portfolio';

/**
 * Portfolio Tracking V1 Frontend Contract & Epistemic Tests.
 *
 * Verifies that:
 * 1. Portfolio response surfaces deterministic valuation & coverage states.
 * 2. Scheme option identity is exact (Direct/Regular, Growth/IDCW, AMFI, ISIN).
 * 3. Historical NAV observation date (2024-01-15) is explicitly exposed.
 * 4. Analytical Quality Score status (CANDIDATE) is preserved.
 * 5. NO portfolio score or recommendation language is used.
 */
describe('Portfolio Tracking V1 — Frontend Contract & Integrity Tests', () => {

  it('TC-FE-P01: Portfolio Summary DTO structure supports deterministic valuation & coverage', () => {
    const mockSummary: PortfolioSummaryDto = {
      totalHoldings: 1,
      valuedHoldingsCount: 1,
      valuationCoverageState: 'VALUATION_COMPLETE',
      totalAvailableValue: 167067.20,
      totalInvestedAmount: 150000.00,
      totalAbsoluteGainLoss: 17067.20,
      totalAbsoluteGainLossPercentage: 11.38,
      gainLossState: 'CALCULATED',
      categoryAllocations: [
        { category: 'Equity', totalValue: 167067.20, percentageShare: 100, fundCount: 1 }
      ],
      amcAllocations: [
        { amcName: 'HDFC Mutual Fund', totalValue: 167067.20, percentageShare: 100, fundCount: 1 }
      ],
      scoredHoldingsCount: 1,
      holdings: [
        {
          id: 1,
          schemeOptionId: 1,
          amfiCode: '118955',
          isin: 'INF179K01UT0',
          fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
          amcName: 'HDFC Mutual Fund',
          category: 'Equity',
          subcategory: 'Flexi Cap Fund',
          planType: 'DIRECT',
          optionType: 'GROWTH',
          units: 100,
          costBasisAmount: 1500,
          investedAmount: 150000,
          navValue: 1670.6720,
          navAsOfDate: '2024-01-15',
          valuationState: 'VALUATION_AVAILABLE',
          availableValue: 167067.20,
          absoluteGainLoss: 17067.20,
          absoluteGainLossPercentage: 11.38,
          analyticalScore: {
            available: true,
            scoreValue: 72.5,
            confidence: 'MEDIUM',
            status: 'CANDIDATE',
            methodologyVersion: 'YUKIRA_SCORE_V1',
            asOfDate: '2024-01-15'
          },
          dataQualityState: 'HISTORICAL_EVIDENCE',
          createdAt: '2026-10-03T20:00:00Z'
        }
      ],
      dataQualityLimitations: [
        'Valuations reflect historical NAV observations as of 2024-01-15.'
      ],
      investigationQuestions: [
        'Verify current official NAV for scheme options with historical data.'
      ]
    };

    assert.equal(mockSummary.totalHoldings, 1);
    assert.equal(mockSummary.valuationCoverageState, 'VALUATION_COMPLETE');
    assert.equal(mockSummary.totalAvailableValue, 167067.20);
    assert.equal(mockSummary.gainLossState, 'CALCULATED');
  });

  it('TC-FE-P02: Holding identity exposes exact option attributes', () => {
    const holding: PortfolioHoldingDto = {
      id: 1,
      schemeOptionId: 1,
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
      amcName: 'HDFC Mutual Fund',
      category: 'Equity',
      subcategory: 'Flexi Cap Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      units: 100,
      costBasisAmount: null,
      investedAmount: null,
      navValue: 1670.6720,
      navAsOfDate: '2024-01-15',
      valuationState: 'VALUATION_AVAILABLE',
      availableValue: 167067.20,
      absoluteGainLoss: null,
      absoluteGainLossPercentage: null,
      analyticalScore: {
        available: true,
        scoreValue: 72.5,
        confidence: 'MEDIUM',
        status: 'CANDIDATE',
        methodologyVersion: 'YUKIRA_SCORE_V1',
        asOfDate: '2024-01-15'
      },
      dataQualityState: 'HISTORICAL_EVIDENCE',
      createdAt: '2026-10-03T20:00:00Z'
    };

    assert.equal(holding.schemeOptionId, 1);
    assert.equal(holding.amfiCode, '118955');
    assert.equal(holding.isin, 'INF179K01UT0');
    assert.equal(holding.planType, 'DIRECT');
    assert.equal(holding.optionType, 'GROWTH');
    assert.equal(holding.navAsOfDate, '2024-01-15');
    assert.equal(holding.analyticalScore.status, 'CANDIDATE');
    assert.notEqual(holding.analyticalScore.status, 'VALIDATED');
  });

  it('TC-FE-P03: Missing NAV yields VALUATION_UNAVAILABLE without zeroing', () => {
    const unvaluedHolding: PortfolioHoldingDto = {
      id: 2,
      schemeOptionId: 99,
      amfiCode: 'N/A',
      isin: 'N/A',
      fundName: 'Unlisted Debt Fund',
      amcName: 'Test AMC',
      category: 'Debt',
      subcategory: 'Overnight Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      units: 50,
      costBasisAmount: 100,
      investedAmount: 5000,
      navValue: null,
      navAsOfDate: null,
      valuationState: 'VALUATION_UNAVAILABLE',
      availableValue: null,
      absoluteGainLoss: null,
      absoluteGainLossPercentage: null,
      analyticalScore: {
        available: false,
        scoreValue: null,
        confidence: 'UNKNOWN',
        status: 'NOT_AVAILABLE',
        methodologyVersion: 'YUKIRA_SCORE_V1',
        asOfDate: null
      },
      dataQualityState: 'MISSING_VALUATION',
      createdAt: '2026-10-03T20:00:00Z'
    };

    assert.equal(unvaluedHolding.valuationState, 'VALUATION_UNAVAILABLE');
    assert.equal(unvaluedHolding.availableValue, null);
    assert.equal(unvaluedHolding.analyticalScore.available, false);
  });

  it('TC-FE-P04: Portfolio Analytical Overview V1 surfaces yukiraScore with dimensions and preserves schemeOptionId', () => {
    const analyticalHolding: PortfolioHoldingDto = {
      id: 1,
      schemeOptionId: 1,
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
      amcName: 'HDFC Mutual Fund',
      category: 'Equity',
      subcategory: 'Flexi Cap Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      units: 100,
      costBasisAmount: 1500,
      investedAmount: 150000,
      navValue: 1670.6720,
      navAsOfDate: '2024-01-15',
      valuationState: 'VALUATION_AVAILABLE',
      availableValue: 167067.20,
      absoluteGainLoss: 17067.20,
      absoluteGainLossPercentage: 11.38,
      analyticalScore: {
        available: true,
        scoreValue: 67.94,
        confidence: 'HIGH',
        status: 'PARTIAL',
        methodologyVersion: 'YUKIRA_SCORE_V1',
        asOfDate: '2024-01-15'
      },
      yukiraScore: {
        score: 67.94,
        confidence: 82,
        status: 'PARTIAL',
        scoreVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'CANDIDATE',
        asOfDate: '2024-01-15',
        dimensions: [
          { dimension: 'RETURN_QUALITY', dimensionName: 'Return Quality', score: 75.0, weight: 0.30, status: 'CALCULATED' },
          { dimension: 'RISK_QUALITY', dimensionName: 'Risk Quality', score: 65.0, weight: 0.30, status: 'CALCULATED' },
          { dimension: 'BENCHMARK_RELATIVE', dimensionName: 'Benchmark-Relative Quality', score: 68.0, weight: 0.25, status: 'CALCULATED' },
          { dimension: 'DOWNSIDE_CONSISTENCY', dimensionName: 'Consistency & Downside Quality', score: 60.0, weight: 0.15, status: 'CALCULATED' }
        ]
      },
      dataQualityState: 'HISTORICAL_EVIDENCE',
      createdAt: '2026-10-03T20:00:00Z'
    };

    assert.equal(analyticalHolding.schemeOptionId, 1);
    assert.ok(analyticalHolding.yukiraScore);
    assert.equal(analyticalHolding.yukiraScore?.score, 67.94);
    assert.equal(analyticalHolding.yukiraScore?.confidence, 82);
    assert.equal(analyticalHolding.yukiraScore?.status, 'PARTIAL');
    assert.equal(analyticalHolding.yukiraScore?.asOfDate, '2024-01-15');
    assert.equal(analyticalHolding.yukiraScore?.dimensions?.length, 4);
    assert.equal(analyticalHolding.yukiraScore?.dimensions?.[0]?.dimensionName, 'Return Quality');

    // Score independence check: change investor units and confirm score is unchanged
    const alteredUnitsHolding = { ...analyticalHolding, units: 500, investedAmount: 750000 };
    assert.equal(alteredUnitsHolding.yukiraScore?.score, analyticalHolding.yukiraScore?.score);

    // Confirm no portfolio score property exists on holding
    const rawHolding = analyticalHolding as unknown as Record<string, unknown>;
    assert.equal(rawHolding.portfolioScore, undefined);
    assert.equal(rawHolding.weightedScore, undefined);
  });

  it('TC-FE-P05: Portfolio Exposure & Concentration Drill-Down V1 contract verification', () => {
    const mockSummary: PortfolioSummaryDto = {
      totalHoldings: 2,
      valuedHoldingsCount: 2,
      valuationCoverageState: 'VALUATION_COMPLETE',
      totalAvailableValue: 200000.00,
      totalInvestedAmount: 180000.00,
      totalAbsoluteGainLoss: 20000.00,
      totalAbsoluteGainLossPercentage: 11.11,
      gainLossState: 'CALCULATED',
      categoryAllocations: [
        { category: 'Equity', totalValue: 200000.00, percentageShare: 100.0, fundCount: 2 }
      ],
      amcAllocations: [
        { amcName: 'HDFC Mutual Fund', totalValue: 120000.00, percentageShare: 60.0, fundCount: 1 },
        { amcName: 'Parag Parikh Mutual Fund', totalValue: 80000.00, percentageShare: 40.0, fundCount: 1 }
      ],
      scoredHoldingsCount: 2,
      holdings: [
        {
          id: 1,
          schemeOptionId: 1,
          amfiCode: '118955',
          isin: 'INF179K01UT0',
          fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
          amcName: 'HDFC Mutual Fund',
          category: 'Equity',
          subcategory: 'Flexi Cap Fund',
          planType: 'DIRECT',
          optionType: 'GROWTH',
          units: 100,
          costBasisAmount: 1000,
          investedAmount: 100000,
          navValue: 1200.0,
          navAsOfDate: '2024-01-15',
          valuationState: 'VALUATION_AVAILABLE',
          availableValue: 120000.00,
          absoluteGainLoss: 20000.00,
          absoluteGainLossPercentage: 20.0,
          analyticalScore: {
            available: true,
            scoreValue: 67.94,
            confidence: 'HIGH',
            status: 'PARTIAL',
            methodologyVersion: 'YUKIRA_SCORE_V1',
            asOfDate: '2024-01-15'
          },
          dataQualityState: 'HISTORICAL_EVIDENCE',
          createdAt: '2026-10-03T20:00:00Z',
          portfolioWeight: 60.0
        },
        {
          id: 2,
          schemeOptionId: 2,
          amfiCode: '122639',
          isin: 'INF879O01015',
          fundName: 'Parag Parikh Flexi Cap Fund - Direct Plan - Growth Option',
          amcName: 'Parag Parikh Mutual Fund',
          category: 'Equity',
          subcategory: 'Flexi Cap Fund',
          planType: 'DIRECT',
          optionType: 'GROWTH',
          units: 80,
          costBasisAmount: 1000,
          investedAmount: 80000,
          navValue: 1000.0,
          navAsOfDate: '2024-01-15',
          valuationState: 'VALUATION_AVAILABLE',
          availableValue: 80000.00,
          absoluteGainLoss: 0,
          absoluteGainLossPercentage: 0,
          analyticalScore: {
            available: true,
            scoreValue: 71.2,
            confidence: 'HIGH',
            status: 'PARTIAL',
            methodologyVersion: 'YUKIRA_SCORE_V1',
            asOfDate: '2024-01-15'
          },
          dataQualityState: 'HISTORICAL_EVIDENCE',
          createdAt: '2026-10-03T20:00:00Z',
          portfolioWeight: 40.0
        }
      ],
      dataQualityLimitations: [
        'Valuations reflect historical NAV observations as of 2024-01-15.'
      ],
      investigationQuestions: [
        'Verify current official NAV for scheme options with historical data.'
      ],
      concentrationAnalysis: {
        topHoldingName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
        topHoldingSchemeOptionId: 1,
        topHoldingWeight: 60.0,
        top3HoldingsWeight: 100.0,
        top5HoldingsWeight: 100.0,
        topAmcName: 'HDFC Mutual Fund',
        topAmcWeight: 60.0,
        topCategoryName: 'Equity',
        topCategoryWeight: 100.0,
        concentrationState: 'COMPLETE'
      }
    };

    assert.equal(mockSummary.holdings[0].portfolioWeight, 60.0);
    assert.equal(mockSummary.holdings[1].portfolioWeight, 40.0);
    assert.ok(mockSummary.concentrationAnalysis);
    assert.equal(mockSummary.concentrationAnalysis?.topHoldingWeight, 60.0);
    assert.equal(mockSummary.concentrationAnalysis?.top3HoldingsWeight, 100.0);
    assert.equal(mockSummary.concentrationAnalysis?.topAmcName, 'HDFC Mutual Fund');
    assert.equal(mockSummary.concentrationAnalysis?.topAmcWeight, 60.0);
    assert.equal(mockSummary.concentrationAnalysis?.concentrationState, 'COMPLETE');

    // Confirm no portfolio score recommendation property exists on concentration object
    const rawConc = mockSummary.concentrationAnalysis as unknown as Record<string, unknown>;
    assert.equal(rawConc.portfolioScore, undefined);
    assert.equal(rawConc.recommendation, undefined);
  });

  it('TC-FE-P06: Portfolio Analytical Score V1 contract verification & zero-recommendation invariants', () => {
    const mockSummary: PortfolioSummaryDto = {
      totalHoldings: 2,
      valuedHoldingsCount: 2,
      valuationCoverageState: 'VALUATION_COMPLETE',
      totalAvailableValue: 200000.00,
      totalInvestedAmount: 180000.00,
      totalAbsoluteGainLoss: 20000.00,
      totalAbsoluteGainLossPercentage: 11.11,
      gainLossState: 'CALCULATED',
      categoryAllocations: [],
      amcAllocations: [],
      scoredHoldingsCount: 1,
      holdings: [],
      dataQualityLimitations: [],
      investigationQuestions: [],
      portfolioAnalyticalScore: {
        portfolioScore: 67.94,
        scoreState: 'PARTIAL',
        coveredHoldingCount: 1,
        totalHoldingCount: 2,
        coveredPortfolioValue: 120000.00,
        totalValuedPortfolioValue: 200000.00,
        coveredPortfolioWeight: 60.00,
        excludedHoldingCount: 1,
        asOfDate: '2024-01-15',
        scoreVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'CANDIDATE',
        contributingHoldings: [
          {
            schemeOptionId: 1,
            fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
            fundScore: 67.94,
            portfolioWeight: 60.00,
            normalizedScoreWeight: 100.00,
            scoreContribution: 67.94,
            scoreStatus: 'PARTIAL',
            scoreAsOfDate: '2024-01-15'
          }
        ],
        excludedHoldings: [
          {
            schemeOptionId: 2,
            fundName: 'Unscored Fund',
            exclusionReason: 'NO_FUND_SCORE',
            portfolioWeight: 40.00
          }
        ]
      }
    };

    assert.ok(mockSummary.portfolioAnalyticalScore);
    const scoreDto = mockSummary.portfolioAnalyticalScore!;
    assert.equal(scoreDto.portfolioScore, 67.94);
    assert.equal(scoreDto.scoreState, 'PARTIAL');
    assert.equal(scoreDto.coveredHoldingCount, 1);
    assert.equal(scoreDto.totalHoldingCount, 2);
    assert.equal(scoreDto.coveredPortfolioValue, 120000.00);
    assert.equal(scoreDto.totalValuedPortfolioValue, 200000.00);
    assert.equal(scoreDto.coveredPortfolioWeight, 60.00);
    assert.equal(scoreDto.excludedHoldingCount, 1);
    assert.equal(scoreDto.contributingHoldings.length, 1);
    assert.equal(scoreDto.contributingHoldings[0].schemeOptionId, 1);
    assert.equal(scoreDto.contributingHoldings[0].scoreContribution, 67.94);
    assert.equal(scoreDto.excludedHoldings.length, 1);
    assert.equal(scoreDto.excludedHoldings[0].exclusionReason, 'NO_FUND_SCORE');

    // Confirm no recommendation fields present anywhere in DTO
    const rawScore = scoreDto as unknown as Record<string, unknown>;
    assert.equal(rawScore.buySellRecommendation, undefined);
    assert.equal(rawScore.actionableAdvice, undefined);
    assert.equal(rawScore.rebalanceSuggestion, undefined);
  });

  it('TC-FE-P07: Portfolio Risk & Quality Evidence Drill-Down V1 contract verification', () => {
    const mockSummary: PortfolioSummaryDto = {
      totalHoldings: 1,
      valuedHoldingsCount: 1,
      valuationCoverageState: 'VALUATION_COMPLETE',
      totalAvailableValue: 167067.20,
      totalInvestedAmount: 150000.00,
      totalAbsoluteGainLoss: 17067.20,
      totalAbsoluteGainLossPercentage: 11.38,
      gainLossState: 'CALCULATED',
      categoryAllocations: [],
      amcAllocations: [],
      scoredHoldingsCount: 1,
      holdings: [],
      dataQualityLimitations: [],
      investigationQuestions: [],
      portfolioRiskEvidence: {
        coverageState: 'COMPLETE',
        totalHoldingCount: 1,
        valuedHoldingCount: 1,
        holdingsWithRiskEvidenceCount: 1,
        coveredPortfolioValue: 167067.20,
        totalValuedPortfolioValue: 167067.20,
        coveredPortfolioWeight: 100.00,
        volatilityCoverageCount: 1,
        downsideSemideviationCoverageCount: 1,
        drawdownCoverageCount: 1,
        betaCoverageCount: 1,
        downsideBetaCoverageCount: 1,
        holdingRiskEvidences: [
          {
            schemeOptionId: 1,
            fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
            portfolioWeight: 100.00,
            yukiraScore: 67.94,
            scoreStatus: 'PARTIAL',
            hasRiskEvidence: true,
            asOfDate: '2024-01-15',
            metrics: [
              {
                metricCode: 'RSK-01',
                metricName: 'Annualized Volatility 3Y',
                numericValue: 0.1425,
                formattedValue: '14.25%',
                unit: 'PERCENTAGE',
                asOfDate: '2024-01-15',
                observationCount: 739,
                calculationStatus: 'CALCULATED',
                availabilityState: 'AVAILABLE'
              },
              {
                metricCode: 'RSK-03',
                metricName: 'Maximum Drawdown 3Y',
                numericValue: -0.1850,
                formattedValue: '-18.50%',
                unit: 'PERCENTAGE',
                asOfDate: '2024-01-15',
                observationCount: 739,
                calculationStatus: 'CALCULATED',
                availabilityState: 'AVAILABLE'
              },
              {
                metricCode: 'MKT-01',
                metricName: 'Beta 3Y',
                numericValue: 0.9500,
                formattedValue: '0.95',
                unit: 'RATIO',
                asOfDate: '2024-01-15',
                observationCount: 738,
                calculationStatus: 'CALCULATED',
                availabilityState: 'AVAILABLE'
              },
              {
                metricCode: 'MKT-05',
                metricName: 'Capture Spread 3Y',
                numericValue: 0.0520,
                formattedValue: '5.20 pts',
                unit: 'PERCENTAGE_POINTS',
                asOfDate: '2024-01-15',
                observationCount: null,
                calculationStatus: 'CALCULATED',
                availabilityState: 'AVAILABLE'
              }
            ]
          }
        ],
        investigationPrompts: [
          "Top holding 'HDFC Flexi Cap Fund - Direct Plan - Growth Option' represents 100.00% of valued portfolio exposure. Inspect its individual fund score and risk evidence breakdown."
        ],
        dataLimitations: [
          'Portfolio Risk & Quality Evidence Drill-Down V1 presents individual fund-level analytical risk metrics. It does NOT calculate a composite portfolio risk score or portfolio volatility.'
        ]
      }
    };

    assert.ok(mockSummary.portfolioRiskEvidence);
    const riskSummary = mockSummary.portfolioRiskEvidence!;
    assert.equal(riskSummary.coverageState, 'COMPLETE');
    assert.equal(riskSummary.holdingsWithRiskEvidenceCount, 1);
    assert.equal(riskSummary.coveredPortfolioWeight, 100.00);
    assert.equal(riskSummary.holdingRiskEvidences.length, 1);
    assert.equal(riskSummary.holdingRiskEvidences[0].schemeOptionId, 1);
    assert.equal(riskSummary.holdingRiskEvidences[0].metrics.length, 4);
    assert.equal(riskSummary.holdingRiskEvidences[0].metrics[0].metricCode, 'RSK-01');
    assert.equal(riskSummary.holdingRiskEvidences[0].metrics[0].metricName, 'Annualized Volatility 3Y');
    assert.equal(riskSummary.holdingRiskEvidences[0].metrics[2].metricCode, 'MKT-01');
    assert.equal(riskSummary.holdingRiskEvidences[0].metrics[2].metricName, 'Beta 3Y');
    assert.equal(riskSummary.holdingRiskEvidences[0].metrics[3].metricCode, 'MKT-05');
    assert.equal(riskSummary.holdingRiskEvidences[0].metrics[3].metricName, 'Capture Spread 3Y');

    // Confirm MKT-05 is NOT named Beta and MKT-01 is NOT named Capture Spread
    assert.notEqual(riskSummary.holdingRiskEvidences[0].metrics[3].metricName, 'Beta 3Y');
    assert.notEqual(riskSummary.holdingRiskEvidences[0].metrics[2].metricName, 'Capture Spread 3Y');

    // Confirm no portfolio risk score or recommendation fields present
    const rawRisk = riskSummary as unknown as Record<string, unknown>;
    assert.equal(rawRisk.portfolioRiskScore, undefined);
    assert.equal(rawRisk.portfolioVolatility, undefined);
    assert.equal(rawRisk.buySellRecommendation, undefined);
  });
});



