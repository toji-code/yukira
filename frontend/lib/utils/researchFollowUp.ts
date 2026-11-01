/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F15: Research Review Follow-Up and Unresolved-Issue Tracking
 *
 * Implements deterministic tracking of specific follow-up issues, questions,
 * and empirical concerns discovered during research review.
 *
 * CRITICAL EPISTEMIC GUARDRAILS:
 * 1. Follow-up issues describe the INVESTIGATION PROCESS, not confirmed fund defects.
 * 2. Unresolved questions do NOT prove that an investment thesis is incorrect.
 * 3. Resolved issues do NOT prove that an investment thesis is valid or suitable.
 * 4. Target dates are investor-entered reminders, NEVER financial urgency signals.
 * 5. Issues are NEVER auto-resolved, auto-dismissed, or silently deleted.
 * 6. Browser-local storage is partitioned by authenticated user / guest identity.
 * 7. Exact (schemeId, schemeOptionId) identity is strictly preserved.
 */

import { getWatchlistItemKey } from "@/lib/hooks/useWatchlist";
import { ReviewItemKey } from "@/lib/utils/researchReviewChecklist";

export type FollowUpCategory =
  | "EVIDENCE_LIMITATION"
  | "QUANT_EXPLANATION"
  | "THESIS_QUESTION"
  | "RISK_PORTFOLIO"
  | "MISSING_INFORMATION"
  | "OTHER";

export type FollowUpStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "WAITING"
  | "RESOLVED"
  | "DISMISSED";

export type FollowUpOutcomeType =
  | "ANSWERED"
  | "PARTIALLY_ANSWERED"
  | "DATA_UNAVAILABLE"
  | "HYPOTHESIS_ADJUSTED"
  | "DISMISSED";

export interface ResearchFollowUpIssue {
  id: string; // Stable unique identifier e.g. "fui-1730000000-abcde"
  schemeId: number;
  schemeOptionId?: number | null;
  schemeCode?: string;
  schemeName: string;
  planType?: string;
  optionType?: string;
  amfiCode?: string | null;
  isin?: string | null;

  // Issue content
  title: string;
  category: FollowUpCategory;
  questionOrReason: string;
  nextAction: string;
  targetDate?: string | null; // ISO Date YYYY-MM-DD

  // Workflow status
  status: FollowUpStatus;

  // Association with existing research sections
  briefSection?: "A" | "B" | "C" | "D" | "E" | "F" | "G" | "H";
  checklistKey?: ReviewItemKey;

  // Resolution context (Phase F15/F18)
  resolutionNote?: string | null;
  resolvedAt?: string | null; // ISO timestamp
  outcomeType?: FollowUpOutcomeType | null; // Phase F18: structured investor-authored outcome classification
  residualUncertainty?: string | null; // Phase F18: documented uncertainty or missing data for partial answers

  // Reopened audit record (Phase F18 & F24: historical outcome preservation and renewal rationale)
  priorResolutionNote?: string | null; // Preserves historical resolution note across reopening
  priorResolvedAt?: string | null; // Preserves timestamp of prior resolution
  priorOutcomeType?: FollowUpOutcomeType | null; // Preserves prior outcome classification
  reopenedAt?: string | null; // Timestamp when issue was reopened for renewed investigation
  reopenReason?: string | null; // Phase F24: investor-authored reason for renewed investigation

  // Investigation completion & blocker resolution (Phase F24)
  closureLimitationRationale?: string | null; // Explanation when resolving despite evidence drift, unfulfilled action, or missing provenance
  hasResidualUncertainty?: boolean | null; // Explicit flag indicating resolution with residual uncertainty

  // Evidentiary provenance & traceability context (Phase F19)
  evidenceRunId?: number | null; // Authoritative calculation run ID when finding was recorded
  evidenceAsOfDate?: string | null; // Market observation cutoff date when finding was recorded
  priorEvidenceRunId?: number | null; // Preserves calculation run ID from prior historical resolution
  priorEvidenceAsOfDate?: string | null; // Preserves observation cutoff date from prior historical resolution

  // Reassessment & Evidence Change History (Phase F28)
  reassessmentDisposition?: ReassessmentDispositionType | null;
  reassessmentRationale?: string | null;
  reassessedAt?: string | null; // Timestamp of latest reassessment
  reassessmentHistory?: ReassessmentRecord[]; // Full chronological list of reassessment records

  // Investigation Dependencies & Blocker Tracking (Phase F29)
  dependencies?: InvestigationDependency[];

  // Timestamps
  createdAt: string; // ISO timestamp
  updatedAt: string; // ISO timestamp
}

export interface FollowUpCategoryConfig {
  key: FollowUpCategory;
  label: string;
  shortLabel: string;
  badgeClass: string;
  description: string;
  defaultBriefSection: "A" | "B" | "C" | "D" | "E" | "F" | "G";
}

export const FOLLOW_UP_CATEGORIES_CONFIG: Record<
  FollowUpCategory,
  FollowUpCategoryConfig
> = {
  EVIDENCE_LIMITATION: {
    key: "EVIDENCE_LIMITATION",
    label: "Data / Evidence Limitation",
    shortLabel: "Data Limit",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: "Observation window cutoff, historical observation gaps, or benchmark comparability issues.",
    defaultBriefSection: "F",
  },
  QUANT_EXPLANATION: {
    key: "QUANT_EXPLANATION",
    label: "Quantitative Anomaly Requiring Explanation",
    shortLabel: "Quant Anomaly",
    badgeClass: "bg-cyan-500/15 text-cyan-300 border-cyan-500/40",
    description: "Unexplained return divergence, volatility anomaly, tracking drag, or beta drift requiring audit.",
    defaultBriefSection: "B",
  },
  THESIS_QUESTION: {
    key: "THESIS_QUESTION",
    label: "Thesis Question / Hypothesis Uncertainty",
    shortLabel: "Thesis Question",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
    description: "Core investment hypothesis question, manager continuity, or strategy execution uncertainty.",
    defaultBriefSection: "D",
  },
  RISK_PORTFOLIO: {
    key: "RISK_PORTFOLIO",
    label: "Risk / Portfolio Context Question",
    shortLabel: "Risk & Portfolio",
    badgeClass: "bg-rose-500/15 text-rose-300 border-rose-500/40",
    description: "Asset overlap with existing holdings, tail risk, or share-class expense ratio mismatch.",
    defaultBriefSection: "G",
  },
  MISSING_INFORMATION: {
    key: "MISSING_INFORMATION",
    label: "Missing Research Information",
    shortLabel: "Missing Info",
    badgeClass: "bg-yellow-500/15 text-yellow-300 border-yellow-500/40",
    description: "Missing factsheet disclosures, incomplete strategy explanation, or unverified filings.",
    defaultBriefSection: "A",
  },
  OTHER: {
    key: "OTHER",
    label: "Other Investigation Inquiry",
    shortLabel: "Other Diligence",
    badgeClass: "bg-surface-raised text-text-secondary border-border",
    description: "General follow-up inquiry requiring investor due-diligence attention.",
    defaultBriefSection: "A",
  },
};

export const FOLLOW_UP_STATUS_CONFIG: Record<
  FollowUpStatus,
  { label: string; badgeClass: string; description: string; isUnresolved: boolean }
> = {
  OPEN: {
    label: "Open",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: "Identified inquiry requiring investigation; not yet addressed.",
    isUnresolved: true,
  },
  IN_PROGRESS: {
    label: "In Progress",
    badgeClass: "bg-blue-500/15 text-blue-300 border-blue-500/40",
    description: "Investigation currently active; collecting corroborating evidence.",
    isUnresolved: true,
  },
  WAITING: {
    label: "Waiting",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
    description: "Awaiting subsequent NAV cutoff, AMC factsheet release, or market cycle data.",
    isUnresolved: true,
  },
  RESOLVED: {
    label: "Resolved",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/40",
    description: "Issue addressed and factual outcome recorded by investor.",
    isUnresolved: false,
  },
  DISMISSED: {
    label: "Dismissed",
    badgeClass: "bg-surface-raised text-text-muted border-border/70",
    description: "Investor explicitly decided not to pursue this inquiry.",
    isUnresolved: false,
  },
};

export interface FollowUpOutcomeConfig {
  key: FollowUpOutcomeType;
  label: string;
  shortLabel: string;
  badgeClass: string;
  description: string;
}

export const FOLLOW_UP_OUTCOMES_CONFIG: Record<
  FollowUpOutcomeType,
  FollowUpOutcomeConfig
