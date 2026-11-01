/**
 * YUKIRA Quantitative Intelligence Platform
 * Task 12 & Task 13: Continuous Fund & Portfolio Monitoring API Client
 *
 * Epistemic Contract:
 * - Deterministic, backend-authoritative monitoring data
 * - Authoritative Metric Registry alignment: RSK-01 (Volatility), RSK-02 (Downside Semideviation), RSK-03 (Max Drawdown)
 * - Clear distinction between genuine investment change and data quality/disclosure timing artifacts
 * - Operational execution ledger tracking scheduled evaluation runs and evidence availability
 * - Zero predictive or automated buy/sell recommendations
 */

import { apiFetch } from './client';
import { loadAllTheses, ResearchThesisRecord } from '@/lib/utils/thesisTracking';

export type MonitoringEventClassification =
  | 'INVESTMENT_EVIDENCE_CHANGE'
  | 'PERFORMANCE_CHANGE'
  | 'PORTFOLIO_EXPOSURE_CHANGE'
  | 'HOLDINGS_DISCLOSURE_CHANGE'
  | 'DATA_FRESHNESS_CHANGE'
  | 'DATA_QUALITY_CHANGE'
  | 'SOURCE_REVISION'
  | 'METHODOLOGY_CHANGE'
  | 'COVERAGE_CHANGE'
  | 'THESIS_CONDITION_TRIGGERED';

export type MonitoringSeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH';

export type MonitoringLifecycleStatus =
  | 'NEW'
  | 'ACKNOWLEDGED'
  | 'INVESTIGATING'
  | 'RESOLVED'
  | 'DISMISSED';

export type MonitoringExecutionStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'
  | 'SKIPPED';

export interface MonitoringExecutionDto {
  id: number;
  investorId?: number | null;
  executionTrigger: 'SCHEDULED' | 'INVESTOR_INITIATED';
  executionStatus: MonitoringExecutionStatus;
  startedAt: string;
  completedAt?: string | null;
  knowledgeCutoff?: string | null;
  fundsEvaluatedCount: number;
  portfolioScopesEvaluatedCount: number;
  conditionsEvaluatedCount: number;
  eventsGeneratedCount: number;
  eventsDeduplicatedCount: number;
  unavailableEvidenceCount: number;
  staleSourceCount: number;
  errorMessage?: string | null;
  summaryNotes?: string | null;
  createdAt: string;
}

export interface MonitoringMetricConfigDto {
  metricCode: string;
  metricName: string;
  category: string;
  unit: string;
  scaleFactor: number;
  isNegativeConvention: boolean;
  defaultOperator: string;
  supportedOperators: string[];
  candidateThresholdNumeric?: number | null;
  candidateThresholdDisplay?: string | null;
  guidance?: string | null;
  governanceStatus?: string | null;
}

export interface MonitoringOverviewDto {
  totalActiveEvents: number;
  highSeverityCount: number;
  mediumSeverityCount: number;
  lowSeverityCount: number;
  infoSeverityCount: number;
  awaitingAcknowledgementCount: number;
  investigatingCount: number;
  resolvedCount: number;
  dismissedCount: number;
  triggeredThesisConditionsCount: number;
  dataFreshnessWarningsCount: number;
  monitoredFundsCount: number;
  monitoredPortfolioPositionsCount: number;
  lastMonitoringEvaluationTime?: string | null;
  latestExecutionStatus?: string | null;
  nextScheduledEvaluation?: string | null;
  unavailableEvidenceCount?: number;
  staleSourceCount?: number;
  epistemicDisclaimer: string;
}

export interface MonitoringEventNoteDto {
  id: number;
  eventId: number;
  noteText: string;
  author: string;
  createdAt: string;
}

