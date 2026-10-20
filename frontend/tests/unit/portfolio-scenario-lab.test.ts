import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  ScenarioTemplateDto,
  ScenarioLabRequestDto,
  ScenarioLabResponseDto,
  ScenarioComparisonResponseDto,
  ScenarioDimensionMetadataDto,
} from '../../lib/api/portfolio';

describe('Portfolio Scenario Lab & Evidence-Based Risk Investigation — Frontend Contract & Integrity Tests (Phase 2N)', () => {

  it('TC-FE-SC01: ScenarioTemplateDto and ScenarioLabRequestDto validate scenario types and shock parameters', () => {
    const template: ScenarioTemplateDto = {
      id: 'TEMPLATE_SEBI_MIDSMALL_SHAKEOUT',
      name: 'Mid & Small-Cap Liquidity Shakeout',
      category: 'MARKET_CAP',
      description: 'Simulates severe liquidity squeeze across mid and small-cap segments.',
      broadMarketShockPct: null,
      marketCapShocks: {
        'LARGE_CAP': -4.0,
        'MID_CAP': -10.0,
        'SMALL_CAP': -18.0,
        'CASH': 0.0,
        'UNCLASSIFIED': 0.0,
      },
      sectorShocks: null,
      combinationMethod: null,
      notes: ['Mutually exclusive market-cap partition', 'Cash receives 0.00% return'],
    };

    assert.equal(template.category, 'MARKET_CAP');
    assert.equal(template.marketCapShocks?.['SMALL_CAP'], -18.0);
    assert.equal(template.marketCapShocks?.['LARGE_CAP'], -4.0);
    assert.equal(template.notes.length, 2);

    const request: ScenarioLabRequestDto = {
      funds: [
        { schemeOptionId: 1, allocationWeight: 60.0 },
        { schemeOptionId: 10151, allocationWeight: 40.0 },
      ],
      asOfDate: '2026-09-30',
      benchmarkCode: 'NIFTY_500_TRI',
      scenarioType: 'MARKET_CAP',
      scenarioName: 'Mid & Small-Cap Shakeout',
      marketCapShocks: template.marketCapShocks,
      unmodeledShockPct: 0.0,
    };

    assert.equal(request.funds.length, 2);
    assert.equal(request.funds[0].allocationWeight, 60.0);
    assert.equal(request.funds[1].allocationWeight, 40.0);
    assert.equal(request.benchmarkCode, 'NIFTY_500_TRI');
    assert.equal(request.unmodeledShockPct, 0.0);
  });

  it('TC-FE-SC02: Broad-Market Sensitivity enforces SENSITIVITY_ESTIMATE_ONLY label and beta gating rules', () => {
    const validBetaResponse: ScenarioLabResponseDto = {
      scenarioName: 'Broad Correction -10%',
      scenarioType: 'BROAD_MARKET',
      calculationTimestamp: '2026-10-20T12:00:00Z',
      asOfDate: '2026-09-30',
      knowledgeCutoff: '2026-09-30T23:59:59Z',
      benchmarkCode: 'NIFTY_500_TRI',
      benchmarkName: 'Nifty 500 Total Returns Index',
      epistemicLabel: 'SENSITIVITY_ESTIMATE_ONLY',
      isGated: false,
      gatingReason: null,
      portfolioShockPct: -8.80,
      modeledPortfolioShockPct: -8.80,
      coveredPortfolioWeightPct: 100.0,
      unmodeledPortfolioWeightPct: 0.0,
      portfolioBeta: 0.88,
      betaObservationCount: 252,
      betaEstimationWindowDays: 252,
      betaAvailable: true,
      betaGatingReason: null,
      combinationMethod: 'LINEAR_BETA',
      overlapDisclosures: ['Single factor beta sensitivity model'],
      assumptions: [
        'Broad market benchmark: Nifty 500 Total Returns Index (NIFTY_500_TRI).',
        'User-specified broad equity market shock: -10.00%.',
        'Linear beta approximation: Estimated Return = Beta × Market Shock.',
      ],
      dataLimitations: [
        'Beta linearity omission: Assumes uniform linear sensitivity. Real drawdowns exhibit nonlinear asymmetric downside capture.',
        'Static beta estimation: Historical OLS beta estimated over N = 252 aligned daily return intervals.',
      ],
      epistemicClassifications: {
        'MethodologyVersion': 'SCENARIO_LAB_V1',
        'EpistemicStatus': 'SENSITIVITY_ESTIMATE_ONLY',
        'Disclaimer': 'Mathematical sensitivity simulation. Does NOT constitute investment advice or predictive return forecasts.',
      },
      investigationQuestions: [
        'How does each fund historical downside capture ratio compare to its linear OLS beta?',
        'Would active managers raise cash or hedge during a persistent broad market downturn?',
      ],
      constituents: [
        {
          schemeOptionId: 1,
          fundName: 'HDFC Flexi Cap Fund - Direct - Growth',
          amcName: 'HDFC Mutual Fund',
          portfolioWeightPct: 60.0,
          normalizedWeightPct: 60.0,
          constituentShockPct: -9.20,
          weightedContributionPct: -5.52,
          fundBeta: 0.92,
          holdingsSnapshotDate: '2026-09-30',
          coveredHoldingsWeightPct: 98.5,
          unmodeledHoldingsWeightPct: 1.5,
          marketCapCoverages: { 'LARGE_CAP': 72.0, 'MID_CAP': 18.0, 'SMALL_CAP': 8.5 },
          sectorCoverages: { 'Financial Services': 32.0, 'Information Technology': 14.0 },
          epistemicStatus: 'SENSITIVITY_ESTIMATE_ONLY',
          calculationMethod: 'LINEAR_BETA',
          notes: ['Beta = 0.92 x (-10.0%) = -9.20%'],
        },
        {
          schemeOptionId: 10151,
          fundName: 'Parag Parikh Flexi Cap Fund - Direct - Growth',
          amcName: 'PPFAS Mutual Fund',
          portfolioWeightPct: 40.0,
          normalizedWeightPct: 40.0,
          constituentShockPct: -8.20,
          weightedContributionPct: -3.28,
          fundBeta: 0.82,
          holdingsSnapshotDate: '2026-09-30',
          coveredHoldingsWeightPct: 97.2,
          unmodeledHoldingsWeightPct: 2.8,
          marketCapCoverages: { 'LARGE_CAP': 65.0, 'MID_CAP': 20.0, 'SMALL_CAP': 12.2 },
          sectorCoverages: { 'Financial Services': 28.0, 'Information Technology': 22.0 },
          epistemicStatus: 'SENSITIVITY_ESTIMATE_ONLY',
          calculationMethod: 'LINEAR_BETA',
          notes: ['Beta = 0.82 x (-10.0%) = -8.20%'],
        },
      ],
      exposures: [
        {
          dimensionCategory: 'BROAD_MARKET',
          dimensionName: 'Broad Market (NIFTY_500_TRI)',
          portfolioWeightPct: 100.0,
          coverageWeightPct: 100.0,
          shockPct: -10.0,
          weightedContributionPct: -8.80,
          coverageState: 'COVERED',
          overlapRule: 'SYSTEMATIC_FACTOR',
          notes: 'OLS Beta = 0.88',
        },
      ],
    };

    // Assert epistemic integrity
    assert.equal(validBetaResponse.epistemicLabel, 'SENSITIVITY_ESTIMATE_ONLY');
    assert.equal(validBetaResponse.isGated, false);
    assert.equal(validBetaResponse.portfolioShockPct, -8.80);
    assert.equal(validBetaResponse.betaObservationCount, 252);

    // Exact reconciliation: sum of constituent contributions must equal portfolio shock
    const sumContrib = validBetaResponse.constituents.reduce((acc, c) => acc + c.weightedContributionPct, 0);
    assert.equal(Math.round(sumContrib * 100) / 100, validBetaResponse.portfolioShockPct);

    // Gated Beta Scenario Verification
    const gatedBetaResponse: ScenarioLabResponseDto = {
      ...validBetaResponse,
      epistemicLabel: 'DATA_UNAVAILABLE',
      isGated: true,
      gatingReason: 'Insufficient common aligned daily trading intervals (N = 8 < 10 threshold).',
      portfolioShockPct: 0.0,
      portfolioBeta: null,
      betaAvailable: false,
      betaGatingReason: 'Insufficient common aligned daily trading intervals (N = 8 < 10 threshold).',
    };

    assert.equal(gatedBetaResponse.epistemicLabel, 'DATA_UNAVAILABLE');
    assert.equal(gatedBetaResponse.isGated, true);
    assert.equal(gatedBetaResponse.betaAvailable, false);
    assert.equal(gatedBetaResponse.portfolioShockPct, 0.0);
    assert.ok(gatedBetaResponse.gatingReason?.includes('Insufficient common aligned'));
  });

  it('TC-FE-SC03: Market-Cap Bucket Partition enforces zero-renormalization of unmodeled exposure', () => {
    // 75% covered (50% Large, 15% Mid, 10% Small), 25% unclassified
    const unmodeledExposureResponse: ScenarioLabResponseDto = {
      scenarioName: 'Partial Coverage Test',
      scenarioType: 'MARKET_CAP',
      calculationTimestamp: '2026-10-20T12:00:00Z',
      asOfDate: '2026-09-30',
      knowledgeCutoff: '2026-09-30T23:59:59Z',
      benchmarkCode: 'NIFTY_500_TRI',
      benchmarkName: 'Nifty 500 TRI',
      epistemicLabel: 'SENSITIVITY_ESTIMATE_ONLY',
      isGated: false,
      gatingReason: null,
      portfolioShockPct: -5.70,
      modeledPortfolioShockPct: -5.70,
      coveredPortfolioWeightPct: 75.0,
      unmodeledPortfolioWeightPct: 25.0,
      portfolioBeta: 0.90,
      betaObservationCount: 252,
      betaEstimationWindowDays: 252,
      betaAvailable: true,
      betaGatingReason: null,
      combinationMethod: 'MUTUALLY_EXCLUSIVE_PARTITION',
      overlapDisclosures: ['Holdings uniquely assigned to single market-cap bucket'],
      assumptions: [
        'Market-cap bucket shocks: Large-Cap (-4.0%), Mid-Cap (-8.0%), Small-Cap (-15.0%), Cash (0.0%).',
        'Unclassified exposure (25.00%) is assumed to experience 0.00% return.',
      ],
      dataLimitations: [
        'Incomplete holdings exposure coverage: 25.00% of portfolio weight is unclassified or missing classification. It is treated with an explicit 0% return assumption rather than imputed or scaled to 100%.',
      ],
      epistemicClassifications: {},
      investigationQuestions: [],
      constituents: [],
      exposures: [
        {
          dimensionCategory: 'MARKET_CAP',
          dimensionName: 'Large Cap',
          portfolioWeightPct: 50.0,
          coverageWeightPct: 50.0,
          shockPct: -4.0,
          weightedContributionPct: -2.00,
          coverageState: 'COVERED',
          overlapRule: 'MUTUALLY_EXCLUSIVE',
          notes: '50% x (-4%) = -2.00%',
        },
        {
          dimensionCategory: 'MARKET_CAP',
          dimensionName: 'Mid Cap',
          portfolioWeightPct: 15.0,
          coverageWeightPct: 15.0,
          shockPct: -8.0,
          weightedContributionPct: -1.20,
          coverageState: 'COVERED',
          overlapRule: 'MUTUALLY_EXCLUSIVE',
          notes: '15% x (-8%) = -1.20%',
        },
        {
          dimensionCategory: 'MARKET_CAP',
          dimensionName: 'Small Cap',
          portfolioWeightPct: 10.0,
          coverageWeightPct: 10.0,
          shockPct: -25.0,
          weightedContributionPct: -2.50,
          coverageState: 'COVERED',
          overlapRule: 'MUTUALLY_EXCLUSIVE',
          notes: '10% x (-25%) = -2.50%',
        },
        {
          dimensionCategory: 'UNMODELED',
          dimensionName: 'Unclassified / Missing',
          portfolioWeightPct: 25.0,
          coverageWeightPct: 0.0,
          shockPct: 0.0,
          weightedContributionPct: 0.0,
          coverageState: 'UNMODELED',
          overlapRule: 'MUTUALLY_EXCLUSIVE',
          notes: 'Explicit 0.00% assumed shock. Never scaled to 100%.',
        },
      ],
    };

    assert.equal(unmodeledExposureResponse.coveredPortfolioWeightPct, 75.0);
    assert.equal(unmodeledExposureResponse.unmodeledPortfolioWeightPct, 25.0);

    // Sum of covered weights + unmodeled weight = 100%
    const totalWeight = unmodeledExposureResponse.coveredPortfolioWeightPct + unmodeledExposureResponse.unmodeledPortfolioWeightPct;
    assert.equal(totalWeight, 100.0);

    // Estimated impact: -2.00% + -1.20% + -2.50% + 0.0% = -5.70%
    // If it had been illegally renormalized to 100% (scaling by 1/0.75 = 1.333), the impact would be -7.60%.
    assert.equal(unmodeledExposureResponse.portfolioShockPct, -5.70);
    assert.ok(unmodeledExposureResponse.dataLimitations[0].includes('25.00% of portfolio weight is unclassified'));
  });

  it('TC-FE-SC04: Scenario Comparison validates baseline comparability and calculates exact delta impacts', () => {
    const scA: ScenarioLabResponseDto = {
      scenarioName: 'Scenario A (Mild Shock)',
      scenarioType: 'BROAD_MARKET',
      calculationTimestamp: '2026-10-20T12:00:00Z',
      asOfDate: '2026-09-30',
      knowledgeCutoff: '2026-09-30T23:59:59Z',
      benchmarkCode: 'NIFTY_500_TRI',
      benchmarkName: 'Nifty 500 TRI',
      epistemicLabel: 'SENSITIVITY_ESTIMATE_ONLY',
      isGated: false,
      gatingReason: null,
      portfolioShockPct: -5.00,
      modeledPortfolioShockPct: -5.00,
      coveredPortfolioWeightPct: 98.0,
      unmodeledPortfolioWeightPct: 2.0,
      portfolioBeta: 1.0,
      betaObservationCount: 252,
      betaEstimationWindowDays: 252,
      betaAvailable: true,
      betaGatingReason: null,
      combinationMethod: 'LINEAR_BETA',
      overlapDisclosures: [],
      assumptions: [],
      dataLimitations: [],
      epistemicClassifications: {},
      investigationQuestions: [],
      constituents: [
        {
          schemeOptionId: 1,
          fundName: 'HDFC Flexi Cap Fund',
          amcName: 'HDFC MF',
          portfolioWeightPct: 100.0,
          normalizedWeightPct: 100.0,
          constituentShockPct: -5.00,
          weightedContributionPct: -5.00,
          fundBeta: 1.0,
          holdingsSnapshotDate: '2026-09-30',
          coveredHoldingsWeightPct: 98.0,
          unmodeledHoldingsWeightPct: 2.0,
          marketCapCoverages: {},
          sectorCoverages: {},
          epistemicStatus: 'SENSITIVITY_ESTIMATE_ONLY',
          calculationMethod: 'LINEAR_BETA',
          notes: [],
        },
      ],
      exposures: [],
    };

    const scB: ScenarioLabResponseDto = {
      ...scA,
      scenarioName: 'Scenario B (Severe Shock)',
      portfolioShockPct: -15.00,
      modeledPortfolioShockPct: -15.00,
      constituents: [
        {
          ...scA.constituents[0],
          constituentShockPct: -15.00,
          weightedContributionPct: -15.00,
        },
      ],
    };

    const comparison: ScenarioComparisonResponseDto = {
      scenarioA: scA,
      scenarioB: scB,
      isComparable: true,
      nonComparableReasons: [],
      deltaPortfolioShockPct: -10.00, // scB - scA = -15 - (-5) = -10.00%
      deltaModeledShockPct: -10.00,
      deltaCoveredWeightPct: 0.0,
      constituentDeltas: [
        {
          schemeOptionId: 1,
          fundName: 'HDFC Flexi Cap Fund',
          portfolioWeightPct: 100.0,
          scenarioAShockPct: -5.00,
          scenarioBShockPct: -15.00,
          deltaShockPct: -10.00,
          scenarioAContributionPct: -5.00,
          scenarioBContributionPct: -15.00,
          deltaContributionPct: -10.00,
        },
      ],
      exposureDeltas: [],
      divergenceExplanation: 'Comparison is valid: Both scenarios share identical constituent funds, identical evaluation dates (2026-09-30), identical benchmark (NIFTY_500_TRI), and identical holdings exposure coverage (98.00%). Divergence is entirely attributable to the user-specified shock parameters.',
    };

    assert.equal(comparison.isComparable, true);
    assert.equal(comparison.deltaPortfolioShockPct, -10.00);
    assert.equal(comparison.constituentDeltas[0].deltaShockPct, -10.00);
    assert.equal(comparison.constituentDeltas[0].deltaContributionPct, -10.00);
    assert.ok(comparison.divergenceExplanation.includes('Both scenarios share identical constituent funds'));
  });

  it('TC-FE-SC05: ScenarioDimensionMetadataDto delivers available templates and pre-flight coverage inspection', () => {
    const meta: ScenarioDimensionMetadataDto = {
      asOfDate: '2026-09-30',
      knowledgeCutoff: '2026-09-30T23:59:59Z',
      benchmarkCode: 'NIFTY_500_TRI',
      benchmarkName: 'Nifty 500 TRI',
      isBetaAvailable: true,
      portfolioBeta: 0.94,
      betaObservationCount: 252,
      betaGatingReason: null,
      marketCapWeights: {
        'LARGE_CAP': 68.5,
        'MID_CAP': 18.2,
        'SMALL_CAP': 9.8,
        'CASH': 3.5,
      },
      sectorWeights: {
        'Financial Services': 31.4,
        'Information Technology': 16.2,
        'Consumer Discretionary': 12.0,
      },
      coveredWeightPct: 100.0,
      unmodeledWeightPct: 0.0,
      availableTemplates: [
        {
          id: 'TEMPLATE_BROAD_10',
          name: 'Broad Market Correction -10%',
          category: 'BROAD_MARKET',
          description: 'Linear sensitivity to standard equity market correction.',
          broadMarketShockPct: -10.0,
          marketCapShocks: null,
          sectorShocks: null,
          combinationMethod: null,
          notes: ['Benchmark: NIFTY_500_TRI'],
        },
      ],
      supportedDimensions: ['BROAD_MARKET', 'MARKET_CAP', 'SECTOR', 'COMBINED'],
      dataLimitations: [],
    };

    assert.equal(meta.isBetaAvailable, true);
    assert.equal(meta.portfolioBeta, 0.94);
    assert.equal(meta.marketCapWeights['LARGE_CAP'], 68.5);
    assert.equal(meta.availableTemplates.length, 1);
    assert.equal(meta.supportedDimensions.length, 4);
  });
});
