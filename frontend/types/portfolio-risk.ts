export interface UnvaluedPositionDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string;
  units: number;
  reason: string;
}

export type EvidenceState =
  | 'SUFFICIENT_EVIDENCE'
  | 'LIMITED_EVIDENCE'
  | 'INSUFFICIENT_EVIDENCE'
  | 'UNAVAILABLE'
  | 'NUMERICALLY_INVALID'
  | 'ALIGNED'
  | 'PARTIALLY_ALIGNED'
  | 'STALE_OBSERVATIONS'
  | string;

export interface PortfolioRiskSummaryDto {
  totalKnownPortfolioValue: number;
  valuedFundCount: number;
  unvaluedPositionCount: number;
  knownValueAllocationCoveragePct: number;
  weightDenominator: string;
  unvaluedPositions: UnvaluedPositionDto[];
  asOfDate: string;
  knowledgeCutoff: string;
  commonStartDate: string | null;
  commonEndDate: string | null;
  alignedObservationCount: number;
  evidenceState: EvidenceState;
  windowEvidenceState: EvidenceState;
  portfolioVolatilityEvidenceState: EvidenceState;
  excludedObservationCount: number;
  calendarGapExclusionsCount: number;
  hasSynchronousDatesAcrossAllFunds: boolean;
  perFundObservationCounts: Record<string, number>;
  alignmentLimitations: string;
  annualizedPortfolioVolatilityProxy: number;
  annualizationFactor: number;
  annualizationConvention: string;
  sampleDenominatorConvention: string;
  eulerReconciliationDiscrepancy: number;
  isEulerReconciled: boolean;
  eulerEvidenceState: EvidenceState;
  methodologyDisclaimer: string;
}

export interface PortfolioCovarianceMatrixDto {
  fundIds: number[];
  fundNames: string[];
  amfiCodes: string[];
  covarianceMatrix: number[][];
  correlationMatrix: number[][];
  pairwiseObservationCounts: number[][];
  pairwiseEvidenceStates: string[][];
  matrixCompleteness: string;
  isMathematicallySuitable: boolean;
  matrixEvidenceState: EvidenceState;
  isSymmetric: boolean;
  isFinite: boolean;
  hasNonNegativeDiagonal: boolean;
  isPositiveSemiDefinite: boolean;
  isNearSingular: boolean;
  isSingular?: boolean;
  minimumEigenvalue: number;
  maximumEigenvalue: number;
  conditionNumber: number | null;
  determinant: number | null;
  matrixSuitabilityState: 'SUITABLE' | 'CONDITIONALLY_SUITABLE' | 'UNSUITABLE' | string;
  matrixSuitabilityExplanation: string;
}

export interface PortfolioFundRiskContributionDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string;
  planType: string;
  category: string;
  marketValue: number;
  allocationWeightPct: number;
  standaloneVolatilityPct: number;
  marginalRiskContributionPct: number;
  componentRiskContributionPct: number;
  percentageRiskContribution: number;
  contributionToWeightRatio: number | null;
  isNegativeContribution: boolean;
  isRiskDriver: boolean;
  isDiversificationDampener: boolean;
  classification: string;
  evidenceState: EvidenceState;
  standaloneVolatilityEvidenceState: EvidenceState;
  marginalRiskContributionEvidenceState: EvidenceState;
  componentRiskContributionEvidenceState: EvidenceState;
  isMathematicallyReliable: boolean;
  interpretation: string;
}

export interface CorrelatedPairDto {
  schemeOptionIdA: number;
  fundNameA: string;
  schemeOptionIdB: number;
  fundNameB: string;
  correlation: number;
  pairwiseObservationCount: number;
  overlapHoldingCount: number;
  interpretation: string;
}

export interface PortfolioDiversificationAnalysisDto {
  weightedStandaloneVolatilityPct: number;
  portfolioVolatilityProxyPct: number;
  diversificationEffectPct: number;
  diversificationRatio: number;
  diversificationBenefitClassification: string;
  diversificationEvidenceState: EvidenceState;
  isEvidenceSupported: boolean;
  highlyCorrelatedPairs: CorrelatedPairDto[];
  lowOrNegativeCorrelationPairs: CorrelatedPairDto[];
  sharedSecuritiesCount: number;
  sharedSecuritiesWeightPct: number;
  sectorOverlapContext: string;
  diversificationCaveat: string;
}

