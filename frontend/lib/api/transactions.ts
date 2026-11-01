import { apiFetch } from './client';

export type TransactionType =
  | 'PURCHASE'
  | 'REDEMPTION'
  | 'SWITCH_IN'
  | 'SWITCH_OUT'
  | 'SIP_PURCHASE'
  | 'STP_PURCHASE'
  | 'STP_REDEMPTION'
  | 'DIVIDEND_PAYOUT'
  | 'DIVIDEND_REINVESTMENT'
  | 'BONUS'
  | 'REVERSAL'
  | 'OTHER';

export type TransactionSourceAttribution =
  | 'CAS_IMPORT'
  | 'MANUAL_ENTRY'
  | 'REVERSAL_ENTRY'
  | 'BROKER_API';

export type ExternalCashFlowType =
  | 'INFLOW'
  | 'OUTFLOW'
  | 'NEUTRAL'
  | 'NON_EXTERNAL';

export interface InvestorTransactionDto {
  id: number;
  schemeOptionId: number;
  schemeName: string;
  planType?: string;
  folioNumber?: string;
  transactionDate: string;
  settlementDate?: string;
  transactionType: TransactionType;
  units?: number;
  nav?: number;
  grossAmount?: number;
  stampDuty?: number;
  stt?: number;
  netAmount?: number;
  externalCashFlowType: ExternalCashFlowType;
  sourceAttribution: TransactionSourceAttribution;
  description?: string;
  userNotes?: string;
  isReversed: boolean;
  reversalReason?: string;
  reversedAt?: string;
  importBatchId?: number;
  createdAt: string;
}

export interface CreateManualTransactionRequest {
  schemeOptionId: number;
  transactionDate: string;
  settlementDate?: string;
  transactionType: TransactionType;
  units?: number;
  nav?: number;
  grossAmount?: number;
  stampDuty?: number;
  stt?: number;
  netAmount?: number;
  folioNumber?: string;
  description?: string;
  userNotes?: string;
}

export interface ReverseTransactionRequest {
  reversalReason: string;
}

export type ReconciliationStatus =
  | 'RECONCILED'
  | 'PARTIALLY_RECONCILED'
  | 'UNRECONCILED'
  | 'INSUFFICIENT_HISTORY'
  | 'SOURCE_CONFLICT';

export interface HoldingsReconciliationItemDto {
  schemeOptionId: number;
  schemeName: string;
  folioNumber?: string;
  reportedUnits: number;
  ledgerInflowUnits: number;
  ledgerOutflowUnits: number;
  netLedgerUnits: number;
  unitDifference: number;
  reconciliationStatus: ReconciliationStatus;
  reconciliationNotes: string;
  earliestTransactionDate?: string;
  transactionCount: number;
}

export interface PortfolioHoldingsReconciliationResponseDto {
  reconciliationDate: string;
  totalHoldingsEvaluated: number;
  reconciledCount: number;
  partiallyReconciledCount: number;
  unreconciledCount: number;
  insufficientHistoryCount: number;
  sourceConflictCount: number;
  overallStatus: ReconciliationStatus;
  reconciliationSummary: string;
  items: HoldingsReconciliationItemDto[];
}

export type XirrStatus =
  | 'AVAILABLE'
  | 'INSUFFICIENT_CASH_FLOWS'
  | 'INSUFFICIENT_HISTORY'
  | 'MISSING_ENDING_VALUATION'
  | 'INCOMPLETE_PORTFOLIO_VALUATION'
  | 'NO_VALID_ROOT'
  | 'NON_CONVERGENT';

export interface CashFlowScheduleItemDto {
  date: string;
  amount: number;
  description: string;
  cashFlowType: string;
}

