/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F23: Investigation Action Queue and Evidence-Change Triage
 *
 * Implements deterministic prioritization and triage of active follow-up investigations
 * based on explicit workflow states, overdue diligence dates, reopened inquiry histories,
 * evidence drift, and provenance completeness.
 *
 * CRITICAL EPISTEMIC GUARDRAILS:
 * 1. WORKFLOW ATTENTION ONLY: This queue represents investigative due-diligence order,
 *    NEVER financial risk, fund quality scores, or investment recommendations.
 * 2. DETERMINISTIC ORDERING: Sorting is strictly rule-based (Overdue > Reopened > Drift > Active > Waiting > Provenance).
 *    No AI-generated ranks or opaque composite weights.
 * 3. NO MUTATION: Viewing, opening, or filtering queued items NEVER mutates issue status.
 * 4. HISTORICAL CONTEXT PRESERVED: Resolved and dismissed issues are excluded from the active queue
 *    by default, but remain fully accessible in the broader workspace.
 * 5. EXACT IDENTITY: Exact (schemeId, schemeOptionId) tuples and plan/option designations are strictly preserved.
 * 6. NO SILENT SUBSTITUTION: If a fund option is not in the active watchlist, its record is preserved
 *    without redirecting to a different share class.
 */

import {
  ResearchFollowUpIssue,
  FindingProvenanceSummary,
  evaluateFindingEvidenceTraceability,
  isTargetDatePassed,
  FOLLOW_UP_CATEGORIES_CONFIG,
  FOLLOW_UP_STATUS_CONFIG,
  FOLLOW_UP_OUTCOMES_CONFIG,
  evaluateInvestigationClosureState,
  InvestigationClosureResolution,
  evaluateIssueDependencies,
  IssueDependencyEvaluation,
  InvestigationDependency,
} from "@/lib/utils/researchFollowUp";
import { WatchlistResponse } from "@/lib/api/investor";
import { getEvidenceSnapshot } from "@/lib/utils/evidenceChange";

export type QueueInclusionReasonCode =
  | "OVERDUE_TARGET_DATE"
  | "REOPENED_INQUIRY"
  | "DEPENDENCY_ADDRESSED_REVIEW_NEEDED"
  | "EVIDENCE_DRIFT"
  | "SUPERSEDED_RUN"
  | "OPEN_IN_PROGRESS"
  | "WAITING_DEPENDENCY"
  | "RESIDUAL_UNCERTAINTY"
  | "PROVENANCE_INCOMPLETE";

export interface QueueInclusionReason {
  code: QueueInclusionReasonCode;
  label: string;
  shortLabel: string;
  badgeClass: string;
  explanation: string;
  priorityTier: number; // 1 (highest workflow attention) to 6 (lowest)
}

export interface QueuedInvestigationItem {
  issue: ResearchFollowUpIssue;
  schemeId: number;
  schemeOptionId: number | null | undefined;
  schemeName: string;
  planType: string;
  optionType: string;
  amfiCode: string | null;
  isin: string | null;

  // Primary inclusion reason and complete list of reasons
  primaryReason: QueueInclusionReason;
  allReasons: QueueInclusionReason[];

  // Workflow attention tier (1 to 6)
  priorityTier: number;

  // Provenance & Evidence Comparison context
  provenance: FindingProvenanceSummary;
  activeNavDate: string | null;
  activeRunId: number | null;
  hasActiveContext: boolean;
  isCutoffDrifted: boolean;
  isRunSuperseded: boolean;
  isPreservedWithoutWatchlist: boolean;

  // Explicit explanations & next actions
  whyAttentionNeeded: string;
  suggestedNextStep: string;
  isWaiting: boolean;
  isOverdue: boolean;
  overdueDate: string | null;
  recordedNextAction: string;
  closureState: InvestigationClosureResolution;
  dependencyEvaluation: IssueDependencyEvaluation;
}

export interface InvestigationActionQueueSummary {
  totalActionableCount: number;
  overdueCount: number;
  reopenedCount: number;
  evidenceDriftCount: number;
  openInProgressCount: number;
  waitingCount: number;
  uncertaintyOrProvenanceCount: number;
  dependencyAddressedCount: number;
}

/**
 * Evaluates whether an issue meets one or more actionable triage conditions.
 * Returns an array of explicit reasons ordered by workflow priority.
 */
