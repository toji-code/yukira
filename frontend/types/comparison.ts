export interface ComparisonFund {
  schemeOptionId: number;
  schemeName: string;
  schemeCode: string;
  amcName: string;
  amfiCode: string;
  isin: string | null;
  planType: string;
  optionType: string;
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

export interface ComparisonPeriod {
  startDate: string;
  endDate: string;
  knowledgeCutoffTime: string;
}

export interface ComparisonResponse {
  funds: ComparisonFund[];
  metrics: ComparisonMetric[];
  period: ComparisonPeriod;
  runId: number;
  executionTimestamp: string;
}

export interface ComparisonRequest {
  schemeOptionIds: number[];
  asOfDate: string;
  knowledgeCutoffTime: string;
  metricCodes?: string[];
}
