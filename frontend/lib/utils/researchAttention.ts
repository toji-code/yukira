/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F8: Investor Research Priority and Attention Workspace
 *
 * Implements deterministic research inquiry triage, explainable attention
 * precedence ordering, and descriptive research completion context based
 * on observable workflow states, evidence changes, and due-diligence progress.
 *
 * Epistemic Guardrail: This module NEVER computes investment scores,
 * fund quality rankings, performance forecasts, or buy/sell recommendations.
 * Priority exclusively reflects outstanding research tasks and evidence timeliness.
 */

import { WatchlistResponse, DueDiligenceStatus } from "@/lib/api/investor";
import { EvidenceChangeSummary } from "@/lib/utils/evidenceChange";
import { PortfolioContextResolution } from "@/lib/utils/portfolioContext";

export type AttentionReasonCode =
  | "NEEDS_FOLLOW_UP"              // Explicitly marked for follow-up by investor
  | "REVIEW_NEEDED"                // Reviewed, but underlying evidence changed (reconsideration check)
  | "STALE_EVIDENCE"               // Observations terminate at historical pilot cutoff (2024-01-15)
  | "UNREVIEWED_INVESTIGATION"     // TO_INVESTIGATE, awaiting initial diligence review
  | "ACTIVE_NEXT_STEP_PENDING"     // IN_PROGRESS with an explicit investor-authored next action
  | "MISSING_QUESTION"             // No investigation question formulated
  | "MISSING_NEXT_STEP"            // No next investigation step specified
  | "MISSING_THESIS"               // No investor-authored working thesis documented
  | "EVIDENCE_CHANGED"             // Underlying evidence changed (when not already reviewed)
  | "PORTFOLIO_VARIANT_MISMATCH"   // Researched plan differs from held share class
  | "REVIEWED_STABLE";             // Diligence complete and evidence stable against baseline

export interface AttentionReason {
  code: AttentionReasonCode;
  label: string;
  shortLabel: string;
  badgeClass: string;
  description: string;
  priorityRank: number; // 1 = highest attention precedence, 99 = no attention needed
  suggestedActionLabel: string;
  suggestedBriefSection?: "A" | "B" | "C" | "D" | "E" | "F" | "G";
}

export interface ResearchCompleteness {
  hasObjective: boolean;
  hasQuestion: boolean;
  hasThesis: boolean;
  hasNextStep: boolean;
  hasStatus: boolean;
  completedCount: number;
  totalElements: number;
  isComplete: boolean;
  missingElements: string[];
  descriptiveStatus: string;
}

export interface ResolvedAttentionItem {
  item: WatchlistResponse;
  primaryReason: AttentionReason;
  secondaryReasons: AttentionReason[];
  completeness: ResearchCompleteness;
  isInAttentionQueue: boolean;
}

export interface ResearchAttentionSummary {
  totalSaved: number;
  attentionQueueCount: number;
  needsFollowUpCount: number;
  reviewNeededCount: number;
  toInvestigateCount: number;
  staleEvidenceCount: number;
  incompleteRecordCount: number;
  missingThesisCount: number;
  missingQuestionCount: number;
  missingNextStepCount: number;
  portfolioMismatchCount: number;
}

/**
 * Resolves attention reasons and completeness for a single saved fund item.
 * Evaluates deterministically using existing observable properties.
 */
