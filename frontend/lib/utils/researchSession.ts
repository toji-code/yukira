/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F12: Research Workspace Context Continuity and Resumable Investigation Sessions
 *
 * Implements deterministic serialization, validation, and browser-local persistence
 * for recent investor research context and investigation sessions.
 *
 * CRITICAL EPISTEMIC INTEGRITY RULES:
 * 1. Resumable sessions store navigation context and recent working inquiry, NOT financial truth or authoritative research records.
 * 2. Does NOT copy metrics, alter evidence baselines, infer completed reviews, or substitute scheme options.
 * 3. Exact (schemeId, schemeOptionId) identity is preserved without conflating Direct/Regular or Growth/IDCW variants.
 * 4. Separate from F11 Saved Views: Saved views are reusable workspace configurations; session context is recent investigation memory.
 */

import { FilterTab, SortOption, isValidFilterTab, isValidSortOption } from "./savedResearchViews";
import { formatDateTime } from "./formatters";
import { ReviewItemKey } from "./researchReviewChecklist";
import { GapClassificationCode, ResearchCoverageState } from "./researchCoverage";
import { FollowUpStatus, ResearchFollowUpIssue } from "./researchFollowUp";

export type ResearchSessionDestination = "BRIEF" | "NOTE" | "PROFILE" | "COMPARE" | "WORKSPACE";

export type InvestigationHandoffLifecycleStage =
  | "UNINVESTIGATED_GAP"
  | "ACTIVE_UNRESOLVED"
  | "WAITING_DEPENDENCY"
  | "EVIDENCE_DRIFTED"
  | "RESOLVED_WITH_UNCERTAINTY"
  | "RESOLVED_OR_DISMISSED"
  | "HISTORICAL_ORPHANED";

export interface InvestigationHandoffContext {
  // Exact instrument identity
  schemeId: number;
  schemeOptionId?: number | null;
  schemeName: string;
  schemeCode?: string;
  planType?: string;
  optionType?: string;
  amfiCode?: string | null;
  isin?: string | null;

  // Diligence dimension & section target
  briefSection?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H";
  dimensionKey?: ReviewItemKey;
  dimensionTitle?: string;

  // Motivating gap & coverage state
  gapClassification?: GapClassificationCode;
  coverageState?: ResearchCoverageState;

  // Associated follow-up inquiry if an issue exists
  followUpIssueId?: string | null;
  followUpIssueTitle?: string | null;
  followUpStatus?: FollowUpStatus | null;

  // Provenance & evidence baseline
  evidenceRunId?: number | null;
  evidenceAsOfDate?: string | null;
  evidenceStatus?: string | null;
  isRunSuperseded?: boolean;
  isCutoffDrifted?: boolean;

  // Originating workspace/action
  originatingAction?:
    | "COVERAGE_OVERVIEW_TRIAGE"
    | "COVERAGE_DIMENSION_DEEP_LINK"
    | "LOG_INQUIRY_FROM_COVERAGE"
    | "INVESTIGATION_QUEUE_RESUME"
    | "CHECKLIST_SECTION_REVIEW"
    | "WORKSPACE_NAVIGATION"
    | "REASSESSMENT_START"
    | "EVIDENCE_HISTORY_REVIEW";

  // Reassessment & Evidence Context (Phase F28)
  reassessmentReason?: string | null;
  priorOutcomeType?: string | null;
  priorResolutionNote?: string | null;
  priorEvidenceRunId?: number | null;
  priorEvidenceAsOfDate?: string | null;

  // Dependency & Blocker Context (Phase F29)
  dependencyId?: string | null;
  dependencyDescription?: string | null;
  dependencyStatus?: string | null;
  hasOutstandingBlockers?: boolean;
  addressedDependenciesCount?: number;

  // Continuity lifecycle & explainable rationale (deterministic per Section 2.D)
  lifecycleStage: InvestigationHandoffLifecycleStage;
  whyAttentionNeeded: string;
  suggestedNextStep: string;

  // Timestamps
  createdAt: string;
  lastResumedAt?: string | null;
}

