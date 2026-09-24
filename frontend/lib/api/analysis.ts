import { apiFetch } from './client';
import {
  AnalysisResponse,
  CalculationRequest,
} from '@/types/analysis';

export async function fetchAnalysis(runId: number): Promise<AnalysisResponse> {
  return apiFetch<AnalysisResponse>(`/api/v1/analysis/${runId}`);
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
