/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F25: Research Coverage Gaps and Investigation Completeness Triage
 *
 * Implements deterministic tracking of research coverage across fund diligence dimensions,
 * distinguishing missing documentation from genuine evidence limitations, connecting
 * actionable gaps to existing follow-up investigations, and recommending prioritized next diligence steps.
 *
 * CRITICAL EPISTEMIC GUARDRAILS:
 * 1. COVERAGE IS NOT SUITABILITY: Research coverage measures whether diligence records exist.
 *    It NEVER implies fund quality, manager skill, return potential, or an investment recommendation.
 * 2. MISSING DOCUMENTATION != BAD FUND: An unexamined area or unrecorded checklist item
 *    is an epistemic limitation of the research record, NOT proof of a fund defect or risk.
 * 3. NO COMPOSITE SCORES: No numerical coverage grades, quality indexes, or artificial completion percentages.
 * 4. DETERMINISTIC CLASSIFICATION: Gaps are categorized based strictly on stored evidence and user inputs:
 *    DOCUMENTATION_GAP, EVIDENCE_GAP, OPEN_INVESTIGATION, EVIDENCE_TRACEABILITY_LIMITATION, RECORDED_UNCERTAINTY.
 * 5. EXACT IDENTITY: Exact (schemeId, schemeOptionId) tuples and plan/option designations are strictly preserved.
 */

import { WatchlistResponse } from "@/lib/api/investor";
import {
  ResearchFollowUpIssue,
  evaluateFindingEvidenceTraceability,
  isTargetDatePassed,
} from "@/lib/utils/researchFollowUp";
import {
  ReviewItemKey,
  ReviewItemStatus,
  CHECKLIST_ITEMS_CONFIG,
  InvestigationReviewChecklistRecord,
  isItemReassessmentNeeded,
} from "@/lib/utils/researchReviewChecklist";
import { EvidenceChangeSummary } from "@/lib/utils/evidenceChange";
import {
  PortfolioContextResolution,
  resolvePortfolioContext,
} from "@/lib/utils/portfolioContext";
import { PortfolioSummaryDto } from "@/lib/api/portfolio";
import { AnalyticalProfileResponse } from "@/types/analysis";
import { isFieldRecorded } from "@/lib/utils/researchCompleteness";
import { getWatchlistItemKey } from "@/lib/hooks/useWatchlist";

export type ResearchCoverageState =
  | "DOCUMENTED"
  | "INCOMPLETE"
  | "NOT_DOCUMENTED"
  | "EVIDENCE_UNAVAILABLE"
  | "INVESTIGATION_PENDING"
  | "UNCERTAIN";

export interface ResearchCoverageStateMeta {
  state: ResearchCoverageState;
  label: string;
  shortLabel: string;
  badgeClass: string;
  description: string;
}

export const RESEARCH_COVERAGE_STATES: Record<ResearchCoverageState, ResearchCoverageStateMeta> = {
  DOCUMENTED: {
    state: "DOCUMENTED",
    label: "Documented",
    shortLabel: "Documented",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/40",
    description: "Existing research records confirm this area was examined by the investor with supporting evidence.",
  },
  INCOMPLETE: {
    state: "INCOMPLETE",
    label: "Incomplete",
    shortLabel: "Incomplete",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: "Some relevant notes or observations are recorded, but the investigation remains unfinished.",
  },
  NOT_DOCUMENTED: {
    state: "NOT_DOCUMENTED",
    label: "Not Documented",
    shortLabel: "Not Documented",
    badgeClass: "bg-surface-raised text-text-muted border-border",
    description: "Existing records do not establish that this dimension was examined.",
  },
  EVIDENCE_UNAVAILABLE: {
    state: "EVIDENCE_UNAVAILABLE",
    label: "Evidence Unavailable",
    shortLabel: "Data Missing",
    badgeClass: "bg-rose-500/15 text-rose-300 border-rose-500/40",
    description: "Necessary source data, quantitative calculation, or baseline cutoff is not available.",
  },
  INVESTIGATION_PENDING: {
    state: "INVESTIGATION_PENDING",
    label: "Investigation Pending",
    shortLabel: "Pending Issue",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
    description: "An active follow-up inquiry indicates outstanding due diligence on this dimension.",
  },
  UNCERTAIN: {
    state: "UNCERTAIN",
    label: "Uncertain",
    shortLabel: "Uncertain",
    badgeClass: "bg-yellow-500/15 text-yellow-300 border-yellow-500/40",
    description: "Existing research explicitly records residual uncertainty or uncorroborated limitations.",
  },
};

export type GapClassificationCode =
  | "DOCUMENTATION_GAP"
  | "EVIDENCE_GAP"
  | "OPEN_INVESTIGATION"
  | "EVIDENCE_TRACEABILITY_LIMITATION"
  | "RECORDED_UNCERTAINTY"
  | "NO_IDENTIFIED_GAP";

export interface GapClassification {
  code: GapClassificationCode;
  label: string;
  shortLabel: string;
  badgeClass: string;
  description: string;
}