export interface ResumableResearchSession {
  id: string; // Stable session identifier
  destination: ResearchSessionDestination;
  destinationLabel: string; // e.g. "Research Brief (Section F: History & Changes)"
  // Exact fund & share-class identity
  schemeId?: number;
  schemeOptionId?: number | null;
  schemeCode?: string;
  schemeName?: string;
  category?: string | null;
  amcName?: string | null;
  planType?: string | null;
  optionType?: string | null;
  amfiCode?: string | null;
  isin?: string | null;
  // Specific research section if Brief
  briefSection?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H";
  // Associated follow-up inquiry if session was triggered by resuming an issue
  followUpIssueId?: string | null;
  followUpIssueTitle?: string | null;
  // Associated comparison candidate keys if compare workflow
  comparisonCandidateKeys?: string[];
  // Phase F27: Preserved Investigation Handoff Context
  handoffContext?: InvestigationHandoffContext | null;
  // Active workspace filter configuration when action occurred
  searchQuery?: string;
  filterTab?: FilterTab;
  selectedAmc?: string;
  sortBy?: SortOption;
  savedViewId?: string | null;
  savedViewName?: string | null;
  // Timestamp of the interaction
  updatedAt: string; // ISO string
}

const SESSION_STORAGE_PREFIX = "yukira_recent_session_";
const HANDOFF_STORAGE_PREFIX = "yukira_investigation_handoff_";

/**
 * Derives a namespaced local-storage key to partition session state by user identity.
 * Prevents investigation context leakage between accounts on shared terminals.
 */
export function getResearchSessionStorageKey(userId?: string | null): string {
  if (userId && typeof userId === "string" && userId.trim().length > 0) {
    return `${SESSION_STORAGE_PREFIX}${encodeURIComponent(userId.trim())}`;
  }
  return `${SESSION_STORAGE_PREFIX}guest`;
}

export function getInvestigationHandoffStorageKey(userId?: string | null): string {
  if (userId && typeof userId === "string" && userId.trim().length > 0) {
    return `${HANDOFF_STORAGE_PREFIX}${encodeURIComponent(userId.trim())}`;
  }
  return `${HANDOFF_STORAGE_PREFIX}guest`;
}

/**
 * Deterministically derives the continuity lifecycle stage of an investigation handoff.
 */
export function deriveHandoffLifecycleStage(params: {
  isWorkspaceItemMissing?: boolean;
  issue?: ResearchFollowUpIssue | null;
  gapClassification?: GapClassificationCode;
  isRunSuperseded?: boolean;
  isCutoffDrifted?: boolean;
}): InvestigationHandoffLifecycleStage {
  if (params.isWorkspaceItemMissing) {
    return "HISTORICAL_ORPHANED";
  }
  if (params.issue) {
    const deps = params.issue.dependencies || [];
    const awaitingBlockers = deps.filter((d) => d.status === "AWAITING");
    const addressedBlockers = deps.filter((d) => d.status === "ADDRESSED");

    if (params.issue.status === "WAITING") {
      // If all recorded dependencies have been addressed, mark as ACTIVE_UNRESOLVED (ready to resume/review)
      if (deps.length > 0 && awaitingBlockers.length === 0 && addressedBlockers.length > 0) {
        return "ACTIVE_UNRESOLVED";
      }
      return "WAITING_DEPENDENCY";
    }
    if (params.issue.status === "OPEN" || params.issue.status === "IN_PROGRESS") {
      if (params.isRunSuperseded || params.isCutoffDrifted) {
        return "EVIDENCE_DRIFTED";
      }
      return "ACTIVE_UNRESOLVED";
    }
    if (params.issue.status === "RESOLVED" || params.issue.status === "DISMISSED") {
      if (
        params.issue.hasResidualUncertainty ||
        (params.issue.residualUncertainty && params.issue.residualUncertainty.trim().length > 0) ||
        params.issue.outcomeType === "PARTIALLY_ANSWERED" ||
        params.issue.outcomeType === "DATA_UNAVAILABLE"
      ) {
        return "RESOLVED_WITH_UNCERTAINTY";
      }
      return "RESOLVED_OR_DISMISSED";
    }
  }
  if (
    params.gapClassification === "EVIDENCE_TRACEABILITY_LIMITATION" ||
    params.isRunSuperseded ||
    params.isCutoffDrifted
  ) {
    return "EVIDENCE_DRIFTED";
  }
  return "UNINVESTIGATED_GAP";
}

