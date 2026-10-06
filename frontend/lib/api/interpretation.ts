/**
 * YUKIRA Quantitative Intelligence Platform
 * Typed client for Grounded AI Interpretation Layer.
 *
 * Grounded in:
 * - GET /api/v1/analysis/{runId}/interpretation
 * - GET /api/v1/scores/{schemeOptionId}/interpretation
 * - GET /api/v1/portfolio/interpretation
 */

import { apiFetch } from '@/lib/api/client';
import { GroundedAiInterpretation } from '@/types/interpretation';
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
 * Retrieves grounded AI interpretation for a scheme option.
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
