import { Holding } from './domain';

/**
 * Typed domain interfaces for Fund Information Enrichment.

 * Reflects authoritative data models for AUM, Expense Ratio (TER), Fund Manager,
 * Investment Terms (SIP / Lumpsum), and Portfolio Holdings.
 * Zero financial approximations or frontend calculations.
 */

export interface AumDto {
  readonly reportedTotalNetAssets: number | null;
  readonly formattedAum: string | null;
  readonly currency: string;
  readonly unit: string;
  readonly asOfDate: string | null;
  readonly sourceArtifactId: number | null;
  readonly sourceDocumentTitle: string | null;
  readonly qualityAssessment: string;
  readonly status: 'AVAILABLE' | 'MISSING' | 'NOT_APPLICABLE';
}

export interface ExpenseRatioDto {
  readonly expenseRatio: number | null;
  readonly formattedExpenseRatio: string | null;
  readonly planType: string;
  readonly optionType: string;
  readonly regularPlanRatio: number | null;
  readonly formattedRegularPlanRatio: string | null;
  readonly asOfDate: string | null;
  readonly sourceArtifactId: number | null;
  readonly sourceDocumentTitle: string | null;
  readonly qualityAssessment: string;
  readonly status: 'AVAILABLE' | 'MISSING' | 'NOT_APPLICABLE';
}

export interface FundManagerDto {
  readonly managerName: string;
  readonly role: string | null;
  readonly startDate: string | null;
  readonly endDate: string | null;
  readonly asOfDate: string | null;
  readonly sourceArtifactId: number | null;
  readonly sourceDocumentTitle: string | null;
  readonly qualityAssessment: string;
  readonly status: 'AVAILABLE' | 'MISSING' | 'NOT_APPLICABLE';
}

export interface InvestmentTermsDto {
  readonly minSipAmount: number | null;
  readonly formattedMinSipAmount: string | null;
  readonly sipFrequencies: readonly string[];
  readonly minLumpsumAmount: number | null;
  readonly formattedMinLumpsumAmount: string | null;
  readonly minAdditionalAmount: number | null;
  readonly formattedMinAdditionalAmount: string | null;
  readonly lockInPeriodDays: number | null;
  readonly exitLoadDescription: string | null;
  readonly asOfDate: string | null;
  readonly sourceArtifactId: number | null;
  readonly sourceDocumentTitle: string | null;
  readonly qualityAssessment: string;
  readonly status: 'AVAILABLE' | 'MISSING' | 'NOT_APPLICABLE';
}

export interface HoldingsSummaryDto {
  readonly asOfDate: string | null;
  readonly reportedHoldingsCount: number;
  readonly sumReportedWeights: number | null;
  readonly formattedSumReportedWeights: string | null;
  readonly sourceArtifactId: number | null;
  readonly sourceDocumentTitle: string | null;
  readonly qualityAssessment: string;
  readonly status: 'AVAILABLE' | 'MISSING' | 'NOT_APPLICABLE';
}

export interface PortfolioSnapshotDetailDto {
  readonly schemeOptionId?: number | null;
  readonly schemeName?: string | null;
  readonly portfolioDate: string | null;
  readonly reportedTotalNetAssets?: number | null;
  readonly totalNetAssets?: number | null;
  readonly formattedTotalNetAssets: string | null;
  readonly reportedHoldingsCount?: number;
  readonly holdingsCount?: number;
  readonly sumReportedWeights?: number | null;
  readonly totalPortfolioWeight?: number | null;
  readonly formattedCoverage?: string | null;
  readonly formattedTotalPortfolioWeight?: string | null;
  readonly sourceArtifactId?: number | null;
  readonly sourceSha256Hash?: string | null;
  readonly sourceArtifactHash?: string | null;
  readonly sourceUri?: string | null;
  readonly sourceDocumentTitle: string | null;
  readonly dataQualityStatus?: string;
  readonly qualityStatus?: string;
  readonly freshnessStatus: string;
  readonly dataQualityLimitations?: readonly string[];
  readonly dataLimitations?: readonly string[];
  readonly holdings: readonly Holding[];
}

