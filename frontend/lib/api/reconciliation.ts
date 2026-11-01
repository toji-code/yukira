/**
 * YUKIRA Quantitative Intelligence Platform
 * Tasks 260-264, 273, 274, 299, 304-309, 310-359: V1 Product Reconciliation & Operational Ledger API Client
 *
 * Epistemic Contract:
 * - Strict requirement reconciliation ledger (Tasks 210-259 & 260-309)
 * - Authoritative V1 canonical product capability model
 * - Phase 2H frozen metric validation registry and state transition guards
 * - Operational background job dependencies and provider readiness
 * - Cash-flow schedule and XIRR solver evidence contracts
 * - Authoritative statutory Indian mutual fund tax classification rules
 * - Capability matrix per scheme option
 * - Immutable versioned research thesis tracking
 */

import { apiFetch } from './client';

export type ReconciliationStatus =
  | 'IMPLEMENTED'
  | 'PARTIALLY_IMPLEMENTED'
  | 'NOT_IMPLEMENTED'
  | 'IMPLEMENTATION_UNCLEAR'
  | 'BLOCKED_BY_EXTERNAL_DEPENDENCY';

export type DiscrepancyType =
  | 'EXACT_MATCH'
  | 'TASK_NUMBER_MISMATCH'
  | 'TITLE_MISMATCH'
  | 'SCOPE_DISCREPANCY'
  | 'MISSING_FROM_REPORT'
  | 'REPORTED_WITHOUT_SOURCE_FOUNDATION';

export interface RequirementReconciliationItem {
  taskNumber: number;
  taskName: string;
  componentTier: string;
  implementationStatus: string;
  backendReference: string;
  schemaReference: string;
  frontendReference: string;
  epistemicLimitations: string;
  affectedWorkflows: string[];
}

export interface ReconstructedTaskAudit {
  originalTaskNumber: number;
  originalTitle: string;
  originalObjective: string;
  reportedClaim: string;
  actualImplementation: string;
  discrepancyType: DiscrepancyType;
  trueStatus: string;
}

export interface CanonicalProductCapability {
  capabilityId: string;
  capabilityName: string;
  originalTaskReferences: number[];
  primaryBackendService: string;
  primaryRepository: string;
  databaseTablesAndMigrations: string[];
  restEndpoints: string[];
  frontendComponentsAndRoutes: string[];
  implementationState: string;
  validationStatus: 'APPROVED' | 'VALIDATED' | 'CANDIDATE' | 'UNIMPLEMENTED';
  dataDependencies: string[];
  knownLimitations: string;
}

export interface ImplementationGapItem {
  gapCategory: string;
  gapIdentifier: string;
  description: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  remediationStrategy: string;
  targetedTask: string;
}

export interface HistoricalTestBaseline {
  component: string;
  historicalPassingCount: number;
  historicalBaselineDate: string;
  historicalBaselineCommit: string;
  currentBatchStatus: string;
  executionPolicyNote: string;
}

export interface DependencyRegisterItem {
  capabilityKey: string;
  capabilityName: string;
  governingStatus: 'APPROVED' | 'VALIDATED' | 'CANDIDATE';
  requiredDataSources: string[];
  fallbackRules: string[];
  isEndToEndConnected: boolean;
  verificationSummary: string;
}

export interface Phase2HMetricDefinition {
  metricCode: string;
  metricName: string;
  category: string;
  mathematicalFormula: string;
  governanceStatus: 'APPROVED' | 'VALIDATED' | 'CANDIDATE' | 'RETIRED';
  unit: string;
  minimumObservations: number;
  annualizationBasis?: number | null;
  numericalTolerance: number;
  epistemicNote: string;
}

export interface MetricTransitionRequest {
  metricCode: string;
  targetStatus: string;
  reviewerIdentity: string;
  rationale: string;
  referenceDatasetRef?: string;
  independentMethodology?: string;
  numericalTolerance?: number;
}

export interface MetricTransitionResult {
  metricCode: string;
  previousStatus: string;
  newStatus: string;
  success: boolean;
  message: string;
  evidenceId?: number | null;
}