> = {
  ANSWERED: {
    key: "ANSWERED",
    label: "Question Answered (Evidence Verified)",
    shortLabel: "Answered",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/40",
    description: "Inspected evidence sufficiently addresses the inquiry or explains the observed anomaly.",
  },
  PARTIALLY_ANSWERED: {
    key: "PARTIALLY_ANSWERED",
    label: "Partially Answered (Residual Uncertainty)",
    shortLabel: "Partial Answer",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: "Evidence explains part of the inquiry; residual uncertainty or follow-up remains.",
  },
  DATA_UNAVAILABLE: {
    key: "DATA_UNAVAILABLE",
    label: "Inconclusive (Data Unavailable)",
    shortLabel: "Data Limit",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
    description: "Required market observations, portfolio disclosures, or historical runs are unavailable.",
  },
  HYPOTHESIS_ADJUSTED: {
    key: "HYPOTHESIS_ADJUSTED",
    label: "Hypothesis / Thesis Adjusted",
    shortLabel: "Thesis Adjusted",
    badgeClass: "bg-cyan-500/15 text-cyan-300 border-cyan-500/40",
    description: "Findings led to refined thesis assumptions or updated reconsideration conditions.",
  },
  DISMISSED: {
    key: "DISMISSED",
    label: "Dismissed (Not Pursued)",
    shortLabel: "Dismissed",
    badgeClass: "bg-surface-raised text-text-muted border-border/70",
    description: "Inquiry determined to be immaterial, non-actionable, or intentionally discontinued.",
  },
};

// =========================================================================
// Phase F24: Investigation Completion, Blocker Resolution & Evidence Closure
// =========================================================================

export type InvestigationClosureState =
  | "STILL_INVESTIGATING" // OPEN or IN_PROGRESS
  | "WAITING_DEPENDENCY" // WAITING
  | "RESOLVED_COMPLETE" // RESOLVED with no residual uncertainty / verified outcome
  | "RESOLVED_WITH_UNCERTAINTY" // RESOLVED with explicit residual uncertainty or partial answer
  | "DISMISSED"; // DISMISSED

export interface InvestigationClosureResolution {
  state: InvestigationClosureState;
  label: string;
  shortLabel: string;
  badgeClass: string;
  description: string;
  isClosed: boolean;
  hasResidualUncertainty: boolean;
  isWaiting: boolean;
}

/**
 * Deterministically resolves the concrete closure classification for a follow-up inquiry.
 * Explicitly distinguishes complete resolutions from outcomes bearing residual uncertainty.
 */
export function evaluateInvestigationClosureState(
  issue: ResearchFollowUpIssue
): InvestigationClosureResolution {
  if (issue.status === "DISMISSED") {
    return {
      state: "DISMISSED",
      label: "Dismissed (Discontinued)",
      shortLabel: "Dismissed",
      badgeClass: "bg-surface-raised text-text-muted border-border/70",
      description: "Investor intentionally stopped pursuing this inquiry with documented rationale.",
      isClosed: true,
      hasResidualUncertainty: false,
      isWaiting: false,
    };
  }

  if (issue.status === "RESOLVED") {
    const hasUncertainty = Boolean(
      (issue.residualUncertainty && issue.residualUncertainty.trim().length > 0) ||
      issue.outcomeType === "PARTIALLY_ANSWERED" ||
      issue.outcomeType === "DATA_UNAVAILABLE" ||
      issue.hasResidualUncertainty === true
    );

    if (hasUncertainty) {
      return {
        state: "RESOLVED_WITH_UNCERTAINTY",
        label: "Resolved (With Residual Uncertainty)",
        shortLabel: "Resolved with Uncertainty",
        badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
        description:
          "Empirical outcome recorded, but investor explicitly documents remaining uncertainty or missing disclosures.",
        isClosed: true,
        hasResidualUncertainty: true,
        isWaiting: false,
      };
    }

    return {
      state: "RESOLVED_COMPLETE",
      label: "Resolved (Outcome Verified)",
      shortLabel: "Resolved",
      badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/40",
      description: "Documented investigation question sufficiently addressed by verified evidence.",
      isClosed: true,
      hasResidualUncertainty: false,
      isWaiting: false,
    };
  }

  if (issue.status === "WAITING") {
    return {
      state: "WAITING_DEPENDENCY",
      label: "Waiting on Dependency",
      shortLabel: "Waiting",
      badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
      description: "Investigation paused waiting on subsequent NAV cutoff, factsheet release, or external dependency.",
      isClosed: false,
      hasResidualUncertainty: false,
      isWaiting: true,
    };
  }

  // OPEN or IN_PROGRESS
  return {
    state: "STILL_INVESTIGATING",
    label: issue.status === "IN_PROGRESS" ? "In Progress" : "Open Inquiry",
    shortLabel: issue.status === "IN_PROGRESS" ? "In Progress" : "Open",
    badgeClass:
      issue.status === "IN_PROGRESS"
        ? "bg-blue-500/15 text-blue-300 border-blue-500/40"
        : "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: "Active inquiry undergoing due-diligence review and evidence collection.",
    isClosed: false,
    hasResidualUncertainty: false,
    isWaiting: false,
  };
}

export interface InvestigationClosureGuidance {
  isClosureReady: boolean;
  canResolveCleanly?: boolean; // Convenience alias for presentation views
  materialLimitations: {
    code: "EVIDENCE_DRIFT" | "SUPERSEDED_RUN" | "PROVENANCE_UNRECORDED" | "PENDING_NEXT_ACTION" | "WAITING_DEPENDENCY";
    label: string;
    description: string;
    severity: "warning" | "info";
    explanation?: string;
  }[];
  blockers?: {
    code: string;
    label: string;
    explanation: string;
  }[];
  requiresLimitationExplanation: boolean;
}

/**
 * Evaluates whether an issue being closed has outstanding material limitations,
 * unfulfilled next actions, unaddressed dependencies, or unverified evidence drift that should be explicitly
 * acknowledged by the investor before resolution.
 */
export function evaluateInvestigationClosureGuidance(
  issue: Partial<ResearchFollowUpIssue>,
  prov?: FindingProvenanceSummary | null,
  activeNextAction?: string
): InvestigationClosureGuidance {
  const limitations: InvestigationClosureGuidance["materialLimitations"] = [];

  // 1. Evidence drift
  if (prov?.status === "HISTORICAL_EVIDENCE_CHANGED") {
    limitations.push({
      code: "EVIDENCE_DRIFT",
      label: "Observation Cutoff Drift",
      description: "Evidence observations have shifted since this inquiry was originally logged.",
      severity: "warning",
    });
  }

  // 2. Later calculation run available
  if (prov?.status === "HISTORICAL_RUN_SUPERSEDED") {
    limitations.push({
      code: "SUPERSEDED_RUN",
      label: "Subsequent Calculation Run Available",
      description: `Run #${prov.currentRunId} is available in active workspace vs recorded run #${prov.recordedRunId}.`,
      severity: "info",
    });
  }

  // 3. Provenance unrecorded
  if (prov?.status === "PROVENANCE_NOT_RECORDED") {
    limitations.push({
      code: "PROVENANCE_UNRECORDED",
      label: "Provenance Unrecorded",
      description: "Finding lacks calculation run ID and observation cutoff date.",
      severity: "info",
    });
  }

  // 4. Pending next action that was recorded
  const actionText = (activeNextAction || issue.nextAction || "").trim();
  if (
    actionText.length > 0 &&
    actionText.toLowerCase() !== "none" &&
    actionText.toLowerCase() !== "resolved" &&
    actionText.toLowerCase() !== "completed"
  ) {
    limitations.push({
      code: "PENDING_NEXT_ACTION",
      label: "Documented Next Action Exists",
      description: `Planned action: "${actionText}". Confirm if this was executed or is no longer required.`,
      severity: "warning",
    });
  }

  // 5. Waiting on dependency or unaddressed blockers (Phase F29)
  const awaitingDeps = (issue.dependencies || []).filter((d) => d.status === "AWAITING");
  if (awaitingDeps.length > 0) {
    limitations.push({
      code: "WAITING_DEPENDENCY",
      label: "Unresolved Dependencies / Blockers",
      description: `Inquiry has ${awaitingDeps.length} unresolved prerequisite blocker(s) awaiting information: ${awaitingDeps.map((d) => d.description).join("; ")}.`,
      severity: "warning",
    });
  } else if (issue.status === "WAITING") {
    limitations.push({
      code: "WAITING_DEPENDENCY",
      label: "Waiting Status Transition",
      description: "Inquiry was marked waiting on an external dependency.",
      severity: "warning",
    });
  }

  const hasWarning = limitations.some((l) => l.severity === "warning");
  const isClosureReady = !hasWarning;

  return {
    isClosureReady,
    canResolveCleanly: isClosureReady,
    materialLimitations: limitations.map((l) => ({ ...l, explanation: l.description })),
    blockers: limitations.map((l) => ({ code: l.code, label: l.label, explanation: l.description })),
    requiresLimitationExplanation: hasWarning,
  };
}

export type FindingEvidenceStatus =
  | "CURRENT_CONTEXT"
  | "HISTORICAL_EVIDENCE_CHANGED"
  | "HISTORICAL_RUN_SUPERSEDED"
  | "PROVENANCE_NOT_RECORDED";