export const GAP_CLASSIFICATIONS: Record<GapClassificationCode, GapClassification> = {
  DOCUMENTATION_GAP: {
    code: "DOCUMENTATION_GAP",
    label: "Documentation Gap",
    shortLabel: "Doc Gap",
    badgeClass: "bg-surface-raised text-text-muted border-border",
    description: "No sufficient research record is available to establish that this question was examined.",
  },
  EVIDENCE_GAP: {
    code: "EVIDENCE_GAP",
    label: "Evidence Gap",
    shortLabel: "Evidence Missing",
    badgeClass: "bg-rose-500/15 text-rose-300 border-rose-500/40",
    description: "Necessary source data or supporting calculation evidence is unavailable or unrecorded.",
  },
  OPEN_INVESTIGATION: {
    code: "OPEN_INVESTIGATION",
    label: "Open Investigation",
    shortLabel: "Active Issue",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
    description: "An associated follow-up inquiry remains unresolved on the active diligence slate.",
  },
  EVIDENCE_TRACEABILITY_LIMITATION: {
    code: "EVIDENCE_TRACEABILITY_LIMITATION",
    label: "Traceability Limitation",
    shortLabel: "Drift / No Provenance",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: "Historical finding lacks provenance or underlying evidence context has shifted since review.",
  },
  RECORDED_UNCERTAINTY: {
    code: "RECORDED_UNCERTAINTY",
    label: "Recorded Uncertainty",
    shortLabel: "Uncertainty",
    badgeClass: "bg-yellow-500/15 text-yellow-300 border-yellow-500/40",
    description: "Investor finding explicitly acknowledged an unresolved limitation or pending future disclosure.",
  },
  NO_IDENTIFIED_GAP: {
    code: "NO_IDENTIFIED_GAP",
    label: "No Identified Gap",
    shortLabel: "Documented",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/40",
    description: "Existing records do not indicate an outstanding coverage gap (does not imply fund safety).",
  },
};

export type CoverageNextActionType =
  | "NAVIGATE_SECTION"
  | "VIEW_ISSUE"
  | "CREATE_ISSUE"
  | "REASSESS_EVIDENCE"
  | "RECORD_UNAVAILABLE";

export interface CoverageDimensionItem {
  key: ReviewItemKey;
  dimensionId: string;
  title: string;
  shortLabel: string;
  briefSection: "A" | "B" | "C" | "D" | "E" | "F" | "G";
  description: string;
  evidenceSource: string;

  // Evaluation outcome
  coverageState: ResearchCoverageState;
  state: ResearchCoverageState;
  coverageStateMeta: ResearchCoverageStateMeta;
  gapClassifications: GapClassification[];
  primaryGap: GapClassification;

  // Explainability
  whyStatusAssigned: string;
  explanation: string;
  suggestedNextAction: string;
  nextActionType: CoverageNextActionType;
  nextActionRecommendation?: {
    actionType: CoverageNextActionType;
    actionExplanation: string;
  };
  hasActionableGap: boolean;
  checklistKey?: ReviewItemKey;

  // Connected investigations for this dimension (exact fund option)
  associatedIssues: ResearchFollowUpIssue[];
  activeIssue: ResearchFollowUpIssue | null;
  hasResidualUncertainty: boolean;
  residualUncertaintyNotes: string[];

  // Review checklist facts
  checklistStatus: ReviewItemStatus;
  isReviewed: boolean;
  isReassessmentNeeded: boolean;
  isApplicable: boolean;
}

export interface FundCoverageReport {
  schemeId: number;
  schemeOptionId?: number | null;
  schemeName: string;
  schemeCode: string;
  planType: string;
  optionType: string;
  amfiCode: string | null;
  isin: string | null;

  // Summary counts
  totalDimensions: number;
  documentedCount: number;
  actionableGapCount: number;
  hasActionableGaps: boolean;
  incompleteCount: number;
  notDocumentedCount: number;
  evidenceUnavailableCount: number;
  investigationPendingCount: number;
  uncertainCount: number;

  // Gap category counts
  documentationGapCount: number;
  evidenceGapCount: number;
  openInvestigationCount: number;
  traceabilityLimitationCount: number;
  recordedUncertaintyCount: number;
  noGapCount: number;

  // Full dimension breakdowns
  dimensions: CoverageDimensionItem[];
  prioritizedGaps: CoverageDimensionItem[];

  // Explanatory summary
  descriptiveSummary: string;
}

export type CrossFundCoverageFilter =
  | "ALL"
  | "ACTIONABLE_GAPS"
  | "OPEN_INVESTIGATIONS"
  | "DATA_OR_PROVENANCE_LIMITS"
  | "RECORDED_UNCERTAINTY"
  | "NO_IDENTIFIED_GAPS";

export interface FundCoverageOverviewItem {
  fund: WatchlistResponse;
  report: FundCoverageReport;
  key: string; // schemeId:schemeOptionId
  schemeId: number;
  schemeOptionId: number | null | undefined;
  schemeName: string;
  schemeCode: string;
  planType: string;
  optionType: string;
  amfiCode: string | null;
  isin: string | null;

  // Overview metrics
  documentedCount: number;
  totalDimensions: number;
  actionableGapCount: number;
  documentationGapCount: number;
  openInvestigationCount: number;
  evidenceUnavailableCount: number;
  traceabilityLimitationCount: number;
  recordedUncertaintyCount: number;
  hasActionableGaps: boolean;

  // Investigation context
  associatedIssues: ResearchFollowUpIssue[];
  activeIssues: ResearchFollowUpIssue[];
  overdueIssues: ResearchFollowUpIssue[];
  reopenedIssues: ResearchFollowUpIssue[];
  driftIssues: ResearchFollowUpIssue[];
  uncertaintyIssues: ResearchFollowUpIssue[];

