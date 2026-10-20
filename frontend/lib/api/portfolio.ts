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

// ---------------------------------------------------------------------------
// PORTFOLIO INTELLIGENCE & HOLDINGS COVERAGE API
// ---------------------------------------------------------------------------

export interface TopDisclosedHoldingDto {
  positionNumber: number;
  securityName: string;
  isin: string;
  sector: string;
  assetClass: string;
  reportedWeightPct: number;
  formattedWeight: string;
}

export interface FundConcentrationDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode?: string;
  isin?: string;
  portfolioDate: string | null;
  positionCount: number;
  totalDisclosedWeightPct?: number;
  sumDisclosedWeightsPct?: number;
  formattedDisclosedWeight?: string;
  formattedDisclosedCoverage?: string;
  top5DisclosedWeightPct?: number;
  top5WeightPct?: number;
  formattedTop5Weight?: string;
  top10DisclosedWeightPct?: number;
  top10WeightPct?: number;
  formattedTop10Weight?: string;
  hhiIndex?: number;
  hhi?: number;
  formattedHhi?: string;
  concentrationClassification?: 'HIGHLY_CONCENTRATED' | 'MODERATELY_CONCENTRATED' | 'DIVERSIFIED' | string;
  hhiClassification?: string;
  topDisclosedHoldings: TopDisclosedHoldingDto[];
  topHoldings?: Array<{
    rank: number;
    securityName: string;
    isin: string | null;
    sector: string | null;
    weightPct: number;
    formattedWeight: string;
  }>;
  qualityState: string;
  limitations: string[];
}

export interface OverlappingSecurityDto {
  securityName: string;
  isin: string;
  sector: string;
  fundAWeightPct: number;
  fundBWeightPct: number;
  overlapContributionPct: number;
  formattedOverlapContribution: string;
}

export interface PortfolioOverlapResponseDto {
  schemeOptionIdA: number;
  fundNameA: string;
  portfolioDateA: string | null;
  schemeOptionIdB: number;
  fundNameB: string;
  portfolioDateB: string | null;
  overlapPercentage: number;
  formattedOverlapPercentage: string;
  commonSecuritiesCount: number;
  fundAPositionCount: number;
  fundBPositionCount: number;
  uniqueSecuritiesFundA: number;
  uniqueSecuritiesFundB: number;
  overlappingSecurities: OverlappingSecurityDto[];
  qualityState: string;
  limitations: string[];
}

export interface ConstituentFundExposureDto {
  schemeOptionId: number;
  fundName: string;
  fundWeightInPortfolioPct: number;
  holdingWeightInFundPct: number;
  effectiveLookThroughWeightPct: number;
}

export interface AggregatedSecurityExposureDto {
  securityName: string;
  isin: string;
  sector: string;
  assetClass: string;
  lookThroughWeightPct: number;
  formattedLookThroughWeight: string;
  estimatedMarketValue: number;
  holdingFundCount: number;
  constituentFundExposures: ConstituentFundExposureDto[];
}

export interface PortfolioSecurityAggregationDto {
  totalConstituentFunds: number;
  valuedConstituentFunds: number;
  totalPortfolioValue: number;
  disclosedCoveragePercentage: number;
  uniqueSecuritiesCount: number;
  topSecurityExposures: AggregatedSecurityExposureDto[];
  qualityState: string;
  limitations: string[];
}

export interface SourceClassificationDto {
  schemeOptionId: number;
  amfiCode: string;
  fundName: string;
  sourceClassification: string;
  positionCount: number;
  qualityState: string;
}

export interface HoldingsCoverageReportDto {
  auditAsOfDate: string;
  totalActiveUniverse: number;
  activeWithNav: number;
  active3YReady: number;
  activeWithHoldings: number;
  activeWithLatestHoldings: number;
  activeWithRecentHoldings: number;
  activeWithValidHoldings: number;
  activeWithSecurityIdentities: number;
  staleHoldingsCount: number;
  identityUnresolvedCount: number;
  sourceNotConfiguredCount: number;
  sourceFailedCount: number;
  sourceClassifications: SourceClassificationDto[];
}

export async function fetchFundConcentration(schemeOptionId: number, knowledgeCutoff?: string): Promise<FundConcentrationDto> {
  const query = knowledgeCutoff ? `?knowledgeCutoff=${encodeURIComponent(knowledgeCutoff)}` : '';
  const raw = await apiFetch<Record<string, unknown>>(`/api/v1/schemes/options/${schemeOptionId}/holdings/concentration${query}`);
  const rawTopHoldings = (raw.topHoldings as Array<{
    rank?: number;
    positionNumber?: number;
    securityName: string;
    isin: string | null;
    sector: string | null;
    weightPct?: number;
    disclosedWeightPct?: number;
    formattedWeight: string;
  }>) || [];

  const topDisclosed: TopDisclosedHoldingDto[] = (raw.topDisclosedHoldings as TopDisclosedHoldingDto[]) ||
    rawTopHoldings.map((h, i) => ({
      positionNumber: h.positionNumber || h.rank || i + 1,
      securityName: h.securityName,
      isin: h.isin,
      sector: h.sector,
      disclosedWeightPct: h.disclosedWeightPct ?? h.weightPct ?? 0,
      formattedWeight: h.formattedWeight || "0.00%",
    }));

  return {
    ...(raw as unknown as FundConcentrationDto),
    topDisclosedHoldings: topDisclosed,
  };
}

export async function fetchPairwiseOverlap(schemeOptionIdA: number, schemeOptionIdB: number, knowledgeCutoff?: string): Promise<PortfolioOverlapResponseDto> {
  const params = new URLSearchParams({
    schemeOptionIdA: schemeOptionIdA.toString(),
    schemeOptionIdB: schemeOptionIdB.toString(),
  });
  if (knowledgeCutoff) params.set('knowledgeCutoff', knowledgeCutoff);
  return apiFetch<PortfolioOverlapResponseDto>(`/api/v1/schemes/options/overlap?${params.toString()}`);
}

export async function fetchLookThroughExposure(fundWeights: Record<number, number>, knowledgeCutoff?: string): Promise<PortfolioSecurityAggregationDto> {
  const query = knowledgeCutoff ? `?knowledgeCutoff=${encodeURIComponent(knowledgeCutoff)}` : '';
  return apiFetch<PortfolioSecurityAggregationDto>(`/api/v1/schemes/options/look-through${query}`, {
    method: 'POST',
    body: JSON.stringify({ fundWeights }),
  });
}

export async function fetchHoldingsCoverageAudit(asOfDate?: string): Promise<HoldingsCoverageReportDto> {
  const query = asOfDate ? `?asOfDate=${encodeURIComponent(asOfDate)}` : '';
  return apiFetch<HoldingsCoverageReportDto>(`/api/v1/schemes/options/coverage-audit${query}`);
}

// ---------------------------------------------------------------------------
// CAS / EXTERNAL PORTFOLIO IMPORT API
// ---------------------------------------------------------------------------

export interface CasImportHoldingDetailDto {
  id: number;
  rawSchemeName: string;
  folioNumber: string | null;
  isin: string | null;
  amfiCode: string | null;
  units: number;
  costBasisAmount: number | null;
  marketValue: number | null;
  nav: number | null;
  resolvedSchemeOptionId: number | null;
  resolvedFundName: string | null;
  resolvedAmcName: string | null;
  resolutionStatus: 'RESOLVED' | 'AMBIGUOUS' | 'UNRESOLVED' | 'INVALID' | string;
  resolutionMethod: 'ISIN_EXACT' | 'AMFI_EXACT' | 'NAME_EXACT' | 'UNRESOLVED' | string;
  resolutionNotes: string | null;
}

export interface CasImportResultDto {
  importId: number;
  sourceFilename: string;
  fileType: string;
  status: 'PENDING_REVIEW' | 'CONFIRMED' | 'FAILED' | 'CANCELLED' | string;
  totalRecordsDetected: number;
  resolvedRecordsCount: number;
  unresolvedRecordsCount: number;
  ambiguousRecordsCount: number;
  statementDate: string | null;
  parserVersion: string;
  createdAt: string;
  confirmedAt: string | null;
  holdings: CasImportHoldingDetailDto[];
  limitations: string[];
  casProvider?: string;
  importQualityState?: string;
}

export interface CasImportConfirmResponseDto {
  importId: number;
  status: string;
  holdingsAddedCount: number;
  holdingsUpdatedCount: number;
  holdingsUnchangedCount: number;
  unresolvedSkippedCount: number;
  confirmedAt: string;
  messages: string[];
}

