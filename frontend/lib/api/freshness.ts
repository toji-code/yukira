import { apiFetch } from './client';

export interface BenchmarkDataFreshnessDto {
  benchmarkCode: string;
  benchmarkName: string;
  category: string;
  latestObservationDate: string | null;
  daysLag: number | null;
  totalObservations: number;
  freshnessState: 'FRESH' | 'PARTIAL' | 'STALE' | 'UNAVAILABLE' | string;
  latestSourceHash: string | null;
  explanation: string;
}

export interface FbilDataFreshnessDto {
  seriesIdentifier: string;
  latestObservationDate: string | null;
  daysLag: number | null;
  totalObservations: number;
  freshnessState: 'FRESH' | 'PARTIAL' | 'STALE' | 'UNAVAILABLE' | string;
  latestRate: number | null;
  explanation: string;
}

export interface NavFreshnessSummary {
  freshnessState: 'FRESH' | 'PARTIAL' | 'STALE' | 'UNAVAILABLE' | string;
  latestNavDate: string | null;
  daysLag: number | null;
  totalObservationsInLedger: number;
  referenceUniverseTotalOptions: number;
  referenceUniverseSufficient3Y: number;
  referenceUniversePartial3Y: number;
  explanation: string;
}

export interface UniverseFreshnessSummary {
  totalEligibleReferenceSchemes: number;
  largeCapCount: number;
  largeMidCapCount: number;
  midCapCount: number;
  smallCapCount: number;
  flexiCapCount: number;
  syncStatus: 'CURRENT' | 'STALE' | 'UNRESOLVED' | string;
  explanation: string;
}

export interface HoldingsFreshnessSummary {
  totalReferenceUniverse: number;
  schemesWithUsableHoldings: number;
  latestReportedPortfolioDate: string | null;
  amcCoverageStatus: Record<string, string>;
  overallHoldingsState: 'CURRENT' | 'PARTIAL' | 'STALE' | 'MISSING' | 'DATA_QUALITY_LIMITED' | 'UNRESOLVED' | string;
  explanation: string;
}

export interface MetricsCalculationFreshnessSummary {
  totalCalculationRuns: number;
  schemesWithCalculated3YMetrics: number;
  calculationEngineVersion: string;
  status: 'CURRENT' | 'OPERATIONAL' | 'DEGRADED' | string;
  explanation: string;
}

export interface CasProcessingStatusSummary {
  totalImportedStatements: number;
  engineStatus: 'OPERATIONAL' | 'DEGRADED' | string;
  supportedRegistrars: string[];
  explanation: string;
}

export interface SourceDomainFreshnessDto {
  domainIdentifier: 'AMFI_NAV' | 'NSE_TRI_BENCHMARK' | 'MUTUAL_FUND_UNIVERSE' | 'AMC_PORTFOLIO_HOLDINGS' | 'HISTORICAL_METRIC_CALCULATION' | 'INVESTOR_CAS_IMPORT' | string;
  displayName: string;
  latestObservationDate: string | null;
  latestSuccessfulIngestionTime: string | null;
  latestAttemptedIngestionTime: string | null;
  status: 'CURRENT' | 'PARTIAL' | 'STALE' | 'MISSING' | 'DATA_QUALITY_LIMITED' | 'OPERATIONAL' | 'UNAVAILABLE' | string;
  recordsOrSchemesCovered: number;
  totalTargetDenominator: number;
  limitationExplanation: string;
  sourceProvenanceOrRunId: string | null;
}

export interface MarketDataFreshnessDto {
  referenceDate: string;
  overallHealthStatus: 'CURRENT' | 'PARTIAL' | 'STALE' | 'UNAVAILABLE' | string;
  navFreshness: NavFreshnessSummary;
  benchmarkFreshnessByCategory: Record<string, BenchmarkDataFreshnessDto>;
  riskFreeFreshness: FbilDataFreshnessDto;
  universeFreshness: UniverseFreshnessSummary;
  holdingsFreshness: HoldingsFreshnessSummary;
  metricsFreshness: MetricsCalculationFreshnessSummary;
  casProcessingStatus: CasProcessingStatusSummary;
  domainSummaries: SourceDomainFreshnessDto[];
  evaluatedAt: string;
  governanceDisclaimer: string;
}

export async function fetchMarketDataFreshness(
  referenceDate?: string
): Promise<MarketDataFreshnessDto> {
  const query = referenceDate ? `?referenceDate=${encodeURIComponent(referenceDate)}` : '';
  return apiFetch<MarketDataFreshnessDto>(`/api/v1/market-data/freshness${query}`);
}