export interface SchemeInvestorPerformanceDto {
  schemeOptionId: number;
  schemeName: string;
  totalInvestedCapital: number;
  totalRedeemedCapital: number;
  realizedGains: number;
  remainingCostBasis: number;
  currentUnits: number;
  currentNav: number;
  currentMarketValue: number;
  unrealizedGains: number;
  dividendIncomeReceived: number;
  totalGainOrLoss: number;
  xirrStatus: XirrStatus;
  xirrPercent?: number;
  xirrAnnualizedReturn?: number;
  fundBuyAndHoldCagrPercent?: number;
  performanceComparisonNotes: string;
  cashFlowCount: number;
  firstCashFlowDate?: string;
  lastCashFlowDate?: string;
  calculationNotes: string;
  cashFlowSchedule: CashFlowScheduleItemDto[];
}

export interface PortfolioInvestorPerformanceResponseDto {
  valuationDate: string;
  knowledgeCutoffDate: string;
  totalPortfolioInvestedCapital: number;
  totalPortfolioRedeemedCapital: number;
  totalPortfolioRealizedGains: number;
  totalPortfolioCostBasis: number;
  totalPortfolioMarketValue: number;
  totalPortfolioUnrealizedGains: number;
  totalPortfolioDividendIncome: number;
  totalPortfolioNetGainOrLoss: number;
  portfolioXirrStatus: XirrStatus;
  portfolioXirrPercent?: number;
  portfolioXirrAnnualizedReturn?: number;
  portfolioXirrNotes: string;
  twrStatus: string;
  twrLimitationDisclosure: string;
  schemePerformances: SchemeInvestorPerformanceDto[];
  portfolioCashFlowSchedule: CashFlowScheduleItemDto[];
  evidenceFindings: string[];
}

export interface CasImportTransactionReviewDto {
  id: number;
  importBatchId: number;
  schemeOptionId?: number;
  schemeName: string;
  folioNumber?: string;
  transactionDate: string;
  rawTransactionType?: string;
  canonicalTransactionType: TransactionType;
  units?: number;
  nav?: number;
  amount?: number;
  stampDuty?: number;
  stt?: number;
  isConfirmed: boolean;
  resolutionStatus: string;
  deduplicationHash?: string;
}

export async function fetchInvestorTransactions(params?: {
  schemeOptionId?: number;
  transactionType?: string;
  startDate?: string;
  endDate?: string;
}): Promise<InvestorTransactionDto[]> {
  const query = new URLSearchParams();
  if (params?.schemeOptionId) query.set('schemeOptionId', params.schemeOptionId.toString());
  if (params?.transactionType) query.set('transactionType', params.transactionType);
  if (params?.startDate) query.set('startDate', params.startDate);
  if (params?.endDate) query.set('endDate', params.endDate);
  const qStr = query.toString();
  return apiFetch<InvestorTransactionDto[]>(`/api/v1/portfolio/transactions${qStr ? `?${qStr}` : ''}`);
}

