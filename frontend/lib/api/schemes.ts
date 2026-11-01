import { apiFetch } from './client';
import { Scheme, SchemeOption, Holding } from '@/types/domain';

export interface SchemeFilterParams {
  search?: string;
  category?: string;
  subcategory?: string;
  amcCode?: string;
  status?: string;
}

export interface SchemeOptionFilterParams {
  search?: string;
  planType?: string;
  optionType?: string;
  category?: string;
  amcCode?: string;
  schemeId?: number;
}

export async function fetchSchemes(params?: SchemeFilterParams): Promise<Scheme[]> {
  const queryParts: string[] = [];
  if (params?.search && params.search.trim()) {
    queryParts.push(`search=${encodeURIComponent(params.search.trim())}`);
  }
  if (params?.category && params.category.trim() && params.category !== 'ALL') {
    queryParts.push(`category=${encodeURIComponent(params.category.trim())}`);
  }
  if (params?.subcategory && params.subcategory.trim()) {
    queryParts.push(`subcategory=${encodeURIComponent(params.subcategory.trim())}`);
  }
  if (params?.amcCode && params.amcCode.trim()) {
    queryParts.push(`amcCode=${encodeURIComponent(params.amcCode.trim())}`);
  }
  if (params?.status && params.status.trim()) {
    queryParts.push(`status=${encodeURIComponent(params.status.trim())}`);
  }
  const queryString = queryParts.length > 0 ? `?${queryParts.join('&')}` : '';
  return apiFetch<Scheme[]>(`/api/v1/schemes${queryString}`, undefined, 25000);
}

export async function fetchAllSchemes(): Promise<Scheme[]> {
  return fetchSchemes();
}

export async function fetchSchemeById(id: number | string): Promise<Scheme> {
  return apiFetch<Scheme>(`/api/v1/schemes/${id}`, undefined, 20000);
}

export async function fetchSchemeOptions(params?: SchemeOptionFilterParams): Promise<SchemeOption[]> {
  const queryParts: string[] = [];
  if (params?.search && params.search.trim()) {
    queryParts.push(`search=${encodeURIComponent(params.search.trim())}`);
  }
  if (params?.planType && params.planType !== 'ALL') {
    queryParts.push(`planType=${encodeURIComponent(params.planType)}`);
  }
  if (params?.optionType && params.optionType !== 'ALL') {
    queryParts.push(`optionType=${encodeURIComponent(params.optionType)}`);
  }
  if (params?.category && params.category !== 'ALL') {
    queryParts.push(`category=${encodeURIComponent(params.category)}`);
  }
  if (params?.amcCode && params.amcCode.trim()) {
    queryParts.push(`amcCode=${encodeURIComponent(params.amcCode.trim())}`);
  }
  if (params?.schemeId) {
    queryParts.push(`schemeId=${params.schemeId}`);
  }
  const queryString = queryParts.length > 0 ? `?${queryParts.join('&')}` : '';
  return apiFetch<SchemeOption[]>(`/api/v1/schemes/options${queryString}`, undefined, 25000);
}

export async function fetchAllSchemeOptions(): Promise<SchemeOption[]> {
  // The full expanded-universe share-class payload is ~9.5 MB across 14k+ options, which
  // legitimately exceeds standard client defaults. Give this call an explicit budget.
  return apiFetch<SchemeOption[]>('/api/v1/schemes/options', undefined, 45000);
}

export async function fetchSchemeOptionsBySchemeId(schemeId: number | string): Promise<SchemeOption[]> {
  try {
    return await apiFetch<SchemeOption[]>(`/api/v1/schemes/${schemeId}/options`, undefined, 20000);
  } catch (err) {
    console.warn(`Failed to fetch scheme options for scheme ${schemeId}:`, err);
    return [];
  }
}

export async function fetchSchemeHoldings(schemeOptionId: number): Promise<Holding[]> {
  return apiFetch<Holding[]>(`/api/v1/schemes/options/${schemeOptionId}/holdings`);
}

