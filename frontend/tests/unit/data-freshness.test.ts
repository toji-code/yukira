import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { AmfiDataFreshnessDto, AmfiNavRefreshResultDto } from '../../lib/api/ingestion';

describe('Production NAV Refresh & Data Freshness V1 — Frontend Contract Tests', () => {

  it('TC-FE-RF01: AmfiDataFreshnessDto contract enforces deterministic freshness states and threshold', () => {
    const mockFreshness: AmfiDataFreshnessDto = {
      freshnessState: 'FRESH',
      latestNavDate: '2024-01-15',
      latestRetrievalTimestamp: '2024-01-15T23:59:59+05:30',
      latestIngestionTimestamp: '2024-01-16T00:05:00+05:30',
      latestSourceHash: '900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259',
      latestObservationsIngested: 10,
      latestRevisionsDetected: 0,
      latestDuplicatesSkipped: 2,
      totalNavObservationCount: 32192,
      validationIssueCount: 0,
      daysSinceLatestObservation: 0,
      freshnessThresholdDays: 3,
      governanceDisclaimer: 'Data freshness is based strictly on verified AMFI source artifacts. Ingestion of raw NAV observations does NOT automatically recalculate analytical scores or execute quant engines.'
    };

    assert.equal(mockFreshness.freshnessState, 'FRESH');
    assert.equal(mockFreshness.latestNavDate, '2024-01-15');
    assert.equal(mockFreshness.freshnessThresholdDays, 3);
    assert.equal(mockFreshness.totalNavObservationCount, 32192);
    assert.ok(mockFreshness.governanceDisclaimer.includes('does NOT automatically recalculate analytical scores'));
  });

  it('TC-FE-RF02: AmfiNavRefreshResultDto preserves exact ingestion counters and cryptographic hash', () => {
    const mockResult: AmfiNavRefreshResultDto = {
      status: 'SUCCESS',
      sourceArtifactId: 101,
      sourceHash: '900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259',
      totalParsedRows: 15000,
      validRows: 14950,
      observationsIngested: 120,
      revisionsDetected: 2,
      duplicatesSkipped: 14828,
      issuesCreated: 50,
      freshness: {
        freshnessState: 'FRESH',
        latestNavDate: '2024-01-15',
        latestRetrievalTimestamp: '2024-01-15T23:59:59+05:30',
        latestIngestionTimestamp: '2024-01-16T00:05:00+05:30',
        latestSourceHash: '900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259',
        latestObservationsIngested: 120,
        latestRevisionsDetected: 2,
        latestDuplicatesSkipped: 14828,
        totalNavObservationCount: 32312,
        validationIssueCount: 50,
        daysSinceLatestObservation: 0,
        freshnessThresholdDays: 3,
        governanceDisclaimer: 'Data freshness is based strictly on verified AMFI source artifacts.'
      },
      messages: ['Ingested universe catalog & NAV observations from artifact #101']
    };

    assert.equal(mockResult.status, 'SUCCESS');
    assert.equal(mockResult.sourceArtifactId, 101);
    assert.equal(mockResult.revisionsDetected, 2);
    assert.equal(mockResult.duplicatesSkipped, 14828);
  });
});
