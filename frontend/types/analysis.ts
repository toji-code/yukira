export interface Ret02Identity {
  schemeId: number | null;
  schemeName: string;
  amfiCode: string | null;
  schemeOptionId: number | null;
  optionType: string;
  isin: string | null;
}

export interface Ret02Result {
  metricCode: string;
  metricName: string;
  numericValue: number | null;
  formattedValue: string | null;
  units: string;
  calculationStatus: 'CALCULATED' | 'INSUFFICIENT_DATA' | 'FAILED' | string;
  errorMessage: string | null;
}

export interface Ret02Period {
  requestedStartDate: string | null;
  requestedEndDate: string | null;
  selectedStartDate: string | null;
  selectedEndDate: string | null;
  startLookbackDaysUsed: number;
  endLookbackDaysUsed: number;
  startSubstituted: boolean;
  endSubstituted: boolean;
}

export interface Ret02Pit {
  knowledgeCutoffTime: string;
  pitFilteringApplied: boolean;
  temporalLimitationDisclosure: string;
  cutoffConventionApplied: string;
  sourceAvailabilitySemantic?: string;
}

export interface Ret02Methodology {
  methodologyCode: string;
  methodologyVersion: string;
  approvalStatus: 'CANDIDATE' | 'APPROVED' | 'DEPRECATED' | string;
  isCandidate: boolean;
  lookbackSpecification: string;
  formulaDisclosure: string;
}

export interface Ret02QualityDimension {
  dimension: string;
  state: string;
  description: string;
}

export interface Ret02Quality {
  overallAssessment: string;
  dimensions: Ret02QualityDimension[];
  validationFlags: string[];
}

export interface Ret02InputObservationRef {
  observationId: number;
  role: 'START' | 'END' | 'INTERMEDIATE' | string;
  effectiveDate: string;
  revisionSeq: number;
  navValue: number;
  availabilityTime: string;
  qualityAssessment: string;
  verificationStatus: string;
  revisionStatus: string;
  temporalStatus: string; // Dimension 4: Freshness (CURRENT / STALE)
  presenceStatus: string;
  integrityCondition?: string; // Dimension 6: Integrity (NONE / DUPLICATE / CONFLICTING)
  sourceAvailabilitySemantic?: string; // Separate temporal/availability semantic (HISTORICAL_BACKFILL)
  sourceArtifactId: number | null;
  sourceArtifactSha256: string | null;
}

export interface Ret02SourceArtifactSummary {
  sourceArtifactId: number;
  sourceUrl: string;
  sha256Hash: string;
  retrievalTimestamp: string;
  byteSize: number;
}

export interface Ret02Provenance {
  calculationRunId: number;
  runStatus: string;
  executionStartedAt: string;
  executionCompletedAt: string | null;
  quantEngineVersion: string;
  methodologyGitCommit: string | null;
  inputSnapshotSha256: string | null;
  inputObservations: Ret02InputObservationRef[];
  sourceArtifacts: Ret02SourceArtifactSummary[];
}

export interface Ret02Limitations {
  factualAvailabilityTimestampUnavailable: boolean;
  analyticalCutoffConvention: string;
  sourceAvailabilitySemantic?: string;
  candidateLookbackApplied: boolean;
  lookbackWindowDays: number;
  insufficientEvidence: boolean;
  disclosureSummary: string;
}

export interface Ret02Benchmark {
  benchmarkRequired: boolean;
  benchmarkId: number | null;
  benchmarkNotice: string;
}

export interface Rsk01Window {
  requestedStartDate: string | null;
  requestedEndDate: string | null;
  actualStartDate: string | null;
  actualEndDate: string | null;
  observationCount: number;
  minObservationsRequired: number;
  windowMonths: number;
}

export interface Rsk01Methodology {
  methodologyCode: string;
  methodologyVersion: string;
  approvalStatus: 'CANDIDATE' | 'APPROVED' | 'DEPRECATED' | string;
  isCandidate: boolean;
  annualizationConvention: string;
  denominatorConvention: string;
  formulaDisclosure: string;
  lookbackSpecification?: string;
}