  // Deterministic workflow prioritization
  priorityTier: number; // 1 (Highest) to 7 (Documented, no gaps)
  priorityTierLabel: string;
  priorityBadgeClass: string;
  primaryWorkflowReason: string;
  allWorkflowReasons: string[];
  mostUrgentGap: CoverageDimensionItem | null;
  suggestedNextStep: string;
  nextActionType: CoverageNextActionType;
}

export interface CrossFundCoverageSummary {
  totalFundsAssessed: number;
  fundsWithActionableGaps: number;
  fundsWithOpenInvestigations: number;
  fundsWithMissingEvidence: number;
  fundsWithResidualUncertainty: number;
  fundsWithNoIdentifiedGaps: number;
  totalActionableGapsAcrossFunds: number;
  totalOpenInvestigationsAcrossFunds: number;
}

/**
 * Deterministically evaluates research coverage across all 8 core diligence dimensions
 * for a specific fund share class.
 *
 * Epistemic Rules:
 * 1. An area is only DOCUMENTED if the investor has reviewed the evidence or populated required thesis entries.
 * 2. Missing documentation is strictly classified as DOCUMENTATION_GAP, never conflated with EVIDENCE_GAP.
 * 3. Unavailable quantitative data or missing baselines are classified as EVIDENCE_GAP.
 * 4. Unresolved follow-up inquiries trigger INVESTIGATION_PENDING and OPEN_INVESTIGATION.
 * 5. Recorded residual uncertainty triggers UNCERTAIN and RECORDED_UNCERTAINTY.
 * 6. Evidence drift or superseded runs trigger EVIDENCE_TRACEABILITY_LIMITATION.
 */