export async function searchSchemeOptions(query: string): Promise<SchemeOption[]> {
  if (!query || query.trim().length === 0) return [];
  return apiFetch<SchemeOption[]>(`/api/v1/schemes/options?search=${encodeURIComponent(query.trim())}`);
}

export interface ScreenerMetricValue {
  metricCode: string;
  numericValue: number | null;
  formattedValue: string;
  periodType: string;
  units: string;
  calculationStatus: string;
  percentileRank?: number | null;
  formattedPercentile?: string | null;
  peerDenominator?: number | null;
  rankingDirection?: string | null;
  peerCohortStatus?: string | null;
  unavailableReason?: string | null;
  errorMessage?: string | null;
}

export interface ScreenerItem {
  schemeOptionId: number;
  schemeId: number;
  schemeCode: string;
  schemeName: string;
  amcId: number;
  amcName: string;
  amfiCode: string;
  isin: string;
  category: string;
  categoryDisplayName: string;
  distinctNavDates: number;
  sufficiencyStatus: string;
  isEligibleFor3Y: boolean;
  exclusionReason?: string | null;
  candidateBenchmarkCode: string;
  candidateBenchmarkName: string;
  benchmarkGovernanceStatus: string;
  benchmarkObservationCount: number;
  benchmarkCoverageStatus: string;
  latestCalculationRunId?: number | null;
  asOfDate: string;
  knowledgeCutoffTime?: string | null;
  calculationStatus: string;
  validMetricCount?: number;
  metrics: Record<string, ScreenerMetricValue>;
  allTimeNavDates?: number | null;
  earliestNavDate?: string | null;
  latestNavDate?: string | null;
  dataFreshnessStatus?: string | null;
  planType?: string;
  optionType?: string;
  horizon?: string;
  isEligibleForHorizon?: boolean;
}

export interface CategoryScreenerSummary {
  category: string;
  displayName: string;
  candidateBenchmarkCode: string;
  totalOptions: number;
  eligibleCount: number;
  partialCount: number;
  calculatedCount: number;
}

export interface ScreenerMetricDefinition {
  metricCode: string;
  metricName: string;
  category: string;
  units: string;
  evaluationWindow: string;
  benchmarkDependent: boolean;
  formulaDescription: string;
  epistemicStatus: string;
  rankingDirection?: string | null;
  percentileInterpretation?: string | null;
}

export interface UniverseScreenerResponse {
  asOfDate: string;
  totalReferenceOptions: number;
  eligibleOptionsCount: number;
  partialOptionsCount: number;
  zeroHistoryCount: number;
  calculatedOptionsCount: number;
  allTimeSufficientCount?: number | null;
  allTimePartialCount?: number | null;
  threeYearSufficientCount?: number | null;
  categorySummaries: Record<string, CategoryScreenerSummary>;
  metricDefinitions: ScreenerMetricDefinition[];
  items: ScreenerItem[];
  governanceDisclaimers: string[];
  generatedAt: string;
  horizon?: string;
  evaluationWindow?: string;
  horizonStartDate?: string;
  horizonThreshold?: number;
}

export async function fetchUniverseScreener(params?: {
  category?: string;
  eligibleOnly?: boolean;
  asOfDate?: string;
  horizon?: string;
}): Promise<UniverseScreenerResponse> {
  const queryParts: string[] = [];
  if (params?.category && params.category !== 'ALL') {
    queryParts.push(`category=${encodeURIComponent(params.category)}`);
  }
  if (params?.eligibleOnly) {
    queryParts.push(`eligibleOnly=true`);
  }
  if (params?.asOfDate) {
    queryParts.push(`asOfDate=${encodeURIComponent(params.asOfDate)}`);
  }
  if (params?.horizon) {
    queryParts.push(`horizon=${encodeURIComponent(params.horizon)}`);
  }
  const qs = queryParts.length > 0 ? `?${queryParts.join('&')}` : '';
  return apiFetch<UniverseScreenerResponse>(`/api/v1/schemes/screener${qs}`, undefined, 30000);
}
