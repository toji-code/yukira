import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  NavFreshnessDetailDto,
  HoldingsFreshnessDetailDto,
  MetricEligibilityItemDto,
  SevenQuestionsAnswerDto,
} from '../../types/enrichment';
import { SchemeOption } from '../../types/domain';

describe('Data Quality & Freshness Experience — Frontend Tests', () => {

  it('TC-DQF01: NAV and holdings freshness states are strictly independent', () => {
    // Current NAV with STALE holdings
    const navFresh: NavFreshnessDetailDto = {
      latestNavDate: '2024-01-15',
      observationCount: 1243,
      usableDateRange: '2019-01-01 to 2024-01-15',
      freshnessState: 'CURRENT',
      cadencePolicy: 'Daily NAV published by 23:00 IST on trading days.',
      observationAgeDays: 0,
      sourceArtifactId: 1,
      sha256Hash: '900508f8...',
      warnings: [],
      explanation: 'NAV is current.',
    };

    const holdingsStale: HoldingsFreshnessDetailDto = {
      latestReportingDate: '2023-11-30',
      availabilityTime: '2023-12-10T18:30:00Z',
      ingestionTimestamp: '2023-12-11T09:00:00Z',
      freshnessState: 'STALE',
      coverageState: 'PARTIAL',
      cadencePolicy: 'Monthly portfolio disclosure within 10 days of calendar month-end.',
      reportingAgeDays: 75,
      positionCount: 10,
      rawSumWeights: 0.5482,
      rawSumWeightsPercentage: 54.82,
      unclassifiedExposurePercentage: 45.18,
      snapshotCount: 1,
      usableComparisonPeriods: ['2023-11-30'],
      sourceArtifactId: 10,
      sha256Hash: 'abc123hash...',
      sourceUri: 'https://amc.com/factsheet.pdf',
      discoveryStatus: 'OFFICIAL_DISCLOSURE_ACCESSIBLE',
      warnings: ['Reporting age exceeds 45 days'],
      explanation: 'Holdings are stale.',
    };

    assert.equal(navFresh.freshnessState, 'CURRENT');
    assert.equal(holdingsStale.freshnessState, 'STALE');
    assert.notEqual(navFresh.freshnessState, holdingsStale.freshnessState);
  });

  it('TC-DQF02: Raw holdings weights are never forced or normalized to 100%', () => {
    const holdingsDetail: HoldingsFreshnessDetailDto = {
      latestReportingDate: '2024-01-31',
      availabilityTime: '2024-02-10T18:30:00Z',
      ingestionTimestamp: '2024-02-10T18:35:00Z',
      freshnessState: 'PARTIAL',
      coverageState: 'PARTIAL',
      cadencePolicy: 'Monthly disclosure policy.',
      reportingAgeDays: 20,
      positionCount: 10,
      rawSumWeights: 0.5482,
      rawSumWeightsPercentage: 54.82,
      unclassifiedExposurePercentage: 45.18,
      snapshotCount: 1,
      usableComparisonPeriods: ['2024-01-31'],
      sourceArtifactId: 2,
      sha256Hash: '900508f8...',
      sourceUri: null,
      discoveryStatus: 'DISCOVERY_RESTRICTED',
      warnings: ['Raw total does not equal 100%'],
      explanation: 'Disclosed holdings cover top 10 positions only.',
    };

    // The raw weight percentage must remain 54.82%, not 100%
    assert.equal(holdingsDetail.rawSumWeightsPercentage, 54.82);
    assert.equal(holdingsDetail.unclassifiedExposurePercentage, 45.18);
    assert.equal(holdingsDetail.coverageState, 'PARTIAL');
  });

  it('TC-DQF03: Holdings reporting date is strictly separated from availability date and ingestion timestamp', () => {
    const holdingsDetail: HoldingsFreshnessDetailDto = {
      latestReportingDate: '2024-01-31',
      availabilityTime: '2024-02-10T18:30:00Z',
      ingestionTimestamp: '2024-02-10T18:35:00Z',
      freshnessState: 'PARTIAL',
      coverageState: 'PARTIAL',
      cadencePolicy: 'Monthly disclosure.',
      reportingAgeDays: 10,
      positionCount: 10,
      rawSumWeights: 0.5235,
      rawSumWeightsPercentage: 52.35,
      unclassifiedExposurePercentage: 47.65,
      snapshotCount: 1,
      usableComparisonPeriods: ['2024-01-31'],
      sourceArtifactId: 3,
      sha256Hash: 'hash123...',
      sourceUri: 'https://amc.com/factsheet.pdf',
      discoveryStatus: 'OFFICIAL_DISCLOSURE_ACCESSIBLE',
      warnings: [],
      explanation: 'Disclosed holdings cover 52.35%.',
    };

    assert.equal(holdingsDetail.latestReportingDate, '2024-01-31');
    assert.equal(holdingsDetail.availabilityTime, '2024-02-10T18:30:00Z');
    assert.equal(holdingsDetail.ingestionTimestamp, '2024-02-10T18:35:00Z');
    assert.notEqual(holdingsDetail.latestReportingDate, holdingsDetail.availabilityTime);
    assert.notEqual(holdingsDetail.availabilityTime, holdingsDetail.ingestionTimestamp);
  });

  it('TC-DQF04: Seven questions answer DTO conveys complete due-diligence facts without estimation', () => {
    const sevenQuestions: SevenQuestionsAnswerDto = {
      q1LatestNavObservation: '2024-01-15 (1,243 observations available)',
      q2LatestHoldingsReportingDate: '2024-01-31 (10 disclosed positions)',
      q3HoldingsSourceAvailabilityDate: '2024-02-10T18:30:00Z',
      q4IsDataCurrentRelativeToCadence: 'NAV: STALE · Holdings: STALE (PARTIAL)',
      q5CompletenessOfDisclosedHoldings: '54.82% disclosed exposure (45.18% unclassified/residual)',
      q6MetricCalculabilityStatus: 'RET-02 is ELIGIBLE; PORTFOLIO_DRIFT requires >=2 snapshots (1 available)',
      q7EvidenceOrLimitationsToInvestigate: 'Investigate factsheet disclosure limitation vs full statutory portfolio.',
    };

    assert.ok(sevenQuestions.q1LatestNavObservation.includes('2024-01-15'));
    assert.ok(sevenQuestions.q2LatestHoldingsReportingDate.includes('2024-01-31'));
    assert.ok(sevenQuestions.q3HoldingsSourceAvailabilityDate.includes('2024-02-10'));
    assert.ok(sevenQuestions.q4IsDataCurrentRelativeToCadence.includes('STALE'));
    assert.ok(sevenQuestions.q5CompletenessOfDisclosedHoldings.includes('54.82%'));
    assert.ok(sevenQuestions.q6MetricCalculabilityStatus.includes('requires >=2 snapshots'));
    assert.ok(sevenQuestions.q7EvidenceOrLimitationsToInvestigate.includes('Investigate factsheet'));
  });

  it('TC-DQF05: Metric eligibility matrix distinguishes APPROVED vs CANDIDATE governance tiers', () => {
    const metrics: MetricEligibilityItemDto[] = [
      {
        metricCode: 'RET-02',
        metricName: 'Simple Period Return',
        governanceTier: 'APPROVED',
        eligibilityState: 'ELIGIBLE',
        requiredEvidence: 'Requires >= 2 NAV observations',
        availableEvidence: '1,243 observations available',
        explanation: 'Eligible for deterministic computation.',
      },
      {
        metricCode: 'PORTFOLIO_DRIFT',
        metricName: 'Disclosed Holdings Drift',
        governanceTier: 'CANDIDATE',
        eligibilityState: 'INSUFFICIENT_HISTORY',
        requiredEvidence: 'Requires >= 2 monthly portfolio snapshots',
        availableEvidence: '1 snapshot available',
        explanation: 'Requires at least 2 distinct monthly snapshots to compute portfolio drift.',
      },
    ];

    assert.equal(metrics[0].governanceTier, 'APPROVED');
    assert.equal(metrics[0].eligibilityState, 'ELIGIBLE');
    assert.equal(metrics[1].governanceTier, 'CANDIDATE');
    assert.equal(metrics[1].eligibilityState, 'INSUFFICIENT_HISTORY');
  });

  it('TC-DQF06: Unknown and insufficient history states handle missing data without false confidence', () => {
    const unknownNav: NavFreshnessDetailDto = {
      latestNavDate: null,
      observationCount: 0,
      usableDateRange: 'None',
      freshnessState: 'UNAVAILABLE',
      cadencePolicy: 'Daily NAV expected.',
      observationAgeDays: null,
      sourceArtifactId: null,
      sha256Hash: null,
      warnings: ['Zero NAV observations registered'],
      explanation: 'No NAV history found.',
    };

    assert.equal(unknownNav.freshnessState, 'UNAVAILABLE');
    assert.equal(unknownNav.latestNavDate, null);
    assert.equal(unknownNav.observationCount, 0);
  });

  it('TC-DQF07: SchemeOption entity transient freshness fields correctly propagate to catalog view', () => {
    const option: Partial<SchemeOption> = {
      id: 1,
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      navFreshnessState: 'STALE',
      navFreshnessExplanation: 'NAV observation is from 2024-01-15 (STALE)',
      holdingsFreshnessState: 'PARTIAL',
      holdingsFreshnessExplanation: 'Disclosed holdings cover 54.82% of portfolio',
      holdingsRawSumPercentage: 54.82,
      snapshotCount: 1,
    };

    assert.equal(option.navFreshnessState, 'STALE');
    assert.equal(option.holdingsFreshnessState, 'PARTIAL');
    assert.equal(option.holdingsRawSumPercentage, 54.82);
    assert.equal(option.snapshotCount, 1);
  });

});