export function evaluateFundResearchCoverage(
  fund: WatchlistResponse,
  issues: ResearchFollowUpIssue[],
  checklist?: InvestigationReviewChecklistRecord | null,
  evidenceSummary?: EvidenceChangeSummary | null,
  profile?: AnalyticalProfileResponse | null,
  portfolioContext?: PortfolioContextResolution | null
): FundCoverageReport {
  const currentRunId =
    profile?.provenance?.calculationRunId ||
    null;
  const currentAsOf =
    profile?.context?.asOfDate ? String(profile.context.asOfDate) : fund.latestNavDate || "2024-01-15";

  // Filter issues strictly for this exact share class identity
  const fundIssues = issues.filter((i) => {
    if (fund.schemeOptionId !== undefined && fund.schemeOptionId !== null) {
      return i.schemeOptionId === fund.schemeOptionId;
    }
    return i.schemeId === fund.schemeId;
  });

  const dimensions: CoverageDimensionItem[] = CHECKLIST_ITEMS_CONFIG.map((config) => {
    const isApplicable = config.defaultApplicable(fund, portfolioContext);
    const chkItem = checklist?.items[config.key];
    const checklistStatus: ReviewItemStatus =
      chkItem?.status || (isApplicable ? "NOT_REVIEWED" : "NOT_APPLICABLE");
    const isReviewed = checklistStatus === "REVIEWED";
    const isReassessmentNeeded = chkItem
      ? isItemReassessmentNeeded(chkItem, config.key, evidenceSummary)
      : false;

    // Associate issues by matching checklistKey or briefSection
    const associatedIssues = fundIssues.filter(
      (iss) =>
        iss.checklistKey === config.key ||
        (iss.briefSection === config.briefSection && (!iss.checklistKey || iss.checklistKey === config.key))
    );

    const activeIssues = associatedIssues.filter(
      (i) => i.status === "OPEN" || i.status === "IN_PROGRESS" || i.status === "WAITING"
    );
    const activeIssue = activeIssues.length > 0 ? activeIssues[0] : null;

    const uncertaintyIssues = associatedIssues.filter(
      (i) =>
        Boolean(i.hasResidualUncertainty) ||
        Boolean(i.residualUncertainty && i.residualUncertainty.trim().length > 0) ||
        i.outcomeType === "PARTIALLY_ANSWERED" ||
        i.outcomeType === "DATA_UNAVAILABLE"
    );
    const hasResidualUncertainty = uncertaintyIssues.length > 0;
    const residualUncertaintyNotes = uncertaintyIssues
      .map((i) => i.residualUncertainty?.trim())
      .filter((n): n is string => Boolean(n && n.length > 0));

    // Traceability drift on associated issues
    const hasDriftIssues = associatedIssues.some((iss) => {
      const prov = evaluateFindingEvidenceTraceability(iss, currentRunId, currentAsOf);
      return prov.status === "HISTORICAL_EVIDENCE_CHANGED" || prov.status === "HISTORICAL_RUN_SUPERSEDED";
    });

    // Check specific evidence availability by dimension
    let isEvidenceUnavailable = false;
    if (config.key === "QUANTITATIVE_PROFILE" || config.key === "RISK_LIMITATIONS") {
      // Evidence unavailable if profile has zero metrics or explicitly flagged
      if (evidenceSummary?.status === "HISTORICAL_UNAVAILABLE") {
        isEvidenceUnavailable = true;
      }
    } else if (config.key === "EVIDENCE_HISTORY") {
      if (evidenceSummary?.status === "HISTORICAL_UNAVAILABLE") {
        isEvidenceUnavailable = true;
      }
    }

    // Check underlying investor documentation presence
    let isDocRecorded = false;
    let partialDocRecorded = false;

    if (config.key === "IDENTIFY_OPTION") {
      isDocRecorded = Boolean(fund.schemeName && (fund.schemeCode || fund.amfiCode || fund.isin));
    } else if (config.key === "QUANTITATIVE_PROFILE") {
      isDocRecorded = Boolean(profile && ((profile.returnMetrics?.length || 0) > 0 || (profile.riskMetrics?.length || 0) > 0));
    } else if (config.key === "RISK_LIMITATIONS") {
      isDocRecorded = Boolean(profile && ((profile.returnMetrics?.length || 0) > 0 || (profile.riskMetrics?.length || 0) > 0));
    } else if (config.key === "THESIS_EVIDENCE") {
      const hasThesis = isFieldRecorded(fund.workingThesis);
      const hasEvidence = isFieldRecorded(fund.supportingEvidence);
      isDocRecorded = hasThesis && hasEvidence;
      partialDocRecorded = hasThesis || hasEvidence;
    } else if (config.key === "RECONSIDERATION_FACTORS") {
      isDocRecorded = isFieldRecorded(fund.reconsiderationConditions);
    } else if (config.key === "EVIDENCE_HISTORY") {
      isDocRecorded = Boolean(evidenceSummary && evidenceSummary.status !== "HISTORICAL_UNAVAILABLE");
    } else if (config.key === "PORTFOLIO_CONTEXT") {
      isDocRecorded = Boolean(portfolioContext && (portfolioContext.isExactMatch || portfolioContext.isVariantMatch || fund.isHeldInPortfolio !== undefined));
    } else if (config.key === "QUESTIONS_NEXT_STEPS") {
      const hasQ = isFieldRecorded(fund.investigationQuestion);
      const hasStep = isFieldRecorded(fund.nextStep);
      isDocRecorded = hasQ || hasStep || associatedIssues.length > 0;
    }

    // Determine Gap Classifications (deterministic multi-label)
    const gapClassifications: GapClassification[] = [];

    if (isEvidenceUnavailable) {
      gapClassifications.push(GAP_CLASSIFICATIONS.EVIDENCE_GAP);
    }
    if (activeIssue) {
      gapClassifications.push(GAP_CLASSIFICATIONS.OPEN_INVESTIGATION);
    }
    if (isReassessmentNeeded || hasDriftIssues) {
      gapClassifications.push(GAP_CLASSIFICATIONS.EVIDENCE_TRACEABILITY_LIMITATION);
    }
    if (hasResidualUncertainty) {
      gapClassifications.push(GAP_CLASSIFICATIONS.RECORDED_UNCERTAINTY);
    }
    if (!isReviewed && !isDocRecorded && isApplicable) {
      gapClassifications.push(GAP_CLASSIFICATIONS.DOCUMENTATION_GAP);
    } else if (!isReviewed && isApplicable && !activeIssue && !isEvidenceUnavailable) {
      gapClassifications.push(GAP_CLASSIFICATIONS.DOCUMENTATION_GAP);
    }

    if (gapClassifications.length === 0) {
      gapClassifications.push(GAP_CLASSIFICATIONS.NO_IDENTIFIED_GAP);
    }

    // Determine Overall Coverage State
    let coverageState: ResearchCoverageState;
    let whyStatusAssigned: string;

    if (!isApplicable) {
      coverageState = "DOCUMENTED";
      whyStatusAssigned = "Marked not applicable for this fund share class and portfolio situation.";
    } else if (isEvidenceUnavailable) {
      coverageState = "EVIDENCE_UNAVAILABLE";
      whyStatusAssigned = "Supporting calculations or baseline data are unavailable in current environment.";
    } else if (activeIssue) {
      coverageState = "INVESTIGATION_PENDING";
      whyStatusAssigned = `Unresolved investigation logged: "${activeIssue.title}" (${activeIssue.status}).`;
    } else if (hasResidualUncertainty) {
      coverageState = "UNCERTAIN";
      whyStatusAssigned = `Documented outcome carries explicit residual uncertainty or uncorroborated limitations.`;
    } else if (isReassessmentNeeded) {
      coverageState = "INCOMPLETE";
      whyStatusAssigned = "Evidence has drifted since last review; renewed inspection against active calculation run required.";
    } else if (isReviewed && (isDocRecorded || config.key === "QUANTITATIVE_PROFILE" || config.key === "RISK_LIMITATIONS" || config.key === "IDENTIFY_OPTION")) {
      coverageState = "DOCUMENTED";
      whyStatusAssigned = "Investor has inspected the evidence and recorded findings without outstanding limitations.";
    } else if (isReviewed || partialDocRecorded || isDocRecorded) {
      coverageState = "INCOMPLETE";
      whyStatusAssigned = isReviewed
        ? "Inspected by investor, but core documentation fields remain partially unrecorded."
        : "Initial documentation recorded, but investor evidence review has not been confirmed.";
    } else {
      coverageState = "NOT_DOCUMENTED";
      whyStatusAssigned = "No evidence review or investor findings have been documented for this area.";
    }

    // Determine Primary Gap
    let primaryGap: GapClassification = gapClassifications[0];
    if (gapClassifications.some((g) => g.code === "EVIDENCE_GAP")) {
      primaryGap = GAP_CLASSIFICATIONS.EVIDENCE_GAP;
    } else if (gapClassifications.some((g) => g.code === "OPEN_INVESTIGATION")) {
      primaryGap = GAP_CLASSIFICATIONS.OPEN_INVESTIGATION;
    } else if (gapClassifications.some((g) => g.code === "RECORDED_UNCERTAINTY")) {
      primaryGap = GAP_CLASSIFICATIONS.RECORDED_UNCERTAINTY;
    } else if (gapClassifications.some((g) => g.code === "EVIDENCE_TRACEABILITY_LIMITATION")) {
      primaryGap = GAP_CLASSIFICATIONS.EVIDENCE_TRACEABILITY_LIMITATION;
    } else if (gapClassifications.some((g) => g.code === "DOCUMENTATION_GAP")) {
      primaryGap = GAP_CLASSIFICATIONS.DOCUMENTATION_GAP;
    }

    // Resolve Suggested Next Action
    let suggestedNextAction: string;
    let nextActionType: CoverageNextActionType;

    if (primaryGap.code === "OPEN_INVESTIGATION" && activeIssue) {
      suggestedNextAction = activeIssue.nextAction && activeIssue.nextAction.trim().length > 0
        ? `Review active investigation: "${activeIssue.title}" — Planned: ${activeIssue.nextAction}`
        : `Review active inquiry "${activeIssue.title}" in Section ${config.briefSection}`;
      nextActionType = "VIEW_ISSUE";
    } else if (primaryGap.code === "EVIDENCE_TRACEABILITY_LIMITATION") {
      suggestedNextAction = `Inspect Section ${config.briefSection} (${config.shortLabel}) to confirm re-inspection against active run #${currentRunId || "latest"}`;
      nextActionType = "REASSESS_EVIDENCE";
    } else if (primaryGap.code === "EVIDENCE_GAP") {
      suggestedNextAction = `Verify data availability in Section ${config.briefSection} or record missing disclosure note`;
      nextActionType = "RECORD_UNAVAILABLE";
    } else if (primaryGap.code === "RECORDED_UNCERTAINTY") {
      suggestedNextAction = `Inspect Section ${config.briefSection} to verify whether subsequent market disclosures resolve residual uncertainty`;
      nextActionType = "NAVIGATE_SECTION";
    } else if (primaryGap.code === "DOCUMENTATION_GAP") {
      suggestedNextAction = `Inspect Section ${config.briefSection} (${config.shortLabel}) in Research Brief and document findings`;
      nextActionType = "NAVIGATE_SECTION";
    } else {
      suggestedNextAction = `Area examined; confirm Section ${config.briefSection} aligns with working thesis`;
      nextActionType = "NAVIGATE_SECTION";
    }

    return {
      key: config.key,
      dimensionId: config.key,
      title: config.title,
      shortLabel: config.shortLabel,
      briefSection: config.briefSection,
      description: config.description,
      evidenceSource: config.evidenceSource,
      coverageState,
      state: coverageState,
      coverageStateMeta: RESEARCH_COVERAGE_STATES[coverageState],
      gapClassifications,
      primaryGap,
      whyStatusAssigned,
      explanation: whyStatusAssigned,
      suggestedNextAction,
      nextActionType,
      nextActionRecommendation: {
        actionType: nextActionType,
        actionExplanation: suggestedNextAction,
      },
      hasActionableGap: coverageState !== "DOCUMENTED",
      checklistKey: config.key,
      associatedIssues,
      activeIssue,
      hasResidualUncertainty,
      residualUncertaintyNotes,
      checklistStatus,
      isReviewed,
      isReassessmentNeeded,
      isApplicable,
    };
  });

  // Calculate coverage summary statistics
  const totalDimensions = dimensions.length; // 8
  const documentedCount = dimensions.filter((d) => d.coverageState === "DOCUMENTED").length;
  const actionableGapCount = totalDimensions - documentedCount;
  const hasActionableGaps = actionableGapCount > 0;
  const incompleteCount = dimensions.filter((d) => d.coverageState === "INCOMPLETE").length;
  const notDocumentedCount = dimensions.filter((d) => d.coverageState === "NOT_DOCUMENTED").length;
  const evidenceUnavailableCount = dimensions.filter((d) => d.coverageState === "EVIDENCE_UNAVAILABLE").length;
  const investigationPendingCount = dimensions.filter((d) => d.coverageState === "INVESTIGATION_PENDING").length;
  const uncertainCount = dimensions.filter((d) => d.coverageState === "UNCERTAIN").length;

  // Gap category counts
  const documentationGapCount = dimensions.filter((d) => d.gapClassifications.some((g) => g.code === "DOCUMENTATION_GAP")).length;
  const evidenceGapCount = dimensions.filter((d) => d.gapClassifications.some((g) => g.code === "EVIDENCE_GAP")).length;
  const openInvestigationCount = dimensions.filter((d) => d.gapClassifications.some((g) => g.code === "OPEN_INVESTIGATION")).length;
  const traceabilityLimitationCount = dimensions.filter((d) => d.gapClassifications.some((g) => g.code === "EVIDENCE_TRACEABILITY_LIMITATION")).length;
  const recordedUncertaintyCount = dimensions.filter((d) => d.gapClassifications.some((g) => g.code === "RECORDED_UNCERTAINTY")).length;
  const noGapCount = dimensions.filter((d) => d.primaryGap.code === "NO_IDENTIFIED_GAP").length;

  // Prioritize gaps: Actionable issues & data limits first, then unexamined areas, then documented
  const gapPriorityWeight = (d: CoverageDimensionItem): number => {
    switch (d.primaryGap.code) {
      case "OPEN_INVESTIGATION":
        return 1;
      case "EVIDENCE_TRACEABILITY_LIMITATION":
        return 2;
      case "EVIDENCE_GAP":
        return 3;
      case "RECORDED_UNCERTAINTY":
        return 4;
      case "DOCUMENTATION_GAP":
        return d.coverageState === "NOT_DOCUMENTED" ? 5 : 6;
      case "NO_IDENTIFIED_GAP":
        return 7;
      default:
        return 8;
    }
  };

  const prioritizedGaps = [...dimensions].sort((a, b) => gapPriorityWeight(a) - gapPriorityWeight(b));

  const descriptiveSummary =
    documentedCount === totalDimensions
      ? "Comprehensive research coverage: all 8 diligence dimensions documented and examined."
      : `${documentedCount} of ${totalDimensions} dimensions documented; ${investigationPendingCount} pending inquiry, ${uncertainCount} with recorded uncertainty, and ${documentationGapCount} documentation gap(s).`;

  return {
    schemeId: fund.schemeId,
    schemeOptionId: fund.schemeOptionId,
    schemeName: fund.schemeName,
    schemeCode: fund.schemeCode,
    planType: fund.planType || "DIRECT",
    optionType: fund.optionType || "GROWTH",
    amfiCode: fund.amfiCode || null,
    isin: fund.isin || null,
    totalDimensions,
    documentedCount,
    actionableGapCount,
    hasActionableGaps,
    incompleteCount,
    notDocumentedCount,
    evidenceUnavailableCount,
    investigationPendingCount,
    uncertainCount,
    documentationGapCount,
    evidenceGapCount,
    openInvestigationCount,
    traceabilityLimitationCount,
    recordedUncertaintyCount,
    noGapCount,
    dimensions,
    prioritizedGaps,
    descriptiveSummary,
  };
}