export interface MonitoringEventDto {
  id: number;
  investorId?: number | null;
  schemeId?: number | null;
  schemeOptionId?: number | null;
  schemeCode?: string | null;
  schemeName?: string | null;
  planType?: string | null;
  optionType?: string | null;
  portfolioScope?: string | null;
  eventClassification: MonitoringEventClassification;
  severity: MonitoringSeverity;
  lifecycleStatus: MonitoringLifecycleStatus;
  metricOrEvidenceId: string;
  metricName: string;
  previousValue?: string | null;
  currentValue?: string | null;
  changeMagnitude?: number | null;
  unitOrFormat?: string | null;
  thresholdClassification?: string | null;
  evidenceState?: string | null;
  effectiveDate?: string | null;
  availabilityTime?: string | null;
  ingestionTimestamp?: string | null;
  sourceRevision?: string | null;
  previousObservationDate?: string | null;
  currentObservationDate?: string | null;
  knowledgeCutoff?: string | null;
  sourceReference?: string | null;
  calculationRunId?: number | null;
  executionId?: number | null;
  metricRegistryVersion?: string | null;
  methodologyVersion?: string | null;
  evidenceCoverageInfo?: string | null;
  explanation: string;
  triggerRationale?: string | null;
  knownLimitations?: string | null;
  isDataArtifact: boolean;
  artifactExplanation?: string | null;
  dedupHash?: string | null;
  associatedThesisKey?: string | null;
  associatedConditionId?: number | null;
  associatedFollowUpId?: string | null;
  investigationNotes?: string | null;
  acknowledgedAt?: string | null;
  investigatingAt?: string | null;
  resolvedAt?: string | null;
  dismissedAt?: string | null;
  resolutionNote?: string | null;
  createdAt: string;
  updatedAt: string;
  notes: MonitoringEventNoteDto[];
}