export interface OperationalJobSummary {
  id: number;
  jobIdentifier: string;
  jobName: string;
  executionTrigger: string;
  scheduleCron: string;
  requiredConfigurationKeys: string;
  externalProviderRef: string;
  providerConfigStatus: string;
  isOperational: boolean;
  lastKnownHealthStatus: string;
}

export interface ExternalProviderReadiness {
  providerIdentifier: string;
  providerName: string;
  readinessStatus: 'OPERATIONAL' | 'CONFIGURATION_PENDING' | 'EXTERNAL_FEED_PENDING';
  primaryEndpointOrHost: string;
  requiredEnvKeys: string[];
  epistemicNote: string;
}

export interface XirrCashFlowItem {
  cashFlowDate: string;
  amount: number;
  cashFlowType: 'PURCHASE' | 'REDEMPTION' | 'TERMINAL_VALUATION';
  transactionReference: string;
  isReconciled: boolean;
}

export interface XirrSolverContract {
  investorId: number;
  openingDate: string;
  openingValuation: number;
  closingDate: string;
  closingValuation: number;
  cashFlowSchedule: XirrCashFlowItem[];
  calculatedXirrPercent?: number | null;
  solverOutcome: 'CONVERGED' | 'DID_NOT_CONVERGE' | 'INSUFFICIENT_CASH_FLOWS';
  solverIterations: number;
  hasUnreconciledTransactions: boolean;
  calculationEvidenceNotice: string;
}

export interface IndianMutualFundTaxRule {
  id: number;
  statutorySection: string;
  assetClass: string;
  minHoldingDays: number;
  maxHoldingDays?: number | null;
  taxRatePercent?: number | null;
  statutoryExemptionLimit?: number | null;
  indexationEligible: boolean;
  statutoryNotes: string;
  legalInstrumentRef?: string | null;
  financeActRef?: string | null;
  transferStartDate?: string | null;
  transferEndDate?: string | null;
  ruleVersion?: string | null;
  reviewStatus?: string | null;
}

export interface SchemeCapabilityMatrix {
  schemeOptionId: number;
  amfiCode: string;
  isin: string;
  schemeName: string;
  ret02Ready: boolean;
  ret03Ready: boolean;
  rsk01Ready: boolean;
  mkt01Ready: boolean;
  overlapReady: boolean;
  holdingsReady: boolean;
  earliestNavDate?: string | null;
  latestNavDate?: string | null;
  totalObservations: number;
  completenessRatio: number;
  readinessNotes: string[];
  unavailableReasonCodes?: string[];
}

export interface ResearchThesisVersion {
  id: number;
  investorId: number;
  schemeId: number;
  schemeOptionId?: number | null;
  thesisId: string;
  versionNumber: number;
  author: string;
  thesisTitle: string;
  thesisBody: string;
  linkedCondition?: string | null;
  linkedEvidence?: string | null;
  revisionReason?: string | null;
  createdAt: string;
}

export interface ThesisCreationRequest {
  investorId: number;
  schemeId: number;
  schemeOptionId?: number | null;
  thesisId: string;
  author: string;
  title: string;
  body: string;
  linkedCondition?: string | null;
  linkedEvidence?: string | null;
  revisionReason?: string | null;
}

export interface MonitoringEvaluationRun {
  id: number;
  runIdentifier: string;
  evaluationCutoff: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  evaluatedRulesCount: number;
  eventsTriggeredCount: number;
  executionDurationMs: number;
  executedAt: string;
  failureReason?: string | null;
}

export interface NotificationDelivery {
  id: number;
  eventId: number;
  channel: 'IN_APP' | 'EMAIL' | 'WEBHOOK';
  recipientTarget: string;
  status: 'QUEUED' | 'ATTEMPTED' | 'ACCEPTED' | 'FAILED' | 'CONFIGURATION_REQUIRED';
  attemptCount: number;
  queuedAt: string;
  lastAttemptAt?: string | null;
  deliveredAt?: string | null;
  failureReason?: string | null;
}

export interface SourceCalendarExecutionLog {
  id: number;
  sourceKey: string;
  executionType: string;
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'SKIPPED';
  recordsIngested: number;
  recordsRejected: number;
  executionDurationMs: number;
  executedAt: string;
  notes?: string | null;
}

