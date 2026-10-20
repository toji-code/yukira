import { apiFetch } from './client';
import {
  ComparisonRequest,
  ComparisonResponse,
  PortfolioOverlapResponseDto,
  PairwiseCorrelationDto
} from '@/types/comparison';

export async function executeComparison(
  request: ComparisonRequest
): Promise<ComparisonResponse> {
  return apiFetch<ComparisonResponse>('/api/v1/analysis/compare', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function fetchPairwiseOverlap(
  schemeOptionIdA: number,
  schemeOptionIdB: number,
  knowledgeCutoff?: string
): Promise<PortfolioOverlapResponseDto> {
  const params = new URLSearchParams({
    schemeOptionIdA: schemeOptionIdA.toString(),
    schemeOptionIdB: schemeOptionIdB.toString(),
  });
  if (knowledgeCutoff) {
    params.set('knowledgeCutoff', knowledgeCutoff);
  }
  return apiFetch<PortfolioOverlapResponseDto>(`/api/v1/schemes/options/overlap?${params.toString()}`);
}

export async function fetchPairwiseCorrelation(
  schemeOptionIdA: number,
  schemeOptionIdB: number,
  knowledgeCutoff?: string
): Promise<PairwiseCorrelationDto> {
  const params = new URLSearchParams({
    schemeOptionIdA: schemeOptionIdA.toString(),
    schemeOptionIdB: schemeOptionIdB.toString(),
  });
  if (knowledgeCutoff) {
    params.set('knowledgeCutoff', knowledgeCutoff);
  }
  return apiFetch<PairwiseCorrelationDto>(`/api/v1/schemes/options/correlation?${params.toString()}`);
}
