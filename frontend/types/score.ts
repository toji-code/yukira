/**
 * YUKIRA Quantitative Intelligence Platform
 * Authoritative TypeScript contract for YUKIRA Analytical Quality Score (YUKIRA_SCORE_V1).
 *
 * Grounded in:
 * - backend/src/main/java/com/yukira/backend/scoring/dto/AnalyticalScoreResponse.java
 * - ScoreMethodologyConfig.java
 *
 * Epistemic Rules:
 * 1. Score represents quantitative analytical performance [0, 100].
 * 2. Confidence represents epistemic certainty / evidence completeness [0, 100].
 * 3. Confidence has strictly zero weight in the score. They must NEVER be combined.
 * 4. Missing metrics or unavailable dimensions must never be coerced to 0.
 */

export type ScoreStatus =
  | 'AVAILABLE'
  | 'PARTIAL'
  | 'INSUFFICIENT_DATA'
  | 'DATA_QUALITY_LIMITED'
  | 'NOT_APPLICABLE'
  | string;

export type MetricEligibility =
  | 'ELIGIBLE'
  | 'EXCLUDED'
  | 'INSUFFICIENT_DATA'
  | 'DATA_QUALITY_ANOMALY'
  | string;

export type MetricDirection =
  | 'HIGHER_IS_BETTER'
  | 'LOWER_IS_BETTER'
  | string;

export interface MetricContribution {
  id?: number;
  metricCode: string;
  metricName: string;
  rawValue: number | null;
  formattedRawValue: string | null;
  normalizedValue: number | null;
  direction: MetricDirection;
  weight: number;
  effectiveWeight: number;
  contribution: number;
  observationCount: number | null;
  eligibility: MetricEligibility;
  exclusionReason?: string | null;
  unit?: string | null;
  metricResultId?: number | null;
}

export interface DimensionScore {
  id?: number;
  dimension: string;
  dimensionName: string;
  score: number | null;
  weight: number;
  effectiveWeight: number;
  contribution: number;
  status: ScoreStatus;
  confidence: number | null;
  eligibleMetricCount: number;
  totalMetricCount: number;
  metricContributions: MetricContribution[];
}

export interface EvidenceConfidence {
  totalObservations: number;
  validObservations: number;
  suspiciousObservations: number;
  invalidObservations: number;
  pairedBenchmarkObservations: number;
  pairedReturnPeriods: number;
  meetsObservationThreshold: boolean;
  sourceArtifactVerified: boolean;
  pitIntegrityMaintained: boolean;
  confidenceScore: number;
  assessment: string;
  observationNotes: string;
}

export interface AnalyticalScore {
  scoreId: number;
  schemeOptionId: number;
  schemeName: string;
  amfiCode: string;
  isin: string;
  score: number | null;
  confidence: number | null;
  status: ScoreStatus;
  scoreVersion: string;
  methodologyStatus: string;
  asOfDate: string;
  knowledgeCutoffTime?: string;
  calculationRunId?: number;
  referencePopulation: string;
  summary?: string;
  disclaimer: string;
  dimensions: DimensionScore[];
  evidenceConfidence: EvidenceConfidence;
}

export interface MethodologyConfig {
  scoreVersion: string;
  methodologyStatus: string;
  referencePopulation: string;
  effectiveDate: string;
  minRequiredObservations: number;
  performanceDimensions: Record<string, { weight: number; code: string; name: string }>;
  scoredDimensionCodes?: string[];
  authorizedScoreInputMetrics?: string[];
  analyticalNonScoreMetrics?: string[];
  evidenceConfidenceWeight: number;
  evidenceConfidenceRole: string;
  mkt05CalibrationStatus?: string;
  unauthorizedMetrics: string[];
  disclaimer: string;
}

export interface CurrentScoreResponse extends Omit<Partial<AnalyticalScore>, 'score'> {
  score?: AnalyticalScore | number | null;
  isCurrent: boolean;
  dataFreshnessState: string;
  message?: string;
}

export interface AnalyticalRefreshResult {
  refreshStatus: string;
  processedFundsCount: number;
  scoresCalculatedCount: number;
  scoresCachedCount: number;
  scoresFailedCount: number;
  benchmarkDataAvailable: boolean;
  benchmarkMessage: string;
  asOfDate: string;
  knowledgeCutoffTime: string;
  scoreVersion: string;
  calculationRunId: number;
}