export interface FindingProvenanceSummary {
  status: FindingEvidenceStatus;
  statusBadgeLabel: string;
  statusBadgeClass: string;
  statusDescription: string;
  recordedRunId: number | null;
  recordedAsOfDate: string | null;
  currentRunId: number | null;
  currentAsOfDate: string | null;
  isRunChanged: boolean;
  isAsOfDateChanged: boolean;
  isEvidenceChanged: boolean;
  evidenceChangesCount: number;
  hasPriorOutcome: boolean;
  priorRunId: number | null;
  priorAsOfDate: string | null;
  priorResolvedAt: string | null;
  priorResolutionNote: string | null;
  priorOutcomeType: FollowUpOutcomeType | null;
  reopenedAt: string | null;
}

/**
 * Evaluates the evidentiary provenance and current context stability for a documented finding.
 * Distinguishes recorded facts from unrecorded provenance without fabricating data.
 */
export function evaluateFindingEvidenceTraceability(
  issue: ResearchFollowUpIssue,
  currentRunId?: number | null,
  currentAsOfDate?: string | null,
  evidenceChangesCount?: number
): FindingProvenanceSummary {
  const recordedRunId = issue.evidenceRunId ?? null;
  const recordedAsOfDate = issue.evidenceAsOfDate ?? null;
  const curRun = currentRunId ?? null;
  const curAsOf = currentAsOfDate ?? null;

  const isRunRecorded = recordedRunId !== null;
  const isAsOfRecorded = recordedAsOfDate !== null;
  const hasRecordedProvenance = isRunRecorded || isAsOfRecorded;

  const isRunChanged = isRunRecorded && curRun !== null && recordedRunId !== curRun;
  const isAsOfDateChanged = isAsOfRecorded && curAsOf !== null && recordedAsOfDate !== curAsOf;
  const numChanges = evidenceChangesCount ?? 0;
  const isEvidenceChanged = numChanges > 0;

  let status: FindingEvidenceStatus;
  let statusBadgeLabel: string;
  let statusBadgeClass: string;
  let statusDescription: string;

  if (!hasRecordedProvenance) {
    status = "PROVENANCE_NOT_RECORDED";
    statusBadgeLabel = "Provenance Unrecorded";
    statusBadgeClass = "bg-surface-raised border-border text-text-muted";
    statusDescription =
      "No specific calculation run or observation cutoff was recorded when this finding was logged. Available evidence can be examined directly in the Research Brief.";
  } else if (isEvidenceChanged) {
    status = "HISTORICAL_EVIDENCE_CHANGED";
    statusBadgeLabel = "Evidence Changed Since Finding";
    statusBadgeClass = "bg-amber-500/15 border-amber-500/40 text-amber-300";
    statusDescription = `${numChanges} evidence observation difference(s) detected since this finding was recorded. Re-inspect evidence in Section F to verify whether findings remain sound.`;
  } else if (isRunChanged || isAsOfDateChanged) {
    status = "HISTORICAL_RUN_SUPERSEDED";
    statusBadgeLabel = "Later Run Available";
    statusBadgeClass = "bg-blue-500/15 border-blue-500/40 text-blue-300";
    statusDescription =
      "A subsequent calculation run or horizon cutoff has completed since this finding was recorded. A differing run identifier alone does not prove metric values changed.";
  } else {
    status = "CURRENT_CONTEXT";
    statusBadgeLabel = "Current Context";
    statusBadgeClass = "bg-emerald-500/15 border-emerald-500/40 text-emerald-300";
    statusDescription =
      "Finding was documented against the active calculation run and observation horizon.";
  }

  return {
    status,
    statusBadgeLabel,
    statusBadgeClass,
    statusDescription,
    recordedRunId,
    recordedAsOfDate,
    currentRunId: curRun,
    currentAsOfDate: curAsOf,
    isRunChanged,
    isAsOfDateChanged,
    isEvidenceChanged,
    evidenceChangesCount: numChanges,
    hasPriorOutcome: Boolean(issue.priorResolutionNote || issue.reopenedAt),
    priorRunId: issue.priorEvidenceRunId ?? null,
    priorAsOfDate: issue.priorEvidenceAsOfDate ?? null,
    priorResolvedAt: issue.priorResolvedAt ?? null,
    priorResolutionNote: issue.priorResolutionNote ?? null,
    priorOutcomeType: issue.priorOutcomeType ?? null,
    reopenedAt: issue.reopenedAt ?? null,
  };
}

export interface FollowUpWorkspaceSummary {
  totalIssues: number;
  unresolvedCount: number; // OPEN + IN_PROGRESS + WAITING
  openCount: number;
  inProgressCount: number;
  waitingCount: number;
  targetDatePassedCount: number;
  resolvedCount: number;
  dismissedCount: number;
}

const FOLLOW_UP_STORAGE_PREFIX = "yukira_research_followup_issues_";

/**
 * Derives a namespaced local-storage key to partition follow-up issues by user identity.
 */
export function getFollowUpStorageKey(userId?: string | null): string {
  if (userId && typeof userId === "string" && userId.trim().length > 0) {
    return `${FOLLOW_UP_STORAGE_PREFIX}${encodeURIComponent(userId.trim())}`;
  }
  return `${FOLLOW_UP_STORAGE_PREFIX}guest`;
}

/**
 * Checks whether an investor-entered target date has elapsed (strictly prior to current date).
 * Returns false if targetDate is undefined, null, or empty.
 */
export function isTargetDatePassed(targetDate?: string | null): boolean {
  if (!targetDate || typeof targetDate !== "string" || targetDate.trim().length === 0) {
    return false;
  }
  try {
    const parts = targetDate.trim().split("-");
    if (parts.length !== 3) return false;
    const target = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
    if (isNaN(target.getTime())) return false;

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return target.getTime() < today.getTime();
  } catch {
    return false;
  }
}

/**
 * Loads all follow-up issues from local browser storage.
 */
export function loadAllFollowUpIssues(userId?: string | null): ResearchFollowUpIssue[] {
  if (typeof window === "undefined") return [];
  try {
    const storageKey = getFollowUpStorageKey(userId);
    const raw = window.localStorage.getItem(storageKey);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is ResearchFollowUpIssue =>
        Boolean(item && typeof item === "object" && item.id && item.schemeId && item.title)
    );
  } catch {
    return [];
  }
}

/**
 * Retrieves all follow-up issues associated with a specific mutual fund share class.
 */
export function getFundFollowUpIssues(
  schemeId: number,
  schemeOptionId?: number | null,
  userId?: string | null
): ResearchFollowUpIssue[] {
  const all = loadAllFollowUpIssues(userId);
  return all.filter((issue) => {
    if (issue.schemeId !== schemeId) return false;
    if (schemeOptionId !== undefined && schemeOptionId !== null) {
      return issue.schemeOptionId === schemeOptionId;
    }
    return true;
  });
}

/**
 * Persists or updates a research follow-up issue record.
 */
export function saveFollowUpIssue(
  issue: ResearchFollowUpIssue,
  userId?: string | null
): ResearchFollowUpIssue {
  const all = loadAllFollowUpIssues(userId);
  const now = new Date().toISOString();
  const existingIndex = all.findIndex((i) => i.id === issue.id);

  const updatedIssue: ResearchFollowUpIssue = {
    ...issue,
    updatedAt: now,
    createdAt: issue.createdAt || now,
  };

  let nextList: ResearchFollowUpIssue[];
  if (existingIndex >= 0) {
    nextList = [...all];
    nextList[existingIndex] = updatedIssue;
  } else {
    nextList = [updatedIssue, ...all];
  }

  if (typeof window !== "undefined") {
    try {
      const storageKey = getFollowUpStorageKey(userId);
      window.localStorage.setItem(storageKey, JSON.stringify(nextList));
    } catch (err) {
      console.warn("Failed to persist research follow-up issue to browser storage", err);
    }
  }

  return updatedIssue;
}

/**
 * Updates the status of an existing follow-up issue with mandatory outcome documentation.
 * Preserves prior resolution notes and outcomes when reopening an issue.
 */
