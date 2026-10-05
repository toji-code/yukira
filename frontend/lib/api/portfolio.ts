import { apiFetch } from './client';
import { YukiraScoreSummary } from '@/types/domain';

export interface AnalyticalScoreState {
  available: boolean;
  scoreValue: number | null;
  confidence: string;
  status: string;
  methodologyVersion: string;
  asOfDate: string | null;
}

export interface HoldingRiskMetricDto {
  metricCode: string;
  metricName: string;
  numericValue: number | null;
  formattedValue: string | null;
  unit: string;
  asOfDate: string | null;
  observationCount: number | null;
  calculationStatus: string;
  availabilityState: 'AVAILABLE' | 'UNAVAILABLE' | string;
}

export interface HoldingRiskEvidenceDto {
  schemeOptionId: number;
  fundName: string;
  portfolioWeight: number | null;
  yukiraScore: number | null;
  scoreStatus: string;
  hasRiskEvidence: boolean;
  asOfDate: string | null;
  metrics: HoldingRiskMetricDto[];
}

export interface PortfolioRiskEvidenceSummaryDto {
  coverageState: 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE' | 'EMPTY' | string;
  totalHoldingCount: number;
  valuedHoldingCount: number;
  holdingsWithRiskEvidenceCount: number;
  coveredPortfolioValue: number | null;
  totalValuedPortfolioValue: number | null;
  coveredPortfolioWeight: number | null;
  volatilityCoverageCount: number;
  downsideSemideviationCoverageCount: number;
  drawdownCoverageCount: number;
  betaCoverageCount: number;
  downsideBetaCoverageCount: number;
  holdingRiskEvidences: HoldingRiskEvidenceDto[];
  investigationPrompts: string[];
  dataLimitations: string[];
}

export interface PortfolioHoldingDto {
  id: number;
  schemeOptionId: number;
  amfiCode: string;
  isin: string;
  fundName: string;
  amcName: string;
  category: string;
  subcategory: string;
  planType: string;
  optionType: string;
  units: number;
  costBasisAmount: number | null;
  investedAmount: number | null;
  navValue: number | null;
  navAsOfDate: string | null;
  valuationState: 'VALUATION_AVAILABLE' | 'VALUATION_UNAVAILABLE';
  availableValue: number | null;
  absoluteGainLoss: number | null;
  absoluteGainLossPercentage: number | null;
  analyticalScore: AnalyticalScoreState;
  dataQualityState: string;
  createdAt: string;
  yukiraScore?: YukiraScoreSummary | null;
  portfolioWeight?: number | null;
  riskEvidence?: HoldingRiskEvidenceDto | null;
}

export interface CategoryAllocationDto {
  category: string;
  totalValue: number;
  percentageShare: number;
  fundCount: number;
}

export interface AmcAllocationDto {
  amcName: string;
  totalValue: number;
  percentageShare: number;
  fundCount: number;
}

export interface ConcentrationAnalysisDto {
  topHoldingName: string | null;
  topHoldingSchemeOptionId: number | null;
  topHoldingWeight: number | null;
  top3HoldingsWeight: number | null;
  top5HoldingsWeight: number | null;
  topAmcName: string | null;
  topAmcWeight: number | null;
  topCategoryName: string | null;
  topCategoryWeight: number | null;
  concentrationState: string;
}

export interface ContributingHoldingScoreDto {
  schemeOptionId: number;
  fundName: string;
  fundScore: number;
  scoreStatus: string;
  scoreAsOfDate: string;
  portfolioWeight: number | null;
  normalizedScoreWeight: number;
  scoreContribution: number;
}

export interface ExcludedHoldingScoreDto {
  schemeOptionId: number;
  fundName: string;
  portfolioWeight: number | null;
  exclusionReason: string;
}

export interface PortfolioAnalyticalScoreDto {
  portfolioScore: number | null;
  scoreState: 'COMPLETE' | 'PARTIAL' | 'INSUFFICIENT_DATA' | 'UNAVAILABLE' | 'EMPTY' | string;
  coveredHoldingCount: number;
  totalHoldingCount: number;
  coveredPortfolioValue: number | null;
  totalValuedPortfolioValue: number | null;
  coveredPortfolioWeight: number | null;
  excludedHoldingCount: number;
  asOfDate: string | null;
  scoreVersion: string;
  methodologyStatus: string;
  contributingHoldings: ContributingHoldingScoreDto[];
  excludedHoldings: ExcludedHoldingScoreDto[];
}