/**
 * Deterministically generates explainable next-action rationale conforming strictly to Section 2.D.
 */
export function deriveHandoffExplainableRationale(stage: InvestigationHandoffLifecycleStage): {
  whyAttentionNeeded: string;
  suggestedNextStep: string;
  badgeClass: string;
  badgeLabel: string;
} {
  switch (stage) {
    case "HISTORICAL_ORPHANED":
      return {
        whyAttentionNeeded:
          "The original fund option is no longer in the active watchlist; its historical investigation remains available.",
        suggestedNextStep:
          "Review preserved diligence notes and inquiry outcomes without share-class substitution.",
        badgeClass: "bg-amber-500/20 text-amber-300 border-amber-500/40",
        badgeLabel: "HISTORICAL (NOT IN WATCHLIST)",
      };
    case "ACTIVE_UNRESOLVED":
      return {
        whyAttentionNeeded: "The linked inquiry remains open.",
        suggestedNextStep:
          "Resume active inquiry to evaluate empirical observations or update planned next actions.",
        badgeClass: "bg-purple-500/20 text-purple-300 border-purple-500/40",
        badgeLabel: "ACTIVE UNRESOLVED",
      };
    case "WAITING_DEPENDENCY":
      return {
        whyAttentionNeeded: "The investigation is waiting on an external prerequisite or missing disclosure.",
        suggestedNextStep:
          "Check whether the prerequisite has been received, record blocker resolution, or resume investigation.",
        badgeClass: "bg-sky-500/20 text-sky-300 border-sky-500/40",
        badgeLabel: "WAITING ON DEPENDENCY",
      };
    case "EVIDENCE_DRIFTED":
      return {
        whyAttentionNeeded: "The evidence baseline has changed since the previous review.",
        suggestedNextStep:
          "Audit Section F historical changes and verify if newer calculation runs affect findings.",
        badgeClass: "bg-orange-500/20 text-orange-300 border-orange-500/40",
        badgeLabel: "EVIDENCE DRIFTED",
      };
    case "RESOLVED_WITH_UNCERTAINTY":
      return {
        whyAttentionNeeded: "The prior outcome records residual uncertainty.",
        suggestedNextStep:
          "Inspect documented uncertainty notes or reopen inquiry if new market evidence is available.",
        badgeClass: "bg-yellow-500/20 text-yellow-300 border-yellow-500/40",
        badgeLabel: "RESIDUAL UNCERTAINTY",
      };
    case "RESOLVED_OR_DISMISSED":
      return {
        whyAttentionNeeded: "This investigation was previously resolved or dismissed.",
        suggestedNextStep:
          "Inspect recorded resolution notes or reopen if the investment thesis requires renewal.",
        badgeClass: "bg-emerald-500/20 text-emerald-300 border-emerald-500/40",
        badgeLabel: "RECORDED DISPOSITION",
      };
    case "UNINVESTIGATED_GAP":
    default:
      return {
        whyAttentionNeeded: "This coverage dimension has not been documented.",
        suggestedNextStep:
          "Open diligence dimension in Fund Research Brief to examine evidence and record findings.",
        badgeClass: "bg-rose-500/20 text-rose-300 border-rose-500/40",
        badgeLabel: "UNINVESTIGATED GAP",
      };
  }
}

/**
 * Creates an InvestigationHandoffContext deterministically.
 */
