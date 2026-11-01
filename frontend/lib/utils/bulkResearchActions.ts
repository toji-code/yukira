import { WatchlistResponse, DueDiligenceStatus, DUE_DILIGENCE_STATUS_MAP } from "@/lib/api/investor";
import { getWatchlistItemKey } from "@/lib/hooks/useWatchlist";

export type BulkActionType =
  | "STATUS_TO_INVESTIGATE"
  | "STATUS_IN_PROGRESS"
  | "STATUS_NEEDS_FOLLOW_UP"
  | "STATUS_REVIEWED"
  | "SHORTLIST_ADD"
  | "SHORTLIST_REMOVE";

export interface SkippedItemDetail {
  key: string;
  schemeId: number;
  schemeOptionId?: number | null;
  schemeName: string;
  reason: string;
}

export interface FailedItemDetail {
  key: string;
  schemeId: number;
  schemeOptionId?: number | null;
  schemeName: string;
  error: string;
}

export interface BulkActionPreview {
  action: BulkActionType;
  actionLabel: string;
  targetStatus?: DueDiligenceStatus;
  targetStatusLabel?: string;
  totalSelected: number;
  eligibleCount: number;
  skippedCount: number;
  eligibleItems: WatchlistResponse[];
  skippedItems: SkippedItemDetail[];
  consequenceNotice: string;
  shortlistLimitWarning?: {
    currentCount: number;
    maxLimit: number;
    attemptedAddCount: number;
    availableSlots: number;
    excessCount: number;
  };
}

export interface BulkActionOutcome {
  action: BulkActionType;
  actionLabel: string;
  targetStatus?: DueDiligenceStatus;
  totalAttempted: number;
  successCount: number;
  skippedCount: number;
  failedCount: number;
  failedKeys: string[];
  failedItems: FailedItemDetail[];
  skippedItems: SkippedItemDetail[];
  timestamp: string;
  persistenceNote: string;
}

/**
 * Maps a BulkActionType to a human-readable title and target status if applicable.
 */
export function getBulkActionMeta(action: BulkActionType): {
  label: string;
  targetStatus?: DueDiligenceStatus;
  targetStatusLabel?: string;
} {
  switch (action) {
    case "STATUS_TO_INVESTIGATE":
      return {
        label: "Mark as To Investigate",
        targetStatus: "TO_INVESTIGATE",
        targetStatusLabel: DUE_DILIGENCE_STATUS_MAP.TO_INVESTIGATE.label,
      };
    case "STATUS_IN_PROGRESS":
      return {
        label: "Mark as In Progress",
        targetStatus: "IN_PROGRESS",
        targetStatusLabel: DUE_DILIGENCE_STATUS_MAP.IN_PROGRESS.label,
      };
    case "STATUS_NEEDS_FOLLOW_UP":
      return {
        label: "Mark as Needs Follow-Up",
        targetStatus: "NEEDS_FOLLOW_UP",
        targetStatusLabel: DUE_DILIGENCE_STATUS_MAP.NEEDS_FOLLOW_UP.label,
      };
    case "STATUS_REVIEWED":
      return {
        label: "Mark as Reviewed",
        targetStatus: "REVIEWED",
        targetStatusLabel: DUE_DILIGENCE_STATUS_MAP.REVIEWED.label,
      };
    case "SHORTLIST_ADD":
      return {
        label: "Add to Comparison Shortlist",
      };
    case "SHORTLIST_REMOVE":
      return {
        label: "Remove from Comparison Shortlist",
      };
  }
}

/**
 * Evaluates the selected items against the requested bulk action to generate a preview.
 * Determines eligible vs skipped records, checks shortlist candidate capacity limits,
 * and formulates epistemic consequence disclosures.
 */