export function updateFollowUpIssueStatus(
  issueId: string,
  newStatus: FollowUpStatus,
  resolutionNote?: string | null,
  outcomeType?: FollowUpOutcomeType | null,
  residualUncertainty?: string | null,
  userId?: string | null,
  evidenceRunId?: number | null,
  evidenceAsOfDate?: string | null
): ResearchFollowUpIssue | null {
  const all = loadAllFollowUpIssues(userId);
  const existing = all.find((i) => i.id === issueId);
  if (!existing) return null;

  const now = new Date().toISOString();
  const wasClosed = existing.status === "RESOLVED" || existing.status === "DISMISSED";
  const isNowActive = newStatus === "OPEN" || newStatus === "IN_PROGRESS" || newStatus === "WAITING";
  const isReopening = wasClosed && isNowActive;
  const isResolving = newStatus === "RESOLVED" || newStatus === "DISMISSED";

  // When reopening a closed issue, permanently capture previous resolution facts into prior* fields
  const priorResolutionNote = isReopening
    ? existing.resolutionNote || existing.priorResolutionNote || null
    : existing.priorResolutionNote || null;
  const priorResolvedAt = isReopening
    ? existing.resolvedAt || existing.priorResolvedAt || null
    : existing.priorResolvedAt || null;
  const priorOutcomeType = isReopening
    ? existing.outcomeType || existing.priorOutcomeType || null
    : existing.priorOutcomeType || null;
  const priorEvidenceRunId = isReopening
    ? existing.evidenceRunId || existing.priorEvidenceRunId || null
    : existing.priorEvidenceRunId || null;
  const priorEvidenceAsOfDate = isReopening
    ? existing.evidenceAsOfDate || existing.priorEvidenceAsOfDate || null
    : existing.priorEvidenceAsOfDate || null;
  const reopenedAt = isReopening ? now : existing.reopenedAt || null;

  const updated: ResearchFollowUpIssue = {
    ...existing,
    status: newStatus,
    resolutionNote: isResolving
      ? (resolutionNote !== undefined ? resolutionNote : existing.resolutionNote)
      : (isReopening ? null : existing.resolutionNote),
    outcomeType: isResolving
      ? (outcomeType !== undefined ? outcomeType : existing.outcomeType || (newStatus === "DISMISSED" ? "DISMISSED" : "ANSWERED"))
      : (isReopening ? null : existing.outcomeType),
    residualUncertainty: isResolving
      ? (residualUncertainty !== undefined ? residualUncertainty : existing.residualUncertainty)
      : (isReopening ? null : existing.residualUncertainty),
    resolvedAt: isResolving ? (existing.resolvedAt || now) : null,
    evidenceRunId: isResolving
      ? (evidenceRunId !== undefined ? evidenceRunId : existing.evidenceRunId)
      : (isReopening ? null : existing.evidenceRunId),
    evidenceAsOfDate: isResolving
      ? (evidenceAsOfDate !== undefined ? evidenceAsOfDate : existing.evidenceAsOfDate)
      : (isReopening ? null : existing.evidenceAsOfDate),
    priorResolutionNote,
    priorResolvedAt,
    priorOutcomeType,
    priorEvidenceRunId,
    priorEvidenceAsOfDate,
    reopenedAt,
    updatedAt: now,
  };

  saveFollowUpIssue(updated, userId);
  return updated;
}

/**
 * Removes a follow-up issue record.
 */
export function deleteFollowUpIssue(issueId: string, userId?: string | null): void {
  const all = loadAllFollowUpIssues(userId);
  const nextList = all.filter((i) => i.id !== issueId);
  if (typeof window !== "undefined") {
    try {
      const storageKey = getFollowUpStorageKey(userId);
      window.localStorage.setItem(storageKey, JSON.stringify(nextList));
    } catch (err) {
      console.warn("Failed to delete follow-up issue from browser storage", err);
    }
  }
}

/**
 * Computes deterministic aggregate statistics across a list of follow-up issues.
 */
export function computeFollowUpSummary(
  issues: ResearchFollowUpIssue[]
): FollowUpWorkspaceSummary {
  let openCount = 0;
  let inProgressCount = 0;
  let waitingCount = 0;
  let targetDatePassedCount = 0;
  let resolvedCount = 0;
  let dismissedCount = 0;

  for (const issue of issues) {
    if (issue.status === "OPEN") {
      openCount++;
    } else if (issue.status === "IN_PROGRESS") {
      inProgressCount++;
    } else if (issue.status === "WAITING") {
      waitingCount++;
    } else if (issue.status === "RESOLVED") {
      resolvedCount++;
    } else if (issue.status === "DISMISSED") {
      dismissedCount++;
    }

    const isUnresolved =
      issue.status === "OPEN" ||
      issue.status === "IN_PROGRESS" ||
      issue.status === "WAITING";

    if (isUnresolved && isTargetDatePassed(issue.targetDate)) {
      targetDatePassedCount++;
    }
  }

  const unresolvedCount = openCount + inProgressCount + waitingCount;

  return {
    totalIssues: issues.length,
    unresolvedCount,
    openCount,
    inProgressCount,
    waitingCount,
    targetDatePassedCount,
    resolvedCount,
    dismissedCount,
  };
}

// =========================================================================
// Phase F28: Evidence-Aware Reassessment Continuity & Investigation History
// =========================================================================

export type ReassessmentDispositionType =
  | "PRIOR_OUTCOME_CONFIRMED"
  | "OUTCOME_REVISED"
  | "EVIDENCE_INSUFFICIENT"
  | "DEFERRED_DEPENDENCY";

export interface ReassessmentRecord {
  id: string;
  reassessedAt: string; // ISO timestamp
  disposition: ReassessmentDispositionType;
  rationale: string;
  priorOutcomeType?: FollowUpOutcomeType | null;
  priorResolutionNote?: string | null;
  revisedOutcomeType?: FollowUpOutcomeType | null;
  revisedResolutionNote?: string | null;
  evidenceRunId?: number | null;
  evidenceAsOfDate?: string | null;
  priorEvidenceRunId?: number | null;
  priorEvidenceAsOfDate?: string | null;
  residualUncertainty?: string | null;
  dependencyNote?: string | null;
}

export const REASSESSMENT_DISPOSITION_CONFIG: Record<
  ReassessmentDispositionType,
  { label: string; shortLabel: string; badgeClass: string; description: string }
> = {
  PRIOR_OUTCOME_CONFIRMED: {
    label: "Prior Outcome Retained (Confirmed Applicable)",
    shortLabel: "Outcome Retained",
    badgeClass: "bg-emerald-500/15 text-emerald-300 border-emerald-500/40",
    description: "Changed evidence was examined; previous conclusion remains sound and supported.",
  },
  OUTCOME_REVISED: {
    label: "Outcome Revised Based on Changed Evidence",
    shortLabel: "Outcome Revised",
    badgeClass: "bg-cyan-500/15 text-cyan-300 border-cyan-500/40",
    description: "Changed evidence led to a modified conclusion or adjusted research thesis.",
  },
  EVIDENCE_INSUFFICIENT: {
    label: "Reviewed (Evidence Insufficient for Conclusion)",
    shortLabel: "Evidence Insufficient",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: "Changed evidence was reviewed, but available data remains inconclusive.",
  },
  DEFERRED_DEPENDENCY: {
    label: "Reassessment Deferred (Pending External Dependency)",
    shortLabel: "Reassessment Deferred",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
    description: "Reassessment deferred pending subsequent factsheet release, filings, or trading cycle.",
  },
};

export type TimelineEventType =
  | "CREATED"
  | "PRIOR_RESOLVED"
  | "REOPENED"
  | "REASSESSED"
  | "RESOLVED"
  | "DISMISSED"
  | "WAITING_DEPENDENCY"
  | "DEPENDENCY_RECORDED"
  | "DEPENDENCY_ADDRESSED"
  | "DEPENDENCY_DISCONTINUED"
  | "EVIDENCE_DRIFT_DETECTED"
  | "SUPERSEDED_RUN_DETECTED";

export interface InvestigationTimelineEvent {
  id: string;
  timestamp: string; // ISO timestamp
  type: TimelineEventType;
  label: string;
  badgeClass: string;
  summary: string;
  details?: string | null;
  provenanceRunId?: number | null;
  provenanceAsOfDate?: string | null;
  outcomeType?: FollowUpOutcomeType | null;
  residualUncertainty?: string | null;
  reopenReason?: string | null;
  disposition?: ReassessmentDispositionType | null;
  dependencyId?: string | null;
  dependencyStatus?: DependencyStatus | null;
  isCurrentState?: boolean;
}

/**
 * Builds a deterministic chronological timeline of an investigation from its stored records and active provenance context.
 * Strictly separates historical events from current active evidence state without fabricating intermediate events.
 */
