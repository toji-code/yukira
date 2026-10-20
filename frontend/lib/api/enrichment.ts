import { EnrichedFundProfileDto, PortfolioSnapshotDetailDto, DataQualityFreshnessDto } from '@/types/enrichment';
import { Holding } from '@/types/domain';

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * Fetches institutional Data Quality & Freshness evidence for a specific scheme option:
 * independent NAV/holdings freshness, cadence policies, raw unnormalized weights,
 * metric eligibility matrix, and answers to the 7 key investor due diligence questions.
 */
export async function fetchDataQualityFreshness(
  schemeOptionId: number,
  evaluationDate?: string,
  knowledgeCutoff?: string
): Promise<DataQualityFreshnessDto> {
  const url = new URL(`${BASE_URL}/api/v1/schemes/options/${schemeOptionId}/quality-freshness`);
  if (evaluationDate) {
    url.searchParams.set('evaluationDate', evaluationDate);
  }
  if (knowledgeCutoff) {
    url.searchParams.set('knowledgeCutoff', knowledgeCutoff);
  }

  const res = await fetch(url.toString(), {
    headers: {
      Accept: 'application/json',
    },
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(`Failed to fetch data quality & freshness evidence: ${res.status} ${res.statusText}`);
  }

  return res.json();
}

/**
 * Fetches enriched fund profile (AUM, TER, Manager, Investment Terms, Holdings summary)
 * for a specific scheme option under point-in-time constraints.
 */
export async function fetchOptionEnrichment(
  schemeOptionId: number,
  knowledgeCutoff?: string
): Promise<EnrichedFundProfileDto> {
  const url = new URL(`${BASE_URL}/api/v1/schemes/options/${schemeOptionId}/enrichment`);
  if (knowledgeCutoff) {
    url.searchParams.set('knowledgeCutoff', knowledgeCutoff);
  }

  const res = await fetch(url.toString(), {
    headers: {
      Accept: 'application/json',
    },
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(`Failed to fetch fund enrichment: ${res.status} ${res.statusText}`);
  }

  return res.json();
}

/**
 * Fetches detailed portfolio holdings with pre-formatted weights, ISIN, and verification metadata.
 */
export async function fetchOptionHoldings(
  schemeOptionId: number,
  knowledgeCutoff?: string
): Promise<Holding[]> {
  const url = new URL(`${BASE_URL}/api/v1/schemes/options/${schemeOptionId}/holdings`);
  if (knowledgeCutoff) {
    url.searchParams.set('knowledgeCutoff', knowledgeCutoff);
  }

  const res = await fetch(url.toString(), {
    headers: {
      Accept: 'application/json',
    },
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(`Failed to fetch scheme holdings: ${res.status} ${res.statusText}`);
  }

  return res.json();
}

/**
 * Fetches full monthly portfolio holdings snapshot including provenance, quality, and limitations.
 */
export async function fetchHoldingsSnapshot(
  schemeOptionId: number,
  asOfDate?: string
): Promise<PortfolioSnapshotDetailDto> {
  const url = new URL(`${BASE_URL}/api/v1/schemes/options/${schemeOptionId}/holdings/snapshot`);
  if (asOfDate) {
    url.searchParams.set('asOfDate', asOfDate);
  }

  const res = await fetch(url.toString(), {
    headers: {
      Accept: 'application/json',
    },
    cache: 'no-store',
  });

  if (!res.ok) {
    throw new Error(`Failed to fetch holdings snapshot: ${res.status} ${res.statusText}`);
  }

  return res.json();
}

/**
 * Fetches list of available monthly holdings reporting dates for a scheme option.
 */
export async function fetchAvailableHoldingsDates(
  schemeOptionId: number
): Promise<string[]> {
  const url = new URL(`${BASE_URL}/api/v1/schemes/options/${schemeOptionId}/holdings/dates`);
  const res = await fetch(url.toString(), {
    headers: {
      Accept: 'application/json',
    },
    cache: 'no-store',
  });

  if (!res.ok) {
    return [];
  }

  return res.json();
}