export interface PortfolioDownsideAnalysisDto {
  maximumDrawdownPct: number;
  maxDrawdownPeakDate: string | null;
  maxDrawdownTroughDate: string | null;
  drawdownDurationTradingDays: number;
  recoveryTradingDays: number;
  hasRecovered: boolean;
  drawdownEvidenceState: EvidenceState;
  drawdownLimitationExplanation: string;
  negativeReturnFrequencyPct: number;
  historicalVaR95Pct: number;
  historicalExpectedShortfall95Pct: number;
  varConfidenceLevel: number;
  effectiveTailObservationCount: number;
  requiredMinimumTailObservations: number;
  varQuantileConvention: string;
  esAveragingConvention: string;
  varEvidenceState: EvidenceState;
  expectedShortfallEvidenceState: EvidenceState;
  observationCount: number;
  metricGovernanceStatus: string;
  tailSampleLimitations: string;
}

export type StressWithholdingReasonCode =
  | 'NONE'
  | 'MISSING_BENCHMARK_OBSERVATIONS'
  | 'MISSING_CONSTITUENT_OBSERVATIONS'
  | 'INCOMPLETE_CONSTITUENT_WINDOW'
  | 'PRE_INCEPTION_INSUFFICIENT_HISTORY'
  | 'INSUFFICIENT_COMMON_OBSERVATIONS'
  | 'UNSYNCHRONIZED_DATES'
  | 'NON_FINITE_RETURN'
  | 'NEGATIVE_OR_ZERO_WEALTH'
  | 'INVALID_CARINO_DOMAIN'
  | 'ATTRIBUTION_RECONCILIATION_FAILED'
  | string;

export interface RiskInvestigationFindingDto {
  id: string;
  findingType: 'OBSERVED_FACT' | 'CALCULATED_RISK' | 'INTERPRETATION' | 'DATA_LIMITATION' | 'UNKNOWN' | string;
  title: string;
  headline: string;
  evidence: string;
  suggestedInvestigation: string;
  actionableWorkspacePrompt: string;
  calculationRunId?: string | null;
  episodeId?: string | null;
  allocationSnapshotId?: string | null;
  findingVersion?: string;
  evidenceState?: string | null;
  isSuperseded?: boolean;
  supersededByRunId?: string | null;
  findingId?: string;
  lifecycleStatus?: string;
  calculationInputFingerprint?: string;
  supersededByFindingId?: string | null;
  investigationQuestion?: string;
}

export interface PortfolioLookbackWindowResultDto {
  windowCode: '1Y' | '3Y' | '5Y' | string;
  requestedObservationCount: number;
  actualObservationCount: number;
  observationStartDate: string | null;
  observationEndDate: string | null;
  windowEvidenceState: EvidenceState;
  matrixSuitabilityState: string;
  matrixEvidenceState: EvidenceState;
  minimumEigenvalue: number;
  conditionNumber: number | null;
  isPositiveSemiDefinite: boolean;
  isNearSingular: boolean;
  annualizedPortfolioVolatilityProxy: number;
  portfolioVolatilityEvidenceState: EvidenceState;
  weightedStandaloneVolatilityPct: number;
  diversificationEffectPct: number;
  diversificationRatio: number;
  diversificationEvidenceState: EvidenceState;
  maximumDrawdownPct: number;
  drawdownDurationTradingDays: number;
  hasRecovered: boolean;
  drawdownEvidenceState: EvidenceState;
  historicalVaR95Pct: number;
  historicalExpectedShortfall95Pct: number;
  effectiveTailObservationCount: number;
  varEvidenceState: EvidenceState;
  expectedShortfallEvidenceState: EvidenceState;
  fundRiskContributions: PortfolioFundRiskContributionDto[];
  correlationMatrix: number[][];
  fundNames: string[];
  fundIds: number[];
  status: 'AVAILABLE' | 'LIMITED_EVIDENCE' | 'INSUFFICIENT_EVIDENCE' | 'UNAVAILABLE' | string;
  limitationExplanation: string;
}

export interface VolatilitySensitivityComparisonDto {
  comparisonPair: '1Y_VS_3Y' | '3Y_VS_5Y' | '1Y_VS_5Y' | string;
  windowA: string;
  windowB: string;
  volatilityA: number;
  volatilityB: number;
  absoluteDifferencePct: number | null;
  relativeChangePct: number | null;
  shiftClassification: string;
  comparabilityState: string;
  explanation: string;
}

