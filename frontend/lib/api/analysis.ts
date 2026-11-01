import { apiFetch } from './client';
import {
  AnalysisResponse,
  AnalyticalProfileResponse,
  CalculationRequest,
  DataQualityAuditResponse,
  ProfileCalculationRequest,
  RollingConsistencyResponse,
  CaptureRatioResponse,
  TrackingConsistencyResponse,
  BetaDynamicsResponse,
  BenchmarkRelationshipResponse,
  MetricHorizonEligibilityDto,
  BenchmarkObservationSeriesDto,
} from '@/types/analysis';

export async function fetchAnalysis<T = AnalysisResponse>(runId: number): Promise<T> {
  return apiFetch<T>(`/api/v1/analysis/${runId}`);
}


export async function executeAnalysis(
  metricCode: string,
  request: CalculationRequest
): Promise<AnalysisResponse> {
  const normalizedMetric = metricCode.toLowerCase().replace(/[^a-z0-9]/g, '');
  try {
    return await apiFetch<AnalysisResponse>(`/api/v1/analysis/${normalizedMetric}`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  } catch (err: unknown) {
    const apiErr = err as { status?: number; details?: Record<string, unknown> };
    if (apiErr?.status === 422 && apiErr?.details && typeof apiErr.details === 'object' && 'result' in apiErr.details) {
      return apiErr.details as unknown as AnalysisResponse;
    }
    throw err;
  }
}

export async function executeProfileAnalysis(
  request: ProfileCalculationRequest
): Promise<AnalyticalProfileResponse> {
  try {
    return await apiFetch<AnalyticalProfileResponse>(
      '/api/v1/analysis/profile',
      {
        method: 'POST',
        body: JSON.stringify(request),
      },
      45000
    );
  } catch (err: unknown) {
    const apiErr = err as { status?: number; details?: Record<string, unknown> };
    if (apiErr?.status === 422 && apiErr?.details && typeof apiErr.details === 'object' && 'metrics' in apiErr.details) {
      return apiErr.details as unknown as AnalyticalProfileResponse;
    }
    throw err;
  }
}

export async function fetchDataQualityAudit(
  schemeOptionId: number,
  knowledgeCutoffTime?: string
): Promise<DataQualityAuditResponse> {
  const query = knowledgeCutoffTime
    ? `?knowledgeCutoffTime=${encodeURIComponent(knowledgeCutoffTime)}`
    : '';
  return apiFetch<DataQualityAuditResponse>(`/api/v1/analysis/quality/${schemeOptionId}${query}`);
}

export async function fetchRollingConsistency(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoffTime?: string,
  benchmarkId?: number
): Promise<RollingConsistencyResponse> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoffTime) params.append('knowledgeCutoffTime', knowledgeCutoffTime);
  if (benchmarkId) params.append('benchmarkId', benchmarkId.toString());
  const queryString = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<RollingConsistencyResponse>(`/api/v1/analysis/rolling/${schemeOptionId}${queryString}`);
}

export async function fetchCaptureRatios(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoffTime?: string,
  benchmarkId?: number
): Promise<CaptureRatioResponse> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoffTime) params.append('knowledgeCutoffTime', knowledgeCutoffTime);
  if (benchmarkId) params.append('benchmarkId', benchmarkId.toString());
  const queryString = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<CaptureRatioResponse>(`/api/v1/analysis/capture/${schemeOptionId}${queryString}`);
}

export async function fetchTrackingConsistency(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoffTime?: string,
  benchmarkId?: number
): Promise<TrackingConsistencyResponse> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoffTime) params.append('knowledgeCutoffTime', knowledgeCutoffTime);
  if (benchmarkId) params.append('benchmarkId', benchmarkId.toString());
  const queryString = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<TrackingConsistencyResponse>(`/api/v1/analysis/tracking-consistency/${schemeOptionId}${queryString}`);
}

export async function fetchBetaDynamics(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoffTime?: string,
  benchmarkId?: number,
  horizon?: string
): Promise<BetaDynamicsResponse> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoffTime) params.append('knowledgeCutoffTime', knowledgeCutoffTime);
  if (benchmarkId) params.append('benchmarkId', benchmarkId.toString());
  if (horizon) params.append('horizon', horizon);
  const queryString = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<BetaDynamicsResponse>(`/api/v1/analysis/beta/${schemeOptionId}${queryString}`);
}

export async function fetchBenchmarkRelationship(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoffTime?: string,
  benchmarkId?: number
): Promise<BenchmarkRelationshipResponse> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoffTime) params.append('knowledgeCutoffTime', knowledgeCutoffTime);
  if (benchmarkId) params.append('benchmarkId', benchmarkId.toString());
  const queryString = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<BenchmarkRelationshipResponse>(`/api/v1/analysis/benchmark-relationship/${schemeOptionId}${queryString}`);
}

export async function fetchHorizonEligibility(
  horizon?: string
): Promise<MetricHorizonEligibilityDto[]> {
  const query = horizon ? `?horizon=${encodeURIComponent(horizon)}` : '';
  return apiFetch<MetricHorizonEligibilityDto[]>(`/api/v1/analysis/horizon-eligibility${query}`);
}

export async function fetchSchemeOptionHorizonEligibility(
  schemeOptionId: number,
  horizon?: string
): Promise<MetricHorizonEligibilityDto[]> {
  const query = horizon ? `?horizon=${encodeURIComponent(horizon)}` : '';
  return apiFetch<MetricHorizonEligibilityDto[]>(`/api/v1/analysis/horizon-eligibility/${schemeOptionId}${query}`);
}

export async function fetchBenchmarkSeries(
  schemeOptionId: number,
  startDate?: string,
  analysisCutoff?: string,
  knowledgeCutoff?: string
): Promise<BenchmarkObservationSeriesDto> {
  const params = new URLSearchParams();
  if (startDate) params.append('startDate', startDate);
  if (analysisCutoff) params.append('analysisCutoff', analysisCutoff);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const queryString = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<BenchmarkObservationSeriesDto>(`/api/v1/schemes/options/${schemeOptionId}/benchmark-series${queryString}`);
}