export function createInvestigationHandoff(params: {
  fund: {
    schemeId: number;
    schemeOptionId?: number | null;
    schemeName: string;
    schemeCode?: string;
    planType?: string;
    optionType?: string;
    amfiCode?: string | null;
    isin?: string | null;
  };
  briefSection?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H";
  dimensionKey?: ReviewItemKey;
  dimensionTitle?: string;
  gapClassification?: GapClassificationCode;
  coverageState?: ResearchCoverageState;
  issue?: ResearchFollowUpIssue | null;
  evidenceContext?: {
    calculationRunId?: number | null;
    asOfDate?: string | null;
    evidenceStatus?: string | null;
  };
  originatingAction?:
    | "COVERAGE_OVERVIEW_TRIAGE"
    | "COVERAGE_DIMENSION_DEEP_LINK"
    | "LOG_INQUIRY_FROM_COVERAGE"
    | "INVESTIGATION_QUEUE_RESUME"
    | "CHECKLIST_SECTION_REVIEW"
    | "WORKSPACE_NAVIGATION"
    | "REASSESSMENT_START"
    | "EVIDENCE_HISTORY_REVIEW";
  whyAttentionNeeded?: string;
  suggestedNextStep?: string;
  isWorkspaceItemMissing?: boolean;
  reassessmentReason?: string | null;
  priorOutcomeType?: string | null;
  priorResolutionNote?: string | null;
  priorEvidenceRunId?: number | null;
  priorEvidenceAsOfDate?: string | null;
}): InvestigationHandoffContext {
  const isRunSuperseded = Boolean(
    params.issue?.priorEvidenceRunId &&
      params.issue?.evidenceRunId &&
      params.issue.priorEvidenceRunId !== params.issue.evidenceRunId
  );
  const isCutoffDrifted = Boolean(
    params.issue?.priorEvidenceAsOfDate &&
      params.issue?.evidenceAsOfDate &&
      params.issue.priorEvidenceAsOfDate !== params.issue.evidenceAsOfDate
  );

  const stage = deriveHandoffLifecycleStage({
    isWorkspaceItemMissing: params.isWorkspaceItemMissing,
    issue: params.issue,
    gapClassification: params.gapClassification,
    isRunSuperseded,
    isCutoffDrifted,
  });

  const rationale = deriveHandoffExplainableRationale(stage);

  return {
    schemeId: params.fund.schemeId,
    schemeOptionId: params.fund.schemeOptionId,
    schemeName: params.fund.schemeName,
    schemeCode: params.fund.schemeCode,
    planType: params.fund.planType || "DIRECT",
    optionType: params.fund.optionType || "GROWTH",
    amfiCode: params.fund.amfiCode || null,
    isin: params.fund.isin || null,
    briefSection: params.briefSection || "coverage",
    dimensionKey: params.dimensionKey,
    dimensionTitle: params.dimensionTitle || (params.briefSection ? getBriefSectionTitle(params.briefSection) : undefined),
    gapClassification: params.gapClassification || (params.issue ? "OPEN_INVESTIGATION" : "DOCUMENTATION_GAP"),
    coverageState: params.coverageState || (params.issue ? "INVESTIGATION_PENDING" : "NOT_DOCUMENTED"),
    followUpIssueId: params.issue?.id || null,
    followUpIssueTitle: params.issue?.title || null,
    followUpStatus: params.issue?.status || null,
    evidenceRunId: params.issue?.evidenceRunId ?? params.evidenceContext?.calculationRunId ?? null,
    evidenceAsOfDate: params.issue?.evidenceAsOfDate ?? params.evidenceContext?.asOfDate ?? null,
    evidenceStatus: params.evidenceContext?.evidenceStatus ?? null,
    isRunSuperseded,
    isCutoffDrifted,
    originatingAction: params.originatingAction || "COVERAGE_OVERVIEW_TRIAGE",
    reassessmentReason: params.reassessmentReason || (params.originatingAction === "REASSESSMENT_START" ? "Evidence baseline changed since prior review" : null),
    priorOutcomeType: params.priorOutcomeType || params.issue?.priorOutcomeType || (params.issue?.status === "RESOLVED" || params.issue?.status === "DISMISSED" ? params.issue?.outcomeType : null) || null,
    priorResolutionNote: params.priorResolutionNote || params.issue?.priorResolutionNote || params.issue?.resolutionNote || null,
    priorEvidenceRunId: params.priorEvidenceRunId || params.issue?.priorEvidenceRunId || null,
    priorEvidenceAsOfDate: params.priorEvidenceAsOfDate || params.issue?.priorEvidenceAsOfDate || null,
    dependencyId: params.issue?.dependencies?.[0]?.id || null,
    dependencyDescription: params.issue?.dependencies?.[0]?.description || null,
    dependencyStatus: params.issue?.dependencies?.[0]?.status || null,
    hasOutstandingBlockers: params.issue?.dependencies?.some((d) => d.status === "AWAITING") || false,
    addressedDependenciesCount: params.issue?.dependencies?.filter((d) => d.status === "ADDRESSED").length || 0,
    lifecycleStage: stage,
    whyAttentionNeeded: params.whyAttentionNeeded || rationale.whyAttentionNeeded,
    suggestedNextStep: params.suggestedNextStep || rationale.suggestedNextStep,
    createdAt: new Date().toISOString(),
    lastResumedAt: null,
  };
}