export interface Rsk01Limitations {
  candidateAnnualizationApplied: boolean;
  candidateDenominatorApplied: boolean;
  insufficientEvidence: boolean;
  observationCount: number;
  minObservationsRequired: number;
  disclosureSummary: string;
  factualAvailabilityTimestampUnavailable?: boolean;
  analyticalCutoffConvention?: string;
  sourceAvailabilitySemantic?: string;
  candidateLookbackApplied?: boolean;
  lookbackWindowDays?: number;
}

export interface AnalysisResponse {
  identity: Ret02Identity;
  result: Ret02Result;
  period?: Ret02Period;
  window?: Rsk01Window;
  pit: Ret02Pit;
  methodology: Ret02Methodology | Rsk01Methodology;
  quality: Ret02Quality;
  provenance: Ret02Provenance;
  limitations: Ret02Limitations | Rsk01Limitations;
  benchmark: Ret02Benchmark;
}

export interface CalculationRequest {
  schemeOptionId: number;
  startDate?: string;
  endDate: string;
  knowledgeCutoffTime: string;
  methodologyTag?: string;
}

export interface ProfileContext {
  runId: number;
  schemeOptionId: number;
  schemeName: string;
  amfiCode: string;
  isin: string;
  optionType: string;
  planType: string;
  benchmarkId: number;
  benchmarkName: string;
  benchmarkCode: string;
  riskFreeProxy: string;
  startDate: string;
  asOfDate: string;
  knowledgeCutoffTime: string;
  runStatus: string;
}

export interface ProfileMetricItem {
  metricCode: string;
  metricName: string;
  category: string;
  numericValue: number | null;
  formattedValue: string | null;
  units: string;
  periodType: string;
  calculationStatus: string;
  governanceStatus: string;
  formulaDisclosure: string;
  interpretation: string;
  limitations: string;
  errorMessage?: string | null;
  diagnostics?: Record<string, unknown>;
}

export interface ProfileProvenance {
  calculationRunId: number;
  runStatus: string;
  inputSnapshotSha256: string;
  executionStartedAt: string;
  executionCompletedAt: string | null;
  engineSoftwareVersion: string;
  methodologyTag: string;
  navObservationCount: number;
  benchmarkObservationCount: number;
  riskFreeObservationCount: number;
  sampleObservations?: Ret02InputObservationRef[];
}

export interface ProfileQuality {
  overallAssessment: string;
  dimensions: Ret02QualityDimension[];
  validationFlags: string[];
}

export interface ProfileLimitations {
  analysisCutoff: string;
  knowledgeCutoff: string;
  temporalLimitationDisclosure: string;
  candidateMethodologyDisclaimer: string;
}

export interface AnalyticalProfileResponse {
  context: ProfileContext;
  returnMetrics: ProfileMetricItem[];
  riskMetrics: ProfileMetricItem[];
  riskAdjustedMetrics: ProfileMetricItem[];
  marketSensitivityMetrics: ProfileMetricItem[];
  provenance: ProfileProvenance;
  quality: ProfileQuality;
  limitations: ProfileLimitations;
}

export interface ProfileCalculationRequest {
  schemeOptionId: number;
  benchmarkId?: number;
  asOfDate: string;
  knowledgeCutoffTime: string;
  methodologyTag?: string;
  metricCodes?: string[];
  parameters?: Record<string, unknown>;
}

export interface QualityAuditContext {
  schemeOptionId: number;
  schemeName: string;
  amfiCode: string | null;
  isin: string | null;
  optionType: string;
  planType: string;
  ledgerStartDate: string | null;
  ledgerEndDate: string | null;
  knowledgeCutoffTime: string;
  evaluatedAt: string;
}

export interface QualityAuditSummary {
  overallStatus: string;
  totalObservations: number;
  validObservations: number;
  missingObservations: number;
  revisedObservations: number;
  lookbackSubstitutionsCount: number;
}