export interface PortfolioSummaryDto {
  totalHoldings: number;
  valuedHoldingsCount: number;
  valuationCoverageState: 'VALUATION_COMPLETE' | 'VALUATION_PARTIAL' | 'VALUATION_UNAVAILABLE' | 'EMPTY';
  totalAvailableValue: number | null;
  totalInvestedAmount: number | null;
  totalAbsoluteGainLoss: number | null;
  totalAbsoluteGainLossPercentage: number | null;
  gainLossState: 'CALCULATED' | 'PARTIALLY_AVAILABLE' | 'NOT_AVAILABLE';
  categoryAllocations: CategoryAllocationDto[];
  amcAllocations: AmcAllocationDto[];
  scoredHoldingsCount: number;
  holdings: PortfolioHoldingDto[];
  dataQualityLimitations: string[];
  investigationQuestions: string[];
  concentrationAnalysis?: ConcentrationAnalysisDto | null;
  portfolioAnalyticalScore?: PortfolioAnalyticalScoreDto | null;
  portfolioRiskEvidence?: PortfolioRiskEvidenceSummaryDto | null;
}


export interface MetricContributionSnapshotDto {
  id: number;
  metricCode: string;
  metricName: string;
  rawValue: number | null;
  normalizedValue: number | null;
  direction: string;
  weight: number;
  contribution: number | null;
  eligibility: string;
  exclusionReason: string | null;
  unit: string | null;
}

export interface DimensionSnapshotDto {
  id: number;
  dimension: string;
  dimensionName: string;
  score: number | null;
  weight: number;
  status: string;
  confidence: number;
  eligibleMetricCount: number;
  totalMetricCount: number;
  metricContributions: MetricContributionSnapshotDto[];
}

export interface ScoreSnapshotDto {
  availabilityState: 'AVAILABLE' | 'PARTIAL' | 'INSUFFICIENT_DATA' | 'UNAVAILABLE' | 'NOT_APPLICABLE' | string;
  analyticalScoreId: number | null;
  schemeOptionId: number;
  score: number | null;
  confidence: number | null;
  status: string;
  scoreVersion: string | null;
  methodologyStatus: string | null;
  asOfDate: string | null;
  knowledgeCutoffTime: string | null;
  calculationRunId: number | null;
  referencePopulation: string | null;
  unavailableReason: string | null;
  dimensions: DimensionSnapshotDto[];
}

export interface HoldingPerformanceDto {
  valuationState: string;
  units: number;
  availableValue: number | null;
  investedAmount: number | null;
  absoluteGainLoss: number | null;
  absoluteGainLossPercentage: number | null;
  currentPortfolioWeight: number | null;
}

export interface HoldingScoreHistoryDto {
  schemeOptionId: number;
  fundName: string;
  amcName: string;
  amfiCode: string;
  isin: string;
  holdingPerformance: HoldingPerformanceDto;
  currentFundScore: ScoreSnapshotDto | null;
  selectedHistoricalFundScore: ScoreSnapshotDto;
  historicalFundScores: ScoreSnapshotDto[];
}

export interface HistoricalPortfolioScoreAvailabilityDto {
  state: 'AVAILABLE' | 'PARTIAL' | 'INSUFFICIENT_DATA' | 'UNAVAILABLE' | 'NOT_APPLICABLE' | string;
  requestedAsOfDate: string | null;
  portfolioScore: number | null;
  reason: string;
  evidenceBoundary: string;
}

export interface PortfolioScoreHistoryDto {
  requestedAsOfDate: string | null;
  selectionPolicy: string;
  currentPortfolioScore: PortfolioAnalyticalScoreDto | null;
  historicalPortfolioScore: HistoricalPortfolioScoreAvailabilityDto;
  holdings: HoldingScoreHistoryDto[];
  dataQualityLimitations: string[];
  investigationQuestions: string[];
}

export async function fetchPortfolioScoreHistory(asOfDate?: string): Promise<PortfolioScoreHistoryDto> {
  const query = asOfDate ? `?asOfDate=${encodeURIComponent(asOfDate)}` : '';
  return apiFetch<PortfolioScoreHistoryDto>(`/api/v1/investor/portfolio/score-history${query}`);
}
export async function fetchPortfolioSummary(): Promise<PortfolioSummaryDto> {
  return apiFetch<PortfolioSummaryDto>('/api/v1/portfolio');
}

export async function addPortfolioHolding(
  schemeOptionId: number,
  units: number,
  costBasisAmount?: number | null
): Promise<PortfolioHoldingDto> {
  return apiFetch<PortfolioHoldingDto>('/api/v1/portfolio/holdings', {
    method: 'POST',
    body: JSON.stringify({ schemeOptionId, units, costBasisAmount: costBasisAmount ?? null }),
  });
}

export async function addPortfolioHoldingsBulk(
  requests: { schemeOptionId: number; units: number; costBasisAmount?: number | null }[]
): Promise<PortfolioHoldingDto[]> {
  return apiFetch<PortfolioHoldingDto[]>('/api/v1/portfolio/holdings/bulk', {
    method: 'POST',
    body: JSON.stringify(requests),
  });
}