/**
 * Validates and sanitizes a raw session record loaded from storage.
 * Defaults invalid values and ensures exact identifiers are preserved.
 */
export function sanitizeResearchSession(raw: unknown): ResumableResearchSession | null {
  if (!raw || typeof raw !== "object") return null;
  const item = raw as Record<string, unknown>;

  if (typeof item.id !== "string" || !item.id.trim()) return null;
  const validDestinations: ResearchSessionDestination[] = ["BRIEF", "NOTE", "PROFILE", "COMPARE", "WORKSPACE"];
  if (typeof item.destination !== "string" || !validDestinations.includes(item.destination as ResearchSessionDestination)) {
    return null;
  }

  const destination = item.destination as ResearchSessionDestination;
  const destinationLabel =
    typeof item.destinationLabel === "string" && item.destinationLabel.trim()
      ? item.destinationLabel.trim()
      : destination;

  const schemeId = typeof item.schemeId === "number" && !isNaN(item.schemeId) ? item.schemeId : undefined;
  const schemeOptionId =
    typeof item.schemeOptionId === "number" && !isNaN(item.schemeOptionId)
      ? item.schemeOptionId
      : item.schemeOptionId === null
      ? null
      : undefined;

  const validBriefSections = ["coverage", "checklist", "A", "B", "C", "D", "E", "F", "G", "H"];
  const briefSection =
    typeof item.briefSection === "string" && validBriefSections.includes(item.briefSection)
      ? (item.briefSection as "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H")
      : undefined;

  const followUpIssueId =
    typeof item.followUpIssueId === "string" && item.followUpIssueId.trim()
      ? item.followUpIssueId.trim()
      : item.followUpIssueId === null
      ? null
      : undefined;

  const followUpIssueTitle =
    typeof item.followUpIssueTitle === "string" && item.followUpIssueTitle.trim()
      ? item.followUpIssueTitle.trim()
      : item.followUpIssueTitle === null
      ? null
      : undefined;

  const filterTab: FilterTab | undefined = isValidFilterTab(item.filterTab) ? item.filterTab : undefined;
  const sortBy: SortOption | undefined = isValidSortOption(item.sortBy) ? item.sortBy : undefined;

  // Phase F27: Sanitize optional investigation handoff context
  let handoffContext: InvestigationHandoffContext | null = null;
  if (item.handoffContext && typeof item.handoffContext === "object") {
    const rawH = item.handoffContext as Record<string, unknown>;
    if (typeof rawH.schemeId === "number") {
      const hBriefSection =
        typeof rawH.briefSection === "string" && validBriefSections.includes(rawH.briefSection)
          ? (rawH.briefSection as "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H")
          : undefined;
      handoffContext = {
        schemeId: rawH.schemeId,
        schemeOptionId: typeof rawH.schemeOptionId === "number" ? rawH.schemeOptionId : null,
        schemeName: typeof rawH.schemeName === "string" ? rawH.schemeName : (item.schemeName as string) || "Fund",
        schemeCode: typeof rawH.schemeCode === "string" ? rawH.schemeCode : undefined,
        planType: typeof rawH.planType === "string" ? rawH.planType : undefined,
        optionType: typeof rawH.optionType === "string" ? rawH.optionType : undefined,
        amfiCode: typeof rawH.amfiCode === "string" ? rawH.amfiCode : null,
        isin: typeof rawH.isin === "string" ? rawH.isin : null,
        briefSection: hBriefSection,
        dimensionKey: typeof rawH.dimensionKey === "string" ? (rawH.dimensionKey as ReviewItemKey) : undefined,
        dimensionTitle: typeof rawH.dimensionTitle === "string" ? rawH.dimensionTitle : undefined,
        gapClassification: typeof rawH.gapClassification === "string" ? (rawH.gapClassification as GapClassificationCode) : undefined,
        coverageState: typeof rawH.coverageState === "string" ? (rawH.coverageState as ResearchCoverageState) : undefined,
        followUpIssueId: typeof rawH.followUpIssueId === "string" ? rawH.followUpIssueId : null,
        followUpIssueTitle: typeof rawH.followUpIssueTitle === "string" ? rawH.followUpIssueTitle : null,
        followUpStatus: typeof rawH.followUpStatus === "string" ? (rawH.followUpStatus as FollowUpStatus) : null,
        evidenceRunId: typeof rawH.evidenceRunId === "number" ? rawH.evidenceRunId : null,
        evidenceAsOfDate: typeof rawH.evidenceAsOfDate === "string" ? rawH.evidenceAsOfDate : null,
        evidenceStatus: typeof rawH.evidenceStatus === "string" ? rawH.evidenceStatus : null,
        isRunSuperseded: Boolean(rawH.isRunSuperseded),
        isCutoffDrifted: Boolean(rawH.isCutoffDrifted),
        originatingAction: typeof rawH.originatingAction === "string" ? (rawH.originatingAction as any) : undefined,
        lifecycleStage: typeof rawH.lifecycleStage === "string" ? (rawH.lifecycleStage as InvestigationHandoffLifecycleStage) : "UNINVESTIGATED_GAP",
        whyAttentionNeeded: typeof rawH.whyAttentionNeeded === "string" ? rawH.whyAttentionNeeded : "This coverage dimension has not been documented.",
        suggestedNextStep: typeof rawH.suggestedNextStep === "string" ? rawH.suggestedNextStep : "Open diligence dimension to examine evidence and record findings.",
        createdAt: typeof rawH.createdAt === "string" ? rawH.createdAt : new Date().toISOString(),
        lastResumedAt: typeof rawH.lastResumedAt === "string" ? rawH.lastResumedAt : null,
      };
    }
  }

  const updatedAt = typeof item.updatedAt === "string" ? item.updatedAt : new Date().toISOString();

  return {
    id: item.id.trim(),
    destination,
    destinationLabel,
    schemeId,
    schemeOptionId,
    schemeCode: typeof item.schemeCode === "string" ? item.schemeCode.trim() : undefined,
    schemeName: typeof item.schemeName === "string" ? item.schemeName.trim() : undefined,
    category: typeof item.category === "string" ? item.category.trim() : null,
    amcName: typeof item.amcName === "string" ? item.amcName.trim() : null,
    planType: typeof item.planType === "string" ? item.planType.trim() : null,
    optionType: typeof item.optionType === "string" ? item.optionType.trim() : null,
    amfiCode: typeof item.amfiCode === "string" ? item.amfiCode.trim() : null,
    isin: typeof item.isin === "string" ? item.isin.trim() : null,
    briefSection,
    followUpIssueId,
    followUpIssueTitle,
    comparisonCandidateKeys: Array.isArray(item.comparisonCandidateKeys)
      ? item.comparisonCandidateKeys.filter((k): k is string => typeof k === "string")
      : undefined,
    handoffContext,
    searchQuery: typeof item.searchQuery === "string" ? item.searchQuery : undefined,
    filterTab,
    selectedAmc: typeof item.selectedAmc === "string" ? item.selectedAmc : undefined,
    sortBy,
    savedViewId: typeof item.savedViewId === "string" ? item.savedViewId.trim() : null,
    savedViewName: typeof item.savedViewName === "string" ? item.savedViewName.trim() : null,
    updatedAt,
  };
}

