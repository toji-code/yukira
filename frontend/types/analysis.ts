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
