export interface Amc {
  id: number;
  name: string;
  code: string;
}

export interface Scheme {
  id: number;
  amc: Amc;
  name: string;
  code: string;
  inceptionDate: string;
  status: string;
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
  optionType: 'GROWTH' | 'IDCW_PAYOUT' | 'IDCW_REINVESTMENT' | string;
  amfiCode: string | null;
  isin: string | null;
  status: string;
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
  category?: string;
  asOfDate: string;
  source?: string;
}