export interface CorrelationSensitivityComparisonDto {
  schemeOptionIdA: number;
  fundNameA: string;
  schemeOptionIdB: number;
  fundNameB: string;
  correlation1Y: number | null;
  correlation3Y: number | null;
  correlation5Y: number | null;
  change1Yto3Y: number | null;
  change3Yto5Y: number | null;
  hasClassificationChanged: boolean;
  isMateriallySensitive: boolean;
  comparabilityState: string;
  interpretation: string;
}

export interface FundRiskContributionSensitivityDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string;
  allocationWeightPct: number;
  percentageRiskContribution1Y: number | null;
  percentageRiskContribution3Y: number | null;
  percentageRiskContribution5Y: number | null;
  change1Yto3Y: number | null;
  change3Yto5Y: number | null;
  hasSignFlipped: boolean;
  hasClassificationShifted: boolean;
  classification1Y: string;
  classification3Y: string;
  classification5Y: string;
  sensitivityNote: string;
}

export interface DiversificationSensitivityComparisonDto {
  diversificationEffect1Y: number;
  diversificationEffect3Y: number;
  diversificationEffect5Y: number;
  diversificationRatio1Y: number;
  diversificationRatio3Y: number;
  diversificationRatio5Y: number;
  changeEffect1Yto3Y: number | null;
  changeEffect3Yto5Y: number | null;
  comparabilityState: string;
  explanation: string;
}

export interface DownsideSensitivityComparisonDto {
  maxDrawdown1Y: number;
  maxDrawdown3Y: number;
  maxDrawdown5Y: number;
  var95Pct1Y: number;
  var95Pct3Y: number;
  var95Pct5Y: number;
  expectedShortfall95Pct1Y: number;
  expectedShortfall95Pct3Y: number;
  expectedShortfall95Pct5Y: number;
  tailObservations1Y: number;
  tailObservations3Y: number;
  tailObservations5Y: number;
  comparabilityState: string;
  explanation: string;
}

export interface PortfolioLookbackSensitivityDto {
  overallComparabilityState: 'DIRECTLY_COMPARABLE' | 'CONDITIONALLY_COMPARABLE' | 'NOT_DIRECTLY_COMPARABLE' | 'INSUFFICIENT_EVIDENCE' | string;
  includedFundUniverse: string[];
  allocationWeights: number[];
  windows: PortfolioLookbackWindowResultDto[];
  volatilityComparisons: VolatilitySensitivityComparisonDto[];
  correlationComparisons: CorrelationSensitivityComparisonDto[];
  contributionComparisons: FundRiskContributionSensitivityDto[];
  diversificationComparison: DiversificationSensitivityComparisonDto;
  downsideComparison: DownsideSensitivityComparisonDto;
  comparabilityLimitations: string[];
  methodologyNotes: Record<string, string>;
}

export interface PortfolioAllocationSnapshotDto {
  snapshotId: string;
  effectiveAsOfDate: string;
  knowledgeCutoff: string;
  allocationBasis: string;
  valuedPositionCount: number;
  unvaluedPositionCount: number;
  knownValueAllocationCoveragePct: number;
  isCompleteValuationCoverage: boolean;
  portfolioConstructionConvention: string;
  snapshotProvenanceDescription: string;
  isHypotheticalStaticReplayProxy: boolean;
  knownValuedCapitalAmount?: number | null;
  totalPortfolioCapitalAmount?: number | null;
  knownValuedCapitalPercentage?: number | null;
  valuedAllocationNormalizedPercentage?: number;
  totalPositionCount?: number;
  unvaluedPositionExposureStatus?: string;
  snapshotFingerprint?: string;
  isStaticAllocationProxy?: boolean;
  totalValuedAllocationPercentage?: number;
  staticProxyDisclaimer?: string;
  valuedPositionsCount?: number;
  unvaluedPositionsCount?: number;
  weightDerivationDenominator?: string;
}

export interface StressCalculationContextDto {
  calculationRunId: string;
  portfolioAllocationSnapshotId: string;
  requestedBenchmarkCode: string;
  stressIdentificationPolicyVersion: string;
  stressEvidencePolicyVersion: string;
  stressMethodologyVersion: string;
  attributionMethodology: string;
  portfolioConstructionConvention: string;
  analysisCutoffDate: string;
  knowledgeCutoff: string;
  executionTimestamp: string;
  reconciliationTolerancePct: number;
  isReproducible: boolean;
  reproducibilityContext: string;
  calculationInputFingerprint?: string;
  benchmarkSourceRevisionId?: string | null;
  constituentSourceRevisions?: Record<string, string>;
  sourceLineageState?: string;
  sourceLineageLimitation?: string;
}

