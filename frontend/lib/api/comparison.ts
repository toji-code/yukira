import { apiFetch } from './client';
import { ComparisonRequest, ComparisonResponse } from '@/types/comparison';

export async function executeComparison(
  request: ComparisonRequest
): Promise<ComparisonResponse> {
  return apiFetch<ComparisonResponse>('/api/v1/analysis/compare', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