export async function stageCasImportJson(rawJson: string, filename = 'cams_cas_import.json'): Promise<CasImportResultDto> {
  return apiFetch<CasImportResultDto>(`/api/v1/investor/portfolio/imports?filename=${encodeURIComponent(filename)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: rawJson,
  });
}

export async function fetchCasImportDetails(importId: number): Promise<CasImportResultDto> {
  return apiFetch<CasImportResultDto>(`/api/v1/investor/portfolio/imports/${importId}`);
}

export async function confirmCasImport(importId: number, mode = 'MERGE'): Promise<CasImportConfirmResponseDto> {
  return apiFetch<CasImportConfirmResponseDto>(`/api/v1/investor/portfolio/imports/${importId}/confirm?mode=${encodeURIComponent(mode)}`, {
    method: 'POST',
  });
}

export async function fetchInvestorCasImports(): Promise<CasImportResultDto[]> {
  return apiFetch<CasImportResultDto[]>('/api/v1/investor/portfolio/imports');
}

export async function uploadCasFileMultipart(file: File, password?: string): Promise<CasImportResultDto> {
  const formData = new FormData();
  formData.append('file', file);
  if (password && password.trim().length > 0) {
    formData.append('password', password.trim());
  }

  const response = await fetch('/api/v1/investor/portfolio/imports', {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`CAS file upload failed: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

// ---------------------------------------------------------------------------
// POINT-IN-TIME SNAPSHOT HISTORY & PORTFOLIO DRIFT API
// ---------------------------------------------------------------------------

export interface PortfolioSnapshotSummaryDto {
  snapshotId: number;
  schemeOptionId: number;
  schemeName: string;
  amfiCode: string;
  portfolioDate: string;
  availabilityTime: string;
  revisionSeq: number;
  isLatestRevision: boolean;
  reportedHoldingsCount: number;
  sumReportedWeightsPct: number;
  sumReportedWeightsPctFormatted: string;
  sourceArtifactId: number | null;
  sourceArtifactSha256: string | null;
  sourceProvider: string;
  analyticalEligibility: string;
  qualityState: string;
}

export interface SnapshotDriftHeaderDto {
  snapshotId: number;
  portfolioDate: string;
  availabilityTime: string;
  sourceArtifactId: number | null;
  sourceArtifactSha256: string | null;
  holdingsCount: number;
  sumWeightsPct: number;
  top5Pct: number;
  top10Pct: number;
  hhi: number;
}

export interface AssetClassDriftDto {
  assetClass: string;
  earlierWeightPct: number;
  earlierWeightPctFormatted: string;
  laterWeightPct: number;
  laterWeightPctFormatted: string;
  weightDeltaPct: number;
  weightDeltaPctFormatted: string;
}

export interface HoldingDriftItemDto {
  securityName: string;
  isin: string | null;
  sector: string | null;
  assetClass: string;
  changeType: 'WEIGHT_INCREASED' | 'WEIGHT_DECREASED' | 'NEWLY_REPORTED' | 'NO_LONGER_REPORTED' | 'UNCHANGED' | string;
  earlierWeightPct: number | null;
  earlierWeightPctFormatted: string | null;
  laterWeightPct: number | null;
  laterWeightPctFormatted: string | null;
  weightDeltaPct: number;
  weightDeltaPctFormatted: string;
  changeDescription: string;
}

export interface DriftClassificationSummaryDto {
  totalComparedPositions: number;
  newlyReportedCount: number;
  noLongerReportedCount: number;
  weightIncreasedCount: number;
  weightDecreasedCount: number;
  unchangedCount: number;
  unclassifiedCount: number;
}

export interface SectorDriftDto {
  sector: string;
  earlierWeightPct: number;
  earlierWeightPctFormatted: string;
  laterWeightPct: number;
  laterWeightPctFormatted: string;
  weightDeltaPct: number;
  weightDeltaPctFormatted: string;
}

export interface SectorDriftCoverageDto {
  earlierCoveragePct: number;
  earlierCoveragePctFormatted: string;
  laterCoveragePct: number;
  laterCoveragePctFormatted: string;
  unclassifiedWeightPctEarlier: number;
  unclassifiedWeightPctEarlierFormatted: string;
  unclassifiedWeightPctLater: number;
  unclassifiedWeightPctLaterFormatted: string;
  coverageStatus: 'ADEQUATE_COVERAGE' | 'PARTIAL_COVERAGE' | 'UNAVAILABLE' | string;
  coverageLimitationNotes: string[];
}

export interface PortfolioDriftResponseDto {
  schemeOptionId: number;
  schemeName: string;
  amfiCode: string;
  status: 'COMPARABLE' | 'INSUFFICIENT_HISTORY' | 'UNAVAILABLE' | 'INVALID_SELECTION' | string;
  statusReason: string;
  earlierSnapshot: SnapshotDriftHeaderDto | null;
  laterSnapshot: SnapshotDriftHeaderDto | null;
  top5PctDelta: number;
  top10PctDelta: number;
  hhiDelta: number;
  classificationSummary?: DriftClassificationSummaryDto | null;
  assetClassDrift: AssetClassDriftDto[];
  sectorDrift?: SectorDriftDto[];
  sectorCoverage?: SectorDriftCoverageDto | null;
  holdingChanges: HoldingDriftItemDto[];
  topIncreases?: HoldingDriftItemDto[];
  topDecreases?: HoldingDriftItemDto[];
  additions?: HoldingDriftItemDto[];
  exits?: HoldingDriftItemDto[];
  isComparable: boolean;
  comparabilityNotes: string[];
  qualityState: string;
  methodologyVersion: string;
  investigationPrompts?: string[];
}

export async function fetchAvailableSnapshots(schemeOptionId: number, knowledgeCutoff?: string): Promise<PortfolioSnapshotSummaryDto[]> {
  const query = knowledgeCutoff ? `?knowledgeCutoff=${encodeURIComponent(knowledgeCutoff)}` : '';
  return apiFetch<PortfolioSnapshotSummaryDto[]>(`/api/v1/schemes/options/${schemeOptionId}/holdings/snapshots${query}`);
}

export async function fetchPortfolioDrift(schemeOptionId: number, earlierSnapshotId?: number, laterSnapshotId?: number, knowledgeCutoff?: string): Promise<PortfolioDriftResponseDto> {
  const params = new URLSearchParams();
  if (earlierSnapshotId !== undefined) params.set('earlierSnapshotId', earlierSnapshotId.toString());
  if (laterSnapshotId !== undefined) params.set('laterSnapshotId', laterSnapshotId.toString());
  if (knowledgeCutoff) params.set('knowledgeCutoff', knowledgeCutoff);
  const queryStr = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<PortfolioDriftResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/holdings/drift${queryStr}`);
}

export interface MarketCapExposureDto {
  largeCapWeightPct: number;
  largeCapWeightPctFormatted: string;
  midCapWeightPct: number;
  midCapWeightPctFormatted: string;
  smallCapWeightPct: number;
  smallCapWeightPctFormatted: string;
  cashWeightPct: number;
  cashWeightPctFormatted: string;
  debtOtherWeightPct: number;
  debtOtherWeightPctFormatted: string;
  unclassifiedWeightPct: number;
  unclassifiedWeightPctFormatted: string;
  classificationMethodology: string;
  classificationStatus?: string;
  sourceRankingPeriod?: string | null;
  sourcePublicationDate?: string | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  sourceArtifactId?: number | null;
  sourceArtifactSha256?: string | null;
  totalClassifiedHoldingsCount?: number;
  totalUnclassifiedHoldingsCount?: number;
}

export interface FactorDimensionsSummaryDto {
  valueStatus: string;
  qualityStatus: string;
  momentumStatus: string;
  documentation: string;
}

export interface ConcentrationSummaryDto {
  top5WeightPct: number;
  top5WeightPctFormatted: string;
  top10WeightPct: number;
  top10WeightPctFormatted: string;
  hhi: number;
  hhiClassification: string;
}

export interface SectorWeightDto {
  sector: string;
  weightPct: number;
  weightPctFormatted: string;
  isUnclassified: boolean;
}

export interface SnapshotExposureDetailDto {
  snapshotId: number;
  portfolioDate: string;
  availabilityTime: string;
  sourceArtifactId: number | null;
  sourceArtifactSha256: string | null;
  totalHoldingsDisclosed: number;
  sumReportedWeightsPct: number;
  sumReportedWeightsPctFormatted: string;
  mappedWeightPct: number;
  mappedWeightPctFormatted: string;
  unmappedWeightPct: number;
  unmappedWeightPctFormatted: string;
  classificationCoveragePct: number;
  classificationCoveragePctFormatted: string;
  marketCap: MarketCapExposureDto;
  otherFactors: FactorDimensionsSummaryDto;
  concentration: ConcentrationSummaryDto;
  topSectors: SectorWeightDto[];
  completenessState: string;
  coverageLimitations: string[];
}

export interface ReclassifiedHoldingDto {
  isin: string;
  companyName: string;
  previousCategory: string;
  previousRank: number | null;
  currentCategory: string;
  currentRank: number | null;
  portfolioWeightPct: number;
  portfolioWeightPctFormatted: string;
  shiftDescription: string;
}

export interface StyleDriftAttributionDto {
  largeCapRebalanceDeltaPct: number;
  largeCapReclassifyDeltaPct: number;
  largeCapCoverageDeltaPct: number;
  largeCapNetDeltaPct: number;
  midCapRebalanceDeltaPct: number;
  midCapReclassifyDeltaPct: number;
  midCapCoverageDeltaPct: number;
  midCapNetDeltaPct: number;
  smallCapRebalanceDeltaPct: number;
  smallCapReclassifyDeltaPct: number;
  smallCapCoverageDeltaPct: number;
  smallCapNetDeltaPct: number;
  attributionMethodology: string;
  attributionExplanation: string;
  reclassifiedHoldings: ReclassifiedHoldingDto[];
}

export interface SnapshotDriftDeltaDto {
  previousDate: string;
  largeCapDeltaPct: number;
  largeCapDeltaPctFormatted: string;
  midCapDeltaPct: number;
  midCapDeltaPctFormatted: string;
  smallCapDeltaPct: number;
  smallCapDeltaPctFormatted: string;
  cashDeltaPct: number;
  cashDeltaPctFormatted: string;
  top10DeltaPct: number;
  top10DeltaPctFormatted: string;
  hhiDelta: number;
  coverageDeltaPct: number;
  coverageDeltaPctFormatted: string;
  isComparable: boolean;
  comparabilityNote: string;
  styleDriftAttribution?: StyleDriftAttributionDto | null;
}

export interface HistoricalSnapshotExposureDto {
  snapshotId: number;
  portfolioDate: string;
  availabilityTime: string;
  totalHoldingsDisclosed: number;
  sumReportedWeightsPct: number;
  mappedWeightPct: number;
  classificationCoveragePct: number;
  largeCapWeightPct: number;
  midCapWeightPct: number;
  smallCapWeightPct: number;
  cashWeightPct: number;
  debtOtherWeightPct: number;
  unclassifiedWeightPct: number;
  top5WeightPct: number;
  top10WeightPct: number;
  hhi: number;
  classificationStatus?: string;
  rankingPeriod?: string | null;
  deltaFromPrevious: SnapshotDriftDeltaDto | null;
}

export interface ObservedExposureShiftDto {
  shiftDimension: string;
  description: string;
  fromDate: string;
  toDate: string;
  magnitudeDeltaPct: number;
  magnitudeDeltaPctFormatted: string;
  coincidesWithCoverageChange: boolean;
  evidenceNote: string;
}

export interface AttributionPrerequisiteItemDto {
  prerequisiteCode: string;
  prerequisiteName: string;
  currentStatus: string;
  technicalRequirement: string;
}

export interface MacroActiveDecompositionDto {
  empiricalBeta: number;
  marketSensitivityContributionPct: number;
  marketSensitivityContributionPctFormatted: string;
  initialCashWeightPct: number;
  initialCashWeightPctFormatted: string;
  riskFreeReturnPct: number;
  riskFreeReturnPctFormatted: string;
  cashDragContributionPct: number;
  cashDragContributionPctFormatted: string;
  selectionAndSectorResidualPct: number;
  selectionAndSectorResidualPctFormatted: string;
  reconciledActiveReturnPct: number;
  reconciledActiveReturnPctFormatted: string;
  reconciliationToleranceDeltaPct: number;
  isReconciled: boolean;
  reconciliationFormula: string;
}

export interface ActiveStyleContextDto {
  largeCapWeightPct: number;
  largeCapWeightPctFormatted: string;
  midCapWeightPct: number;
  midCapWeightPctFormatted: string;
  smallCapWeightPct: number;
  smallCapWeightPctFormatted: string;
  cashWeightPct: number;
  cashWeightPctFormatted: string;
  unclassifiedWeightPct: number;
  unclassifiedWeightPctFormatted: string;
  classificationStatus: string;
  classificationRankingPeriod: string;
  activeTiltSummary: string;
}

export interface BrinsonSegmentDto {
  segmentName: string;
  portfolioWeightPct: number;
  benchmarkWeightPct: number;
  portfolioReturnPct: number;
  benchmarkReturnPct: number;
  allocationEffectPct: number;
  selectionEffectPct: number;
  interactionEffectPct: number;
  totalSegmentEffectPct: number;
}

export interface SectorWeightSummaryDto {
  sectorName: string;
  weightPct: number;
  holdingCount: number;
}

export interface ExposureTimelineObservationDto {
  snapshotId: number;
  portfolioDate: string;
  availabilityTime: string;
  disclosureScope: string;
  reportedWeightSumPct: number;
  holdingsCount: number;
  classificationCoveragePct: number;
  largeCapWeightPct: number;
  midCapWeightPct: number;
  smallCapWeightPct: number;
  cashWeightPct: number;
  unclassifiedWeightPct: number;
  topSectors: SectorWeightSummaryDto[];
  comparabilityStatus: string;
  comparabilityReason: string;
  daysSincePreviousSnapshot: number | null;
  sourceStorageUri: string | null;
  sourceSha256: string | null;
}

export interface BrinsonAttributionSummaryDto {
  executionStatus: string;
  brinsonVariant: string;
  statusExplanation?: string;
  reasonIfNotCalculated: string;
  totalAllocationEffectPct: number;
  totalSelectionEffectPct: number;
  totalInteractionEffectPct: number;
  totalActiveReturnReconciledPct?: number;
  reconciliationDeltaPct?: number;
  isReconciled?: boolean;
  benchmarkDisclosedWeightPct?: number;
  benchmarkWeightedCount?: number;
  benchmarkTotalCount?: number;
  missingPrerequisites?: string[];
  segments: BrinsonSegmentDto[];
}

export interface DataDependencyItemDto {
  dependencyCode: string;
  dependencyName: string;
  requiredFor: string;
  repositoryStatus: string;
  officialSource: string;
  epistemicNote: string;
}

export interface AttributionDataReadinessGateDto {
  overallReadiness: string;
  totalDependencies: number;
  availableDependencies: number;
  missingDependencies: number;
  dependencies: DataDependencyItemDto[];
}

export interface AttributionProvenanceDto {
  methodologyVersion: string;
  calculationRunTimestamp: string;
  formulaSpecification: string;
  fundNavSourceSha256: string;
  benchmarkObservationSha256: string;
  riskFreeSourceDescription: string;
  nonAdvisoryDisclaimer: string;
}

export interface ActiveReturnAttributionResponseDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string | null;
  schemeCode: string | null;
  benchmarkCode: string;
  benchmarkName: string;
  asOfDate: string;
  knowledgeCutoff: string;
  attributionPeriodStart: string;
  attributionPeriodEnd: string;
  observationCount: number;
  fundReturnPct: number;
  fundReturnPctFormatted: string;
  benchmarkReturnPct: number;
  benchmarkReturnPctFormatted: string;
  activeReturnPct: number;
  activeReturnPctFormatted: string;
  modelStatus: string;
  modelName: string;
  modelExplanation: string;
  macroAttribution: MacroActiveDecompositionDto;
  styleContext: ActiveStyleContextDto;
  exposureTimeline: ExposureTimelineObservationDto[];
  brinsonSegmentation: BrinsonAttributionSummaryDto;
  dataReadinessGate: AttributionDataReadinessGateDto;
  investigationPrompts: string[];
  epistemicLimitations: string[];
  provenance: AttributionProvenanceDto;
}

export interface FactorReturnAttributionDto {
  modelStatus: string;
  modelSpecification: string;
  missingPrerequisites: AttributionPrerequisiteItemDto[];
  epistemicLimitations: string[];
  governanceDisclaimer: string;
  activeAttribution?: ActiveReturnAttributionResponseDto | null;
}

export interface FactorExposureEpistemicDto {
  methodologyVersion: string;
  governanceStatus: string;
  disclaimer: string;
  factualObservations: string[];
  investigationQuestions: string[];
}

export interface PortfolioFactorExposureResponseDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string | null;
  schemeCode: string | null;
  benchmarkCode: string;
  benchmarkName: string;
  asOfDate: string;
  knowledgeCutoffTime: string;
  analysisStatus: string;
  statusReason: string;
  currentExposure: SnapshotExposureDetailDto | null;
  driftHistory: HistoricalSnapshotExposureDto[];
  largestObservedShifts: ObservedExposureShiftDto[];
  returnAttribution: FactorReturnAttributionDto;
  epistemic: FactorExposureEpistemicDto;
}

export async function fetchFactorExposure(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoff?: string
): Promise<PortfolioFactorExposureResponseDto> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<PortfolioFactorExposureResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/holdings/factor-exposure${qs}`);
}

export async function fetchActiveReturnAttribution(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoff?: string,
  horizon?: string,
  startDate?: string,
  endDate?: string
): Promise<ActiveReturnAttributionResponseDto> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  if (horizon) params.append('horizon', horizon);
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<ActiveReturnAttributionResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/attribution${qs}`);
}

export interface DuplicateHoldingDetailDto {
  securityId: number;
  canonicalName: string;
  sector: string;
  occurrenceCount: number;
  totalReportedWeightPct: number;
  note: string;
}

export interface DataReliabilitySummaryDto {
  snapshotDate: string;
  availabilityTime: string;
  sumReportedWeights: number;
  sumReportedWeightsPct: string;
  totalHoldingsDisclosed: number;
  uniqueSecuritiesCount: number;
  duplicateHoldingsCount: number;
  duplicateHoldings: DuplicateHoldingDetailDto[];
  invalidWeightsCount: number;
  weightReconciliationStatus: string;
  weightReconciliationCode: string;
  weightReconciliationExplanation: string;
  mappedWeightPct: number;
  unmappedWeightPct: number;
  classifiedWeightPct: number;
  unclassifiedWeightPct: number;
  cashAndEquivalentsWeightPct: number;
  equityWeightPct: number;
  completenessType: string;
  sourceArtifactUri: string | null;
  sourceArtifactSha256: string | null;
  sourceArtifactByteSize: number | null;
}

export interface IssuerExposureDetailDto {
  rank: number;
  issuerName: string;
  totalWeightPct: number;
  instrumentsHeldCount: number;
  primarySector: string;
  primaryAssetClass: string;
}

export interface IssuerConcentrationDto {
  totalIssuersDisclosed: number;
  top5IssuerWeightPct: number;
  top10IssuerWeightPct: number;
  top20IssuerWeightPct: number;
  issuerHhi: number;
  effectiveNumberOfIssuers: number;
  hhiConcentrationTier: string;
  securityHhi: number;
  effectiveNumberOfSecurities: number;
  topIssuers: IssuerExposureDetailDto[];
}

export interface SectorExposureDetailDto {
  rank: number;
  sectorName: string;
  weightPct: number;
  holdingsCount: number;
}

export interface SectorConcentrationDto {
  totalSectorsDisclosed: number;
  top1SectorWeightPct: number;
  top1SectorName: string;
  top3SectorWeightPct: number;
  unclassifiedSectorWeightPct: number;
  taxonomyType: string;
  taxonomyDisclosure: string;
  sectors: SectorExposureDetailDto[];
}

export interface MarketCapReliabilityDto {
  largeCapWeightPct: number;
  midCapWeightPct: number;
  smallCapWeightPct: number;
  cashWeightPct: number;
  debtOtherWeightPct: number;
  unclassifiedWeightPct: number;
  classificationStandard: string;
  classificationProvenance: string;
  historicalPitStatus: string;
  methodologyLimitation: string;
}

export interface SnapshotReliabilityDetailDto {
  snapshotId: number;
  portfolioDate: string;
  availabilityTime: string;
  holdingsCount: number;
  uniqueSecuritiesCount: number;
  sumReportedWeightsPct: number;
  weightReconciliationCode: string;
  completenessType: string;
  hasDuplicateSecurities: boolean;
  sourceArtifactSha256: string | null;
}

export interface HistoricalComparabilityPairDto {
  earlierDate: string;
  laterDate: string;
  intervalDays: number;
  earlierSumWeightPct: number;
  laterSumWeightPct: number;
  weightDeltaPct: number;
  earlierHoldingsCount: number;
  laterHoldingsCount: number;
  holdingsDeltaCount: number;
  earlierCompleteness: string;
  laterCompleteness: string;
  isDataGapExceeded: boolean;
  comparabilityStatus: string;
  reasonCodes: string[];
  assessmentSummary: string;
}

export interface InvestigationItemDto {
  category: string;
  severity: string;
  title: string;
  empiricalEvidence: string;
  dueDiligenceQuestion: string;
}

export interface PortfolioConstructionMethodologyDto {
  methodologyVersion: string;
  governanceStatus: string;
  hhiFormula: string;
  effectiveHoldingsFormula: string;
  weightReconciliationPolicy: string;
  comparabilityAssessmentStandard: string;
  liquidityDiscretionDisclaimer: string;
}

export interface PortfolioConstructionRiskResponseDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string | null;
  schemeCode: string | null;
  asOfDate: string;
  knowledgeCutoff: string;
  status: string;
  message: string;
  dataReliability: DataReliabilitySummaryDto | null;
  issuerConcentration: IssuerConcentrationDto | null;
  sectorConcentration: SectorConcentrationDto | null;
  marketCapReliability: MarketCapReliabilityDto | null;
  snapshotHistory: SnapshotReliabilityDetailDto[];
  comparabilityMatrix: HistoricalComparabilityPairDto[];
  investigationItems: InvestigationItemDto[];
  methodology: PortfolioConstructionMethodologyDto;
}

export async function fetchPortfolioConstructionRisk(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoff?: string
): Promise<PortfolioConstructionRiskResponseDto> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<PortfolioConstructionRiskResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/holdings/construction-risk${qs}`);
}

