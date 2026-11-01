import { apiFetch } from './client';

export type DueDiligenceStatus = 'TO_INVESTIGATE' | 'IN_PROGRESS' | 'REVIEWED' | 'NEEDS_FOLLOW_UP';

export interface DueDiligenceStatusMeta {
  key: DueDiligenceStatus;
  label: string;
  badgeLabel: string;
  colorClass: string;
  badgeClass: string;
  description: string;
}

export const DUE_DILIGENCE_STATUS_MAP: Record<DueDiligenceStatus, DueDiligenceStatusMeta> = {
  TO_INVESTIGATE: {
    key: 'TO_INVESTIGATE',
    label: 'To Investigate',
    badgeLabel: 'TO INVESTIGATE',
    colorClass: 'text-amber-400 border-amber-500/40 bg-amber-500/10',
    badgeClass: 'bg-amber-500/20 text-amber-300 border-amber-500/40',
    description: 'Question or evidence has not yet been reviewed.',
  },
  IN_PROGRESS: {
    key: 'IN_PROGRESS',
    label: 'In Progress',
    badgeLabel: 'IN PROGRESS',
    colorClass: 'text-sky-400 border-sky-500/40 bg-sky-500/10',
    badgeClass: 'bg-sky-500/20 text-sky-300 border-sky-500/40',
    description: 'Active investor investigation of peer evidence, metrics, or fund disclosures.',
  },
  REVIEWED: {
    key: 'REVIEWED',
    label: 'Reviewed',
    badgeLabel: 'REVIEWED',
    colorClass: 'text-emerald-400 border-emerald-500/40 bg-emerald-500/10',
    badgeClass: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40',
    description: 'Evidence reviewed by investor. (Does not imply approval or outcome guarantee).',
  },
  NEEDS_FOLLOW_UP: {
    key: 'NEEDS_FOLLOW_UP',
    label: 'Needs Follow-Up',
    badgeLabel: 'NEEDS FOLLOW-UP',
    colorClass: 'text-rose-400 border-rose-500/40 bg-rose-500/10',
    badgeClass: 'bg-rose-500/20 text-rose-300 border-rose-500/40',
    description: 'Investigation exposed missing evidence, stale observations, or open questions.',
  },
};

export interface WatchlistResponse {
  id: number;
  schemeId: number;
  schemeCode: string;
  schemeName: string;
  amcName?: string | null;
  category?: string | null;
  schemeOptionId?: number | null;
  optionType?: string | null;
  planType?: string | null;
  amfiCode?: string | null;
  isin?: string | null;
  latestNav?: number | null;
  latestNavDate?: string | null;
  yukiraScore?: number | null;
  yukiraScoreStatus?: string | null;
  reasonSaved?: string | null;
  investigationQuestion?: string | null;
  nextStep?: string | null;
  investigationStatus?: DueDiligenceStatus | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  isHeldInPortfolio?: boolean;
  portfolioHoldingId?: number | null;
  holdingUnits?: number | null;
  heldDifferentVariantNotice?: string | null;
  // F7 Investor Thesis Fields
  workingThesis?: string | null;
  supportingEvidence?: string | null;
  reconsiderationConditions?: string | null;
  thesisUpdatedAt?: string | null;
  thesisStatus?: 'ACTIVE_HYPOTHESIS' | 'CONFIRMED' | 'REFUTED' | 'MONITORING' | string | null;
  associatedFindingId?: string | null;
}

export interface WatchlistRequest {
  schemeId: number;
  schemeOptionId?: number;
  reasonSaved?: string;
  investigationQuestion?: string;
  nextStep?: string;
  investigationStatus?: DueDiligenceStatus | null;
  workingThesis?: string;
  supportingEvidence?: string;
  reconsiderationConditions?: string;
  thesisStatus?: string;
  associatedFindingId?: string;
}

