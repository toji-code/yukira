import { apiFetch } from './client';
import {
  CalculationRun,
  MetricResult,
  TriggerCalculationRequest,
} from '@/types/calculation';

export async function fetchCalculationRun(id: number): Promise<CalculationRun> {
  return apiFetch<CalculationRun>(`/api/v1/calculation-runs/${id}`);
}

export async function fetchRunMetricResults(runId: number): Promise<MetricResult[]> {
  return apiFetch<MetricResult[]>(`/api/v1/calculation-runs/${runId}/results`);
}

export async function triggerCalculation(
  request: TriggerCalculationRequest
): Promise<CalculationRun> {
  return apiFetch<CalculationRun>('/api/v1/calculation-runs', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