export function buildInvestigationTimeline(
  issue: ResearchFollowUpIssue,
  provenance?: FindingProvenanceSummary | null
): InvestigationTimelineEvent[] {
  const events: InvestigationTimelineEvent[] = [];
  const catConfig = FOLLOW_UP_CATEGORIES_CONFIG[issue.category];

  // 1. Initial Creation Event
  events.push({
    id: `${issue.id}-created`,
    timestamp: issue.createdAt,
    type: "CREATED",
    label: "Inquiry Documented",
    badgeClass: "bg-surface-raised border-border text-text-primary",
    summary: `Logged inquiry: "${issue.title}" (${catConfig?.shortLabel || issue.category})`,
    details: issue.questionOrReason || null,
    provenanceRunId: issue.evidenceRunId,
    provenanceAsOfDate: issue.evidenceAsOfDate,
    isCurrentState: false,
  });

  // 2. Historical Prior Resolution (if preserved from reopening)
  if (issue.priorResolvedAt || issue.priorOutcomeType || issue.priorResolutionNote) {
    const priorOutcomeCfg = issue.priorOutcomeType ? FOLLOW_UP_OUTCOMES_CONFIG[issue.priorOutcomeType] : null;
    events.push({
      id: `${issue.id}-prior-resolved`,
      timestamp: issue.priorResolvedAt || issue.createdAt,
      type: "PRIOR_RESOLVED",
      label: "Prior Outcome Recorded",
      badgeClass: priorOutcomeCfg?.badgeClass || "bg-emerald-500/15 border-emerald-500/40 text-emerald-300",
      summary: `Historical outcome: ${priorOutcomeCfg?.label || issue.priorOutcomeType || "Resolved"}`,
      details: issue.priorResolutionNote || null,
      provenanceRunId: issue.priorEvidenceRunId || issue.evidenceRunId,
      provenanceAsOfDate: issue.priorEvidenceAsOfDate || issue.evidenceAsOfDate,
      outcomeType: issue.priorOutcomeType,
      isCurrentState: false,
    });
  }

  // 3. Reopening Event (if reopened)
  if (issue.reopenedAt || issue.reopenReason) {
    events.push({
      id: `${issue.id}-reopened`,
      timestamp: issue.reopenedAt || issue.updatedAt,
      type: "REOPENED",
      label: "Investigation Reopened",
      badgeClass: "bg-indigo-500/15 border-indigo-500/40 text-indigo-300",
      summary: "Inquiry reopened for renewed due diligence",
      details: issue.reopenReason ? `Renewal rationale: ${issue.reopenReason}` : null,
      reopenReason: issue.reopenReason,
      isCurrentState: false,
    });
  }

  // 4. Reassessment History (Phase F28)
  if (issue.reassessmentHistory && issue.reassessmentHistory.length > 0) {
    issue.reassessmentHistory.forEach((rec, idx) => {
      const dispCfg = REASSESSMENT_DISPOSITION_CONFIG[rec.disposition];
      events.push({
        id: rec.id || `${issue.id}-reassess-${idx}`,
        timestamp: rec.reassessedAt,
        type: "REASSESSED",
        label: "Evidence Reassessment",
        badgeClass: dispCfg?.badgeClass || "bg-cyan-500/15 border-cyan-500/40 text-cyan-300",
        summary: `Reassessment disposition: ${dispCfg?.shortLabel || rec.disposition}`,
        details: rec.rationale,
        provenanceRunId: rec.evidenceRunId,
        provenanceAsOfDate: rec.evidenceAsOfDate,
        outcomeType: rec.revisedOutcomeType || rec.priorOutcomeType,
        disposition: rec.disposition,
        isCurrentState: false,
      });
    });
  } else if (issue.reassessedAt && issue.reassessmentDisposition) {
    const dispCfg = REASSESSMENT_DISPOSITION_CONFIG[issue.reassessmentDisposition];
    events.push({
      id: `${issue.id}-reassessed-latest`,
      timestamp: issue.reassessedAt,
      type: "REASSESSED",
      label: "Evidence Reassessment",
      badgeClass: dispCfg?.badgeClass || "bg-cyan-500/15 border-cyan-500/40 text-cyan-300",
      summary: `Reassessment disposition: ${dispCfg?.shortLabel || issue.reassessmentDisposition}`,
      details: issue.reassessmentRationale || null,
      provenanceRunId: issue.evidenceRunId,
      provenanceAsOfDate: issue.evidenceAsOfDate,
      disposition: issue.reassessmentDisposition,
      isCurrentState: false,
    });
  }

  // 5. Current Resolution or Dismissal (if currently resolved/dismissed)
  const isCurrentlyClosed = issue.status === "RESOLVED" || issue.status === "DISMISSED";
  if (isCurrentlyClosed && issue.resolvedAt) {
    const isDismissed = issue.status === "DISMISSED";
    const outcomeCfg = issue.outcomeType ? FOLLOW_UP_OUTCOMES_CONFIG[issue.outcomeType] : null;
    events.push({
      id: `${issue.id}-current-closed`,
      timestamp: issue.resolvedAt,
      type: isDismissed ? "DISMISSED" : "RESOLVED",
      label: isDismissed ? "Inquiry Dismissed" : "Outcome Recorded",
      badgeClass: isDismissed
        ? "bg-surface-raised border-border text-text-muted"
        : outcomeCfg?.badgeClass || "bg-emerald-500/15 border-emerald-500/40 text-emerald-300",
      summary: isDismissed
        ? "Inquiry dismissed by investor"
        : `Outcome: ${outcomeCfg?.label || issue.outcomeType || "Resolved"}`,
      details: issue.resolutionNote || null,
      provenanceRunId: issue.evidenceRunId,
      provenanceAsOfDate: issue.evidenceAsOfDate,
      outcomeType: issue.outcomeType,
      residualUncertainty: issue.residualUncertainty,
      isCurrentState: false,
    });
  }

  // 6. Structured Dependencies Events (Phase F29)
  if (issue.dependencies && issue.dependencies.length > 0) {
    issue.dependencies.forEach((dep) => {
      const catCfg = DEPENDENCY_CATEGORY_CONFIG[dep.category];
      // 1. Dependency recorded event
      events.push({
        id: `${dep.id}-recorded`,
        timestamp: dep.recordedAt,
        type: "DEPENDENCY_RECORDED",
        label: "Prerequisite Documented",
        badgeClass: catCfg?.badgeClass || "bg-purple-500/15 border-purple-500/40 text-purple-300",
        summary: `Blocked on ${catCfg?.shortLabel || dep.category}: "${dep.description}"`,
        details: `Reason: ${dep.reasonWhyBlocked}${dep.expectedSource ? " • Expected from: " + dep.expectedSource : ""}`,
        provenanceRunId: dep.evidenceRunId,
        provenanceAsOfDate: dep.evidenceAsOfDate,
        dependencyId: dep.id,
        dependencyStatus: dep.status,
        isCurrentState: false,
      });

      // 2. Dependency addressed event (if addressed)
      if (dep.status === "ADDRESSED" && dep.addressedAt) {
        events.push({
          id: `${dep.id}-addressed`,
          timestamp: dep.addressedAt,
          type: "DEPENDENCY_ADDRESSED",
          label: "Prerequisite Addressed",
          badgeClass: "bg-cyan-500/15 border-cyan-500/40 text-cyan-300",
          summary: `Addressed prerequisite: "${dep.description}"`,
          details: `Received: ${dep.receivedInfoOrChange || "Information received"}${dep.resolutionRationale ? " • Rationale: " + dep.resolutionRationale : ""}${dep.resolutionEvidenceReference ? " • Ref: " + dep.resolutionEvidenceReference : ""}`,
          provenanceRunId: dep.resolutionRunId || dep.evidenceRunId,
          provenanceAsOfDate: dep.resolutionAsOfDate || dep.evidenceAsOfDate,
          dependencyId: dep.id,
          dependencyStatus: dep.status,
          isCurrentState: false,
        });
      }

      // 3. Dependency discontinued event (if discontinued)
      if (dep.status === "DISCONTINUED") {
        events.push({
          id: `${dep.id}-discontinued`,
          timestamp: dep.updatedAt || dep.recordedAt,
          type: "DEPENDENCY_DISCONTINUED",
          label: "Prerequisite Discontinued",
          badgeClass: "bg-surface-raised border-border text-text-muted",
          summary: `Discontinued prerequisite: "${dep.description}"`,
          details: dep.resolutionRationale || "Prerequisite discontinued by investor",
          dependencyId: dep.id,
          dependencyStatus: dep.status,
          isCurrentState: false,
        });
      }
    });
  } else if (issue.status === "WAITING" && issue.nextAction) {
    // Legacy fallback milestone when no structured dependencies are recorded
    events.push({
      id: `${issue.id}-waiting`,
      timestamp: issue.updatedAt || issue.createdAt,
      type: "WAITING_DEPENDENCY",
      label: "Waiting on Dependency",
      badgeClass: "bg-purple-500/15 border-purple-500/40 text-purple-300",
      summary: "Investigation paused awaiting dependency",
      details: issue.nextAction,
      isCurrentState: true,
    });
  }

  // 7. Active Evidence State Milestones (clearly tagged as current evidentiary state)
  if (provenance?.status === "HISTORICAL_EVIDENCE_CHANGED") {
    events.push({
      id: `${issue.id}-drift-state`,
      timestamp: new Date().toISOString(),
      type: "EVIDENCE_DRIFT_DETECTED",
      label: "Evidence Drift Detected",
      badgeClass: "bg-amber-500/15 border-amber-500/40 text-amber-300",
      summary: `${provenance.evidenceChangesCount} evidence observation difference(s) detected`,
      details: `Active workspace cutoff (${provenance.currentAsOfDate || "latest"}) differs from recorded baseline (${provenance.recordedAsOfDate || "recorded"}). Reassessment is recommended.`,
      provenanceRunId: provenance.currentRunId,
      provenanceAsOfDate: provenance.currentAsOfDate,
      isCurrentState: true,
    });
  } else if (provenance?.status === "HISTORICAL_RUN_SUPERSEDED") {
    events.push({
      id: `${issue.id}-superseded-state`,
      timestamp: new Date().toISOString(),
      type: "SUPERSEDED_RUN_DETECTED",
      label: "Subsequent Run Available",
      badgeClass: "bg-blue-500/15 border-blue-500/40 text-blue-300",
      summary: `Calculation Run #${provenance.currentRunId} is available`,
      details: `Recorded finding baseline was Run #${provenance.recordedRunId}. Verify if subsequent calculation impacts finding.`,
      provenanceRunId: provenance.currentRunId,
      provenanceAsOfDate: provenance.currentAsOfDate,
      isCurrentState: true,
    });
  }

  // Sort events chronologically (oldest first). Current state markers with identical or latest timestamp appear at the end.
  return events.sort((a, b) => {
    if (a.isCurrentState && !b.isCurrentState) return 1;
    if (!a.isCurrentState && b.isCurrentState) return -1;
    const timeA = new Date(a.timestamp).getTime();
    const timeB = new Date(b.timestamp).getTime();
    if (isNaN(timeA) || isNaN(timeB)) return 0;
    return timeA - timeB;
  });
}

