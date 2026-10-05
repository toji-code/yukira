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