export interface WatchlistNoteRequest {
  reasonSaved?: string;
  investigationQuestion?: string;
  nextStep?: string;
  investigationStatus?: DueDiligenceStatus | null;
  // F7 Investor Thesis Fields
  workingThesis?: string;
  supportingEvidence?: string;
  reconsiderationConditions?: string;
  thesisStatus?: string;
  associatedFindingId?: string;
}

export interface PortfolioResponse {
  schemeOptionId: number;
  units: number;
}

export async function fetchServerWatchlist(): Promise<WatchlistResponse[]> {
  return apiFetch<WatchlistResponse[]>('/api/investor/watchlist');
}

export async function addServerWatchlist(req: WatchlistRequest): Promise<WatchlistResponse> {
  const serverPayload = {
    schemeId: req.schemeId,
    schemeOptionId: req.schemeOptionId,
    reasonSaved: req.reasonSaved,
    investigationQuestion: req.investigationQuestion,
    nextStep: req.nextStep,
    thesisStatus: req.thesisStatus,
    associatedFindingId: req.associatedFindingId,
  };
  return apiFetch<WatchlistResponse>('/api/investor/watchlist', {
    method: 'POST',
    body: JSON.stringify(serverPayload),
  });
}

export async function updateServerWatchlistNote(
  schemeId: number,
  note: WatchlistNoteRequest
): Promise<WatchlistResponse> {
  const serverPayload = {
    reasonSaved: note.reasonSaved,
    investigationQuestion: note.investigationQuestion,
    nextStep: note.nextStep,
    thesisStatus: note.thesisStatus,
    associatedFindingId: note.associatedFindingId,
  };
  return apiFetch<WatchlistResponse>(`/api/investor/watchlist/${schemeId}`, {
    method: 'PUT',
    body: JSON.stringify(serverPayload),
  });
}

export async function updateServerWatchlistNoteByOption(
  schemeOptionId: number,
  note: WatchlistNoteRequest
): Promise<WatchlistResponse> {
  const serverPayload = {
    reasonSaved: note.reasonSaved,
    investigationQuestion: note.investigationQuestion,
    nextStep: note.nextStep,
    thesisStatus: note.thesisStatus,
    associatedFindingId: note.associatedFindingId,
  };
  return apiFetch<WatchlistResponse>(`/api/investor/watchlist/option/${schemeOptionId}`, {
    method: 'PUT',
    body: JSON.stringify(serverPayload),
  });
}

export async function removeServerWatchlist(schemeId: number): Promise<void> {
  return apiFetch<void>(`/api/investor/watchlist/${schemeId}`, {
    method: 'DELETE',
  });
}

export async function removeServerWatchlistByOption(schemeOptionId: number): Promise<void> {
  return apiFetch<void>(`/api/investor/watchlist/option/${schemeOptionId}`, {
    method: 'DELETE',
  });
}

export async function fetchServerPortfolio(): Promise<PortfolioResponse[]> {
  return apiFetch<PortfolioResponse[]>('/api/investor/portfolio');
}

export async function addServerPortfolioHolding(schemeOptionId: number, units: number): Promise<PortfolioResponse> {
  return apiFetch<PortfolioResponse>('/api/investor/portfolio', {
    method: 'POST',
    body: JSON.stringify({ schemeOptionId, units }),
  });
}

export async function removeServerPortfolioHolding(schemeOptionId: number): Promise<void> {
  return apiFetch<void>(`/api/investor/portfolio/${schemeOptionId}`, {
    method: 'DELETE',
  });
}

export interface InvestorAccountResponse {
  id: number;
  auth0Subject: string;
  createdAt?: string | null;
  savedFundsCount?: number;
  portfolioHoldingsCount?: number;
  planId?: string | null;
  planName?: string | null;
  entitlementStatus?: string | null;
  subscriptionStatus?: string | null;
}

export async function fetchInvestorAccount(): Promise<InvestorAccountResponse> {
  return apiFetch<InvestorAccountResponse>('/api/investor/account');
}