/**
 * Deterministically compares two fund coverage overview items for explainable workflow ordering.
 *
 * Rules:
 * 1. Priority tier ascending (Tier 1 Overdue before Tier 2 Reopened before Tier 3 Drift...)
 * 2. Actionable gaps count descending (more gaps before fewer)
 * 3. Open investigations count descending
 * 4. Scheme name alphabetical
 * 5. Exact share class identity tie-breaker
 */
export function compareCoverageOverviewItems(
  a: FundCoverageOverviewItem,
  b: FundCoverageOverviewItem
): number {
  if (a.priorityTier !== b.priorityTier) {
    return a.priorityTier - b.priorityTier;
  }
  if (a.actionableGapCount !== b.actionableGapCount) {
    return b.actionableGapCount - a.actionableGapCount;
  }
  if (a.openInvestigationCount !== b.openInvestigationCount) {
    return b.openInvestigationCount - a.openInvestigationCount;
  }
  const nameComp = a.schemeName.localeCompare(b.schemeName);
  if (nameComp !== 0) return nameComp;
  return (a.schemeOptionId || a.schemeId) - (b.schemeOptionId || b.schemeId);
}

/**
 * Evaluates research coverage and workflow prioritization across a collection of saved mutual fund options.
 *
 * Epistemic Rules:
 * 1. Prioritization reflects workflow due-diligence urgency, NEVER financial quality, risk score, or investment rating.
 * 2. Missing documentation is distinguished from missing evidence baselines.
 * 3. Exactly identifies share class using (schemeId, schemeOptionId) without conflating Direct/Regular or Growth/IDCW.
 * 4. Preserves multiple distinct workflow reasons for attention without counting the same issue twice in totals.
 */