export function calculateBulkActionPreview(
  action: BulkActionType,
  selectedItems: WatchlistResponse[],
  currentShortlistKeys: string[],
  maxShortlistLimit: number = 4
): BulkActionPreview {
  const meta = getBulkActionMeta(action);
  const eligibleItems: WatchlistResponse[] = [];
  const skippedItems: SkippedItemDetail[] = [];

  if (meta.targetStatus) {
    const targetStatus = meta.targetStatus;
    selectedItems.forEach((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      const currentStatus: DueDiligenceStatus = item.investigationStatus || "TO_INVESTIGATE";

      if (currentStatus === targetStatus) {
        skippedItems.push({
          key,
          schemeId: item.schemeId,
          schemeOptionId: item.schemeOptionId,
          schemeName: item.schemeName,
          reason: `Already in status "${DUE_DILIGENCE_STATUS_MAP[targetStatus].label}"`,
        });
      } else {
        eligibleItems.push(item);
      }
    });

    let consequenceNotice = "";
    if (targetStatus === "REVIEWED") {
      consequenceNotice =
        "Marks investigations as reviewed. Note: this records investor research progress only. It does NOT alter quantitative metrics, modify evidence change baselines, dismiss attention flags, or constitute investment advice.";
    } else if (targetStatus === "NEEDS_FOLLOW_UP") {
      consequenceNotice =
        "Flags selected investigations for follow-up review. Investor hypotheses, questions, and working theses are preserved without modification.";
    } else if (targetStatus === "TO_INVESTIGATE") {
      consequenceNotice =
        "Resets investigations to the initial diligence queue. Existing research notes and evidence baselines remain intact.";
    } else if (targetStatus === "IN_PROGRESS") {
      consequenceNotice =
        "Marks investigations as actively under evaluation. Preserves all working theses and next steps.";
    }

    return {
      action,
      actionLabel: meta.label,
      targetStatus,
      targetStatusLabel: meta.targetStatusLabel,
      totalSelected: selectedItems.length,
      eligibleCount: eligibleItems.length,
      skippedCount: skippedItems.length,
      eligibleItems,
      skippedItems,
      consequenceNotice,
    };
  }

  if (action === "SHORTLIST_ADD") {
    selectedItems.forEach((item) => {
      const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
      if (currentShortlistKeys.includes(key)) {
        skippedItems.push({
          key,
          schemeId: item.schemeId,
          schemeOptionId: item.schemeOptionId,
          schemeName: item.schemeName,
          reason: "Already included in comparison shortlist",
        });
      } else {
        eligibleItems.push(item);
      }
    });

    const currentCount = currentShortlistKeys.length;
    const availableSlots = Math.max(0, maxShortlistLimit - currentCount);
    let shortlistLimitWarning: BulkActionPreview["shortlistLimitWarning"] | undefined;

    if (eligibleItems.length > availableSlots) {
      shortlistLimitWarning = {
        currentCount,
        maxLimit: maxShortlistLimit,
        attemptedAddCount: eligibleItems.length,
        availableSlots,
        excessCount: eligibleItems.length - availableSlots,
      };
    }

    return {
      action,
      actionLabel: meta.label,
      totalSelected: selectedItems.length,
      eligibleCount: eligibleItems.length,
      skippedCount: skippedItems.length,
      eligibleItems,
      skippedItems,
      consequenceNotice:
        "Adds selected mutual funds to the side-by-side evidence comparison shortlist. Shortlist membership enables comparative evaluation but does not imply an investment recommendation or portfolio addition.",
      shortlistLimitWarning,
    };
  }

  // SHORTLIST_REMOVE
  selectedItems.forEach((item) => {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    if (!currentShortlistKeys.includes(key)) {
      skippedItems.push({
        key,
        schemeId: item.schemeId,
        schemeOptionId: item.schemeOptionId,
        schemeName: item.schemeName,
        reason: "Not currently in comparison shortlist",
      });
    } else {
      eligibleItems.push(item);
    }
  });

  return {
    action,
    actionLabel: meta.label,
    totalSelected: selectedItems.length,
    eligibleCount: eligibleItems.length,
    skippedCount: skippedItems.length,
    eligibleItems,
    skippedItems,
    consequenceNotice:
      "Removes selected funds from the comparison shortlist. Research notes and saved fund investigations remain untouched.",
  };
}

/**
 * Executes a bulk due-diligence status update deterministically.
 * Updates each eligible item sequentially, preserving existing notes and thesis fields.
 * If any update throws an error, it records the failure, keeps the item in `failedKeys`,
 * and allows the caller to retain failed items in the selection for immediate retry.
 */
export async function executeBulkStatusUpdate(
  preview: BulkActionPreview,
  updateInvestigationStatus: (
    schemeId: number,
    schemeOptionId: number | null | undefined,
    status: DueDiligenceStatus
  ) => void | Promise<void>
): Promise<BulkActionOutcome> {
  if (!preview.targetStatus) {
    throw new Error(`Action ${preview.action} has no target status.`);
  }

  const targetStatus = preview.targetStatus;
  const successfulKeys: string[] = [];
  const failedKeys: string[] = [];
  const failedItems: FailedItemDetail[] = [];

  for (const item of preview.eligibleItems) {
    const key = getWatchlistItemKey(item.schemeId, item.schemeOptionId);
    try {
      await Promise.resolve(updateInvestigationStatus(item.schemeId, item.schemeOptionId, targetStatus));
      successfulKeys.push(key);
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : "Failed to update investigation status";
      failedKeys.push(key);
      failedItems.push({
        key,
        schemeId: item.schemeId,
        schemeOptionId: item.schemeOptionId,
        schemeName: item.schemeName,
        error: errorMsg,
      });
    }
  }

  return {
    action: preview.action,
    actionLabel: preview.actionLabel,
    targetStatus,
    totalAttempted: preview.eligibleItems.length,
    successCount: successfulKeys.length,
    skippedCount: preview.skippedItems.length,
    failedCount: failedKeys.length,
    failedKeys,
    failedItems,
    skippedItems: preview.skippedItems,
    timestamp: new Date().toISOString(),
    persistenceNote:
      "Due-diligence status updates are persisted to research workspace storage. Notes, working theses, and evidence baselines remain unchanged.",
  };
}