export async function removePortfolioHolding(schemeOptionId: number): Promise<void> {
  return apiFetch<void>(`/api/v1/portfolio/holdings/${schemeOptionId}`, {
    method: 'DELETE',
  });
}

export interface CriterionEvaluationDto {
  criterion: string;
  state: 'MATCH' | 'NO_MATCH' | 'UNKNOWN' | 'NOT_APPLICABLE' | 'SUPPORTED' | 'AVAILABLE' | string;
  explanation: string;
}

export interface PortfolioGoalAlignmentRequest {
  goalCategory?: string;
  horizonYears?: number;
  riskTolerance?: string;
  investmentMode?: string;
  fundCategory?: string;
}

export interface HoldingGoalAlignmentDto {
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
  units: number;
  marketValue: number | null;
  portfolioWeightPercentage: number | null;
  valuationStatus: string;
  alignmentState: 'ELIGIBLE' | 'PARTIALLY_EVALUATED' | 'NOT_ELIGIBLE' | 'INSUFFICIENT_DATA' | string;
  criteriaMap: Record<string, CriterionEvaluationDto>;
  analyticalScore: AnalyticalScoreState;
  evidenceState: {
    asOfDate: string | null;
    navDataAvailable: boolean;
    enrichmentDataAvailable: boolean;
  };
  investigationQuestions: string[];
}

export interface PortfolioGoalAlignmentDto {
  goalRequirements: PortfolioGoalAlignmentRequest;
  coverageState: 'COMPLETE' | 'PARTIAL' | 'UNVALUED' | 'NO_HOLDINGS' | string;
  totalHoldingsCount: number;
  valuedHoldingsCount: number;
  alignedHoldingsCount: number;
  partiallyEvaluatedHoldingsCount: number;
  notAlignedHoldingsCount: number;
  insufficientDataHoldingsCount: number;
  summaryTotalValue: number | null;
  coveredPortfolioValue: number | null;
  alignedPortfolioWeightPercentage: number;
  partiallyEvaluatedPortfolioWeightPercentage: number;
  notAlignedPortfolioWeightPercentage: number;
  insufficientDataPortfolioWeightPercentage: number;
  unknownExposurePercentage: number;
  holdingEvaluations: HoldingGoalAlignmentDto[];
  investigationQuestions: string[];
  limitations: string[];
}

export async function fetchPortfolioGoalAlignment(
  request?: PortfolioGoalAlignmentRequest
): Promise<PortfolioGoalAlignmentDto> {
  return apiFetch<PortfolioGoalAlignmentDto>('/api/v1/portfolio/goal-alignment', {
    method: 'POST',
    body: JSON.stringify(request ?? {}),
  });
}

export interface ReportMetadataDto {
  reportId: string;
  generationTimestamp: string;
  valuationAsOfDate: string;
  scoreAsOfDate: string;
  knowledgeCutoff: string;
  databaseEnvironment: string;
  scoreVersion: string;
  scoreMethodologyStatus: string;
  referencePopulation: string;
}

export interface DimensionDetailDto {
  dimensionCode: string;
  dimensionName: string;
  scoreValue: number | null;
  weightPercentage: number | null;
  status: string;
}

export interface ReportAnalyticalScoreDetailDto {
  available: boolean;
  scoreValue: number | null;
  confidence: string;
  status: string;
  scoreVersion: string;
  asOfDate: string | null;
  dimensions: DimensionDetailDto[];
}

export interface ReportHoldingDetailDto {
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
  units: number;
  costBasisAmount: number | null;
  investedAmount: number | null;
  navValue: number | null;
  navAsOfDate: string | null;
  valuationState: string;
  availableValue: number | null;
  absoluteGainLoss: number | null;
  absoluteGainLossPercentage: number | null;
  portfolioWeightPercentage: number | null;
  scoreDetail: ReportAnalyticalScoreDetailDto;
  riskEvidence: HoldingRiskEvidenceDto | null;
  goalAlignmentDetail?: HoldingGoalAlignmentDto | null;
  sourceArtifactHash: string | null;
}

export interface PortfolioReportDto {
  reportMetadata: ReportMetadataDto;
  portfolioSummary: PortfolioSummaryDto;
  goalAlignment?: PortfolioGoalAlignmentDto | null;
  holdingDetails: ReportHoldingDetailDto[];
  dataQualityLimitations: string[];
  investigationQuestions: string[];
  disclaimers: string[];
  targetDriftAnalysis?: PortfolioDriftAnalysisDto | null;
}