export function evaluateQueueInclusionReasons(
  issue: ResearchFollowUpIssue,
  activeNavDate: string | null,
  activeRunId: number | null,
  prov: FindingProvenanceSummary,
  isCutoffDrifted: boolean,
  isRunSuperseded: boolean
): QueueInclusionReason[] {
  const reasons: QueueInclusionReason[] = [];

  // Exclude resolved and dismissed records from active triage by default
  const isUnresolved =
    issue.status === "OPEN" || issue.status === "IN_PROGRESS" || issue.status === "WAITING";
  if (!isUnresolved) {
    return reasons;
  }

  const depEval = evaluateIssueDependencies(issue);

  // 1. Tier 1: Explicitly Overdue Target Date
  if (isTargetDatePassed(issue.targetDate)) {
    reasons.push({
      code: "OVERDUE_TARGET_DATE",
      label: "Target Date Overdue",
      shortLabel: "Overdue",
      badgeClass: "bg-rose-500/20 text-rose-300 border-rose-500/40",
      explanation: `Target diligence date (${issue.targetDate}) has elapsed without documented resolution.`,
      priorityTier: 1,
    });
  }

  // 2. Tier 2: Reopened Inquiry with Preserved Prior Outcome
  if (Boolean(issue.priorResolutionNote || issue.reopenedAt)) {
    reasons.push({
      code: "REOPENED_INQUIRY",
      label: "Reopened Inquiry",
      shortLabel: "Reopened",
      badgeClass: "bg-purple-500/20 text-purple-300 border-purple-500/40",
      explanation:
        "Previously resolved inquiry was reopened; preserved prior outcome requires renewed verification against active facts.",
      priorityTier: 2,
    });
  }

  // Phase F29: Blocker Addressed / Awaiting Evidence Review (High workflow priority, Tier 3)
  if (depEval.state === "ALL_ADDRESSED_REVIEW_NEEDED") {
    reasons.push({
      code: "DEPENDENCY_ADDRESSED_REVIEW_NEEDED",
      label: "Blocker Addressed (Evidence Review Needed)",
      shortLabel: "Blocker Addressed",
      badgeClass: "bg-cyan-500/20 text-cyan-300 border-cyan-500/40",
      explanation: `All ${depEval.addressedCount} recorded prerequisite(s) have been addressed. Received evidence requires investor review to resume analysis.`,
      priorityTier: 3,
    });
  }

  // 3. Tier 3: Evidence Context Drift or Superseded Calculation Run
  const isEvidenceDrift =
    prov.status === "HISTORICAL_EVIDENCE_CHANGED" ||
    isCutoffDrifted ||
    issue.category === "EVIDENCE_LIMITATION" ||
    issue.briefSection === "F" ||
    issue.title.toLowerCase().includes("drift") ||
    issue.title.toLowerCase().includes("reassessment");

  if (isEvidenceDrift) {
    reasons.push({
      code: "EVIDENCE_DRIFT",
      label: "Evidence Context Changed",
      shortLabel: "Evidence Drift",
      badgeClass: "bg-amber-500/20 text-amber-300 border-amber-500/40",
      explanation: isCutoffDrifted
        ? `Observation cutoff drift detected: finding recorded as of ${issue.evidenceAsOfDate || "unknown"}; active workspace has observations through ${activeNavDate}.`
        : "Observation differences detected since this finding was recorded. Verification against active data advised.",
      priorityTier: 3,
    });
  } else if (prov.status === "HISTORICAL_RUN_SUPERSEDED" || isRunSuperseded) {
    reasons.push({
      code: "SUPERSEDED_RUN",
      label: "Calculation Run Superseded",
      shortLabel: "Run Superseded",
      badgeClass: "bg-blue-500/20 text-blue-300 border-blue-500/40",
      explanation: `Subsequent calculation run #${activeRunId || "newer"} is available in active workspace compared to recorded run #${issue.evidenceRunId}.`,
      priorityTier: 3,
    });
  }

  // 4. Tier 4: Active Inquiries (Open or In Progress)
  if (issue.status === "OPEN" || issue.status === "IN_PROGRESS") {
    reasons.push({
      code: "OPEN_IN_PROGRESS",
      label: issue.status === "OPEN" ? "Open Diligence Inquiry" : "In-Progress Investigation",
      shortLabel: issue.status === "OPEN" ? "Open" : "In Progress",
      badgeClass: "bg-accent/20 text-accent border-accent/40",
      explanation:
        issue.status === "OPEN"
          ? "Unresolved inquiry awaiting due-diligence review and corroborating observations."
          : "Active investigation in progress; evidence collection and synthesis pending.",
      priorityTier: 4,
    });
  }

  // 5. Tier 5: Inquiries Waiting on External Dependencies (Phase F29 Enhanced)
  if (issue.status === "WAITING") {
    if (depEval.hasOutstandingBlockers) {
      const blockerList = depEval.activeBlockers.length > 0
        ? depEval.activeBlockers.map((b) => `"${b.description}"`).join("; ")
        : (issue.questionOrReason || "External prerequisite or market observation pending.");
      const sourceSuffix = depEval.activeBlockers[0]?.expectedSource
        ? ` Expected from: ${depEval.activeBlockers[0].expectedSource}.`
        : "";
      reasons.push({
        code: "WAITING_DEPENDENCY",
        label: depEval.addressedCount > 0 ? "Partially Blocked on Dependencies" : "Waiting on External Dependency",
        shortLabel: depEval.addressedCount > 0 ? "Partially Blocked" : "Waiting",
        badgeClass: "bg-indigo-500/20 text-indigo-300 border-indigo-500/40",
        explanation: `Investigation paused waiting on prerequisite: ${blockerList}.${sourceSuffix} Progress is conditioned on external information and cannot be resolved through calculation alone.`,
        priorityTier: 5,
      });
    }
  }

  // 6. Tier 6: Missing Provenance or Residual Uncertainty
  if (issue.residualUncertainty && issue.residualUncertainty.trim().length > 0) {
    reasons.push({
      code: "RESIDUAL_UNCERTAINTY",
      label: "Residual Uncertainty Documented",
      shortLabel: "Uncertainty",
      badgeClass: "bg-yellow-500/20 text-yellow-300 border-yellow-500/40",
      explanation: `Documented empirical uncertainty: "${issue.residualUncertainty}".`,
      priorityTier: 6,
    });
  }

  if (prov.status === "PROVENANCE_NOT_RECORDED") {
    reasons.push({
      code: "PROVENANCE_INCOMPLETE",
      label: "Provenance Unrecorded",
      shortLabel: "No Provenance",
      badgeClass: "bg-surface-raised text-text-muted border-border",
      explanation:
        "Point-in-time calculation run ID and observation cutoff were not recorded when this finding was logged.",
      priorityTier: 6,
    });
  }

  // Sort matched reasons by priority tier ascending
  return reasons.sort((a, b) => a.priorityTier - b.priorityTier);
}