export interface PortfolioStressEvidenceRecordDto {
  recordId: string;
  investorSubject: string | null;
  allocationSnapshot: PortfolioAllocationSnapshotDto;
  calculationContext: StressCalculationContextDto;
  benchmarkReference: StressBenchmarkReferenceDto;
  summary: PortfolioStressSummaryDto | null;
  episodes: PortfolioStressEpisodeDto[];
  attachedFindings: RiskInvestigationFindingDto[];
  unvaluedPositions: UnvaluedPositionDto[];
  knownValueCoveragePct: number;
  governanceDisclaimers: string[];
  methodologyNotes: Record<string, string>;
  exportedAt: string;
  exportNotice: string;
  schemaVersion?: string;
  calculationInputFingerprint?: string;
  sourceLineageState?: string;
  portfolioAllocationSnapshotId?: string | null;
}

export interface StressDataQualitySignalDto {
  signalId?: string;
  signalType: 'OBSERVATION_GAP_DETECTED' | 'POTENTIAL_STALE_NAV' | 'POTENTIAL_NON_TRADING' | 'SOURCE_REPORTING_DELAY_POSSIBLE' | 'DATA_QUALITY_REVIEW_REQUIRED' | 'NO_ANOMALY_DETECTED' | string;
  severity: 'INFO' | 'WARNING' | 'REVIEW_RECOMMENDED' | string;
  schemeOptionId: number | null;
  fundName: string | null;
  startDate: string | null;
  endDate: string | null;
  affectedDaysCount: number;
  affectedObservationCount?: number;
  affectedStartDate?: string | null;
  affectedEndDate?: string | null;
  calculationWithheld?: boolean;
  description: string;
  isCalculationWithholding: boolean;
  signalLifecycleState?: 'ACTIVE_WARNING' | 'ACKNOWLEDGED' | 'RESOLVED' | 'INFORMATIONAL' | string;
  firstDetectedRunId?: string | null;
  detectionTimestamp?: string | null;
  sourceRevisionReference?: string | null;
  isBenchmarkSignal?: boolean;
  benchmarkCode?: string | null;
}

export interface FundStressContributionDto {
  schemeOptionId: number;
  fundName: string;
  amfiCode: string;
  category: string;
  currentAllocationWeightPct: number;
  fundCumulativeReturnPct: number | null;
  weightedReturnContributionPct: number | null;
  approximateWeightedReturnIndicatorPct: number | null;
  exactAdditiveReturnContributionPct: number | null;
  weightedReturnToLinkedAttributionDifferencePct: number | null;
  rebalancingInteractionPct: number | null;
  fundMaxDrawdownPct: number | null;
  fundPeakDate: string | null;
  fundTroughDate: string | null;
  alignedObservationCount: number;
  expectedObservations: number;
  availableObservations: number;
  constituentCoveragePct: number;
  hasPreInception: boolean;
  earliestKnownNavDate: string | null;
  evidenceState: EvidenceState;
  attributionRole: 'POSITIVE_CONTRIBUTOR' | 'RESILIENT_LOWER_LOSS_CONTRIBUTOR' | 'MODERATE_DETRACTOR' | 'PRIMARY_DETRACTOR' | 'NEUTRAL_CONTRIBUTOR' | 'LIMITED_EVIDENCE' | 'INSUFFICIENT_EVIDENCE' | 'NUMERICALLY_INVALID' | string;
  notes: string;
  metricEvidenceState?: string;
  attributionSign?: 'POSITIVE' | 'NEGATIVE' | 'ZERO' | 'UNDEFINED' | string;
  outperformedPortfolioProxy?: boolean;
  attributionRoleInterpretation?: string;
  usableObservationCount?: number;
  excludedObservationCount?: number;
  preInceptionDateCount?: number;
  missingNavDateCount?: number;
  invalidNavDateCount?: number;
  standalonePerformanceRole?: string;
  linkedAttributionRole?: string;
  numericalValidityState?: string;
  constituentWindowCoverage?: string;
  tradingDaysInWindow?: number;
  calendarDaysInWindow?: number;
  sourceQualityState?: string;
  attributionReconciliationStatus?: string;
  withholdingReasonCode?: StressWithholdingReasonCode | string;
  withholdingReasonExplanation?: string | null;
}

