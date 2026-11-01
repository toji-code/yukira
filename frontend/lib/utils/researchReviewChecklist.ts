/**
 * YUKIRA Quantitative Intelligence Platform
 * Phase F14: Research Review Checklist and Evidence-Based Review Completion
 *
 * Implements deterministic tracking of investor due-diligence review activity
 * across individual fund investigations.
 *
 * CRITICAL EPISTEMIC GUARDRAILS:
 * 1. Checklist completion records the INVESTOR'S REVIEW ACTIVITY only.
 * 2. It does NOT independently validate financial calculations, metric correctness,
 *    fund quality, manager skill, or investment suitability.
 * 3. Populating documentation does NOT count as evidence inspection.
 * 4. Changing due-diligence status does NOT mark checklist items reviewed.
 * 5. Completing all checklist items does NOT auto-transition due-diligence status.
 * 6. Evidence drift / changes require renewed inspection even if previously marked reviewed.
 * 7. Browser-local storage is partitioned by authenticated user / guest identity.
 * 8. Exact (schemeId, schemeOptionId) identity is strictly preserved.
 */

import { WatchlistResponse } from "@/lib/api/investor";
import { getWatchlistItemKey } from "@/lib/hooks/useWatchlist";
import { EvidenceChangeSummary } from "@/lib/utils/evidenceChange";
import { PortfolioContextResolution } from "@/lib/utils/portfolioContext";

export type ReviewItemKey =
  | "IDENTIFY_OPTION"
  | "QUANTITATIVE_PROFILE"
  | "RISK_LIMITATIONS"
  | "THESIS_EVIDENCE"
  | "RECONSIDERATION_FACTORS"
  | "EVIDENCE_HISTORY"
  | "PORTFOLIO_CONTEXT"
  | "QUESTIONS_NEXT_STEPS";

export type ReviewItemStatus =
  | "NOT_REVIEWED"
  | "REVIEWED"
  | "NEEDS_INVESTIGATION"
  | "NOT_APPLICABLE";

export interface ChecklistItemRecord {
  itemKey: ReviewItemKey;
  status: ReviewItemStatus;
  reviewedAt?: string | null; // ISO timestamp
  notes?: string | null; // Optional investor finding note
  snapshotRunId?: number | null; // Calculation run ID at time of review
  snapshotAsOfDate?: string | null; // Data cutoff date at time of review
  // Phase F17: Historical Review Preservation & Reassessment Tracking
  priorReviewedAt?: string | null; // Preserves historical review timestamp across evidence drift
  priorSnapshotRunId?: number | null; // Preserves calculation run ID from historical review
  priorSnapshotAsOfDate?: string | null; // Preserves cutoff date from historical review
  reassessedAt?: string | null; // Timestamp when investor explicitly performed renewed inspection
  reassessedRunId?: number | null; // Calculation run ID evaluated during reassessment
  reassessedAsOfDate?: string | null; // Data cutoff date evaluated during reassessment
  reassessmentNotes?: string | null; // Investor note recorded during reassessment
}

export interface InvestigationReviewChecklistRecord {
  key: string; // schemeId:schemeOptionId formatted via getWatchlistItemKey
  schemeId: number;
  schemeOptionId?: number | null;
  items: Record<ReviewItemKey, ChecklistItemRecord>;
  lastReviewedAt?: string | null;
  updatedAt: string;
}

export interface ChecklistItemConfig {
  key: ReviewItemKey;
  title: string;
  shortLabel: string;
  briefSection: "A" | "B" | "C" | "D" | "E" | "F" | "G";
  description: string;
  evidenceSource: string;
  defaultApplicable: (
    fund: WatchlistResponse,
    portfolioContext?: PortfolioContextResolution | null
  ) => boolean;
}

export const REVIEW_STATUS_LABELS: Record<
  ReviewItemStatus,
  { label: string; badgeClass: string; description: string }
