import { apiFetch } from './client';

export interface GoalDiscoveryRequest {
  goalCategory: string;
  horizonYears: number;
  riskTolerance: string;
  investmentMode: string;
  fundCategory?: string;
}

export interface CriterionEvaluation {
  criterion: string;
  state: 'MATCH' | 'NO_MATCH' | 'SUPPORTED' | 'UNSUPPORTED' | 'UNKNOWN' | 'AVAILABLE' | 'INSUFFICIENT' | 'NOT_APPLICABLE';
  explanation: string;
}

export interface ScoreSummary {
  available: boolean;
  scoreValue?: number | null;
  confidence: string;
  status: string;
  scoreVersion: string;
  asOfDate?: string | null;
}

export interface EvidenceState {
  navAsOfDate: string;
  enrichmentAsOfDate: string;
  dataQualitySummary: string;
  qualityFlags: string[];
}

export interface GoalDiscoveryResult {
  schemeOptionId: number;
  schemeId: number;
  fundName: string;
  amcName: string;
  category: string;
  subcategory: string;
  planType: string;
  optionType: string;
  amfiCode: string;
  isin: string;
  resultState: 'ELIGIBLE' | 'PARTIALLY_EVALUATED' | 'INSUFFICIENT_DATA' | 'NOT_ELIGIBLE';
  criteria: Record<string, CriterionEvaluation>;
  analyticalScore: ScoreSummary;
  evidenceState: EvidenceState;
  investigationQuestions: string[];
}

export interface GoalDiscoveryResponse {
  normalizedRequirements: GoalDiscoveryRequest;
  totalEvaluated: number;
  results: GoalDiscoveryResult[];
}

export async function evaluateGoalDiscovery(
  request: GoalDiscoveryRequest
): Promise<GoalDiscoveryResponse> {
  return apiFetch<GoalDiscoveryResponse>('/api/v1/discovery/goals/evaluate', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}