export interface InvestorResearchThesisHistoryDto {
  id: number;
  watchlistId: number;
  previousThesis?: string | null;
  newThesis?: string | null;
  previousStatus?: string | null;
  newStatus?: string | null;
  changeReason?: string | null;
  changedAt: string;
}

export async function fetchWatchlistThesisHistory(watchlistId: number): Promise<InvestorResearchThesisHistoryDto[]> {
  return apiFetch<InvestorResearchThesisHistoryDto[]>(`/api/investor/watchlist/${watchlistId}/history`);
}

export interface InvestorComparisonResearchItem {
  id: number;
  comparisonTitle: string;
  schemeOptionIdsCsv: string;
  asOfDate: string;
  knowledgeCutoff: string;
  benchmarkCode: string;
  compatibilityStatus: string;
  metricsSummaryJson?: string | null;
  investorThesis?: string | null;
  investorNotes?: string | null;
  associatedFindingId?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export async function fetchSavedComparisons(): Promise<InvestorComparisonResearchItem[]> {
  return apiFetch<InvestorComparisonResearchItem[]>('/api/investor/workspace/comparisons');
}

// --- TASK 191: RESEARCH TIMELINE ---
export interface InvestorResearchTimelineEventDto {
  eventId: string;
  eventType: string;
  title: string;
  summary: string;
  targetType: string;
  targetId: string;
  referenceUrl: string;
  occurredAt: string;
}

export async function fetchUnifiedTimeline(limit: number = 50): Promise<InvestorResearchTimelineEventDto[]> {
  return apiFetch<InvestorResearchTimelineEventDto[]>(`/api/v1/investors/workspace/timeline?limit=${limit}`);
}

// --- TASK 192: RESEARCH TAGS ---
export interface InvestorResearchTagDto {
  id: number;
  tagName: string;
  targetType: string;
  targetId: string;
  createdAt: string;
}

export async function fetchResearchTags(targetType?: string, targetId?: string): Promise<InvestorResearchTagDto[]> {
  const query = targetType && targetId ? `?targetType=${encodeURIComponent(targetType)}&targetId=${encodeURIComponent(targetId)}` : '';
  return apiFetch<InvestorResearchTagDto[]>(`/api/v1/investors/workspace/tags${query}`);
}

export async function addResearchTag(tagName: string, targetType: string, targetId: string): Promise<InvestorResearchTagDto> {
  return apiFetch<InvestorResearchTagDto>('/api/v1/investors/workspace/tags', {
    method: 'POST',
    body: JSON.stringify({ tagName, targetType, targetId }),
  });
}

export async function deleteResearchTag(id: number): Promise<void> {
  await apiFetch(`/api/v1/investors/workspace/tags/${id}`, { method: 'DELETE' });
}

// --- TASK 193: SAVED DISCOVERY FILTERS ---
export interface InvestorSavedFilterDto {
  id: number;
  filterName: string;
  filterCriteriaJson: string;
  filterType: string;
  createdAt: string;
  updatedAt: string;
}

export async function fetchSavedFilters(): Promise<InvestorSavedFilterDto[]> {
  return apiFetch<InvestorSavedFilterDto[]>('/api/v1/investors/workspace/filters');
}

export async function saveFilter(filterName: string, filterCriteriaJson: string, filterType?: string): Promise<InvestorSavedFilterDto> {
  return apiFetch<InvestorSavedFilterDto>('/api/v1/investors/workspace/filters', {
    method: 'POST',
    body: JSON.stringify({ filterName, filterCriteriaJson, filterType }),
  });
}

export async function deleteFilter(id: number): Promise<void> {
  await apiFetch(`/api/v1/investors/workspace/filters/${id}`, { method: 'DELETE' });
}

// --- TASK 194: COMPARISON REVISION HISTORY ---
export interface InvestorComparisonVersionDto {
  id: number;
  comparisonResearchId: number;
  versionNumber: number;
  schemeOptionIdsCsv: string;
  asOfDate: string;
  benchmarkCode: string;
  compatibilityStatus: string;
  metricsSummaryJson?: string | null;
  investorThesis?: string | null;
  investorNotes?: string | null;
  versionReason?: string | null;
  createdAt: string;
}

export async function fetchComparisonVersions(comparisonId: number): Promise<InvestorComparisonVersionDto[]> {
  return apiFetch<InvestorComparisonVersionDto[]>(`/api/v1/investors/workspace/comparisons/${comparisonId}/versions`);
}

// --- TASK 195: INVESTIGATION ACTIONS ---
export interface InvestorInvestigationActionDto {
  id: number;
  actionTitle: string;
  actionDescription?: string | null;
  targetType: string;
  targetId: string;
  status: string;
  priority: string;
  dueDate?: string | null;
  completedAt?: string | null;
  outcomeNotes?: string | null;
  createdAt: string;
  updatedAt: string;
}

export async function fetchInvestigationActions(status?: string): Promise<InvestorInvestigationActionDto[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return apiFetch<InvestorInvestigationActionDto[]>(`/api/v1/investors/workspace/actions${query}`);
}

export async function createInvestigationAction(data: {
  actionTitle: string;
  actionDescription?: string;
  targetType?: string;
  targetId?: string;
  priority?: string;
  dueDate?: string;
}): Promise<InvestorInvestigationActionDto> {
  return apiFetch<InvestorInvestigationActionDto>('/api/v1/investors/workspace/actions', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateActionStatus(id: number, status: string, outcomeNotes?: string): Promise<InvestorInvestigationActionDto> {
  return apiFetch<InvestorInvestigationActionDto>(`/api/v1/investors/workspace/actions/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, outcomeNotes }),
  });
}

// --- TASK 203: DISPLAY PREFERENCES ---
export interface InvestorDisplayPreferenceDto {
  id: number;
  defaultAnalysisPeriod: string;
  defaultWorkspaceTab: string;
  preferredTheme: string;
  showCandidateMetricWarnings: boolean;
  dateDisplayFormat: string;
  updatedAt: string;
}

export async function fetchDisplayPreferences(): Promise<InvestorDisplayPreferenceDto> {
  return apiFetch<InvestorDisplayPreferenceDto>('/api/v1/investors/preferences');
}

export async function updateDisplayPreferences(prefs: Partial<InvestorDisplayPreferenceDto>): Promise<InvestorDisplayPreferenceDto> {
  return apiFetch<InvestorDisplayPreferenceDto>('/api/v1/investors/preferences', {
    method: 'PUT',
    body: JSON.stringify(prefs),
  });
}

// --- TASK 204: GLOBAL INVESTOR SEARCH ---
export interface GlobalSearchResultDto {
  resultType: string;
  title: string;
  subtitle: string;
  targetUrl: string;
  badgeText: string;
  badgeVariant: string;
}

export async function globalSearch(query: string): Promise<GlobalSearchResultDto[]> {
  return apiFetch<GlobalSearchResultDto[]>(`/api/v1/investors/search?q=${encodeURIComponent(query)}`);
}

// --- TASK 205: EVIDENCE EXPORT BUNDLE ---
export interface EvidenceExportBundleDto {
  exportId: string;
  investorId: number;
  exportFingerprint: string;
  generatedAt: string;
  governanceNote: string;
  portfolioSummary: Record<string, any>;
  activeWatchlist: Record<string, any>[];
  savedComparisons: Record<string, any>[];
  monitoringConditions: Record<string, any>[];
  investigationActions: Record<string, any>[];
}

export async function exportEvidenceBundle(): Promise<EvidenceExportBundleDto> {
  return apiFetch<EvidenceExportBundleDto>('/api/v1/investors/export/bundle');
}

// --- PHASE 3 & 4: COVERAGE, BENCHMARKS, LIFECYCLE ---
export interface SchemeDataCoverageDto {
  id: number;
  schemeId: number;
  schemeName: string;
  navCoverageStatus: string;
  benchmarkCoverageStatus: string;
  holdingsCoverageStatus: string;
  riskFreeCoverageStatus: string;
  peerUniverseStatus: string;
  earliestNavDate?: string | null;
  latestNavDate?: string | null;
  latestHoldingsPeriod?: string | null;
  latestNavAvailability?: string | null;
  dataLimitationsSummary?: string | null;
  updatedAt: string;
}

export async function fetchSchemeCoverage(schemeId: number): Promise<SchemeDataCoverageDto> {
  return apiFetch<SchemeDataCoverageDto>(`/api/v1/schemes/${schemeId}/coverage`);
}

export interface SchemeBenchmarkMappingDto {
  id: number;
  schemeId: number;
  schemeName: string;
  benchmarkId: number;
  benchmarkCode: string;
  benchmarkName: string;
  mappingRole: string;
  mappingSource: string;
  effectiveFrom: string;
  effectiveTo?: string | null;
  isTri: boolean;
  provenanceReference?: string | null;
  createdAt: string;
}

export async function fetchSchemeBenchmarks(schemeId: number): Promise<SchemeBenchmarkMappingDto[]> {
  return apiFetch<SchemeBenchmarkMappingDto[]>(`/api/v1/schemes/${schemeId}/benchmarks`);
}

export interface SchemeLifecycleRecordDto {
  id: number;
  schemeId: number;
  schemeName: string;
  lifecycleStatus: string;
  effectiveDate: string;
  successorSchemeId?: number | null;
  successorSchemeName?: string | null;
  predecessorSchemeId?: number | null;
  predecessorSchemeName?: string | null;
  regulatoryCircularRef?: string | null;
  remarks?: string | null;
  createdAt: string;
}

export async function fetchSchemeLifecycle(schemeId: number): Promise<SchemeLifecycleRecordDto[]> {
  return apiFetch<SchemeLifecycleRecordDto[]>(`/api/v1/schemes/${schemeId}/lifecycle`);
}

// --- PHASE 6: PORTFOLIO CASH FLOWS, VALUATION, SNAPSHOTS ---
export interface PortfolioCashFlowTimelineDto {
  investorId: number;
  asOfDate: string;
  netCumulativeInvested: number;
  currentPortfolioValue: number;
  totalCashInflows: number;
  totalCashOutflows: number;
  cashFlows: {
    transactionId: number;
    transactionDate: string;
    transactionType: string;
    classification: string;
    schemeName: string;
    amount: number;
    units: number;
    nav: number;
    flowDirection: string;
  }[];
}

export async function fetchCashFlowTimeline(asOfDate?: string): Promise<PortfolioCashFlowTimelineDto> {
  const query = asOfDate ? `?asOfDate=${encodeURIComponent(asOfDate)}` : '';
  return apiFetch<PortfolioCashFlowTimelineDto>(`/api/v1/portfolio/transactions/timeline${query}`);
}

export interface PortfolioValuationHistoryDto {
  id: number;
  portfolioId?: number | null;
  valuationDate: string;
  knowledgeCutoff?: string | null;
  totalMarketValue: number;
  totalCostBasis: number;
  unrealizedGainLoss: number;
  valuedPositionsCount: number;
  unvaluedPositionsCount: number;
  valuationSourceContext?: string | null;
  createdAt: string;
}

export async function fetchValuationHistory(): Promise<PortfolioValuationHistoryDto[]> {
  return apiFetch<PortfolioValuationHistoryDto[]>('/api/v1/portfolio/valuation-history');
}

export interface PortfolioResearchSnapshotDto {
  id: number;
  snapshotTitle: string;
  allocationSnapshotId?: number | null;
  asOfDate: string;
  knowledgeCutoff: string;
  fingerprint: string;
  xirrValue?: number | null;
  totalValue: number;
  cashFlowSummaryJson?: string | null;
  researchThesis?: string | null;
  investigationNotes?: string | null;
  createdAt: string;
}

export async function fetchResearchSnapshots(): Promise<PortfolioResearchSnapshotDto[]> {
  return apiFetch<PortfolioResearchSnapshotDto[]>('/api/v1/portfolio/research-snapshots');
}

export async function createResearchSnapshot(data: {
  snapshotTitle: string;
  allocationSnapshotId?: number;
  asOfDate?: string;
  xirrValue?: number;
  totalValue?: number;
  cashFlowSummaryJson?: string;
  researchThesis?: string;
  investigationNotes?: string;
}): Promise<PortfolioResearchSnapshotDto> {
  return apiFetch<PortfolioResearchSnapshotDto>('/api/v1/portfolio/research-snapshots', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// --- PHASE 8: EVIDENCE PACKET & METHODOLOGY EXPLAINERS ---
export interface CanonicalEvidencePacketDto {
  packetFingerprint: string;
  schemeOptionId: number;
  schemeName: string;
  isin: string;
  amfiCode: string;
  categoryName: string;
  asOfDate: string;
  knowledgeCutoff: string;
  primaryBenchmarkCode: string;
  metrics: {
    metricCode: string;
    metricName: string;
    formattedValue: string;
    numericValue: number;
    methodologyStatus: string;
    methodologyVersion: string;
    calculationRunId: number;
    runFingerprint: string;
  }[];
  dataQualityFlags: Record<string, string>;
  dataLimitations: string[];
  missingFields: string[];
  generatedAt: string;
}

export async function fetchCanonicalEvidencePacket(schemeOptionId: number): Promise<CanonicalEvidencePacketDto> {
  return apiFetch<CanonicalEvidencePacketDto>(`/api/v1/ai/evidence-packet/${schemeOptionId}`);
}

export interface MethodologyGovernanceExplainerDto {
  metricCode: string;
  metricName: string;
  methodologyStatus: string;
  description: string;
  mathematicalFormula: string;
  requiredInputsSummary: string;
  minimumHorizonRequired: string;
  governanceNote: string;
  regulatoryReferences: string[];
}

export async function fetchMethodologyExplainers(): Promise<MethodologyGovernanceExplainerDto[]> {
  return apiFetch<MethodologyGovernanceExplainerDto[]>('/api/v1/ai/methodology/explainers');
}

// --- PHASE 10: TELEMETRY & RELEASE READINESS ---
export interface OperationalJobExecutionDto {
  id: number;
  jobName: string;
  executionTrigger: string;
  executionStatus: string;
  startedAt: string;
  completedAt?: string | null;
  scopeDescription?: string | null;
  recordsProcessed: number;
  recordsFailed: number;
  failureReason?: string | null;
  telemetryJson?: string | null;
  createdAt: string;
}

export async function fetchOperationalJobs(jobName?: string): Promise<OperationalJobExecutionDto[]> {
  const query = jobName ? `?jobName=${encodeURIComponent(jobName)}` : '';
  return apiFetch<OperationalJobExecutionDto[]>(`/api/v1/admin/telemetry/jobs${query}`);
}

export interface V1PlatformReadinessDto {
  platformVersion: string;
  releaseStatus: string;
  methodologyGovernanceStatus: string;
  baselineOperationalMetric: string;
  totalCandidateMetricsCount: number;
  nonAdvisorySafetyEnforced: boolean;
  bitemporalPitEnforced: boolean;
  deterministicBackendAuthoritative: boolean;
  subsystemHealth: Record<string, string>;
  evaluatedAt: string;
}

export async function fetchPlatformReadiness(): Promise<V1PlatformReadinessDto> {
  return apiFetch<V1PlatformReadinessDto>('/api/v1/admin/telemetry/readiness');
}

