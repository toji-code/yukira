import { apiFetch } from './client';
import {
  Ret02AnalysisResponse,
  Ret02CalculationRequest,
} from '@/types/analysis';

export async function fetchRet02Analysis(runId: number): Promise<Ret02AnalysisResponse> {
  return apiFetch<Ret02AnalysisResponse>(`/api/v1/analysis/ret02/${runId}`);
}

export async function executeRet02Analysis(
  request: Ret02CalculationRequest
): Promise<Ret02AnalysisResponse> {
  return apiFetch<Ret02AnalysisResponse>('/api/v1/analysis/ret02', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