/**
 * Resolves a practical, actionable next step for a queued investigation.
 */
export function resolveSuggestedNextStep(
  issue: ResearchFollowUpIssue,
  reasons: QueueInclusionReason[],
  isPreservedWithoutWatchlist: boolean
): string {
  const depEval = evaluateIssueDependencies(issue);

  if (depEval.state === "ALL_ADDRESSED_REVIEW_NEEDED") {
    const firstAddressed = depEval.addressedBlockers[0];
    return `Prerequisite '${firstAddressed?.description || "dependency"}' addressed. Review received disclosure/evidence in Section ${issue.briefSection || "A"} and resume investigation.`;
  }

  if (issue.status === "WAITING") {
    if (depEval.activeBlockers.length > 0) {
      const firstActive = depEval.activeBlockers[0];
      const sourceStr = firstActive.expectedSource ? ` (expected from ${firstActive.expectedSource})` : "";
      return `Awaiting prerequisite: "${firstActive.description}"${sourceStr}. Verify external receipt and record blocker resolution when received.`;
    }
    if (issue.nextAction && issue.nextAction.trim().length > 0) {
      return `Waiting on dependency: ${issue.nextAction.trim()}`;
    }
    return `Waiting on external dependency or reporting cycle: ${issue.questionOrReason || "External filing or market observation pending."}`;
  }

  if (issue.nextAction && issue.nextAction.trim().length > 0) {
    return issue.nextAction.trim();
  }

  if (isPreservedWithoutWatchlist) {
    return "Re-add fund option to active watchlist to resume interactive research brief.";
  }

  const primaryCode = reasons[0]?.code;
  switch (primaryCode) {
    case "OVERDUE_TARGET_DATE":
      return `Target date passed (${issue.targetDate}). Review Section ${issue.briefSection || "A"} in Research Brief and record outcome or update target date.`;
    case "REOPENED_INQUIRY":
      return `Inspect preserved prior outcome note and re-examine Section ${issue.briefSection || "A"} to verify current facts.`;
    case "DEPENDENCY_ADDRESSED_REVIEW_NEEDED":
      return `Prerequisite addressed. Inspect received evidence in Section ${issue.briefSection || "A"} and proceed with due diligence.`;
    case "EVIDENCE_DRIFT":
      return `Inspect Section F (History & Drift) to compare recorded observations with active market data.`;
    case "SUPERSEDED_RUN":
      return `Verify if calculations in Section ${issue.briefSection || "B"} changed under subsequent calculation run.`;
    case "WAITING_DEPENDENCY":
      return `Verify if pending factsheet or subsequent NAV cutoff has been published.`;
    case "PROVENANCE_INCOMPLETE":
      return `Update issue to associate authoritative calculation run ID or observation cutoff date.`;
    case "RESIDUAL_UNCERTAINTY":
      return `Examine additional portfolio disclosures or factsheet notes to corroborate residual uncertainty.`;
    default:
      return `Resume investigation in Section ${issue.briefSection || "A"} of the Fund Research Brief.`;
  }
}