export type ReassessmentStatus =
  | "REASSESSMENT_CURRENT"
  | "REASSESSMENT_RECOMMENDED"
  | "REASSESSMENT_RECORDED"
  | "EVIDENCE_INSUFFICIENT"
  | "DEFERRED_DEPENDENCY"
  | "NOT_APPLICABLE";

export interface ReassessmentStateEvaluation {
  status: ReassessmentStatus;
  statusLabel: string;
  badgeClass: string;
  explanation: string;
  // Concept 1: Previous recorded outcome
  previousRecordedOutcome: {
    outcomeType: FollowUpOutcomeType | null;
    outcomeLabel: string;
    resolutionNote: string | null;
    resolvedAt: string | null;
    evidenceRunId: number | null;
    evidenceAsOfDate: string | null;
    residualUncertainty: string | null;
  } | null;
  // Concept 2: Current evidence state
  currentEvidenceState: {
    status: FindingEvidenceStatus;
    label: string;
    description: string;
    badgeClass: string;
    isDrifted: boolean;
    isSuperseded: boolean;
    currentRunId: number | null;
    currentAsOfDate: string | null;
  };
  // Concept 3: Reassessment status
  reassessmentStatus: {
    hasReassessmentRecorded: boolean;
    latestDisposition: ReassessmentDispositionType | null;
    latestRationale: string | null;
    latestReassessedAt: string | null;
    totalReassessments: number;
    isReassessmentNeeded: boolean;
  };
}

/**
 * Deterministically evaluates and separates the three distinct concepts per Section 2.B:
 * 1. Previous recorded outcome (what investor previously concluded).
 * 2. Current evidence state (whether recorded baseline is unchanged, superseded, or drifted).
 * 3. Reassessment status (whether investor has recorded a new review of the changed evidence).
 */
export function evaluateReassessmentState(
  issue: ResearchFollowUpIssue,
  provenance?: FindingProvenanceSummary | null
): ReassessmentStateEvaluation {
  // Concept 1: Previous recorded outcome
  const priorType = issue.priorOutcomeType || (issue.status === "RESOLVED" || issue.status === "DISMISSED" ? issue.outcomeType : null);
  const priorNote = issue.priorResolutionNote || issue.resolutionNote || null;
  const priorDate = issue.priorResolvedAt || issue.resolvedAt || null;
  const priorRunId = issue.priorEvidenceRunId || issue.evidenceRunId || null;
  const priorAsOf = issue.priorEvidenceAsOfDate || issue.evidenceAsOfDate || null;
  const priorUncertainty = issue.residualUncertainty || null;

  const previousRecordedOutcome = priorType || priorNote
    ? {
      outcomeType: priorType ?? null,
      outcomeLabel: priorType ? FOLLOW_UP_OUTCOMES_CONFIG[priorType]?.label || String(priorType) : (issue.status === "DISMISSED" ? "Dismissed" : "Resolved"),
      resolutionNote: priorNote,
      resolvedAt: priorDate,
      evidenceRunId: priorRunId,
      evidenceAsOfDate: priorAsOf,
      residualUncertainty: priorUncertainty,
    }
    : null;

  // Concept 2: Current evidence state
  const provStatus = provenance?.status || "CURRENT_CONTEXT";
  const isDrifted = provStatus === "HISTORICAL_EVIDENCE_CHANGED";
  const isSuperseded = provStatus === "HISTORICAL_RUN_SUPERSEDED";
  const currentEvidenceState = {
    status: provStatus,
    label: provenance?.statusBadgeLabel || "Current Context",
    description: provenance?.statusDescription || "Finding was documented against active calculation run.",
    badgeClass: provenance?.statusBadgeClass || "bg-emerald-500/15 border-emerald-500/40 text-emerald-300",
    isDrifted,
    isSuperseded,
    currentRunId: provenance?.currentRunId ?? null,
    currentAsOfDate: provenance?.currentAsOfDate ?? null,
  };

  // Concept 3: Reassessment status
  const totalReassessments = issue.reassessmentHistory?.length || (issue.reassessedAt ? 1 : 0);
  const latestDisposition = issue.reassessmentHistory?.[issue.reassessmentHistory.length - 1]?.disposition || issue.reassessmentDisposition || null;
  const latestRationale = issue.reassessmentHistory?.[issue.reassessmentHistory.length - 1]?.rationale || issue.reassessmentRationale || null;
  const latestReassessedAt = issue.reassessmentHistory?.[issue.reassessmentHistory.length - 1]?.reassessedAt || issue.reassessedAt || null;
  const hasReassessmentRecorded = Boolean(latestDisposition);

  const isEvidenceShifted = isDrifted || isSuperseded;
  const isReassessmentNeeded = Boolean(
    previousRecordedOutcome && isEvidenceShifted && (!hasReassessmentRecorded || (latestReassessedAt && priorDate && latestReassessedAt < priorDate))
  );

  let status: ReassessmentStatus;
  let statusLabel: string;
  let badgeClass: string;
  let explanation: string;

  if (!previousRecordedOutcome && (issue.status === "OPEN" || issue.status === "IN_PROGRESS")) {
    status = "NOT_APPLICABLE";
    statusLabel = "Initial Due Diligence";
    badgeClass = "bg-blue-500/15 border-blue-500/40 text-blue-300";
    explanation = "Inquiry is undergoing initial due-diligence review; no prior outcome recorded.";
  } else if (hasReassessmentRecorded) {
    if (latestDisposition === "EVIDENCE_INSUFFICIENT") {
      status = "EVIDENCE_INSUFFICIENT";
      statusLabel = "Evidence Insufficient";
      badgeClass = "bg-amber-500/15 border-amber-500/40 text-amber-300";
      explanation = "Changed evidence was examined, but available observations remain insufficient to reach a conclusive outcome.";
    } else if (latestDisposition === "DEFERRED_DEPENDENCY") {
      status = "DEFERRED_DEPENDENCY";
      statusLabel = "Reassessment Deferred";
      badgeClass = "bg-purple-500/15 border-purple-500/40 text-purple-300";
      explanation = "Reassessment was explicitly deferred pending subsequent disclosures or external dependencies.";
    } else {
      status = "REASSESSMENT_RECORDED";
      statusLabel = latestDisposition === "OUTCOME_REVISED" ? "Outcome Revised" : "Prior Outcome Retained";
      badgeClass = latestDisposition === "OUTCOME_REVISED"
        ? "bg-cyan-500/15 border-cyan-500/40 text-cyan-300"
        : "bg-emerald-500/15 border-emerald-500/40 text-emerald-300";
      explanation = `Reassessment recorded on ${latestReassessedAt ? new Date(latestReassessedAt).toLocaleDateString() : "record"}: ${latestDisposition === "OUTCOME_REVISED" ? "Outcome was updated based on changed evidence" : "Prior conclusion was verified as still applicable"}.`;
    }
  } else if (isReassessmentNeeded) {
    status = "REASSESSMENT_RECOMMENDED";
    statusLabel = "Reassessment Recommended";
    badgeClass = "bg-amber-500/15 border-amber-500/40 text-amber-300";
    explanation = "Underlying market evidence or calculation run has changed since the prior outcome was documented. Re-evaluating the finding is recommended.";
  } else {
    status = "REASSESSMENT_CURRENT";
    statusLabel = "Evidence Consistent";
    badgeClass = "bg-emerald-500/15 border-emerald-500/40 text-emerald-300";
    explanation = "Finding evidence baseline is consistent with active workspace calculations.";
  }

  return {
    status,
    statusLabel,
    badgeClass,
    explanation,
    previousRecordedOutcome,
    currentEvidenceState,
    reassessmentStatus: {
      hasReassessmentRecorded,
      latestDisposition,
      latestRationale,
      latestReassessedAt,
      totalReassessments,
      isReassessmentNeeded,
    },
  };
}