> = {
  NOT_REVIEWED: {
    label: "Not Reviewed",
    badgeClass: "bg-surface-raised text-text-muted border-border",
    description: "Evidence has not yet been inspected for this section.",
  },
  REVIEWED: {
    label: "Reviewed",
    badgeClass: "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
    description: "Investor has examined and verified the evidence in this section.",
  },
  NEEDS_INVESTIGATION: {
    label: "Needs Follow-Up",
    badgeClass: "bg-rose-500/10 text-rose-400 border-rose-500/30",
    description: "Investor identified unresolved questions or concerns requiring further audit.",
  },
  NOT_APPLICABLE: {
    label: "Not Applicable",
    badgeClass: "bg-surface-raised/40 text-text-tertiary border-border/50",
    description: "Section is not applicable to this share class, plan, or portfolio situation.",
  },
};

export const CHECKLIST_ITEMS_CONFIG: ChecklistItemConfig[] = [
  {
    key: "IDENTIFY_OPTION",
    title: "Share Class & Option Identity",
    shortLabel: "Option Identity",
    briefSection: "A",
    description: "Verify Direct vs. Regular plan, Growth option, AMFI scheme code, and ISIN accuracy.",
    evidenceSource: "Master Registry (AMFI / Fund Factsheet)",
    defaultApplicable: () => true,
  },
  {
    key: "QUANTITATIVE_PROFILE",
    title: "Quantitative Profile & Horizon",
    shortLabel: "Quant Profile",
    briefSection: "B",
    description: "Inspect 16 deterministic metrics, annualized returns, benchmark active returns, and data horizon.",
    evidenceSource: "Quantitative Calculation Engine (Historical Cutoff 2024-01-15)",
    defaultApplicable: () => true,
  },
  {
    key: "RISK_LIMITATIONS",
    title: "Risk Profile & Limitations",
    shortLabel: "Risk & Limitations",
    briefSection: "C",
    description: "Inspect annualized volatility, maximum drawdown, tracking error, and observation depth limits.",
    evidenceSource: "Risk Kernel & Deterministic Data Freshness Registry",
    defaultApplicable: () => true,
  },
  {
    key: "THESIS_EVIDENCE",
    title: "Working Thesis & Empirical Notes",
    shortLabel: "Thesis & Evidence",
    briefSection: "D",
    description: "Formulate and audit the investor's core thesis and supporting empirical evidence points.",
    evidenceSource: "Investor Research Decision Log (Local Storage)",
    defaultApplicable: () => true,
  },
  {
    key: "RECONSIDERATION_FACTORS",
    title: "Reconsideration Triggers & Invalidation",
    shortLabel: "Invalidation Triggers",
    briefSection: "E",
    description: "Define explicit falsification criteria and regime shifts that would disconfirm the working thesis.",
    evidenceSource: "Investor Research Decision Log (Local Storage)",
    defaultApplicable: () => true,
  },
  {
    key: "EVIDENCE_HISTORY",
    title: "Evidence Stability & Metric Drift",
    shortLabel: "Evidence History",
    briefSection: "F",
    description: "Check for evidence drift, calculation run updates, or benchmark revisions against snapshot baseline.",
    evidenceSource: "Cryptographic Evidence Snapshot Registry",
    defaultApplicable: () => true,
  },
  {
    key: "PORTFOLIO_CONTEXT",
    title: "Portfolio Relationship & Exposure",
    shortLabel: "Portfolio Context",
    briefSection: "G",
    description: "Verify whether this option is already held, variant mismatch drag, or sizing implications.",
    evidenceSource: "Investor Portfolio Service",
    defaultApplicable: (fund, portfolioContext) => {
      if (fund.isHeldInPortfolio) return true;
      if (portfolioContext?.isVariantMatch) return true;
      return false; // Defaults to optional/not applicable if fund is not held or referenced
    },
  },
  {
    key: "QUESTIONS_NEXT_STEPS",
    title: "Diligence Questions & Next Step",
    shortLabel: "Questions & Next Steps",
    briefSection: "A",
    description: "Ensure remaining investigation uncertainties are documented with an actionable next diligence step.",
    evidenceSource: "Watchlist Due-Diligence Context (Server Synced)",
    defaultApplicable: () => true,
  },
];

