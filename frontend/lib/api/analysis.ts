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
  return apiFetch<AnalysisResponse>(`/api/v1/analysis/${metricCode.toLowerCase()}`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