export function evaluateCrossFundCoverage(
  funds: WatchlistResponse[],
  issues: ResearchFollowUpIssue[],
  checklistsMap?: Record<string, InvestigationReviewChecklistRecord | null>,
  evidenceSummariesMap?: Record<string, EvidenceChangeSummary | null>,
  portfolioSummary?: PortfolioSummaryDto | null,
  portfolioLoading?: boolean
): {
  items: FundCoverageOverviewItem[];
  summary: CrossFundCoverageSummary;
} {
  if (!funds || funds.length === 0) {
    return {
      items: [],
      summary: {
        totalFundsAssessed: 0,
        fundsWithActionableGaps: 0,
        fundsWithOpenInvestigations: 0,
        fundsWithMissingEvidence: 0,
        fundsWithResidualUncertainty: 0,
        fundsWithNoIdentifiedGaps: 0,
        totalActionableGapsAcrossFunds: 0,
        totalOpenInvestigationsAcrossFunds: 0,
      },
    };
  }

  const items: FundCoverageOverviewItem[] = funds.map((fund) => {
    const key = getWatchlistItemKey(fund.schemeId, fund.schemeOptionId);
    const checklist = checklistsMap?.[key] || null;
    const evSummary = evidenceSummariesMap?.[key] || null;
    const portContext = resolvePortfolioContext(fund, portfolioSummary || null, portfolioLoading);

    // Evaluate standard 8-dimension coverage report
    const report = evaluateFundResearchCoverage(
      fund,
      issues,
      checklist,
      evSummary,
      null, // Profile evaluated on-demand in brief modal
      portContext
    );

    // Exact fund option issue associations
    const fundIssues = issues.filter((i) => {
      if (fund.schemeOptionId !== undefined && fund.schemeOptionId !== null) {
        return i.schemeOptionId === fund.schemeOptionId;
      }
      return i.schemeId === fund.schemeId;
    });

    const activeIssues = fundIssues.filter(
      (i) => i.status === "OPEN" || i.status === "IN_PROGRESS" || i.status === "WAITING"
    );

    const overdueIssues = activeIssues.filter(
      (i) => Boolean(i.targetDate && isTargetDatePassed(i.targetDate))
    );

    const reopenedIssues = activeIssues.filter(
      (i) => Boolean(i.reopenedAt || i.priorResolutionNote)
    );

    const driftIssues = activeIssues.filter((i) => {
      const prov = evaluateFindingEvidenceTraceability(i);
      return prov.status === "HISTORICAL_EVIDENCE_CHANGED" || prov.status === "HISTORICAL_RUN_SUPERSEDED";
    });

    const uncertaintyIssues = fundIssues.filter(
      (i) =>
        Boolean(i.hasResidualUncertainty) ||
        Boolean(i.residualUncertainty && i.residualUncertainty.trim().length > 0) ||
        i.outcomeType === "PARTIALLY_ANSWERED" ||
        i.outcomeType === "DATA_UNAVAILABLE"
    );

    // Deterministic workflow prioritization
    let priorityTier = 7;
    let priorityTierLabel = "Documented (No Gaps Recorded)";
    let priorityBadgeClass = "bg-emerald-500/15 text-emerald-300 border-emerald-500/40";
    let primaryWorkflowReason = "All 8 diligence dimensions have recorded examination (does not imply fund safety or suitability).";
    let suggestedNextStep = "Research records documented; review in Brief to inspect evidentiary audit trail.";
    let nextActionType: CoverageNextActionType = "NAVIGATE_SECTION";
    const allWorkflowReasons: string[] = [];

    // 1. Explicitly overdue unresolved investigations
    if (overdueIssues.length > 0) {
      priorityTier = 1;
      priorityTierLabel = "Overdue Investigation";
      priorityBadgeClass = "bg-rose-500/20 text-rose-300 border-rose-500/40";
      primaryWorkflowReason = `Overdue inquiry "${overdueIssues[0].title}" (target date ${overdueIssues[0].targetDate || "passed"}).`;
      suggestedNextStep = `Resume overdue inquiry in Section ${overdueIssues[0].briefSection || "B"} to record findings or adjust target date.`;
      nextActionType = "VIEW_ISSUE";
      allWorkflowReasons.push(`Overdue inquiry: "${overdueIssues[0].title}"`);
    }
    // 2. Reopened investigations with preserved prior outcomes
    else if (reopenedIssues.length > 0) {
      priorityTier = 2;
      priorityTierLabel = "Reopened Investigation";
      priorityBadgeClass = "bg-indigo-500/20 text-indigo-300 border-indigo-500/40";
      primaryWorkflowReason = `Reopened inquiry "${reopenedIssues[0].title}" requires renewed due diligence against current data.`;
      suggestedNextStep = `Review prior outcome and conduct fresh inspection in Section ${reopenedIssues[0].briefSection || "B"}.`;
      nextActionType = "VIEW_ISSUE";
      allWorkflowReasons.push(`Reopened inquiry: "${reopenedIssues[0].title}"`);
    }
    // 3. Documented evidence drift or superseded calculation runs affecting unresolved issues
    else if (driftIssues.length > 0 || report.traceabilityLimitationCount > 0) {
      priorityTier = 3;
      priorityTierLabel = "Evidence Drift";
      priorityBadgeClass = "bg-amber-500/20 text-amber-300 border-amber-500/40";
      const driftIssue = driftIssues[0];
      primaryWorkflowReason = driftIssue
        ? `Underlying evidence cutoff or run has changed since "${driftIssue.title}" was recorded.`
        : `Diligence evidence has drifted; re-inspection against active calculation run required.`;
      suggestedNextStep = `Audit historical changes in Section F to verify whether findings still hold.`;
      nextActionType = "REASSESS_EVIDENCE";
      allWorkflowReasons.push("Evidence baseline changed since review");
    }
    // 4. Multiple or active unresolved diligence questions across relevant dimensions
    else if (activeIssues.length >= 2) {
      priorityTier = 4;
      priorityTierLabel = `Multiple Inquiries (${activeIssues.length})`;
      priorityBadgeClass = "bg-purple-500/20 text-purple-300 border-purple-500/40";
      primaryWorkflowReason = `${activeIssues.length} unresolved diligence inquiries logged across research dimensions.`;
      suggestedNextStep = `Resolve active inquiry: "${activeIssues[0].title}" (${activeIssues[0].status}).`;
      nextActionType = "VIEW_ISSUE";
      allWorkflowReasons.push(`${activeIssues.length} unresolved inquiries`);
    } else if (activeIssues.length === 1) {
      priorityTier = 4;
      priorityTierLabel = "Active Inquiry";
      priorityBadgeClass = "bg-purple-500/20 text-purple-300 border-purple-500/40";
      primaryWorkflowReason = `Unresolved inquiry logged: "${activeIssues[0].title}" (${activeIssues[0].status}).`;
      suggestedNextStep = activeIssues[0].nextAction || `Inspect Section ${activeIssues[0].briefSection || "B"} to address question.`;
      nextActionType = "VIEW_ISSUE";
      allWorkflowReasons.push(`Active inquiry: "${activeIssues[0].title}"`);
    }
    // 5. Missing evidence or provenance that prevents meaningful assessment
    else if (report.evidenceUnavailableCount > 0) {
      priorityTier = 5;
      priorityTierLabel = "Evidence Unavailable";
      priorityBadgeClass = "bg-sky-500/20 text-sky-300 border-sky-500/40";
      primaryWorkflowReason = `${report.evidenceUnavailableCount} dimension(s) lack required source data or baseline calculations.`;
      suggestedNextStep = `Record data limitation in review checklist or inspect available fund disclosures.`;
      nextActionType = "RECORD_UNAVAILABLE";
      allWorkflowReasons.push(`${report.evidenceUnavailableCount} dimension(s) missing underlying evidence`);
    }
    // 6. Unexamined research dimensions without an existing investigation
    else if (report.actionableGapCount > 0) {
      priorityTier = 6;
      priorityTierLabel = "Unexamined Gaps";
      priorityBadgeClass = "bg-surface-raised text-text-secondary border-border";
      const mostUrgent = report.prioritizedGaps[0];
      primaryWorkflowReason = mostUrgent
        ? `Diligence gap in ${mostUrgent.title}: ${mostUrgent.whyStatusAssigned}`
        : `${report.actionableGapCount} research dimension(s) unexamined.`;
      suggestedNextStep = mostUrgent
        ? mostUrgent.suggestedNextAction
        : `Open Research Brief to complete initial investigation review.`;
      nextActionType = mostUrgent ? mostUrgent.nextActionType : "NAVIGATE_SECTION";
      allWorkflowReasons.push(`${report.actionableGapCount} unexamined research dimension(s)`);
    } else {
      allWorkflowReasons.push("Documented across all 8 dimensions");
    }

    return {
      fund,
      report,
      key,
      schemeId: fund.schemeId,
      schemeOptionId: fund.schemeOptionId,
      schemeName: fund.schemeName,
      schemeCode: fund.schemeCode,
      planType: fund.planType || "DIRECT",
      optionType: fund.optionType || "GROWTH",
      amfiCode: fund.amfiCode || null,
      isin: fund.isin || null,
      documentedCount: report.documentedCount,
      totalDimensions: report.totalDimensions,
      actionableGapCount: report.actionableGapCount,
      documentationGapCount: report.documentationGapCount,
      openInvestigationCount: report.openInvestigationCount,
      evidenceUnavailableCount: report.evidenceUnavailableCount,
      traceabilityLimitationCount: report.traceabilityLimitationCount,
      recordedUncertaintyCount: report.recordedUncertaintyCount,
      hasActionableGaps: report.hasActionableGaps,
      associatedIssues: fundIssues,
      activeIssues,
      overdueIssues,
      reopenedIssues,
      driftIssues,
      uncertaintyIssues,
      priorityTier,
      priorityTierLabel,
      priorityBadgeClass,
      primaryWorkflowReason,
      allWorkflowReasons,
      mostUrgentGap: report.prioritizedGaps[0] || null,
      suggestedNextStep,
      nextActionType,
    };
  });

  // Sort deterministically
  items.sort(compareCoverageOverviewItems);

  // Compute CrossFundCoverageSummary
  const summary: CrossFundCoverageSummary = {
    totalFundsAssessed: items.length,
    fundsWithActionableGaps: items.filter((i) => i.hasActionableGaps).length,
    fundsWithOpenInvestigations: items.filter((i) => i.openInvestigationCount > 0).length,
    fundsWithMissingEvidence: items.filter(
      (i) => i.evidenceUnavailableCount > 0 || i.traceabilityLimitationCount > 0 || i.driftIssues.length > 0
    ).length,
    fundsWithResidualUncertainty: items.filter(
      (i) => i.recordedUncertaintyCount > 0 || i.uncertaintyIssues.length > 0
    ).length,
    fundsWithNoIdentifiedGaps: items.filter((i) => !i.hasActionableGaps).length,
    totalActionableGapsAcrossFunds: items.reduce((sum, i) => sum + i.actionableGapCount, 0),
    totalOpenInvestigationsAcrossFunds: items.reduce((sum, i) => sum + i.openInvestigationCount, 0),
  };

  return { items, summary };
}