/**
 * Loads the most recent research session from local browser storage.
 */
export function loadRecentResearchSession(userId?: string | null): ResumableResearchSession | null {
  if (typeof window === "undefined") return null;
  const key = getResearchSessionStorageKey(userId);

  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return sanitizeResearchSession(parsed);
  } catch (e) {
    console.error("Failed to load recent research session from storage", e);
    return null;
  }
}

/**
 * Persists a meaningful research session to browser local storage.
 */
export function saveRecentResearchSession(
  session: ResumableResearchSession,
  userId?: string | null
): boolean {
  if (typeof window === "undefined") return false;
  const key = getResearchSessionStorageKey(userId);

  try {
    window.localStorage.setItem(key, JSON.stringify(session));
    return true;
  } catch (e) {
    console.error("Failed to save recent research session to storage", e);
    return false;
  }
}

/**
 * Clears the stored research session from browser local storage.
 */
export function clearRecentResearchSession(userId?: string | null): void {
  if (typeof window === "undefined") return;
  const key = getResearchSessionStorageKey(userId);
  try {
    window.localStorage.removeItem(key);
  } catch (e) {
    console.error("Failed to clear research session from storage", e);
  }
}

/**
 * Formats a relative timestamp (e.g. "Just now", "4 mins ago", "2 hours ago")
 * falling back to the standard Indian local date/time string.
 */