export interface ActiveSecurityPositionDto {
  isin: string;
  symbol: string | null;
  companyName: string;
  fundWeightPct: number;
  benchmarkWeightPct: number | null;
  activeWeightPct: number;
  marketCapCategory: string;
  industry: string;
  positionStatus: 'OVERWEIGHT' | 'UNDERWEIGHT' | 'FUND_ONLY' | 'BENCHMARK_ONLY' | string;
}

export interface SectorExposureComparisonDto {
  sectorName: string;
  fundSectorWeightPct: number;
  benchmarkSectorWeightPct: number;
  activeSectorExposurePct: number;
  fundStockCount: number;
  benchmarkStockCount: number;
}

export interface MarketCapExposureComparisonDto {
  category: string;
  fundWeightPct: number;
  benchmarkWeightPct: number;
  activeExposurePct: number;
}

export interface BenchmarkCompositionComparisonResponseDto {
  schemeOptionId: number;
  fundName: string;
  planType: string;
  optionType: string;
  portfolioDate: string;
  fundHoldingsCount: number;
  sumFundWeightsPct: number;

  benchmarkId: number;
  benchmarkCode: string;
  benchmarkName: string;
  benchmarkCompositionDate: string;
  benchmarkConstituentCount: number;
  mappedConstituentCount: number;
  unmappedConstituentCount: number;
  weightCoverageStatus: 'TOP_WEIGHTS_DISCLOSED' | 'MEMBERSHIP_ONLY' | 'UNAVAILABLE' | string;
  sumDisclosedBenchmarkWeightsPct: number;