// API Methods
export async function getReconciliationLedger(): Promise<RequirementReconciliationItem[]> {
  return apiFetch<RequirementReconciliationItem[]>('/api/v1/reconciliation/ledger');
}

export async function getReconstructedTasksLedger(): Promise<ReconstructedTaskAudit[]> {
  return apiFetch<ReconstructedTaskAudit[]>('/api/v1/reconciliation/reconstructed-tasks');
}

export async function getCapabilities(): Promise<CanonicalProductCapability[]> {
  return apiFetch<CanonicalProductCapability[]>('/api/v1/reconciliation/capabilities');
}

export async function getImplementationGaps(): Promise<ImplementationGapItem[]> {
  return apiFetch<ImplementationGapItem[]>('/api/v1/reconciliation/gaps');
}

export async function getHistoricalTestBaselines(): Promise<HistoricalTestBaseline[]> {
  return apiFetch<HistoricalTestBaseline[]>('/api/v1/reconciliation/test-baselines');
}

export async function getDependencyRegister(): Promise<DependencyRegisterItem[]> {
  return apiFetch<DependencyRegisterItem[]>('/api/v1/reconciliation/dependencies');
}

export async function getPhase2HMetricRegistry(): Promise<Phase2HMetricDefinition[]> {
  return apiFetch<Phase2HMetricDefinition[]>('/api/v1/reconciliation/metrics/registry');
}