export interface QualityAuditLedgerContinuity {
  firstEffectiveDate: string | null;
  lastEffectiveDate: string | null;
  calendarDaysSpan: number;
  totalNavObservations: number;
  expectedTradingDaysEstimate: number;
  coveragePercentage: number;
  nonTradingGapsExplanation: string;
}

export interface QualityAuditTaxonomyDimension {
  dimensionName: string;
  status: string;
  description: string;
  whyItMatters: string;
  evidence: string;
}

export interface QualityAuditSourceArtifactStatus {
  artifactId: number;
  sourceUrl: string;
  sha256Hash: string;
  byteSize: number;
  ingestedAt: string;
  verificationStatus: string;
}

export interface QualityAuditAnomalyItem {
  issueId: string;
  checkCode: string;
  severity: 'DATA_LIMITATION' | 'ANOMALY' | 'NO_ISSUE' | string;
  whatYukiraSees: string;
  whyItMatters: string;
  evidence: string;
  affectedPeriod: string;
  status: string;
  limitation: string;
}

export interface QualityAuditLimitations {
  pitKnowledgeCutoff: string;
  marketHolidayConvention: string;
  disclaimer: string;
}

export interface DataQualityAuditResponse {
  context: QualityAuditContext;
  summary: QualityAuditSummary;
  ledgerContinuity: QualityAuditLedgerContinuity;
  dimensions: QualityAuditTaxonomyDimension[];
  sourceArtifactStatus: QualityAuditSourceArtifactStatus;
  detectedAnomalies: QualityAuditAnomalyItem[];
  limitations: QualityAuditLimitations;
}

export interface RollingContext {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string | null;
  isin: string | null;
  planType: string;
  optionType: string;
  benchmarkId: number;
  benchmarkName: string;
  startDate: string;
  endDate: string;
  knowledgeCutoffTime: string;
  executionCompletedAt: string;
}

export interface RollingHorizonResult {
  periodType: string;
  windowYears: number;
  minWindowsRequired: number;
  sufficientData: boolean;
  returnStatus: string;
  totalWindows: number | null;
  meanReturn: number | null;
  medianReturn: number | null;
  minReturn: number | null;
  maxReturn: number | null;
  p25Return: number | null;
  p75Return: number | null;
  stdDev: number | null;
  outperformanceStatus: string;
  pairedWindows: number | null;
  outperformingWindows: number | null;
  underperformingWindows: number | null;
  outperformancePercentage: number | null;
  meanExcessReturn: number | null;
  statusReason: string;
}

export interface RollingWindowSample {
  startDate: string;
  endDate: string;
  fundReturn: number;
  benchmarkReturn: number | null;
  outperforming: boolean | null;
}

export interface RollingEpistemic {
  observation: string;
  interpretation: string;
  limitation: string;
  dataQualityStatus: string;
  benchmarkIntegrityDisclosure: string;
  sourceArtifactSha256: string | null;
  calculationRunId: number | null;
}

export interface RollingConsistencyResponse {
  context: RollingContext;
  primary3Y: RollingHorizonResult;
  supporting1Y: RollingHorizonResult;
  sampleWindows: RollingWindowSample[];
  epistemic: RollingEpistemic;
}

export interface CaptureContext {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string;
  isin: string;
  planType: string;
  optionType: string;
  benchmarkId: number | null;
  benchmarkName: string;
  startDate: string | null;
  endDate: string | null;
  knowledgeCutoffTime: string;
  evaluatedAt: string;
}

export interface CaptureMetrics {
  upsideCaptureRatio: number | null;
  upsideStatus: string;
  upDaysCount: number;
  minUpDaysRequired: number;
  isUpSufficient: boolean;
  fundUpCumulativeReturn: number | null;
  benchUpCumulativeReturn: number | null;
  downsideCaptureRatio: number | null;
  downsideStatus: string;
  downDaysCount: number;
  minDownDaysRequired: number;
  isDownSufficient: boolean;
  fundDownCumulativeReturn: number | null;
  benchDownCumulativeReturn: number | null;
  isInverseCaptureGain: boolean;
  captureSpread: number | null;
  spreadStatus: string;
  totalPairedDays: number;
  flatDaysCount: number;
}

