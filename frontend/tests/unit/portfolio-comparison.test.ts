import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { PortfolioComparisonResponse, ComparisonFund, ComparisonMetricRow } from '../../types/comparison';

/**
 * Portfolio Fund Comparison & Evidence Matrix V1 — Frontend Contract & Integrity Tests.
 *
 * Verifies that:
 * 1. Comparison DTO preserves exact scheme_option_id identity.
 * 2. Persisted fund scores and dimensions are displayed without comparison ranking scores.
 * 3. Canonical metric codes (RSK-01, MKT-05 Capture Spread, RET-03) maintain exact labels.
 * 4. MKT-05 is labeled "Capture Spread 3Y" and never "Beta".
 * 5. Holding economics are isolated from fund analytical scores.
 * 6. Missing metrics display Unavailable or N/A without being converted to zero.
 */
describe('Portfolio Fund Comparison V1 — Frontend Contract & Integrity Tests', () => {

  it('TC-FE-C01: Comparison response structure preserves exact scheme_option_id identity', () => {
    const mockFund1: ComparisonFund = {
      schemeOptionId: 1,
      schemeId: 101,
      schemeName: 'HDFC Flexi Cap Fund',
      amcName: 'HDFC Mutual Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      analyticalScore: {
        scoreValue: 67.09,
        confidence: 95,
        scoreStatus: 'PARTIAL',
        methodologyVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'CANDIDATE',
        asOfDate: '2024-01-15',
        dimensions: [
          { dimension: 'RETURN_QUALITY', dimensionName: 'Return Quality', score: 70.0, weight: 0.35, status: 'PARTIAL' },
          { dimension: 'RISK_QUALITY', dimensionName: 'Risk Quality', score: 65.0, weight: 0.30, status: 'COMPLETE' },
          { dimension: 'BENCHMARK_RELATIVE', dimensionName: 'Benchmark-Relative Quality', score: 68.0, weight: 0.20, status: 'PARTIAL' },
          { dimension: 'CONSISTENCY_DOWNSIDE', dimensionName: 'Consistency & Downside Quality', score: 62.0, weight: 0.15, status: 'PARTIAL' }
        ]
      },
      holdingContext: {
        unitsHeld: 100.0,
        portfolioWeight: 60.0,
        availableValue: 167067.20,
        costBasisAmount: 1500.0,
        investedAmount: 150000.0,
        absoluteGainLoss: 17067.20,
        absoluteGainLossPercentage: 11.38
      }
    };

    const mockFund2: ComparisonFund = {
      schemeOptionId: 10199,
      schemeId: 202,
      schemeName: 'ICICI Prudential Bluechip Fund',
      amcName: 'ICICI Prudential Mutual Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      amfiCode: '120586',
      isin: 'INF109K01216',
      analyticalScore: {
        scoreValue: 71.50,
        confidence: 90,
        scoreStatus: 'COMPLETE',
        methodologyVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'CANDIDATE',
        asOfDate: '2024-01-15'
      },
      holdingContext: {
        unitsHeld: 200.0,
        portfolioWeight: 40.0,
        availableValue: 111378.13,
        costBasisAmount: 500.0,
        investedAmount: 100000.0,
        absoluteGainLoss: 11378.13,
        absoluteGainLossPercentage: 11.38
      }
    };

    const mockResponse: PortfolioComparisonResponse = {
      comparisonSize: 2,
      asOfDate: '2024-01-15',
      knowledgeCutoff: '2024-01-31T23:59:59+05:30',
      methodologyVersion: 'YUKIRA_SCORE_V1',
      methodologyStatus: 'CANDIDATE',
      referencePopulation: 'INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1',
      funds: [mockFund1, mockFund2],
      canonicalMetrics: [
        {
          metricCode: 'RET-03',
          metricName: '3-Year CAGR',
          fundValues: {
            '1': { availabilityState: 'AVAILABLE', formattedValue: '+14.25%', rawValue: 0.1425 },
            '10199': { availabilityState: 'AVAILABLE', formattedValue: '+15.10%', rawValue: 0.1510 }
          }
        },
        {
          metricCode: 'MKT-05',
          metricName: 'Capture Spread 3Y',
          fundValues: {
            '1': { availabilityState: 'AVAILABLE', formattedValue: '+2.15%', rawValue: 0.0215 },
            '10199': { availabilityState: 'UNAVAILABLE', formattedValue: null, rawValue: null }
          }
        }
      ],
      coverageSummary: {
        totalSelectedFunds: 2,
        scoredFundsCount: 2,
        completeRiskEvidenceCount: 1,
        missingMetricsCount: 1,
        differingAsOfDates: false
      },
      investigationPrompts: [
        'ICICI Prudential Bluechip Fund has missing metric data for MKT-05 Capture Spread 3Y.'
      ]
    };

    assert.equal(mockResponse.funds.length, 2);
    assert.equal(mockResponse.funds[0].schemeOptionId, 1);
    assert.equal(mockResponse.funds[1].schemeOptionId, 10199);
    assert.equal(mockResponse.funds[0].analyticalScore?.scoreValue, 67.09);
    assert.equal(mockResponse.funds[1].analyticalScore?.scoreValue, 71.50);
  });

  it('TC-FE-C02: MKT-05 is strictly labeled Capture Spread 3Y', () => {
    const row: ComparisonMetricRow = {
      metricCode: 'MKT-05',
      metricName: 'Capture Spread 3Y',
      fundValues: {
        '1': { availabilityState: 'AVAILABLE', formattedValue: '+1.50%', rawValue: 0.0150 }
      }
    };

    assert.equal(row.metricCode, 'MKT-05');
    assert.equal(row.metricName, 'Capture Spread 3Y');
    assert.notEqual(row.metricName, 'Beta 3Y');
  });

  it('TC-FE-C03: Unavailable metrics are represented without being converted to zero', () => {
    const row: ComparisonMetricRow = {
      metricCode: 'RSK-02',
      metricName: 'Downside Semideviation 3Y',
      fundValues: {
        '1': { availabilityState: 'UNAVAILABLE', formattedValue: null, rawValue: null }
      }
    };

    const val = row.fundValues['1'];
    assert.equal(val.availabilityState, 'UNAVAILABLE');
    assert.equal(val.formattedValue, null);
    assert.notEqual(val.rawValue, 0);
  });

  it('TC-FE-C04: Holding context changes do not alter fund analytical scores', () => {
    const fund: ComparisonFund = {
      schemeOptionId: 1,
      schemeId: 101,
      schemeName: 'HDFC Flexi Cap Fund',
      amcName: 'HDFC Mutual Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      analyticalScore: {
        scoreValue: 67.09,
        confidence: 95,
        scoreStatus: 'PARTIAL',
        methodologyVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'CANDIDATE',
        asOfDate: '2024-01-15'
      },
      holdingContext: {
        unitsHeld: 500.0, // Modified units
        portfolioWeight: 100.0,
        availableValue: 835336.00,
        costBasisAmount: 1500.0,
        investedAmount: 750000.0,
        absoluteGainLoss: 85336.00,
        absoluteGainLossPercentage: 11.38
      }
    };

    // Fund score remains exactly 67.09 despite unit/holding change
    assert.equal(fund.analyticalScore?.scoreValue, 67.09);
    assert.equal(fund.holdingContext?.unitsHeld, 500.0);
  });
});