export interface PortfolioStressEpisodeDto {
  episodeId: string;
  legacyEpisodeId?: string | null;
  episodeName: string;
  regimeCategory: string;
  startDate: string;
  endDate: string;
  benchmarkPeakDate: string;
  firstDeclineDate: string;
  benchmarkTroughDate: string;
  benchmarkRecoveryDate: string | null;
  benchmarkHasRecovered: boolean;
  benchmarkRecoveryState: 'BENCHMARK_RECOVERED' | 'BENCHMARK_UNRECOVERED' | string;
  benchmarkMaxDrawdownPct: number;
  benchmarkCumulativeReturnPct: number | null;
  portfolioCumulativeReturnPct: number | null;
  activeReturnPct: number | null;
  portfolioMaxDrawdownPct: number | null;
  portfolioPeakDate: string | null;
  portfolioTroughDate: string | null;
  portfolioRecoveryDate: string | null;
  portfolioHasRecovered: boolean;
  portfolioRecoveryState: 'PORTFOLIO_RECOVERED' | 'PORTFOLIO_UNRECOVERED' | 'RECOVERY_UNAVAILABLE' | string;
  recoveryComparison: 'BOTH_RECOVERED' | 'BENCHMARK_RECOVERED_PORTFOLIO_LAGGING' | 'PORTFOLIO_RECOVERED_BENCHMARK_LAGGING' | 'BOTH_UNRECOVERED' | 'RECOVERY_UNAVAILABLE' | string;
  portfolioAnnualizedVolatilityPct: number | null;
  worstDailyReturnPct: number | null;
  worstDailyReturnDate: string | null;
  bestDailyReturnPct: number | null;
  bestDailyReturnDate: string | null;
  alignedTradingDaysCount: number;
  hasReducedCommonDateSubset?: boolean;
  usableObservationCount?: number;
  expectedObservationCount?: number;
  observationCoveragePct?: number;
  excludedObservationCount?: number;
  preInceptionDateCount?: number;
  missingNavDateCount?: number;
  invalidNavDateCount?: number;
  excludedObservationReasons?: string[];
  expectedBenchmarkTradingDays: number;
  benchmarkObservationCount: number;
  portfolioCoverageRatioPct: number;
  knownValueAllocationCoveragePct: number;
  missingConstituentCount: number;
  partialConstituentCount: number;
  includedConstituents: string[];
  excludedOrUnavailableConstituents: string[];
  missingAllocationWeightPct: number;
  partialPortfolioNotice: string | null;
  evidenceState: EvidenceState;
  recoveryState: 'RECOVERED' | 'ONGOING_UNRECOVERED' | 'NOT_APPLICABLE' | string;
  fundContributions: FundStressContributionDto[];
  stressProxyConvention: string;
  portfolioConstructionConvention: string;
  attributionMethod: string;
  attributionReconciliationStatus: 'EXACTLY_RECONCILED' | 'RECONCILIATION_FAILED' | 'NUMERICALLY_INVALID' | 'UNAVAILABLE' | 'NOT_APPLICABLE' | string;
  attributionDiscrepancyPct: number | null;
  compoundingResidualPct: number | null;
  benchmarkCodeActuallyUsed: string;
  isFallbackBenchmark: boolean;
  benchmarkComparabilityNotice: string | null;
  comparabilityState: 'DIRECTLY_COMPARABLE' | 'CONDITIONALLY_COMPARABLE_FALLBACK_BENCHMARK' | 'CONDITIONALLY_COMPARABLE_PARTIAL_HISTORY' | 'NOT_DIRECTLY_COMPARABLE_INSUFFICIENT_EVIDENCE' | string;
  comparabilityLimitations: string[];
  analysisCutoffDate: string;
  evidenceLimitationReason: string | null;
  calculationAvailability?: 'CALCULATED' | 'WITHHELD_INSUFFICIENT_DATA' | 'WITHHELD_NUMERICALLY_INVALID' | string;
  dataCompleteness?: 'COMPLETE_WINDOW' | 'PARTIAL_BENCHMARK_SYNCHRONIZED_WINDOW' | 'TRUNCATED_CONSTITUENT_WINDOW' | 'INCOMPLETE_INSUFFICIENT_OBSERVATIONS' | string;
  statisticalEvidenceAdequacy?: 'DESCRIPTIVE_EVENT_SAMPLE' | 'LIMITED_EVENT_SAMPLE' | 'INSUFFICIENT_SAMPLE' | 'NUMERICALLY_INVALID' | string;
  volatilityEvidenceState?: 'DESCRIPTIVE_ONLY_HIGH_VARIANCE_SAMPLE' | 'INSUFFICIENT_OBSERVATIONS_FOR_VOLATILITY' | 'NOT_APPLICABLE' | string;
  tailEvidenceState?: 'DESCRIPTIVE_EXTREME_OBSERVATION' | 'NOT_APPLICABLE' | string;
  knowledgeCutoff?: string | null;
  evidencePolicyVersion?: string;
  usableCommonTradingDaysCount?: number;
  excludedBenchmarkTradingDaysCount?: number;
  excludedDateReasons?: string[];
  commonSubsetStartDate?: string;
  commonSubsetEndDate?: string;
  preInceptionDateExclusionCount?: number;
  missingNavDateExclusionCount?: number;
  invalidNavDateExclusionCount?: number;
  peakToTroughLossPct?: number | null;
  tradingDaysToTrough?: number;
  tradingDaysToRecovery?: number | null;
  unrecoveredDurationDays?: number | null;
  benchmarkTradingDaysToTrough?: number;
  benchmarkTradingDaysToRecovery?: number | null;
  dataQualitySignals?: StressDataQualitySignalDto[];
  dataQualityWarningCount?: number;
  stressIdentificationPolicyVersion?: string;
  stressMethodologyVersion?: string;
  calculationRunId?: string;
  episodeLifecycleState?: 'ACTIVE_DECLINE' | 'ONGOING_RECOVERY' | 'RECOVERED' | 'UNRECOVERED_CUTOFF_REACHED' | 'RECOVERY_UNVERIFIABLE_DATA_GAP' | string;
  lifecycleTransitionContext?: string | null;
  calculationEligibilityOutcome?: 'ELIGIBLE_COMPLETE_WINDOW' | 'ELIGIBLE_COMMON_DATE_SUBSET' | 'WITHHELD_INCOMPLETE_CONSTITUENT_HISTORY' | 'WITHHELD_INSUFFICIENT_OBSERVATIONS' | 'WITHHELD_NUMERICALLY_INVALID' | 'UNAVAILABLE_SOURCE_DATA' | string;
  benchmarkObservationAvailability?: 'AVAILABLE' | 'NO_OBSERVATIONS' | string;
  constituentObservationAvailability?: 'ALL_CONSTITUENTS_AVAILABLE' | 'PARTIAL_CONSTITUENTS_AVAILABLE' | 'INSUFFICIENT_CONSTITUENT_HISTORY' | 'UNAVAILABLE_SOURCE_DATA' | string;
  commonDateSynchronization?: 'SYNCHRONIZED_COMPLETE' | 'SYNCHRONIZED_COMMON_SUBSET' | 'UNSYNCHRONIZED_INSUFFICIENT_OVERLAP' | string;
  constituentUniverseCompleteness?: 'COMPLETE_PORTFOLIO_UNIVERSE' | 'INCOMPLETE_PORTFOLIO_UNIVERSE' | string;
  numericalValidityState?: 'VALID' | 'NUMERICALLY_INVALID' | string;
  constituentWindowCoverage?: 'COMPLETE_WINDOW' | 'COMMON_DATE_SUBSET' | 'TRUNCATED_CONSTITUENT_WINDOW' | 'NO_COVERAGE' | string;
  constituentObservationStartDate?: string | null;
  constituentObservationEndDate?: string | null;
  calendarDaysToTrough?: number;
  calendarDaysToRecovery?: number | null;
  unrecoveredTradingDaysDuration?: number | null;
  unrecoveredCalendarDaysDuration?: number | null;
  benchmarkCalendarDaysToTrough?: number;
  benchmarkCalendarDaysToRecovery?: number | null;
  portfolioAllocationSnapshotId?: string | null;
  calculationInputFingerprint?: string;
  benchmarkSourceQualityState?: string;
  isPortfolioReturnComputable?: boolean;
  withholdingReasonCode?: StressWithholdingReasonCode | string;
  withholdingReasonExplanation?: string | null;
}

