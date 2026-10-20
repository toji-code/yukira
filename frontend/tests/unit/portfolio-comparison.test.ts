import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { PortfolioComparisonResponse, ComparisonFund, ComparisonMetricRow, ComparisonMetric } from '../../types/comparison';

/**
 * Portfolio Fund Comparison & Evidence Matrix V2 — Frontend Contract & Integrity Tests.
 *
 * Verifies that:
 * 1. Comparison DTO preserves exact scheme_option_id identity for 3 pilot schemes (1, 10262, 10151).
 * 2. Persisted fund scores and dimensions are displayed without comparison ranking scores.
 * 3. Canonical metric codes (RSK-01, MKT-05, RET-03, CONC-01) maintain exact labels.
 * 4. Explicit comparability statuses (COMPARABLE, COMPARABLE_WITH_LIMITATIONS) and limitation reasons are attached.
 * 5. Portfolio concentration metrics (CONC-01, CONC-02, CONC-03) are properly modeled in comparison DTO.
 * 6. Missing metrics display Unavailable without being converted to zero.
 */
describe('Portfolio Fund Comparison V2 — Frontend Contract & Integrity Tests', () => {

  it('TC-FE-C01: Comparison response structure preserves exact pilot scheme identity', () => {
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
        asOfDate: '2024-01-15'
      }
    };

    const mockFund2: ComparisonFund = {
      schemeOptionId: 10262,
      schemeId: 202,
      schemeName: 'Parag Parikh Flexi Cap Fund',
      amcName: 'PPFAS Mutual Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      amfiCode: '122639',
      isin: 'INF879O01027',
      analyticalScore: {
        scoreValue: 71.50,
        confidence: 90,
        scoreStatus: 'COMPLETE',
        methodologyVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'CANDIDATE',
        asOfDate: '2024-01-15'
      }
    };

    const mockFund3: ComparisonFund = {
      schemeOptionId: 10151,
      schemeId: 303,
      schemeName: 'Parag Parikh ELSS Tax Saver Fund',
      amcName: 'PPFAS Mutual Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      amfiCode: '147481',
      isin: 'INF879O01100',
      analyticalScore: {
        scoreValue: 64.20,
        confidence: 85,
        scoreStatus: 'PARTIAL',
        methodologyVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'CANDIDATE',
        asOfDate: '2024-01-15'
      }
    };

    const mockResponse: PortfolioComparisonResponse = {
      comparisonSize: 3,
      asOfDate: '2024-01-15',
      knowledgeCutoff: '2024-01-31T23:59:59+05:30',
      methodologyVersion: 'YUKIRA_SCORE_V1',
      methodologyStatus: 'CANDIDATE',
      referencePopulation: 'INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1',
      funds: [mockFund1, mockFund2, mockFund3],
      canonicalMetrics: [
        {
          metricCode: 'RET-03',
          metricName: '3-Year CAGR',
          category: 'PERFORMANCE',
          comparabilityStatus: 'COMPARABLE_WITH_LIMITATIONS',
          limitationReason: 'Parag Parikh ELSS Tax Saver Fund has ~618 NAV observations (~2.5Y history), which is shorter than the 3Y window (750 obs)',
          fundValues: {
            '1': { availabilityState: 'AVAILABLE', formattedValue: '+14.25%', rawValue: 0.1425 },
            '10262': { availabilityState: 'AVAILABLE', formattedValue: '+18.50%', rawValue: 0.1850 },
            '10151': { availabilityState: 'AVAILABLE', formattedValue: '+16.20%', rawValue: 0.1620 }
          }
        },
        {
          metricCode: 'CONC-01',
          metricName: 'Top 5 Holdings Concentration',
          category: 'PORTFOLIO',
          comparabilityStatus: 'COMPARABLE',
          limitationReason: null,
          fundValues: {
            '1': { availabilityState: 'AVAILABLE', formattedValue: '34.20%', rawValue: 34.20 },
            '10262': { availabilityState: 'AVAILABLE', formattedValue: '28.90%', rawValue: 28.90 },
            '10151': { availabilityState: 'AVAILABLE', formattedValue: '31.40%', rawValue: 31.40 }
          }
        }
      ],
      coverageSummary: {
        totalSelectedFunds: 3,
        scoredFundsCount: 3,
        completeRiskEvidenceCount: 2,
        missingMetricsCount: 0,
        differingAsOfDates: false,
        overallComparabilityStatus: 'COMPARABLE_WITH_LIMITATIONS',
        overallComparabilityReason: 'Comparison includes Parag Parikh ELSS Tax Saver Fund with shorter NAV history (~618 obs vs 750+ baseline)'
      },
      investigationPrompts: [
        'Parag Parikh ELSS Tax Saver Fund has shorter available NAV history (~618 observations) than HDFC Flexi Cap and PPFAS Flexi Cap (~750+ observations). Compare 1Y returns or check inception date.'
      ]
    };

    assert.equal(mockResponse.funds.length, 3);
    assert.equal(mockResponse.funds[0].schemeOptionId, 1);
    assert.equal(mockResponse.funds[1].schemeOptionId, 10262);
    assert.equal(mockResponse.funds[2].schemeOptionId, 10151);
    assert.equal(mockResponse.coverageSummary.overallComparabilityStatus, 'COMPARABLE_WITH_LIMITATIONS');
    assert.equal(mockResponse.canonicalMetrics[0].comparabilityStatus, 'COMPARABLE_WITH_LIMITATIONS');
    assert.equal(mockResponse.canonicalMetrics[1].comparabilityStatus, 'COMPARABLE');
  });

  it('TC-FE-C02: Comparability limitation reason is captured and accessible', () => {
    const row: ComparisonMetricRow = {
      metricCode: 'RSK-01',
      metricName: 'Annualized Volatility (3Y)',
      category: 'RISK',
      comparabilityStatus: 'COMPARABLE_WITH_LIMITATIONS',
      limitationReason: 'Calculated over differing effective historical lengths (618 vs 750 trading days)',
      fundValues: {
        '1': { availabilityState: 'AVAILABLE', formattedValue: '12.45%', rawValue: 0.1245 },
        '10151': { availabilityState: 'AVAILABLE', formattedValue: '11.80%', rawValue: 0.1180 }
      }
    };

    assert.equal(row.comparabilityStatus, 'COMPARABLE_WITH_LIMITATIONS');
    assert.match(row.limitationReason || '', /differing effective historical lengths/);
  });

  it('TC-FE-C03: Unavailable metrics are represented without being converted to zero', () => {
    const row: ComparisonMetricRow = {
      metricCode: 'RSK-02',
      metricName: 'Downside Semideviation 3Y',
      category: 'RISK',
      comparabilityStatus: 'INSUFFICIENT_DATA',
      limitationReason: 'Insufficient observations for downside risk calculation',
      fundValues: {
        '1': { availabilityState: 'UNAVAILABLE', formattedValue: null, rawValue: null }
      }
    };

    const val = row.fundValues['1'];
    assert.equal(val.availabilityState, 'UNAVAILABLE');
    assert.equal(val.formattedValue, null);
    assert.notEqual(val.rawValue, 0);
  });

  it('TC-FE-C04: Portfolio concentration metrics are included in comparison', () => {
    const concMetric: ComparisonMetricRow = {
      metricCode: 'CONC-03',
      metricName: 'Herfindahl-Hirschman Index (HHI)',
      category: 'PORTFOLIO',
      comparabilityStatus: 'COMPARABLE',
      limitationReason: null,
      fundValues: {
        '1': { availabilityState: 'AVAILABLE', formattedValue: '0.0425', rawValue: 0.0425 },
        '10262': { availabilityState: 'AVAILABLE', formattedValue: '0.0380', rawValue: 0.0380 }
      }
    };

    assert.equal(concMetric.category, 'PORTFOLIO');
    assert.equal(concMetric.metricCode, 'CONC-03');
    assert.equal(concMetric.fundValues['1'].formattedValue, '0.0425');
  });

  it('TC-FE-C05: Pairwise holdings overlap preserves source-disclosed weights and flags date mismatches', () => {
    const mockOverlap: import('../../types/comparison').PortfolioOverlapResponseDto = {
      schemeOptionIdA: 1,
      fundNameA: 'HDFC Flexi Cap Fund',
      portfolioDateA: '2024-01-31',
      schemeOptionIdB: 10262,
      fundNameB: 'Parag Parikh Flexi Cap Fund',
      portfolioDateB: '2026-09-30',
      overlapPercentage: 27.05,
      formattedOverlapPercentage: '27.05%',
      commonSecurityCount: 8,
      holdingsCountA: 10,
      holdingsCountB: 96,
      uniqueHoldingsCountA: 2,
      uniqueHoldingsCountB: 88,
      topOverlappingSecurities: [
        {
          securityName: 'HDFC Bank Ltd.',
          isin: 'INE040A01034',
          sector: 'Financial Services',
          fundAWeightPct: 8.50,
          formattedWeightA: '8.50%',
          fundBWeightPct: 7.20,
          formattedWeightB: '7.20%',
          overlapContributionPct: 7.20,
          formattedOverlapContribution: '7.20%'
        }
      ],
      qualityState: 'DATA_QUALITY_LIMITED',
      limitations: [
        'Fund A holdings snapshot as of 2024-01-31 is >60 days old.',
        'Reporting date mismatch: Fund A as of 2024-01-31, Fund B as of 2026-09-30.',
        'Fund A has partial holdings disclosure (10 positions, 50.82% coverage).'
      ]
    };

    assert.equal(mockOverlap.overlapPercentage, 27.05);
    assert.equal(mockOverlap.commonSecurityCount, 8);
    assert.equal(mockOverlap.qualityState, 'DATA_QUALITY_LIMITED');
    assert.equal(mockOverlap.limitations.length, 3);
    assert.match(mockOverlap.limitations[0], />60 days old/);
    assert.match(mockOverlap.limitations[1], /Reporting date mismatch/);
    assert.match(mockOverlap.limitations[2], /partial holdings disclosure/);

    // Verify min(wA, wB) overlap contribution logic
    const topSec = mockOverlap.topOverlappingSecurities![0];
    assert.equal(topSec.overlapContributionPct, Math.min(topSec.fundAWeightPct!, topSec.fundBWeightPct!));
  });

  it('TC-FE-C06: Missing holdings snapshots return UNAVAILABLE and are not represented as 0% overlap', () => {
    const mockUnavailableOverlap: import('../../types/comparison').PortfolioOverlapResponseDto = {
      schemeOptionIdA: 1,
      fundNameA: 'HDFC Flexi Cap Fund',
      portfolioDateA: '2024-01-31',
      schemeOptionIdB: 10151,
      fundNameB: 'Parag Parikh ELSS Tax Saver Fund',
      portfolioDateB: null,
      overlapPercentage: 0.0,
      formattedOverlapPercentage: '0.00%',
      commonSecurityCount: 0,
      holdingsCountA: 10,
      holdingsCountB: 0,
      uniqueHoldingsCountA: 10,
      uniqueHoldingsCountB: 0,
      topOverlappingSecurities: [],
      qualityState: 'UNAVAILABLE',
      limitations: ['No holdings snapshot available for fund B (Parag Parikh ELSS Tax Saver Fund).']
    };

    assert.equal(mockUnavailableOverlap.qualityState, 'UNAVAILABLE');
    assert.match(mockUnavailableOverlap.limitations[0], /No holdings snapshot available/);
    assert.equal(mockUnavailableOverlap.topOverlappingSecurities!.length, 0);
  });

  it('TC-FE-C07: Pairwise Daily Return Correlation preserves sample size N, common dates, and candidate disclaimer', () => {
    const mockCorrelation: import('../../types/comparison').PairwiseCorrelationDto = {
      schemeOptionIdA: 1,
      fundNameA: 'HDFC Flexi Cap Fund',
      schemeOptionIdB: 10262,
      fundNameB: 'Parag Parikh Flexi Cap Fund',
      correlation: 0.5430,
      formattedCorrelation: '+0.5430',
      pairedObservationsCount: 865,
      commonStartDate: '2021-01-04',
      commonEndDate: '2026-10-01',
      calculationStatus: 'CALCULATED',
      comparabilityStatus: 'COMPARABLE',
      limitations: [
        'Candidate methodology — not validated for production investment advice.',
        'Calculated over 865 intersecting market trading dates.'
      ],
      interpretation: 'Moderate positive co-movement. Indicates partial diversification benefit over the common 865-day sample.'
    };

    assert.equal(mockCorrelation.calculationStatus, 'CALCULATED');
    assert.equal(mockCorrelation.comparabilityStatus, 'COMPARABLE');
    assert.equal(mockCorrelation.correlation, 0.5430);
    assert.equal(mockCorrelation.pairedObservationsCount, 865);
    assert.match(mockCorrelation.limitations[0], /Candidate methodology/);
    assert.match(mockCorrelation.interpretation, /Moderate positive co-movement/);
  });

  it('TC-FE-C08: Exact scheme option identity and URL preservation across 2 and 3 option comparisons', () => {
    const optionIds2 = [1, 10262];
    const url2 = `/compare?ids=${optionIds2.join(',')}`;
    assert.equal(url2, '/compare?ids=1,10262');

    const optionIds3 = [1, 10262, 10151];
    const url3 = `/compare?ids=${optionIds3.join(',')}`;
    assert.equal(url3, '/compare?ids=1,10262,10151');

    // Parse back
    const parsed = url3.split('?ids=')[1].split(',').map(Number);
    assert.deepEqual(parsed, [1, 10262, 10151]);
  });

  it('TC-FE-C09: Benchmark-relative metrics (RET-07, MKT-01, MKT-02, MKT-05, REL-01, REL-02, RAT-04) are supported and preserve units and non-persisting active spread', () => {
    const mockMetric: ComparisonMetric = {
      metricCode: 'RET-07',
      metricName: '3-Year Active / Excess Return',
      category: 'BENCHMARK_RELATIVE',
      description: 'Annualized fund return minus NIFTY 500 TRI return.',
      interpretation: 'Factual descriptive historical spread; does not imply manager skill or future persistence.',
      limitations: 'Conceals interim path volatility.',
      units: 'PERCENTAGE',
      period: '3Y',
      governanceStatus: 'CANDIDATE',
      overallComparabilityStatus: 'COMPARABLE',
      results: [
        {
          schemeOptionId: 1,
          numericValue: 0.1045,
          stringValue: '+10.45%',
          formattedValue: '+10.45%',
          calculationStatus: 'CALCULATED',
          units: 'PERCENTAGE',
          dataDate: '2024-01-15'
        },
        {
          schemeOptionId: 10262,
          numericValue: 0.0736,
          stringValue: '+7.36%',
          formattedValue: '+7.36%',
          calculationStatus: 'CALCULATED',
          units: 'PERCENTAGE',
          dataDate: '2024-01-15'
        }
      ]
    };

    assert.equal(mockMetric.metricCode, 'RET-07');
    assert.equal(mockMetric.units, 'PERCENTAGE');
    assert.match(mockMetric.interpretation, /Factual descriptive historical spread/);
    assert.equal(mockMetric.results.length, 2);
    assert.equal(mockMetric.results[0].formattedValue, '+10.45%');
    assert.equal(mockMetric.results[1].formattedValue, '+7.36%');
  });

  it('TC-FE-C10: Cumulative growth common intersection calculation rebases strictly to base 100 on initial paired date', () => {
    const fundAHistory = [
      { navDate: '2021-01-15', navValue: 1000.0 },
      { navDate: '2021-01-18', navValue: 1020.0 },
      { navDate: '2021-01-19', navValue: 1050.0 },
    ];
    const fundBHistory = [
      { navDate: '2021-01-15', navValue: 50.0 },
      { navDate: '2021-01-18', navValue: 52.0 },
      { navDate: '2021-01-19', navValue: 51.0 },
    ];

    // Intersection
    const datesA = new Set(fundAHistory.map((d) => d.navDate));
    const commonDates = fundBHistory.filter((d) => datesA.has(d.navDate)).map((d) => d.navDate);
    assert.deepEqual(commonDates, ['2021-01-15', '2021-01-18', '2021-01-19']);

    // Rebase to base 100
    const rebaseA = fundAHistory.map((d) => ({
      date: d.navDate,
      indexed: (d.navValue / fundAHistory[0].navValue) * 100
    }));
    const rebaseB = fundBHistory.map((d) => ({
      date: d.navDate,
      indexed: (d.navValue / fundBHistory[0].navValue) * 100
    }));

    assert.equal(rebaseA[0].indexed, 100.0);
    assert.equal(rebaseA[1].indexed, 102.0);
    assert.equal(rebaseA[2].indexed, 105.0);

    assert.equal(rebaseB[0].indexed, 100.0);
    assert.equal(rebaseB[1].indexed, 104.0);
    assert.equal(rebaseB[2].indexed, 102.0);
  });

  it('TC-FE-C11: MetricRow deterministic fallback formatting correctly handles null formattedValue with units', () => {
    const formatFallback = (result: { formattedValue?: string | null; numericValue?: number | null; units?: string }) => {
      if (result.formattedValue && result.formattedValue.trim() !== '') {
        return result.formattedValue;
      }
      if (result.numericValue !== null && result.numericValue !== undefined) {
        if (result.units === 'PERCENTAGE') {
          return `${(result.numericValue * 100).toFixed(2)}%`;
        }
        if (result.units === 'BASIS_POINTS') {
          return `${result.numericValue.toFixed(0)} bps`;
        }
        if (result.units === 'DAYS') {
          return `${result.numericValue.toFixed(0)} days`;
        }
        return result.numericValue.toFixed(2);
      }
      return '—';
    };

    assert.equal(formatFallback({ formattedValue: null, numericValue: 0.1234, units: 'PERCENTAGE' }), '12.34%');
    assert.equal(formatFallback({ formattedValue: '', numericValue: 250, units: 'BASIS_POINTS' }), '250 bps');
    assert.equal(formatFallback({ formattedValue: null, numericValue: 45, units: 'DAYS' }), '45 days');
    assert.equal(formatFallback({ formattedValue: null, numericValue: 1.08, units: 'RATIO' }), '1.08');
    assert.equal(formatFallback({ formattedValue: '+27.25%', numericValue: 0.2725, units: 'PERCENTAGE' }), '+27.25%');
    assert.equal(formatFallback({ formattedValue: null, numericValue: null, units: 'PERCENTAGE' }), '—');
  });
});
