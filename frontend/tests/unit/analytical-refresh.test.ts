import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { AnalyticalScore, CurrentScoreResponse, AnalyticalRefreshResult } from '../../types/score';

describe('Production Analytical Refresh & Current Score V1 — Frontend Contract Tests', () => {

  it('TC-FE-AR01: CurrentScoreResponse contract enforces read model with isCurrent flag and data freshness', () => {
    const mockResponse: CurrentScoreResponse = {
      score: {
        scoreId: 10,
        schemeOptionId: 1,
        schemeName: 'HDFC Flexi Cap Fund - Direct Plan - Growth',
        amfiCode: '118955',
        isin: 'INF179K01UT0',
        score: 84.5,
        confidence: 100.0,
        status: 'AVAILABLE',
        scoreVersion: 'YUKIRA_SCORE_V1',
        methodologyStatus: 'PROVISIONAL_CANDIDATE',
        asOfDate: '2024-01-15',
        knowledgeCutoffTime: '2024-01-15T23:59:59+05:30',
        calculationRunId: 105,
        referencePopulation: 'EQUITY_FLEXI_CAP_DIRECT_GROWTH',
        disclaimer: 'Candidate methodology — not validated for production investment advice.',
        dimensions: [],
        evidenceConfidence: {
          totalObservations: 10,
          validObservations: 10,
          suspiciousObservations: 0,
          invalidObservations: 0,
          pairedBenchmarkObservations: 10,
          pairedReturnPeriods: 10,
          meetsObservationThreshold: true,
          sourceArtifactVerified: true,
          pitIntegrityMaintained: true,
          confidenceScore: 100.0,
          assessment: 'HIGH_CONFIDENCE',
          observationNotes: 'Verified observations.'
        }
      },
      isCurrent: true,
      dataFreshnessState: 'FRESH',
      message: 'Current analytical score snapshot available as of 2024-01-15.'
    };

    assert.equal(mockResponse.isCurrent, true);
    assert.equal(mockResponse.dataFreshnessState, 'FRESH');
    assert.equal((mockResponse.score as AnalyticalScore)?.schemeOptionId, 1);
    assert.equal((mockResponse.score as AnalyticalScore)?.scoreVersion, 'YUKIRA_SCORE_V1');
  });

  it('TC-FE-AR02: AnalyticalRefreshResult contract exposes batch counters and benchmark availability', () => {
    const mockRefreshResult: AnalyticalRefreshResult = {
      refreshStatus: 'SUCCESS',
      processedFundsCount: 1,
      scoresCalculatedCount: 1,
      scoresCachedCount: 0,
      scoresFailedCount: 0,
      benchmarkDataAvailable: true,
      benchmarkMessage: 'Required benchmark observations (NIFTY_500_TRI) are available through 2024-01-15.',
      asOfDate: '2024-01-15',
      knowledgeCutoffTime: '2024-01-15T23:59:59+05:30',
      scoreVersion: 'YUKIRA_SCORE_V1',
      calculationRunId: 105
    };

    assert.equal(mockRefreshResult.refreshStatus, 'SUCCESS');
    assert.equal(mockRefreshResult.benchmarkDataAvailable, true);
    assert.equal(mockRefreshResult.processedFundsCount, 1);
    assert.equal(mockRefreshResult.asOfDate, '2024-01-15');
  });

  it('TC-FE-AR03: AnalyticalRefreshResult exposes explicit benchmark dependency state when data is missing', () => {
    const mockDependencyResult: AnalyticalRefreshResult = {
      refreshStatus: 'BENCHMARK_DEPENDENCY_REQUIRED',
      processedFundsCount: 0,
      scoresCalculatedCount: 0,
      scoresCachedCount: 0,
      scoresFailedCount: 0,
      benchmarkDataAvailable: false,
      benchmarkMessage: 'Benchmark observations required past 2024-01-15 are unavailable. Current scores cannot be calculated without un-fabricated benchmark observations.',
      asOfDate: '2026-10-04',
      knowledgeCutoffTime: '2026-10-04T23:59:59+05:30',
      scoreVersion: 'YUKIRA_SCORE_V1',
      calculationRunId: 0
    };

    assert.equal(mockDependencyResult.refreshStatus, 'BENCHMARK_DEPENDENCY_REQUIRED');
    assert.equal(mockDependencyResult.benchmarkDataAvailable, false);
    assert.ok(mockDependencyResult.benchmarkMessage.includes('un-fabricated benchmark observations'));
  });
});
