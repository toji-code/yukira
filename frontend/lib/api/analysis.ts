import { apiFetch } from './client';
import {
  AnalysisResponse,
  AnalyticalProfileResponse,
  CalculationRequest,
  ProfileCalculationRequest,
} from '@/types/analysis';

export async function fetchAnalysis<T = AnalysisResponse>(runId: number): Promise<T> {
  return apiFetch<T>(`/api/v1/analysis/${runId}`);
}


export async function executeAnalysis(
  metricCode: string,
  request: CalculationRequest
): Promise<AnalysisResponse> {
  const normalizedMetric = metricCode.toLowerCase().replace(/[^a-z0-9]/g, '');
  return apiFetch<AnalysisResponse>(`/api/v1/analysis/${normalizedMetric}`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function executeProfileAnalysis(
  request: ProfileCalculationRequest
): Promise<AnalyticalProfileResponse> {
  return apiFetch<AnalyticalProfileResponse>('/api/v1/analysis/profile', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
