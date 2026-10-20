import { YukiraScoreSummary } from './domain';

export interface ScoreDimensionSummary {
  dimension: string;
  dimensionName: string;
  score: number | null;
  weight: number;
  status: string;
}

export interface FundScoreSummary {
  scoreValue: number | null;
  confidence: number | null;
  scoreStatus: string;
  methodologyVersion: string;
  methodologyStatus: string;
  asOfDate: string | null;
  dimensions?: ScoreDimensionSummary[] | null;
}

export interface PortfolioHoldingContext {
  unitsHeld: number;
  portfolioWeight: number | null;
  availableValue: number | null;
  costBasisAmount: number | null;
  investedAmount: number | null;
  absoluteGainLoss: number | null;
  absoluteGainLossPercentage: number | null;
}

export interface FundEnrichmentSummary {
  aumInCrores: number | null;
  aumFormatted: string | null;
  expenseRatioPct: number | null;
  terFormatted: string | null;
  fundManager: string | null;
  investmentTerms: string | null;
}

export interface ComparisonFund {
  schemeOptionId: number;
  schemeId?: number | null;
  schemeName: string;
  schemeCode?: string | null;
  amcName: string;
  amfiCode: string;
  isin: string | null;
  planType: string;
  optionType: string;
  analyticalScore?: FundScoreSummary | null;
  yukiraScore?: YukiraScoreSummary | null;
  holdingContext?: PortfolioHoldingContext | null;
  portfolioHolding?: {
    units: number;
    availableValue: number | null;
    costBasis?: number | null;
    currentWeightPercentage?: number | null;
    unrealizedGainLossAmount?: number | null;
    unrealizedGainLossPercentage?: number | null;
  } | null;
  enrichment?: FundEnrichmentSummary | null;
}

export interface MetricValueCell {
  availabilityState: string;
  formattedValue: string | null;
  rawValue: number | null;
}

export interface ComparisonMetricRow {
  metricCode: string;
  metricName: string;
  category?: string;
  comparabilityStatus?: 'COMPARABLE' | 'COMPARABLE_WITH_LIMITATIONS' | 'NOT_COMPARABLE' | 'INSUFFICIENT_DATA' | string;
  limitationReason?: string | null;
  fundValues: Record<string, MetricValueCell>;
}

export interface ComparisonMetricResult {
  schemeOptionId: number;
  numericValue: number | null;
  stringValue?: string | null;
  formattedValue: string | null;
  units: string;
  calculationStatus: 'CALCULATED' | 'INSUFFICIENT_DATA' | 'FAILED' | string;
  errorMessage?: string | null;
  observationCount?: number;
  comparabilityStatus?: 'COMPARABLE' | 'COMPARABLE_WITH_LIMITATIONS' | 'NOT_COMPARABLE' | 'INSUFFICIENT_DATA' | string;
  limitationReason?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  dataDate?: string | null;
}

export interface ComparisonMetric {
  metricCode: string;
  metricName: string;
  category: string;
  governanceStatus: string;
  period: string;
  description: string;
  interpretation: string;
  limitations: string;
  units?: string;
  overallComparabilityStatus?: 'COMPARABLE' | 'COMPARABLE_WITH_LIMITATIONS' | 'NOT_COMPARABLE' | 'INSUFFICIENT_DATA' | string;
  overallComparabilityReason?: string | null;
  results: ComparisonMetricResult[];
}

export interface ComparisonCoverageSummary {
  totalSelectedFunds: number;
  scoredFundsCount: number;
  completeRiskEvidenceCount: number;
  missingMetricsCount: number;
  differingAsOfDates: boolean;
  overallComparabilityStatus?: 'COMPARABLE' | 'COMPARABLE_WITH_LIMITATIONS' | 'NOT_COMPARABLE' | 'INSUFFICIENT_DATA' | string;
  overallComparabilityReason?: string | null;
}

export interface ComparisonPeriod {
  startDate: string;
  endDate: string;
  knowledgeCutoffTime: string;
}

export interface PortfolioComparisonResponse {
  comparisonSize: number;
  asOfDate: string;
  knowledgeCutoff: string;
  methodologyVersion: string;
  methodologyStatus: string;
  referencePopulation: string;
  funds: ComparisonFund[];
  canonicalMetrics: ComparisonMetricRow[];
  coverageSummary: ComparisonCoverageSummary;
  investigationPrompts: string[];
}

export interface ComparisonResponse {
  comparisonSize?: number;
  asOfDate?: string;
  knowledgeCutoff?: string;
  methodologyVersion?: string;
  methodologyStatus?: string;
  referencePopulation?: string;
  funds: ComparisonFund[];
  canonicalMetrics?: ComparisonMetricRow[];
  metrics?: ComparisonMetric[];
  coverageSummary?: ComparisonCoverageSummary;
  investigationPrompts?: string[];
  period?: ComparisonPeriod;
  runId?: number;
  executionTimestamp?: string;
}

export interface ComparisonRequest {
  schemeOptionIds: number[];
  asOfDate: string;
  knowledgeCutoffTime: string;
  metricCodes?: string[];
}

export interface OverlappingSecurityDto {
  securityName: string;
  isin: string | null;
  sector: string | null;
  fundAWeightPct?: number;
  fundBWeightPct?: number;
  weightInA?: number;
  weightInB?: number;
  formattedWeightA?: string;
  formattedWeightB?: string;
  overlapContributionPct?: number;
  overlapContribution?: number;
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
  commonSecurityCount?: number;
  commonSecuritiesCount?: number;
  holdingsCountA?: number;
  holdingsCountB?: number;
  fundAPositionCount?: number;
  fundBPositionCount?: number;
  uniqueHoldingsCountA?: number;
  uniqueHoldingsCountB?: number;
  topOverlappingSecurities?: OverlappingSecurityDto[];
  overlappingSecurities?: OverlappingSecurityDto[];
  qualityState: string;
  limitations: string[];
}

export interface PairwiseCorrelationDto {
  schemeOptionIdA: number;
  fundNameA: string;
  schemeOptionIdB: number;
  fundNameB: string;
  correlation: number | null;
  formattedCorrelation: string | null;
  pairedObservationsCount: number;
  commonStartDate: string | null;
  commonEndDate: string | null;
  calculationStatus: string;
  comparabilityStatus: string;
  limitations: string[];
  interpretation: string;
}