  commonConstituentCount: number;
  fundSpecificCount: number;
  benchmarkSpecificCount: number;
  holdingsOverlapPct: number;
  unmappedHoldingsWeightPct: number;

  activePositions: ActiveSecurityPositionDto[];
  sectorExposures: SectorExposureComparisonDto[];
  marketCapExposures: MarketCapExposureComparisonDto[];

  brinsonAttributionStatus: 'BLOCKED_PENDING_STOCK_RETURNS' | 'READY' | 'INSUFFICIENT_DATA' | string;
  brinsonBlockerReason: string;
  investorOrientation: string;
  dueDiligencePrompts: string[];

  sourceArtifactId: number | null;
  sourceArtifactSha256: string;
  sourceUrl: string;
  knowledgeCutoff: string;
  methodologyVersion: string;
}

export async function fetchBenchmarkCompositionComparison(
  schemeOptionId: number,
  asOfDate?: string,
  knowledgeCutoff?: string
): Promise<BenchmarkCompositionComparisonResponseDto> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<BenchmarkCompositionComparisonResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/benchmark-comparison${qs}`);
}

// ---------------------------------------------------------------------------
// MULTI-PERIOD BENCHMARK RECONSTITUTION & PORTFOLIO STYLE DIVERGENCE API
// ---------------------------------------------------------------------------

export interface ReconstitutionMemberDto {
  isin: string;
  symbol: string | null;
  companyName: string;
  industry: string | null;
  effectiveDate: string;
  sourceWeightPct: number | null;
  mappingStatus: string;
  canonicalSecurityId: number | null;
  changeType: 'ENTERED' | 'EXITED' | 'RETAINED' | string;
  notes?: string | null;
}

export interface BenchmarkReconstitutionResponseDto {
  benchmarkCode: string;
  benchmarkName: string;
  earlierDate: string;
  laterDate: string;
  intervalDays: number;
  intervalClassification: string;
  earlierConstituentCount: number;
  laterConstituentCount: number;
  enteredCount: number;
  exitedCount: number;
  retainedCount: number;
  turnoverPct: number;
  unresolvedCount: number;
  coverageStatus: 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE' | string;
  provenanceStatus: 'OFFICIAL_RECONSTITUTION' | 'TWO_POINT_OBSERVED_TRANSITION' | 'UNVERIFIED_SOURCE' | string;
  enteredConstituents: ReconstitutionMemberDto[];
  exitedConstituents: ReconstitutionMemberDto[];
  retainedConstituents: ReconstitutionMemberDto[];
  investorNotice: string;
  knowledgeCutoff: string;
}

export interface ApparentChangeBreakdownDto {
  changeCategory: 'FUND_PORTFOLIO_CHANGE' | 'BENCHMARK_MEMBERSHIP_CHANGE' | 'CLASSIFICATION_COVERAGE_CHANGE' | string;
  securityName: string;
  isin: string | null;
  earlierState: string;
  laterState: string;
  impactWeightPct: number | null;
  description: string;
  epistemicNote: string;
}

export interface PeriodDivergenceSummaryDto {
  portfolioDate: string;
  benchmarkDate: string;
  snapshotDateDifferenceDays: number;
  snapshotDateCompatible: boolean;
  fundHoldingsCount: number;
  benchmarkConstituentCount: number;
  commonCount: number;
  fundOnlyCount: number;
  benchmarkOnlyCount: number;
  overlapPct: number;
  fundMappedPct: number;
  benchmarkDisclosedWeightPct: number;
  offBenchmarkCapitalAllocationPct: number;
  periodStatus: 'VERIFIED_COMPLETE' | 'VERIFIED_PARTIAL' | 'UNVERIFIED_SOURCE' | 'INSUFFICIENT_DATA' | string;
  warningMessage: string | null;
}

export interface BenchmarkDivergenceResponseDto {
  schemeOptionId: number;
  fundName: string;
  benchmarkCode: string;
  benchmarkName: string;
  startDate: string;
  endDate: string;
  knowledgeCutoff: string;
  overallStatus: string;
  coverageExplanation: string;
  periods: PeriodDivergenceSummaryDto[];
  apparentChanges: ApparentChangeBreakdownDto[];
  dueDiligencePrompts: string[];
}

export async function fetchBenchmarkDivergence(
  schemeOptionId: number,
  startDate?: string,
  endDate?: string,
  knowledgeCutoff?: string
): Promise<BenchmarkDivergenceResponseDto> {
  const params = new URLSearchParams();
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<BenchmarkDivergenceResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/benchmark-divergence${qs}`);
}

export async function fetchBenchmarkReconstitution(
  schemeOptionId: number,
  benchmarkCode?: string,
  fromDate?: string,
  toDate?: string,
  knowledgeCutoff?: string
): Promise<BenchmarkReconstitutionResponseDto> {
  const params = new URLSearchParams();
  if (benchmarkCode) params.append('benchmarkCode', benchmarkCode);
  if (fromDate) params.append('fromDate', fromDate);
  if (toDate) params.append('toDate', toDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<BenchmarkReconstitutionResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/benchmark-reconstitution${qs}`);
}

// ---------------------------------------------------------------------------
// POINT-IN-TIME ACTIVE SHARE & OFF-BENCHMARK ALLOCATION TRAJECTORY API
// ---------------------------------------------------------------------------

export interface ActiveShareConstituentInputDto {
  isin: string;
  securityName: string;
  portfolioWeightPct: number | null;
  benchmarkWeightPct: number | null;
  isBenchmarkMember: boolean;
  absoluteDiscrepancyPct: number | null;
  mappingStatus: string;
  marketCapCategory: string;
}

export interface ActiveShareObservationDto {
  portfolioDate: string;
  portfolioAvailabilityTime: string | null;
  portfolioSourceArtifactId: number | null;
  benchmarkDate: string | null;
  benchmarkAvailabilityTime: string | null;
  snapshotDateDiffDays: number;
  isTemporallyComparable: boolean;
  activeSharePct: number | null;
  activeShareLowerBoundPct: number | null;
  offBenchmarkAllocationPct: number;
  membershipOverlapPct: number;
  fundHoldingsCount: number;
  benchmarkConstituentsCount: number;
  portfolioWeightCoveragePct: number;
  benchmarkWeightCoveragePct: number;
  benchmarkMembershipCoveragePct: number;
  securityMappingCoveragePct: number;
  status: 'VERIFIED_COMPLETE' | 'GATED_INCOMPLETE_BENCHMARK_WEIGHTS' | 'UNVERIFIED_SOURCE' | 'VERIFIED_PARTIAL' | 'INSUFFICIENT_DATA' | string;
  statusReason: string;
  constituentInputs: ActiveShareConstituentInputDto[];
  // Independent 5-dimensional coverage fields
  membershipCoverageStatus?: 'VERIFIED_COMPLETE' | 'VERIFIED_PARTIAL' | 'UNAVAILABLE' | string;
  mappingCoverageStatus?: 'VERIFIED_COMPLETE' | 'VERIFIED_PARTIAL' | string;
  weightCoverageStatus?: 'VERIFIED_COMPLETE' | 'GATED_INCOMPLETE_BENCHMARK_WEIGHTS' | 'INSUFFICIENT_DATA' | string;
  temporalCoverageStatus?: 'VERIFIED_COMPLETE' | 'VERIFIED_PARTIAL' | 'UNVERIFIED_SOURCE' | string;
  sourceConfidenceStatus?: 'VERIFIED_COMPLETE' | 'UNVERIFIED_SOURCE' | string;
  benchmarkDisclosedWeightSum?: number | null;
  benchmarkWeightedConstituentsCount?: number | null;
  benchmarkUnknownWeightCount?: number | null;
  benchmarkSourceArtifactSha256?: string | null;
  benchmarkSourceStorageUri?: string | null;
}

export interface ActiveShareChangeBreakdownDto {
  earlierDate: string;
  laterDate: string;
  changeCategory: 'PORTFOLIO_WEIGHT_CHANGE' | 'BENCHMARK_MEMBERSHIP_CHANGE' | 'CLASSIFICATION_CHANGE' | 'COVERAGE_IDENTITY_CHANGE' | 'UNRESOLVED_CHANGE' | string;
  isin: string;
  securityName: string;
  description: string;
  priorWeightPct: number | null;
  laterWeightPct: number | null;
  weightImpactPct: number | null;
  sourceEvidence: string;
  epistemicCaution: string;
}

export interface ActiveShareReturnAssociationDto {
  periodStartDate: string;
  periodEndDate: string;
  startOffBenchmarkAllocationPct: number | null;
  endOffBenchmarkAllocationPct: number | null;
  fundReturnPct: number | null;
  benchmarkReturnPct: number | null;
  activeReturnPct: number | null;
  sampleDays: number;
  associationLabel: 'DESCRIPTIVE_ASSOCIATION_ONLY' | string;
  methodologicalCaveat: string;
}

export interface ActiveShareTrajectoryResponseDto {
  schemeOptionId: number;
  fundName: string;
  planType: string;
  optionType: string;
  benchmarkId: number;
  benchmarkCode: string;
  benchmarkName: string;
  overallStatus: 'VERIFIED_COMPLETE' | 'GATED_INCOMPLETE_BENCHMARK_WEIGHTS' | 'UNVERIFIED_SOURCE' | 'VERIFIED_PARTIAL' | 'INSUFFICIENT_DATA' | string;
  statusReason: string;
  methodologyVersion: string;
  calculationRunId: string;
  knowledgeCutoff: string;
  totalSnapshotsEvaluated: number;
  eligibleObservationsCount: number;
  skippedPeriodsCount: number;
  missingDataRequirements: string[];
  observations: ActiveShareObservationDto[];
  apparentChanges: ActiveShareChangeBreakdownDto[];
  returnAssociations: ActiveShareReturnAssociationDto[];
  epistemicDisclaimers: string[];
}

export async function fetchActiveShareTrajectory(
  schemeOptionId: number,
  asOfDate?: string,
  startDate?: string,
  endDate?: string,
  knowledgeCutoff?: string
): Promise<ActiveShareTrajectoryResponseDto> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<ActiveShareTrajectoryResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/active-share${qs}`);
}

// ---------------------------------------------------------------------------
// POINT-IN-TIME BENCHMARK FACTSHEET ARCHIVE & COVERAGE AUDIT API
// ---------------------------------------------------------------------------