export function formatSessionRelativeTime(isoStr: string): string {
  try {
    const d = new Date(isoStr);
    const now = new Date();
    const diffSec = Math.floor((now.getTime() - d.getTime()) / 1000);

    if (diffSec < 60) return "Just now";
    if (diffSec < 3600) {
      const mins = Math.floor(diffSec / 60);
      return `${mins} ${mins === 1 ? "minute" : "minutes"} ago`;
    }
    if (diffSec < 86400) {
      const hours = Math.floor(diffSec / 3600);
      return `${hours} ${hours === 1 ? "hour" : "hours"} ago`;
    }
    if (diffSec < 604800) {
      const days = Math.floor(diffSec / 86400);
      return `${days} ${days === 1 ? "day" : "days"} ago`;
    }
    return formatDateTime(isoStr);
  } catch {
    return isoStr;
  }
}

/**
 * Maps a Brief section ID to its human-readable title.
 */
export function getBriefSectionTitle(
  sec?: "coverage" | "checklist" | "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H"
): string {
  switch (sec) {
    case "coverage":
      return "Coverage & Gaps Analysis";
    case "checklist":
      return "Investigation Review Checklist";
    case "A":
      return "Section A: Context & Attention Priority";
    case "B":
      return "Section B: Key Findings & Empirical Observations";
    case "C":
      return "Section C: Risk-Adjusted Profile & Limitations";
    case "D":
      return "Section D: Investor Working Thesis & Questions";
    case "E":
      return "Section E: Reconsideration Triggers";
    case "F":
      return "Section F: Historical Evidence Changes";
    case "G":
      return "Section G: Portfolio Holding Context";
    case "H":
      return "Section H: Market Behaviour & Rolling Consistency";
    default:
      return "Research Brief";
  }
}

/**
 * Persists an explicit investigation handoff to browser local storage.
 */
export function saveInvestigationHandoff(
  handoff: InvestigationHandoffContext,
  userId?: string | null
): void {
  if (typeof window === "undefined") return;
  const key = getInvestigationHandoffStorageKey(userId);
  try {
    window.localStorage.setItem(key, JSON.stringify(handoff));
  } catch (e) {
    console.error("Failed to save investigation handoff to storage", e);
  }
}

/**
 * Loads the active investigation handoff from browser local storage.
 */
export function loadInvestigationHandoff(
  userId?: string | null
): InvestigationHandoffContext | null {
  if (typeof window === "undefined") return null;
  const key = getInvestigationHandoffStorageKey(userId);
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object" && typeof parsed.schemeId === "number") {
      return parsed as InvestigationHandoffContext;
    }
    return null;
  } catch (e) {
    console.error("Failed to load investigation handoff from storage", e);
    return null;
  }
}

/**
 * Clears the active investigation handoff from browser local storage.
 */
export function clearInvestigationHandoff(userId?: string | null): void {
  if (typeof window === "undefined") return;
  const key = getInvestigationHandoffStorageKey(userId);
  try {
    window.localStorage.removeItem(key);
  } catch (e) {
    console.error("Failed to clear investigation handoff from storage", e);
  }
}
