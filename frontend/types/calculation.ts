import { SchemeOption, Benchmark } from './domain';

export type RunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | string;
export type CalculationStatus = 'CALCULATED' | 'INSUFFICIENT_DATA' | 'ERROR' | string;

export interface MethodologyVersion {
  id: number;
  methodologyCode: string;
  versionTag: string;
  approvalStatus: 'CANDIDATE' | 'APPROVED' | 'DEPRECATED' | string;
  gitCommitHash: string;
  parameterConfiguration: Record<string, unknown> | string;
  effectiveFrom?: string;
}

export interface CalculationRun {
  id: number;
  schemeOption?: SchemeOption;
  schemeOptionId?: number;
  benchmark?: Benchmark;
  benchmarkId?: number;
  asOfDate: string;
  knowledgeCutoffTime: string;
  methodologyVersion?: MethodologyVersion;
  methodologyVersionId?: number;
  engineSoftwareVersion: string;
  inputSnapshotSha256: string | null;
  executionStartedAt: string;
  executionCompletedAt: string | null;
  runStatus: RunStatus;
  errorMessage: string | null;
}

export interface MetricResult {
  id: number;
  calculationRunId?: number;
  metricCode: string;
  periodType: string;
  numericValue: number | null;
  stringValue: string | null;
  units: string;
  calculationStatus: CalculationStatus;
  diagnostics: string | Record<string, unknown> | null;
  errorMessage: string | null;
}

export interface TriggerCalculationRequest {
  schemeOptionId: number;
  benchmarkId: number;
  asOfDate: string;
  knowledgeCutoffTime: string;
  metricCodes?: string[];
  methodologyTag?: string;
  parameters?: Record<string, unknown>;
}
