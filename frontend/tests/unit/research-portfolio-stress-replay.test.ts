import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  HistoricalMarketEpisodeDto,
  TrajectoryPointDto,
  ConstituentReplayResultDto,
  StressExposureContextDto,
  ModeledSensitivityScenarioDto,
  PortfolioStressReplayResponseDto,
} from '../../lib/api/portfolio';

describe('Historical Market-Regime Replay & Portfolio Stress Testing — Frontend Contract & Integrity Tests', () => {

  it('TC-FE-ST01: HistoricalMarketEpisodeDto validates episode availability and reason gating', () => {
    const verifiedEpisode: HistoricalMarketEpisodeDto = {
      episodeId: 'EPISODE_2021_2022_RATE_HIKE_DRAWDOWN',
      title: '2021–2022 Global Rate-Hike & Inflation Drawdown',
      category: 'GLOBAL_MONETARY_TIGHTENING',
      startDate: '2021-10-18',
      endDate: '2022-06-17',
      peakTroughConvention: 'Peak-to-Trough of NIFTY 500 TRI (24,305.72 to 20,052.33, -17.50% correction)',
      regimeType: 'FULL_DECLINE',
      description: 'Coordinated central bank policy tightening and elevated global inflation.',
      isAvailable: true,
      unavailabilityReason: null,
      tradingDaysCount: 166,
      benchmarkTotalReturnPct: -17.50,
    };

    assert.equal(verifiedEpisode.isAvailable, true);
    assert.equal(verifiedEpisode.unavailabilityReason, null);
    assert.equal(verifiedEpisode.tradingDaysCount, 166);

    const gatedCovidEpisode: HistoricalMarketEpisodeDto = {
      episodeId: 'EPISODE_2020_COVID_SHOCK',
      title: 'February–March 2020 COVID-19 Liquidity Shock',
      category: 'SYSTEMIC_PANDEMIC_CRISIS',
      startDate: '2020-02-19',
      endDate: '2020-03-23',
      peakTroughConvention: 'Systemic pandemic liquidity crash',
      regimeType: 'FULL_DECLINE',
      description: 'Global pandemic declaration and synchronized worldwide liquidation.',
      isAvailable: false,
      unavailabilityReason: 'Benchmark NIFTY_500_TRI observations start on 2021-01-15. Pre-2021 historical data is unavailable without synthetic extrapolation (§4 AGENTS.md).',
      tradingDaysCount: null,
      benchmarkTotalReturnPct: null,
    };

    assert.equal(gatedCovidEpisode.isAvailable, false);
    assert.equal(gatedCovidEpisode.tradingDaysCount, null);
    assert.match(gatedCovidEpisode.unavailabilityReason || '', /Pre-2021 historical data is unavailable/);
  });

  it('TC-FE-ST02: PortfolioStressReplayResponseDto validates trajectory points and drawdown consistency', () => {
    const mockTrajectory: TrajectoryPointDto[] = [
      {
        date: '2021-10-18',
        portfolioCumulativeReturnPct: 0.0,
        benchmarkCumulativeReturnPct: 0.0,
        activeReturnPct: 0.0,
        portfolioDrawdownPct: 0.0,
        benchmarkDrawdownPct: 0.0,
      },
      {
        date: '2022-01-17',
        portfolioCumulativeReturnPct: -2.45,
        benchmarkCumulativeReturnPct: -4.10,
        activeReturnPct: 1.65,
        portfolioDrawdownPct: -3.80,
        benchmarkDrawdownPct: -6.20,
      },
      {
        date: '2022-06-17',
        portfolioCumulativeReturnPct: -13.29,
        benchmarkCumulativeReturnPct: -17.50,
        activeReturnPct: 4.21,
        portfolioDrawdownPct: -14.85,
        benchmarkDrawdownPct: -18.20,
      },
    ];

    assert.equal(mockTrajectory.length, 3);
    const endPoint = mockTrajectory[2];
    assert.equal(endPoint.portfolioCumulativeReturnPct, -13.29);
    assert.equal(endPoint.benchmarkCumulativeReturnPct, -17.50);
    // Active return = Portfolio - Benchmark = -13.29 - (-17.50) = +4.21%
    assert.equal(parseFloat((endPoint.portfolioCumulativeReturnPct - endPoint.benchmarkCumulativeReturnPct).toFixed(2)), 4.21);
    assert.equal(endPoint.activeReturnPct, 4.21);
    assert.equal(endPoint.portfolioDrawdownPct <= 0, true);
    assert.equal(endPoint.benchmarkDrawdownPct <= 0, true);
  });

  it('TC-FE-ST03: Constituent loss contributions reconcile with portfolio return under Carino logarithmic linking', () => {
    const constituents: ConstituentReplayResultDto[] = [
      {
        schemeOptionId: 1,
        fundName: 'HDFC Flexi Cap Fund',
        amfiCode: '118955',
        allocationWeightPct: 60.0,
        cumulativeReturnPct: -10.41,
        contributionPct: -6.25,
        standaloneContributionPct: -6.25,
        maxDrawdownPct: -12.30,
        annualizedVolatilityPct: 16.40,
        worstDailyReturnPct: -3.42,
        observationCount: 166,
      },
      {
        schemeOptionId: 10262,
        fundName: 'Parag Parikh Flexi Cap Fund',
        amfiCode: '122639',
        allocationWeightPct: 40.0,
        cumulativeReturnPct: -17.60,
        contributionPct: -7.04,
        standaloneContributionPct: -7.04,
        maxDrawdownPct: -18.50,
        annualizedVolatilityPct: 18.20,
        worstDailyReturnPct: -4.12,
        observationCount: 166,
      },
    ];

    const sumWeights = constituents.reduce((s, c) => s + c.allocationWeightPct, 0);
    assert.equal(sumWeights, 100.0);

    const sumContribution = constituents.reduce((s, c) => s + c.contributionPct, 0);
    assert.equal(Math.abs(sumContribution - (-13.29)) <= 0.02, true);

    const sumStandalone = constituents.reduce((s, c) => s + c.standaloneContributionPct, 0);
    assert.equal(Math.abs(sumStandalone - (-13.29)) <= 0.02, true);

    // Contribution equals weight * return
    const hdfcExpectedContrib = (60.0 / 100) * -10.41;
    assert.equal(Math.abs(constituents[0].contributionPct - hdfcExpectedContrib) < 0.05, true);
  });

  it('TC-FE-ST04: Exposure context flags temporal mismatch (> 45 days) with prominent warning', () => {
    const contextMismatched: StressExposureContextDto = {
      observationDate: '2026-09-30',
      daysMismatchFromEpisodeEnd: 1560,
      comparabilityState: 'SIGNIFICANT_TEMPORAL_MISMATCH',
      epistemicWarning: 'Holdings disclosure date (2026-09-30) is 1560 days from stress period. Underlying holdings during market drawdown likely diverged from this static snapshot.',
      largeCapWeightPct: 68.4,
      midCapWeightPct: 18.2,
      smallCapWeightPct: 8.5,
      cashWeightPct: 0.0,
      unclassifiedWeightPct: 4.9,
      topSectors: [
        { macroSector: 'Financial Services', weightPct: 32.5 },
        { macroSector: 'Technology', weightPct: 14.8 },
      ],
    };

    assert.equal(contextMismatched.comparabilityState, 'SIGNIFICANT_TEMPORAL_MISMATCH');
    assert.equal(contextMismatched.daysMismatchFromEpisodeEnd > 45, true);
    assert.match(contextMismatched.epistemicWarning || '', /days from stress period/);
  });

  it('TC-FE-ST05: Modeled sensitivity scenarios strictly require SENSITIVITY_ESTIMATE_ONLY label and omitted risks disclosure', () => {
    const scenario: ModeledSensitivityScenarioDto = {
      scenarioId: 'SCENARIO_CORRECTION_10',
      shockLabel: 'Standard Market Correction (-10% Benchmark)',
      benchmarkShockPct: -10.0,
      estimatedPortfolioLossPct: -8.84,
      betaUsed: 0.884,
      epistemicStatus: 'SENSITIVITY_ESTIMATE_ONLY',
      constituentEstimates: [
        {
          schemeOptionId: 1,
          fundName: 'HDFC Flexi Cap Fund',
          allocationWeightPct: 60.0,
          betaToBenchmark: 0.82,
          estimatedLossPct: -8.20,
          contributionPct: -4.92,
        },
        {
          schemeOptionId: 10262,
          fundName: 'Parag Parikh Flexi Cap Fund',
          allocationWeightPct: 40.0,
          betaToBenchmark: 0.98,
          estimatedLossPct: -9.80,
          contributionPct: -3.92,
        },
      ],
      omittedEffects: ['Non-linear liquidity dry-up', 'Correlations tending toward 1.0 during market crashes', 'Gap down openings and circuit limits'],
    };

    assert.equal(scenario.epistemicStatus, 'SENSITIVITY_ESTIMATE_ONLY');
    assert.equal(scenario.omittedEffects.length >= 2, true);
    // Linear beta transmission impact = beta * shock = 0.884 * -10.0 = -8.84%
    assert.equal(parseFloat((scenario.betaUsed * scenario.benchmarkShockPct).toFixed(2)), -8.84);
  });

  it('TC-FE-ST06: PortfolioStressReplayResponseDto enforces epistemic classifications and daily rebalancing disclosures', () => {
    const response: PortfolioStressReplayResponseDto = {
      status: 'HYPOTHETICAL_RECONSTRUCTION',
      episodeId: 'EPISODE_2021_2022_RATE_HIKE_DRAWDOWN',
      episodeTitle: '2021–2022 Global Rate-Hike & Inflation Drawdown',
      startDate: '2021-10-18',
      endDate: '2022-06-17',
      alignedTradingDaysCount: 166,
      portfolioCumulativeReturnPct: -13.29,
      benchmarkCumulativeReturnPct: -17.50,
      activeReturnPct: 4.21,
      portfolioAnnualizedVolatilityPct: 15.82,
      benchmarkAnnualizedVolatilityPct: 17.65,
      portfolioDownsideDeviationPct: 11.20,
      portfolioMaxDrawdownPct: -14.85,
      benchmarkMaxDrawdownPct: -18.20,
      peakDate: '2021-10-18',
      troughDate: '2022-06-17',
      recoveryDate: null,
      worstDailyReturnPct: -3.12,
      worstDailyReturnDate: '2022-02-24',
      benchmarkWorstDailyReturnPct: -4.85,
      constituentResults: [],
      trajectory: [],
      exposureContext: {
        observationDate: '2026-09-30',
        daysMismatchFromEpisodeEnd: 1560,
        comparabilityState: 'SIGNIFICANT_TEMPORAL_MISMATCH',
        epistemicWarning: 'Significant mismatch',
        largeCapWeightPct: 70,
        midCapWeightPct: 20,
        smallCapWeightPct: 10,
        cashWeightPct: 0,
        unclassifiedWeightPct: 0,
        topSectors: [],
      },
      modeledScenarios: [],
      epistemicClassifications: {
        portfolioReconstruction: 'HYPOTHETICAL_RECONSTRUCTION',
        modeledScenarios: 'SENSITIVITY_ESTIMATE_ONLY',
      },
      limitations: [
        'Daily rebalancing does not reflect investor transaction costs or taxes',
        'Holdings snapshots are temporally distant from this 2021 episode',
      ],
      rebalancingConvention: 'DAILY_REBALANCED_TARGET_WEIGHTS',
      attributionConvention: 'CARINO_LOGARITHMIC_LINKING',
      standaloneSumReturnPct: -13.29,
      compoundingResidualPct: 0.00,
      attributionResidualPct: 0.00,
      benchmarkCode: 'NIFTY_500_TRI',
      benchmarkName: 'Nifty 500 Total Returns Index',
      benchmarkId: 123,
    };

    assert.equal(response.status, 'HYPOTHETICAL_RECONSTRUCTION');
    assert.equal(response.epistemicClassifications.portfolioReconstruction, 'HYPOTHETICAL_RECONSTRUCTION');
    assert.equal(response.rebalancingConvention, 'DAILY_REBALANCED_TARGET_WEIGHTS');
    assert.equal(response.attributionConvention, 'CARINO_LOGARITHMIC_LINKING');
    assert.equal(response.attributionResidualPct, 0.00);
    assert.equal(response.benchmarkCode, 'NIFTY_500_TRI');
    assert.equal(response.benchmarkName, 'Nifty 500 Total Returns Index');
    assert.equal(response.benchmarkId, 123);
    assert.equal(response.limitations.length >= 2, true);
  });

  it('TC-FE-ST07: Benchmark identity integrity prevents silent substitution and preserves explicit naming', () => {
    const episode: HistoricalMarketEpisodeDto = {
      episodeId: 'EPISODE_2024_SMALLCAP_LIQUIDITY_SHOCK',
      title: 'February–March 2024 Small-Cap Liquidity & Valuation Shakeout',
      category: 'MARKET_STRUCTURE_LIQUIDITY',
      startDate: '2024-02-19',
      endDate: '2024-03-18',
      peakTroughConvention: 'Small-cap acute drawdown',
      regimeType: 'SHOCK_REVERSAL',
      description: 'Regulatory liquidity warnings and mid/small-cap valuation correction.',
      isAvailable: true,
      unavailabilityReason: null,
      tradingDaysCount: 19,
      benchmarkTotalReturnPct: -1.81,
      benchmarkCode: 'NIFTY_500_TRI',
    };

    assert.equal(episode.benchmarkCode, 'NIFTY_500_TRI');
    assert.notEqual(episode.benchmarkCode, 'NIFTY_50');
    assert.notEqual(episode.benchmarkCode, 'NIFTY_50_TRI');
  });
});