export interface EnrichedFundProfileDto {
  readonly schemeId: number | null;
  readonly schemeOptionId: number;
  readonly schemeName: string;
  readonly schemeCode: string;
  readonly planType: string;
  readonly optionType: string;
  readonly amfiCode: string | null;
  readonly isin: string | null;
  readonly aum: AumDto;
  readonly expenseRatio: ExpenseRatioDto;
  readonly fundManagers: readonly FundManagerDto[];
  readonly investmentTerms: InvestmentTermsDto;
  readonly holdingsSummary: HoldingsSummaryDto;
  readonly epistemicStatus: 'VERIFIED_PRIMARY_SOURCE' | 'PARTIAL_SOURCE_COVERAGE' | 'SOURCE_NOT_FOUND';
}

export interface NavFreshnessDetailDto {
  readonly latestNavDate: string | null;
  readonly observationCount: number;
  readonly usableDateRange: string;
  readonly freshnessState: 'CURRENT' | 'STALE' | 'UNAVAILABLE' | 'UNKNOWN' | string;
  readonly cadencePolicy: string;
  readonly observationAgeDays: number | null;
  readonly sourceArtifactId: number | null;
  readonly sha256Hash: string | null;
  readonly warnings: readonly string[];
  readonly explanation: string;
}

export interface HoldingsFreshnessDetailDto {
  readonly latestReportingDate: string | null;
  readonly availabilityTime: string | null;
  readonly ingestionTimestamp: string | null;
  readonly freshnessState: 'CURRENT' | 'STALE' | 'UNAVAILABLE' | 'UNKNOWN' | string;
  readonly coverageState: 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE' | string;
  readonly cadencePolicy: string;
  readonly reportingAgeDays: number | null;
  readonly positionCount: number;
  readonly rawSumWeights: number;
  readonly rawSumWeightsPercentage: number;
  readonly unclassifiedExposurePercentage: number;
  readonly snapshotCount: number;
  readonly usableComparisonPeriods: readonly string[];
  readonly sourceArtifactId: number | null;
  readonly sha256Hash: string | null;
  readonly sourceUri: string | null;
  readonly discoveryStatus: string;
  readonly warnings: readonly string[];
  readonly explanation: string;
}

export interface MetricEligibilityItemDto {
  readonly metricCode: string;
  readonly metricName: string;
  readonly governanceTier: 'APPROVED' | 'VALIDATED' | 'CANDIDATE' | string;
  readonly eligibilityState: 'ELIGIBLE' | 'INSUFFICIENT_HISTORY' | 'INPUT_UNAVAILABLE' | 'UNVALIDATED_CANDIDATE' | string;
  readonly requiredEvidence: string;
  readonly availableEvidence: string;
  readonly explanation: string;
}

export interface DataQualityFindingDto {
  readonly category: string;
  readonly severity: 'INFO' | 'WARNING' | 'LIMITATION' | string;
  readonly title: string;
  readonly explanation: string;
}

export interface SevenQuestionsAnswerDto {
  readonly q1LatestNavObservation: string;
  readonly q2LatestHoldingsReportingDate: string;
  readonly q3HoldingsSourceAvailabilityDate: string;
  readonly q4IsDataCurrentRelativeToCadence: string;
  readonly q5CompletenessOfDisclosedHoldings: string;
  readonly q6MetricCalculabilityStatus: string;
  readonly q7EvidenceOrLimitationsToInvestigate: string;
}

export interface DataQualityFreshnessDto {
  readonly schemeOptionId: number;
  readonly schemeCode: string;
  readonly schemeName: string;
  readonly amcName: string;
  readonly planType: string;
  readonly optionType: string;
  readonly amfiCode: string | null;
  readonly isin: string | null;
  readonly evaluationDate: string | null;
  readonly knowledgeCutoff: string | null;
  readonly navEvidence: NavFreshnessDetailDto;
  readonly holdingsEvidence: HoldingsFreshnessDetailDto;
  readonly metricEligibility: readonly MetricEligibilityItemDto[];
  readonly qualityFindings: readonly DataQualityFindingDto[];
  readonly sevenQuestions: SevenQuestionsAnswerDto;
}