export interface ChecklistProgressSummary {
  key: string;
  totalItems: number;
  applicableCount: number;
  reviewedCount: number;
  needsInvestigationCount: number;
  notReviewedCount: number;
  notApplicableCount: number;
  // Progress description
  completionRatio: string; // e.g. "4 of 7 applicable sections reviewed"
  completionPercentage: number; // 0-100 deterministic percentage
  isFullyReviewed: boolean;
  hasItemsNeedingInvestigation: boolean;
  // Evidence change interaction
  isEvidenceChangedSinceReview: boolean;
  reassessmentNeededCount: number; // Count of REVIEWED items requiring renewed inspection due to evidence drift
  reassessedItemsCount: number; // Count of items explicitly reassessed against current baseline
  evidenceReviewNotice?: string | null;
  // Status badge
  statusBadgeLabel: string;
  statusBadgeClass: string;
}

const CHECKLIST_STORAGE_PREFIX = "yukira_investigation_review_checklists_";

/**
 * Derives a namespaced local-storage key to partition checklist state by user identity.
 */
export function getReviewChecklistStorageKey(userId?: string | null): string {
  if (userId && typeof userId === "string" && userId.trim().length > 0) {
    return `${CHECKLIST_STORAGE_PREFIX}${encodeURIComponent(userId.trim())}`;
  }
  return `${CHECKLIST_STORAGE_PREFIX}guest`;
}

/**
 * Loads all stored review checklist records from browser storage.
 */