/**
 * Executes bulk addition to the comparison shortlist, respecting maximum slot capacity.
 * If candidates exceed available slots and caller specifies `fillAvailableSlots`,
 * it adds candidates up to the capacity and returns remaining as skipped.
 */
export function executeBulkShortlistAdd(
  preview: BulkActionPreview,
  currentShortlist: string[],
  mode: "fill_available" | "replace_all" = "fill_available",
  maxLimit: number = 4
): {
  newShortlist: string[];
  addedCount: number;
  skippedCount: number;
  outcome: BulkActionOutcome;
} {
  const existingSet = new Set(currentShortlist);
  const eligibleKeys = preview.eligibleItems.map((i) =>
    getWatchlistItemKey(i.schemeId, i.schemeOptionId)
  );

  let updatedList: string[];
  let addedCount = 0;
  const skippedItems: SkippedItemDetail[] = [...preview.skippedItems];

  if (mode === "replace_all") {
    const toTake = eligibleKeys.slice(0, maxLimit);
    updatedList = toTake;
    addedCount = toTake.length;
    if (eligibleKeys.length > maxLimit) {
      preview.eligibleItems.slice(maxLimit).forEach((item) => {
        skippedItems.push({
          key: getWatchlistItemKey(item.schemeId, item.schemeOptionId),
          schemeId: item.schemeId,
          schemeOptionId: item.schemeOptionId,
          schemeName: item.schemeName,
          reason: `Exceeded comparison capacity of ${maxLimit} candidates`,
        });
      });
    }
  } else {
    // Fill available slots
    const availableSlots = Math.max(0, maxLimit - currentShortlist.length);
    const toAdd = eligibleKeys.slice(0, availableSlots);
    toAdd.forEach((k) => existingSet.add(k));
    updatedList = Array.from(existingSet);
    addedCount = toAdd.length;

    if (eligibleKeys.length > availableSlots) {
      preview.eligibleItems.slice(availableSlots).forEach((item) => {
        skippedItems.push({
          key: getWatchlistItemKey(item.schemeId, item.schemeOptionId),
          schemeId: item.schemeId,
          schemeOptionId: item.schemeOptionId,
          schemeName: item.schemeName,
          reason: `Shortlist capacity reached (${maxLimit} candidates maximum)`,
        });
      });
    }
  }

  const outcome: BulkActionOutcome = {
    action: "SHORTLIST_ADD",
    actionLabel: "Add to Comparison Shortlist",
    totalAttempted: preview.eligibleItems.length,
    successCount: addedCount,
    skippedCount: skippedItems.length,
    failedCount: 0,
    failedKeys: [],
    failedItems: [],
    skippedItems,
    timestamp: new Date().toISOString(),
    persistenceNote: `Added ${addedCount} mutual fund candidates to comparison shortlist. Shortlist contains ${updatedList.length} of ${maxLimit} candidates.`,
  };

  return {
    newShortlist: updatedList,
    addedCount,
    skippedCount: skippedItems.length,
    outcome,
  };
}

/**
 * Executes bulk removal of selected candidates from the comparison shortlist.
 */
export function executeBulkShortlistRemove(
  preview: BulkActionPreview,
  currentShortlist: string[]
): {
  newShortlist: string[];
  removedCount: number;
  outcome: BulkActionOutcome;
} {
  const keysToRemove = new Set(
    preview.eligibleItems.map((i) => getWatchlistItemKey(i.schemeId, i.schemeOptionId))
  );
  const newShortlist = currentShortlist.filter((k) => !keysToRemove.has(k));
  const removedCount = currentShortlist.length - newShortlist.length;

  const outcome: BulkActionOutcome = {
    action: "SHORTLIST_REMOVE",
    actionLabel: "Remove from Comparison Shortlist",
    totalAttempted: preview.eligibleItems.length,
    successCount: removedCount,
    skippedCount: preview.skippedItems.length,
    failedCount: 0,
    failedKeys: [],
    failedItems: [],
    skippedItems: preview.skippedItems,
    timestamp: new Date().toISOString(),
    persistenceNote: `Removed ${removedCount} candidates from comparison shortlist.`,
  };

  return {
    newShortlist,
    removedCount,
    outcome,
  };
}