/**
 * Filters cross-fund coverage overview items according to user filter tabs and optional search query.
 */
export function filterCrossFundCoverageItems(
  items: FundCoverageOverviewItem[],
  filter: CrossFundCoverageFilter,
  searchQuery?: string
): FundCoverageOverviewItem[] {
  let filtered = items;

  // Filter tab condition
  if (filter === "ACTIONABLE_GAPS") {
    filtered = filtered.filter((i) => i.hasActionableGaps);
  } else if (filter === "OPEN_INVESTIGATIONS") {
    filtered = filtered.filter((i) => i.openInvestigationCount > 0);
  } else if (filter === "DATA_OR_PROVENANCE_LIMITS") {
    filtered = filtered.filter(
      (i) => i.evidenceUnavailableCount > 0 || i.traceabilityLimitationCount > 0 || i.driftIssues.length > 0
    );
  } else if (filter === "RECORDED_UNCERTAINTY") {
    filtered = filtered.filter(
      (i) => i.recordedUncertaintyCount > 0 || i.uncertaintyIssues.length > 0
    );
  } else if (filter === "NO_IDENTIFIED_GAPS") {
    filtered = filtered.filter((i) => !i.hasActionableGaps);
  }

  // Optional search query
  if (searchQuery && searchQuery.trim().length > 0) {
    const q = searchQuery.trim().toLowerCase();
    filtered = filtered.filter((i) => {
      return (
        i.schemeName.toLowerCase().includes(q) ||
        i.schemeCode.toLowerCase().includes(q) ||
        (i.amfiCode && i.amfiCode.toLowerCase().includes(q)) ||
        (i.isin && i.isin.toLowerCase().includes(q)) ||
        (i.planType && i.planType.toLowerCase().includes(q)) ||
        (i.optionType && i.optionType.toLowerCase().includes(q)) ||
        i.primaryWorkflowReason.toLowerCase().includes(q) ||
        i.suggestedNextStep.toLowerCase().includes(q) ||
        i.associatedIssues.some((iss) => iss.title.toLowerCase().includes(q))
      );
    });
  }

  return filtered;
}