export function loadAllReviewChecklists(
  userId?: string | null
): Record<string, InvestigationReviewChecklistRecord> {
  if (typeof window === "undefined") return {};
  try {
    const key = getReviewChecklistStorageKey(userId);
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

/**
 * Retrieves the review checklist for a specific fund share class.
 */
export function getInvestigationReviewChecklist(
  schemeId: number,
  schemeOptionId?: number | null,
  userId?: string | null
): InvestigationReviewChecklistRecord | null {
  const itemKey = getWatchlistItemKey(schemeId, schemeOptionId);
  const all = loadAllReviewChecklists(userId);
  return all[itemKey] || null;
}

/**
 * Creates default items map for a fund share class based on applicability.
 */
export function createDefaultChecklistItems(
  fund: WatchlistResponse,
  portfolioContext?: PortfolioContextResolution | null
): Record<ReviewItemKey, ChecklistItemRecord> {
  const result: Partial<Record<ReviewItemKey, ChecklistItemRecord>> = {};
  for (const config of CHECKLIST_ITEMS_CONFIG) {
    const isApplicable = config.defaultApplicable(fund, portfolioContext);
    result[config.key] = {
      itemKey: config.key,
      status: isApplicable ? "NOT_REVIEWED" : "NOT_APPLICABLE",
      reviewedAt: null,
      notes: null,
    };
  }
  return result as Record<ReviewItemKey, ChecklistItemRecord>;
}

/**
 * Persists a review checklist record to browser storage.
 */
export function saveInvestigationReviewChecklist(
  record: InvestigationReviewChecklistRecord,
  userId?: string | null
): void {
  if (typeof window === "undefined") return;
  try {
    const storageKey = getReviewChecklistStorageKey(userId);
    const all = loadAllReviewChecklists(userId);
    all[record.key] = {
      ...record,
      updatedAt: new Date().toISOString(),
    };
    window.localStorage.setItem(storageKey, JSON.stringify(all));
  } catch (err) {
    console.warn("Failed to persist investigation review checklist to browser storage", err);
  }
}

/**
 * Evaluates whether an individual checklist item requires renewed investor inspection
 * due to underlying evidence changes (calculation run transition, observation cutoff shift, or metric value drift).
 */
export function isItemReassessmentNeeded(
  item: ChecklistItemRecord | undefined,
  itemKey: ReviewItemKey,
  evidenceSummary?: EvidenceChangeSummary | null
): boolean {
  if (!item || item.status !== "REVIEWED") return false;
  if (!evidenceSummary || evidenceSummary.status !== "EVIDENCE_CHANGED") return false;

  // Sections sensitive to quantitative calculation runs, observation windows, and metric shifts
  const isEvidenceSensitive =
    itemKey === "QUANTITATIVE_PROFILE" || itemKey === "EVIDENCE_HISTORY";
  if (!isEvidenceSensitive) return false;

  // If item was already reassessed against the current calculation run
  if (
    item.reassessedRunId &&
    evidenceSummary.currentRunId &&
    item.reassessedRunId === evidenceSummary.currentRunId
  ) {
    return false;
  }

  // If item has a recorded snapshot run ID equal to currentRunId and no prior review drift
  if (
    item.snapshotRunId &&
    evidenceSummary.currentRunId &&
    item.snapshotRunId === evidenceSummary.currentRunId &&
    !item.priorReviewedAt
  ) {
    return false;
  }

  return true;
}

/**
 * Updates the review status of a specific checklist item for a fund share class.
 * Preserves historical review timestamps and run references when items are toggled or reassessed.
 */
export function updateChecklistItemStatus(
  fund: WatchlistResponse,
  itemKey: ReviewItemKey,
  status: ReviewItemStatus,
  meta?: {
    notes?: string | null;
    snapshotRunId?: number | null;
    snapshotAsOfDate?: string | null;
    isReassessment?: boolean;
    reassessmentNotes?: string | null;
  },
  userId?: string | null,
  portfolioContext?: PortfolioContextResolution | null
): InvestigationReviewChecklistRecord {
  const key = getWatchlistItemKey(fund.schemeId, fund.schemeOptionId);
  const existing = getInvestigationReviewChecklist(fund.schemeId, fund.schemeOptionId, userId);

  const items = existing ? { ...existing.items } : createDefaultChecklistItems(fund, portfolioContext);

  const prevItem = items[itemKey] || { itemKey, status: "NOT_REVIEWED" };
  const now = new Date().toISOString();

  // Preserve historical review details into prior* fields if resetting or reassessing
  const priorReviewedAt =
    prevItem.priorReviewedAt ||
    (prevItem.status === "REVIEWED" && status !== "REVIEWED" ? prevItem.reviewedAt : null) ||
    prevItem.priorReviewedAt ||
    null;

  const priorSnapshotRunId =
    prevItem.priorSnapshotRunId !== undefined
      ? prevItem.priorSnapshotRunId
      : prevItem.snapshotRunId;

  const priorSnapshotAsOfDate =
    prevItem.priorSnapshotAsOfDate !== undefined
      ? prevItem.priorSnapshotAsOfDate
      : prevItem.snapshotAsOfDate;

  items[itemKey] = {
    ...prevItem,
    status,
    reviewedAt:
      status === "REVIEWED" || status === "NEEDS_INVESTIGATION"
        ? now
        : prevItem.reviewedAt,
    notes: meta?.notes !== undefined ? meta.notes : prevItem.notes,
    snapshotRunId:
      meta?.snapshotRunId !== undefined ? meta.snapshotRunId : prevItem.snapshotRunId,
    snapshotAsOfDate:
      meta?.snapshotAsOfDate !== undefined ? meta.snapshotAsOfDate : prevItem.snapshotAsOfDate,
    priorReviewedAt: priorReviewedAt || prevItem.priorReviewedAt,
    priorSnapshotRunId,
    priorSnapshotAsOfDate,
    reassessedAt: meta?.isReassessment ? now : prevItem.reassessedAt,
    reassessedRunId: meta?.isReassessment ? meta.snapshotRunId ?? null : prevItem.reassessedRunId,
    reassessedAsOfDate: meta?.isReassessment ? meta.snapshotAsOfDate ?? null : prevItem.reassessedAsOfDate,
    reassessmentNotes:
      meta?.reassessmentNotes !== undefined
        ? meta.reassessmentNotes
        : prevItem.reassessmentNotes,
  };

  const updatedRecord: InvestigationReviewChecklistRecord = {
    key,
    schemeId: fund.schemeId,
    schemeOptionId: fund.schemeOptionId,
    items,
    lastReviewedAt: status === "REVIEWED" ? now : existing?.lastReviewedAt,
    updatedAt: now,
  };

  saveInvestigationReviewChecklist(updatedRecord, userId);
  return updatedRecord;
}

/**
 * Explicitly records investor reassessment of a previously reviewed checklist item
 * against the current quantitative calculation run and evidence observations.
 * Preserves historical review facts while recording new reassessment timestamp and run ID.
 */
export function reassessChecklistItem(
  fund: WatchlistResponse,
  itemKey: ReviewItemKey,
  meta?: {
    reassessmentNotes?: string | null;
    currentRunId?: number | null;
    currentAsOfDate?: string | null;
  },
  userId?: string | null,
  portfolioContext?: PortfolioContextResolution | null
): InvestigationReviewChecklistRecord {
  const key = getWatchlistItemKey(fund.schemeId, fund.schemeOptionId);
  const existing = getInvestigationReviewChecklist(fund.schemeId, fund.schemeOptionId, userId);

  const items = existing ? { ...existing.items } : createDefaultChecklistItems(fund, portfolioContext);

  const prevItem = items[itemKey] || { itemKey, status: "NOT_REVIEWED" };
  const now = new Date().toISOString();

  // Preserve historical review details into prior* fields
  const priorReviewedAt = prevItem.priorReviewedAt || prevItem.reviewedAt || now;
  const priorSnapshotRunId =
    prevItem.priorSnapshotRunId !== undefined
      ? prevItem.priorSnapshotRunId
      : prevItem.snapshotRunId;
  const priorSnapshotAsOfDate =
    prevItem.priorSnapshotAsOfDate !== undefined
      ? prevItem.priorSnapshotAsOfDate
      : prevItem.snapshotAsOfDate;

  items[itemKey] = {
    ...prevItem,
    status: "REVIEWED",
    reviewedAt: now,
    priorReviewedAt,
    priorSnapshotRunId,
    priorSnapshotAsOfDate,
    reassessedAt: now,
    reassessedRunId: meta?.currentRunId ?? prevItem.snapshotRunId ?? null,
    reassessedAsOfDate: meta?.currentAsOfDate ?? prevItem.snapshotAsOfDate ?? null,
    reassessmentNotes:
      meta?.reassessmentNotes !== undefined
        ? meta.reassessmentNotes
        : prevItem.reassessmentNotes,
    snapshotRunId:
      meta?.currentRunId !== undefined ? meta.currentRunId : prevItem.snapshotRunId,
    snapshotAsOfDate:
      meta?.currentAsOfDate !== undefined ? meta.currentAsOfDate : prevItem.snapshotAsOfDate,
  };

  const updatedRecord: InvestigationReviewChecklistRecord = {
    key,
    schemeId: fund.schemeId,
    schemeOptionId: fund.schemeOptionId,
    items,
    lastReviewedAt: now,
    updatedAt: now,
  };

  saveInvestigationReviewChecklist(updatedRecord, userId);
  return updatedRecord;
}

/**
 * Computes deterministic progress summary for an investigation review checklist.
 */
export function computeChecklistProgress(
  record: InvestigationReviewChecklistRecord | null,
  fund: WatchlistResponse,
  evidenceSummary?: EvidenceChangeSummary | null,
  portfolioContext?: PortfolioContextResolution | null
): ChecklistProgressSummary {
  const key = getWatchlistItemKey(fund.schemeId, fund.schemeOptionId);
  const items = record ? record.items : createDefaultChecklistItems(fund, portfolioContext);

  const totalItems = CHECKLIST_ITEMS_CONFIG.length;
  let applicableCount = 0;
  let reviewedCount = 0;
  let needsInvestigationCount = 0;
  let notReviewedCount = 0;
  let notApplicableCount = 0;
  let reassessmentNeededCount = 0;
  let reassessedItemsCount = 0;

  for (const config of CHECKLIST_ITEMS_CONFIG) {
    const item = items[config.key];
    const status = item ? item.status : "NOT_REVIEWED";

    if (status === "NOT_APPLICABLE") {
      notApplicableCount++;
    } else {
      applicableCount++;
      if (status === "REVIEWED") {
        reviewedCount++;
        if (item?.reassessedAt) {
          reassessedItemsCount++;
        }
        if (isItemReassessmentNeeded(item, config.key, evidenceSummary)) {
          reassessmentNeededCount++;
        }
      } else if (status === "NEEDS_INVESTIGATION") {
        needsInvestigationCount++;
      } else {
        notReviewedCount++;
      }
    }
  }

  const completionPercentage =
    applicableCount > 0 ? Math.round((reviewedCount / applicableCount) * 100) : 0;
  const isFullyReviewed = applicableCount > 0 && reviewedCount === applicableCount;
  const hasItemsNeedingInvestigation = needsInvestigationCount > 0;

  // Evidence change interaction:
  // If evidence has changed since snapshot, flag that previous reviews may require re-inspection
  const isEvidenceChanged =
    evidenceSummary?.status === "EVIDENCE_CHANGED" || Boolean(evidenceSummary?.requiresReview);

  let evidenceReviewNotice: string | null = null;
  if (reassessmentNeededCount > 0) {
    evidenceReviewNotice = `Underlying evidence baseline has shifted since previous review. ${reassessmentNeededCount} section(s) require renewed investor inspection.`;
  } else if (isEvidenceChanged && reviewedCount > 0) {
    evidenceReviewNotice =
      "Evidence baseline has changed since initial inspection. Sections B and F require renewed review.";
  }

  // Derive badge
  let statusBadgeLabel = "NOT REVIEWED";
  let statusBadgeClass = "bg-surface-raised text-text-muted border-border";

  if (hasItemsNeedingInvestigation) {
    statusBadgeLabel = `${needsInvestigationCount} FOLLOW-UP NEEDED`;
    statusBadgeClass = "bg-rose-500/10 text-rose-400 border-rose-500/30";
  } else if (reassessmentNeededCount > 0) {
    statusBadgeLabel = `${reassessmentNeededCount} REASSESSMENT NEEDED`;
    statusBadgeClass = "bg-amber-500/15 text-amber-300 border-amber-500/40";
  } else if (isEvidenceChanged && reviewedCount > 0) {
    statusBadgeLabel = "EVIDENCE CHANGED (RE-REVIEW)";
    statusBadgeClass = "bg-amber-500/10 text-amber-400 border-amber-500/30";
  } else if (isFullyReviewed) {
    statusBadgeLabel = `REVIEW COMPLETE (${reviewedCount}/${applicableCount})`;
    statusBadgeClass = "bg-emerald-500/10 text-emerald-400 border-emerald-500/30";
  } else if (reviewedCount > 0) {
    statusBadgeLabel = `PARTIALLY REVIEWED (${reviewedCount}/${applicableCount})`;
    statusBadgeClass = "bg-blue-500/10 text-blue-400 border-blue-500/30";
  }

  return {
    key,
    totalItems,
    applicableCount,
    reviewedCount,
    needsInvestigationCount,
    notReviewedCount,
    notApplicableCount,
    completionRatio: `${reviewedCount} of ${applicableCount} applicable sections reviewed`,
    completionPercentage,
    isFullyReviewed,
    hasItemsNeedingInvestigation,
    isEvidenceChangedSinceReview: isEvidenceChanged,
    reassessmentNeededCount,
    reassessedItemsCount,
    evidenceReviewNotice,
    statusBadgeLabel,
    statusBadgeClass,
  };
}
