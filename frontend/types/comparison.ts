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
  fundValues: Record<string, MetricValueCell>;
}

export interface ComparisonMetricResult {
  schemeOptionId: number;
  numericValue: number | null;
  formattedValue: string | null;
  units: string;
  calculationStatus: 'CALCULATED' | 'INSUFFICIENT_DATA' | 'FAILED' | string;
  errorMessage: string | null;
  observationCount?: number;
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
  results: ComparisonMetricResult[];
}

export interface ComparisonCoverageSummary {
  totalSelectedFunds: number;
  scoredFundsCount: number;
  completeRiskEvidenceCount: number;
  missingMetricsCount: number;
  differingAsOfDates: boolean;
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