/**
 * Deterministically sorts queued investigation items by:
 * 1. Priority Tier (1 to 6)
 * 2. Overdue target date (earliest first)
 * 3. Reopened timestamp (most recently reopened first)
 * 4. General target date (earliest first)
 * 5. Last updated date (newest first)
 * 6. Stable tie-breaker on issue ID
 */
export function sortQueuedItems(items: QueuedInvestigationItem[]): QueuedInvestigationItem[] {
  return [...items].sort((a, b) => {
    // 1. Primary sort: Workflow Priority Tier (1 is highest urgency)
    if (a.priorityTier !== b.priorityTier) {
      return a.priorityTier - b.priorityTier;
    }

    // 2. Secondary sort within Tier 1 (Overdue): earliest target date first (most overdue first)
    if (a.priorityTier === 1 && a.issue.targetDate && b.issue.targetDate) {
      if (a.issue.targetDate !== b.issue.targetDate) {
        return a.issue.targetDate.localeCompare(b.issue.targetDate);
      }
    }

    // 3. Secondary sort within Tier 2 (Reopened): most recently reopened first
    if (a.priorityTier === 2 && a.issue.reopenedAt && b.issue.reopenedAt) {
      if (a.issue.reopenedAt !== b.issue.reopenedAt) {
        return b.issue.reopenedAt.localeCompare(a.issue.reopenedAt);
      }
    }

    // 4. Secondary sort within other tiers: target date first if present
    if (a.issue.targetDate && b.issue.targetDate && a.issue.targetDate !== b.issue.targetDate) {
      return a.issue.targetDate.localeCompare(b.issue.targetDate);
    }
    if (a.issue.targetDate && !b.issue.targetDate) return -1;
    if (!a.issue.targetDate && b.issue.targetDate) return 1;

    // 5. Secondary sort: newest updated first
    if (a.issue.updatedAt !== b.issue.updatedAt) {
      return b.issue.updatedAt.localeCompare(a.issue.updatedAt);
    }

    // 6. Deterministic tie-breaker: issue ID
    return a.issue.id.localeCompare(b.issue.id);
  });
}

/**
 * Builds the consolidated Investigation Action Queue and summary statistics
 * from research follow-up issues and available watchlist/snapshot context.
 */
