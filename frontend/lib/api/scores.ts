/**
 * YUKIRA Quantitative Intelligence Platform
 * Typed client for Analytical Quality Score endpoints.
 *
 * Grounded in:
 * - GET /api/v1/scores/{schemeOptionId}/latest
 * - GET /api/v1/scores/run/{calculationRunId}
 * - GET /api/v1/scores/methodology
 */

import { apiFetch } from '@/lib/api/client';
import { AnalyticalScore, MethodologyConfig, CurrentScoreResponse, AnalyticalRefreshResult } from '@/types/score';
import { ApiError } from '@/types/api';

/**
 * Retrieves the current analytical score read model for a specific scheme option.
 */
export async function fetchCurrentScore(
  schemeOptionId: number
): Promise<CurrentScoreResponse> {
  return await apiFetch<CurrentScoreResponse>(
    `/api/v1/scores/${schemeOptionId}/current`
  );
}

/**
 * Triggers a production analytical score refresh batch operation.
 */
export async function triggerAnalyticalRefresh(
  asOfDate?: string
): Promise<AnalyticalRefreshResult> {
  const url = asOfDate
    ? `/api/v1/scores/refresh?asOfDate=${encodeURIComponent(asOfDate)}`
    : '/api/v1/scores/refresh';
  return await apiFetch<AnalyticalRefreshResult>(url, {
    method: 'POST',
  });
}

/**
 * Retrieves the machine-readable scoring methodology configuration.
 */
export async function fetchScoreMethodology(): Promise<MethodologyConfig> {
  return await apiFetch<MethodologyConfig>('/api/v1/scores/methodology');
}


/**
 * Retrieves the latest analytical score for a specific scheme option.
 * Returns null if the scheme option exists but has no score calculated yet (HTTP 404).
 */
export async function fetchLatestScore(
  schemeOptionId: number
): Promise<AnalyticalScore | null> {
  try {
    return await apiFetch<AnalyticalScore>(
      `/api/v1/scores/${schemeOptionId}/latest`
    );
  } catch (err: unknown) {
    const apiErr = err as ApiError;
    if (apiErr?.status === 404) {
      return null;
    }
    throw err;
  }
}

/**
 * Retrieves all historical analytical score snapshots for a specific scheme option.
 * Returns empty array if no historical snapshots exist.
 */
export async function fetchScoreHistory(
  schemeOptionId: number
): Promise<AnalyticalScore[]> {
  try {
    return await apiFetch<AnalyticalScore[]>(
      `/api/v1/scores/${schemeOptionId}/history`
    );
  } catch (err: unknown) {
    const apiErr = err as ApiError;
    if (apiErr?.status === 404) {
      return [];
    }
    throw err;
  }
}

/**
 * Retrieves the analytical score linked to a specific calculation run ID.
 * Returns null if no score was generated during that calculation run (HTTP 404).
 */
export async function fetchScoreByRunId(
  calculationRunId: number
): Promise<AnalyticalScore | null> {
  try {
    return await apiFetch<AnalyticalScore>(
      `/api/v1/scores/run/${calculationRunId}`
    );
  } catch (err: unknown) {
    const apiErr = err as ApiError;
    if (apiErr?.status === 404) {
      return null;
    }
    throw err;
  }
}