export interface BenchmarkFactsheetSnapshotDto {
  effectiveDate: string;
  availabilityTime: string;
  sourceArtifactId: number;
  sourceArtifactSha256: string;
  sourceStorageUri: string;
  sourcePublisher: string;
  totalConstituentsCount: number;
  weightedConstituentsCount: number;
  unknownWeightConstituentsCount: number;
  disclosedWeightSumPct: number;
  membershipCoveragePct: number;
  identifierMappingCoveragePct: number;
  weightCoveragePct: number;
  membershipCoverageStatus: string;
  mappingCoverageStatus: string;
  weightCoverageStatus: string;
  sourceConfidenceStatus: string;
  unmetDataDependencies: string;
  notes: string;
}

export interface BenchmarkFactsheetArchiveResponseDto {
  benchmarkId: number;
  benchmarkCode: string;
  benchmarkName: string;
  publisher: string;
  methodologyVersion: string;
  knowledgeCutoff: string;
  totalSnapshotsArchived: number;
  snapshots: BenchmarkFactsheetSnapshotDto[];
  historicalCoverageGaps: string[];
  governanceDisclosures: string[];
}

export async function fetchBenchmarkFactsheetArchive(
  benchmarkCode = 'NIFTY_500_TRI',
  knowledgeCutoff?: string
): Promise<BenchmarkFactsheetArchiveResponseDto> {
  const query = knowledgeCutoff ? `?knowledgeCutoff=${encodeURIComponent(knowledgeCutoff)}` : '';
  return apiFetch<BenchmarkFactsheetArchiveResponseDto>(`/api/v1/benchmarks/${benchmarkCode}/factsheet-archive${query}`);
}

// ---------------------------------------------------------------------------
// POINT-IN-TIME SECTOR & INDUSTRY EXPOSURE DRIFT API
// ---------------------------------------------------------------------------

export interface FundSectorExposureDto {
  sectorName: string;
  fundWeightPct: number;
  holdingCount: number;
  classificationProvenance: 'BENCHMARK_PIT_CLASSIFICATION' | 'FUND_DISCLOSURE_MAPPED' | 'ASSET_CLASS_CASH' | 'ASSET_CLASS_DEBT' | 'UNCLASSIFIED' | string;
  topHoldings: string[];
}

export interface BenchmarkSectorExposureDto {
  sectorName: string;
  disclosedWeightPct: number | null;
  weightedConstituentsCount: number;
  totalConstituentsCount: number;
  constituentDisclosedCoveragePct: number;
  weightStatus: 'PARTIAL_DISCLOSURE_ONLY' | 'COMPLETE_DISCLOSURE' | 'NO_DISCLOSED_WEIGHTS' | string;
}

export interface SectorActiveComparisonDto {
  sectorName: string;
  fundWeightPct: number;
  benchmarkDisclosedWeightPct: number | null;
  definitiveActiveWeightPct: number | null;
  comparisonStatus: 'GATED_INCOMPLETE_BENCHMARK_WEIGHTS' | 'VERIFIED_COMPLETE' | 'TEMPORAL_MISMATCH' | 'BENCHMARK_UNAVAILABLE' | 'FUND_ONLY_SECTOR' | string;
  statusReason: string;
  fundHoldingCount: number;
  benchmarkConstituentCount: number;
  benchmarkWeightedConstituentCount: number;
}

export interface FundIndustryExposureDto {
  industryName: string;
  parentSectorName: string;
  classificationLevel: string;
  fundWeightPct: number;
  holdingCount: number;
  classificationSource: string;
  classificationProvenance: string;
  effectiveDate: string;
  availabilityTime: string;
  classificationCoveragePct: number;
  topHoldings: string[];
}

export interface IndustryActiveComparisonDto {
  industryName: string;
  parentSectorName: string;
  fundWeightPct: number;
  benchmarkDisclosedWeightPct: number | null;
  definitiveActiveWeightPct: number | null;
  comparisonStatus: 'GATED_INCOMPLETE_BENCHMARK_WEIGHTS' | 'VERIFIED_COMPLETE' | 'TEMPORAL_MISMATCH' | 'BENCHMARK_UNAVAILABLE' | 'FUND_ONLY_INDUSTRY' | string;
  statusReason: string;
  fundHoldingCount: number;
  benchmarkConstituentCount: number;
  benchmarkWeightedConstituentCount: number;
}

export interface SecurityExposureWaterfallDto {
  securityId: number | null;
  companyName: string;
  isin: string | null;
  symbol: string | null;
  parentSectorName: string;
  industryName: string;
  fundWeightPct: number;
  benchmarkWeightPct: number | null;
  activeSpreadPct: number | null;
  exposureCategory: 'COMPARABLE_ACTIVE_EXPOSURE' | 'BENCHMARK_MEMBER_WEIGHT_UNKNOWN' | 'OFF_BENCHMARK_HOLDING' | 'UNRESOLVED_MEMBERSHIP' | 'BENCHMARK_UNAVAILABLE' | string;
  membershipStatus: 'BENCHMARK_CONSTITUENT_WEIGHTED' | 'BENCHMARK_CONSTITUENT_UNWEIGHTED' | 'OFF_BENCHMARK' | 'UNRESOLVED' | string;
  classificationProvenance: string;
  mappingStatus: string;
  inclusionReason: string;
}

export interface SectorDriftObservationDto {
  portfolioSnapshotId: number;
  portfolioDate: string;
  portfolioAvailabilityTime: string;
  portfolioDisclosureScope: 'FULL_PORTFOLIO_DISCLOSURE' | 'PARTIAL_TOP_HOLDINGS' | string;
  sumFundWeightsPct: number;
  fundHoldingsCount: number;
  benchmarkId: number;
  benchmarkCode: string;
  benchmarkDate: string | null;
  benchmarkAvailabilityTime: string | null;
  benchmarkWeightCoveragePct: number;
  benchmarkConstituentsCount: number;
  benchmarkWeightedCount: number;
  snapshotDateDiffDays: number;
  isTemporallyComparable: boolean;
  observationStatus: 'VERIFIED_COMPLETE' | 'GATED_INCOMPLETE_BENCHMARK_WEIGHTS' | 'PARTIAL_PORTFOLIO_DISCLOSURE' | 'TEMPORAL_MISMATCH' | 'UNAVAILABLE' | string;
  statusReason: string;
  sectors: FundSectorExposureDto[];
  benchmarkSectors: BenchmarkSectorExposureDto[];
  sectorComparisons: SectorActiveComparisonDto[];
  fundClassificationCoveragePct: number;
  sourceArtifactUri: string | null;
  sourceArtifactSha256: string | null;
  industries: FundIndustryExposureDto[];
  industryComparisons: IndustryActiveComparisonDto[];
  securityWaterfall: SecurityExposureWaterfallDto[];
}

export interface SectorDriftChangeDto {
  earlierDate: string;
  laterDate: string;
  sectorName: string;
  earlierWeightPct: number;
  laterWeightPct: number;
  deltaWeightPct: number;
  changeDriver: 'PORTFOLIO_WEIGHT_ADJUSTMENT' | 'SECURITY_ENTRY_EXIT' | 'DISCLOSURE_SCOPE_SHIFT' | 'CLASSIFICATION_SHIFT' | 'UNRESOLVED_DISCREPANCY' | string;
  driverExplanation: string;
  earlierHoldingCount: number;
  laterHoldingCount: number;
}

export interface IndustryDriftChangeDto {
  earlierDate: string;
  laterDate: string;
  parentSectorName: string;
  industryName: string;
  earlierWeightPct: number;
  laterWeightPct: number;
  deltaWeightPct: number;
  changeDriver: 'PORTFOLIO_WEIGHT_ADJUSTMENT' | 'SECURITY_ENTRY_EXIT' | 'DISCLOSURE_SCOPE_SHIFT' | 'CLASSIFICATION_SHIFT' | 'UNRESOLVED_DISCREPANCY' | string;
  driverExplanation: string;
  earlierHoldingCount: number;
  laterHoldingCount: number;
}

export interface SectorDriftResponseDto {
  schemeOptionId: number;
  fundName: string;
  planType: string;
  optionType: string;
  benchmarkId: number;
  benchmarkCode: string;
  benchmarkName: string;
  overallStatus: 'PARTIAL_SECTOR_VIEW_GATED_BENCHMARK' | 'VERIFIED_COMPLETE' | 'INSUFFICIENT_DATA' | string;
  statusReason: string;
  methodologyVersion: string;
  runId: string;
  knowledgeCutoff: string;
  totalSnapshotsFound: number;
  eligibleObservationsCount: number;
  skippedPeriodsCount: number;
  observations: SectorDriftObservationDto[];
  sectorDriftChanges: SectorDriftChangeDto[];
  industryDriftChanges: IndustryDriftChangeDto[];
  dataQualityLimitations: string[];
  missingDataRequirements: string[];
  investigationPrompts: string[];
}