// =========================================================================
// Phase F29: Investigation Dependency Tracking and Blocker Resolution Continuity
// =========================================================================

export type DependencyCategory =
  | "DISCLOSURE_OR_FILING" // e.g. AMC monthly factsheet, portfolio disclosure, annual report, regulatory filing
  | "EVIDENCE_OR_NAV"      // e.g. Subsequent NAV cutoff, market cycle data, benchmark calculation series
  | "INVESTMENT_DECISION"   // e.g. Portfolio committee decision, mandate review, manager transition
  | "UNRESOLVED_QUESTION"  // e.g. Clarification from AMC investor relations, custodian, auditor
  | "OTHER_PREREQUISITE";  // e.g. General due diligence prerequisite

export type DependencyStatus =
  | "AWAITING"     // Actively blocked waiting for information/event
  | "ADDRESSED"    // Information received/prerequisite changed; awaiting investor evidence review
  | "DISCONTINUED"; // Prerequisite abandoned or rendered obsolete

export interface DependencyResolutionRecord {
  resolvedAt: string; // ISO timestamp
  receivedInfoOrChange: string; // What was received or what prerequisite changed
  evidenceReference?: string | null; // Document/source identifier, link, or evidence description
  evidenceRunId?: number | null; // Calculation run ID if applicable
  evidenceAsOfDate?: string | null; // Observation cutoff date if applicable
  resolutionRationale?: string | null; // Concise investor rationale
  unblockedStatus: "READY_TO_RESUME" | "STILL_BLOCKED_BY_OTHER" | "AWAITING_EVIDENCE_REVIEW";
}

export interface InvestigationDependency {
  id: string; // Unique stable ID e.g. "dep-1730000000-abcde"
  schemeId: number;
  schemeOptionId?: number | null;
  linkedIssueId: string;
  description: string; // Concise description of missing evidence, disclosure, decision, or prerequisite
  category: DependencyCategory;
  reasonWhyBlocked: string; // Reason the investigation cannot yet proceed
  expectedSource?: string | null; // Expected party or source (only when explicitly recorded by investor)
  evidenceReference?: string | null; // Associated evidence reference or source identifier if already available
  evidenceRunId?: number | null;
  evidenceAsOfDate?: string | null;
  status: DependencyStatus;
  recordedAt: string; // ISO timestamp
  updatedAt: string; // ISO timestamp

  // Blocker resolution records
  addressedAt?: string | null; // ISO timestamp when marked addressed
  receivedInfoOrChange?: string | null; // What was received or changed
  resolutionRationale?: string | null; // Concise investor resolution rationale
  resolutionEvidenceReference?: string | null; // Reference to received disclosure/source
  resolutionRunId?: number | null;
  resolutionAsOfDate?: string | null;
  resolutionHistory?: DependencyResolutionRecord[];
}

export const DEPENDENCY_CATEGORY_CONFIG: Record<
  DependencyCategory,
  { key: DependencyCategory; label: string; shortLabel: string; badgeClass: string; description: string }
> = {
  DISCLOSURE_OR_FILING: {
    key: "DISCLOSURE_OR_FILING",
    label: "Disclosure or Regulatory Filing",
    shortLabel: "Disclosure/Filing",
    badgeClass: "bg-sky-500/15 text-sky-300 border-sky-500/40",
    description: "Awaiting AMC monthly factsheet, portfolio disclosure, annual report, or regulatory filing.",
  },
  EVIDENCE_OR_NAV: {
    key: "EVIDENCE_OR_NAV",
    label: "Market Evidence or NAV Cutoff",
    shortLabel: "Evidence/NAV",
    badgeClass: "bg-amber-500/15 text-amber-300 border-amber-500/40",
    description: "Awaiting subsequent NAV cutoff date, market cycle data, or index/benchmark calculation series.",
  },
  INVESTMENT_DECISION: {
    key: "INVESTMENT_DECISION",
    label: "Investment or Mandate Decision",
    shortLabel: "Decision",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
    description: "Awaiting portfolio committee decision, mandate review, manager transition, or allocation ruling.",
  },
  UNRESOLVED_QUESTION: {
    key: "UNRESOLVED_QUESTION",
    label: "External Clarification or Inquiry",
    shortLabel: "Clarification",
    badgeClass: "bg-yellow-500/15 text-yellow-300 border-yellow-500/40",
    description: "Awaiting AMC investor relations response, custodian query, or auditor verification.",
  },
  OTHER_PREREQUISITE: {
    key: "OTHER_PREREQUISITE",
    label: "Other Diligence Prerequisite",
    shortLabel: "Prerequisite",
    badgeClass: "bg-surface-raised text-text-secondary border-border",
    description: "General due-diligence prerequisite or dependency documented by investor.",
  },
};

export const DEPENDENCY_STATUS_CONFIG: Record<
  DependencyStatus,
  { key: DependencyStatus; label: string; shortLabel: string; badgeClass: string; description: string }
> = {
  AWAITING: {
    key: "AWAITING",
    label: "Awaiting Information / Prerequisite",
    shortLabel: "Awaiting",
    badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
    description: "Prerequisite is pending; investigation cannot proceed until received.",
  },
  ADDRESSED: {
    key: "ADDRESSED",
    label: "Addressed (Awaiting Evidence Review)",
    shortLabel: "Addressed",
    badgeClass: "bg-cyan-500/15 text-cyan-300 border-cyan-500/40",
    description: "Prerequisite or disclosure received; ready for investor review and reassessment.",
  },
  DISCONTINUED: {
    key: "DISCONTINUED",
    label: "Discontinued (No Longer Blocking)",
    shortLabel: "Discontinued",
    badgeClass: "bg-surface-raised text-text-muted border-border/70",
    description: "Prerequisite was rendered obsolete or investor decided not to condition progress on it.",
  },
};

export type IssueDependencyBlockingState =
  | "BLOCKED_AWAITING"
  | "ALL_ADDRESSED_REVIEW_NEEDED"
  | "READY_TO_RESUME"
  | "NO_DEPENDENCIES"
  | "UNCERTAIN_BLOCKING";

export interface IssueDependencyEvaluation {
  state: IssueDependencyBlockingState;
  stateLabel: string;
  badgeClass: string;
  description: string;
  totalDependencies: number;
  awaitingCount: number;
  addressedCount: number;
  discontinuedCount: number;
  isFullyUnblocked: boolean;
  hasOutstandingBlockers: boolean;
  activeBlockers: InvestigationDependency[];
  addressedBlockers: InvestigationDependency[];
  suggestedAction: string;
}

/**
 * Deterministically evaluates the concrete blocking state of an issue across multiple dependencies.
 * Prevents false completion when one dependency is addressed but others remain unresolved.
 */