export async function transitionMetricGovernance(request: MetricTransitionRequest): Promise<MetricTransitionResult> {
  return apiFetch<MetricTransitionResult>('/api/v1/reconciliation/metrics/transition', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function getOperationalJobs(): Promise<OperationalJobSummary[]> {
  return apiFetch<OperationalJobSummary[]>('/api/v1/reconciliation/operational/jobs');
}

export async function getProviderReadiness(): Promise<ExternalProviderReadiness[]> {
  return apiFetch<ExternalProviderReadiness[]>('/api/v1/reconciliation/operational/readiness');
}

export async function getPortfolioXirrContract(investorId: number, asOfDate?: string): Promise<XirrSolverContract> {
  const query = asOfDate ? `?asOfDate=${encodeURIComponent(asOfDate)}` : '';
  return apiFetch<XirrSolverContract>(`/api/v1/reconciliation/portfolio/${investorId}/xirr-contract${query}`);
}

export async function getTaxRules(): Promise<IndianMutualFundTaxRule[]> {
  return apiFetch<IndianMutualFundTaxRule[]>('/api/v1/reconciliation/tax-rules');
}

export async function getCapabilityMatrix(schemeOptionId: number): Promise<SchemeCapabilityMatrix> {
  return apiFetch<SchemeCapabilityMatrix>(`/api/v1/reconciliation/coverage-matrix/${schemeOptionId}`);
}

export async function getInvestorTheses(investorId: number): Promise<ResearchThesisVersion[]> {
  return apiFetch<ResearchThesisVersion[]>(`/api/v1/reconciliation/theses/${investorId}`);
}

export async function getThesisHistory(investorId: number, thesisId: string): Promise<ResearchThesisVersion[]> {
  return apiFetch<ResearchThesisVersion[]>(`/api/v1/reconciliation/theses/${investorId}/${thesisId}`);
}

export async function saveThesisVersion(request: ThesisCreationRequest): Promise<ResearchThesisVersion> {
  return apiFetch<ResearchThesisVersion>('/api/v1/reconciliation/theses', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function getMonitoringRuns(limit = 10): Promise<MonitoringEvaluationRun[]> {
  return apiFetch<MonitoringEvaluationRun[]>(`/api/v1/reconciliation/monitoring/runs?limit=${limit}`);
}

export async function getNotificationOutbox(limit = 20): Promise<NotificationDelivery[]> {
  return apiFetch<NotificationDelivery[]>(`/api/v1/reconciliation/monitoring/outbox?limit=${limit}`);
}

export async function dispatchNotifications(batchSize = 10): Promise<NotificationDelivery[]> {
  return apiFetch<NotificationDelivery[]>(`/api/v1/reconciliation/monitoring/dispatch?batchSize=${batchSize}`, {
    method: 'POST',
  });
}

export async function getCalendarLogs(limit = 20): Promise<SourceCalendarExecutionLog[]> {
  return apiFetch<SourceCalendarExecutionLog[]>(`/api/v1/reconciliation/calendar/logs?limit=${limit}`);
}

// ---------------------------------------------------------------------------
// Tasks 360-409: Operational Truth, PIT, Benchmark & Evidence Linkage Contracts
// ---------------------------------------------------------------------------

export interface Task360To409AuditItem {
  taskNumber: number;
  taskTitle: string;
  phaseCategory: string;
  backendArtifacts: string[];
  databaseArtifacts: string[];
  frontendArtifacts: string[];
  truthState: string;
  governanceContract: string;
}

export interface OperationalStatusReport {
  generatedAt: string;
  totalJobs: number;
  stateCounts: Record<string, number>;
  activeJobs: Array<{
    jobKey: string;
    jobName: string;
    capabilityState: string;
    schedulerState: string;
    executionLockState: string;
    lastExecutionOutcome: string;
    lastExecutedAt?: string;
    requiresManualApproval: boolean;
  }>;
  recentExecutions: Array<{
    jobKey: string;
    triggerSource: string;
    executionOutcome: string;
    durationMs: number;
    completedAt: string;
    errorSummary?: string;
    operatorNotes?: string;
  }>;
}

export interface InvestorSafeDisclosure {
  disclosureTimestamp: string;
  platformOperatingState: string;
  isDegraded: boolean;
  activeDisclaimers: string[];
  unconfiguredProviders: string[];
  pendingDependencies: string[];
}

export interface BenchmarkCoverageItem {
  id?: number;
  benchmarkCode: string;
  benchmarkName: string;
  indexType?: string;
  assignedSchemeUniverseCount?: number;
  availableStartDate?: string;
  availableEndDate?: string;
  earliestDate?: string;
  latestDate?: string;
  totalObservations: number;
  missingIntervalsCount?: number;
  sourceArtifactCoverageRatio?: number;
  status: string;
  ingestionState?: string;
  lastSyncedAt?: string;
  operationalDisclosure?: string;
  declaredStartDate?: string;
  declaredEndDate?: string;
  declaredObservationCount?: number;
  actualObservationCount?: number;
  actualStartDate?: string;
  actualEndDate?: string;
  coverageReconciliationStatus?: string;
  sourceArtifactLineageStatus?: string;
}

export interface SchemeBenchmarkMappingInterval {
  schemeId: number;
  benchmarkCode: string;
  validFrom: string;
  validTo?: string;
  statutoryCircularRef: string;
  justificationNotes: string;
}

export interface SchemeCoverageMatrix10d {
  schemeOptionId: number;
  isin: string;
  amfiCode: string;
  dimensionStates: Record<string, string>;
  isFullyEligibleForAdvancedAnalytics: boolean;
  auditNotes: string;
}

export interface EndToEndEvidenceChain {
  instrumentIdentity: {
    schemeOptionId: number;
    schemeName: string;
    isin: string;
    amfiCode: string;
    planType: string;
    optionType: string;
    amcName: string;
  };
  sourceObservation: {
    observationId?: number;
    effectiveDate?: string;
    availabilityTime?: string;
    revisionSeq?: number;
    navValue?: number;
    observationStatus: string;
  };
  sourceArtifact: {
    sha256Hash: string;
    artifactId?: number;
    retrievalOrigin: string;
    casStoragePath: string;
    publisherIdentity: string;
    authenticityStatus: string;
  };
  deterministicCalculation: {
    metricCode: string;
    metricName: string;
    mathematicalFormula: string;
    inputParameters: Record<string, unknown>;
    executionTimestamp: string;
    numericalResult: number;
    formattedResult: string;
    governanceStatus: string;
  };
  epistemicAssessment: {
    qualityState: string;
    verificationState: string;
    revisionState: string;
    freshnessState: string;
    presenceState: string;
    integrityState: string;
  };
  aiInterpretationClaim: {
    claimText: string;
    citedEvidenceToken: string;
    groundingSpan: string;
    abstentionReason: string;
    promptTemplateVersion: string;
  };
  verificationChainFingerprint: string;
  auditTrailStatus: string;
}

export async function getTasks360To409Ledger(): Promise<Task360To409AuditItem[]> {
  return apiFetch<Task360To409AuditItem[]>('/api/v1/reconciliation/tasks-360-409');
}

export async function getOperationalStatusReport(): Promise<OperationalStatusReport> {
  return apiFetch<OperationalStatusReport>('/api/v1/reconciliation/operational/status-report');
}

export async function getInvestorSafeDisclosure(): Promise<InvestorSafeDisclosure> {
  return apiFetch<InvestorSafeDisclosure>('/api/v1/reconciliation/operational/investor-disclosure');
}

export async function getBenchmarkCoverage(): Promise<BenchmarkCoverageItem[]> {
  return apiFetch<BenchmarkCoverageItem[]>('/api/v1/reconciliation/benchmark/coverage');
}

export async function getSchemeBenchmarkMappingHistory(schemeId: number): Promise<SchemeBenchmarkMappingInterval[]> {
  return apiFetch<SchemeBenchmarkMappingInterval[]>(`/api/v1/reconciliation/benchmark/mapping-history/${schemeId}`);
}

export async function getScheme10DCoverage(schemeOptionId: number): Promise<SchemeCoverageMatrix10d> {
  return apiFetch<SchemeCoverageMatrix10d>(`/api/v1/reconciliation/coverage-10d/${schemeOptionId}`);
}

export async function getEndToEndEvidenceChain(schemeOptionId: number): Promise<EndToEndEvidenceChain> {
  return apiFetch<EndToEndEvidenceChain>(`/api/v1/reconciliation/evidence-chain/${schemeOptionId}`);
}

// ---------------------------------------------------------------------------
// Tasks 410-459: Implementation Reconciliation, Contradictions & Validation Readiness
// ---------------------------------------------------------------------------

export interface TaskRequirementReconciliation {
  id: number;
  taskNumber: number;
  originalTitle: string;
  originalObjective: string;
  requiredBehaviors: string;
  reportedTitle: string;
  discrepancyType: string;
  actualImplementationStatus: string;
  componentTier: string;
  evidenceClassification: string;
  primaryArtifacts: string;
  identifiedGaps: string;
  reconciledAt: string;
}

export interface HistoricalReportContradiction {
  id: number;
  contradictionKey: string;
  area: string;
  earlierBatchClaim: string;
  reconciledBatchTruth: string;
  contradictionSeverity: string;
  affectedEntities: string;
  remediationAction: string;
  epistemicImpact: string;
  reconciledAt: string;
}

export interface ValidationReadinessItem {
  id: number;
  readinessAreaCode: string;
  readinessAreaName: string;
  relevantImplementationArtifacts: string;
  requiredVerificationActivity: string;
  requiredRealDataOrSourceEvidence: string;
  expectedAcceptanceCriteria: string;
  upstreamDependencies: string;
  riskIfNotCompleted: string;
  responsibleReviewArea: string;
  validationStatus: string;
  hasValidationExecuted: boolean;
  notes?: string;
  createdAt: string;
}

export async function getTaskRequirementReconciliations(): Promise<TaskRequirementReconciliation[]> {
  return apiFetch<TaskRequirementReconciliation[]>('/api/v1/reconciliation/task-requirements');
}

export async function getHistoricalReportContradictions(): Promise<HistoricalReportContradiction[]> {
  return apiFetch<HistoricalReportContradiction[]>('/api/v1/reconciliation/contradictions');
}

export async function getValidationReadiness(): Promise<ValidationReadinessItem[]> {
  return apiFetch<ValidationReadinessItem[]>('/api/v1/reconciliation/validation-readiness');
}

export async function getValidationReadinessByArea(areaCode: string): Promise<ValidationReadinessItem> {
  return apiFetch<ValidationReadinessItem>(`/api/v1/reconciliation/validation-readiness/${encodeURIComponent(areaCode)}`);
}