export function resolveAttentionItem(
  item: WatchlistResponse,
  evidenceSummary?: EvidenceChangeSummary | null,
  portfolioContext?: PortfolioContextResolution | null
): ResolvedAttentionItem {
  const reasons: AttentionReason[] = [];

  const status: DueDiligenceStatus = item.investigationStatus || "TO_INVESTIGATE";
  const hasObjective = Boolean(item.reasonSaved && item.reasonSaved.trim().length > 0);
  const hasQuestion = Boolean(item.investigationQuestion && item.investigationQuestion.trim().length > 0);
  const hasThesis = Boolean(item.workingThesis && item.workingThesis.trim().length > 0);
  const hasNextStep = Boolean(item.nextStep && item.nextStep.trim().length > 0);
  const hasStatus = Boolean(item.investigationStatus);

  // 1. NEEDS_FOLLOW_UP (Precedence Rank 1)
  if (status === "NEEDS_FOLLOW_UP") {
    reasons.push({
      code: "NEEDS_FOLLOW_UP",
      label: "⚠️ Follow-Up Required",
      shortLabel: "Follow-Up",
      badgeClass: "bg-rose-500/10 border-rose-500/40 text-rose-300 font-semibold",
      description: "Investigation explicitly marked for follow-up by investor.",
      priorityRank: 1,
      suggestedActionLabel: "Address Follow-Up →",
      suggestedBriefSection: "D",
    });
  }

  // 2. Material evidence changes on previously reviewed research (Precedence Rank 2)
  if (evidenceSummary?.requiresReview || (status === "REVIEWED" && evidenceSummary?.status === "EVIDENCE_CHANGED")) {
    reasons.push({
      code: "REVIEW_NEEDED",
      label: "⚠️ Reconsideration Needed",
      shortLabel: "Reconsideration",
      badgeClass: "bg-amber-500/10 border-amber-500/40 text-amber-300 font-semibold",
      description: "Previously marked Reviewed, but underlying market observations have changed since baseline.",
      priorityRank: 2,
      suggestedActionLabel: "Review Evidence Changes →",
      suggestedBriefSection: "F",
    });
  }

  // 3. Stale historical evidence cutoff (Precedence Rank 3)
  const isStale = item.latestNavDate === "2024-01-15" || !item.latestNavDate;
  if (isStale) {
    reasons.push({
      code: "STALE_EVIDENCE",
      label: "⏳ Stale Cutoff (2024-01-15)",
      shortLabel: "Stale Cutoff",
      badgeClass: "bg-amber-500/10 border-amber-500/30 text-amber-400 font-medium",
      description: "Observations terminate at historical cutoff (2024-01-15); fresh market inquiry recommended.",
      priorityRank: 3,
      suggestedActionLabel: "Inspect Evidence in Profile →",
      suggestedBriefSection: "C",
    });
  }

  // 4. TO_INVESTIGATE items (Precedence Rank 4)
  if (status === "TO_INVESTIGATE") {
    reasons.push({
      code: "UNREVIEWED_INVESTIGATION",
      label: "📋 Awaiting Initial Review",
      shortLabel: "To Investigate",
      badgeClass: "bg-sky-500/10 border-sky-500/30 text-sky-300 font-medium",
      description: "Saved fund awaiting initial due-diligence inquiry.",
      priorityRank: 4,
      suggestedActionLabel: "Start Due Diligence →",
      suggestedBriefSection: "A",
    });
  }

  // 5. IN_PROGRESS items with an explicit next step (Precedence Rank 5)
  if (status === "IN_PROGRESS" && hasNextStep) {
    reasons.push({
      code: "ACTIVE_NEXT_STEP_PENDING",
      label: "⚡ Next Step Pending",
      shortLabel: "Next Step",
      badgeClass: "bg-accent/15 border-accent/40 text-accent font-semibold",
      description: `Investor action pending: "${item.nextStep}"`,
      priorityRank: 5,
      suggestedActionLabel: "Resume Diligence →",
      suggestedBriefSection: "D",
    });
  }

  // 6. Incomplete records: Missing question or next step (Precedence Rank 6)
  if (!hasQuestion) {
    reasons.push({
      code: "MISSING_QUESTION",
      label: "❓ Missing Question",
      shortLabel: "No Question",
      badgeClass: "bg-surface-raised border-border text-text-secondary",
      description: "No specific due-diligence question formulated.",
      priorityRank: 6.0,
      suggestedActionLabel: "+ Record Question",
      suggestedBriefSection: "D",
    });
  }

  if (!hasNextStep) {
    reasons.push({
      code: "MISSING_NEXT_STEP",
      label: "↳ Missing Next Step",
      shortLabel: "No Next Step",
      badgeClass: "bg-surface-raised border-border text-text-secondary",
      description: "No actionable next step specified for ongoing investigation.",
      priorityRank: 6.5,
      suggestedActionLabel: "+ Specify Next Step",
      suggestedBriefSection: "D",
    });
  }

  // 7. Missing Thesis (Precedence Rank 7)
  if (!hasThesis) {
    reasons.push({
      code: "MISSING_THESIS",
      label: "💡 No Thesis Documented",
      shortLabel: "No Thesis",
      badgeClass: "bg-surface-raised border-border/80 text-text-muted",
      description: "No investor working hypothesis or conclusion documented before capital allocation.",
      priorityRank: 7.0,
      suggestedActionLabel: "+ Record Thesis",
      suggestedBriefSection: "D",
    });
  }

  // 8. General Evidence Change (when not in reviewed state, Precedence Rank 8)
  if (evidenceSummary?.status === "EVIDENCE_CHANGED" && !evidenceSummary?.requiresReview) {
    reasons.push({
      code: "EVIDENCE_CHANGED",
      label: "📊 Evidence Updated",
      shortLabel: "Evidence Changed",
      badgeClass: "bg-amber-500/10 border-amber-500/30 text-amber-300 font-medium",
      description: "Underlying observations have changed since baseline was captured.",
      priorityRank: 8.0,
      suggestedActionLabel: "Compare Evidence in Brief →",
      suggestedBriefSection: "F",
    });
  }

  // 9. Portfolio variant mismatch (Precedence Rank 9)
  const isVariantMismatch = Boolean(item.heldDifferentVariantNotice || (portfolioContext && portfolioContext.isVariantMatch));
  if (isVariantMismatch) {
    const mismatchDesc = item.heldDifferentVariantNotice ||
      (portfolioContext?.heldHolding
        ? `Portfolio holds ${portfolioContext.heldHolding.planType} • ${portfolioContext.heldHolding.optionType} while research target is ${item.planType || "DIRECT"} • ${item.optionType || "GROWTH"}.`
        : "Portfolio holds a different plan or option variant of this fund family.");

    reasons.push({
      code: "PORTFOLIO_VARIANT_MISMATCH",
      label: "⚠️ Different Option Held",
      shortLabel: "Option Mismatch",
      badgeClass: "bg-amber-500/10 border-amber-500/30 text-amber-300 font-medium",
      description: mismatchDesc,
      priorityRank: 9.0,
      suggestedActionLabel: "Inspect Portfolio Context →",
      suggestedBriefSection: "G",
    });
  }

  // Fallback: If no outstanding attention reasons apply (Precedence Rank 99)
  if (reasons.length === 0) {
    reasons.push({
      code: "REVIEWED_STABLE",
      label: "✓ Reviewed & Stable",
      shortLabel: "Stable",
      badgeClass: "bg-emerald-500/10 border-emerald-500/30 text-emerald-300 font-medium",
      description: "Due diligence complete; underlying observations stable against baseline.",
      priorityRank: 99,
      suggestedActionLabel: "Inspect Research Brief →",
      suggestedBriefSection: "A",
    });
  }

  // Sort reasons by priorityRank ascending to select the primary reason deterministically
  reasons.sort((a, b) => a.priorityRank - b.priorityRank);

  const primaryReason = reasons[0];
  const secondaryReasons = reasons.slice(1);
  const isInAttentionQueue = primaryReason.priorityRank < 99;

  // Compute Research Completeness descriptively
  const missingElements: string[] = [];
  if (!hasObjective) missingElements.push("Objective");
  if (!hasQuestion) missingElements.push("Question");
  if (!hasThesis) missingElements.push("Working Thesis");
  if (!hasNextStep) missingElements.push("Next Step");

  const completedCount = 5 - missingElements.length;
  const isComplete = missingElements.length === 0;

  const descriptiveStatus = isComplete
    ? "Complete research record (5/5 elements recorded)"
    : `Missing: ${missingElements.join(", ")}`;

  const completeness: ResearchCompleteness = {
    hasObjective,
    hasQuestion,
    hasThesis,
    hasNextStep,
    hasStatus,
    completedCount,
    totalElements: 5,
    isComplete,
    missingElements,
    descriptiveStatus,
  };

  return {
    item,
    primaryReason,
    secondaryReasons,
    completeness,
    isInAttentionQueue,
  };
}