export interface StressBenchmarkReferenceDto {
  benchmarkCode: string;
  benchmarkName: string;
  provider: string;
  benchmarkId: number | null;
  minObservationDate: string | null;
  maxObservationDate: string | null;
  totalObservationCount: number;
  status: string;
  isFallbackBenchmark: boolean;
  comparabilityNotice: string | null;
  benchmarkSeriesType?: string;
  fallbackReason?: string | null;
  benchmarkMethodologyVersion?: string;
  observationCoverageRatioPct?: number;
  effectiveObservationWindow?: string;
}

export interface PortfolioStressSummaryDto {
  totalEpisodesEvaluated: number;
  episodesSufficientEvidence: number;
  episodesLimitedEvidence: number;
  episodesInsufficientEvidence: number;
  worstPortfolioDrawdownPct: number | null;
  worstDrawdownEpisodeId: string | null;
  worstPortfolioCumulativeReturnPct: number | null;
  worstReturnEpisodeId: string | null;
  averageStressCumulativeReturnPct: number | null;
  mostResilientFundName: string;
  greatestDetractorFundName: string;
  overallStressAssessment: string;
  aggregationConvention: string;
  comparabilityNotice: string | null;
  episodesEligible?: number;
  episodesComputable?: number;
  episodesCompleteWindow?: number;
  episodesCommonDateSubset?: number;
  episodesWithheld?: number;
  episodesUnavailable?: number;
  episodesExcluded?: number;
  episodesWithFallbackBenchmark?: number;
  aggregationDenominator?: string;
  comparabilityLimitations?: string[];
  portfolioAllocationSnapshotId?: string | null;
  calculationRunId?: string | null;
  directlyComparableEpisodesCount?: number;
  fallbackBenchmarkEpisodesCount?: number;
  commonDateSubsetEpisodesCount?: number;
  withheldEpisodesCount?: number;
  directlyComparableAverageReturnPct?: number | null;
  fallbackBenchmarkAverageReturnPct?: number | null;
  commonDateSubsetAverageReturnPct?: number | null;
  groupingExplanation?: string;
  calculationInputFingerprint?: string;
}

