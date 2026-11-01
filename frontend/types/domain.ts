export interface Amc {
  id: number;
  name: string;
  code: string;
}

export interface ScoreDimensionSummary {
  id?: number;
  dimension: string;
  dimensionCode?: string;
  dimensionName: string;
  score?: number | null;
  weight: number;
  effectiveWeight?: number | null;
  contribution?: number | null;
  status: string;
  confidence?: number | null;
  eligibleMetricCount?: number;
  totalMetricCount?: number;
}

export interface YukiraScoreSummary {
  scoreId?: number;
  schemeOptionId?: number;
  score?: number | null;
  confidence?: number | null;
  status: string;
  scoreVersion?: string;
  methodologyStatus?: string;
  asOfDate?: string | null;
  summary?: string | null;
  dimensions?: ScoreDimensionSummary[] | null;
}


export interface Scheme {
  id: number;
  amc?: Amc;
  name: string;
  code: string;
  category?: string;
  subcategory?: string;
  inceptionDate: string;
  status: string;
  yukiraScore?: YukiraScoreSummary | null;
}

export interface SchemePlan {
  id: number;
  scheme?: Scheme;
  planType: 'DIRECT' | 'REGULAR' | string;
  planCode: string;
  status: string;
}

export interface SchemeOption {
  id: number;
  plan?: SchemePlan;
  optionType: 'GROWTH' | 'IDCW' | 'IDCW_PAYOUT' | 'IDCW_REINVESTMENT' | string;
  amfiCode: string | null;
  isin: string | null;
  status: string;
  yukiraScore?: YukiraScoreSummary | null;
  latestNavDate?: string | null;
  navObservationCount?: number | null;
  latestHoldingsDate?: string | null;
  holdingsCount?: number | null;
  navFreshnessState?: string | null;
  navFreshnessExplanation?: string | null;
  holdingsFreshnessState?: string | null;
  holdingsFreshnessExplanation?: string | null;
  holdingsRawSumPercentage?: number | null;
  snapshotCount?: number | null;
}

export interface Benchmark {
  id: number;
  benchmarkCode: string;
  name: string;
  provider: string;
  benchmarkType: string;
}

export interface Holding {
  id?: number;
  securityName: string;
  weight: number;
  formattedWeight?: string;
  category?: string;
  assetClass?: string;
  sector?: string;
  isin?: string;
  asOfDate: string;
  source?: string;
  dataQuality?: string;
}
