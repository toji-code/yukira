import { apiFetch } from './client';

export interface CompositeFundProfile {
  schemeId: number;
  schemeName: string;
  schemeCode: string;
  amcName: string;
  amcCode: string;
  category: string;
  subCategory?: string;
  benchmarkName: string;
  benchmarkCode: string;
  dataCoverageStatus: string;
  earliestObservationDate?: string;
  latestObservationDate?: string;
  totalObservations: number;
  options: Array<{
    optionId: number;
    optionName: string;
    planType: string;
    optionType: string;
    amfiSchemeCode: string;
    isin?: string;
    latestNav?: number;
    latestNavDate?: string;
    ret02OneYear?: number;
    ret02ThreeYear?: number;
    ret02FiveYear?: number;
    rsk01Volatility?: number;
    maxDrawdown?: number;
  }>;
  managers: Array<{
    managerName: string;
    fromDate?: string;
    toDate?: string;
    isCurrent: boolean;
  }>;
  expenseRatios: Array<{
    planType: string;
    totalExpenseRatio: number;
    effectiveDate: string;
  }>;
  exitLoadSummary?: string;
  minimumSipAmount?: number;
  minimumLumpsumAmount?: number;
  methodologyStatusWarning?: string;
}

export interface FundHoldingsConstruction {
  schemeOptionId: number;
  portfolioDate: string;
  totalHoldingsCount: number;
  totalReportedNetAssets?: number;
  top5ConcentrationPercentage: number;
  top10ConcentrationPercentage: number;
  top20ConcentrationPercentage: number;
  concentrationDenominator: string;
  assetAllocation: {
    netEquityPercentage: number;
    debtPercentage: number;
    cashPercentage: number;
    derivativePercentage: number;
  };
  marketCapExposure: {
    largeCapPercentage: number;
    midCapPercentage: number;
    smallCapPercentage: number;
    unclassifiedPercentage: number;
  };
  sectorExposures: Array<{
    sectorName: string;
    weightPercentage: number;
    holdingsCount: number;
  }>;
  topHoldings: Array<{
    rank: number;
    isin?: string;
    securityName: string;
    assetClass: string;
    sector: string;
    marketCapCategory: string;
    reportedWeight: number;
    marketValue?: number;
    resolutionStatus?: 'EXACT_ISIN_MATCH' | 'NAME_CANDIDATE' | 'UNRESOLVED_SECURITY' | string;
  }>;
  unresolvedSecuritiesCount?: number;
  unresolvedWeightPercentage?: number;
  disclosureLagDays?: number;
  sourceArtifactHash?: string;
  epistemicNotice: string;
}

export interface PairwiseHoldingsOverlap {
  schemeOptionIdA: number;
  schemeNameA: string;
  portfolioDateA: string;
  schemeOptionIdB: number;
  schemeNameB: string;
  portfolioDateB: string;
  hasReportingDateMismatch: boolean;
  sharedSecuritiesCount: number;
  uniqueSecuritiesInFundA: number;
  uniqueSecuritiesInFundB: number;
  totalOverlapPercentage: number;
  activeSharePercentage: number;
  topOverlappingPositions: Array<{
    isin?: string;
    securityName: string;
    sector: string;
    weightInFundA: number;
    weightInFundB: number;
    overlapContribution: number;
  }>;
  methodologyStatus: string;
}

export interface ComprehensiveFundComparison {
  funds: Array<{
    schemeOptionId: number;
    schemeId: number;
    schemeName: string;
    optionName: string;
    amcName: string;
    category: string;
    planType: string;
    optionType: string;
    amfiSchemeCode: string;
    isin?: string;
    latestNav?: number;
    latestNavDate?: string;
    ret02OneYear?: number;
    ret02ThreeYear?: number;
    ret02FiveYear?: number;
    rsk01Volatility?: number;
    maxDrawdown?: number;
    primaryBenchmarkCode: string;
    top5Concentration?: number;
    top10Concentration?: number;
    equityPercentage?: number;
    debtPercentage?: number;
    cashPercentage?: number;
    dataReadiness: string;
  }>;
  overlapMatrix: Array<{
    optionIdA: number;
    optionIdB: number;
    overlapPercentage: number;
    dateMismatch: boolean;
  }>;
  comparisonDate: string;
  epistemicGovernanceNotice: string;
}

export interface DrawdownEpisodeReport {
  schemeOptionId: number;
  observationStartDate: string;
  observationEndDate: string;
  totalObservations: number;
  maxDrawdownPercentage: number;
  maxDrawdownPeakDate?: string;
  maxDrawdownTroughDate?: string;
  currentDrawdownPercentage?: number;
  currentPeakDate?: string;
  topEpisodes: Array<{
    rank: number;
    peakDate: string;
    peakNav: number;
    troughDate: string;
    troughNav: number;
    drawdownDepthPercentage: number;
    recoveryDate?: string;
    daysToTrough: number;
    daysToRecovery?: number;
    isRecovered: boolean;
  }>;
  methodologyStatus: string;
}