export function evaluateIssueDependencies(
  issue: ResearchFollowUpIssue
): IssueDependencyEvaluation {
  const deps = issue.dependencies || [];
  const totalDependencies = deps.length;

  if (totalDependencies === 0) {
    if (issue.status === "WAITING") {
      return {
        state: "UNCERTAIN_BLOCKING",
        stateLabel: "Waiting (Unstructured Dependency)",
        badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
        description: "Investigation was marked WAITING, but specific structured dependencies are unrecorded. Record explicit dependency or update lifecycle.",
        totalDependencies: 0,
        awaitingCount: 0,
        addressedCount: 0,
        discontinuedCount: 0,
        isFullyUnblocked: false,
        hasOutstandingBlockers: true,
        activeBlockers: [],
        addressedBlockers: [],
        suggestedAction: "Record structured dependency or resume investigation in workspace.",
      };
    }
    return {
      state: "NO_DEPENDENCIES",
      stateLabel: "No Dependencies Recorded",
      badgeClass: "bg-surface-raised text-text-muted border-border",
      description: "No specific external prerequisites or blocking disclosures are currently documented.",
      totalDependencies: 0,
      awaitingCount: 0,
      addressedCount: 0,
      discontinuedCount: 0,
      isFullyUnblocked: true,
      hasOutstandingBlockers: false,
      activeBlockers: [],
      addressedBlockers: [],
      suggestedAction: "Proceed with standard investigation workflow.",
    };
  }

  const activeBlockers = deps.filter((d) => d.status === "AWAITING");
  const addressedBlockers = deps.filter((d) => d.status === "ADDRESSED");
  const discontinuedCount = deps.filter((d) => d.status === "DISCONTINUED").length;
  const awaitingCount = activeBlockers.length;
  const addressedCount = addressedBlockers.length;

  if (awaitingCount > 0) {
    const isMultiple = awaitingCount > 1;
    const partiallyAddressed = addressedCount > 0;
    return {
      state: "BLOCKED_AWAITING",
      stateLabel: partiallyAddressed
        ? `Blocked (${awaitingCount} Awaiting, ${addressedCount} Addressed)`
        : `Blocked (${awaitingCount} Prerequisite${isMultiple ? "s" : ""})`,
      badgeClass: "bg-purple-500/15 text-purple-300 border-purple-500/40",
      description: partiallyAddressed
        ? `Investigation remains blocked by ${awaitingCount} unresolved prerequisite(s), while ${addressedCount} prerequisite(s) have been addressed.`
        : `Investigation is paused awaiting ${awaitingCount} external prerequisite(s) before work can proceed.`,
      totalDependencies,
      awaitingCount,
      addressedCount,
      discontinuedCount,
      isFullyUnblocked: false,
      hasOutstandingBlockers: true,
      activeBlockers,
      addressedBlockers,
      suggestedAction: `Awaiting: ${activeBlockers[0].description}. Record information when received.`,
    };
  }

  if (addressedCount > 0) {
    return {
      state: "ALL_ADDRESSED_REVIEW_NEEDED",
      stateLabel: "All Blockers Addressed (Review Needed)",
      badgeClass: "bg-cyan-500/15 text-cyan-300 border-cyan-500/40",
      description: `All ${addressedCount} recorded prerequisite(s) have been addressed. Received information is ready for investor evidence review or reassessment.`,
      totalDependencies,
      awaitingCount: 0,
      addressedCount,
      discontinuedCount,
      isFullyUnblocked: true,
      hasOutstandingBlockers: false,
      activeBlockers: [],
      addressedBlockers,
      suggestedAction: "Review received disclosure/evidence and resume investigation or begin reassessment.",
    };
  }

  return {
    state: "READY_TO_RESUME",
    stateLabel: "Dependencies Discontinued",
    badgeClass: "bg-surface-raised text-text-secondary border-border",
    description: "All recorded dependencies have been discontinued. Investigation can resume.",
    totalDependencies,
    awaitingCount: 0,
    addressedCount: 0,
    discontinuedCount,
    isFullyUnblocked: true,
    hasOutstandingBlockers: false,
    activeBlockers: [],
    addressedBlockers: [],
    suggestedAction: "Resume investigation in active research workspace.",
  };
}

/**
 * Records a new structured dependency for an investigation issue.
 * If issue was not in WAITING status, marks it WAITING to prevent false progress.
 */
export function recordInvestigationDependency(
  issueId: string,
  depParams: {
    description: string;
    category: DependencyCategory;
    reasonWhyBlocked: string;
    expectedSource?: string | null;
    evidenceReference?: string | null;
    evidenceRunId?: number | null;
    evidenceAsOfDate?: string | null;
    setIssueWaiting?: boolean;
  },
  userId?: string | null
): ResearchFollowUpIssue | null {
  const all = loadAllFollowUpIssues(userId);
  const existing = all.find((i) => i.id === issueId);
  if (!existing) return null;

  const now = new Date().toISOString();
  const depId = `dep-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;

  const newDep: InvestigationDependency = {
    id: depId,
    schemeId: existing.schemeId,
    schemeOptionId: existing.schemeOptionId,
    linkedIssueId: existing.id,
    description: depParams.description.trim(),
    category: depParams.category,
    reasonWhyBlocked: depParams.reasonWhyBlocked.trim(),
    expectedSource: depParams.expectedSource?.trim() || null,
    evidenceReference: depParams.evidenceReference?.trim() || null,
    evidenceRunId: depParams.evidenceRunId ?? existing.evidenceRunId ?? null,
    evidenceAsOfDate: depParams.evidenceAsOfDate ?? existing.evidenceAsOfDate ?? null,
    status: "AWAITING",
    recordedAt: now,
    updatedAt: now,
  };

  const updatedDeps = [...(existing.dependencies || []), newDep];
  const shouldSetWaiting = depParams.setIssueWaiting !== false;
  const newStatus = shouldSetWaiting ? "WAITING" : existing.status;

  const updatedIssue: ResearchFollowUpIssue = {
    ...existing,
    status: newStatus,
    dependencies: updatedDeps,
    updatedAt: now,
  };

  saveFollowUpIssue(updatedIssue, userId);
  return updatedIssue;
}

/**
 * Explicitly records that a dependency has been addressed without automatically
 * resolving the investigation or validating financial math.
 * Checks whether other dependencies remain before transitioning status.
 */
export function resolveInvestigationDependency(
  params: {
    issueId: string;
    dependencyId: string;
    receivedInfoOrChange: string;
    resolutionRationale?: string | null;
    resolutionEvidenceReference?: string | null;
    resolutionRunId?: number | null;
    resolutionAsOfDate?: string | null;
    nextIssueAction: "RESUME_IN_PROGRESS" | "CONTINUE_WAITING" | "KEEP_CURRENT_STATUS";
    userId?: string | null;
  }
): ResearchFollowUpIssue | null {
  const all = loadAllFollowUpIssues(params.userId);
  const existing = all.find((i) => i.id === params.issueId);
  if (!existing) return null;

  const now = new Date().toISOString();
  const deps = existing.dependencies || [];
  const depIndex = deps.findIndex((d) => d.id === params.dependencyId);
  if (depIndex < 0) return null;

  const targetDep = deps[depIndex];
  const resolutionRecord: DependencyResolutionRecord = {
    resolvedAt: now,
    receivedInfoOrChange: params.receivedInfoOrChange.trim(),
    evidenceReference: params.resolutionEvidenceReference?.trim() || null,
    evidenceRunId: params.resolutionRunId ?? null,
    evidenceAsOfDate: params.resolutionAsOfDate ?? null,
    resolutionRationale: params.resolutionRationale?.trim() || null,
    unblockedStatus:
      params.nextIssueAction === "RESUME_IN_PROGRESS"
        ? "READY_TO_RESUME"
        : params.nextIssueAction === "CONTINUE_WAITING"
          ? "STILL_BLOCKED_BY_OTHER"
          : "AWAITING_EVIDENCE_REVIEW",
  };

  const updatedDep: InvestigationDependency = {
    ...targetDep,
    status: "ADDRESSED",
    addressedAt: now,
    receivedInfoOrChange: params.receivedInfoOrChange.trim(),
    resolutionRationale: params.resolutionRationale?.trim() || null,
    resolutionEvidenceReference: params.resolutionEvidenceReference?.trim() || null,
    resolutionRunId: params.resolutionRunId ?? null,
    resolutionAsOfDate: params.resolutionAsOfDate ?? null,
    resolutionHistory: [...(targetDep.resolutionHistory || []), resolutionRecord],
    updatedAt: now,
  };

  const nextDeps = [...deps];
  nextDeps[depIndex] = updatedDep;

  // Determine whether any OTHER dependencies remain in AWAITING status
  const otherAwaiting = nextDeps.some((d, idx) => idx !== depIndex && d.status === "AWAITING");

  let nextStatus = existing.status;
  if (otherAwaiting) {
    // If other blockers remain, the issue MUST stay WAITING
    nextStatus = "WAITING";
  } else if (params.nextIssueAction === "RESUME_IN_PROGRESS" && existing.status === "WAITING") {
    // Only transition to IN_PROGRESS if NO other blockers remain and user chose to resume
    nextStatus = "IN_PROGRESS";
  }

  const updatedIssue: ResearchFollowUpIssue = {
    ...existing,
    status: nextStatus,
    dependencies: nextDeps,
    updatedAt: now,
  };

  saveFollowUpIssue(updatedIssue, params.userId);
  return updatedIssue;
}

/**
 * Discontinues a dependency when no longer relevant.
 */
export function discontinueInvestigationDependency(
  issueId: string,
  dependencyId: string,
  discontinueReason: string,
  userId?: string | null
): ResearchFollowUpIssue | null {
  const all = loadAllFollowUpIssues(userId);
  const existing = all.find((i) => i.id === issueId);
  if (!existing) return null;

  const now = new Date().toISOString();
  const deps = existing.dependencies || [];
  const depIndex = deps.findIndex((d) => d.id === dependencyId);
  if (depIndex < 0) return null;

  const targetDep = deps[depIndex];
  const updatedDep: InvestigationDependency = {
    ...targetDep,
    status: "DISCONTINUED",
    resolutionRationale: discontinueReason.trim() || "Prerequisite discontinued by investor",
    updatedAt: now,
  };

  const nextDeps = [...deps];
  nextDeps[depIndex] = updatedDep;

  // Check remaining blockers
  const remainingBlockers = nextDeps.some((d) => d.status === "AWAITING");
  const nextStatus = (!remainingBlockers && existing.status === "WAITING") ? "IN_PROGRESS" : existing.status;

  const updatedIssue: ResearchFollowUpIssue = {
    ...existing,
    status: nextStatus,
    dependencies: nextDeps,
    updatedAt: now,
  };

  saveFollowUpIssue(updatedIssue, userId);
  return updatedIssue;
}

