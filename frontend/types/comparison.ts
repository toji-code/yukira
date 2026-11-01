import { YukiraScoreSummary } from './domain';

export interface ScoreDimensionSummary {
  dimension: string;
  dimensionCode?: string;
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
  category?: string | null;
  subcategory?: string | null;
  benchmarkName?: string | null;
  benchmarkCode?: string | null;
  benchmarkReturnVariant?: string | null;
  primaryBenchmarkName?: string | null;
  primaryBenchmarkReturnVariant?: string | null;
  inceptionDate?: string | null;
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

export interface PairwiseCompatibilityDto {
  fundAId: number;
  fundBId: number;
  fundAName: string;
  fundBName: string;
  categoryCompatibility: 'SAME_CATEGORY' | 'CROSS_CATEGORY' | 'INCOMPATIBLE_MANDATE' | string;
  categoryA?: string;
  categoryB?: string;
  benchmarkCompatibility: 'IDENTICAL_BENCHMARK' | 'COMPARABLE_TRI' | 'DIFFERENT_BENCHMARKS' | 'NON_TRI_BENCHMARK' | string;
  benchmarkA?: string;
  benchmarkB?: string;
  observationPeriodCompatibility: 'ALIGNED_COMMON_WINDOW' | 'TRUNCATED_HISTORY' | 'ASYMMETRIC_BOUNDS' | 'INSUFFICIENT_HISTORY' | string;
  freshnessCompatibility: 'SYNCHRONOUS_FRESH' | 'ASYMMETRIC_FRESHNESS' | 'STALE_DATA' | string;
  holdingsCompatibility: 'SYNCHRONOUS_DISCLOSURES' | 'DIFFERING_DISCLOSURE_DATES' | 'PARTIAL_COVERAGE' | 'UNCLASSIFIED_RESIDUAL' | string;
  holdingsDateA?: string | null;
  holdingsDateB?: string | null;
  metricAvailabilityCompatibility: 'ALL_METRICS_AVAILABLE' | 'PARTIAL_METRIC_AVAILABILITY' | 'SPARSE_METRICS' | string;
  methodologyCompatibility: 'UNIFORM_METHODOLOGY' | 'MIXED_GOVERNANCE_STATUS' | string;
  pairwiseStatus: 'COMPARABLE' | 'PARTIALLY_COMPARABLE' | 'NOT_COMPARABLE' | 'INSUFFICIENT_EVIDENCE';
  pairwiseLimitations: string[];
}

export interface ComparisonCompatibilitySummary {
  overallStatus: 'COMPARABLE' | 'PARTIALLY_COMPARABLE' | 'NOT_COMPARABLE' | 'INSUFFICIENT_EVIDENCE';
  overallCompatibility?: string;
  overallReason: string;
  pairwiseCompatibilities: PairwiseCompatibilityDto[];
  overallLimitations: string[];
}

export interface CommonSupportedPeriod {
  commonStartDate: string | null;
  commonEndDate: string | null;
  commonObservationCount: number | null;
  isAligned: boolean;
  benchmarkCode?: string | null;
  limitationNotice: string | null;
}

export interface ComparisonDifferenceExplanation {
  metricCode: string;
  metricName: string;
  differenceType: 'OBSERVED_DIFFERENCE' | 'CALCULATED_DIFFERENCE' | 'INTERPRETATION' | 'DATA_LIMITATION' | 'UNKNOWN' | string;
  whatDiffers: string;
  magnitude: string;
  isComparable: boolean;
  supportingSource: string;
  isMaterial: boolean;
  limitation: string;
  additionalEvidenceNeeded: string;
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
  compatibilitySummary?: ComparisonCompatibilitySummary | null;
  compatibility?: ComparisonCompatibilitySummary | null;
  commonSupportedPeriod?: CommonSupportedPeriod | null;
  commonPeriod?: CommonSupportedPeriod | null;
  differenceExplanations?: ComparisonDifferenceExplanation[] | null;
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
  weightA?: number;
  weightB?: number;
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
  overlappingSecuritiesCount?: number;
  holdingsCountA?: number;
  holdingsCountB?: number;
  fundAPositionCount?: number;
  fundBPositionCount?: number;
  uniqueHoldingsCountA?: number;
  uniqueHoldingsCountB?: number;
  topOverlappingSecurities?: OverlappingSecurityDto[];
  overlappingSecurities?: OverlappingSecurityDto[];
  interpretation?: string;
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