/**
 * Deterministic priority comparator for sorting the research attention queue.
 * Orders strictly by primary precedence, then secondary precedence, then recency, then name.
 */
export function compareAttentionItems(a: ResolvedAttentionItem, b: ResolvedAttentionItem): number {
  if (a.primaryReason.priorityRank !== b.primaryReason.priorityRank) {
    return a.primaryReason.priorityRank - b.primaryReason.priorityRank;
  }
  // Secondary rank tie-breaker
  const aSecRank = a.secondaryReasons[0]?.priorityRank ?? 999;
  const bSecRank = b.secondaryReasons[0]?.priorityRank ?? 999;
  if (aSecRank !== bSecRank) {
    return aSecRank - bSecRank;
  }
  // Recency tie-breaker
  const timeA = a.item.updatedAt ? new Date(a.item.updatedAt).getTime() : 0;
  const timeB = b.item.updatedAt ? new Date(b.item.updatedAt).getTime() : 0;
  if (timeB !== timeA) {
    return timeB - timeA;
  }
  return a.item.schemeName.localeCompare(b.item.schemeName);
}

/**
 * Aggregates attention counts across all resolved items without double-counting.
 */
export function computeResearchAttentionSummary(
  resolvedItems: ResolvedAttentionItem[]
): ResearchAttentionSummary {
  const totalSaved = resolvedItems.length;
  let attentionQueueCount = 0;
  let needsFollowUpCount = 0;
  let reviewNeededCount = 0;
  let toInvestigateCount = 0;
  let staleEvidenceCount = 0;
  let incompleteRecordCount = 0;
  let missingThesisCount = 0;
  let missingQuestionCount = 0;
  let missingNextStepCount = 0;
  let portfolioMismatchCount = 0;

  for (const r of resolvedItems) {
    if (r.isInAttentionQueue) attentionQueueCount++;
    const allCodes = new Set([r.primaryReason.code, ...r.secondaryReasons.map((s) => s.code)]);

    if (allCodes.has("NEEDS_FOLLOW_UP")) needsFollowUpCount++;
    if (allCodes.has("REVIEW_NEEDED")) reviewNeededCount++;
    if (allCodes.has("UNREVIEWED_INVESTIGATION")) toInvestigateCount++;
    if (allCodes.has("STALE_EVIDENCE")) staleEvidenceCount++;
    if (!r.completeness.isComplete) incompleteRecordCount++;
    if (allCodes.has("MISSING_THESIS")) missingThesisCount++;
    if (allCodes.has("MISSING_QUESTION")) missingQuestionCount++;
    if (allCodes.has("MISSING_NEXT_STEP")) missingNextStepCount++;
    if (allCodes.has("PORTFOLIO_VARIANT_MISMATCH")) portfolioMismatchCount++;
  }

  return {
    totalSaved,
    attentionQueueCount,
    needsFollowUpCount,
    reviewNeededCount,
    toInvestigateCount,
    staleEvidenceCount,
    incompleteRecordCount,
    missingThesisCount,
    missingQuestionCount,
    missingNextStepCount,
    portfolioMismatchCount,
  };
}