export interface MonitoringConditionDto {
  id: number;
  investorId?: number | null;
  schemeId?: number | null;
  schemeOptionId?: number | null;
  schemeCode?: string | null;
  schemeName?: string | null;
  targetScope: 'SCHEME_OPTION' | 'PORTFOLIO';
  conditionName: string;
  evidenceField: string;
  operator: 'GT' | 'GTE' | 'LT' | 'LTE' | 'ABS_CHANGE_GT' | 'DATA_UNAVAILABLE' | 'EVIDENCE_STALE';
  thresholdValue?: string | null;
  thresholdNumeric?: number | null;
  thresholdUnit?: string | null;
  thresholdClassification?: string | null;
  timeWindow?: string | null;
  associatedThesisKey?: string | null;
  thesisReconsiderationGuidance?: string | null;
  isEnabled: boolean;
  conditionStatus?: string;
  evaluationState: 'TRIGGERED' | 'CLEAR' | 'NOT_EVALUATED' | 'DATA_UNAVAILABLE' | 'REQUIRES_REVIEW';
  evidenceState?: 'AVAILABLE' | 'STALE' | 'MISSING' | 'INSUFFICIENT_HISTORY' | 'DATA_QUALITY_LIMITED' | 'UNRESOLVED' | 'METHODOLOGY_INCOMPATIBLE' | null;
  lastEvaluatedValue?: string | null;
  lastEvaluatedAt?: string | null;
  lastObservationDate?: string | null;
  triggerCount?: number | null;
  lastTriggeredAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MonitoringConditionRequest {
  schemeId?: number | null;
  schemeOptionId?: number | null;
  targetScope?: 'SCHEME_OPTION' | 'PORTFOLIO';
  conditionName: string;
  evidenceField: string;
  operator: string;
  thresholdValue?: string | null;
  thresholdNumeric?: number | null;
  thresholdUnit?: string | null;
  thresholdClassification?: string | null;
  timeWindow?: string | null;
  associatedThesisKey?: string | null;
  thesisReconsiderationGuidance?: string | null;
  isEnabled?: boolean;
}

export interface MonitoringLifecycleUpdateRequest {
  lifecycleStatus: MonitoringLifecycleStatus;
  resolutionNote?: string | null;
  investigationNote?: string | null;
}

export interface MonitoringEvaluationResponseDto {
  evaluatedFundsCount: number;
  evaluatedPortfolioPositionsCount: number;
  evaluatedConditionsCount: number;
  newEventsDetectedCount: number;
  totalActiveEventsCount: number;
  evaluationTimestamp: string;
  evaluationSummaryNotes: string[];
}

export interface MonitoringAiExplanationDto {
  eventId: number;
  eventClassification: string;
  metricName: string;
  plainLanguageExplanation: string;
  historicalEvidenceSummary: string;
  possibleImplications: string[];
  conflictingEvidenceOrCaveats: string[];
  missingInformationOrLimitations: string;
  specificInvestigationQuestions: string[];
  isAiGenerated: boolean;
  providerModel: string;
  generatedAt: string;
  governanceDisclaimer: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface MonitoringFilterParams {
  schemeId?: number;
  schemeOptionId?: number;
  classification?: string;
  severity?: string;
  status?: string;
  scope?: string;
  page?: number;
  size?: number;
}

export const CLASSIFICATION_META: Record<
  MonitoringEventClassification,
  { label: string; badgeClass: string; description: string }
> = {
  INVESTMENT_EVIDENCE_CHANGE: {
    label: 'Investment Evidence',
    badgeClass: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
    description: 'Material shift in quantitative risk, return, or fundamental metrics.',
  },
  PERFORMANCE_CHANGE: {
    label: 'Performance Drift',
    badgeClass: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
    description: 'Significant change in rolling CAGR, benchmark-relative return, or drawdown.',
  },
  PORTFOLIO_EXPOSURE_CHANGE: {
    label: 'Portfolio Exposure',
    badgeClass: 'bg-purple-500/15 text-purple-300 border-purple-500/30',
    description: 'Consolidated security concentration, sector tilt, or asset allocation drift.',
  },
  HOLDINGS_DISCLOSURE_CHANGE: {
    label: 'Holdings Disclosure',
    badgeClass: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
    description: 'Monthly statutory disclosure changes, top-10 concentration shift, or new exits.',
  },
  DATA_FRESHNESS_CHANGE: {
    label: 'Data Freshness Warning',
    badgeClass: 'bg-orange-500/15 text-orange-300 border-orange-500/30',
    description: 'Observation age exceeds daily NAV cadence or monthly statutory disclosure window.',
  },
  DATA_QUALITY_CHANGE: {
    label: 'Data Quality Limitation',
    badgeClass: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
    description: 'Unresolved holdings weights, unvalued positions, or source discrepancy.',
  },
  SOURCE_REVISION: {
    label: 'Source Revision',
    badgeClass: 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30',
    description: 'Provider retroactively revised published observations.',
  },
  METHODOLOGY_CHANGE: {
    label: 'Methodology Governance',
    badgeClass: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30',
    description: 'Metric calculation methodology or benchmark specification updated.',
  },
  COVERAGE_CHANGE: {
    label: 'Coverage Change',
    badgeClass: 'bg-yellow-500/15 text-yellow-300 border-yellow-500/30',
    description: 'Disclosed holdings coverage expanded or contracted.',
  },
  THESIS_CONDITION_TRIGGERED: {
    label: 'Thesis Triggered',
    badgeClass: 'bg-red-500/20 text-red-300 border-red-500/50',
    description: 'Investor-authored invalidation criteria breached; thesis reassessment required.',
  },
};

export const SEVERITY_META: Record<
  MonitoringSeverity,
  { label: string; badgeClass: string; dotClass: string }
> = {
  HIGH: {
    label: 'HIGH',
    badgeClass: 'bg-red-500/20 text-red-300 border-red-500/40',
    dotClass: 'bg-red-400',
  },
  MEDIUM: {
    label: 'MEDIUM',
    badgeClass: 'bg-amber-500/20 text-amber-300 border-amber-500/40',
    dotClass: 'bg-amber-400',
  },
  LOW: {
    label: 'LOW',
    badgeClass: 'bg-sky-500/20 text-sky-300 border-sky-500/40',
    dotClass: 'bg-sky-400',
  },
  INFO: {
    label: 'INFO',
    badgeClass: 'bg-slate-500/20 text-slate-300 border-slate-500/40',
    dotClass: 'bg-slate-400',
  },
};

export const LIFECYCLE_META: Record<
  MonitoringLifecycleStatus,
  { label: string; badgeClass: string }
> = {
  NEW: {
    label: 'New',
    badgeClass: 'bg-amber-500/20 text-amber-300 border-amber-500/40',
  },
  ACKNOWLEDGED: {
    label: 'Acknowledged',
    badgeClass: 'bg-blue-500/20 text-blue-300 border-blue-500/40',
  },
  INVESTIGATING: {
    label: 'Investigating',
    badgeClass: 'bg-purple-500/20 text-purple-300 border-purple-500/40',
  },
  RESOLVED: {
    label: 'Resolved',
    badgeClass: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40',
  },
  DISMISSED: {
    label: 'Dismissed',
    badgeClass: 'bg-slate-500/20 text-slate-400 border-slate-600/40',
  },
};

// API Functions
export async function getMonitoringOverview(): Promise<MonitoringOverviewDto> {
  return apiFetch<MonitoringOverviewDto>('/api/v1/monitoring/overview');
}

export async function getLatestMonitoringExecution(): Promise<MonitoringExecutionDto | null> {
  try {
    return await apiFetch<MonitoringExecutionDto>('/api/v1/monitoring/execution/latest');
  } catch {
    return null;
  }
}

export async function getMonitoringExecutionHistory(
  page: number = 0,
  size: number = 10
): Promise<PageResponse<MonitoringExecutionDto>> {
  return apiFetch<PageResponse<MonitoringExecutionDto>>(
    `/api/v1/monitoring/execution/history?page=${page}&size=${size}`
  );
}

export async function getMonitoringMetrics(): Promise<MonitoringMetricConfigDto[]> {
  return apiFetch<MonitoringMetricConfigDto[]>('/api/v1/monitoring/metrics');
}

export async function getMonitoringEvents(
  params: MonitoringFilterParams = {}
): Promise<PageResponse<MonitoringEventDto>> {
  const query = new URLSearchParams();
  if (params.schemeId) query.set('schemeId', String(params.schemeId));
  if (params.schemeOptionId) query.set('schemeOptionId', String(params.schemeOptionId));
  if (params.classification) query.set('classification', params.classification);
  if (params.severity) query.set('severity', params.severity);
  if (params.status) query.set('status', params.status);
  if (params.scope) query.set('scope', params.scope);
  if (params.page !== undefined) query.set('page', String(params.page));
  if (params.size !== undefined) query.set('size', String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<MonitoringEventDto>>(`/api/v1/monitoring/events${qs ? `?${qs}` : ''}`);
}

export async function getMonitoringEventDetail(id: number): Promise<MonitoringEventDto> {
  return apiFetch<MonitoringEventDto>(`/api/v1/monitoring/events/${id}`);
}

export async function updateEventLifecycle(
  id: number,
  request: MonitoringLifecycleUpdateRequest
): Promise<MonitoringEventDto> {
  return apiFetch<MonitoringEventDto>(`/api/v1/monitoring/events/${id}/lifecycle`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function addEventNote(
  id: number,
  noteText: string
): Promise<MonitoringEventNoteDto> {
  return apiFetch<MonitoringEventNoteDto>(`/api/v1/monitoring/events/${id}/notes`, {
    method: 'POST',
    body: JSON.stringify({ noteText }),
  });
}

export async function runMonitoringScan(): Promise<MonitoringEvaluationResponseDto> {
  return apiFetch<MonitoringEvaluationResponseDto>('/api/v1/monitoring/evaluate', {
    method: 'POST',
  });
}

export async function getMonitoringConditions(): Promise<MonitoringConditionDto[]> {
  return apiFetch<MonitoringConditionDto[]>('/api/v1/monitoring/conditions');
}

export async function createMonitoringCondition(
  request: MonitoringConditionRequest
): Promise<MonitoringConditionDto> {
  return apiFetch<MonitoringConditionDto>('/api/v1/monitoring/conditions', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function updateMonitoringCondition(
  id: number,
  request: MonitoringConditionRequest
): Promise<MonitoringConditionDto> {
  return apiFetch<MonitoringConditionDto>(`/api/v1/monitoring/conditions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

export async function deleteMonitoringCondition(id: number): Promise<void> {
  await apiFetch<void>(`/api/v1/monitoring/conditions/${id}`, {
    method: 'DELETE',
  });
}

export async function toggleMonitoringCondition(id: number): Promise<MonitoringConditionDto> {
  return apiFetch<MonitoringConditionDto>(`/api/v1/monitoring/conditions/${id}/toggle`, {
    method: 'POST',
  });
}

export async function getFundMonitoringEvents(schemeId: number): Promise<MonitoringEventDto[]> {
  return apiFetch<MonitoringEventDto[]>(`/api/v1/monitoring/funds/${schemeId}/events`);
}

export async function getPortfolioMonitoringEvents(): Promise<MonitoringEventDto[]> {
  return apiFetch<MonitoringEventDto[]>('/api/v1/monitoring/portfolio/events');
}

export async function getEventAiExplanation(id: number): Promise<MonitoringAiExplanationDto> {
  return apiFetch<MonitoringAiExplanationDto>(`/api/v1/monitoring/events/${id}/explain`, {
    method: 'POST',
  });
}

export interface MonitoringConditionHistoryDto {
  id: number;
  conditionId: number;
  evaluatedAt: string;
  observationCutoff?: string | null;
  evaluationOutcome: string;
  isTriggered: boolean;
  withholdingReason?: string | null;
  withholdingReasonsJson?: string | null;
  sourceContext?: string | null;
  monitoringEventId?: number | null;
  createdAt: string;
}

export interface InAppNotificationDto {
  id: number;
  title: string;
  summary: string;
  severity: string;
  targetType: string;
  targetId?: string | null;
  actionUrl?: string | null;
  isRead: boolean;
  readAt?: string | null;
  createdAt: string;
}

export interface NotificationPreferenceDto {
  id: number;
  inAppEnabled: boolean;
  emailEnabled: boolean;
  emailAddress?: string | null;
  webhookEnabled: boolean;
  minSeverity: string;
  notifyOnTriggered: boolean;
  notifyOnDataQualityIssue: boolean;
  notifyOnStaleSource: boolean;
  updatedAt: string;
}

export interface WebhookConfigDto {
  id: number;
  webhookUrl: string;
  webhookEventTypes: string;
  status: string;
  failureCount: number;
  lastDeliveredAt?: string | null;
  lastFailureReason?: string | null;
  createdAt: string;
}

export async function getConditionHistory(conditionId: number): Promise<MonitoringConditionHistoryDto[]> {
  return apiFetch<MonitoringConditionHistoryDto[]>(`/api/v1/monitoring/conditions/${conditionId}/history`);
}

export async function fetchInAppNotifications(): Promise<InAppNotificationDto[]> {
  return apiFetch<InAppNotificationDto[]>('/api/v1/investors/notifications/in-app');
}

export async function markNotificationRead(id: number): Promise<void> {
  await apiFetch(`/api/v1/investors/notifications/in-app/${id}/read`, { method: 'POST' });
}

export async function fetchNotificationPreferences(): Promise<NotificationPreferenceDto> {
  return apiFetch<NotificationPreferenceDto>('/api/v1/investors/notifications/preferences');
}

export async function updateNotificationPreferences(
  prefs: Partial<NotificationPreferenceDto>
): Promise<NotificationPreferenceDto> {
  return apiFetch<NotificationPreferenceDto>('/api/v1/investors/notifications/preferences', {
    method: 'PUT',
    body: JSON.stringify(prefs),
  });
}

export async function fetchWebhooks(): Promise<WebhookConfigDto[]> {
  return apiFetch<WebhookConfigDto[]>('/api/v1/investors/notifications/webhooks');
}

export async function registerWebhook(data: {
  webhookUrl: string;
  webhookEventTypes?: string;
}): Promise<WebhookConfigDto> {
  return apiFetch<WebhookConfigDto>('/api/v1/investors/notifications/webhooks', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function deleteWebhook(id: number): Promise<void> {
  await apiFetch(`/api/v1/investors/notifications/webhooks/${id}`, { method: 'DELETE' });
}

export async function rotateWebhookSecret(id: number): Promise<{ secret: string }> {
  return apiFetch<{ secret: string }>(`/api/v1/investors/notifications/webhooks/${id}/rotate-secret`, {
    method: 'POST',
  });
}

/**
 * Synchronizes local research thesis reconsideration conditions into backend monitoring conditions.
 */
export async function syncThesesToMonitoringConditions(): Promise<number> {
  const allTheses = loadAllTheses();
  const existingConditions = await getMonitoringConditions().catch(() => []);
  const existingThesisKeys = new Set(
    existingConditions
      .map((c) => c.associatedThesisKey)
      .filter((k): k is string => Boolean(k))
  );

  let syncedCount = 0;
  for (const key of Object.keys(allTheses)) {
    const thesis: ResearchThesisRecord = allTheses[key];
    if (thesis.reconsiderationConditions && thesis.reconsiderationConditions.trim().length > 0) {
      if (!existingThesisKeys.has(key)) {
        try {
          await createMonitoringCondition({
            schemeId: thesis.schemeId,
            schemeOptionId: thesis.schemeOptionId,
            targetScope: 'SCHEME_OPTION',
            conditionName: `Thesis Reconsideration: ${thesis.schemeName || 'Fund'}`,
            evidenceField: 'RSK-01', // Default to Volatility check or general thesis review
            operator: 'GT',
            thresholdValue: '0.22',
            thresholdNumeric: 0.22,
            thresholdUnit: 'PERCENTAGE',
            thresholdClassification: 'INVESTOR_DEFINED_THRESHOLD',
            timeWindow: '3Y',
            associatedThesisKey: key,
            thesisReconsiderationGuidance: thesis.reconsiderationConditions,
            isEnabled: true,
          });
          syncedCount++;
        } catch (err) {
          console.warn('Failed to sync thesis condition for key ' + key, err);
        }
      }
    }
  }
  return syncedCount;
}
