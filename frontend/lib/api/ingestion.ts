import { apiFetch } from './client';

export interface AmfiDataFreshnessDto {
  freshnessState: 'FRESH' | 'PARTIAL' | 'STALE' | 'UNAVAILABLE' | string;
  latestNavDate: string | null;
  latestRetrievalTimestamp: string | null;
  latestIngestionTimestamp: string | null;
  latestSourceHash: string | null;
  latestObservationsIngested: number;
  latestRevisionsDetected: number;
  latestDuplicatesSkipped: number;
  totalNavObservationCount: number;
  validationIssueCount: number;
  daysSinceLatestObservation: number | null;
  freshnessThresholdDays: number;
  governanceDisclaimer: string;
}

export interface AmfiNavRefreshResultDto {
  status: 'SUCCESS' | 'SKIPPED_DUPLICATE_ARTIFACT' | 'FAILED' | string;
  sourceArtifactId: number | null;
  sourceHash: string | null;
  totalParsedRows: number;
  validRows: number;
  observationsIngested: number;
  revisionsDetected: number;
  duplicatesSkipped: number;
  issuesCreated: number;
  freshness: AmfiDataFreshnessDto;
  messages: string[];
}

export async function fetchAmfiFreshness(): Promise<AmfiDataFreshnessDto> {
  return apiFetch<AmfiDataFreshnessDto>('/api/v1/ingestion/amfi/freshness');
}

export async function triggerAmfiRefresh(): Promise<AmfiNavRefreshResultDto> {
  return apiFetch<AmfiNavRefreshResultDto>('/api/v1/ingestion/amfi/refresh', {
    method: 'POST'
  });
}