export async function fetchSectorDrift(
  schemeOptionId: number,
  asOfDate?: string,
  startDate?: string,
  endDate?: string,
  knowledgeCutoff?: string
): Promise<SectorDriftResponseDto> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<SectorDriftResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/sector-drift${qs}`);
}

export interface MarketCapBucketExposureDto {
  category: 'LARGE_CAP' | 'MID_CAP' | 'SMALL_CAP' | 'CASH' | 'DEBT_OTHER' | 'UNCLASSIFIED' | string;
  categoryLabel: string;
  reportedWeightPct: number;
  normalizedWeightPct: number;
  holdingCount: number;
  topHoldings: string[];
  classificationSource: string;
  rankingPeriod: string | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  provenance: 'VERIFIED_HISTORICAL_PIT' | 'APPROXIMATE_REFERENCE' | 'ASSET_CLASS_DISCLOSURE' | 'UNMAPPED' | string;
}

export interface MarketCapReclassificationItemDto {
  isin: string;
  securityName: string;
  earlierCategory: string;
  earlierRank: number | null;
  laterCategory: string;
  laterRank: number | null;
  earlierWeightPct: number;
  laterWeightPct: number;
  weightDeltaPct: number;
  rankingPeriodChange: string;
  explanation: string;
}

export interface MarketCapBenchmarkComparisonDto {
  benchmarkId: number | null;
  benchmarkCode: string | null;
  benchmarkName: string | null;
  benchmarkDate: string | null;
  benchmarkAvailabilityTime: string | null;
  benchmarkConstituentsCount: number;
  weightedConstituentsCount: number;
  benchmarkWeightCoveragePct: number;
  isWeightCoverageComplete: boolean;
  benchmarkLargeCapDisclosedWeightPct: number;
  benchmarkMidCapDisclosedWeightPct: number;
  benchmarkSmallCapDisclosedWeightPct: number;
  gatingStatus: 'GATED_INCOMPLETE_BENCHMARK_WEIGHTS' | 'VERIFIED_COMPLETE' | 'BENCHMARK_UNAVAILABLE' | 'TEMPORAL_MISMATCH' | string;
  gatingReason: string;
  disclaimer: string;
}

export interface MarketCapObservationDto {
  snapshotId: number;
  portfolioDate: string;
  availabilityTime: string;
  portfolioDisclosureScope: 'FULL_PORTFOLIO_DISCLOSURE' | 'PARTIAL_TOP_HOLDINGS' | string;
  sumReportedWeightsPct: number;
  weightReconciliationDeltaPct: number;
  totalHoldingsCount: number;
  classificationCoverageCountPct: number;
  classificationCoverageWeightPct: number;
  unclassifiedWeightPct: number;
  unclassifiedHoldingCount: number;
  sourceRankingPeriod: string | null;
  sourcePublicationDate: string | null;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  classificationSourceArtifactId: number | null;
  classificationSourceArtifactSha256: string | null;
  classificationStatus: 'VERIFIED_HISTORICAL_PIT' | 'APPROXIMATE_REFERENCE' | 'UNAVAILABLE' | string;
  bucketExposures: MarketCapBucketExposureDto[];
  benchmarkComparison: MarketCapBenchmarkComparisonDto;
}

export interface MarketCapDriftChangeDto {
  earlierDate: string;
  laterDate: string;
  category: string;
  earlierWeightPct: number;
  laterWeightPct: number;
  deltaWeightPct: number;
  weightChangeComponent: number;
  reclassificationComponent: number;
  disclosureScopeComponent: number;
  unresolvedComponent: number;
  isReconciled: boolean;
  reconciliationTolerance: number;
  primaryDriver: 'PORTFOLIO_WEIGHT_ADJUSTMENT' | 'SECURITY_ENTRY_EXIT' | 'MARKET_CAP_RECLASSIFICATION' | 'DISCLOSURE_SCOPE_SHIFT' | 'UNRESOLVED_DISCREPANCY' | string;
  isComparable: boolean;
  nonComparableReason: string | null;
  explanation: string;
  earlierCoveragePct: number;
  laterCoveragePct: number;
  earlierHoldingCount: number;
  laterHoldingCount: number;
  reclassifiedHoldings: MarketCapReclassificationItemDto[];
}

export interface MarketCapDriftResponseDto {
  schemeOptionId: number;
  fundName: string;
  planType: string;
  optionType: string;
  amfiCode: string | null;
  schemeCode: string | null;
  benchmarkId: number | null;
  benchmarkCode: string | null;
  benchmarkName: string | null;
  status: string;
  message: string;
  methodologyVersion: string;
  runId: string;
  knowledgeCutoff: string;
  totalSnapshotsFound: number;
  eligibleSnapshotsCount: number;
  skippedSnapshotsCount: number;
  observations: MarketCapObservationDto[];
  transitions: MarketCapDriftChangeDto[];
  allReclassifiedHoldings: MarketCapReclassificationItemDto[];
  dataQualityWarnings: string[];
  investigationQuestions: string[];
  disclaimers: string[];
}

export async function fetchMarketCapDrift(
  schemeOptionId: number,
  asOfDate?: string,
  startDate?: string,
  endDate?: string,
  knowledgeCutoff?: string
): Promise<MarketCapDriftResponseDto> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<MarketCapDriftResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/market-cap-drift${qs}`);
}

// ==========================================
// POINT-IN-TIME HOLDING LONGEVITY & TURNOVER
// ==========================================

export interface PositionTenureBucketSummaryDto {
  bucketKey: string;
  bucketLabel: string;
  holdingsCount: number;
  totalReportedWeightPct: number;
  portfolioWeightSharePct: number;
  topHoldings: string[];
}

export interface PositionWeightPointDto {
  portfolioDate: string;
  reportedWeightPct: number;
  holdingRank: number | null;
  isPresent: boolean;
  disclosureScope: string;
}

export interface PositionLongevityItemDto {
  securityId: number | null;
  isin: string | null;
  canonicalName: string;
  firstObservedDate: string | null;
  lastObservedDate: string | null;
  appearanceCount: number;
  spanObservationsCount: number;
  appearanceRatioPct: number;
  observedTenureDays: number;
  observedTenureMonths: number;
  observedTenureBucket: string;
  presenceStatus: string;
  persistenceCategory: string;
  isTop10Holding: boolean;
  latestRank: number | null;
  latestReportedWeightPct: number;
  averageObservedWeightPct: number;
  minObservedWeightPct: number;
  maxObservedWeightPct: number;
  weightRangePct: number;
  weightStandardDeviationPct: number;
  consecutiveObservationsCount: number;
  weightTrajectory: PositionWeightPointDto[];
}

export interface CompositionChangeTransitionDto {
  earlierDate: string;
  laterDate: string;
  daysBetween: number;
  earlierDisclosureScope: string;
  laterDisclosureScope: string;
  comparabilityStatus: string;
  comparabilityReason: string;
  isComparable: boolean;
  newEntriesCount: number;
  newEntriesWeightPct: number;
  completeExitsCount: number;
  completeExitsWeightPct: number;
  continuingPositionsCount: number;
  continuingWeightIncreaseSumPct: number;
  continuingWeightDecreaseSumPct: number;
  sumAbsWeightChangesPct: number;
  halfSumCompositionChangePct: number;
  unresolvedDiscrepancyPct: number;
  topEntries: string[];
  topExits: string[];
  largestWeightChanges: string[];
}

export interface TurnoverEvaluationDto {
  turnoverStatus: string;
  turnoverReason: string;
  isTurnoverCalculated: boolean;
  reportedRegulatoryTurnoverRatioPct: number | null;
  descriptiveChangeFormula: string;
  averageMonthlyCompositionChangePct: number;
  annualizedDescriptiveChangePct: number;
  comparableTransitionsCount: number;
  totalTransitionsCount: number;
  limitationsAndDisclaimers: string[];
}

export interface HoldingSnapshotSummaryDto {
  snapshotId: number;
  portfolioDate: string;
  availabilityTime: string;
  reportedHoldingsCount: number;
  sumReportedWeightsPct: number;
  disclosureScope: string;
  sourceArtifactSha256: string | null;
  sourceUri: string;
}

export interface HoldingLongevityResponseDto {
  schemeOptionId: number;
  fundName: string;
  planType: string;
  optionType: string;
  amfiCode: string | null;
  schemeCode: string | null;
  analysisStatus: string;
  statusReason: string;
  methodologyVersion: string;
  calculationRunId: string;
  knowledgeCutoff: string;
  totalSnapshotsFound: number;
  eligibleSnapshotsCount: number;
  skippedPeriodsCount: number;
  observationWindowStartDate: string | null;
  observationWindowEndDate: string | null;
  snapshots: HoldingSnapshotSummaryDto[];
  tenureBuckets: PositionTenureBucketSummaryDto[];
  persistentHoldings: PositionLongevityItemDto[];
  allHoldings: PositionLongevityItemDto[];
  transitions: CompositionChangeTransitionDto[];
  turnoverEvaluation: TurnoverEvaluationDto;
  dataQualityWarnings: string[];
  investigationQuestions: string[];
  methodologyNotes: string[];
}

export async function fetchHoldingLongevity(
  schemeOptionId: number,
  asOfDate?: string,
  startDate?: string,
  endDate?: string,
  knowledgeCutoff?: string
): Promise<HoldingLongevityResponseDto> {
  const params = new URLSearchParams();
  if (asOfDate) params.append('asOfDate', asOfDate);
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);
  if (knowledgeCutoff) params.append('knowledgeCutoff', knowledgeCutoff);
  const qs = params.toString() ? `?${params.toString()}` : '';
  return apiFetch<HoldingLongevityResponseDto>(`/api/v1/schemes/options/${schemeOptionId}/holding-longevity${qs}`);
}

// ---------------------------------------------------------------------------
// MULTI-FUND PORTFOLIO LOOK-THROUGH, OVERLAP & SCENARIO SIMULATION API (Phase 2L)
// ---------------------------------------------------------------------------

export interface ResearchPortfolioFundItemDto {
  schemeOptionId: number;
  allocationWeight: number;
}

export interface MultiFundLookThroughRequestDto {
  funds: ResearchPortfolioFundItemDto[];
  asOfDate?: string;
  knowledgeCutoff?: string;
  rebalanceMethod?: string;
}

export interface ConstituentFundSummaryDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string | null;
  isin: string | null;
  rawWeight: number;
  normalizedWeightPct: number;
  observationDate: string | null;
  holdingsCount: number;
  disclosedEquityCoveragePct: number;
  cashWeightPct: number;
  temporalMismatchDays: number;
  status: string;
}

export interface FundSecurityContributionDto {
  schemeOptionId: number;
  fundName: string;
  fundWeightInPortfolioPct: number;
  holdingWeightInFundPct: number;
  effectiveWeightContributionPct: number;
}

export interface ConsolidatedSecurityExposureDto {
  canonicalName: string;
  isin: string | null;
  sector: string;
  assetClass: string;
  marketCapCategory: string;
  consolidatedWeightPct: number;
  fundCount: number;
  contributions: FundSecurityContributionDto[];
  identityConfidence: string;
  classificationProvenance: string;
}

export interface FundSectorContributionDto {
  schemeOptionId: number;
  fundName: string;
  sectorWeightInFundPct: number;
  effectiveContributionPct: number;
}

export interface SectorTopSecurityDto {
  canonicalName: string;
  isin: string | null;
  consolidatedWeightPct: number;
  fundCount: number;
}

export interface ConsolidatedSectorExposureDto {
  macroSector: string;
  consolidatedWeightPct: number;
  classifiedSecuritiesCount: number;
  fundContributions: FundSectorContributionDto[];
  classificationProvenance: string;
  topSecurities?: SectorTopSecurityDto[];
}

export interface ConsolidatedMarketCapExposureDto {
  category: string;
  consolidatedWeightPct: number;
  securitiesCount: number;
  classificationProvenance: string;
}

export interface SharedSecurityItemDto {
  securityName: string;
  isin: string | null;
  sector: string;
  weightInA: number;
  weightInB: number;
  overlapWeightPct: number;
}

export interface PairwiseOverlapResultDto {
  fundAId: number;
  fundAName: string;
  fundBId: number;
  fundBName: string;
  overlapWeightPct: number;
  sharedSecurityCount: number;
  observationDateA: string | null;
  observationDateB: string | null;
  temporalDeltaDays: number;
  comparabilityState: string;
  topSharedSecurities: SharedSecurityItemDto[];
}

export interface PortfolioConcentrationMetricsDto {
  top5WeightPct: number;
  top10WeightPct: number;
  herfindahlHirschmanIndex: number;
  hhiInterpretation: string;
  uniqueSecuritiesCount: number;
  sharedSecuritiesCount: number;
  sharedSecuritiesWeightPct: number;
  disclosedEquityCoveragePct: number;
  cashAndEquivalentsWeightPct: number;
  unclassifiedWeightPct: number;
}

export interface SharedExposureDiagnosticDto {
  severity: 'INFO' | 'WARNING' | 'NOTE';
  title: string;
  diagnostic: string;
  evidence: string;
}

export interface ConstituentFundRiskProfileDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string | null;
  allocationWeightPct: number;
  annualizedVolatilityPct: number;
  downsideDeviationPct: number;
  maxDrawdownPct: number;
  betaToBenchmark: number | null;
  returnObservationsCount: number;
  riskCoverageState: string;
}

export interface PairwiseCorrelationDto {
  fundAId: number;
  fundAName: string;
  fundBId: number;
  fundBName: string;
  correlationCoefficient: number;
  commonTradingDays: number;
}

export interface FactorExposureItemDto {
  factorCode: string;
  factorName: string;
  category: string;
  exposureValue: string;
  status: string;
  economicDefinition: string;
  requiredInputs: string;
  calculationMethod: string;
  evidenceNotes: string;
}

export interface PortfolioRiskDecompositionDto {
  evaluationStatus: string;
  benchmarkCode: string;
  benchmarkName: string;
  returnStartDate: string | null;
  returnEndDate: string | null;
  alignedTradingDaysCount: number;
  portfolioRealizedVolatilityPct: number | null;
  weightedAverageFundVolatilityPct: number | null;
  diversificationBenefitPct: number | null;
  portfolioMaxDrawdownPct: number | null;
  portfolioDownsideDeviationPct: number | null;
  portfolioBeta: number | null;
  benchmarkRealizedVolatilityPct: number | null;
  benchmarkMaxDrawdownPct: number | null;
  constituentRiskProfiles: ConstituentFundRiskProfileDto[];
  pairwiseCorrelations: PairwiseCorrelationDto[];
  factorExposures: FactorExposureItemDto[];
  rebalancingConvention: string;
  riskObservations: string[];
  dataLimitations: string[];
}

export interface MultiFundLookThroughResponseDto {
  asOfDate: string;
  knowledgeCutoff: string;
  inputTotalAllocation: number;
  isFullyAllocated: boolean;
  qualityState: string;
  constituentFunds: ConstituentFundSummaryDto[];
  consolidatedSecurities: ConsolidatedSecurityExposureDto[];
  consolidatedSectors: ConsolidatedSectorExposureDto[];
  consolidatedMarketCap: ConsolidatedMarketCapExposureDto[];
  pairwiseOverlapMatrix: PairwiseOverlapResultDto[];
  concentrationMetrics: PortfolioConcentrationMetricsDto;
  sharedDiagnostics: SharedExposureDiagnosticDto[];
  limitations: string[];
  epistemicDisclosure: Record<string, string>;
  riskDecomposition?: PortfolioRiskDecompositionDto | null;
}

export interface CandidateSimulationRequestDto {
  currentFunds: ResearchPortfolioFundItemDto[];
  candidateSchemeOptionId: number;
  proposedAllocationPct: number;
  fundingMethod?: string;
  asOfDate?: string;
  knowledgeCutoff?: string;
}

export interface AugmentedSecurityComparisonDto {
  securityName: string;
  isin: string | null;
  sector: string;
  baselineWeightPct: number;
  candidateContributionPct: number;
  simulatedWeightPct: number;
  deltaWeightPct: number;
}

export interface CategoryDeltaDto {
  categoryName: string;
  baselineWeightPct: number;
  simulatedWeightPct: number;
  deltaWeightPct: number;
}

export interface CandidateSimulationResponseDto {
  candidateSchemeOptionId: number;
  candidateFundName: string;
  candidateAmfiCode: string | null;
  proposedAllocationPct: number;
  fundingMethod: string;
  baselinePortfolio: MultiFundLookThroughResponseDto;
  simulatedPortfolio: MultiFundLookThroughResponseDto;
  deltaTop5WeightPct: number;
  deltaTop10WeightPct: number;
  deltaHhi: number;
  deltaSharedSecuritiesWeightPct: number;
  deltaUniqueSecuritiesCount: number;
  newSecuritiesIntroducedCount: number;
  augmentedSecuritiesCount: number;
  topAugmentedSecurities: AugmentedSecurityComparisonDto[];
  topNewSecurities: ConsolidatedSecurityExposureDto[];
  sectorDeltas: CategoryDeltaDto[];
  marketCapDeltas: CategoryDeltaDto[];
  analyticalObservations: string[];
  limitations: string[];
  deltaVolatilityPct?: number | null;
  deltaDiversificationBenefitPct?: number | null;
  deltaBeta?: number | null;
  deltaMaxDrawdownPct?: number | null;
}

export interface SavedResearchPortfolioDto {
  portfolioName: string;
  funds: ResearchPortfolioFundItemDto[];
  totalAllocationPct: number;
  isValid: boolean;
}