export async function fetchPortfolioReport(
  goalRequest?: PortfolioGoalAlignmentRequest
): Promise<PortfolioReportDto> {
  const queryParams = new URLSearchParams();
  if (goalRequest?.goalCategory) queryParams.set('goalCategory', goalRequest.goalCategory);
  if (goalRequest?.horizonYears !== undefined) queryParams.set('horizonYears', String(goalRequest.horizonYears));
  if (goalRequest?.riskTolerance) queryParams.set('riskTolerance', goalRequest.riskTolerance);
  if (goalRequest?.investmentMode) queryParams.set('investmentMode', goalRequest.investmentMode);
  if (goalRequest?.fundCategory) queryParams.set('fundCategory', goalRequest.fundCategory);
  const queryStr = queryParams.toString() ? `?${queryParams.toString()}` : '';
  return apiFetch<PortfolioReportDto>(`/api/v1/investor/portfolio/report${queryStr}`);
}

export interface TargetAllocationItemDto {
  id?: number | null;
  targetType: 'CATEGORY' | 'SCHEME_OPTION' | string;
  schemeOptionId?: number | null;
  categoryName?: string | null;
  displayName?: string | null;
  targetWeightPercentage: number;
}

export interface TargetAllocationDto {
  items: TargetAllocationItemDto[];
  totalTargetWeightPercentage: number;
  isValidTotal: boolean;
}

export interface ItemDriftDto {
  targetType: string;
  schemeOptionId?: number | null;
  categoryName?: string | null;
  displayName: string;
  targetWeightPercentage: number;
  currentWeightPercentage: number;
  driftPercentagePoints: number;
  absoluteDriftPercentagePoints: number;
  driftDirection: 'OVER_ALLOCATED' | 'UNDER_ALLOCATED' | 'MATCHED' | string;
  mappingState: 'MAPPED' | 'UNMAPPED_TARGET' | 'UNMAPPED_HOLDING' | string;
  currentValue: number | null;
  yukiraScore: number | null;
  scoreStatus: string;
  isin?: string | null;
  amfiCode?: string | null;
}

export interface UnmappedHoldingDto {
  schemeOptionId: number;
  fundName: string;
  category: string;
  currentWeightPercentage: number;
  currentValue: number | null;
}

export interface UnmappedTargetDto {
  targetType: string;
  schemeOptionId?: number | null;
  categoryName?: string | null;
  displayName: string;
  targetWeightPercentage: number;
}

export interface PortfolioDriftAnalysisDto {
  status: 'NO_TARGET' | 'COMPLETE' | 'PARTIAL' | 'UNVALUED' | string;
  totalTargetWeightPercentage: number;
  totalCurrentWeightPercentage: number;
  totalAbsoluteDriftPercentagePoints: number;
  coveredPortfolioValue: number | null;
  itemDrifts: ItemDriftDto[];
  unmappedHoldings: UnmappedHoldingDto[];
  unmappedTargets: UnmappedTargetDto[];
  investigationQuestions: string[];
  limitations: string[];
  disclaimers: string[];
}

export async function fetchTargetAllocation(): Promise<TargetAllocationDto> {
  return apiFetch<TargetAllocationDto>('/api/v1/portfolio/target-allocation');
}

export async function saveTargetAllocation(
  target: TargetAllocationDto
): Promise<TargetAllocationDto> {
  return apiFetch<TargetAllocationDto>('/api/v1/portfolio/target-allocation', {
    method: 'POST',
    body: JSON.stringify(target),
  });
}

export async function deleteTargetAllocation(): Promise<void> {
  return apiFetch<void>('/api/v1/portfolio/target-allocation', {
    method: 'DELETE',
  });
}

export async function fetchPortfolioDriftAnalysis(): Promise<PortfolioDriftAnalysisDto> {
  return apiFetch<PortfolioDriftAnalysisDto>('/api/v1/portfolio/drift');
}

import { ComparisonResponse } from '@/types/comparison';

export type {
  ComparisonResponse,
  PortfolioComparisonResponse,
  ComparisonFund,
  ComparisonMetricRow,
  ScoreDimensionSummary
} from '@/types/comparison';

export async function fetchPortfolioComparison(
  schemeOptionIds?: number[],
  metricCodes?: string[]
): Promise<ComparisonResponse> {
  const queryParams = new URLSearchParams();
  if (schemeOptionIds && schemeOptionIds.length > 0) {
    queryParams.set('schemeOptionIds', schemeOptionIds.join(','));
  }
  if (metricCodes && metricCodes.length > 0) {
    queryParams.set('metricCodes', metricCodes.join(','));
  }
  const queryStr = queryParams.toString() ? `?${queryParams.toString()}` : '';
  return apiFetch<ComparisonResponse>(`/api/v1/portfolio/compare${queryStr}`);
}



