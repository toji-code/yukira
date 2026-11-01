import { apiFetch } from './client';
import {
  PortfolioRiskDiversificationResponseDto,
  PortfolioRiskDiversificationRequestDto,
  PortfolioStressEvidenceRecordDto
} from '@/types/portfolio-risk';

export * from '@/types/portfolio-risk';

export async function fetchPortfolioRiskDiversification(params?: {
  asOfDate?: string;
  knowledgeCutoff?: string;
  benchmarkCode?: string;
  schemeOptionIds?: number[];
}): Promise<PortfolioRiskDiversificationResponseDto> {
  const query = new URLSearchParams();
  if (params?.asOfDate) query.set('asOfDate', params.asOfDate);
  if (params?.knowledgeCutoff) query.set('knowledgeCutoff', params.knowledgeCutoff);
  if (params?.benchmarkCode) query.set('benchmarkCode', params.benchmarkCode);
  if (params?.schemeOptionIds && params.schemeOptionIds.length > 0) {
    query.set('schemeOptionIds', params.schemeOptionIds.join(','));
  }
  const qStr = query.toString();
  return apiFetch<PortfolioRiskDiversificationResponseDto>(
    `/api/v1/portfolio/risk-diversification${qStr ? `?${qStr}` : ''}`
  );
}

export async function evaluatePortfolioRiskDiversification(
  request: PortfolioRiskDiversificationRequestDto
): Promise<PortfolioRiskDiversificationResponseDto> {
  return apiFetch<PortfolioRiskDiversificationResponseDto>(
    '/api/v1/portfolio/risk-diversification',
    {
      method: 'POST',
      body: JSON.stringify(request),
    }
  );
}

export async function exportHistoricalStressEvidenceRecord(params?: {
  asOfDate?: string;
  knowledgeCutoff?: string;
  benchmarkCode?: string;
  schemeOptionIds?: number[];
}): Promise<PortfolioStressEvidenceRecordDto> {
  const query = new URLSearchParams();
  if (params?.asOfDate) query.set('asOfDate', params.asOfDate);
  if (params?.knowledgeCutoff) query.set('knowledgeCutoff', params.knowledgeCutoff);
  if (params?.benchmarkCode) query.set('benchmarkCode', params.benchmarkCode);
  if (params?.schemeOptionIds && params.schemeOptionIds.length > 0) {
    query.set('schemeOptionIds', params.schemeOptionIds.join(','));
  }
  const qStr = query.toString();
  return apiFetch<PortfolioStressEvidenceRecordDto>(
    `/api/v1/portfolio/risk-diversification/stress-export${qStr ? `?${qStr}` : ''}`
  );
}