export interface CaptureEpistemic {
  observation: string;
  interpretation: string;
  limitation: string;
  dataQualityStatus: string;
  benchmarkLineage: string;
  sourceArtifactSha256: string;
  calculationRunId: number | null;
}

export interface CaptureRatioResponse {
  context: CaptureContext;
  metrics: CaptureMetrics;
  epistemic: CaptureEpistemic;
}

export interface TrackingContext {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string;
  isin: string;
  planType: string;
  optionType: string;
  benchmarkId: number | null;
  benchmarkName: string;
  startDate: string | null;
  endDate: string | null;
  knowledgeCutoffTime: string;
  evaluatedAt: string;
}

export interface TrackingMetrics {
  trackingErrorAnnualized: number | null;
  trackingErrorStatus: string;
  meanDailyExcessReturn: number | null;
  annualizedMeanActiveReturn: number | null;
  informationRatio: number | null;
  informationRatioStatus: string;
  pairedObservationsCount: number;
  minPairedObservationsRequired: number;
  isSufficientObservations: boolean;
  periodsPerYear: number;
  annualizationConvention: string;
  denominatorConvention: string;
  zeroTrackingError: boolean;
}

export interface TrackingEpistemic {
  observation: string;
  interpretation: string;
  limitation: string;
  dataQualityStatus: string;
  benchmarkLineage: string;
  sourceArtifactSha256: string;
  calculationRunId: number | null;
}

export interface TrackingConsistencyResponse {
  context: TrackingContext;
  metrics: TrackingMetrics;
  epistemic: TrackingEpistemic;
}

export interface BetaContext {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string;
  isin: string;
  planType: string;
  optionType: string;
  benchmarkId: number | null;
  benchmarkName: string;
  startDate: string | null;
  endDate: string | null;
  knowledgeCutoffTime: string;
  evaluatedAt: string;
}

export interface BetaMetrics {
  standardBeta: number | null;
  standardBetaStatus: string;
  downsideBeta: number | null;
  downsideBetaStatus: string;
  upsideBeta: number | null;
  upsideBetaStatus: string;
  betaAsymmetrySpread: number | null;
  asymmetryStatus: string;
  totalPairedDays: number;
  upDaysCount: number;
  downDaysCount: number;
  flatDaysCount: number;
  minPairedRequired: number;
  minDownRequired: number;
  minUpRequired: number;
  isStandardSufficient: boolean;
  isDownsideSufficient: boolean;
  isUpsideSufficient: boolean;
  riskFreeProxy: string;
  riskFreeAligned: boolean;
}

export interface BetaEpistemic {
  observation: string;
  interpretation: string;
  limitation: string;
  dataQualityStatus: string;
  benchmarkLineage: string;
  sourceArtifactSha256: string;
  calculationRunId: number | null;
}

export interface BetaDynamicsResponse {
  context: BetaContext;
  metrics: BetaMetrics;
  epistemic: BetaEpistemic;
}

export interface BenchmarkRelationshipContext {
  benchmarkName: string;
  startDate: string | null;
  endDate: string | null;
  knowledgeCutoffTime: string;
}

export interface BenchmarkRelationshipMetrics {
  correlation: number | null;
  correlationStatus: string;
  rSquared: number | null;
  rSquaredStatus: string;
  pairedObservationCount: number;
  minPairedRequired: number;
  isSufficient: boolean;
  resultState: string;
}

export interface BenchmarkRelationshipEpistemic {
  observation: string;
  interpretation: string;
  limitation: string;
  dataQualityStatus: string;
  benchmarkLineage: string;
  sourceArtifactSha256: string | null;
  calculationRunId: number | null;
}

export interface BenchmarkRelationshipResponse {
  context: BenchmarkRelationshipContext;
  metrics: BenchmarkRelationshipMetrics;
  epistemic: BenchmarkRelationshipEpistemic;
}