export interface PortfolioStressAnalysisResponseDto {
  evaluationStatus: string;
  benchmarkReference: StressBenchmarkReferenceDto;
  summary: PortfolioStressSummaryDto | null;
  episodes: PortfolioStressEpisodeDto[];
  evidencePolicyVersion: string;
  identificationPolicyVersion: string;
  methodologyVersion: string;
  portfolioConstructionConvention: string;
  attributionMethodology: string;
  governanceDisclaimers: string[];
  methodologyNotes: Record<string, string>;
  knownValueCoveragePct: number;
  unvaluedPositions: UnvaluedPositionDto[];
  calculationRunId: string | null;
  portfolioAllocationSnapshotId?: string | null;
  asOfDate: string | null;
  knowledgeCutoff: string | null;
  reconciliationTolerance: number;
  overallComparabilityState: string;
  allocationSnapshot?: PortfolioAllocationSnapshotDto | null;
  calculationContext?: StressCalculationContextDto | null;
  calculationInputFingerprint?: string;
  sourceLineageState?: string;
  evaluationContextType?: string;
}

export interface PortfolioRiskDiversificationResponseDto {
  evaluationStatus: string;
  summary: PortfolioRiskSummaryDto;
  covarianceMatrix: PortfolioCovarianceMatrixDto | null;
  riskContributions: PortfolioFundRiskContributionDto[];
  diversification: PortfolioDiversificationAnalysisDto | null;
  downsideAnalysis: PortfolioDownsideAnalysisDto | null;
  investigationFindings: RiskInvestigationFindingDto[];
  governanceDisclaimers: string[];
  methodologyNotes: Record<string, string>;
  calculationRunId: string;
  evidenceThresholdPolicyVersion: string;
  classificationPolicyVersion?: string;
  numericalTolerance: number;
  metricRegistryMappings: Record<string, string>;
  lookbackSensitivity?: PortfolioLookbackSensitivityDto | null;
  historicalStressAnalysis?: PortfolioStressAnalysisResponseDto | null;
  allocationSnapshot?: PortfolioAllocationSnapshotDto | null;
}

export interface PortfolioRiskDiversificationRequestDto {
  asOfDate?: string;
  knowledgeCutoff?: string;
  benchmarkCode?: string;
  overrideSchemeOptionIds?: number[];
}
