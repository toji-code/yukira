/**
 * YUKIRA Quantitative Intelligence Platform
 * Typed client for Grounded AI Interpretation Layer.
 *
 * Grounded in:
 * - GET /api/v1/analysis/{runId}/interpretation
 * - POST /api/v1/analysis/profile/interpretation
 * - GET /api/v1/analysis/profile/{schemeOptionId}/interpretation
 * - GET /api/v1/scores/{schemeOptionId}/interpretation
 * - GET /api/v1/portfolio/interpretation
 */

import { apiFetch } from '@/lib/api/client';
import { GroundedAiInterpretation } from '@/types/interpretation';
import { AnalyticalProfileResponse } from '@/types/analysis';
import { ApiError } from '@/types/api';

/**
 * Retrieves grounded AI interpretation for a calculation run.
 */
export async function fetchAnalysisInterpretation(
  runId: number
): Promise<GroundedAiInterpretation | null> {
  try {
    return await apiFetch<GroundedAiInterpretation>(
      `/api/v1/analysis/${runId}/interpretation`
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
 * Generates grounded AI interpretation directly from an active AnalyticalProfileResponse.
 * Ensures 100% synchronization with the profile on screen.
 */
export async function generateProfileInterpretation(
  profile: AnalyticalProfileResponse
): Promise<GroundedAiInterpretation | null> {
  try {
    return await apiFetch<GroundedAiInterpretation>(
      '/api/v1/analysis/profile/interpretation',
      {
        method: 'POST',
        body: JSON.stringify(profile),
      }
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
 * Retrieves grounded AI interpretation for a scheme option across explicit horizon and date context.
 */
export async function fetchProfileInterpretation(
  schemeOptionId: number,
  options?: {
    horizon?: string;
    asOfDate?: string;
    knowledgeCutoffTime?: string;
  }
): Promise<GroundedAiInterpretation | null> {
  const params = new URLSearchParams();
  if (options?.horizon) params.set('horizon', options.horizon);
  if (options?.asOfDate) params.set('asOfDate', options.asOfDate);
  if (options?.knowledgeCutoffTime) params.set('knowledgeCutoffTime', options.knowledgeCutoffTime);

  const qs = params.toString();
  const url = `/api/v1/analysis/profile/${schemeOptionId}/interpretation${qs ? `?${qs}` : ''}`;

  try {
    return await apiFetch<GroundedAiInterpretation>(url);
  } catch (err: unknown) {
    const apiErr = err as ApiError;
    if (apiErr?.status === 404) {
      return null;
    }
    throw err;
  }
}

/**
 * Retrieves grounded AI interpretation for a scheme option score.
 */
export async function fetchScoreInterpretation(
  schemeOptionId: number
): Promise<GroundedAiInterpretation | null> {
  try {
    return await apiFetch<GroundedAiInterpretation>(
      `/api/v1/scores/${schemeOptionId}/interpretation`
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
 * Retrieves grounded AI interpretation for the investor's current portfolio.
 */
export async function fetchPortfolioInterpretation(): Promise<GroundedAiInterpretation | null> {
  try {
    return await apiFetch<GroundedAiInterpretation>(
      '/api/v1/portfolio/interpretation'
    );
  } catch (err: unknown) {
    const apiErr = err as ApiError;
    if (apiErr?.status === 404) {
      return null;
    }
    throw err;
  }
}