export async function fetchMultiFundLookThrough(
  request: MultiFundLookThroughRequestDto
): Promise<MultiFundLookThroughResponseDto> {
  return apiFetch<MultiFundLookThroughResponseDto>('/api/v1/portfolio/multi-fund/look-through', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function simulateCandidateFund(
  request: CandidateSimulationRequestDto
): Promise<CandidateSimulationResponseDto> {
  return apiFetch<CandidateSimulationResponseDto>('/api/v1/portfolio/multi-fund/candidate-simulation', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function fetchDefaultPilotPortfolio(): Promise<MultiFundLookThroughRequestDto> {
  return apiFetch<MultiFundLookThroughRequestDto>('/api/v1/portfolio/multi-fund/default-pilot');
}

export async function fetchSavedResearchPortfolio(): Promise<SavedResearchPortfolioDto> {
  return apiFetch<SavedResearchPortfolioDto>('/api/v1/portfolio/multi-fund/saved');
}

export async function saveResearchPortfolio(
  payload: SavedResearchPortfolioDto
): Promise<SavedResearchPortfolioDto> {
  return apiFetch<SavedResearchPortfolioDto>('/api/v1/portfolio/multi-fund/saved', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

// ============================================================================
// Historical Market-Regime Replay & Portfolio Stress Testing Interfaces
// ============================================================================

export interface HistoricalMarketEpisodeDto {
  episodeId: string;
  title: string;
  category: string;
  startDate: string;
  endDate: string;
  peakTroughConvention: string;
  regimeType: string;
  description: string;
  isAvailable: boolean;
  unavailabilityReason: string | null;
  tradingDaysCount: number | null;
  benchmarkTotalReturnPct: number | null;
  benchmarkCode?: string | null;
}

export interface TrajectoryPointDto {
  date: string;
  portfolioCumulativeReturnPct: number;
  benchmarkCumulativeReturnPct: number;
  activeReturnPct: number;
  portfolioDrawdownPct: number;
  benchmarkDrawdownPct: number;
}

export interface ConstituentReplayResultDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string | null;
  allocationWeightPct: number;
  cumulativeReturnPct: number;
  contributionPct: number;
  standaloneContributionPct: number;
  annualizedVolatilityPct: number | null;
  maxDrawdownPct: number;
  worstDailyReturnPct: number;
  observationCount: number;
}

export interface SectorExposureContextItemDto {
  macroSector: string;
  weightPct: number;
}

export interface StressExposureContextDto {
  observationDate: string | null;
  daysMismatchFromEpisodeEnd: number;
  comparabilityState: string;
  largeCapWeightPct: number;
  midCapWeightPct: number;
  smallCapWeightPct: number;
  cashWeightPct: number;
  unclassifiedWeightPct: number;
  topSectors: SectorExposureContextItemDto[];
  epistemicWarning: string | null;
}

export interface ConstituentSensitivityDto {
  schemeOptionId: number;
  fundName: string;
  allocationWeightPct: number;
  betaToBenchmark: number;
  estimatedLossPct: number;
  contributionPct: number;
}

export interface ModeledSensitivityScenarioDto {
  scenarioId: string;
  shockLabel: string;
  benchmarkShockPct: number;
  estimatedPortfolioLossPct: number;
  betaUsed: number;
  epistemicStatus: string;
  constituentEstimates: ConstituentSensitivityDto[];
  omittedEffects: string[];
}

export interface PortfolioStressReplayRequestDto {
  funds: ResearchPortfolioFundItemDto[];
  episodeId?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  benchmarkCode?: string | null;
  knowledgeCutoff?: string | null;
}

export interface PortfolioStressReplayResponseDto {
  status: string;
  episodeId: string;
  episodeTitle: string;
  startDate: string;
  endDate: string;
  alignedTradingDaysCount: number;
  portfolioCumulativeReturnPct: number;
  benchmarkCumulativeReturnPct: number;
  activeReturnPct: number;
  portfolioAnnualizedVolatilityPct: number | null;
  benchmarkAnnualizedVolatilityPct: number | null;
  portfolioDownsideDeviationPct: number | null;
  portfolioMaxDrawdownPct: number;
  benchmarkMaxDrawdownPct: number;
  peakDate: string | null;
  troughDate: string | null;
  recoveryDate: string | null;
  worstDailyReturnPct: number;
  worstDailyReturnDate: string | null;
  benchmarkWorstDailyReturnPct: number | null;
  constituentResults: ConstituentReplayResultDto[];
  trajectory: TrajectoryPointDto[];
  exposureContext: StressExposureContextDto;
  modeledScenarios: ModeledSensitivityScenarioDto[];
  epistemicClassifications: Record<string, string>;
  limitations: string[];
  rebalancingConvention: string;
  attributionConvention: string;
  standaloneSumReturnPct: number;
  compoundingResidualPct: number;
  attributionResidualPct: number;
  benchmarkCode: string;
  benchmarkName: string;
  benchmarkId: number;
}

export async function fetchStressReplayEpisodes(
  funds: ResearchPortfolioFundItemDto[],
  knowledgeCutoff?: string,
  benchmarkCode?: string
): Promise<HistoricalMarketEpisodeDto[]> {
  return apiFetch<HistoricalMarketEpisodeDto[]>('/api/v1/portfolio/multi-fund/stress-replay/episodes', {
    method: 'POST',
    body: JSON.stringify({ funds, knowledgeCutoff, benchmarkCode }),
  });
}

export async function executePortfolioStressReplay(
  request: PortfolioStressReplayRequestDto
): Promise<PortfolioStressReplayResponseDto> {
  return apiFetch<PortfolioStressReplayResponseDto>('/api/v1/portfolio/multi-fund/stress-replay', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

// ---------------------------------------------------------------------------
// Phase 2N: Investor Portfolio Scenario Lab & Risk Investigation
// ---------------------------------------------------------------------------

export type ScenarioType =
  | 'BROAD_MARKET'
  | 'MARKET_CAP'
  | 'SECTOR'
  | 'COMBINED'
  | 'CUSTOM';

export interface ScenarioTemplateDto {
  id: string;
  name: string;
  category: 'BROAD_MARKET' | 'MARKET_CAP' | 'SECTOR' | 'COMBINED';
  description: string;
  broadMarketShockPct?: number | null;
  marketCapShocks?: Record<string, number> | null;
  sectorShocks?: Record<string, number> | null;
  combinationMethod?: string | null;
  notes: string[];
}

export interface ScenarioConstituentImpactDto {
  schemeOptionId: number;
  fundName: string;
  amcName: string;
  portfolioWeightPct: number;
  normalizedWeightPct: number;
  constituentShockPct: number;
  weightedContributionPct: number;
  fundBeta: number | null;
  holdingsSnapshotDate: string | null;
  coveredHoldingsWeightPct: number;
  unmodeledHoldingsWeightPct: number;
  marketCapCoverages: Record<string, number>;
  sectorCoverages: Record<string, number>;
  epistemicStatus: string;
  calculationMethod: string;
  notes: string[];
}

export interface ScenarioExposureImpactDto {
  dimensionCategory: string;
  dimensionName: string;
  portfolioWeightPct: number;
  coverageWeightPct: number;
  shockPct: number;
  weightedContributionPct: number;
  coverageState: 'COVERED' | 'PARTIAL' | 'UNMODELED' | 'EXCLUDED' | string;
  overlapRule: string;
  notes: string;
}

export interface ScenarioLabResponseDto {
  scenarioName: string;
  scenarioType: ScenarioType;
  calculationTimestamp: string;
  asOfDate: string;
  knowledgeCutoff: string;
  benchmarkCode: string;
  benchmarkName: string;
  epistemicLabel: string;
  isGated: boolean;
  gatingReason: string | null;
  portfolioShockPct: number;
  modeledPortfolioShockPct: number;
  coveredPortfolioWeightPct: number;
  unmodeledPortfolioWeightPct: number;
  portfolioBeta: number | null;
  betaObservationCount: number;
  betaEstimationWindowDays: number;
  betaAvailable: boolean;
  betaGatingReason: string | null;
  combinationMethod: string;
  overlapDisclosures: string[];
  assumptions: string[];
  dataLimitations: string[];
  epistemicClassifications: Record<string, string>;
  investigationQuestions: string[];
  constituents: ScenarioConstituentImpactDto[];
  exposures: ScenarioExposureImpactDto[];
}

export interface ScenarioLabRequestDto {
  funds: ResearchPortfolioFundItemDto[];
  asOfDate?: string;
  knowledgeCutoff?: string;
  benchmarkCode?: string;
  scenarioType: ScenarioType;
  scenarioName?: string;
  scenarioDescription?: string;
  broadMarketShockPct?: number | null;
  marketCapShocks?: Record<string, number> | null;
  sectorShocks?: Record<string, number> | null;
  combinationMethod?: 'ADDITIVE_EXCESS_TILTS' | 'SEQUENTIAL' | 'STANDALONE_MAX' | string | null;
  unmodeledShockPct?: number | null;
}

export interface ScenarioConstituentDeltaDto {
  schemeOptionId: number;
  fundName: string;
  portfolioWeightPct: number;
  scenarioAShockPct: number;
  scenarioBShockPct: number;
  deltaShockPct: number;
  scenarioAContributionPct: number;
  scenarioBContributionPct: number;
  deltaContributionPct: number;
}

export interface ScenarioExposureDeltaDto {
  dimensionCategory: string;
  dimensionName: string;
  portfolioWeightPct: number;
  scenarioAShockPct: number;
  scenarioBShockPct: number;
  deltaShockPct: number;
  scenarioAContributionPct: number;
  scenarioBContributionPct: number;
  deltaContributionPct: number;
}

export interface ScenarioComparisonResponseDto {
  scenarioA: ScenarioLabResponseDto;
  scenarioB: ScenarioLabResponseDto;
  isComparable: boolean;
  nonComparableReasons: string[];
  deltaPortfolioShockPct: number;
  deltaModeledShockPct: number;
  deltaCoveredWeightPct: number;
  constituentDeltas: ScenarioConstituentDeltaDto[];
  exposureDeltas: ScenarioExposureDeltaDto[];
  divergenceExplanation: string;
}

export interface ScenarioComparisonRequestDto {
  scenarioA: ScenarioLabRequestDto;
  scenarioB: ScenarioLabRequestDto;
}

export interface ScenarioDimensionMetadataDto {
  asOfDate: string;
  knowledgeCutoff: string;
  benchmarkCode: string;
  benchmarkName: string;
  isBetaAvailable: boolean;
  portfolioBeta: number | null;
  betaObservationCount: number;
  betaGatingReason: string | null;
  marketCapWeights: Record<string, number>;
  sectorWeights: Record<string, number>;
  coveredWeightPct: number;
  unmodeledWeightPct: number;
  availableTemplates: ScenarioTemplateDto[];
  supportedDimensions: string[];
  dataLimitations: string[];
}

export async function fetchScenarioLabMetadata(
  funds: ResearchPortfolioFundItemDto[],
  benchmarkCode?: string,
  asOfDate?: string,
  knowledgeCutoff?: string
): Promise<ScenarioDimensionMetadataDto> {
  return apiFetch<ScenarioDimensionMetadataDto>('/api/v1/portfolio/multi-fund/scenario-lab/metadata', {
    method: 'POST',
    body: JSON.stringify({ funds, benchmarkCode, asOfDate, knowledgeCutoff }),
  });
}

export async function executeScenarioLab(
  request: ScenarioLabRequestDto
): Promise<ScenarioLabResponseDto> {
  return apiFetch<ScenarioLabResponseDto>('/api/v1/portfolio/multi-fund/scenario-lab/run', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function compareScenarioLab(
  request: ScenarioComparisonRequestDto
): Promise<ScenarioComparisonResponseDto> {
  return apiFetch<ScenarioComparisonResponseDto>('/api/v1/portfolio/multi-fund/scenario-lab/compare', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

// ---------------------------------------------------------------------------
// PORTFOLIO RISK ATTRIBUTION (PHASE 2O)
// ---------------------------------------------------------------------------

export interface RiskOverviewMetricDto {
  metricCode: string;
  metricName: string;
  numericValue: number | null;
  formattedValue: string | null;
  unit: string;
  description: string;
  availabilityState: string;
}

export interface FundRiskContributionDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string;
  planType: string;
  allocationWeightPct: number;
  weightFraction: number;
  annualizedVolatilityPct: number;
  marginalContributionPct: number;
  volatilityContributionPct: number;
  percentageRiskContribution: number;
  isNegativeContribution: boolean;
  isDiversificationDampener: boolean;
  covarianceWithPortfolio: number;
  interpretation: string;
}

export interface RiskReconciliationDto {
  portfolioVolatilityPct: number;
  sumRiskContributionsPct: number;
  reconciliationDifferencePct: number;
  sumPercentageContributions: number;
  isReconciled: boolean;
  reconciliationStatus: string;
  mathematicalIdentityFormula: string;
}

export interface AttributionSectorExposureDto {
  sectorName: string;
  portfolioWeightPct: number;
  fundContributions: Record<string, number>;
}

export interface AttributionMarketCapExposureDto {
  category: string;
  portfolioWeightPct: number;
  fundContributions: Record<string, number>;
}

export interface AttributionSecurityExposureDto {
  securityName: string;
  isin: string;
  sector: string;
  capCategory: string;
  portfolioWeightPct: number;
  fundCount: number;
}

export interface RiskExposureContextDto {
  sectors: AttributionSectorExposureDto[];
  marketCaps: AttributionMarketCapExposureDto[];
  topSecurities: AttributionSecurityExposureDto[];
  disclosedEquityCoveragePct: number;
  unclassifiedWeightPct: number;
  cashAndDebtWeightPct: number;
  exposureDisclaimers: string[];
}

export interface RiskInterpretationPanelDto {
  primaryRiskDrivers: string[];
  diversificationInsights: string[];
  concentrationAssessment: string;
  riskHhi: number;
  coverageLimitations: string[];
  investigationPrompts: string[];
}

export interface PortfolioRiskAttributionResponseDto {
  evaluationStatus: string;
  asOfDate: string;
  knowledgeCutoff: string;
  calculationStartDate: string | null;
  calculationEndDate: string | null;
  alignedObservationCount: number;
  annualizationFactor: number;
  annualizationConvention: string;
  benchmarkCode: string;
  benchmarkName: string;
  overviewMetrics: RiskOverviewMetricDto[];
  fundContributions: FundRiskContributionDto[];
  reconciliation: RiskReconciliationDto;
  exposureContext: RiskExposureContextDto;
  interpretation: RiskInterpretationPanelDto;
  dataQualityWarnings: string[];
  methodologyNotes: Record<string, string>;
}

export interface PortfolioRiskAttributionRequestDto {
  funds: ResearchPortfolioFundItemDto[];
  asOfDate?: string;
  knowledgeCutoff?: string;
  benchmarkCode?: string;
}

export async function fetchPortfolioRiskAttribution(
  request: PortfolioRiskAttributionRequestDto
): Promise<PortfolioRiskAttributionResponseDto> {
  return apiFetch<PortfolioRiskAttributionResponseDto>('/api/v1/portfolio/multi-fund/risk-attribution', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

// ---------------------------------------------------------------------------
// PORTFOLIO RISK DRIVERS & CONCENTRATION INVESTIGATION (PHASE 2P)
// ---------------------------------------------------------------------------

export interface PortfolioConcentrationSummaryDto {
  top5WeightPct: number;
  top10WeightPct: number;
  largestSecurityWeightPct: number;
  largestSecurityName: string;
  distinctSecuritiesCount: number;
  sharedSecuritiesCount: number;
  sharedSecuritiesWeightPct: number;
  disclosedEquityCoveragePct: number;
  unclassifiedWeightPct: number;
  cashAndEquivalentsWeightPct: number;
  herfindahlHirschmanIndex: number;
  hhiInterpretation: string;
}

export interface RiskDriverFindingDto {
  id: string;
  category: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO' | 'WARNING' | string;
  title: string;
  observedValue: string;
  threshold: string;
  explanation: string;
  investigationPrompt: string;
  evidence: string;
}

export interface PortfolioRiskDriversResponseDto {
  evaluationStatus: string;
  unavailabilityReason?: string | null;
  asOfDate: string;
  knowledgeCutoff: string;
  totalAllocationPct: number;
  isFullyAllocated: boolean;
  constituentFunds: ConstituentFundSummaryDto[];
  concentrationSummary: PortfolioConcentrationSummaryDto;
  underlyingSecurities: ConsolidatedSecurityExposureDto[];
  pairwiseOverlap: PairwiseOverlapResultDto[];
  sectors: ConsolidatedSectorExposureDto[];
  marketCaps: ConsolidatedMarketCapExposureDto[];
  findings: RiskDriverFindingDto[];
  dataQualityWarnings: string[];
  methodologyNotes: Record<string, string>;
}

export async function fetchPortfolioRiskDrivers(
  request: MultiFundLookThroughRequestDto
): Promise<PortfolioRiskDriversResponseDto> {
  return apiFetch<PortfolioRiskDriversResponseDto>('/api/v1/portfolio/multi-fund/risk-drivers', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}