export function buildInvestigationActionQueue(
  issues: ResearchFollowUpIssue[],
  savedItems?: WatchlistResponse[]
): {
  queuedItems: QueuedInvestigationItem[];
  summary: InvestigationActionQueueSummary;
} {
  const queuedItems: QueuedInvestigationItem[] = [];

  let overdueCount = 0;
  let reopenedCount = 0;
  let evidenceDriftCount = 0;
  let openInProgressCount = 0;
  let waitingCount = 0;
  let uncertaintyOrProvenanceCount = 0;
  let dependencyAddressedCount = 0;

  for (const issue of issues) {
    // Look up active workspace context for the exact fund option
    const matchingWatchlistItem = savedItems?.find((item) => {
      if (issue.schemeOptionId !== undefined && issue.schemeOptionId !== null) {
        return item.schemeOptionId === issue.schemeOptionId;
      }
      return item.schemeId === issue.schemeId;
    });

    const isPreservedWithoutWatchlist =
      Boolean(savedItems && savedItems.length > 0) && !matchingWatchlistItem;

    const snapshot = getEvidenceSnapshot(issue.schemeId, issue.schemeOptionId);

    const activeNavDate = matchingWatchlistItem?.latestNavDate || snapshot?.asOfDate || null;
    const activeRunId = snapshot?.calculationRunId ?? null;
    const hasActiveContext = Boolean(activeNavDate || activeRunId !== null);

    const isCutoffDrifted = Boolean(
      issue.evidenceAsOfDate &&
        activeNavDate &&
        issue.evidenceAsOfDate !== activeNavDate
    );

    const isRunSuperseded = Boolean(
      issue.evidenceRunId &&
        activeRunId !== null &&
        issue.evidenceRunId !== activeRunId
    );

    const prov = evaluateFindingEvidenceTraceability(
      issue,
      activeRunId,
      activeNavDate,
      isCutoffDrifted ? 1 : 0
    );

    const reasons = evaluateQueueInclusionReasons(
      issue,
      activeNavDate,
      activeRunId,
      prov,
      isCutoffDrifted,
      isRunSuperseded
    );

    if (reasons.length > 0) {
      const primaryReason = reasons[0];
      const priorityTier = primaryReason.priorityTier;

      // Track summary statistics
      if (reasons.some((r) => r.code === "OVERDUE_TARGET_DATE")) overdueCount++;
      if (reasons.some((r) => r.code === "REOPENED_INQUIRY")) reopenedCount++;
      if (reasons.some((r) => r.code === "DEPENDENCY_ADDRESSED_REVIEW_NEEDED")) dependencyAddressedCount++;
      if (reasons.some((r) => r.code === "EVIDENCE_DRIFT" || r.code === "SUPERSEDED_RUN")) evidenceDriftCount++;
      if (issue.status === "OPEN" || issue.status === "IN_PROGRESS") openInProgressCount++;
      if (issue.status === "WAITING") waitingCount++;
      if (
        reasons.some(
          (r) => r.code === "RESIDUAL_UNCERTAINTY" || r.code === "PROVENANCE_INCOMPLETE"
        )
      ) {
        uncertaintyOrProvenanceCount++;
      }

      const suggestedNextStep = resolveSuggestedNextStep(issue, reasons, isPreservedWithoutWatchlist);
      const whyAttentionNeeded = reasons.map((r) => r.explanation).join(" ");
      const depEval = evaluateIssueDependencies(issue);

      queuedItems.push({
        issue,
        schemeId: issue.schemeId,
        schemeOptionId: issue.schemeOptionId,
        schemeName: issue.schemeName,
        planType: issue.planType || "DIRECT",
        optionType: issue.optionType || "GROWTH",
        amfiCode: issue.amfiCode || null,
        isin: issue.isin || null,
        primaryReason,
        allReasons: reasons,
        priorityTier,
        provenance: prov,
        activeNavDate,
        activeRunId,
        hasActiveContext,
        isCutoffDrifted,
        isRunSuperseded,
        isPreservedWithoutWatchlist,
        whyAttentionNeeded,
        suggestedNextStep,
        isWaiting: issue.status === "WAITING",
        isOverdue: isTargetDatePassed(issue.targetDate),
        overdueDate: issue.targetDate || null,
        recordedNextAction: issue.nextAction || "",
        closureState: evaluateInvestigationClosureState(issue),
        dependencyEvaluation: depEval,
      });
    }
  }

  const sortedQueuedItems = sortQueuedItems(queuedItems);

  const summary: InvestigationActionQueueSummary = {
    totalActionableCount: sortedQueuedItems.length,
    overdueCount,
    reopenedCount,
    evidenceDriftCount,
    openInProgressCount,
    waitingCount,
    uncertaintyOrProvenanceCount,
    dependencyAddressedCount,
  };

  return {
    queuedItems: sortedQueuedItems,
    summary,
  };
}
