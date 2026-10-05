import { EnrichedFundProfileDto } from '@/types/enrichment';
import { Holding } from '@/types/domain';

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

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