export interface RollingReturnReport {
  horizon: string;
  windowTradingDays: number;
  totalRollingPeriods: number;
  minReturnPercentage: number;
  p25ReturnPercentage: number;
  medianReturnPercentage: number;
  p75ReturnPercentage: number;
  maxReturnPercentage: number;
  meanReturnPercentage: number;
  standardDeviationPercentage: number;
  positivePeriodsPercentage: number;
  sampledPoints: Array<{
    startDate: string;
    endDate: string;
    annualizedReturnPercentage: number;
  }>;
  methodologyStatus: string;
}

export interface ResearchBrief {
  schemeOptionId: number;
  schemeId: number;
  schemeName: string;
  optionName: string;
  amcName: string;
  category: string;
  benchmarkName: string;
  reportDate: string;
  profile: CompositeFundProfile;
  holdingsConstruction?: FundHoldingsConstruction;
  markdownDossier: string;
}

export interface SourcePublicationCalendarItem {
  id: number;
  sourceName: string;
  frequency: string;
  expectedLagBusinessDays: number;
  expectedCutoffTime: string;
  timezone: string;
  isActive: boolean;
  status?: string;
  lastObservedDate?: string;
  lastObservedAt?: string;
  nextExpectedDate?: string;
  nextExpectedAt?: string;
  businessDaysLag: number;
  freshnessStatus: string;
  notes?: string;
}

export interface PortfolioDriftReport {
  investorId: number;
  totalPortfolioMarketValue: number;
  totalPositionsCount: number;
  driftedPositionsCount: number;
  maxDriftPercentage: number;
  alertThresholdPercentage: number;
  toleranceBandPercentage?: number;
  driftItems: Array<{
    schemeOptionId: number;
    schemeName: string;
    actualMarketValue: number;
    actualWeightPercentage: number;
    targetWeightPercentage: number;
    driftPercentage: number;
    rebalanceAction: string;
    rebalanceCashAmount: number;
    triggersDriftAlert: boolean;
  }>;
  epistemicNotice: string;
}

export interface FifoLotRecord {
  lotId: number;
  schemeOptionId?: number;
  schemeName?: string;
  acquisitionDate: string;
  unitsAcquired?: number;
  remainingUnits: number;
  acquisitionNav: number;
  currentNav: number;
  totalCostBasis: number;
  currentMarketValue: number;
  unrealizedGainLoss: number;
  unrealizedGainLossPercentage: number;
  holdingPeriodDays: number;
  capitalGainType?: 'LTCG' | 'STCG' | 'DEEMED_STCG' | string;
  taxSection?: string;
  provisionalNotice?: string;
}

// API methods
export async function fetchCompositeFundProfile(schemeId: number | string): Promise<CompositeFundProfile> {
  return apiFetch<CompositeFundProfile>(`/api/v1/schemes/${schemeId}/profile/composite`);
}

export async function resolveSchemeIdentity(query: string): Promise<any> {
  return apiFetch<any>(`/api/v1/schemes/resolve?query=${encodeURIComponent(query)}`);
}

export async function fetchHoldingsConstruction(schemeOptionId: number, asOfDate?: string): Promise<FundHoldingsConstruction> {
  const qs = asOfDate ? `?asOfDate=${encodeURIComponent(asOfDate)}` : '';
  return apiFetch<FundHoldingsConstruction>(`/api/v1/research/holdings-construction/${schemeOptionId}${qs}`);
}

export async function fetchComprehensiveComparison(optionIds: number[]): Promise<ComprehensiveFundComparison> {
  return apiFetch<ComprehensiveFundComparison>(`/api/v1/research/comparison?options=${optionIds.join(',')}`);
}

export const fetchPairwiseHoldingsOverlap = fetchPairwiseOverlap;
export async function fetchPairwiseOverlap(optionIdA: number, optionIdB: number): Promise<PairwiseHoldingsOverlap> {
  return apiFetch<PairwiseHoldingsOverlap>(`/api/v1/research/overlap?optionA=${optionIdA}&optionB=${optionIdB}`);
}

export const fetchDrawdownEpisodes = fetchDrawdownAnalytics;
export async function fetchDrawdownAnalytics(schemeOptionId: number, limit = 10): Promise<DrawdownEpisodeReport> {
  return apiFetch<DrawdownEpisodeReport>(`/api/v1/analysis/drawdown-analytics/${schemeOptionId}?limit=${limit}`);
}

export async function fetchRollingReturns(schemeOptionId: number, horizon = '1Y'): Promise<RollingReturnReport> {
  return apiFetch<RollingReturnReport>(`/api/v1/analysis/rolling/${schemeOptionId}?horizon=${horizon}`);
}

export async function fetchResearchBrief(schemeOptionId: number): Promise<ResearchBrief> {
  return apiFetch<ResearchBrief>(`/api/v1/research/brief/${schemeOptionId}`);
}

export async function fetchSourceCalendars(): Promise<SourcePublicationCalendarItem[]> {
  return apiFetch<SourcePublicationCalendarItem[]>('/api/v1/source-calendar');
}

export async function fetchPortfolioDrift(threshold = 5.0): Promise<PortfolioDriftReport> {
  return apiFetch<PortfolioDriftReport>(`/api/v1/research/portfolio-drift?threshold=${threshold}`);
}

export async function fetchActiveTaxLots(schemeOptionId: number): Promise<any[]> {
  return apiFetch<any[]>(`/api/v1/research/tax-lots/${schemeOptionId}`);
}