export async function createManualTransaction(request: CreateManualTransactionRequest): Promise<InvestorTransactionDto> {
  return apiFetch<InvestorTransactionDto>('/api/v1/portfolio/transactions', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function reverseTransaction(id: number, request: ReverseTransactionRequest): Promise<InvestorTransactionDto> {
  return apiFetch<InvestorTransactionDto>(`/api/v1/portfolio/transactions/${id}/reverse`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function fetchHoldingsReconciliation(): Promise<PortfolioHoldingsReconciliationResponseDto> {
  return apiFetch<PortfolioHoldingsReconciliationResponseDto>('/api/v1/portfolio/reconciliation');
}

export async function fetchInvestorPerformance(params?: {
  asOfDate?: string;
  knowledgeCutoff?: string;
}): Promise<PortfolioInvestorPerformanceResponseDto> {
  const query = new URLSearchParams();
  if (params?.asOfDate) query.set('asOfDate', params.asOfDate);
  if (params?.knowledgeCutoff) query.set('knowledgeCutoff', params.knowledgeCutoff);
  const qStr = query.toString();
  return apiFetch<PortfolioInvestorPerformanceResponseDto>(`/api/v1/portfolio/performance${qStr ? `?${qStr}` : ''}`);
}

export async function fetchCasImportTransactions(importBatchId: number): Promise<CasImportTransactionReviewDto[]> {
  return apiFetch<CasImportTransactionReviewDto[]>(`/api/v1/portfolio/imports/${importBatchId}/transactions`);
}


export interface PortfolioFinancialDecompositionDto {
  openingPortfolioValue: number;
  externalInvestedCapital: number;
  externalRedeemedCapital: number;
  realizedCapitalGains: number;
  unrealizedCapitalGains: number;
  disclosedDividendIncome: number;
  dividendReinvestmentAmount: number;
  recordedChargesAndCosts: number;
  supportedAdjustments: number;
  closingPortfolioValue: number;
  calculatedExpectedClosingValue: number;
  unexplainedResidual: number;
  reconciliationStatus: string;
  residualCauses: string[];
  reconciliationFormula: string;
  provenanceNotes: string;
}

export interface SchemePerformanceContributionDto {
  schemeOptionId: number;
  schemeName: string;
  planType: string;
  optionType: string;
  amfiCode: string;
  isin: string;
  currentUnits: number;
  currentNav: number;
  navEffectiveDate: string;
  isNavStale: boolean;
  currentMarketValue: number;
  investedCapital: number;
  redeemedCapital: number;
  remainingCostBasis: number;
  realizedGains: number;
  unrealizedGains: number;
  dividendIncome: number;
  reinvestedDividends: number;
  recordedCharges: number;
  netFinancialContribution: number;
  contributionToPortfolioAbsoluteGainsPercent: number;
  contributionDenominator: number;
  contributionDenominatorDescription: string;
  contributionRank: number;
  contributionStatus: string;
  transactionCoverageStartDate?: string;
  transactionCoverageEndDate?: string;
  transactionCount: number;
  holdingsReconciliationStatus: string;
  schemeXirr?: number;
  schemeXirrStatus: string;
  fundNavCagr?: number;
  fundNavCagrPeriod?: string;
  cagrStartDate?: string;
  cagrEndDate?: string;
  excessReturnOverCagr?: number;
  excessReturnEpistemicNotice?: string;
}

export interface HoldingLotPerformanceDto {
  lotId: number;
  schemeOptionId: number;
  schemeName: string;
  acquisitionTransactionId: number;
  acquisitionDate: string;
  acquisitionUnits: number;
  acquisitionNav: number;
  acquisitionCost: number;
  remainingUnits: number;
  remainingCostBasis: number;
  currentNav: number;
  currentMarketValue: number;
  unrealizedGain: number;
  realizedGainFromDisposedUnits: number;
  holdingPeriodDays: number;
  holdingPeriodCategory: string;
  isFullyConsumed: boolean;
  lotStatus: string;
}

export interface RealizedGainItemDto {
  redemptionTransactionId: number;
  schemeOptionId: number;
  schemeName: string;
  redemptionDate: string;
  unitsRedeemed: number;
  redemptionPrice: number;
  redemptionGrossProceeds: number;
  costBasisRedeemed: number;
  netRealizedGain: number;
  matchedPurchaseTransactionId?: number;
  matchedPurchaseDate?: string;
  matchedPurchasePrice?: number;
  holdingPeriodDays: number;
  holdingPeriodCategory: string;
  redemptionType: string;
  isInternalTransfer: boolean;
  chargesDeducted: number;
}

export interface ConsolidatedRealizedPerformanceDto {
  totalRealizedGains: number;
  totalRealizedLosses: number;
  netRealizedGainOrLoss: number;
  totalRedemptionGrossProceeds: number;
  totalCostBasisRedeemed: number;
  totalChargesDeducted: number;
  externalWithdrawalsRealizedGains: number;
  internalTransfersRealizedGains: number;
  disposedLotsCount: number;
  records: RealizedGainItemDto[];
  taxDisclaimer: string;
}

export interface SchemeUnrealizedItemDto {
  schemeOptionId: number;
  schemeName: string;
  units: number;
  latestNav: number;
  navEffectiveDate: string;
  isNavStale: boolean;
  currentMarketValue: number;
  remainingCostBasis: number;
  unrealizedGainOrLoss: number;
  unrealizedPercentage: number;
  holdingsReconciliationStatus: string;
  valuationQualityState: string;
}

export interface ConsolidatedUnrealizedPerformanceDto {
  totalPortfolioMarketValue: number;
  totalRemainingCostBasis: number;
  totalUnrealizedGainOrLoss: number;
  unrealizedGainPercentage: number;
  activeOpenLotsCount: number;
  valuedSchemesCount: number;
  unvaluedSchemesCount: number;
  staleValuationSchemesCount: number;
  items: SchemeUnrealizedItemDto[];
  valuationQualityNotes: string;
}

export interface SchemeDividendItemDto {
  schemeOptionId: number;
  schemeName: string;
  cashDividendPayoutTotal: number;
  dividendReinvestmentTotal: number;
  totalDividendEventsCount: number;
  lastDividendDate?: string;
  sourceCoverage: string;
}

export interface DividendIncomeContributionDto {
  totalCashDividendPayouts: number;
  totalDividendReinvestments: number;
  totalDividendEventsCount: number;
  cashPayoutEventsCount: number;
  reinvestmentEventsCount: number;
  schemeBreakdown: SchemeDividendItemDto[];
  accountingRuleNote: string;
}

export interface MonthlyCashFlowAggregateDto {
  yearMonth: string;
  lumpSumContributions: number;
  sipContributions: number;
  totalContributions: number;
  redemptions: number;
  dividendPayouts: number;
  internalSwitches: number;
  netExternalCashFlow: number;
}

export interface CashFlowTimingContextDto {
  coverageStartDate?: string;
  coverageEndDate?: string;
  totalContributionsCount: number;
  totalLumpSumContributions: number;
  totalSipContributions: number;
  totalRedemptionsCount: number;
  totalInternalTransfersCount: number;
  periodicCashFlowTimeline: MonthlyCashFlowAggregateDto[];
  timingObservations: string[];
}

export interface XirrAmbiguityAndDiagnosticsDto {
  portfolioXirr?: number;
  portfolioXirrStatus: string;
  solverMethod: string;
  iterations: number;
  residualNpv: number;
  dayCountConvention: string;
  firstCashFlowDate?: string;
  lastCashFlowDate?: string;
  endingValuationDate?: string;
  cashFlowCount: number;
  signAlternationsCount: number;
  isMultipleRootsPossible: boolean;
  rootCandidates: number[];
  diagnosticExplanation: string;
  fundCagrComparison: string;
}

export interface PortfolioPerformanceAttributionResponseDto {
  asOfDate: string;
  knowledgeCutoff: string;
  financialDecomposition: PortfolioFinancialDecompositionDto;
  schemeContributions: SchemePerformanceContributionDto[];
  realizedPerformance: ConsolidatedRealizedPerformanceDto;
  unrealizedPerformance: ConsolidatedUnrealizedPerformanceDto;
  dividendContribution: DividendIncomeContributionDto;
  cashFlowTiming: CashFlowTimingContextDto;
  xirrDiagnostics: XirrAmbiguityAndDiagnosticsDto;
  holdingLots: HoldingLotPerformanceDto[];
  researchFindings: string[];
  methodologyDisclaimers: string[];
}

export async function fetchPerformanceAttribution(params?: {
  asOfDate?: string;
  knowledgeCutoff?: string;
}): Promise<PortfolioPerformanceAttributionResponseDto> {
  const query = new URLSearchParams();
  if (params?.asOfDate) query.set('asOfDate', params.asOfDate);
  if (params?.knowledgeCutoff) query.set('knowledgeCutoff', params.knowledgeCutoff);
  const qStr = query.toString();
  return apiFetch<PortfolioPerformanceAttributionResponseDto>(`/api/v1/portfolio/attribution${qStr ? `?${qStr}` : ''}`);
}

export async function fetchHoldingLots(schemeOptionId?: number): Promise<HoldingLotPerformanceDto[]> {
  const query = new URLSearchParams();
  if (schemeOptionId) query.set('schemeOptionId', schemeOptionId.toString());
  const qStr = query.toString();
  return apiFetch<HoldingLotPerformanceDto[]>(`/api/v1/portfolio/lots${qStr ? `?${qStr}` : ''}`);
}
